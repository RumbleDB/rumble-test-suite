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

        for (Map.Entry<String, String> entry : conversions.entrySet()) {
            convertedtestString = convertedtestString.replace(entry.getKey(), entry.getValue());
        }

        if (!originalString.equals(convertedtestString))
            System.out.println("[[convertedString|" + convertedtestString + "]]");
        return convertedtestString;
    }

    public static final Map<String, String> conversions = Map.ofEntries(
        // Also array(+), array(?), array()*, array()+, array()? do not exist
        Map.entry("array(*)", "array"),

        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        Map.entry("item()", "item"),

        // We need fn to specify we want the function
        Map.entry("true()", "fn:true()"),
        Map.entry("fn:fn:true()", "fn:true()"), // not very nice but works for now
        Map.entry("false()", "fn:false()"),
        Map.entry("fn:fn:false()", "fn:false()"),
        Map.entry("not()", "fn:not()"),
        Map.entry("fn:fn:not()", "fn:not()"),

        Map.entry("map(*)", "object"),
        Map.entry("map{", "{"),
        Map.entry("map {", " {"),

        // if it has a space, it is context item for sure
        Map.entry(". ", "$$ ")
    );
}
