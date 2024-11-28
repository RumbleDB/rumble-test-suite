package converter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path WORKING_DIRECTORY_PATH = Paths.get("").toAbsolutePath();
    public static final String OUTPUT_TEST_SUITE_DIRECTORY = "Output_Test_Suite";
    public static final boolean PRODUCE_OUTPUT = true;
}
