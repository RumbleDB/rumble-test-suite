package evaluation.conversion;

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

    @Override
    public String convert(String input) {
        if (input.indexOf('"') < 0 && input.indexOf('\'') < 0) {
            return input;
        }

        StringBuilder output = new StringBuilder(input.length());

        int i = 0;
        while (i < input.length()) {
            // We skip comments
            if (startsWith(input, i, "(:")) {
                int end = skipComment(input, i);
                output.append(input, i, end);
                i = end;
                continue;
            }

            char currentChar = input.charAt(i);

            // Strings
            if (currentChar == '"' || currentChar == '\'') {
                ParsedStringLiteral stringLiteral = parseXQueryStringLiteral(input, i);
                output.append(toJSONiqStringLiteral(stringLiteral.value));
                i = stringLiteral.end;
                continue;
            }

            output.append(currentChar);
            i++;
        }

        return output.toString();
    }

    private static ParsedStringLiteral parseXQueryStringLiteral(String input, int start) {
        char quote = input.charAt(start);
        StringBuilder value = new StringBuilder();

        int i = start + 1;
        while (i < input.length()) {
            char currentChar = input.charAt(i);

            if (currentChar == quote) {
                // Keep going if the quote is doubled (escaped)
                if (i + 1 < input.length() && input.charAt(i + 1) == quote) {
                    value.append(quote);
                    i += 2;
                    continue;
                }

                return new ParsedStringLiteral(value.toString(), i + 1);
            }

            value.append(currentChar);
            i++;
        }

        throw new IllegalArgumentException("Unterminated string literal starting at position " + start);
    }

    private static String toJSONiqStringLiteral(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2);

        output.append('"');

        for (int i = 0; i < value.length(); i++) {
            char currentChar = value.charAt(i);

            switch (currentChar) {
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
                    if (currentChar < 0x20) {
                        output.append("\\u");

                        String hex = Integer.toHexString(currentChar);
                        for (int j = hex.length(); j < 4; j++) {
                            output.append('0');
                        }

                        output.append(hex);
                    } else {
                        output.append(currentChar);
                    }
                    break;
            }
        }

        output.append('"');

        return output.toString();
    }

    private static int skipComment(String input, int start) {
        int depth = 1;
        int i = start + 2;

        while (i < input.length()) {
            if (startsWith(input, i, "(:")) {
                depth++;
                i += 2;
                continue;
            }

            if (startsWith(input, i, ":)")) {
                depth--;
                i += 2;

                if (depth == 0) {
                    return i;
                }

                continue;
            }

            i++;
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
