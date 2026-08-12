package iq.base;

import evaluation.CollectedTestCase;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestBaseTest {
    @Test
    void qt3DependencyExclusionIsReportedAsSkipped() {
        evaluation.TestCase testCase = new evaluation.TestCase(
                null,
                null,
                "dependency feature schemaImport",
                null,
                null,
                null,
                false,
                null,
                null
        );

        TestAbortedException error = assertThrows(
            TestAbortedException.class,
            () -> new TestBase().testCase(new CollectedTestCase(testCase, "test-set", "test-case"))
        );

        assertEquals("Assumption failed: dependency feature schemaImport", error.getMessage());
    }

    @Test
    void unsupportedHarnessAssertionIsReportedAsError() throws Exception {
        XdmNode assertion = new Processor(false)
            .newDocumentBuilder()
            .build(new StreamSource(new StringReader("<unsupported-assertion/>")))
            .children()
            .iterator()
            .next();
        evaluation.TestCase testCase = new evaluation.TestCase(
                "1",
                assertion,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );

        UnsupportedOperationException error = assertThrows(
            UnsupportedOperationException.class,
            () -> new TestBase().testCase(new CollectedTestCase(testCase, "test-set", "test-case"))
        );

        assertEquals("unsupported-assertion assertion is not implemented by the test harness", error.getMessage());
    }
}
