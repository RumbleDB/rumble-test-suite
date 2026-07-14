package evaluation.conversion;

/** Converts XQuery context-item expressions ({@code .}) to their JSONiq equivalent ({@code $$}). */
final class ContextItemConversion implements ConversionPass {

    @Override
    public String convert(String input) {
        StringBuilder output = new StringBuilder(input.length());
        int position = 0;

        while (position < input.length()) {
            if (ConversionLexicalUtils.startsWith(input, position, "(:")) {
                int end = ConversionLexicalUtils.skipComment(input, position);
                output.append(input, position, end);
                position = end;
                continue;
            }

            char current = input.charAt(position);
            if (current == '"' || current == '\'') {
                int end = findStringEnd(input, position);
                output.append(input, position, end);
                position = end;
                continue;
            }

            if (current == '.' && isContextItem(input, position)) {
                output.append("$$");
            } else {
                output.append(current);
            }
            position++;
        }

        return output.toString();
    }

    private static boolean isContextItem(String input, int position) {
        char previous = position == 0 ? '\0' : input.charAt(position - 1);
        char next = position + 1 == input.length() ? '\0' : input.charAt(position + 1);

        return previous != '.'
            && next != '.'
            && !isNameCharacter(previous)
            && !isNameCharacter(next)
            && !Character.isDigit(previous)
            && !Character.isDigit(next);
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetter(character) || character == '_' || character == '-';
    }

    private static int findStringEnd(String input, int start) {
        char delimiter = input.charAt(start);
        int position = start + 1;

        while (position < input.length()) {
            if (input.charAt(position) == '\\') {
                position += 2;
                continue;
            }
            if (input.charAt(position) == delimiter) {
                return position + 1;
            }
            position++;
        }

        return input.length();
    }

}
