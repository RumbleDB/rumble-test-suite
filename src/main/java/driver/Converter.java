package driver;

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

        // convert types
        convertedtestString = convertTypes(convertedtestString);

        convertedtestString = convertedtestString.replace(". ", "$$ ");


        if (!originalString.equals(convertedtestString))
            System.out.println("[[convertedString|" + convertedtestString + "]]");
        return convertedtestString;
    }

    private static String convertTypes(String testString) {
        for (Map.Entry<String, String> entry : typeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        return testString;
    }

    public static final Map<String, String> typeConversions = Map.ofEntries(
        Map.entry("xs:atomic", "atomic"),

        // Also array(+), array(?), array()*, array()+, array()? do not exist
        Map.entry("array(*)", "array"),

        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        Map.entry("item()", "item"),

        // We need fn to specify we want the function
        Map.entry("true()", "fn:true()"),
        Map.entry("false()", "fn:false()"),

        Map.entry("map(*)", "object"),
        Map.entry("map{", "{"),
        Map.entry("map {", " {")
    );
}
