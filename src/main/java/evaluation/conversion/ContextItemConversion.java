package evaluation.conversion;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.rumbledb.parser.xquery.XQueryLexer;
import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Converts XQuery context-item expressions ({@code .}) to their JSONiq equivalent ({@code $$}). */
final class ContextItemConversion implements ConversionPass {

    @Override
    public String convert(String input) {
        XQueryLexer lexer = new XQueryLexer(CharStreams.fromString(input));
        lexer.removeErrorListeners();

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XQueryParser parser = new XQueryParser(tokens);
        parser.removeErrorListeners();

        XQueryParser.ModuleAndThisIsItContext module;
        try {
            module = parser.moduleAndThisIsIt();
        } catch (ParseCancellationException exception) {
            return input;
        }

        SourceTextRewriter rewriter = new SourceTextRewriter(input);
        new ContextItemVisitor(rewriter).visit(module);
        return rewriter.result();
    }

    private static final class ContextItemVisitor extends XQueryParserBaseVisitor<Void> {

        private final SourceTextRewriter rewriter;

        private ContextItemVisitor(SourceTextRewriter rewriter) {
            this.rewriter = rewriter;
        }

        @Override
        public Void visitContextItemExpr(XQueryParser.ContextItemExprContext context) {
            this.rewriter.replace(context.DOT().getSymbol(), "$$");
            return null;
        }
    }

}
