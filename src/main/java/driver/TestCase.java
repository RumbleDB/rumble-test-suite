package driver;

import net.sf.saxon.s9api.XdmNode;

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
