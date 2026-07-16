package evaluation.conversion;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.rumbledb.parser.xquery.XQueryLexer;
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
        XQueryLexer lexer = new XQueryLexer(CharStreams.fromString(originalString));
        lexer.removeErrorListeners();

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XQueryParser parser = new XQueryParser(tokens);
        parser.removeErrorListeners();

        XQueryParser.ModuleAndThisIsItContext module;
        try {
            module = parser.moduleAndThisIsIt();
        } catch (ParseCancellationException exception) {
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
