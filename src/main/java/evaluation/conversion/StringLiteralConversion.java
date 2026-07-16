package evaluation.conversion;

import org.rumbledb.parser.xquery.XQueryParser;
import org.rumbledb.parser.xquery.XQueryParserBaseVisitor;

/** Converts XQuery string literals to JSONiq-compatible string literals. */
final class StringLiteralConversion implements ConversionPass {

    @Override
    public void rewrite(ConversionContext context) {
        new StringLiteralVisitor(context).visit(context.module());
    }

    private static String toJSONiqStringLiteral(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2);
        output.append('"');

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\':
                    output.append("\\\\");
                    break;
                case '"':
                    output.append("\\\"");
                    break;
                case '\b':
                    output.append("\\b");
                    break;
                case '\f':
                    output.append("\\f");
                    break;
                case '\n':
                    output.append("\\n");
                    break;
                case '\r':
                    output.append("\\r");
                    break;
                case '\t':
                    output.append("\\t");
                    break;
                default:
                    appendJSONiqCharacter(output, current);
                    break;
            }
        }

        output.append('"');
        return output.toString();
    }

    private static void appendJSONiqCharacter(StringBuilder output, char character) {
        if (character >= 0x20) {
            output.append(character);
            return;
        }

        output.append("\\u");
        String hex = Integer.toHexString(character);
        for (int i = hex.length(); i < 4; i++) {
            output.append('0');
        }
        output.append(hex);
    }

    private static final class StringLiteralVisitor extends XQueryParserBaseVisitor<Void> {

        private final ConversionContext conversionContext;

        private StringLiteralVisitor(ConversionContext conversionContext) {
            this.conversionContext = conversionContext;
        }

        @Override
        public Void visitStringLiteral(XQueryParser.StringLiteralContext context) {
            String value = XQueryStringLiteral.parse(this.conversionContext.text(context));
            if (value != null) {
                this.conversionContext.replace(context, toJSONiqStringLiteral(value));
            }
            return null;
        }
    }
}
