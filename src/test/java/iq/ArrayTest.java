package iq;

import evaluation.CollectedTestCase;
import iq.base.TestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class ArrayTest extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("array").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
