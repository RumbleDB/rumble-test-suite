package converter;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
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

        DocumentBuilder catalogBuilder = testDriverProcessor.newDocumentBuilder();
        catalogBuilder.setLineNumbering(true);
        XdmNode catalogNode = catalogBuilder.build(catalogFile);
        catalogContent = catalogNode.toString();

        XQueryCompiler xqc = testDriverProcessor.newXQueryCompiler();

        XPathCompiler xpc = testDriverProcessor.newXPathCompiler();
        xpc.setLanguageVersion("3.1");
        xpc.setCaching(true);
        // Yes we do need Namespace. It is required to run evaluateSingle and luckily it is hardcoded in QT3TestDriverHE
        xpc.declareNamespace("", nameSpace);

        System.out.println("Skipped Test Sets: " + testSetsToSkip.size());
        System.out.println("Skipped Test Cases: " + testCasesToSkip.size());
        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            this.processTestSet(catalogBuilder, xpc, xqc, testSet);
        }
        System.out.println("Included Test Sets: " + testSetsOutputted);
        System.out.println("Included Test Cases: " + testCasesOutputted);
        System.out.println("Misspelled Test Sets: " + testSetsToSkip);
        System.out.println("Misspelled Test Sets: " + testCasesToSkip);

        if (Constants.PRODUCE_OUTPUT)
            createOutputCatalog();
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XQueryCompiler xqc, XdmNode testSetNode) throws SaxonApiException{
        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        if (!testSetsToSkip.contains(testSetFileName)) {
            testSetsOutputted++;
            Iterator<XdmNode> iterator = testSetDocNode.children().iterator();
            XdmNode root = iterator.next();

            // name="app-spec-examples" is the only one failing with Regex. Quick fix so that we do not need
            // testSetBody.append("<test-set xmlns=\"" + nameSpace + "\" name=\"" + testSetFileName.split("/")[1] + "\">\n");
            while (!root.children().iterator().hasNext())
                root = iterator.next();
            XQueryExecutable xqe = xqc.compile("declare function local:convert($x)\n" +
                                                     "{ $x };\n" +
                                                     "declare function local:transform($nodes as node()*) as node()*\n" +
                                                     "{\n" +
                                                     "for $n in $nodes return\n" +
                                                     "typeswitch ($n)\n" +
                                                     "case element (test) return <test>{local:convert($n/string())}</test>\n" +
                                                     "case element (result) return <result>{local:convert($n/string())}</result>\n" +
                                                     "case element () return element { fn:node-name($n) } {$n/@*, local:transform($n/node())} \n" +
                                                     "default return $n\n" +
                                                     "};" +
                                                     "declare variable $test-set external;\n" +
                                                     "let $y := $test-set\n" +
                                                     "return local:transform($y)");
            XQueryEvaluator xQueryEvaluator = xqe.load();
            xQueryEvaluator.setExternalVariable(new QName("test-set"), root);
            xQueryEvaluator.iterator();
            for (XdmValue result : xQueryEvaluator) {
                XdmNode output = (XdmNode) result;
                if (Constants.PRODUCE_OUTPUT)
                    createOutputXMLFile(testSetFileName, output);
            }
        }
        else {
            String regex = testSetFileName.replace("/", "\\/");
            regex = "<test-set([^<]*)file=\"" + regex + "\"\\/>\n";
            catalogContent = catalogContent.replaceAll(regex,"");
            testSetsToSkip.remove(testSetFileName);
        }
    }

    private void createOutputXMLFile(String testSetFileName, XdmNode testSetBody) {
        try {
            // It uses / in the filename found in catalog.xml file. It is independent from platform
            String [] directoryAndFile = testSetFileName.split("/");
            Path testSetOutputDirectoryPath = outputSubDirectoryPath.resolve(directoryAndFile[0]);
            File prefixSubDirectory = new File(testSetOutputDirectoryPath.toString());
            if (!prefixSubDirectory.exists())
                prefixSubDirectory.mkdirs();

            Path testSetOutputFilePath = testSetOutputDirectoryPath.resolve(directoryAndFile[1]);
            PrintWriter printWriter = new PrintWriter(testSetOutputFilePath.toString());

            String content = net.sf.saxon.query.QueryResult.serialize(testSetBody.getUnderlyingNode());
            printWriter.write(content);
            printWriter.close();
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
