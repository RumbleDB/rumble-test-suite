package evaluation.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringLiteralConversionTest {

    @Test
    void convertsNumericCharacterReferences() {
        assertEquals(
            "replace(\"abc\\uD834\\uDD57def\", \"[^a-f]\", \"###\")",
            Converter.convert("replace(\"abc&#x1D157;def\", \"[^a-f]\", \"###\")")
        );
    }

    @Test
    void convertsSupplementaryCodepoints() {
        assertEquals(
            "compare(\"\\uD800\\uDC01\", \"\\uD800\\uDC02\")",
            Converter.convert("compare(\"&#65537;\", \"&#65538;\")")
        );
    }

    @Test
    void convertsPredefinedEntityReferences() {
        assertEquals(
            "\"&\\\"\"",
            Converter.convert("\"&amp;&quot;\"")
        );
    }

    @Test
    void doesNotDecodeGeneratedCharacterReferencesTwice() {
        assertEquals(
            "\"&#65;\"",
            Converter.convert("\"&amp;#65;\"")
        );
    }
}
