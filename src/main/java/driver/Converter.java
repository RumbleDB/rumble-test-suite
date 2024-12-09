package driver;

import java.util.Map;


public class Converter {
    /**
     * method that converts a XQuery expression into a JSONiq++ expression
     * 
     * @param originalString
     * @return convertedString that adheres to JSONiq++ grammar instead of XQuery
     * @throws UnsupportedTypeException if unsupported type is encountered
     */
    public static String convert(String originalString) throws UnsupportedTypeException {

        String convertedtestString = convertAtomicTypes(originalString);
        convertedtestString = convertNonAtomicTypes(convertedtestString);

        // TODO problem is we dont want to blindly replace everything
        // maybe we can support ' in same places as " aswell?
        convertedtestString = convertedtestString.replace("'", "\"");

        // Replace with Regex Checks
        // testString = testString.replace("fn:", "");
        // testString = testString.replace("math:", "");
        // testString = testString.replace("map:", "");
        // testString = testString.replace("array:", "");
        // testString = testString.replace("xs:", ""); // This should be handled with all the types before

        // XML notation
        // testString = testString.replace(". ", "$$ ");
        if (originalString.equals(convertedtestString))
            System.out.println("[[info|converted string to " + convertedtestString + "]]");
        return convertedtestString;
    }

    private static String convertAtomicTypes(String testString) throws UnsupportedTypeException {
        for (Map.Entry<String, String> entry : Constants.atomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        for (String target : Constants.unsupportedTypes) {
            if (testString.contains(target))
                throw new UnsupportedTypeException();
        }
        return testString;
    }

    private static String convertNonAtomicTypes(String testString) {
        for (Map.Entry<String, String> entry : Constants.nonAtomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        return testString;
    }
}
