package evaluation;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import evaluation.conversion.EnvironmentQueryRewriter;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.streams.Steps;

import org.rumbledb.resources.ResourceResolver;

public class Environment {
    private final Map<String, String> runtimeResourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();
    private final Map<String, String> externalParamLookup = new HashMap<>();
    private final Map<String, SourceBinding> roleLookup = new LinkedHashMap<>();
    private final Map<URI, URI> importResourceLookup = new HashMap<>();
    private final Map<String, List<String>> moduleLocationHints = new HashMap<>();
    private final Map<String, List<String>> schemaLocationHints = new LinkedHashMap<>();

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

    private Environment() {}

    private Environment(Environment environment) {
        this.runtimeResourceLookup.putAll(environment.runtimeResourceLookup);
        this.paramLookup.putAll(environment.paramLookup);
        this.externalParamLookup.putAll(environment.externalParamLookup);
        this.roleLookup.putAll(environment.roleLookup);
        this.importResourceLookup.putAll(environment.importResourceLookup);
        environment.moduleLocationHints.forEach((namespace, locations) -> {
            this.moduleLocationHints.put(namespace, new ArrayList<>(locations));
        });
        environment.schemaLocationHints.forEach((namespace, locations) -> {
            this.schemaLocationHints.put(namespace, new ArrayList<>(locations));
        });
        this.namespaceLookup.putAll(environment.namespaceLookup);
        this.decimalFormatDeclarations.addAll(environment.decimalFormatDeclarations);
        this.staticBaseUriUndefined = environment.staticBaseUriUndefined;
        this.staticBaseUri = environment.staticBaseUri;
    }

    public static Environment forTestCase(Environment environment, XdmNode testCase, Path testSetDirectory) {
        ImportResources imports = collectImportResources(testCase, testSetDirectory);
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
            sb.append(" ").append(attributeName).append(" = ").append(toXQueryStringLiteral(value));
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
        Iterator<XdmNode> staticBaseUriNodes =
                environmentNode.children("static-base-uri").iterator();
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
        List<XdmNode> resources =
                environmentNode.select(Steps.descendant("resource")).asList();
        for (XdmNode resource : resources) {
            String file = envPath.resolve(resource.attribute("file")).toUri().toString();
            String uri = resource.attribute("uri");
            runtimeResourceLookup.put(uri, file);
        }
    }

    private void initSources(XdmNode environmentNode, Path envPath) {
        List<XdmNode> sources =
                environmentNode.select(Steps.descendant("source")).asList();
        for (XdmNode source : sources) {
            String file = envPath.resolve(source.attribute("file")).toUri().toString();
            String uri = source.attribute("uri");
            String role = source.attribute("role");
            String validation = source.attribute("validation");
            if (uri != null && !file.equals(uri)) {
                runtimeResourceLookup.put(uri, file);
            }
            if (role != null) {
                roleLookup.put(role, new SourceBinding(file, validation));
            }
        }
    }

    private void addImportResources(ImportResources imports) {
        // The compiler currently supports one physical location per logical URI.
        imports.logicalToPhysical().forEach(importResourceLookup::putIfAbsent);
        mergeLocationHints(this.moduleLocationHints, imports.moduleLocationHints());
        mergeLocationHints(this.schemaLocationHints, imports.schemaLocationHints());
    }

    private void mergeLocationHints(
            Map<String, List<String>> target, Map<String, List<String>> additionalLocationHints) {
        additionalLocationHints.forEach((namespace, locations) -> {
            List<String> knownLocations = target.computeIfAbsent(namespace, ignored -> new ArrayList<>());
            for (String location : locations) {
                if (!knownLocations.contains(location)) {
                    knownLocations.add(location);
                }
            }
        });
    }

    private static ImportResources collectImportResources(XdmNode node, Path basePath) {
        Map<URI, URI> imports = new HashMap<>();
        Map<String, List<String>> moduleLocationHints = new HashMap<>();
        Map<String, List<String>> schemaLocationHints = new LinkedHashMap<>();
        for (String elementName : List.of("module", "schema")) {
            for (XdmNode resource : node.select(Steps.descendant(elementName)).asList()) {
                String uri = resource.attribute("uri");
                String file = resource.attribute("file");
                if ("module".equals(elementName) && uri != null && file != null) {
                    moduleLocationHints
                            .computeIfAbsent(uri, ignored -> new ArrayList<>())
                            .add(basePath.resolve(file).toUri().toString());
                }
                if ("schema".equals(elementName) && file != null) {
                    schemaLocationHints
                            .computeIfAbsent(uri == null ? "" : uri, ignored -> new ArrayList<>())
                            .add(basePath.resolve(file).toUri().toString());
                }
                URI logicalUri = parseLogicalUri(uri);
                if (logicalUri != null && file != null) {
                    imports.putIfAbsent(logicalUri, basePath.resolve(file).toUri());
                }
            }
        }
        return new ImportResources(imports, moduleLocationHints, schemaLocationHints);
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
                this.namespaceLookup,
                createDeclarations(),
                this.externalParamLookup,
                this.runtimeResourceLookup,
                this.moduleLocationHints,
                hasSchemaValidatedSource() ? this.schemaLocationHints : Map.of());
    }

    private boolean hasSchemaValidatedSource() {
        return this.roleLookup.values().stream().anyMatch(SourceBinding::requiresSchemaValidation);
    }

    private String createDeclarations() {
        StringBuilder declarations = new StringBuilder();
        declarations.append(createDecimalFormatProlog());
        for (Map.Entry<String, SourceBinding> r : roleLookup.entrySet()) {
            String role = r.getKey();
            SourceBinding source = r.getValue();
            if (role.equals(".")) {
                declarations
                        .append("declare context item := ")
                        .append(source.documentExpression())
                        .append("; ");
            } else {
                declarations
                        .append("declare variable ")
                        .append(role)
                        .append(" := ")
                        .append(source.documentExpression())
                        .append("; ");
            }
        }
        for (Map.Entry<String, String> param : paramLookup.entrySet()) {
            String name = param.getKey();
            String select = param.getValue();
            declarations
                    .append("declare variable $")
                    .append(name)
                    .append(" := ")
                    .append(select)
                    .append(";");
        }
        return declarations.toString();
    }

    private String createDecimalFormatProlog() {
        StringBuilder prolog = new StringBuilder();
        for (String decimalFormatDeclaration : this.decimalFormatDeclarations) {
            prolog.append(decimalFormatDeclaration).append("\n");
        }
        return prolog.toString();
    }

    private record ImportResources(
            Map<URI, URI> logicalToPhysical,
            Map<String, List<String>> moduleLocationHints,
            Map<String, List<String>> schemaLocationHints) {
        private boolean isEmpty() {
            return this.logicalToPhysical.isEmpty()
                    && this.moduleLocationHints.isEmpty()
                    && this.schemaLocationHints.isEmpty();
        }
    }

    private record SourceBinding(String file, String validation) {
        private boolean requiresSchemaValidation() {
            return "strict".equals(this.validation) || "lax".equals(this.validation);
        }

        private String documentExpression() {
            String document = "doc(\"" + file + "\")";
            if (requiresSchemaValidation()) {
                return "validate " + validation + " { " + document + " }";
            }
            return document;
        }
    }
}
