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
    public Environment environment;

    public TestCase(String testString, XdmNode assertion, String skipReason, Environment environment) {
        this.testString = testString;
        this.assertion = assertion;
        this.skipReason = skipReason;
        this.environment = environment;
    }
}
