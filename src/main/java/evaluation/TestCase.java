package evaluation;

import net.sf.saxon.s9api.XdmNode;
import org.rumbledb.config.RumbleRuntimeConfiguration;

/**
 * class that represents one testcase used to create a list of testcases in the driver that get evaluated in the
 * JUnit classes
 */
public class TestCase {
    public String testString;
    public XdmNode assertion;
    public String skipReason;
    private final String xmlVersion;
    private final String defaultFormattingLanguage;
    public Environment environment;

    public TestCase(
            String testString,
            XdmNode assertion,
            String skipReason,
            Environment environment,
            String xmlVersion,
            String defaultFormattingLanguage
    ) {
        this.testString = testString;
        this.assertion = assertion;
        this.skipReason = skipReason;
        this.environment = environment;
        this.xmlVersion = xmlVersion;
        this.defaultFormattingLanguage = defaultFormattingLanguage;
    }

    public void applyDependenciesTo(RumbleRuntimeConfiguration configuration) {
        if (this.xmlVersion != null) {
            configuration.setXmlVersion(this.xmlVersion);
        }
        if (this.defaultFormattingLanguage != null) {
            configuration.setDefaultFormattingLanguage(this.defaultFormattingLanguage);
        }
    }
}
