package iq;

import driver.TestDriver;
import org.rumbledb.api.Item;
import java.util.List;

public class TestBase {
    protected final List<Item> resultAsList;
    protected final String testSet;
    protected final String testCase;

    public TestBase(List<Item> resultAsList, String testSet, String testCase) {
        this.resultAsList = resultAsList;
        this.testSet = testSet;
        this.testCase = testCase;
    }

    public static Iterable<Object[]> getData(String testSuite) throws Exception {
        TestDriver testDriver = new TestDriver();
        testDriver.execute(testSuite);
        return testDriver.getAllTests();
    }

    public void testCase(List<Item> resultAsList) {
        assert resultAsList.size() == 1;
        assert resultAsList.get(0).isBoolean();
        assert (resultAsList.get(0).getBooleanValue());
    }
}
