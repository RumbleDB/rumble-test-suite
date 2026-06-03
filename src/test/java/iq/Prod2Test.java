package iq;

import evaluation.CollectedTestCase;
import iq.base.TestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class Prod2Test extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("prod/[k-zK-Z]").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
