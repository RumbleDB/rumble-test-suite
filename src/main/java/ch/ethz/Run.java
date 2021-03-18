package ch.ethz;


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

public class Run {
    private static String logDirectoryName = "stevanresults";
    private static Path logSubDirectoryPath;
    public static void main(String[] args) throws Exception {
        installShutdownHook();
        TestDriver testDriver = new TestDriver();
        testDriver.execute();
    }

    private static void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                if (Constants.PRODUCE_LOGS) {
                    Path logDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(logDirectoryName);

                    // For comparing with the previous statistics
                    File[] allLogDirectories = new File(logDirectoryPath.toString()).listFiles();
                    Arrays.sort(allLogDirectories, Comparator.reverseOrder());
                    Path lastSuccessPath = allLogDirectories[0].toPath().resolve(Constants.SUCCESS_TESTS_FILENAME);
                    Path lastManagedPath = allLogDirectories[0].toPath().resolve(Constants.MANAGED_TESTS_FILENAME);
                    Path lastCrashesPath = allLogDirectories[0].toPath().resolve(Constants.CRASHED_TESTS_FILENAME);
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
                    List<String> allCurrentPassedTests = new ArrayList<String>(Arrays.asList(Constants.SUCCESS_TESTS_SB.toString().split("\n")));
                    allCurrentPassedTests.addAll(Arrays.asList(Constants.MANAGED_TESTS_SB.toString().split("\n")));

                    if (allPreviousPassedTests != null){
                        for (String passedTest : allPreviousPassedTests){
                            if (!allCurrentPassedTests.contains(passedTest) && !passedTest.contains("List of all test cases")){
                                Constants.BROKEN_TESTS_SB.append(passedTest + "\n");
                            }
                        }
                    }

                    // Slightly repetitive, might be refactored
                    List<String> allCurrentCrashedTests = new ArrayList<String>(Arrays.asList(Constants.CRASHED_TESTS_SB.toString().split("\n")));
                    if (allPreviousCrashedTests != null){
                        Constants.BROKEN_TESTS_SB.append("\n" + "Tests that were not crashing before, but are now and not in list above:" + "\n");
                        for (String crashedTest : allCurrentCrashedTests)
                            if (!allPreviousCrashedTests.contains(crashedTest) && !crashedTest.contains("List of all test cases") && !allPreviousPassedTests.contains(crashedTest)){
                                Constants.BROKEN_TESTS_SB.append(crashedTest + "\n");
                            }
                    }

                    // Create directory for new statistics
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
                    logSubDirectoryPath = logDirectoryPath.resolve(timeStamp);
                    File logSubDirectory = new File(logSubDirectoryPath.toString());
                    if (!logSubDirectory.exists())
                        logSubDirectory.mkdirs();

                    Log(Constants.TEST_CASE_FILENAME, Constants.TEST_CASE_HEADER, Constants.TEST_CASE_SB);
                    Log(Constants.UNSUPPORTED_TYPE_FILENAME, "List of all test cases:\n", Constants.UNSUPPORTED_TYPE_SB);
                    Log(Constants.CRASHED_TESTS_FILENAME, "List of all test cases:\n", Constants.CRASHED_TESTS_SB);
                    Log(Constants.FAILED_TESTS_FILENAME, "List of all test cases:\n", Constants.FAILED_TESTS_SB);
                    Log(Constants.DEPENDENCY_TESTS_FILENAME, "List of all test cases:\n", Constants.DEPENDENCY_TESTS_SB);
                    Log(Constants.UNSUPPORTED_ERRORS_FILENAME, "List of all test cases:\n", Constants.UNSUPPORTED_ERRORS_SB);
                    Log(Constants.SKIPPED_TESTS_FILENAME, "List of all test cases:\n", Constants.SKIPPED_TESTS_SB);
                    Log(Constants.SUCCESS_TESTS_FILENAME, "List of all test cases:\n", Constants.SUCCESS_TESTS_SB);
                    Log(Constants.MANAGED_TESTS_FILENAME, "List of all test cases:\n", Constants.MANAGED_TESTS_SB);
                    Log(Constants.BROKEN_TESTS_FILENAME, "List of test cases that were passing before but not anymore:\n", Constants.BROKEN_TESTS_SB);
                }
            }
        });
    }

    private static void Log(String Filename, String header, StringBuffer stringBuffer){
        try {
            String testCaseFilePath = logSubDirectoryPath.resolve(Filename).toString();
            PrintWriter summedWorkerThreads = new PrintWriter(testCaseFilePath);
            summedWorkerThreads.write(header);
            summedWorkerThreads.close();
            Files.write(Paths.get(testCaseFilePath), stringBuffer.toString().getBytes(), StandardOpenOption.APPEND);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
