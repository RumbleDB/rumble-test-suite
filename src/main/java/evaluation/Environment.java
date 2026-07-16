package evaluation;

import evaluation.conversion.EnvironmentQueryRewriter;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.streams.Steps;
import org.rumbledb.resources.ResourceResolver;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

public class Environment {
    private final Map<String, String> runtimeResourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();
    private final Map<String, String> externalParamLookup = new HashMap<>();
    private final Map<String, String> roleLookup = new HashMap<>();
    private final Map<URI, URI> importResourceLookup = new HashMap<>();

    private final Map<String, String> namespaceLookup = new HashMap<>();


    private final List<String> decimalFormatDeclarations = new ArrayList<>();

    private boolean staticBaseUriUndefined = false;
    private String staticBaseUri = null;

    public Environment(XdmNode environmentNode, Path envPath) {
        initParams(environmentNode);
        initNamespaces(environmentNode);
        initDecimalFormats(environmentNode);
        initStaticBaseUri(environmentNode);
        initResources(environmentNode, envPath);
        initSources(environmentNode, envPath);
        addImportResources(collectImportResources(environmentNode, envPath));
    }

    private Environment() {
    }

    private Environment(Environment environment) {
        this.runtimeResourceLookup.putAll(environment.runtimeResourceLookup);
        this.paramLookup.putAll(environment.paramLookup);
        this.externalParamLookup.putAll(environment.externalParamLookup);
        this.roleLookup.putAll(environment.roleLookup);
        this.importResourceLookup.putAll(environment.importResourceLookup);
        this.namespaceLookup.putAll(environment.namespaceLookup);
        this.decimalFormatDeclarations.addAll(environment.decimalFormatDeclarations);
        this.staticBaseUriUndefined = environment.staticBaseUriUndefined;
        this.staticBaseUri = environment.staticBaseUri;
    }

    public static Environment forTestCase(
            Environment environment,
            XdmNode testCase,
            Path testSetDirectory
    ) {
        Map<URI, URI> imports = collectImportResources(testCase, testSetDirectory);
        if (imports.isEmpty()) {
            return environment;
        }

        Environment result = environment == null ? new Environment() : new Environment(environment);
        result.addImportResources(imports);
        return result;
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
        Iterator<XdmNode> staticBaseUriNodes = environmentNode.children("static-base-uri").iterator();
        if (staticBaseUriNodes.hasNext()) {
            String uri = staticBaseUriNodes.next().attribute("uri");
            if ("#UNDEFINED".equals(uri)) {
                staticBaseUriUndefined = true;
            } else {
                staticBaseUri = uri;
            }
        }
    }

    public boolean isStaticBaseUriUndefined() {
        return staticBaseUriUndefined;
    }

    public String getStaticBaseUri() {
        return staticBaseUri;
    }

    private void initResources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> resources = environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = envPath
                .resolve(resource.attribute("file"))
                .toUri()
                .toString();
            String uri = resource.attribute("uri");
            runtimeResourceLookup.put(uri, file);
        }
    }

    private void initSources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> sources = environmentNode.select(Steps.descendant("source")).asList();
        for (XdmNode source : sources) {
            String file = envPath
                .resolve(source.attribute("file"))
                .toUri()
                .toString();
            String uri = source.attribute("uri");
            String role = source.attribute("role");
            if (uri != null && !file.equals(uri)) {
                runtimeResourceLookup.put(uri, file);
            }
            if (role != null) {
                roleLookup.put(role, file);
            }
        }
    }

    private void addImportResources(Map<URI, URI> imports) {
        // The compiler currently supports one physical location per logical URI.
        imports.forEach(importResourceLookup::putIfAbsent);
    }

    private static Map<URI, URI> collectImportResources(XdmNode node, Path basePath) {
        Map<URI, URI> imports = new HashMap<>();
        for (String elementName : List.of("module", "schema")) {
            for (XdmNode resource : node.select(Steps.descendant(elementName)).asList()) {
                String uri = resource.attribute("uri");
                String file = resource.attribute("file");
                URI logicalUri = parseLogicalUri(uri);
                if (logicalUri != null && file != null) {
                    imports.putIfAbsent(logicalUri, basePath.resolve(file).toUri());
                }
            }
        }
        return imports;
    }

    private static URI parseLogicalUri(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException ignored) {
            // Negative tests can deliberately declare malformed logical URIs.
            return null;
        }
    }

    public ResourceResolver getResourceResolver() {
        return new ResourceResolver(importResourceLookup);
    }

    /**
     * This method takes a query and modifies it such that it executes inside the environment. It adds a context-item
     * declaration, variable declarations and replaces URIs with the right filepaths.
     *
     * @param query contains the query that wants to be executed.
     * @return a String containing the updated query with the context-item, params and resources set.
     */
    public String applyToQuery(String query) {
        return EnvironmentQueryRewriter.rewrite(
            query,
            createDeclarations(),
            externalParamLookup,
            runtimeResourceLookup
        );
    }

    private String createDeclarations() {
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
        return declarations.toString();
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

}
