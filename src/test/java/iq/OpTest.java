package iq;

import driver.TestDriver;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.rumbledb.api.Item;

import java.util.List;
import org.junit.Test;

@RunWith(Parameterized.class)
public class OpTest extends TestBase {
    public OpTest(List<Item> resultAsList, String testSet, String testCase) {
        super(resultAsList, testSet, testCase);
    }

    @Parameterized.Parameters(name = "{1}_{2}: {0}")
    public static Iterable<Object[]> data() throws Exception {
        TestDriver testDriver = new TestDriver();
        testDriver.execute("prod");
        return testDriver.allTests;
    }

    @Test(timeout = 1000000)
    public void test() throws Throwable {
        testCase(this.resultAsList);
    }
}
