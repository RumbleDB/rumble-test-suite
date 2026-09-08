package evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class CaseCollectorTest {
    @TempDir
    Path repository;

    private List<CollectedTestCase> collect(String dependencies) throws Exception {
        writeCatalog("");
        writeTestSet("cases.xml", "<test-case name='probe'>" + dependencies
                + "<test>1</test><result><assert-eq>1</assert-eq></result></test-case>");
        CaseCollector collector = new CaseCollector(repository, TestCaseSelection.fromSystemProperties());
        collector.execute("cases");
        return collector.getAllTests();
    }

    private void writeCatalog(String environments) throws Exception {
        Files.writeString(repository.resolve("catalog.xml"),
                "<catalog xmlns='http://www.w3.org/2010/09/qt-fots-catalog'>" + environments
                + "<test-set name='cases' file='cases.xml'/></catalog>");
    }

    private void writeTestSet(String file, String content) throws Exception {
        Files.writeString(repository.resolve(file),
                "<test-set xmlns='http://www.w3.org/2010/09/qt-fots-catalog'>" + content + "</test-set>");
    }

    @Test
    public void normalizationSupportAndNegationSelectOppositeCases() throws Exception {
        assertNull(collect("<dependency type='unicode-normalization-form' value='FULLY-NORMALIZED'/>")
                .get(0).testCase().skipReason);
        assertNotNull(collect("<dependency type='unicode-normalization-form' value='FULLY-NORMALIZED' satisfied='false'/>")
                .get(0).testCase().skipReason);
    }

    @Test
    public void unsupportedNumberingSequenceRunsOnlyFallbackCase() throws Exception {
        assertNotNull(collect("<dependency type='format-integer-sequence' value='ﯴ'/>").get(0).testCase().skipReason);
        assertNull(collect("<dependency type='format-integer-sequence' value='ﯴ' satisfied='false'/>")
                .get(0).testCase().skipReason);
    }

    @Test
    public void specAlternativesAreNegatedAsOneRequirement() throws Exception {
        assertNull(collect("<dependency type='spec' value='XP20 XQ30+'/>").get(0).testCase().skipReason);
        assertNotNull(collect("<dependency type='spec' value='XP20 XQ30+' satisfied='false'/>")
                .get(0).testCase().skipReason);
        assertNull(collect("<dependency type='spec' value='XP20 XQ30' satisfied='false'/>")
                .get(0).testCase().skipReason);
        assertNotNull(collect("<dependency type='spec' value='XQ31' satisfied='0'/>").get(0).testCase().skipReason);
    }
    @Test
    public void xmlEditionDependenciesSelectSupportedModes() throws Exception {
        assertNotNull(collect("<dependency type='xml-version' value='1.0:4-'/>").get(0).testCase().skipReason);
        TestCase modern = collect("<dependency type='xml-version' value='1.0:5+ 1.1'/>").get(0).testCase();
        assertNull(modern.skipReason);
        assertEquals("1.0", modern.xmlVersion);
        assertEquals("1.1", collect("<dependency type='xml-version' value='1.1'/>").get(0).testCase().xmlVersion);
        assertEquals("1.1", collect("<dependency type='xml-version' value='1.0' satisfied='false'/>")
                .get(0).testCase().xmlVersion);
    }

    @Test
    public void xmlDependenciesMustHaveACommonSupportedMode() throws Exception {
        TestCase compatible = collect("<dependency type='xml-version' value='1.0:5+ 1.1'/>"
                + "<dependency type='xml-version' value='1.1'/>").get(0).testCase();
        assertNull(compatible.skipReason);
        assertEquals("1.1", compatible.xmlVersion);
        assertNotNull(collect("<dependency type='xml-version' value='1.0'/>"
                + "<dependency type='xml-version' value='1.1'/>").get(0).testCase().skipReason);
    }
    @Test
    public void localEnvironmentsShadowCatalogAndDoNotLeakBetweenTestSets() throws Exception {
        Files.writeString(repository.resolve("catalog.xml"),
                "<catalog xmlns='http://www.w3.org/2010/09/qt-fots-catalog'>"
                + "<environment name='shared'><static-base-uri uri='urn:global'/></environment>"
                + "<test-set name='first' file='first.xml'/><test-set name='second' file='second.xml'/></catalog>");
        writeTestSet("first.xml", "<environment name='shared'><static-base-uri uri='urn:local'/></environment>"
                + "<test-case name='first'><environment ref='shared'/><test>1</test>"
                + "<result><assert-eq>1</assert-eq></result></test-case>");
        writeTestSet("second.xml", "<test-case name='second'><environment ref='shared'/><test>1</test>"
                + "<result><assert-eq>1</assert-eq></result></test-case>");
        CaseCollector collector = new CaseCollector(repository, TestCaseSelection.fromSystemProperties());
        collector.execute("");
        assertEquals("urn:local", collector.getAllTests().get(0).testCase().staticBaseUri);
        assertEquals("urn:global", collector.getAllTests().get(1).testCase().staticBaseUri);
    }
    @Test
    public void selectedCaseCanBelongToAnotherPartition() throws Exception {
        writeCatalog("");
        writeTestSet("cases.xml", "<test-case name='selected'><test>1</test>"
                + "<result><assert-eq>1</assert-eq></result></test-case>");
        CaseCollector other = new CaseCollector(repository, new TestCaseSelection("selected"));
        other.execute("other");
        assertTrue(other.getAllTests().isEmpty());
        CaseCollector matching = new CaseCollector(repository, new TestCaseSelection("selected"));
        matching.execute("cases");
        assertEquals(1, matching.getAllTests().size());
        assertEquals("selected", matching.getAllTests().get(0).testCaseName());
    }

    @Test
    public void selectedCaseMustExistAndBeUniqueAcrossCatalog() throws Exception {
        writeCatalog("");
        writeTestSet("cases.xml", "<test-case name='selected'><test>1</test>"
                + "<result><assert-eq>1</assert-eq></result></test-case>");
        CaseCollector unknown = new CaseCollector(repository, new TestCaseSelection("typo"));
        assertThrows(SelectedTestCaseNotFoundException.class, () -> unknown.execute("other"));
        Files.writeString(repository.resolve("catalog.xml"),
                "<catalog xmlns='http://www.w3.org/2010/09/qt-fots-catalog'>"
                + "<test-set name='cases' file='cases.xml'/><test-set name='duplicate' file='duplicate.xml'/></catalog>");
        writeTestSet("duplicate.xml", "<test-case name='selected'><test>1</test>"
                + "<result><assert-eq>1</assert-eq></result></test-case>");
        CaseCollector duplicate = new CaseCollector(repository, new TestCaseSelection("selected"));
        assertThrows(DuplicateSelectedTestCaseException.class, () -> duplicate.execute("cases"));
    }
}
