package evaluation.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringLiteralConversionTest {

    @Test
    public void convertsOrdinaryXQueryStrings() {
        assertEquals("\"a \\\" b\"", Converter.convert("\"a \"\" b\""));
        assertEquals("\"\\\\path\"", Converter.convert("'\\path'"));
        assertEquals("\"a & b\"", Converter.convert("\"a & b\""));
        assertEquals("\"&\"", Converter.convert("\"&amp;\""));
    }

    @Test
    public void decodesCharacterReferencesBeforeJSONiqEscaping() {
        assertEquals(
                "fn:matches(\" \\t\\r\", \"\\\\c+\")", Converter.convert("fn:matches('&#x20;&#x9;&#xD;', '\\c+')"));
        assertEquals("\" \\t\\r\"", Converter.convert("'&#32;&#9;&#13;'"));
        assertEquals("\"\ud83d\ude00\ud83d\ude00\"", Converter.convert("'&#x1F600;&#128512;'"));
        assertEquals("\"&<>\\\"'\"", Converter.convert("'&amp;&lt;&gt;&quot;&apos;'"));
    }

    @Test
    public void decodesReferencesOnlyOnce() {
        assertEquals("\"&#x20;&amp;\"", Converter.convert("'&amp;#x20;&amp;amp;'"));
        assertEquals("\"&amp;\"", Converter.convert("'&#38;amp;'"));
    }

    @Test
    public void roundTripsReferenceLikeTextAndQuotes() {
        String value = "&#x20; &amp; ' \" \ud83d\ude00";
        for (char delimiter : new char[] {'\'', '"'}) {
            String source = XQueryStringLiteral.serialize(value, delimiter);
            assertEquals(value, XQueryStringLiteral.parse(source));
            assertEquals("\"&#x20; &amp; ' \\\" \ud83d\ude00\"", Converter.convert(source));
        }
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
