package evaluation.conversion;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/**
 * Unwraps XQuery XML Schema validate expressions for JSONiq compatibility runs.
 *
 * <p>
 * JSONiq's similarly named {@code validate type} expression validates a sequence type and therefore cannot
 * represent XQuery's strict, lax, or named-schema-type validation semantics. RumbleDB does not currently execute XML
 * Schema validate expressions, so the compatibility conversion retains the operand without reinterpreting it.
 *
 * Note: this is a temporary solution until JSONiq parser and runtime support for XML Schema validate expressions is
 * implemented.
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
            this.conversionContext.replace(context.getStart(), context.LBRACE().getSymbol(), "");
            this.conversionContext.replace(context.RBRACE().getSymbol(), "");
            return visitChildren(context);
        }
    }
}
