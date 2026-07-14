package evaluation.conversion;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Converts XQuery context-item expressions ({@code .}) to their JSONiq equivalent ({@code $$}). */
final class ContextItemConversion implements ConversionPass {

    @Override
    public void rewrite(ConversionContext context) {
        new ContextItemVisitor(context).visit(context.module());
    }

    private static final class ContextItemVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext conversionContext;

        private ContextItemVisitor(ConversionContext conversionContext) {
            this.conversionContext = conversionContext;
        }

        @Override
        public Void visitContextItemExpr(XQueryParser.ContextItemExprContext context) {
            this.conversionContext.replace(context.DOT().getSymbol(), "$$");
            return null;
        }
    }

}
