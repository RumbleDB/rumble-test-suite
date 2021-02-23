package ch.ethz;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();

    // Disable Converter when testing the XQuery parser for Rumble
    public static final boolean TO_CONVERT = false;

    public static final String TEST_SETS_TO_SKIP_FILENAME = "TestSetsToSkip_Item2.txt";

    // Use of converted TestSuite
    public static final boolean USE_CONVERTED_TEST_SUITE = true;
    public static final String OUTPUT_TEST_SUITE_DIRECTORY = "Output_Test_Suite";

    // Enables producing log files
    public static final boolean PRODUCE_LOGS = true;

    // Log files here
    public static final String TEST_CASE_HEADER = "TestSetFileName,Success,Managed,Fails,Skipped,Dependencies,Crashes," +
                                                  "UnsupportedTypes,UnsupportedErrorCodes,Sum,Processed,Matches\n";
    public static final String TEST_CASE_TEMPLATE = "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s\n";

    public static final String TEST_CASE_FILENAME = "Statistics.csv";
    public static final StringBuffer TEST_CASE_SB = new StringBuffer();

    public static final String UNSUPPORTED_TYPE_FILENAME = "UnsupportedTypes.txt";
    public static final StringBuffer UNSUPPORTED_TYPE_SB = new StringBuffer();

    public static final String CRASHED_TESTS_FILENAME = "Crashes.txt";
    public static final StringBuffer CRASHED_TESTS_SB = new StringBuffer();

    public static final String FAILED_TESTS_FILENAME = "Fails.txt";
    public static final StringBuffer FAILED_TESTS_SB = new StringBuffer();

    public static final String DEPENDENCY_TESTS_FILENAME = "Dependencies.txt";
    public static final StringBuffer DEPENDENCY_TESTS_SB = new StringBuffer();

    public static final String UNSUPPORTED_ERRORS_FILENAME = "UnsupportedErrorCodes.txt";
    public static final StringBuffer UNSUPPORTED_ERRORS_SB = new StringBuffer();

    public static final String SKIPPED_TESTS_FILENAME = "Skipped.txt";
    public static final StringBuffer SKIPPED_TESTS_SB = new StringBuffer();

    public static final String SUCCESS_TESTS_FILENAME = "Success.txt";
    public static final StringBuffer SUCCESS_TESTS_SB = new StringBuffer();

    public static final String MANAGED_TESTS_FILENAME = "Managed.txt";
    public static final StringBuffer MANAGED_TESTS_SB = new StringBuffer();

    public static final String BROKEN_TESTS_FILENAME = "BrokenWithLatestImplementation.txt";
    public static final StringBuffer BROKEN_TESTS_SB = new StringBuffer();
}
