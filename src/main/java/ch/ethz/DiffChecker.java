package ch.ethz;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class DiffChecker {
    private static String logDirectoryName = "stevanresults";
    private static String firstFilePathFromResults = "20201129_180351 (implemented assert string equal via result binding compared after \" \" fix was implemented)";
    private static String secondFilePathFromResults = "20201130_165451 (implemented assert string equal via result binding compared before \" \" fix was implemented)";
    private static StringBuffer diffCheckerSB = new StringBuffer();
    private static String diffCheckerDirectoryName = "2 DiffChecker";

    public static void main(String[] args) throws Exception {
        Path logDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(logDirectoryName);
        Path firstFilePath = logDirectoryPath.resolve(firstFilePathFromResults);
        firstFilePath = firstFilePath.resolve(Constants.BROKEN_TESTS_FILENAME);

        Path secondFilePath = logDirectoryPath.resolve(secondFilePathFromResults);
        secondFilePath = secondFilePath.resolve(Constants.BROKEN_TESTS_FILENAME);

        Charset charset = Charset.defaultCharset();
        List<String> allFirstBrokenTests = null;
        List<String> allSecondBrokenTests = null;
        try {
            allFirstBrokenTests = Files.readAllLines(firstFilePath, charset);
            allSecondBrokenTests = Files.readAllLines(secondFilePath, charset);


            for (String firstBrokenTest : allFirstBrokenTests){
                if (!allSecondBrokenTests.contains(firstBrokenTest)){
                    diffCheckerSB.append(firstBrokenTest + "\n");
                }
            }

            diffCheckerSB.append("\n" + "Exists in " + secondFilePath + " but not in " + firstFilePath + "\n");
            for (String secondBrokenTest : allSecondBrokenTests){
                if (!allFirstBrokenTests.contains(secondBrokenTest)){
                    diffCheckerSB.append(secondBrokenTest + "\n");
                }
            }

            Path outputFilePath = logDirectoryPath.resolve(diffCheckerDirectoryName);
            String outputFileName = outputFilePath.resolve("Output.txt").toString();
            PrintWriter printWriter = new PrintWriter(outputFileName);
            printWriter.write("\n" + "Exists in " + firstFilePath + " but not in " + secondFilePath + "\n");
            printWriter.close();
            Files.write(Paths.get(outputFileName), diffCheckerSB.toString().getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            // First time it will fail and we will check for null
        }
    }
}
