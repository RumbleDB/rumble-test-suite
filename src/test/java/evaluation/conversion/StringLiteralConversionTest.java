package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringLiteralConversionTest {

    private final StringLiteralConversion conversion = new StringLiteralConversion();

    @Test
    public void convertsOrdinaryXQueryStrings() {
        assertEquals("\"a \\\" b\"", this.conversion.convert("\"a \"\" b\""));
        assertEquals("\"\\\\path\"", this.conversion.convert("'\\path'"));
        assertEquals("\"a & b\"", this.conversion.convert("\"a & b\""));
        assertEquals("\"&amp;\"", this.conversion.convert("\"&amp;\""));
    }

    @Test
    public void preservesStaticDirectAttributeValues() {
        assertEquals("<elem attr=\"\"\"\"/>", this.conversion.convert("<elem attr=\"\"\"\"/>"));
        assertEquals("<elem attr=''''/>", this.conversion.convert("<elem attr=''''/>"));
        assertEquals("<elem attr=\"&amp;&lt;&gt;\"/>", this.conversion.convert("<elem attr=\"&amp;&lt;&gt;\"/>"));
        assertEquals("<elem attr=\"\\n\"/>", this.conversion.convert("<elem attr=\"\\n\"/>"));
    }

    @Test
    public void preservesInterpolatedDirectAttributeValues() {
        String query = "<e x=\"{$x}\" mixed=\"before {1} after\" literal=\"{{value}}\"/>";
        assertEquals(query, this.conversion.convert(query));

        query = "for $x in 1 return <e quote=\"\"\"\" value=\"{$x}\"/>";
        assertEquals(query, this.conversion.convert(query));
    }

    @Test
    public void preservesQuotesInsideAttributeEnclosedExpressions() {
        String query = "<e attr=\"{comment {\" content \"}}\"/>";
        assertEquals(query, this.conversion.convert(query));
    }

    @Test
    public void convertsStringsInsideAttributeEnclosedExpressions() {
        String query = "<e attr=\"{concat('\\path', \"a \"\" b\")}\"/>";
        String expected = "<e attr=\"{concat(\"\\\\path\", \"a \\\" b\")}\"/>";

        assertEquals(expected, this.conversion.convert(query));

        query = "<e attr=\"&amp;{'a & b'}\"/>";
        expected = "<e attr=\"&amp;{\"a & b\"}\"/>";
        assertEquals(expected, this.conversion.convert(query));
    }

    @Test
    public void doesNotConfuseLessThanComparisonsWithConstructors() {
        assertEquals("$x < y and \"a \\\" b\"", this.conversion.convert("$x < y and \"a \"\" b\""));
    }

    @Test
    public void recognizesConstructorsAfterExpressionKeywords() {
        String afterAnd = "true() and <e attr=\"\"\"\"/>";
        String afterWhere = "for $x in 1 where <e attr=\"\"\"\"/> return $x";
        String afterUnion = "$x union <e attr=\"\"\"\"/>";
        String afterComment = "for $x in 1 return (: outer (: nested :) :) <e attr=\"\\path\"/>";

        assertEquals(afterAnd, this.conversion.convert(afterAnd));
        assertEquals(afterWhere, this.conversion.convert(afterWhere));
        assertEquals(afterUnion, this.conversion.convert(afterUnion));
        assertEquals(afterComment, this.conversion.convert(afterComment));
    }
}
