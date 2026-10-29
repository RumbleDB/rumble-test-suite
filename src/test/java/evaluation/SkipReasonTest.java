package evaluation;

import java.io.StringReader;
import java.util.List;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.streams.Steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SkipReasonTest {

    @Test
    public void selectsXsdVersionPerTest() throws Exception {
        for (String value : List.of("1.0", "1.1", "1.0 1.1")) {
            var result = CaseCollector.checkDependencies(testCase("<test-set><test-case name='t'>"
                    + "<dependency type='xsd-version' value='"
                    + value
                    + "'/></test-case></test-set>"));
            assertNull(result.skipReason);
            assertEquals(value.equals("1.1") ? "1.1" : "1.0", result.xsdVersion);
        }
        assertEquals(
                "1.0",
                CaseCollector.checkDependencies(testCase("<test-set><test-case name='t'/></test-set>")).xsdVersion);
        var negative = CaseCollector.checkDependencies(
                testCase(
                        "<test-set><dependency type='xsd-version' value='1.0' satisfied='false'/><test-case name='t'/></test-set>"));
        assertNull(negative.skipReason);
        assertEquals("1.1", negative.xsdVersion);
        var inherited = CaseCollector.checkDependencies(
                testCase("<test-set><dependency type='xsd-version' value='1.1'/><test-case name='t'/></test-set>"));
        assertNull(inherited.skipReason);
        assertEquals("1.1", inherited.xsdVersion);
    }

    @Test
    public void missingFeatureIsReported() throws Exception {
        assertEquals(
                "missing-feature: feature=infoset-dtd [missing-feature]",
                skipMessage("<dependency type=\"feature\" value=\"infoset-dtd\"/>"));
    }

    @Test
    public void negativeTestOfSupportedFeatureIsNotApplicable() throws Exception {
        assertEquals(
                "not-applicable: feature=!serialization [not-applicable]",
                skipMessage("<dependency type=\"feature\" value=\"serialization\" satisfied=\"false\"/>"));
    }

    @Test
    public void negativeTestOfUnsupportedFeatureRuns() throws Exception {
        assertNull(skipMessage("<dependency type=\"feature\" value=\"infoset-dtd\" satisfied=\"false\"/>"));
    }

    @Test
    public void reportsEveryUnmetDependencyWithTheHighestPrecedenceCategory() throws Exception {
        assertEquals(
                "other-spec: feature=namespace-axis [missing-feature]; spec=XP20+ [other-spec]",
                skipMessage("<dependency type=\"feature\" value=\"namespace-axis\"/>"
                        + "<dependency type=\"spec\" value=\"XP20+\"/>"));
    }

    @Test
    public void testSetDependenciesAreIncluded() throws Exception {
        XdmNode testCase =
                testCase("<test-set><dependency type=\"spec\" value=\"XQ10\"/><test-case name=\"t\"/></test-set>");
        assertEquals(
                "other-spec: spec=XQ10 [other-spec]",
                CaseCollector.checkDependencies(testCase).skipReason.message());
    }

    @Test
    public void unsupportedXmlVersionIsNotApplicable() throws Exception {
        assertEquals(
                "not-applicable: xml-version=1.0:4- [not-applicable]",
                skipMessage("<dependency type=\"xml-version\" value=\"1.0:4-\"/>"));
    }

    @Test
    public void categoryPrecedence() {
        SkipReason reason = new SkipReason(List.of(
                new SkipReason.UnmetDependency("feature", "a", true, SkipReason.Category.NOT_APPLICABLE),
                new SkipReason.UnmetDependency("feature", "b", true, SkipReason.Category.MISSING_FEATURE)));
        assertEquals(SkipReason.Category.NOT_APPLICABLE, reason.category());
    }

    private static String skipMessage(String dependencies) throws SaxonApiException {
        SkipReason reason = CaseCollector.checkDependencies(
                        testCase("<test-set><test-case name=\"t\">" + dependencies + "</test-case></test-set>"))
                .skipReason;
        return reason == null ? null : reason.message();
    }

    private static XdmNode testCase(String testSet) throws SaxonApiException {
        XdmNode document = new Processor(false).newDocumentBuilder().build(new StreamSource(new StringReader(testSet)));
        return document.select(Steps.descendant("test-case")).asNode();
    }
}
