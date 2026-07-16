package evaluation.conversion;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.rumbledb.parser.xquery.XQueryLexer;
import org.rumbledb.parser.xquery.XQueryParser;

/** Parses XQuery source without reporting syntax errors to stderr. */
final class XQueryParsing {

    private XQueryParsing() {
    }

    static XQueryParser.ModuleAndThisIsItContext parseModule(String source) {
        ParseResult result = parse(source);
        return result == null ? null : result.module();
    }

    static XQueryParser.ModuleAndThisIsItContext parseValidModule(String source) {
        ParseResult result = parse(source);
        return result == null || result.hasErrors() ? null : result.module();
    }

    private static ParseResult parse(String source) {
        XQueryLexer lexer = new XQueryLexer(CharStreams.fromString(source));
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
        return new ParseResult(module, lexerErrorListener.hasError() || parser.getNumberOfSyntaxErrors() > 0);
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

    private record ParseResult(XQueryParser.ModuleAndThisIsItContext module, boolean hasErrors) {
    }
}
