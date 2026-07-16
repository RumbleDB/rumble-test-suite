package evaluation.conversion;

import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.rumbledb.parser.xquery.XQueryLexer;
import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Applies an environment to parser-identified parts of an XQuery query. */
public final class EnvironmentQueryRewriter {

    private EnvironmentQueryRewriter() {
    }

    public static String rewrite(
            String query,
            String declarations,
            Map<String, String> externalParams,
            Map<String, String> resources
    ) {
        XQueryParser.ModuleAndThisIsItContext module = parse(query);
        if (module == null) {
            return query;
        }

        ConversionContext context = new ConversionContext(query, module);
        new ExternalParamVisitor(context, externalParams).visit(module);
        insertDeclarations(context, module, declarations);
        String queryWithDeclarations = context.result();

        // Parse the intermediate query so resource URIs inside injected parameter values are rewritten too.
        XQueryParser.ModuleAndThisIsItContext queryWithDeclarationsModule = parse(queryWithDeclarations);
        if (queryWithDeclarationsModule == null) {
            return queryWithDeclarations;
        }

        ConversionContext resourceContext = new ConversionContext(queryWithDeclarations, queryWithDeclarationsModule);
        new ResourceVisitor(resourceContext, resources).visit(queryWithDeclarationsModule);
        return resourceContext.result();
    }

    private static XQueryParser.ModuleAndThisIsItContext parse(String query) {
        XQueryLexer lexer = new XQueryLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();
        ParseErrorListener lexerErrorListener = new ParseErrorListener();
        lexer.addErrorListener(lexerErrorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XQueryParser parser = new XQueryParser(tokens);
        parser.removeErrorListeners();

        XQueryParser.ModuleAndThisIsItContext module;
        try {
            module = parser.moduleAndThisIsIt();
        } catch (ParseCancellationException exception) {
            return null;
        }
        return !lexerErrorListener.hasError() && parser.getNumberOfSyntaxErrors() == 0 ? module : null;
    }

    private static void insertDeclarations(
            ConversionContext context,
            XQueryParser.ModuleAndThisIsItContext module,
            String declarations
    ) {
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
            String name = this.context.text(varDecl.varRef().eqName());
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

    private static final class ParseErrorListener extends BaseErrorListener {

        private boolean hasError;

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception
        ) {
            this.hasError = true;
        }

        private boolean hasError() {
            return this.hasError;
        }
    }
}
