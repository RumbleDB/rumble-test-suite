package evaluation.conversion;

import java.util.List;

import org.rumbledb.parser.xquery.XQueryParser;

/**
 * Converts XQuery expressions into JSONiq++ expressions.
 */
public final class Converter {

    private static final List<ConversionPass> CONVERSION_PASSES = List.of(
        new ContextItemConversion(),
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
        XQueryParser.ModuleAndThisIsItContext module = XQueryParsing.parseModule(originalString);
        if (module == null) {
            // If the input is not valid XQuery, we cannot convert it to JSONiq++.
            return originalString;
        }

        ConversionContext context = new ConversionContext(originalString, module);

        for (ConversionPass conversionPass : CONVERSION_PASSES) {
            conversionPass.rewrite(context);
        }

        return context.result();
    }
}
