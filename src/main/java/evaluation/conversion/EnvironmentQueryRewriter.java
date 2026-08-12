package evaluation.conversion;

import java.util.ArrayList;
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
            String declarations,
            Map<String, String> externalParams,
            Map<String, String> resources,
            Map<String, List<String>> moduleLocationHints) {
        XQueryParser.ModuleAndThisIsItContext module = XQueryParsing.parseValidModule(query);
        if (module == null) {
            return query;
        }

        ConversionContext context = new ConversionContext(query, module);
        new ExternalParamVisitor(context, externalParams).visit(module);
        new ModuleImportVisitor(context, moduleLocationHints).visit(module);
        insertDeclarations(context, module, declarations);
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
            replacement.append(" at ").append(String.join(", ", serializedLocations));
            this.context.replace(moduleImport, replacement.toString());
            return null;
        }
    }
}
