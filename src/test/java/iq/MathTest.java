package iq;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import evaluation.CollectedTestCase;
import iq.base.TestBase;

public class MathTest extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("math").stream();
    }

    @DisplayName("test")
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("data")
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
