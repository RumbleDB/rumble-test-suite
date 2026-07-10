package evaluation;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.streams.Steps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;

public class Environment {
    private final Map<String, String> resourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();
    private final Map<String, String> externalParamLookup = new HashMap<>();
    private final Map<String, String> roleLookup = new HashMap<>();

    private final Map<String, String> namespaceLookup = new HashMap<>();


    private final List<String> decimalFormatDeclarations = new ArrayList<>();

    private boolean staticBaseUriUndefined = false;

    public Environment(XdmNode environmentNode, Path envPath) {
        initParams(environmentNode);
        initNamespaces(environmentNode);
        initDecimalFormats(environmentNode);
        initStaticBaseUri(environmentNode);
        initResources(environmentNode, envPath);
        initSources(environmentNode, envPath);
    }

    private void initParams(XdmNode environmentNode) {
        for (XdmNode param : environmentNode.children("param")) {
            String name = param.attribute("name");
            String select = param.attribute("select");
            String declared = param.attribute("declared");
            if ("true".equals(declared)) {
                externalParamLookup.put(name, select);
            } else {
                paramLookup.put(name, select);
            }
        }
    }

    private void initNamespaces(XdmNode environmentNode) {
        for (XdmNode namespace : environmentNode.children("namespace")) {
            String prefix = namespace.attribute("prefix");
            String uri = namespace.attribute("uri");
            if (prefix != null && uri != null) {
                namespaceLookup.put(prefix, uri);
            }
        }
    }


    private void initDecimalFormats(XdmNode environmentNode) {
        for (XdmNode decimalFormat : environmentNode.children("decimal-format")) {
            StringBuilder sb = new StringBuilder();

            String name = decimalFormat.attribute("name");
            if (name == null || name.isBlank()) {
                sb.append("declare default decimal-format");
            } else {
                sb.append("declare decimal-format ").append(name);

                int colon = name.indexOf(':');
                if (colon > 0) {
                    String prefix = name.substring(0, colon);
                    String uri = resolveNamespaceUri(decimalFormat, prefix);
                    if (uri != null && !namespaceLookup.containsKey(prefix)) {
                        namespaceLookup.put(prefix, uri);
                    }
                }
            }

            appendDecimalFormatAttribute(decimalFormat, sb, "decimal-separator");
            appendDecimalFormatAttribute(decimalFormat, sb, "grouping-separator");
            appendDecimalFormatAttribute(decimalFormat, sb, "zero-digit");
            appendDecimalFormatAttribute(decimalFormat, sb, "digit");
            appendDecimalFormatAttribute(decimalFormat, sb, "minus-sign");
            appendDecimalFormatAttribute(decimalFormat, sb, "percent");
            appendDecimalFormatAttribute(decimalFormat, sb, "per-mille");
            appendDecimalFormatAttribute(decimalFormat, sb, "pattern-separator");
            appendDecimalFormatAttribute(decimalFormat, sb, "exponent-separator");
            appendDecimalFormatAttribute(decimalFormat, sb, "infinity");
            appendDecimalFormatAttribute(decimalFormat, sb, "NaN");

            sb.append(";");
            decimalFormatDeclarations.add(sb.toString());
        }
    }


    private void appendDecimalFormatAttribute(XdmNode decimalFormat, StringBuilder sb, String attributeName) {
        String value = decimalFormat.attribute(attributeName);
        if (value != null) {
            sb.append(" ")
                .append(attributeName)
                .append(" = ")
                .append(toXQueryStringLiteral(value));
        }
    }

    private String toXQueryStringLiteral(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }


    private String resolveNamespaceUri(XdmNode node, String prefix) {
        XdmSequenceIterator<XdmNode> namespaces = node.axisIterator(Axis.NAMESPACE);
        while (namespaces.hasNext()) {
            XdmNode nsNode = namespaces.next();
            if (nsNode.getNodeName() != null) {
                String nsPrefix = nsNode.getNodeName().getLocalName();
                if (prefix.equals(nsPrefix)) {
                    return nsNode.getStringValue();
                }
            }
        }
        return null;
    }


