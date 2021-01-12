package converter;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class TestConverter {
    private String testsRepositoryScriptFileName = "get-tests-repository.sh";
    private String catalogFileName = "catalog.xml";
    private Path testsRepositoryDirectoryPath;
    private List<String> testSetsToSkip = null;
    private List<String> testCasesToSkip = null;
    private static Path outputSubDirectoryPath;
    private int testSetsOutputted = 0;
    private String nameSpace = "http://www.w3.org/2010/09/qt-fots-catalog";
    private int testCasesOutputted = 0;

    void execute() {
        getTestsRepository();
        loadTestsToSkip();
        if (Constants.PRODUCE_OUTPUT)
            createOutputDirectory();

        try {
            processCatalog(new File(testsRepositoryDirectoryPath.resolve(catalogFileName).toString()));
        } catch (SaxonApiException e) {
            e.printStackTrace();
        }
    }

    private void getTestsRepository(){
        System.out.println("Running sh script to obtain the required tests repository!");
        try {
            ProcessBuilder pb = new ProcessBuilder(Constants.WORKING_DIRECTORY_PATH.resolve(testsRepositoryScriptFileName).toString());

            Process p = pb.start();
            final int exitValue = p.waitFor();

            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line = "";
            String testsDirectory = "";

            if (exitValue == 0) {
                while ((line = stdout.readLine()) != null) {
                    System.out.println(line);

                    // Name of the directory is parametrized in testsRepositoryScriptName
                    testsDirectory = line;
                }
            } else {
                while ((line = stderr.readLine()) != null) {
                    System.out.println(line);
                }
            }
            testsRepositoryDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(testsDirectory);
            System.out.println(testsRepositoryDirectoryPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Tests repository obtained!");
    }

    private void loadTestsToSkip() {


        Path testSetsToSkipPath = Constants.WORKING_DIRECTORY_PATH.resolve(Constants.TEST_SETS_TO_SKIP_FILENAME);
        Path testCasesToSkipPath = Constants.WORKING_DIRECTORY_PATH.resolve(Constants.TEST_CASES_TO_SKIP_FILENAME);
        Charset charset = Charset.defaultCharset();

        try {
            testSetsToSkip = Files.readAllLines(testSetsToSkipPath, charset);
            testCasesToSkip = Files.readAllLines(testCasesToSkipPath, charset);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createOutputDirectory() {
        Path outputDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve(Constants.OUTPUT_TEST_SUITE_DIRECTORY);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        outputSubDirectoryPath = outputDirectoryPath.resolve(timeStamp);
        File outputSubDirectory = new File(outputSubDirectoryPath.toString());
        if (!outputSubDirectory.exists())
            outputSubDirectory.mkdirs();
    }

    private void processCatalog(File catalogFile) throws SaxonApiException {
        Processor testDriverProcessor = new Processor(false);

        // TODO check if it is okay to use the default Tiny tree or not
        // catalogBuilder.setTreeModel(this.treeModel);
        DocumentBuilder catalogBuilder = testDriverProcessor.newDocumentBuilder();
        catalogBuilder.setLineNumbering(true);
        XdmNode catalogNode = catalogBuilder.build(catalogFile);

        XPathCompiler xpc = testDriverProcessor.newXPathCompiler();
        xpc.setLanguageVersion("3.1");
        xpc.setCaching(true);
        // Yes we do need Namespace. It is required to run evaluateSingle and luckily it is hardcoded in QT3TestDriverHE
        xpc.declareNamespace("", nameSpace);

        System.out.println("Skipped Test Sets: " + testSetsToSkip.size());
        System.out.println("Skipped Test Cases: " + testCasesToSkip.size());
        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            this.processTestSet(catalogBuilder, xpc, testSet);
        }
        System.out.println("Included Test Sets: " + testSetsOutputted);
        System.out.println("Included Test Cases: " + testCasesOutputted);
        System.out.println("Misspelled Test Sets: " + testSetsToSkip);
        System.out.println("Misspelled Test Sets: " + testCasesToSkip);    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode) throws SaxonApiException{

        // TODO skip creating an Environment - its mainly for HE, EE, PE I think

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        if (!testSetsToSkip.contains(testSetFileName)) {
            testSetsOutputted++;
            StringBuffer testSetBody = new StringBuffer();
            XdmNode root = testSetDocNode.children().iterator().next();

            // TODO This does not quite help us
//            XdmNode description = (XdmNode) xpc.evaluateSingle("description", root);
//            printWriter.write(description.toString());

            // TODO we can separate them like this
//            for (XdmNode environment : root.select(Steps.child("link")).asList()) {
//                printWriter.write(environment.toString());
//            }
//            for (XdmNode environment : root.select(Steps.child("description")).asList()) {
//                printWriter.write(environment.toString());
//            }
//            for (XdmNode environment : root.select(Steps.child("environment")).asList()) {
//                printWriter.write(environment.toString());
//            }

            // TODO checkout if we can use this to better do toString()
//            NodeInfo testDocNodeInfo = testSetDocNode.getUnderlyingNode();
//            int testDocNode = testDocNodeInfo.getNodeKind();
//            int rootNode = root.getUnderlyingNode().getNodeKind();
//            int environment = root.select(Steps.child("environment")).asList().get(0).getUnderlyingNode().getNodeKind();
//            int testcase = testSetDocNode.select(Steps.descendant("test-case")).asList().get(0).getUnderlyingNode().getNodeKind();


            // TODO can't make a new Node
//            QName bla = root.getNodeName();
//            XdmNode a = new XdmNode()

            for (XdmNode child : root.children()){
                if (child.getUnderlyingNode().getDisplayName().equals("test-case"))
                //if (child.getNodeName().getLocalName().equals("test-case"))
                    testSetBody.append(this.processTestCase(child, xpc));
                else {
                    String otherNode = child.toString();
                    otherNode = otherNode.replace(" xmlns=\"" + nameSpace + "\"", "");
                    testSetBody.append(otherNode);
                }
            }

            if (Constants.PRODUCE_OUTPUT)
                createOutputXMLFile(testSetFileName, testSetBody);
        }
        else {
            testSetsToSkip.remove(testSetFileName);
        }
    }

    private void createOutputXMLFile(String testSetFileName, StringBuffer testSetBody) {
        try {
            String [] directoryAndFile = testSetFileName.split("/");
            Path testSetOutputDirectoryPath = outputSubDirectoryPath.resolve(directoryAndFile[0]);
            File prefixSubDirectory = new File(testSetOutputDirectoryPath.toString());
            if (!prefixSubDirectory.exists())
                prefixSubDirectory.mkdirs();

            Path testSetOutputFilePath = testSetOutputDirectoryPath.resolve(directoryAndFile[1]);
            PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());

            // TODO check if I can copy it somehow without hardcoding
//            Pattern pattern = Pattern.compile("<test-set(.*?)>");
//            Matcher matcher = pattern.matcher(testSet);
//            if (matcher.find())
//            {
//                System.out.println(matcher.group(1));
//            }
            String header = "<?xml version=\"1.0\" encoding=\"us-ascii\"?>\n" +
                            "<test-set xmlns=\"" + nameSpace + "\" name=\"" + directoryAndFile[1] + "\">\n";
            printWriter.write(header);
            printWriter.close();
            Files.write(Paths.get(testSetOutputFilePath.toString()), testSetBody.toString().getBytes(), StandardOpenOption.APPEND);
            String endRootTag = "</test-set>";
            Files.write(Paths.get(testSetOutputFilePath.toString()), endRootTag.getBytes(), StandardOpenOption.APPEND);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private String processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String testCaseName = testCase.attribute("name");
        if (!testCasesToSkip.contains(testCaseName)) {
            testCasesOutputted++;
            String testCaseBody = testCase.toString();
            XdmNode testNode = testCase.select(Steps.child("test")).asNode();
            String convertedTestString = this.Convert(testNode.getStringValue());
            XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);
            String expectedResult = Convert(assertion.getStringValue());

            // TODO maybe same issue like with header
            testCaseBody = testCaseBody.replace(" xmlns=\"" + nameSpace + "\"", "");
            testCaseBody = testCaseBody.replace("<test>([^<]*)</test>", "<test>" + convertedTestString + "</test>");
            testCaseBody = testCaseBody.replace("<result>([^<]*)</result>", "<result>\n" + expectedResult + "\n</result>");
            return testCaseBody;
        }
        else{
            testCasesToSkip.remove(testCaseName);
            return "";
        }
    }

    private String Convert(String testString){
        return testString;
    }
}
