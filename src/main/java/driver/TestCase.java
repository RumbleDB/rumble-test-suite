package driver;

import net.sf.saxon.s9api.XdmNode;

public class TestCase {
    public String convertedTestString;
    public XdmNode assertion;
    public String caseDependency;

    public TestCase(String convertedTestString, XdmNode assertion, String caseDependency) {
        this.convertedTestString = convertedTestString;
        this.assertion = assertion;
        this.caseDependency = caseDependency;
    }
}
