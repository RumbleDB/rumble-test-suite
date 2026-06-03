package evaluation.conversion;

import java.util.List;

/**
 * Converts XQuery expressions into JSONiq++ expressions.
 */
public final class Converter {

    private static final List<ConversionPass> CONVERSION_PASSES = List.of(
        new StringLiteralConversion(),
        new SimpleReplacementConversion()
    );

    private Converter() {
    }

    /**
     * Converts an XQuery expression into a JSONiq++ expression.
     *
     * @param originalString string to be converted
     * @return converted string that adheres to JSONiq++ grammar instead of XQuery
     */
    public static String convert(String originalString) {
        String convertedString = originalString;

        for (ConversionPass conversionPass : CONVERSION_PASSES) {
            convertedString = conversionPass.convert(convertedString);
        }

        return convertedString;
    }
}
