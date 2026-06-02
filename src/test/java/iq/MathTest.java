package iq;

import evaluation.CollectedTestCase;
import iq.base.TestBase;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MathTest extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("math").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    @Timeout(value = 1000000, unit = TimeUnit.MILLISECONDS)
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
