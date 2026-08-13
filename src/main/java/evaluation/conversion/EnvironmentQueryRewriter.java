package evaluation.conversion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Applies an environment to parser-identified parts of an XQuery query. */
public final class EnvironmentQueryRewriter {

    private EnvironmentQueryRewriter() {}

    public static String rewrite(
            String query,
            Map<String, String> environmentNamespaces,
            String declarations,
            Map<String, String> externalParams,
            Map<String, String> resources,
            Map<String, List<String>> moduleLocationHints,
            Map<String, List<String>> schemaLocationHints,
            boolean injectEnvironmentSchemaImports) {
        XQueryParser.ModuleAndThisIsItContext module = XQueryParsing.parseValidModule(query);
        if (module == null) {
            return query;
        }

        ConversionContext context = new ConversionContext(query, module);
        new ExternalParamVisitor(context, externalParams).visit(module);
        new ModuleImportVisitor(context, moduleLocationHints).visit(module);
        new SchemaImportVisitor(context, schemaLocationHints).visit(module);
        insertDeclarations(
                context,
                module,
                environmentSchemaImports(injectEnvironmentSchemaImports ? schemaLocationHints : Map.of(), module)
                        + environmentNamespaceDeclarations(environmentNamespaces, module)
                        + declarations);
        String queryWithDeclarations = context.result();

        // Parse the intermediate query so resource URIs inside injected parameter values are rewritten too.
        XQueryParser.ModuleAndThisIsItContext queryWithDeclarationsModule =
                XQueryParsing.parseValidModule(queryWithDeclarations);
        if (queryWithDeclarationsModule == null) {
            return queryWithDeclarations;
        }

        ConversionContext resourceContext = new ConversionContext(queryWithDeclarations, queryWithDeclarationsModule);
        new ResourceVisitor(resourceContext, resources).visit(queryWithDeclarationsModule);
        return resourceContext.result();
    }

    private static String environmentSchemaImports(
            Map<String, List<String>> schemaLocationHints, XQueryParser.ModuleAndThisIsItContext module) {
        if (schemaLocationHints.isEmpty() || module.module().main == null) {
            return "";
        }

        LinkedHashSet<String> importedNamespaces = new LinkedHashSet<>();
        for (XQueryParser.SchemaImportContext schemaImport :
                module.module().main.prolog().schemaImport()) {
            String namespace = XQueryStringLiteral.parse(schemaImport.nsURI.getText());
            if (namespace != null) {
                importedNamespaces.add(namespace);
            }
        }

        StringBuilder declarations = new StringBuilder();
        for (Map.Entry<String, List<String>> schema : schemaLocationHints.entrySet()) {
            if (importedNamespaces.contains(schema.getKey())) {
                continue;
            }
            List<String> locations = new ArrayList<>();
            for (String location : schema.getValue()) {
                locations.add(XQueryStringLiteral.serialize(location, '"'));
            }
            if (locations.isEmpty()) {
                continue;
            }
            declarations
                    .append("import schema ")
                    .append(XQueryStringLiteral.serialize(schema.getKey(), '"'))
                    .append(" at ")
                    .append(String.join(", ", locations))
                    .append(";\n");
        }
        return declarations.toString();
    }

    private static String environmentNamespaceDeclarations(
            Map<String, String> environmentNamespaces, XQueryParser.ModuleAndThisIsItContext module) {
        if (environmentNamespaces.isEmpty()) {
            return "";
        }

        Map<String, String> queryNamespaces = queryNamespaces(module);
        StringBuilder declarations = new StringBuilder();
        for (Map.Entry<String, String> environmentNamespace : environmentNamespaces.entrySet()) {
            String prefix = environmentNamespace.getKey();
            String uri = environmentNamespace.getValue();
            String queryUri = queryNamespaces.get(prefix);
            if (queryUri == null) {
                appendNamespaceDeclaration(declarations, prefix, uri);
            } else if (!queryUri.equals(uri)) {
                throw new IllegalArgumentException("QT3 environment binds prefix "
                        + prefix
                        + " to "
                        + uri
                        + ", but the query binds it to "
                        + queryUri
                        + ".");
            }
        }
        return declarations.toString();
    }

    private static void appendNamespaceDeclaration(StringBuilder declarations, String prefix, String uri) {
        if (prefix.isEmpty()) {
            declarations.append("declare default element namespace ");
        } else {
            declarations.append("declare namespace ").append(prefix).append(" = ");
        }
        declarations.append(XQueryStringLiteral.serialize(uri, '"')).append(";\n");
    }

    private static Map<String, String> queryNamespaces(XQueryParser.ModuleAndThisIsItContext module) {
        if (module.module().main == null) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        XQueryParser.PrologContext prolog = module.module().main.prolog();
        for (XQueryParser.NamespaceDeclContext namespaceDeclaration : prolog.namespaceDecl()) {
            addNamespace(
                    result,
                    namespaceDeclaration.ncName().getText(),
                    namespaceDeclaration.uriLiteral().getText());
        }
        for (XQueryParser.DefaultNamespaceDeclContext namespaceDeclaration : prolog.defaultNamespaceDecl()) {
            if (namespaceDeclaration.type.getText().equals("element")) {
                addNamespace(result, "", namespaceDeclaration.uri.getText());
            }
        }
        for (XQueryParser.SchemaImportContext schemaImport : prolog.schemaImport()) {
            if (schemaImport.schemaPrefix() == null) {
                continue;
            }
            if (schemaImport.schemaPrefix().ncName() == null) {
                addNamespace(result, "", schemaImport.nsURI.getText());
            } else {
                addNamespace(result, schemaImport.schemaPrefix().ncName().getText(), schemaImport.nsURI.getText());
            }
        }
        for (XQueryParser.ModuleImportContext moduleImport : prolog.moduleImport()) {
            if (moduleImport.ncName() != null) {
                addNamespace(result, moduleImport.ncName().getText(), moduleImport.targetNamespace.getText());
            }
        }
        return result;
    }

