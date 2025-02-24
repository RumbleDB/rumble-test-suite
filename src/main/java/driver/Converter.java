package driver;

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
}
