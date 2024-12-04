package driver;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class Logger {
    int numberOfFails;
    int numberOfSuccess;
    int numberOfSkipped;
    int numberOfCrashes;
    int numberOfDependencies;
    int numberOfUnsupportedTypes;
    int numberOfUnsupportedErrorCodes;
    int numberOfProcessedTestCases;
    int numberOfManaged;

    public final StringBuffer TEST_CASE_SB = new StringBuffer();
    public final StringBuffer UNSUPPORTED_TYPE_SB = new StringBuffer();
    public final StringBuffer CRASHED_TESTS_SB = new StringBuffer();
    public final StringBuffer FAILED_TESTS_SB = new StringBuffer();
    public final StringBuffer DEPENDENCY_TESTS_SB = new StringBuffer();
    public final StringBuffer UNSUPPORTED_ERRORS_SB = new StringBuffer();
    public final StringBuffer SKIPPED_TESTS_SB = new StringBuffer();
    public final StringBuffer SUCCESS_TESTS_SB = new StringBuffer();
    public final StringBuffer MANAGED_TESTS_SB = new StringBuffer();
    public final StringBuffer BROKEN_TESTS_SB = new StringBuffer();

    public void resetCounters() {
        numberOfSuccess = 0;
        numberOfFails = 0;
        numberOfSkipped = 0;
        numberOfDependencies = 0;
        numberOfCrashes = 0;
        numberOfUnsupportedTypes = 0;
        numberOfUnsupportedErrorCodes = 0;
        numberOfProcessedTestCases = 0;
        numberOfManaged = 0;
    }

    public void LogSuccess(String lineText) {
        numberOfSuccess++;
        SUCCESS_TESTS_SB.append(lineText + "\n");
    }

    public void LogManaged(String lineText) {
        numberOfManaged++;
        MANAGED_TESTS_SB.append(lineText + "\n");
    }

    public void LogFail(String lineText) {
        numberOfFails++;
        FAILED_TESTS_SB.append(lineText + "\n");
    }

    public void LogSkipped(String lineText) {
        numberOfSkipped++;
        SKIPPED_TESTS_SB.append(lineText + "\n");
    }

    public void LogDependency(String lineText) {
        numberOfDependencies++;
        DEPENDENCY_TESTS_SB.append(lineText + "\n");
    }

    public void LogCrash(String lineText) {
        numberOfCrashes++;
        CRASHED_TESTS_SB.append(lineText + "\n");
    }

    public void LogUnsupportedType(String lineText) {
        numberOfUnsupportedTypes++;
        UNSUPPORTED_TYPE_SB.append(lineText + "\n");
    }

    public void LogUnsupportedErrorCode(String lineText) {
        numberOfUnsupportedErrorCodes++;
        UNSUPPORTED_ERRORS_SB.append(lineText + "\n");
    }

    public void finishTestSetResults(String testSetName) {
        System.out.println(
            testSetName
                + " Success: "
                + numberOfSuccess
                + " Managed: "
                + numberOfManaged
                + " Fails: "
                + numberOfFails
                +
                " Skipped: "
                + numberOfSkipped
                + " Dependencies: "
                + numberOfDependencies
                +
                " Crashes: "
                + numberOfCrashes
                + " UnsupportedTypes: "
                + numberOfUnsupportedTypes
                +
                " UnsupportedErrors: "
                + numberOfUnsupportedErrorCodes
        );
        int sum = (numberOfSuccess
            + numberOfManaged
            + numberOfFails
            + numberOfSkipped
            + numberOfDependencies
            + numberOfCrashes
            + numberOfUnsupportedTypes
            + numberOfUnsupportedErrorCodes);
        String checkMatching = sum == numberOfProcessedTestCases ? "OK" : "NOT";
        TEST_CASE_SB.append(
            String.format(
                "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s\n",
                testSetName,
                numberOfSuccess,
                numberOfManaged,
                numberOfFails,
                numberOfSkipped,
                numberOfDependencies,
                numberOfCrashes,
                numberOfUnsupportedTypes,
                numberOfUnsupportedErrorCodes,
                sum,
                numberOfProcessedTestCases,
                checkMatching
            )
        );
    }

    public void logResults() throws IOException {
        Path logDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve("results");

        // For comparing with the previous statistics
        File[] allLogDirectories = new File(logDirectoryPath.toString()).listFiles();
        Arrays.sort(allLogDirectories, Comparator.reverseOrder());
        Path lastSuccessPath = allLogDirectories[0].toPath().resolve("Success.txt");
        Path lastManagedPath = allLogDirectories[0].toPath().resolve("Managed.txt");
        Path lastCrashesPath = allLogDirectories[0].toPath().resolve("Crashes.txt");
        Charset charset = Charset.defaultCharset();
        List<String> allPreviousPassedTests = null;
        List<String> allPreviousCrashedTests = null;
        try {
            allPreviousPassedTests = Files.readAllLines(lastSuccessPath, charset);
            allPreviousPassedTests.addAll(Files.readAllLines(lastManagedPath, charset));
            allPreviousCrashedTests = Files.readAllLines(lastCrashesPath, charset);
        } catch (IOException e) {
            // First time it will fail and we will check for null
        }
        // Instantiate with new ArrayList, otherwise you cannot do addAll since asList returns non-resizable
        List<String> allCurrentPassedTests = new ArrayList<>(
                Arrays.asList(SUCCESS_TESTS_SB.toString().split("\n"))
        );
        allCurrentPassedTests.addAll(Arrays.asList(MANAGED_TESTS_SB.toString().split("\n")));

        if (allPreviousPassedTests != null) {
            for (String passedTest : allPreviousPassedTests) {
                if (
                    !allCurrentPassedTests.contains(passedTest)
                        && !passedTest.contains("List of all test cases")
                ) {
                    BROKEN_TESTS_SB.append(passedTest + "\n");
                }
            }
        }

        // Slightly repetitive, might be refactored
        List<String> allCurrentCrashedTests = new ArrayList<>(
                Arrays.asList(CRASHED_TESTS_SB.toString().split("\n"))
        );
        if (allPreviousCrashedTests != null) {
            BROKEN_TESTS_SB.append(
                "\n" + "Tests that were not crashing before, but are now and not in list above:" + "\n"
            );
            for (String crashedTest : allCurrentCrashedTests)
                if (
                    !allPreviousCrashedTests.contains(crashedTest)
                        && !crashedTest.contains("List of all test cases")
                        && !allPreviousPassedTests.contains(crashedTest)
                ) {
                    BROKEN_TESTS_SB.append(crashedTest + "\n");
                }
        }

        // Create directory for new statistics
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        Path logSubDirectoryPath = logDirectoryPath.resolve(timeStamp);
        File logSubDirectory = new File(logSubDirectoryPath.toString());
        if (!logSubDirectory.exists())
            logSubDirectory.mkdirs();

        Log(
            logSubDirectoryPath.resolve("Statistics.csv").toString(),
            "TestSetFileName,Success,Managed,Fails,Skipped,Dependencies,Crashes,UnsupportedTypes,UnsupportedErrorCodes,Sum,Processed,Matches\n",
            TEST_CASE_SB
        );
        Log(
            logSubDirectoryPath.resolve("UnsupportedTypes.txt").toString(),
            "List of all test cases:\n",
            UNSUPPORTED_TYPE_SB
        );
        Log(
            logSubDirectoryPath.resolve("Crashes.txt").toString(),
            "List of all test cases:\n",
            CRASHED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Fails.txt").toString(),
            "List of all test cases:\n",
            FAILED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Dependencies.txt").toString(),
            "List of all test cases:\n",
            DEPENDENCY_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("UnsupportedErrorCodes.txt").toString(),
            "List of all test cases:\n",
            UNSUPPORTED_ERRORS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Skipped.txt").toString(),
            "List of all test cases:\n",
            SKIPPED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Success.txt").toString(),
            "List of all test cases:\n",
            SUCCESS_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Managed.txt").toString(),
            "List of all test cases:\n",
            MANAGED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("BrokenWithLatestImplementation.txt").toString(),
            "List of test cases that were passing before but not anymore:\n",
            BROKEN_TESTS_SB
        );
    }

    private static void Log(String testCaseFilePath, String header, StringBuffer stringBuffer) throws IOException {
        PrintWriter summedWorkerThreads = new PrintWriter(testCaseFilePath);
        summedWorkerThreads.write(header);
        summedWorkerThreads.close();
        Files.write(Paths.get(testCaseFilePath), stringBuffer.toString().getBytes(), StandardOpenOption.APPEND);
    }
}
