package converter;

import net.sf.saxon.om.NodeInfo;
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

        System.out.println("Skipped: " + testSetsToSkip.size());
        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            this.processTestSet(catalogBuilder, xpc, testSet);
        }
        System.out.println("Included: " + testSetsOutputted);
        System.out.println("Misspelled: " + testSetsToSkip);
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode) throws SaxonApiException{

        // TODO skip creating an Environment - its mainly for HE, EE, PE I think

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        if (!testSetsToSkip.contains(testSetFileName)) {
            testSetsOutputted++;
            if (Constants.PRODUCE_OUTPUT)
                createOutputXMLFile(testSetFileName, testSetDocNode, xpc);
            for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
                this.processTestCase(testCase, xpc);
            }
        }
        else {
            testSetsToSkip.remove(testSetFileName);
        }
    }

    private void createOutputXMLFile(String testSetFileName, XdmNode testSetDocNode, XPathCompiler xpc) {
        try {
            String [] directoryAndFile = testSetFileName.split("/");
            Path testSetOutputDirectoryPath = outputSubDirectoryPath.resolve(directoryAndFile[0]);
            File prefixSubDirectory = new File(testSetOutputDirectoryPath.toString());
            if (!prefixSubDirectory.exists())
                prefixSubDirectory.mkdirs();

            Path testSetOutputFilePath = testSetOutputDirectoryPath.resolve(directoryAndFile[1]);
            PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());

            // TODO check if I can copy it somehow without hardcoding
            String header = "<?xml version=\"1.0\" encoding=\"us-ascii\"?>\n" +
                            "<test-set xmlns=\"" + nameSpace + "\" name=\"" + directoryAndFile[1] + "\">";
            printWriter.write(header);

            XdmNode root = testSetDocNode.children().iterator().next();
//            XdmNode description = (XdmNode) xpc.evaluateSingle("description", root);
//            printWriter.write(description.toString());

//            for (XdmNode environment : root.select(Steps.child("link")).asList()) {
//                printWriter.write(environment.toString());
//            }
//            for (XdmNode environment : root.select(Steps.child("description")).asList()) {
//                printWriter.write(environment.toString());
//            }
//            for (XdmNode environment : root.select(Steps.child("environment")).asList()) {
//                printWriter.write(environment.toString());
//            }

            // TODO check if it is test-case and then find test and result and covert them.
            // TODO For everything else, remove the XSLMNS

            for (XdmNode children : root.children()){
                printWriter.write(children.toString());
            }

            // TODO checkout if we can use this to better do toString()
//            NodeInfo testDocNodeInfo = testSetDocNode.getUnderlyingNode();
//            int testDocNode = testDocNodeInfo.getNodeKind();
//            int rootNode = root.getUnderlyingNode().getNodeKind();
//            int environment = root.select(Steps.child("environment")).asList().get(0).getUnderlyingNode().getNodeKind();
//            int testcase = testSetDocNode.select(Steps.descendant("test-case")).asList().get(0).getUnderlyingNode().getNodeKind();


            // TODO can't make a new Node
            //QName bla = root.getNodeName();
            //XdmNode a = new XdmNode()

            printWriter.close();
            //Files.write(Paths.get(testSetOutputFilePath), stringBuffer.toString().getBytes(), StandardOpenOption.APPEND);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private void processTestCase(XdmNode testCase, XPathCompiler xpc) {
        String testCaseName = testCase.attribute("name");
        if (!testCasesToSkip.contains(testCaseName)) {

        }
    }
}
