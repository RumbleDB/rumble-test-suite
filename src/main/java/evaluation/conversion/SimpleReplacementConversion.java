package evaluation.conversion;

import java.util.Set;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Adds the explicit {@code fn} prefix to zero-argument calls that conflict with JSONiq syntax. */
final class SimpleReplacementConversion implements ConversionPass {

    private static final Set<String> FUNCTIONS_REQUIRING_PREFIX = Set.of("true", "false", "not");

    @Override
    public void rewrite(ConversionContext context) {
        new BuiltInFunctionVisitor(context).visit(context.module());
    }

    private static final class BuiltInFunctionVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext conversionContext;

        private BuiltInFunctionVisitor(ConversionContext conversionContext) {
            this.conversionContext = conversionContext;
        }

        @Override
        public Void visitFunctionCall(XQueryParser.FunctionCallContext context) {
            String functionName = this.conversionContext.text(context.functionName());
            if (context.argumentList().argument().isEmpty() && FUNCTIONS_REQUIRING_PREFIX.contains(functionName)) {
                this.conversionContext.replace(context.functionName(), "fn:" + functionName);
            }
            return visitChildren(context);
        }
    }
}
