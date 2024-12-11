package iq;

import driver.TestCase;
import iq.base.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Prod2Test extends TestBase {
    public Prod2Test(TestCase testCase, String testSetName, String testCaseName) {
        super(testCase, testSetName, testCaseName);
    }

    @Parameterized.Parameters(name = "[{1}] {2}")
    public static Iterable<Object[]> data() throws Exception {
        return getData("prod/[k-zK-Z]");
    }

    @Test(timeout = 1000000)
    public void test() {
        testCase();
    }
}
