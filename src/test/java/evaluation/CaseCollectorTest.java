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
}
