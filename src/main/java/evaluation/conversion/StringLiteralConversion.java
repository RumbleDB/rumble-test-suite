package evaluation.conversion;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Converts XPath and XQuery string literals to JSONiq string literals.
 */
final class StringLiteralConversion implements ConversionPass {

    private static final String COMMENT_START = "(:";
    private static final String COMMENT_END = ":)";

    private final Converter.StringLiteralSemantics semantics;

    StringLiteralConversion(Converter.StringLiteralSemantics semantics) {
        this.semantics = semantics;
    }

    @Override
    public String convert(String input) {
        StringBuilder output = new StringBuilder(input.length());

        int position = 0;
        while (position < input.length()) {
            if (startsWith(input, position, COMMENT_START)) {
                int commentEnd = findCommentEnd(input, position);
                output.append(input, position, commentEnd);
                position = commentEnd;
                continue;
            }

            if (isQuote(input.charAt(position))) {
                ParsedStringLiteral literal = parseXQueryStringLiteral(input, position);
                output.append(convertLiteral(literal.value));
                position = literal.end;
                continue;
            }

            output.append(input.charAt(position));
            position++;
        }

        return output.toString();
    }

    private String convertLiteral(String value) {
        switch (this.semantics) {
            case XQUERY:
                value = StringEscapeUtils.unescapeXml(value);
                break;
            case XPATH:
                // RumbleDB accepts XML references in JSONiq strings. Protect XPath
                // literals so that references remain lexical text after parsing.
                value = value.replace("&", "&amp;");
                break;
            case DEFAULT:
                break;
        }
        return "\"" + escapeJSONiqStringContent(value) + "\"";
    }

    private static String escapeJSONiqStringContent(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int position = 0; position < value.length(); position++) {
            char character = value.charAt(position);
            switch (character) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private static ParsedStringLiteral parseXQueryStringLiteral(String input, int start) {
        char quote = input.charAt(start);
        StringBuilder value = new StringBuilder();
        int position = start + 1;

        while (position < input.length()) {
            char currentChar = input.charAt(position);

            if (currentChar == quote) {
                if (position + 1 < input.length() && input.charAt(position + 1) == quote) {
                    value.append(quote);
                    position += 2;
                    continue;
                }

                return new ParsedStringLiteral(value.toString(), position + 1);
            }

            value.append(currentChar);
            position++;
        }

        throw new IllegalArgumentException("Unterminated string literal starting at position " + start);
    }

    private static boolean isQuote(char character) {
        return character == '"' || character == '\'';
    }

    private static int findCommentEnd(String input, int start) {
        int depth = 1;
        int position = start + COMMENT_START.length();

        while (position < input.length()) {
            if (startsWith(input, position, COMMENT_START)) {
                depth++;
                position += COMMENT_START.length();
                continue;
            }

            if (startsWith(input, position, COMMENT_END)) {
                depth--;
                position += COMMENT_END.length();

                if (depth == 0) {
                    return position;
                }

                continue;
            }

            position++;
        }

        throw new IllegalArgumentException("Unterminated XQuery comment starting at position " + start);
    }

    private static boolean startsWith(String input, int offset, String prefix) {
        return input.regionMatches(offset, prefix, 0, prefix.length());
    }

    private static final class ParsedStringLiteral {

        private final String value;
        private final int end;

        private ParsedStringLiteral(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }
}
