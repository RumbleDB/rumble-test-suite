package evaluation.conversion;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Converts XQuery string literals to JSONiq-compatible string literals.
 *
 * <p>
 * Examples:
 * </p>
 *
 * <pre>
 * {@code
 * 'abc'      -> "abc"
 * "a "" b"  -> "a \" b"
 * '\?'      -> "\\?"
 * '\'       -> "\\"
 * }
 * </pre>
 *
 * <p>
 * XQuery escaping rules:
 * </p>
 * <ul>
 * <li>Quotes inside string literals are escaped by doubling them.</li>
 * <li>Backslash has no special meaning in XQuery string literals.</li>
 * <li>XML character and predefined entity references are expanded.</li>
 * </ul>
 *
 * <p>
 * JSONiq escaping rules:
 * </p>
 * <ul>
 * <li>String literals use JSON-style escaping.</li>
 * <li>Backslashes must be escaped.</li>
 * <li>Double quotes must be escaped.</li>
 * </ul>
 */
final class StringLiteralConversion implements ConversionPass {

    private static final String COMMENT_START = "(:";
    private static final String COMMENT_END = ":)";

    @Override
    public String convert(String input) {
        if (!containsQuote(input)) {
            return input;
        }

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

    private static String convertLiteral(String xqueryValue) {
        String decodedValue = StringEscapeUtils.unescapeXml(xqueryValue);
        return "\"" + StringEscapeUtils.escapeJson(decodedValue) + "\"";
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

    private static boolean containsQuote(String input) {
        return input.indexOf('"') >= 0 || input.indexOf('\'') >= 0;
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
