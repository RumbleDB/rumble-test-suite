package evaluation.conversion;

import java.util.LinkedHashMap;
import java.util.Map;

final class SimpleReplacementConversion implements ConversionPass {

    private static final Map<String, String> CONVERSIONS = new LinkedHashMap<>();

    static {
        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        CONVERSIONS.put("item()", "item");

        // We need fn to specify we want the function
        CONVERSIONS.put("true()", "fn:true()");
        CONVERSIONS.put("fn:fn:true()", "fn:true()"); // not very nice but works for now
        CONVERSIONS.put("false()", "fn:false()");
        CONVERSIONS.put("fn:fn:false()", "fn:false()");
        CONVERSIONS.put("not()", "fn:not()");
        CONVERSIONS.put("fn:fn:not()", "fn:not()");

        CONVERSIONS.put("map{", "{");
        CONVERSIONS.put("map {", " {");

        // if it has a space, it is context item for sure
        CONVERSIONS.put(". ", "$$ ");
    }


    @Override
    public String convert(String input) {
        String convertedTestString = input;

        for (Map.Entry<String, String> entry : CONVERSIONS.entrySet()) {
            convertedTestString = convertedTestString.replace(entry.getKey(), entry.getValue());
        }

        return convertedTestString;
    }
}
