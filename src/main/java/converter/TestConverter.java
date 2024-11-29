package converter;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import driver.Constants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestConverter {
    private final String catalogFileName = "catalog.xml";
    private Path testsRepositoryDirectoryPath;
    private static Path outputSubDirectoryPath;
    private final String nameSpace = "http://www.w3.org/2010/09/qt-fots-catalog";
    private int testCasesOutputted = 0;
    private String catalogContent;

    void execute() throws Exception {
        getTestsRepository();
        createOutputDirectory();

        processCatalog(new File(testsRepositoryDirectoryPath.resolve(catalogFileName).toString()));
    }

    public void getTestsRepository() throws IOException, InterruptedException {
        System.out.println("Running sh script to obtain the required tests repository!");

        String testsRepositoryScriptFileName = "get-tests-repository.sh";
        ProcessBuilder pb = new ProcessBuilder(
                Constants.WORKING_DIRECTORY_PATH.resolve(testsRepositoryScriptFileName).toString()
        );

        Process p = pb.start();
        final int exitValue = p.waitFor();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        String line;

        if (exitValue == 0) {
            while ((line = stdout.readLine()) != null) {
                System.out.println(line);
            }
        } else {
            while ((line = stderr.readLine()) != null) {
                System.out.println(line);
            }
        }
        testsRepositoryDirectoryPath = driver.Constants.WORKING_DIRECTORY_PATH.resolve("qt3tests");
        System.out.println("Tests repository obtained!");
    }

    private void createOutputDirectory() {
        Path outputDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(Constants.OUTPUT_TEST_SUITE_DIRECTORY);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        outputSubDirectoryPath = outputDirectoryPath.resolve(timeStamp);
        File outputSubDirectory = new File(outputSubDirectoryPath.toString());
        if (!outputSubDirectory.exists())
            outputSubDirectory.mkdirs();
    }


    private void processCatalog(File catalogFile) throws Exception {
        Processor testDriverProcessor = new Processor(false);
        DocumentBuilder catalogBuilder = testDriverProcessor.newDocumentBuilder();
        catalogBuilder.setLineNumbering(true);
        XdmNode catalogNode = catalogBuilder.build(catalogFile);
        catalogContent = catalogNode.toString();

        XPathCompiler xpc = testDriverProcessor.newXPathCompiler();
        xpc.setLanguageVersion("3.1");
        xpc.setCaching(true);
        // Yes we do need Namespace. It is required to run evaluateSingle and luckily it is hardcoded in QT3TestDriverHE
        xpc.declareNamespace("", nameSpace);
        List<XdmNode> testSets = catalogNode.select(Steps.descendant("test-set")).asList();
        for (XdmNode testSet : testSets) {
            this.processTestSet(catalogBuilder, xpc, testSet);
        }
        System.out.println("Included Test Sets: " + testSets.size());
        System.out.println("Included Test Cases: " + testCasesOutputted);

        // CREATE OUTPUT CATALOG
        Path testSetOutputFilePath = outputSubDirectoryPath.resolve(catalogFileName);
        PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());
        printWriter.write(catalogContent);
        printWriter.close();
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode)
            throws Exception {

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        StringBuffer testSetBody = new StringBuffer();
        Iterator<XdmNode> iterator = testSetDocNode.children().iterator();
        XdmNode root = iterator.next();

        while (!root.children().iterator().hasNext())
            root = iterator.next();

        String testSetContent = root.toString();
        Matcher testSetHeader = Pattern.compile("<test-set([^<]*)>", Pattern.DOTALL).matcher(testSetContent);
        if (testSetHeader.find())
            testSetBody.append("<test-set").append(testSetHeader.group(1)).append(">");
        else
            throw new Exception("find didnt work");

        for (XdmNode child : root.children()) {
            if (child.getUnderlyingNode().getDisplayName().equals("test-case"))
                testSetBody.append(this.processTestCase(child, xpc));
            else {
                String otherNode = child.toString();
                otherNode = otherNode.replace(" xmlns=\"" + nameSpace + "\"", "");
                testSetBody.append(otherNode);
            }
        }
        createOutputXMLFile(testSetFileName, testSetBody);
    }

    private String processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        testCasesOutputted++;
        String testCaseBody = testCase.toString();

        Pattern testRegex = Pattern.compile("<test>(.*)</test>", Pattern.DOTALL);
        Matcher testMatcher = testRegex.matcher(testCaseBody);
        if (!testMatcher.find())
            return "";
        String convertedTestString = testMatcher.group(1);
        testCaseBody = testMatcher.replaceAll(Matcher.quoteReplacement("<test>" + convertedTestString + "</test>"));
        Pattern resultRegex = Pattern.compile("<result>(.*)</result>", Pattern.DOTALL);
        Matcher resultMatcher = resultRegex.matcher(testCaseBody);
        if (!resultMatcher.find())
            System.exit(1);
        String expectedResult = resultMatcher.group(1);
        testCaseBody = resultMatcher.replaceAll(
            Matcher.quoteReplacement("<result>" + expectedResult + "</result>")
        );
        testCaseBody = testCaseBody.replace(" xmlns=\"" + nameSpace + "\"", "");
        return testCaseBody;
    }

    private void createOutputXMLFile(String testSetFileName, StringBuffer testSetBody) throws IOException {
        String[] directoryAndFile = testSetFileName.split("/");
        Path testSetOutputDirectoryPath = outputSubDirectoryPath.resolve(directoryAndFile[0]);
        File prefixSubDirectory = new File(testSetOutputDirectoryPath.toString());
        if (!prefixSubDirectory.exists())
            prefixSubDirectory.mkdirs();

        Path testSetOutputFilePath = testSetOutputDirectoryPath.resolve(directoryAndFile[1]);
        PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());

        printWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        printWriter.close();
        Files.write(
            testSetOutputFilePath,
            testSetBody.toString().getBytes(),
            StandardOpenOption.APPEND
        );
        String endRootTag = "</test-set>";
        Files.write(testSetOutputFilePath, endRootTag.getBytes(), StandardOpenOption.APPEND);
    }
}
