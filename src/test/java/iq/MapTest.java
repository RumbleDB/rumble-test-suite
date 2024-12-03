package iq;

import driver.TestDriver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.rumbledb.api.Item;
import java.util.List;

@RunWith(Parameterized.class)
public class MapTest extends TestBase {
    public MapTest(List<Item> resultAsList, String testSet, String testCase) {
        super(resultAsList, testSet, testCase);
    }

    @Parameterized.Parameters(name = "{1} -> {2}")
    public static Iterable<Object[]> data() throws Exception {
        return getData("map");
    }

    @Test(timeout = 1000000)
    public void test() {
        testCase(this.resultAsList);
    }
}
