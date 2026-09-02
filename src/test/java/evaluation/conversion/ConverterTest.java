package evaluation.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConverterTest {

    @Test
    public void appliesAllConversionsToOneParseTree() {
        assertEquals("fn:true(), \"value\", $$", Converter.convert("true(), 'value', ."));
    }

    @Test
    public void prefixesOnlyTargetedUnprefixedZeroArgumentCalls() {
        assertEquals("fn:true()", Converter.convert("true()"));
        assertEquals("fn:false()", Converter.convert("false()"));
        assertEquals("fn:not()", Converter.convert("not()"));
        assertEquals("fn:true()", Converter.convert("fn:true()"));
        assertEquals("true(1)", Converter.convert("true(1)"));
        assertEquals("not($x)", Converter.convert("not($x)"));
        assertEquals("local:true()", Converter.convert("local:true()"));
    }

    @Test
    public void preservesFormattingAndNonCallText() {
        assertEquals("fn:true (: comment :) ()", Converter.convert("true (: comment :) ()"));
        assertEquals("\"true()\"", Converter.convert("\"true()\""));
        assertEquals("<e>true()</e>", Converter.convert("<e>true()</e>"));
    }

    @Test
    public void unwrapsXQueryValidateExpressions() {
        assertEquals(" doc(\"input.xml\") ", Converter.convert("validate strict { doc('input.xml') }"));
        assertEquals(" $$ ", Converter.convert("validate lax { . }"));
        assertEquals(" 1 ", Converter.convert("validate type xs:integer { 1 }"));
    }

    @Test
    public void unwrapsNestedValidateExpressions() {
        assertEquals("  fn:true()  ", Converter.convert("validate { validate strict { true() } }"));
    }
}
