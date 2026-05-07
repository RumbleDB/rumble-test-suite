package evaluation;

import java.util.LinkedHashMap;
import java.util.Map;


public class Converter {
    /**
     * method that converts a XQuery expression into a JSONiq++ expression
     * 
     * @param originalString string to be converted
     * @return convertedString that adheres to JSONiq++ grammar instead of XQuery
     */
    public static String convert(String originalString) {

        String convertedtestString = originalString;

        /*
         *
         * XQuery map constructors are converted to JSONiq object constructors.
         * Inside XQuery string literals, backslash has no special meaning
         *
         * Inside JSONiq string literals, backlashes are escapes.
         * Therefore, backslashes inside string literals
         * must be doubled before map{...} is changed to {...}.
         *
         * We do this minimally invasive for now, as it currently breaks a few fn:json-doc Fn1 tests that only use
         * xquery map
         * constructors.
         */

        convertedtestString = escapeBackslashes(convertedtestString);

        for (Map.Entry<String, String> entry : conversions.entrySet()) {
            convertedtestString = convertedtestString.replace(entry.getKey(), entry.getValue());
        }

        if (!originalString.equals(convertedtestString))
            System.out.println("[[convertedQuery|" + convertedtestString + "]]");
        return convertedtestString;
    }

    public static final Map<String, String> conversions = new LinkedHashMap<>();

    static {
        // Also array(+), array(?), array()*, array()+, array()? do not exist
        conversions.put("array(*)", "array");

        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        conversions.put("item()", "item");

        // We need fn to specify we want the function
        conversions.put("true()", "fn:true()");
        conversions.put("fn:fn:true()", "fn:true()"); // not very nice but works for now
        conversions.put("false()", "fn:false()");
        conversions.put("fn:fn:false()", "fn:false()");
        conversions.put("not()", "fn:not()");
        conversions.put("fn:fn:not()", "fn:not()");

        conversions.put("map(*)", "object");
        conversions.put("map{", "{");
        conversions.put("map {", " {");

        // if it has a space, it is context item for sure
        conversions.put(". ", "$$ ");
    }


    /**
     * Doubles backslashes inside all string literals that occur syntactically inside
     * XQuery map constructors.
     */
    private static String escapeBackslashes(String input) {
        if (!input.contains("\\") || !input.contains("map")) {
            return input;
        }

        StringBuilder out = new StringBuilder(input.length());

        int i = 0;
        int mapBraceDepth = 0;

        while (i < input.length()) {
            if (mapBraceDepth == 0) {
                int mapStartEnd = mapConstructorStartEnd(input, i);

                if (mapStartEnd != -1) {
                    out.append(input, i, mapStartEnd);
                    i = mapStartEnd;
                    mapBraceDepth = 1;
                    continue;
                }

                char c = input.charAt(i);

                if (c == '"' || c == '\'') {
                    int end = findXQueryStringLiteralEnd(input, i);
                    out.append(input, i, end);
                    i = end;
                    continue;
                }

                out.append(c);
                i++;
                continue;
            }

            char c = input.charAt(i);

            if (c == '"' || c == '\'') {
                int end = findXQueryStringLiteralEnd(input, i);
                String rawLiteral = input.substring(i, end);

                if (rawLiteral.indexOf('\\') >= 0) {
                    out.append(doubleBackslashesInsideLiteral(rawLiteral));
                } else {
                    out.append(rawLiteral);
                }

                i = end;
                continue;
            }

            if (c == '{') {
                mapBraceDepth++;
                out.append(c);
                i++;
                continue;
            }

            if (c == '}') {
                mapBraceDepth--;
                out.append(c);
                i++;
                continue;
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }



    private static int mapConstructorStartEnd(String input, int start) {
        if (!input.startsWith("map", start)) {
            return -1;
        }

        if (start > 0 && isIdentifierPart(input.charAt(start - 1))) {
            return -1;
        }

        int i = start + 3;

        if (i < input.length() && isIdentifierPart(input.charAt(i))) {
            return -1;
        }

        while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
            i++;
        }

        if (i < input.length() && input.charAt(i) == '{') {
            return i + 1;
        }

        return -1;
    }

    /**
     * Finds the end of an XQuery string literal.
     *
     * XQuery escapes the delimiter by doubling it
     *
     * Backslash has no special meaning here.
     */
    private static int findXQueryStringLiteralEnd(String input, int start) {
        char quote = input.charAt(start);
        int i = start + 1;

        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == quote) {
                if (i + 1 < input.length() && input.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }

                return i + 1;
            }

            i++;
        }

        throw new IllegalArgumentException("Unterminated string literal starting at position " + start);
    }

    /**
     * Doubles backslashes inside the literal body, preserving the original quote type.
     * "\u0007" => "\\u0007"
     */
    private static String doubleBackslashesInsideLiteral(String rawLiteral) {
        StringBuilder out = new StringBuilder(rawLiteral.length() + 4);

        char quote = rawLiteral.charAt(0);
        out.append(quote);

        for (int i = 1; i < rawLiteral.length() - 1; i++) {
            char c = rawLiteral.charAt(i);

            if (c == '\\') {
                out.append("\\\\");
            } else {
                out.append(c);
            }
        }

        out.append(quote);
        return out.toString();
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$' || c == ':';
    }

}
