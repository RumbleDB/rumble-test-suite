package iq;

import evaluation.TestCase;
import iq.base.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class XQueryMathTest extends TestBase {
    public XQueryMathTest(TestCase testCase, String testSetName, String testCaseName) {
        super(testCase, testSetName, testCaseName, true);
    }

    @Parameterized.Parameters(name = "[{1}] {2}")
    public static Iterable<Object[]> data() throws Exception {
        return getData("math");
    }

    @Test(timeout = 1000000)
    public void test() {
        testCase();
    }
}
