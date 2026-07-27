package evaluation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * contains constants used throughout the code like
 * Test cases to skip, Test sets to skip,
 * Type conversions, Unsupported Types
 * Supported error codes
 */
public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();

    /**
     * Testsets that we decide to skip fully
     */
    public static final List<String> skippedTestSets = List.of();

    /**
     * Individual testcases that we decide to skip
     */
    public static final List<String> skippedGeneralTestCases = List.of();

    /**
     * Individual testcases that we decide to skip only in JSONiq
     */
    public static final List<String> skippedJSONIQTestCases = List.of();

    /**
     * Error codes that we assume indicate a skip reason. When such a exception is thrown we mark the testcase as
     * skipped instead.
     */
    public static final List<String> skipReasonErrorCodes = List.of(
        "XPST0051", // type not implemented
        "XPST0003" // parser failed, assuming that feature is not implemented
    );

    /**
     * Error codes that we assume indicate a skip reason. When such a exception is thrown we mark the testcase as
     * skipped instead.
     * These are the error codes used when evaluating the test cases with the XQuery parser.
     */
    public static final List<String> xQuerySkipReasonErrorCodes = List.of(
        // XPST0017 is not in the list of error codes, as we treat it as an actual test case result
        // this allows us to test also that such static errors are thrown for test cases that expect such error code
        "XPST0051" // type not implemented
    // XPST0003 is not in the list of error codes, as we treat it as an actual test case result
    // this allows us to test also that parsing errors are thrown for test cases that expect such error code
    );

}
