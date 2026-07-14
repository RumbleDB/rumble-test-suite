package evaluation.conversion;

import java.util.Set;

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

    // Temporary heuristic until conversion is driven by the XQuery parse tree.
    // These keywords require another expression, so a following "<name" starts
    // a direct constructor rather than a less-than comparison.
    private static final Set<String> DIRECT_CONSTRUCTOR_PREFIX_KEYWORDS = Set.of(
        "return",
        "then",
        "else",
        "in",
        "satisfies",
        "case",
        "default",
        "and",
        "or",
        "div",
        "idiv",
        "mod",
        "to",
        "union",
        "intersect",
        "except",
        "eq",
        "ne",
        "lt",
        "le",
        "gt",
        "ge",
        "is",
        "before",
        "after",
        "of",
        "as",
        "where",
        "by"
    );

    @Override
    public String convert(String input) {
        if (input.indexOf('"') < 0 && input.indexOf('\'') < 0) {
            return input;
        }

        StringBuilder output = new StringBuilder(input.length());

        int i = 0;
        boolean inDirectElementStartTag = false;
        while (i < input.length()) {
            // We skip comments
            if (startsWith(input, i, "(:")) {
                int end = skipComment(input, i);
                output.append(input, i, end);
                i = end;
                continue;
            }

            // Check if we are at the start of a direct element constructor.
            // In this mode, attribute values are preserved while strings inside
            // enclosed expressions are still converted.
            if (!inDirectElementStartTag && isDirectElementStart(input, i)) {
                inDirectElementStartTag = true;
                output.append('<');
                i++;
                continue;
            }

            char currentChar = input.charAt(i);

            if (inDirectElementStartTag && currentChar == '>') {
                inDirectElementStartTag = false;
                output.append(currentChar);
                i++;
                continue;
            }

            // Attribute values in direct constructors follow XML/XQuery escaping
            // rules. Preserve their text while still converting strings inside
            // enclosed expressions.
            if (inDirectElementStartTag && (currentChar == '"' || currentChar == '\'')) {
                i = convertDirectAttributeValue(input, i, output);
                continue;
            }

            // Genuine XQuery string literals.
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

    /**
     * Detects if the character at the given offset is the start of a direct element constructor.
     *
     * Unfortunately, this is a heuristic and may not be 100% accurate because we are not using a parser here.
     *
     */
    private static boolean isDirectElementStart(String input, int offset) {
        if (input.charAt(offset) != '<' || offset + 1 >= input.length()) {
            // If current char is not '<' or there is no next char, it cannot be a direct element start.
            return false;
        }

        char next = input.charAt(offset + 1);
        if (!(next == '_' || Character.isLetter(next))) {
            // If the next char is not a letter or '_', it cannot be a direct element start.
            return false;
        }

        // Avoid treating the common no-whitespace comparison form ($x<y) as
        // a direct constructor. The parser remains the authority for rarer
        // ambiguous cases.
        int previous = offset - 1;
        while (previous >= 0 && Character.isWhitespace(input.charAt(previous))) {
            previous--;
        }
        if (previous < 0) {
            // If there's no character before '<', it can be a direct element start.
            return true;
        }

        char previousChar = input.charAt(previous);
        if (Character.isLetter(previousChar)) {
            // If the previous character is a letter, check if it forms a keyword with the preceding characters.
            int wordStart = previous;
            while (wordStart > 0 && Character.isLetter(input.charAt(wordStart - 1))) {
                wordStart--;
            }

            // If the preceding word is a keyword in DIRECT_CONSTRUCTOR_PREFIX_KEYWORDS, then treat it as a direct
            // constructor.
            if (DIRECT_CONSTRUCTOR_PREFIX_KEYWORDS.contains(input.substring(wordStart, previous + 1))) {
                return true;
            }
        }

        // If the previous character is not a letter, digit, underscore, or one of the specified characters, then treat
        // it as a direct constructor.
        return !(previousChar == '$'
            || previousChar == ')'
            || previousChar == ']'
            || previousChar == '\''
            || previousChar == '"'
            || Character.isLetterOrDigit(previousChar)
            || previousChar == '_');
    }

    /**
     * Copies one direct XML attribute value to {@code output}, preserving its delimiters and static XML content while
     * recursively converting XQuery string literals inside enclosed expressions. Returns the input position directly
     * after the closing attribute delimiter.
     * 
     * For example, `<e attr="static {concat('\path', 'x')} text"/>` should become `<e attr="static {concat("\\path",
     * "x")} text"/>` (adding JSONiq escaping to the string literals inside the enclosed expression).
     */
    private int convertDirectAttributeValue(String input, int start, StringBuilder output) {
        char delimiter = input.charAt(start);
        output.append(delimiter);
        int i = start + 1;

        while (i < input.length()) {
            char currentChar = input.charAt(i);
            if (currentChar == delimiter) {
                if (i + 1 < input.length() && input.charAt(i + 1) == delimiter) {
                    // If the delimiter is doubled, it's an escaped delimiter, so we append it and continue.
                    output.append(delimiter).append(delimiter);
                    i += 2;
                    continue;
                }

                // We reached the closing delimiter of the attribute value, so we append it and return the position
                // after it.
                output.append(delimiter);
                return i + 1;
            }

            // Handle enclosed expressions inside the attribute value.
            if (currentChar == '{') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '{') {
                    // Double braces does not start an enclosed expression, so we append them and continue.
                    output.append("{{");
                    i += 2;
                    continue;
                }

                // Handle the enclosed expression by finding its end and recursively converting its content.
                int expressionEnd = findEnclosedExpressionEnd(input, i);
                output.append('{');
                output.append(convert(input.substring(i + 1, expressionEnd)));
                output.append('}');
                i = expressionEnd + 1;
                continue;
            }

            if (currentChar == '}' && i + 1 < input.length() && input.charAt(i + 1) == '}') {
                output.append("}}");
                i += 2;
                continue;
            }
            output.append(currentChar);
            i++;
        }

        throw new IllegalArgumentException("Unterminated direct attribute value starting at position " + start);
    }

    private static int findEnclosedExpressionEnd(String input, int start) {
        int depth = 1;
        int i = start + 1;
        while (i < input.length()) {
            if (startsWith(input, i, "(:")) {
                i = skipComment(input, i);
                continue;
            }

            char currentChar = input.charAt(i);
            if (currentChar == '"' || currentChar == '\'') {
                i = parseXQueryStringLiteral(input, i).end;
                continue;
            }
            if (currentChar == '{') {
                depth++;
            } else if (currentChar == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }

        throw new IllegalArgumentException("Unterminated enclosed expression starting at position " + start);
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

    // TODO clarify whether raw control characters need to be escaped for JSONiq string literals
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
