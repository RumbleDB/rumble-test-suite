package evaluation.conversion;

/**
 * Converts XQuery expressions into JSONiq++ expressions.
 */
public final class Converter {

    public enum StringLiteralSemantics {
        DEFAULT,
        XQUERY,
        XPATH
    }

    private Converter() {
    }

    public static String convert(String originalString, StringLiteralSemantics semantics) {
        String convertedString = new StringLiteralConversion(semantics).convert(originalString);
        return new SimpleReplacementConversion().convert(convertedString);
    }
}
