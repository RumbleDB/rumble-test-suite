package converter;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestConverter {
    private final String catalogFileName = "catalog.xml";
    private Path testsRepositoryDirectoryPath;
    private static Path outputSubDirectoryPath;
    private int testSetsOutputted = 0;
    private final String nameSpace = "http://www.w3.org/2010/09/qt-fots-catalog";
    private int testCasesOutputted = 0;
    private String catalogContent;
    private final String[] helperDirectories = new String[] {
        "fn/json-doc",
        "fn/json-to-xml"
    };

    void execute() throws SaxonApiException, IOException, InterruptedException {
        getTestsRepository();
        if (Constants.PRODUCE_OUTPUT) {
            createOutputDirectory();
            copyHelperDirectories();
        }
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

    private void copyHelperDirectories() throws IOException {
        for (String directory : helperDirectories) {
            Path source = testsRepositoryDirectoryPath.resolve(directory);
            File srcDir = new File(source.toString());

            Path destination = outputSubDirectoryPath.resolve(directory);
            File destDir = new File(destination.toString());

            FileUtils.copyDirectory(srcDir, destDir);

        }
    }

    private void processCatalog(File catalogFile) throws SaxonApiException, IOException {
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

        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            this.processTestSet(catalogBuilder, xpc, testSet);
        }
        System.out.println("Included Test Sets: " + testSetsOutputted);
        System.out.println("Included Test Cases: " + testCasesOutputted);

        if (Constants.PRODUCE_OUTPUT)
            createOutputCatalog();
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode)
            throws SaxonApiException,
                IOException {

        // TODO skip creating an Environment - its mainly for HE, EE, PE I think

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        testSetsOutputted++;
        StringBuffer testSetBody = new StringBuffer();
        Iterator<XdmNode> iterator = testSetDocNode.children().iterator();
        XdmNode root = iterator.next();

        // name="app-spec-examples" is the only one failing with Regex. Quick fix so that we do not need
        // testSetBody.append("<test-set xmlns=\"" + nameSpace + "\" name=\"" + testSetFileName.split("/")[1] +
        // "\">\n");
        while (!root.children().iterator().hasNext())
            root = iterator.next();

        String testSetContent = root.toString();
        Matcher testSetHeader = Pattern.compile("<test-set([^<]*)>", Pattern.DOTALL).matcher(testSetContent);
        if (testSetHeader.find())
            testSetBody.append("<test-set").append(testSetHeader.group(1)).append(">");
        else
            System.exit(1);

        // TODO This does not quite help us
        // XdmNode description = (XdmNode) xpc.evaluateSingle("description", root);
        // printWriter.write(description.toString());

        // TODO we can separate them like this
        // for (XdmNode environment : root.select(Steps.child("link")).asList()) {
        // printWriter.write(environment.toString());
        // }
        // for (XdmNode environment : root.select(Steps.child("description")).asList()) {
        // printWriter.write(environment.toString());
        // }
        // for (XdmNode environment : root.select(Steps.child("environment")).asList()) {
        // printWriter.write(environment.toString());
        // }

        // TODO checkout if we can use this to better do toString()
        // NodeInfo testDocNodeInfo = testSetDocNode.getUnderlyingNode();
        // int testDocNode = testDocNodeInfo.getNodeKind();
        // int rootNode = root.getUnderlyingNode().getNodeKind();
        // int environment =
        // root.select(Steps.child("environment")).asList().get(0).getUnderlyingNode().getNodeKind();
        // int testcase =
        // testSetDocNode.select(Steps.descendant("test-case")).asList().get(0).getUnderlyingNode().getNodeKind();


        // TODO can't make a new Node
        // QName bla = root.getNodeName();
        // XdmNode a = new XdmNode()

        for (XdmNode child : root.children()) {
            if (child.getUnderlyingNode().getDisplayName().equals("test-case"))
                // if (child.getNodeName().getLocalName().equals("test-case"))
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

    private String processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        testCasesOutputted++;
        String testCaseBody = testCase.toString();
        // XdmNode testNode = testCase.select(Steps.child("test")).asNode();
        // String convertedTestString = this.Convert(testNode.getStringValue());
        // XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);
        // //String expectedResult = Convert(assertion.getStringValue());
        // String expectedResult = Convert(assertion.toString());
        // TODO try to perform these regex operations with xpc.evaluate()

        // if (convertedTestString.contains("<![CDATA["))
        // System.out.println("CDATA FOUND");

        // if (testCaseName.equals("fn-codepoint-equal-22")) {
        // System.out.println("& FOUND");
        // String check = testCase.getUnderlyingNode().getStringValue();
        // Charset iso = Charset.forName("ISO-8859-1");
        // //testCaseBody = testCaseBody.replace("&", "\\&");
        // XdmNode testNode = testCase.select(Steps.child("test")).asNode();
        // String convertedTestString = this.Convert(testNode.getStringValue());
        // String escapedHTML = StringEscapeUtils.escapeHtml4(convertedTestString);
        // //String escaped = new String(convertedTestString, Charset.forName(new String("ISO-8859-1")));
        // try {
        // StringBuilder sb = HtmlEncoder.escapeNonLatin(convertedTestString, new StringBuilder());
        // String Jean = net.sf.saxon.query.QueryResult.serialize(testCase.getUnderlyingNode());
        // String escaped = sb.toString();
        //
        // //byte[] latin1 = new String(convertedTestString.getBytes(StandardCharsets.UTF_8),
        // "UTF-8").getBytes("ISO-8859-1");
        // //String latin1 = new String(convertedTestString.getBytes(StandardCharsets.UTF_8), "ISO-8859-1");
        // String latin2 = new String(convertedTestString.getBytes(StandardCharsets.ISO_8859_1), "UTF-8");
        // String test = latin2.toString();
        //
        // System.out.println("& FOUND");
        // } catch (IOException | XPathException e) {
        // e.printStackTrace();
        // }
        // System.out.println("& FOUND");
        // }
        // TODO maybe same issue like with header
        Pattern testRegex = Pattern.compile("<test>(.*)</test>", Pattern.DOTALL);
        Matcher testMatcher = testRegex.matcher(testCaseBody);
        // TODO figure out files <test file="normalize-unicode/fn-normalize-unicode-11.xq"/>
        if (!testMatcher.find())
            return "";
        String convertedTestString = this.Convert(testMatcher.group(1));
        testCaseBody = testMatcher.replaceAll(Matcher.quoteReplacement("<test>" + convertedTestString + "</test>"));
        Pattern resultRegex = Pattern.compile("<result>(.*)</result>", Pattern.DOTALL);
        Matcher resultMatcher = resultRegex.matcher(testCaseBody);
        if (!resultMatcher.find())
            System.exit(1);
        String expectedResult = this.Convert(resultMatcher.group(1));
        testCaseBody = resultMatcher.replaceAll(
            Matcher.quoteReplacement("<result>" + expectedResult + "</result>")
        );
        testCaseBody = testCaseBody.replace(" xmlns=\"" + nameSpace + "\"", "");
        return testCaseBody;
    }

    private String Convert(String testString) {
        return testString;
    }

    private void createOutputXMLFile(String testSetFileName, StringBuffer testSetBody) throws IOException {
        // It uses / in the filename found in catalog.xml file. It is independent of platform
        String[] directoryAndFile = testSetFileName.split("/");
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
        Files.write(
            Paths.get(testSetOutputFilePath.toString()),
            testSetBody.toString().getBytes(),
            StandardOpenOption.APPEND
        );
        String endRootTag = "</test-set>";
        Files.write(Paths.get(testSetOutputFilePath.toString()), endRootTag.getBytes(), StandardOpenOption.APPEND);
    }

    private void createOutputCatalog() throws FileNotFoundException {
        Path testSetOutputFilePath = outputSubDirectoryPath.resolve(catalogFileName);
        PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());
        printWriter.write(catalogContent);
        printWriter.close();
    }
}
