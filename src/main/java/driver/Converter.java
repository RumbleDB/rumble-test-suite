package driver;

import java.util.Map;

public class Converter {

    public static String Convert(String testString) throws UnsupportedTypeException {
        testString = ConvertAtomicTypes(testString);
        testString = ConvertNonAtomicTypes(testString);

        // TODO Verify this
        // testString = testString.replace("'", "\"");

        // Replace with Regex Checks
        // testString = testString.replace("fn:", "");
        // testString = testString.replace("math:", "");
        // testString = testString.replace("map:", "");
        // testString = testString.replace("array:", "");
        // testString = testString.replace("xs:", ""); // This should be handled with all the types before

        // XML notation
        // testString = testString.replace(". ", "$$ ");

        return testString;
    }

    private static String ConvertAtomicTypes(String testString) throws UnsupportedTypeException {
        for (Map.Entry<String, String> entry : Constants.atomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        for (String target : Constants.unsupportedTypes) {
            if (testString.contains(target))
                throw new UnsupportedTypeException();
        }
        return testString;
    }

    private static String ConvertNonAtomicTypes(String testString) {
        for (Map.Entry<String, String> entry : Constants.nonAtomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        return testString;
    }
}
