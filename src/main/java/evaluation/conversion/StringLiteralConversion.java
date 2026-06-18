package evaluation.conversion;

/**
 * Converts XQuery string literals to JSONiq-compatible string literals.
 *
 * <p>
 * XQuery treats backslashes as literal characters, while JSONiq treats them as escape characters.
 * Therefore, we escape all backslashes globally to ensure the JSONiq parser evaluates them correctly.
 * </p>
 * <p>
 * Other conversions (like XML entities and doubled-quote escaping) have been removed,
 * as the JSONiq grammar has been updated to natively support them.
 * </p>
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
                output.append(literal.value.replace("\\", "\\\\"));
                position = literal.end;
                continue;
            }

            output.append(input.charAt(position));
            position++;
        }

        return output.toString();
    }

    private static ParsedStringLiteral parseXQueryStringLiteral(String input, int start) {
        char quote = input.charAt(start);
        int position = start + 1;
        
        while (position < input.length()) {
            char currentChar = input.charAt(position);

            if (currentChar == quote) {
                if (position + 1 < input.length() && input.charAt(position + 1) == quote) {
                    position += 2;
                    continue;
                }

                return new ParsedStringLiteral(input.substring(start, position + 1), position + 1);
            }

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
