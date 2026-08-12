package iq;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import evaluation.CollectedTestCase;
import iq.base.TestBase;

public class Fn2Test extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("fn/[k-zK-Z]").stream();
    }

    @DisplayName("test")
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("data")
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
