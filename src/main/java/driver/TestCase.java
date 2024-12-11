package driver;

import net.sf.saxon.s9api.XdmNode;

/**
 * class that represents one testcase used to create a list of testcases in the driver that get evaluated in the
 * *Test.java classes
 */
public class TestCase {
    public String testString;
    public XdmNode assertion;
    public String skipReason;

    public TestCase(String testString, XdmNode assertion, String skipReason) {
        this.testString = testString;
        this.assertion = assertion;
        this.skipReason = skipReason;
    }
}
