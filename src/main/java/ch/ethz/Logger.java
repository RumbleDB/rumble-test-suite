package ch.ethz;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Logger {
    private static String logDirectoryName = "stevanresults";

    public Logger(){
        Path logDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(logDirectoryName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        Path logSubDirectoryPath = logDirectoryPath.resolve(timeStamp);
        File logSubDirectory = new File(logSubDirectoryPath.toString());
        if (!logSubDirectory.exists())
            logSubDirectory.mkdirs();

    }
//
//    public void logTestCaseStats(){
//        Constants.TEST_CASE_SB.append(String.format(Constants.TEST_CASE_TEMPLATE, ())
//    }

}
