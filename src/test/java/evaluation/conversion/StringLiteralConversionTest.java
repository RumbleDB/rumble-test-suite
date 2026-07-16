package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringLiteralConversionTest {

    @Test
    public void convertsOrdinaryXQueryStrings() {
        assertEquals("\"a \\\" b\"", Converter.convert("\"a \"\" b\""));
        assertEquals("\"\\\\path\"", Converter.convert("'\\path'"));
        assertEquals("\"a & b\"", Converter.convert("\"a & b\""));
        assertEquals("\"&amp;\"", Converter.convert("\"&amp;\""));
    }

    @Test
    public void preservesStaticDirectAttributeValues() {
        assertEquals("<elem attr=\"\"\"\"/>", Converter.convert("<elem attr=\"\"\"\"/>"));
        assertEquals("<elem attr=''''/>", Converter.convert("<elem attr=''''/>"));
        assertEquals("<elem attr=\"&amp;&lt;&gt;\"/>", Converter.convert("<elem attr=\"&amp;&lt;&gt;\"/>"));
        assertEquals("<elem attr=\"\\n\"/>", Converter.convert("<elem attr=\"\\n\"/>"));
    }

    @Test
    public void preservesInterpolatedDirectAttributeValues() {
        String query = "<e x=\"{$x}\" mixed=\"before {1} after\" literal=\"{{value}}\"/>";
        assertEquals(query, Converter.convert(query));

        query = "for $x in 1 return <e quote=\"\"\"\" value=\"{$x}\"/>";
        assertEquals(query, Converter.convert(query));
    }

    @Test
    public void preservesQuotesInsideAttributeEnclosedExpressions() {
        String query = "<e attr=\"{comment {\" content \"}}\"/>";
        assertEquals(query, Converter.convert(query));
    }

    @Test
    public void convertsStringsInsideAttributeEnclosedExpressions() {
        String query = "<e attr=\"{concat('\\path', \"a \"\" b\")}\"/>";
        String expected = "<e attr=\"{concat(\"\\\\path\", \"a \\\" b\")}\"/>";

        assertEquals(expected, Converter.convert(query));

        query = "<e attr=\"&amp;{'a & b'}\"/>";
        expected = "<e attr=\"&amp;{\"a & b\"}\"/>";
        assertEquals(expected, Converter.convert(query));
    }

    @Test
    public void doesNotConfuseLessThanComparisonsWithConstructors() {
        assertEquals("$x < y and \"a \\\" b\"", Converter.convert("$x < y and \"a \"\" b\""));
    }

    @Test
    public void recognizesConstructorsAfterExpressionKeywords() {
        String afterAnd = "true() and <e attr=\"\"\"\"/>";
        String afterWhere = "for $x in 1 where <e attr=\"\"\"\"/> return $x";
        String afterUnion = "$x union <e attr=\"\"\"\"/>";
        String afterComment = "for $x in 1 return (: outer (: nested :) :) <e attr=\"\\path\"/>";

        assertEquals("fn:" + afterAnd, Converter.convert(afterAnd));
        assertEquals(afterWhere, Converter.convert(afterWhere));
        assertEquals(afterUnion, Converter.convert(afterUnion));
        assertEquals(afterComment, Converter.convert(afterComment));
    }
}
