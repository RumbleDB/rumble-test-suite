package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ContextItemConversionTest {

    @Test
    public void convertsContextItemExpressions() {
        assertEquals("$$/doc/*", Converter.convert("./doc/*"));
        assertEquals("$$[1]", Converter.convert(".[1]"));
        assertEquals("for $x in 1 return $$", Converter.convert("for $x in 1 return ."));
    }

    @Test
    public void preservesOtherUsesOfDots() {
        String query = "(.., 1.5, .5, local:some.name, \"./text\", (: . :) )";
        assertEquals(query, Converter.convert(query));
    }

    @Test
    public void onlyConvertsExpressionsInsideDirectElementConstructors() {
        String query = "<para>There lived a hobbit.</para>";
        assertEquals(query, Converter.convert(query));

        query = "<eg> (: an (:example:) </eg>";
        assertEquals(query, Converter.convert(query));

        assertEquals(
            "<word count=\"{count($words[$$ = $word])}\"/>",
            Converter.convert("<word count=\"{count($words[. = $word])}\"/>")
        );
    }

    @Test
    public void convertsRecognizedContextItemsInInvalidXQuery() {
        String query = "./value +";
        assertEquals("$$/value +", Converter.convert(query));
    }

    @Test
    public void preservesCharactersOmittedByTheXQueryLexer() {
        String invalidEntityReference = "\"a string &;\"";
        assertEquals(invalidEntityReference, Converter.convert(invalidEntityReference));

        String xpathString = "xs:anyURI(\"http://!$&'()*+,;=/\")";
        assertEquals(xpathString, Converter.convert(xpathString));
    }

    @Test
    public void preservesLexerErrorsWhileApplyingRecognizedEdits() {
        assertEquals("$$/value, \"a string &;\"", Converter.convert("./value, \"a string &;\""));
    }

    @Test
    public void usesCodePointOffsetsForSourceEdits() {
        assertEquals("\"😀\", $$/value", Converter.convert("\"😀\", ./value"));
    }
}
