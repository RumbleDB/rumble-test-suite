package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import evaluation.conversion.Converter.StringLiteralSemantics;
import org.junit.jupiter.api.Test;

class StringLiteralConversionTest {

    @Test
    void convertsNumericCharacterReferences() {
        String character = new String(Character.toChars(0x1D157));

        assertEquals(
            "replace(\"abc" + character + "def\", \"[^a-f]\", \"###\")",
            Converter.convert(
                "replace(\"abc&#x1D157;def\", \"[^a-f]\", \"###\")",
                StringLiteralSemantics.XQUERY
            )
        );
    }

    @Test
    void convertsSupplementaryCodepoints() {
        assertEquals(
            "compare(\"𐀁\", \"𐀂\")",
            Converter.convert(
                "compare(\"&#65537;\", \"&#65538;\")",
                StringLiteralSemantics.XQUERY
            )
        );
    }

    @Test
    void convertsPredefinedEntityReferences() {
        assertEquals(
            "\"&\\\"\"",
            Converter.convert("\"&amp;&quot;\"", StringLiteralSemantics.XQUERY)
        );
    }

    @Test
    void doesNotDecodeGeneratedCharacterReferencesTwice() {
        assertEquals(
            "\"&#65;\"",
            Converter.convert("\"&amp;#65;\"", StringLiteralSemantics.XQUERY)
        );
    }

    @Test
    void preservesXPathCharacterReferences() {
        assertEquals(
            "\"&amp;#x74;\"",
            Converter.convert("\"&#x74;\"", StringLiteralSemantics.XPATH)
        );
    }

    @Test
    void convertsDoubledQuotesAndBackslashes() {
        assertEquals(
            "\"a \\\" b \\\\\\\\ c\"",
            Converter.convert("'a \" b \\\\ c'", StringLiteralSemantics.DEFAULT)
        );
    }
}
