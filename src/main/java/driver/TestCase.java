package driver;

import net.sf.saxon.s9api.XdmNode;

public class TestCase {
    public String convertedTestString;
    public XdmNode assertion;
    public String skipReason;

    public TestCase(String convertedTestString, XdmNode assertion, String skipReason) {
        this.convertedTestString = convertedTestString;
        this.assertion = assertion;
        this.skipReason = skipReason;
    }
}
