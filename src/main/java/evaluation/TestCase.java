package evaluation;

import net.sf.saxon.s9api.XdmNode;

/**
 * class that represents one testcase used to create a list of testcases in the driver that get evaluated in the
 * JUnit classes
 */
public class TestCase {
    public String testString;
    public XdmNode assertion;
    public String skipReason;
    public final String xmlVersion;
    public final String defaultFormattingLanguage;
    public final boolean staticTyping;
    public Environment environment;
    public final String staticBaseUri;

    public TestCase(
            String testString,
            XdmNode assertion,
            String skipReason,
            Environment environment,
            String xmlVersion,
            String defaultFormattingLanguage,
            boolean staticTyping,
            String staticBaseUri
    ) {
        this.testString = testString;
        this.assertion = assertion;
        this.skipReason = skipReason;
        this.environment = environment;
        this.xmlVersion = xmlVersion;
        this.defaultFormattingLanguage = defaultFormattingLanguage;
        this.staticTyping = staticTyping;
        this.staticBaseUri = staticBaseUri;
    }
}
