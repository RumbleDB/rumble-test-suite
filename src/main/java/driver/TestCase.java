package driver;

import net.sf.saxon.s9api.XdmNode;

public class TestCase {
    public String convertedTestString;
    public XdmNode assertion;

    public TestCase(String convertedTestString, XdmNode assertion) {
        this.convertedTestString = convertedTestString;
        this.assertion = assertion;
    }
}
