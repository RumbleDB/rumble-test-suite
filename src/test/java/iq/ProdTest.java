package iq;

import driver.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProdTest extends TestBase {
    public ProdTest(TestCase testCase, String testSetName, String testCaseName) {
        super(testCase, testSetName, testCaseName);
    }

    @Parameterized.Parameters(name = "[{1}] {2}")
    public static Iterable<Object[]> data() throws Exception {
        return getData("prod");
    }

    @Test(timeout = 1000000)
    public void test() {
        testCase();
    }
}
