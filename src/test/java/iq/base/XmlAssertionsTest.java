package iq.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XmlAssertionsTest {
    @Test
    void comparesElementsNamedXmlnsWithoutCloningThem() {
        assertFalse(XmlAssertions.compare("<out><xmlns/><xmlns/></out>", "<out> <xmlns/> <xmlns/> </out>", false)
                .hasDifferences());
    }

    @Test
    void retainsDifferencesInTextAndAttributes() {
        assertTrue(XmlAssertions.compare("<out>a</out>", "<out>b</out>", false).hasDifferences());
        assertTrue(XmlAssertions.compare("<out a='1'/>", "<out a='2'/>", false).hasDifferences());
        assertFalse(
                XmlAssertions.compare("<out> a </out>", "<out>a</out>", false).hasDifferences());
    }

    @Test
    void serializesAmpersandsAsXml() {
        var result =
                java.util.List.of(org.rumbledb.items.ItemFactory.getInstance().createStringItem("a & b"));
        String xml = org.rumbledb.serialization.SequenceSerializer.serialize(
                result,
                new org.rumbledb.serialization.SerializationParameters(),
                org.rumbledb.exceptions.ExceptionMetadata.EMPTY_METADATA);
        assertFalse(XmlAssertions.compare("<out>a &amp; b</out>", "<out>" + xml + "</out>", false)
                .hasDifferences());
    }
}
