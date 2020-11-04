package ch.ethz;


import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;

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
