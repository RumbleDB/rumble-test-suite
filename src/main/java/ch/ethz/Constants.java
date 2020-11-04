package ch.ethz;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();

    // Enables producing log files
    public static final boolean PRODUCE_LOGS = false;

    // Log files here
    public static final String TEST_CASE_HEADER = "TestSetFileName,Success,Fails,Skipped,Dependencies,Crashes," +
                                                  "UnsupportedTypes,UnsuppportedErrorCodes,Sum,Processed,Matches\n";
    public static final String TEST_CASE_TEMPLATE = "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s\n";

    public static final String TEST_CASE_FILENAME = "Test_Case_Statistics.csv";
    public static final StringBuffer TEST_CASE_SB = new StringBuffer();

    public static final String UNSUPPORTED_TYPE_FILENAME = "Test_Cases_With_Unsupported_Type.txt";
    public static final StringBuffer UNSUPPORTED_TYPE_SB = new StringBuffer();

    public static final String CRASHED_TESTS_FILENAME = "Test_Cases_Throwing_Exceptions.txt";
    public static final StringBuffer CRASHED_TESTS_SB = new StringBuffer();

    public static final String FAILED_TESTS_FILENAME = "Test_Cases_Failing_Assertion.txt";
    public static final StringBuffer FAILED_TESTS_SB = new StringBuffer();

    public static final String DEPENDENCY_TESTS_FILENAME = "Test_Cases_With_Dependency.txt";
    public static final StringBuffer DEPENDENCY_TESTS_SB = new StringBuffer();

    public static final String UNSUPPORTED_ERRORS_FILENAME = "Test_Cases_With_Unsupported_Error_Codes.txt";
    public static final StringBuffer UNSUPPORTED_ERRORS_SB = new StringBuffer();

    public static final String SKIPPED_TESTS_FILENAME = "Test_Case_With_Irregular_Dependency.txt";
    public static final StringBuffer SKIPPED_TESTS_SB = new StringBuffer();
}
