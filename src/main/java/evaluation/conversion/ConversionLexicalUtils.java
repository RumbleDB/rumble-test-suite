package evaluation.conversion;

final class ConversionLexicalUtils {

    private ConversionLexicalUtils() {
    }

    static int skipComment(String input, int start) {
        int depth = 1;
        int position = start + 2;

        while (position < input.length()) {
            if (startsWith(input, position, "(:")) {
                depth++;
                position += 2;
                continue;
            }

            if (startsWith(input, position, ":)")) {
                depth--;
                position += 2;
                if (depth == 0) {
                    return position;
                }
                continue;
            }

            position++;
        }

        throw new IllegalArgumentException("Unterminated XQuery comment starting at position " + start);
    }

    static boolean startsWith(String input, int offset, String prefix) {
        return input.regionMatches(offset, prefix, 0, prefix.length());
    }
}