    private static void addNamespace(Map<String, String> namespaces, String prefix, String uriLiteral) {
        String uri = XQueryStringLiteral.parse(uriLiteral);
        if (uri != null) {
            namespaces.put(prefix, uri);
        }
    }

    private static void insertDeclarations(
            ConversionContext context, XQueryParser.ModuleAndThisIsItContext module, String declarations) {
        if (declarations.isEmpty() || module.module().main == null) {
            return;
        }

        XQueryParser.MainModuleContext mainModule = module.module().main;
        Token insertionToken;
        if (!mainModule.prolog().annotatedDecl().isEmpty()) {
            insertionToken = mainModule.prolog().annotatedDecl(0).getStart();
        } else {
            insertionToken = mainModule.program().getStart();
        }
        context.insertBefore(insertionToken, declarations);
    }

    private static final class ExternalParamVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext context;
        private final Map<String, String> externalParams;

        private ExternalParamVisitor(ConversionContext context, Map<String, String> externalParams) {
            this.context = context;
            this.externalParams = externalParams;
        }

        @Override
        public Void visitVarDecl(XQueryParser.VarDeclContext varDecl) {
            String name = this.context.text(varDecl.varBinding().eqName());
            String defaultValue = this.externalParams.get(name);
            if (defaultValue != null && varDecl.external != null && varDecl.COLON_EQ() == null) {
                this.context.insertAfter(varDecl.external, " := (" + defaultValue + ")");
            }
            return visitChildren(varDecl);
        }
    }

    private static final class ResourceVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext context;
        private final Map<String, String> resources;

        private ResourceVisitor(ConversionContext context, Map<String, String> resources) {
            this.context = context;
            this.resources = resources;
        }

        @Override
        public Void visitStringLiteral(XQueryParser.StringLiteralContext stringLiteral) {
            String source = this.context.text(stringLiteral);
            String value = XQueryStringLiteral.parse(source);
            String replacement = this.resources.get(value);
            if (replacement != null) {
                this.context.replace(stringLiteral, XQueryStringLiteral.serialize(replacement, source.charAt(0)));
            }
            return null;
        }
    }

    private static final class ModuleImportVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext context;
        private final Map<String, List<String>> moduleLocationHints;

        private ModuleImportVisitor(ConversionContext context, Map<String, List<String>> moduleLocationHints) {
            this.context = context;
            this.moduleLocationHints = moduleLocationHints;
        }

        @Override
        public Void visitModuleImport(XQueryParser.ModuleImportContext moduleImport) {
            String source = this.context.text(moduleImport.targetNamespace);
            String namespace = XQueryStringLiteral.parse(source);
            if (namespace == null) {
                return null;
            }
            List<String> environmentLocations = this.moduleLocationHints.get(namespace);
            if (environmentLocations == null || environmentLocations.isEmpty()) {
                return null;
            }

            LinkedHashSet<String> mergedLocations = new LinkedHashSet<>();
            for (XQueryParser.UriLiteralContext location : moduleImport.locations) {
                mergedLocations.add(XQueryStringLiteral.parse(this.context.text(location)));
            }
            mergedLocations.addAll(environmentLocations);

            if (mergedLocations.isEmpty()) {
                return null;
            }

            List<String> serializedLocations = new ArrayList<>();
            for (String location : mergedLocations) {
                if (location != null) {
                    serializedLocations.add(XQueryStringLiteral.serialize(location, '"'));
                }
            }
            if (serializedLocations.isEmpty()) {
                return null;
            }

            StringBuilder replacement = new StringBuilder("import module ");
            if (moduleImport.ncName() != null) {
                replacement
                        .append("namespace ")
                        .append(moduleImport.ncName().getText())
                        .append("=")
                        .append(source);
            } else {
                replacement.append(source);
            }
            replacement
                    .append(" at ")
                    .append(String.join(", ", serializedLocations))
                    .append(";");
            this.context.replace(moduleImport, replacement.toString());
            return null;
        }
    }

    private static final class SchemaImportVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext context;
        private final Map<String, List<String>> schemaLocationHints;

        private SchemaImportVisitor(ConversionContext context, Map<String, List<String>> schemaLocationHints) {
            this.context = context;
            this.schemaLocationHints = schemaLocationHints;
        }

        @Override
        public Void visitSchemaImport(XQueryParser.SchemaImportContext schemaImport) {
            String source = this.context.text(schemaImport.nsURI);
            String namespace = XQueryStringLiteral.parse(source);
            List<String> environmentLocations = this.schemaLocationHints.get(namespace);
            if (environmentLocations == null || environmentLocations.isEmpty()) {
                return null;
            }

            List<String> serializedLocations = new ArrayList<>();
            for (String location : environmentLocations) {
                serializedLocations.add(XQueryStringLiteral.serialize(location, '"'));
            }

            StringBuilder replacement = new StringBuilder("import schema ");
            if (schemaImport.schemaPrefix() != null) {
                replacement
                        .append(this.context.text(schemaImport.schemaPrefix()))
                        .append(" ");
            }
            replacement
                    .append(source)
                    .append(" at ")
                    .append(String.join(", ", serializedLocations))
                    .append(";");
            this.context.replace(schemaImport, replacement.toString());
            return null;
        }
    }
}
