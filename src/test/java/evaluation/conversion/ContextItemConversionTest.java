package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ContextItemConversionTest {

    private final ContextItemConversion conversion = new ContextItemConversion();

    @Test
    public void convertsContextItemExpressions() {
        assertEquals("$$/doc/*", this.conversion.convert("./doc/*"));
        assertEquals("$$[1]", this.conversion.convert(".[1]"));
        assertEquals("for $x in 1 return $$", this.conversion.convert("for $x in 1 return ."));
    }

    @Test
    public void preservesOtherUsesOfDots() {
        String query = "(.., 1.5, .5, local:some.name, \"./text\", (: . :) )";
        assertEquals(query, this.conversion.convert(query));
    }

    @Test
    public void onlyConvertsExpressionsInsideDirectElementConstructors() {
        String query = "<para>There lived a hobbit.</para>";
        assertEquals(query, this.conversion.convert(query));

        query = "<eg> (: an (:example:) </eg>";
        assertEquals(query, this.conversion.convert(query));

        assertEquals(
            "<word count=\"{count($words[$$ = $word])}\"/>",
            this.conversion.convert("<word count=\"{count($words[. = $word])}\"/>")
        );
    }

    @Test
    public void convertsRecognizedContextItemsInInvalidXQuery() {
        String query = "./value +";
        assertEquals("$$/value +", this.conversion.convert(query));
    }

    @Test
    public void preservesCharactersOmittedByTheXQueryLexer() {
        String invalidEntityReference = "\"a string &;\"";
        assertEquals(invalidEntityReference, this.conversion.convert(invalidEntityReference));

        String xpathString = "xs:anyURI(\"http://!$&'()*+,;=/\")";
        assertEquals(xpathString, this.conversion.convert(xpathString));
    }

    @Test
    public void preservesLexerErrorsWhileApplyingRecognizedEdits() {
        assertEquals("$$/value, \"a string &;\"", this.conversion.convert("./value, \"a string &;\""));
    }

    @Test
    public void usesCodePointOffsetsForSourceEdits() {
        assertEquals("\"😀\", $$/value", this.conversion.convert("\"😀\", ./value"));
    }
}
