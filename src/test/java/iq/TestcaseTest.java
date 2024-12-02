package iq;

import converter.TestConverter;
import driver.TestDriver;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.rumbledb.api.Item;

import java.util.List;
import org.junit.Test;



@RunWith(Parameterized.class)
public class TestcaseTest {

    private final List<Item> resultAsList;

    @Parameterized.Parameters(name = "{1}_{2}: {0}")
    public static Iterable<Object[]> data() throws Exception {
        TestConverter testConverter = new TestConverter();
        testConverter.execute();
        TestDriver testDriver = new TestDriver();
        testDriver.execute();
        return testDriver.allTests;
    }



    public TestcaseTest(List<Item> resultAsList, String testSetName, String testCaseName) {
        this.resultAsList = resultAsList;
    }

    @Test
    public void test() {
        assert resultAsList.size() == 1;
        assert resultAsList.get(0).isBoolean();
        assert (resultAsList.get(0).getBooleanValue());
    }
}
