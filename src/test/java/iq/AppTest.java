package iq;

import evaluation.CollectedTestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import iq.base.TestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class AppTest extends TestBase {
    public static Stream<CollectedTestCase> data() throws Exception {
        return getData("app").stream();
    }

    @DisplayName("test")
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("data")
    @Timeout(value = 10, unit = TimeUnit.MINUTES) /// App test contains a sudoku solver, that takes about 9 minutes
                                                  /// to finish
    public void test(CollectedTestCase testCase) {
        testCase(testCase);
    }
}
