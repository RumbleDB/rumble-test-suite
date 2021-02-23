package converter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();
    public static final String TEST_SETS_TO_SKIP_FILENAME = "TestSetsToSkip_Item1.txt";
    public static final String TEST_CASES_TO_SKIP_FILENAME = "TestCasesToSkip.txt";
    public static final String OUTPUT_TEST_SUITE_DIRECTORY = "Output_Test_Suite";
    public static final boolean PRODUCE_OUTPUT = true;
}
