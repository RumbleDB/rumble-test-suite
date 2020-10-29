package ch.ethz;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.spark.sql.SparkSession;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TestDriver {
    private String testsRepositoryScriptFileName = "get-tests-repository.sh";
    private String catalogFileName = "catalog.xml";
    private Path workingDirectoryPath;
    private Path testsRepositoryDirectoryPath;
    private String testSetToTest = "math-acos.xml";
    private SparkSession sparkSession;
    private Rumble rumbleInstance;
    private int numberOfFails;
    private int numberOfSuccess;
    private String resultVariableName = "result";

    void execute() {
        workingDirectoryPath = Paths.get("").toAbsolutePath();
        getTestsRepository();
        initializeSparkAndRumble();

        try {
            processCatalog(new File(testsRepositoryDirectoryPath.resolve(catalogFileName).toString()));
        } catch (SaxonApiException e) {
            e.printStackTrace();
        }

    }

    private void getTestsRepository(){
        System.out.println("Running sh script to obtain the required tests repository!");
        try {
            ProcessBuilder pb = new ProcessBuilder(workingDirectoryPath.resolve(testsRepositoryScriptFileName).toString());

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
            testsRepositoryDirectoryPath = workingDirectoryPath.resolve(testsDirectory);
            System.out.println(testsRepositoryDirectoryPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Tests repository obtained!");
    }

    private void initializeSparkAndRumble() {
        // Initialize configuration - the instance will be the same as in org.rumbledb.cli.Main.java (one for shell)
        // TODO RumbleRuntimeConfiguration rumbleConf = new RumbleRuntimeConfiguration() // I added init() in default ctor
        RumbleRuntimeConfiguration rumbleConf = new RumbleRuntimeConfiguration(
                new String[]{
                        "--output-format","json"
                });


        // Initialize Spark - not needed when I have part of code from org.rumbledb.cli.JsoniqQueryExecutor.java
        sparkSession = SparkSession.builder()
                .master("local")
                .appName("rumble-test-suite")
                .getOrCreate();

        // org.rumbledb.cli.JsoniqQueryExecutor.java does this for some reason in the ctor
        //SparkSessionManager.COLLECT_ITEM_LIMIT = rumbleConf.getResultSizeCap();

        // Initialize Rumble
        rumbleInstance = new Rumble(rumbleConf);
    }

    private void processCatalog(File catalogFile) throws SaxonApiException {
        // TODO: Different initialization from Saxon, check if it is okay
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
        xpc.declareNamespace("", "http://www.w3.org/2010/09/qt-fots-catalog");

        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            this.processTestSet(catalogBuilder, xpc, testSet);
        }

        // TODO Learn this Java simplification or leave it here for example
//        Iterator testSetIterator = catalogNode.select(Steps.descendant("test-set")).asList().iterator();
//
//        while(testSetIterator.hasNext()) {
//            XdmNode testSet = (XdmNode)testSetIterator.next();
//            this.processTestSet(catalogBuilder, xpc, testSet);
//        }
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode) throws SaxonApiException{

        // TODO skip single test dependency and multiple test dependencies for now

        // TODO skip creating an Environment

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        // TODO Think about scenario in which we just want to run a specified TestSet (like math) and not all of them
        if (testSetToTest.equals("") || testSetFileName.endsWith(testSetToTest)) {
            for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
                this.processTestCase(testCase, xpc);

            }
        }
    }

    private void processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String testCaseName = testCase.attribute("name");
        if (!Arrays.asList(skipTestCaseList).contains(testCaseName)) {
            XdmNode resultNode = testCase.select(Steps.child("result")).asNode();
            XdmNode testNode = testCase.select(Steps.child("test")).asNode();

            String testString = testNode.getStringValue();

            // Small converter
            testString = Convert(testString);

            // Execute query
            List<Item> resultAsList = runQuery(testString);

            // TODO figure out alternative results afterwards - this is if then else or...
            // This does not return what I want, xpc is not correct
            XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);

            if (compareExpectedResultAndOutput(resultAsList, assertion))
                numberOfSuccess++;
            else
                numberOfFails++;

            // TODO check this results value
//        boolean needSerializedResult = resultNode.select(Steps.descendant("assert-serialization-error")).exists() || resultNode.select(Steps.descendant("serialization-matches")).exists();
//        boolean needResultValue = needSerializedResult &&
//                resultNode.select(Steps.descendant(Predicates.isElement())
//                        .where(Predicates.not(Predicates.hasLocalName("serialization-matches")
//                                .or(Predicates.hasLocalName("assert-serialization-error"))
//                                .or(Predicates.hasLocalName("any-of"))
//                                .or(Predicates.hasLocalName("all-of")))))
//                        .exists();

            //TODO check the XSLT (isApplicable)
//        dependency = (XdmNode)var17.next();
//        exp = dependency.attribute("type");
//        if (exp == null) {
//            throw new IllegalStateException("dependency/@type is missing");
//        }
//
//        value = dependency.attribute("value");
//        if (value == null) {
//            throw new IllegalStateException("dependency/@value is missing");
//        }
//
//        if (exp.equals("spec")) {
//            if (value.contains("XSLT") || value.contains("XT")) {
//                //this.writeTestcaseElement(testCaseName, "n/a", "not" + this.spec.specAndVersion);
//                //++this.notrun;
//                return;
//            }
//        }
        }
    }

    private boolean compareExpectedResultAndOutput(List<Item> resultAsList, XdmNode assertion) {
        String tag = assertion.getNodeName().getLocalName();
        switch(tag) {
            case "assert-empty":
                return AssertEmpty(resultAsList);
            case "assert":
                return Assert(resultAsList, assertion);
            case "assert-eq":
                return AssertEq(resultAsList, assertion);
//            case -1414935165:
//                if (tag.equals("all-of")) {
//                    var9 = 18;
//                }
//                break;
//            case 358832481:
//                if (tag.equals("assert-type")) {
//                    var9 = 11;
//                }
//                break;
            case "assert-string-value":
                return AssertStringValue(resultAsList, assertion);
            default:
                return false;
        }
    }

    private boolean AssertEq(List<Item> resultAsList, XdmNode assertion) {
        String assertExpression = assertion.getStringValue();
        List<String> lines = resultAsList.stream().map(x -> x.serialize()).collect(Collectors.toList());

        assertExpression += "=" + lines.get(0);

        List<Item> nestedResult = runQuery(assertExpression);

        return AssertTrue(nestedResult);
    }

    private boolean Assert(List<Item> resultAsList, XdmNode assertion) {
        // TODO maybe work with XdmNode instead of strings??? Really tricky to convert Rumble result to XdmValue...
        String assertExpression = assertion.getStringValue();
        // I cannot extract value as string... getStringValue throws exception if not string and I cannot cast it
        //assertExpression = assertExpression.replace("$" + resultVariableName, resultAsList.get(0).getStringValue());

        assertExpression = assertExpression.replace("$" + resultVariableName, singleItemToString(resultAsList));

        List<Item> nestedResult = runQuery(assertExpression);

        return AssertTrue(nestedResult);
    }

    private boolean AssertTrue(List<Item> resultAsList){
        if (resultAsList.size() != 1)
            return false;
        if (!resultAsList.get(0).isBoolean())
            return false;

        return resultAsList.get(0).getBooleanValue();
    }

    private boolean AssertEmpty(List<Item> resultAsList) {
        return resultAsList.size() == 0;
    }

    private boolean AssertStringValue(List<Item> resultAsList, XdmNode assertion) {
        // TODO maybe both to lower string
        String assertExpression = assertion.getStringValue();
        return assertExpression.equals(singleItemToString(resultAsList));
    }

    private String Convert(String testString) {

        // Found in fn/abs.xml and math/math-acos.xml
        testString = testString.replace("xs:integer","integer");
        testString = testString.replace("xs:int","integer");
        testString = testString.replace("xs:double", "double");

        // Found in math/math-acos.xml
        testString = testString.replace("math:","");

        // Replace with regex checks!
        testString = testString.replace("fn:","");

        // Found in math acos, asin, atan, cos, exp, exp10, log, log10, pow, sin, sqrt, tan (tests 7, 8, 9 usually)
        // TODO check for assertions as well!
        testString = testString.replace("double('NaN')", "double(\"NaN\")");
        testString = testString.replace("double('INF')", "double(\"Infinity\")");
        testString = testString.replace("double('-INF')", "double(\"-Infinity\")");

        return testString;
    }

    private List<Item> runQuery(String query){
        // Coppied from JsoniqQueryExecutor.java - 150th line of code
        SequenceOfItems queryResult = rumbleInstance.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
        return resultAsList;
    }

    private String singleItemToString(List<Item> itemList){
        if (itemList.size() != 1)
            return null;
        else
            return itemList.stream().map(x -> x.serialize()).collect(Collectors.toList()).get(0);
    }

    private String[] skipTestCaseList = new String[]{
    };
}
