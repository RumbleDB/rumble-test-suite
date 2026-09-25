package evaluation.conversion;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/**
 * Converts XQuery validate expressions for JSONiq compatibility runs.
 *
 * <p>
 * JSONiq supports XML Schema validation with {@code validate}, {@code validate strict}, and {@code validate lax}.
 * Its {@code validate type} expression instead validates a sequence type, so XQuery's named-schema-type form is
 * unwrapped rather than reinterpreted.
 */
final class ValidateExpressionConversion implements ConversionPass {

    @Override
    public void rewrite(ConversionContext context) {
        new ValidateExpressionVisitor(context).visit(context.module());
    }

    private static final class ValidateExpressionVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext conversionContext;

        private ValidateExpressionVisitor(ConversionContext conversionContext) {
            this.conversionContext = conversionContext;
        }

        @Override
        public Void visitValidateExpr(XQueryParser.ValidateExprContext context) {
            // JSONiq executes validate/strict/lax as XML Schema validation, so keep those forms and
            // their typed-node results. XQuery's "validate type T" names a schema type, whereas
            // JSONiq's form takes a sequence type; retain only its operand to avoid changing meaning.
            if (context.KW_TYPE() != null) {
                this.conversionContext.replace(context.getStart(), context.LBRACE().getSymbol(), "");
                this.conversionContext.replace(context.RBRACE().getSymbol(), "");
            }
            return visitChildren(context);
        }
    }
}
