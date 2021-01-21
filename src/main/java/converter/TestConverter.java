package converter;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import net.sf.saxon.trans.XPathException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String catalogContent;

    void execute() {
        getTestsRepository();
        loadTestsToSkip();
        if (Constants.PRODUCE_OUTPUT) {
            createOutputDirectory();
            copyHelperDirectories();
        }

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

    private void copyHelperDirectories() {
        for (String directory : helperDirectories) {
            Path source = testsRepositoryDirectoryPath.resolve(directory);
            File srcDir = new File(source.toString());

            Path destination = outputSubDirectoryPath.resolve(directory);
            File destDir = new File(destination.toString());

            try {
                FileUtils.copyDirectory(srcDir, destDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processCatalog(File catalogFile) throws SaxonApiException {
        Processor testDriverProcessor = new Processor(false);

        // TODO check if it is okay to use the default Tiny tree or not
        // catalogBuilder.setTreeModel(this.treeModel);
        DocumentBuilder catalogBuilder = testDriverProcessor.newDocumentBuilder();
        catalogBuilder.setLineNumbering(true);
        XdmNode catalogNode = catalogBuilder.build(catalogFile);
        catalogContent = catalogNode.toString();

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
        System.out.println("Misspelled Test Sets: " + testCasesToSkip);

        if (Constants.PRODUCE_OUTPUT)
            createOutputCatalog();
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode) throws SaxonApiException{

        // TODO skip creating an Environment - its mainly for HE, EE, PE I think

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        if (!testSetsToSkip.contains(testSetFileName)) {
            testSetsOutputted++;
            StringBuffer testSetBody = new StringBuffer();
            Iterator<XdmNode> iterator = testSetDocNode.children().iterator();
            XdmNode root = iterator.next();

            // name="app-spec-examples" is the only one failing with Regex. Quick fix so that we do not need
            // testSetBody.append("<test-set xmlns=\"" + nameSpace + "\" name=\"" + testSetFileName.split("/")[1] + "\">\n");
            while (!root.children().iterator().hasNext())
                root = iterator.next();

            String testSetContent = root.toString();
            Matcher testSetHeader = Pattern.compile("<test-set([^<]*)>", Pattern.DOTALL).matcher(testSetContent);
            if (testSetHeader.find())
                testSetBody.append("<test-set" + testSetHeader.group(1) + ">");
            else
                System.exit(1);

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
            String regex = testSetFileName.replace("/", "\\/");
            regex = "<test-set([^<]*)file=\"" + regex + "\"\\/>\n";
            catalogContent = catalogContent.replaceAll(regex,"");
            testSetsToSkip.remove(testSetFileName);
        }
    }

    private String processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String testCaseName = testCase.attribute("name");
        if (!testCasesToSkip.contains(testCaseName)) {
            testCasesOutputted++;
            String testCaseBody = testCase.toString();
//            XdmNode testNode = testCase.select(Steps.child("test")).asNode();
//            String convertedTestString = this.Convert(testNode.getStringValue());
//            XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);
//            //String expectedResult = Convert(assertion.getStringValue());
//            String expectedResult = Convert(assertion.toString());
            // TODO try to perform these regex operations with xpc.evaluate()

//            if (convertedTestString.contains("<![CDATA["))
//                System.out.println("CDATA FOUND");

//            if (testCaseName.equals("fn-codepoint-equal-22")) {
//                System.out.println("& FOUND");
//                String check = testCase.getUnderlyingNode().getStringValue();
//                Charset iso = Charset.forName("ISO-8859-1");
//                //testCaseBody = testCaseBody.replace("&", "\\&");
//                XdmNode testNode = testCase.select(Steps.child("test")).asNode();
//                String convertedTestString = this.Convert(testNode.getStringValue());
//                String escapedHTML = StringEscapeUtils.escapeHtml4(convertedTestString);
//                //String escaped = new String(convertedTestString, Charset.forName(new String("ISO-8859-1")));
//                try {
//                    StringBuilder sb = HtmlEncoder.escapeNonLatin(convertedTestString, new StringBuilder());
//                    String Jean = net.sf.saxon.query.QueryResult.serialize(testCase.getUnderlyingNode());
//                    String escaped = sb.toString();
//
//                    //byte[] latin1 = new String(convertedTestString.getBytes(StandardCharsets.UTF_8), "UTF-8").getBytes("ISO-8859-1");
//                    //String latin1 = new String(convertedTestString.getBytes(StandardCharsets.UTF_8), "ISO-8859-1");
//                    String latin2 = new String(convertedTestString.getBytes(StandardCharsets.ISO_8859_1), "UTF-8");
//                    String test = latin2.toString();
//
//                    System.out.println("& FOUND");
//                } catch (IOException | XPathException e) {
//                    e.printStackTrace();
//                }
//                System.out.println("& FOUND");
//            }
            // TODO maybe same issue like with header
            Pattern testRegex = Pattern.compile("<test>(.*)<\\/test>", Pattern.DOTALL);
            Matcher testMatcher = testRegex.matcher(testCaseBody);
            // TODO figure out files <test file="normalize-unicode/fn-normalize-unicode-11.xq"/>
            if (!testMatcher.find())
                return "";
            String convertedTestString = this.Convert(testMatcher.group(1));
            testCaseBody = testMatcher.replaceAll(Matcher.quoteReplacement("<test>" + convertedTestString + "</test>"));
            Pattern resultRegex = Pattern.compile("<result>(.*)<\\/result>", Pattern.DOTALL);
            Matcher resultMatcher = resultRegex.matcher(testCaseBody);
            if (!resultMatcher.find())
                System.exit(1);
            String expectedResult = this.Convert(resultMatcher.group(1));
            testCaseBody = resultMatcher.replaceAll(Matcher.quoteReplacement("<result>" + expectedResult + "</result>"));
            testCaseBody = testCaseBody.replace(" xmlns=\"" + nameSpace + "\"", "");
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

    private void createOutputXMLFile(String testSetFileName, StringBuffer testSetBody) {
        try {
            // It uses / in the filename found in catalog.xml file. It is independent from platform
            String [] directoryAndFile = testSetFileName.split("/");
            Path testSetOutputDirectoryPath = outputSubDirectoryPath.resolve(directoryAndFile[0]);
            File prefixSubDirectory = new File(testSetOutputDirectoryPath.toString());
            if (!prefixSubDirectory.exists())
                prefixSubDirectory.mkdirs();

            Path testSetOutputFilePath = testSetOutputDirectoryPath.resolve(directoryAndFile[1]);
            PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());

            // TODO check if I can extract it from some property as it is sometimes UTF-8 encoding
            String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
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

    private void createOutputCatalog() {
        try {
            Path testSetOutputFilePath = outputSubDirectoryPath.resolve(catalogFileName);
            PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());
            printWriter.write(catalogContent);
            printWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String [] helperDirectories = new String [] {
        "fn/json-doc",
        "fn/json-to-xml"
    };
}
