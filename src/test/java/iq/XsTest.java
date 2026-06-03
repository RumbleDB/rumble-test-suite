package iq;

import evaluation.CollectedTestCase;
import org.junit.jupiter.api.DisplayName;
import iq.base.TestBase;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class XsTest extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("xs").stream();
    }

    @DisplayName("test")
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("data")
    @Timeout(value = 1000000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