    private void initStaticBaseUri(XdmNode environmentNode) {
        Iterator<XdmNode> staticBaseUri = environmentNode.children("static-base-uri").iterator();
        if (staticBaseUri.hasNext() && "#UNDEFINED".equals(staticBaseUri.next().attribute("uri"))) {
            staticBaseUriUndefined = true;
        }
    }

    public boolean isStaticBaseUriUndefined() {
        return staticBaseUriUndefined;
    }

    private void initResources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> resources = environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = envPath
                .resolve(resource.attribute("file"))
                .toString();
            String uri = resource.attribute("uri");
            resourceLookup.put(uri, file);
        }
    }

    private void initSources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> sources = environmentNode.select(Steps.descendant("source")).asList();
        for (XdmNode source : sources) {
            String file = envPath
                .resolve(source.attribute("file"))
                .toString();
            String uri = source.attribute("uri");
            String role = source.attribute("role");
            if (uri != null && !file.equals(uri)) {
                resourceLookup.put(uri, file);
            }
            if (role != null) {
                roleLookup.put(role, file);
            }
        }
    }

    /**
     * This method takes a query and modifies it such that it executes inside the environment. It adds a context-item
     * declaration, variable declarations and replaces URIs with the right filepaths.
     *
     * @param query contains the query that wants to be executed.
     * @return a String containing the updated query with the context-item, params and resources set.
     */
    public String applyToQuery(String query) {
        // Variables marked declared="true" are declared as external in the test case itself;
        query = bindExternalParams(query);

        StringBuilder declarations = new StringBuilder();
        declarations.append(createDecimalFormatAndNamespaceProlog());
        for (Map.Entry<String, String> r : roleLookup.entrySet()) {
            String role = r.getKey();
            String file = r.getValue();
            if (role.equals(".")) {
                declarations.append("declare context item := doc(\"").append(file).append("\"); ");
            } else {
                declarations.append("declare variable ").append(role).append(" := doc(\"").append(file).append("\"); ");
            }
        }
        for (Map.Entry<String, String> param : paramLookup.entrySet()) {
            String name = param.getKey();
            String select = param.getValue();
            declarations.append("declare variable $").append(name).append(" := ").append(select).append(";");
        }

        String newQueryString = insertDeclarationsIntoQuery(query, declarations.toString());

        for (Map.Entry<String, String> fileLookup : resourceLookup.entrySet()) {
            if (newQueryString.contains(fileLookup.getKey())) {
                newQueryString = newQueryString.replace(fileLookup.getKey(), fileLookup.getValue());
            }
        }
        return newQueryString;
    }


    private String bindExternalParams(String query) {
        String result = query;
        for (Map.Entry<String, String> param : externalParamLookup.entrySet()) {
            result = injectExternalDefault(result, param.getKey(), param.getValue());
        }
        return result;
    }

    private String injectExternalDefault(String query, String name, String select) {
        Pattern pattern = Pattern.compile(
            "(declare\\s+variable\\s+\\$" + Pattern.quote(name) + "(?![\\w.-])[^;]*?\\bexternal\\b)(\\s*:=)?"
        );
        Matcher matcher = pattern.matcher(query);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(2) != null
                ? matcher.group()
                : matcher.group(1) + " := (" + select + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String createDecimalFormatAndNamespaceProlog() {
        if (namespaceLookup.isEmpty() && decimalFormatDeclarations.isEmpty()) {
            return "";
        }

        StringBuilder prolog = new StringBuilder();

        for (Map.Entry<String, String> namespace : namespaceLookup.entrySet()) {
            prolog.append("declare namespace ")
                .append(namespace.getKey())
                .append(" = ")
                .append(toXQueryStringLiteral(namespace.getValue()))
                .append(";\n");
        }

        for (String decimalFormatDeclaration : decimalFormatDeclarations) {
            prolog.append(decimalFormatDeclaration).append("\n");
        }
        return prolog.toString();
    }

    private String insertDeclarationsIntoQuery(String query, String declarations) {
        // Preserve any leading version/import prolog, then inject environment declarations before the body.
        if (declarations.isEmpty()) {
            return query;
        }

        int insertAt = findEnvironmentInsertionPoint(query);
        return query.substring(0, insertAt) + declarations + query.substring(insertAt);
    }

    private int findEnvironmentInsertionPoint(String query) {
        // Injected var/context-item declarations are only valid after the leading prolog declarations
        // that the grammar allows before annotated declarations.
        int position = skipWhitespaceAndComments(query, 0);
        int afterVersionDeclaration = consumeDeclaration(query, position, "xquery version");
        if (afterVersionDeclaration >= 0) {
            position = skipWhitespaceAndComments(query, afterVersionDeclaration);
        }

        int insertAt = position;
        while (true) {
            int afterLeadingPrologDeclaration = consumeLeadingPrologDeclaration(query, position);
            if (afterLeadingPrologDeclaration < 0) {
                return insertAt;
            }
            position = skipWhitespaceAndComments(query, afterLeadingPrologDeclaration);
            insertAt = position;
        }
    }

    private int consumeLeadingPrologDeclaration(String query, int start) {
        // These match the XQuery grammar alternatives that must precede annotated declarations.
        String[] declarationPrefixes = {
            "declare default element namespace",
            "declare default function namespace",
            "declare boundary-space",
            "declare default collation",
            "declare base-uri",
            "declare construction",
            "declare ordering",
            "declare default order empty",
            "declare copy-namespaces",
            "declare decimal-format",
            "declare default decimal-format",
            "declare namespace",
            "import schema",
            "import module"
        };

        for (String declarationPrefix : declarationPrefixes) {
            int declarationEnd = consumeDeclaration(query, start, declarationPrefix);
            if (declarationEnd >= 0) {
                return declarationEnd;
            }
        }
        return -1;
    }

    private int consumeDeclaration(String query, int start, String keyword) {
        // Match a specific prolog keyword and advance past its terminating semicolon.
        if (!query.regionMatches(true, start, keyword, 0, keyword.length())) {
            return -1;
        }
        int declarationEnd = findDeclarationTerminator(query, start + keyword.length());
        if (declarationEnd < 0) {
            return -1;
        }
        return declarationEnd + 1;
    }

    private int findDeclarationTerminator(String query, int start) {
        // Scan until the declaration semicolon, ignoring semicolons inside strings or XQuery comments.
        // `quote` tracks whether we are currently inside a string literal.
        char quote = '\0';
        // XQuery comments can nest, so comment scanning is depth-based rather than boolean.
        int commentDepth = 0;

        for (int i = start; i < query.length(); i++) {
            char current = query.charAt(i);
            char next = i + 1 < query.length() ? query.charAt(i + 1) : '\0';

            if (commentDepth > 0) {
                // Nested comments keep consuming input until the matching ':)' closes the outermost one.
                if (current == '(' && next == ':') {
                    commentDepth++;
                    i++;
                } else if (current == ':' && next == ')') {
                    commentDepth--;
                    i++;
                }
                continue;
            }

            if (quote != '\0') {
                // Everything inside a string literal is ignored until the matching quote character reappears.
                if (current == quote) {
                    quote = '\0';
                }
                continue;
            }

            if (current == '(' && next == ':') {
                commentDepth++;
                i++;
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == ';') {
                return i;
            }
        }

        return -1;
    }

    private int skipWhitespaceAndComments(String query, int start) {
        // Leading trivia is allowed between prolog declarations and should not affect insertion.
        int position = start;
        while (position < query.length()) {
            char current = query.charAt(position);
            char next = position + 1 < query.length() ? query.charAt(position + 1) : '\0';

            if (Character.isWhitespace(current)) {
                position++;
                continue;
            }

            if (current == '(' && next == ':') {
                position = skipComment(query, position + 2);
                continue;
            }

            break;
        }
        return position;
    }

    private int skipComment(String query, int start) {
        // XQuery comments can nest, so track depth until the matching ':)'.
        int depth = 1;
        for (int i = start; i < query.length() - 1; i++) {
            char current = query.charAt(i);
            char next = query.charAt(i + 1);
            if (current == '(' && next == ':') {
                depth++;
                i++;
            } else if (current == ':' && next == ')') {
                depth--;
                i++;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return query.length();
    }
}
