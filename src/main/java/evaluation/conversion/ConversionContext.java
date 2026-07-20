package evaluation.conversion;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.rumbledb.parser.xquery.XQueryParser;

/** Shared parse tree and source editor for one XQuery-to-JSONiq conversion. */
final class ConversionContext {

    private final XQueryParser.ModuleAndThisIsItContext module;
    private final SourceTextRewriter rewriter;

    ConversionContext(String source, XQueryParser.ModuleAndThisIsItContext module) {
        this.module = module;
        this.rewriter = new SourceTextRewriter(source);
    }

    XQueryParser.ModuleAndThisIsItContext module() {
        return this.module;
    }

    String text(ParserRuleContext context) {
        return this.rewriter.text(context);
    }

    void replace(Token token, String replacement) {
        this.rewriter.replace(token, replacement);
    }

    void replace(ParserRuleContext context, String replacement) {
        this.rewriter.replace(context, replacement);
    }

    void insertBefore(Token token, String text) {
        this.rewriter.insertBefore(token, text);
    }

    void insertAfter(Token token, String text) {
        this.rewriter.insertAfter(token, text);
    }

    String result() {
        return this.rewriter.result();
    }
}
