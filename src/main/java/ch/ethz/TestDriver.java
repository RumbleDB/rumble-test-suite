package ch.ethz;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Predicates;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import sparksoniq.spark.SparkSessionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TestDriver {
    private String testsRepositoryScriptFileName = "get-tests-repository.sh";
    private String catalogFileName = "catalog.xml";
    private Path workingDirectoryPath;
    private Path testsRepositoryDirectoryPath;
    private String testSetToTest = "math-acos.xml";
    private SparkSession sparkSession;
    private Rumble rumbleInstance;

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
        // Initialize Spark
        sparkSession = SparkSession.builder()
                .master("local")
                .appName("rumble-test-suite")
                .getOrCreate();

        // Initialize Rumble
        // TODO RumbleRuntimeConfiguration rumbleConf = new RumbleRuntimeConfiguration() // I added init() in default ctor
        RumbleRuntimeConfiguration rumbleConf = new RumbleRuntimeConfiguration(
                new String[]{
                        "--output-format","json"
                });
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

        // TODO check if we need a namespace
        // xpc.declareNamespace("", this.catalogNamespace());
        XPathCompiler xpc = testDriverProcessor.newXPathCompiler();
        xpc.setLanguageVersion("3.1");
        xpc.setCaching(true);

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

    private void processTestCase(XdmNode testCase, XPathCompiler xpc){
        XdmNode resultNode = testCase.select(Steps.child("result")).asNode();
        XdmNode testNode = testCase.select(Steps.child("test")).asNode();

        String testString = testNode.getStringValue();

        // Small converter
        testString = Convert(testString);

        // Execute query
        SequenceOfItems queryResult = rumbleInstance.runQuery(testString);

        List<Item> resultList = new ArrayList<Item>(){};
        queryResult.populateListWithWarningOnlyIfCapReached(resultList);

        while (queryResult.hasNext()){
            Item item = queryResult.next();
        }


        boolean needSerializedResult = resultNode.select(Steps.descendant("assert-serialization-error")).exists() || resultNode.select(Steps.descendant("serialization-matches")).exists();
        boolean needResultValue = needSerializedResult &&
                resultNode.select(Steps.descendant(Predicates.isElement())
                        .where(Predicates.not(Predicates.hasLocalName("serialization-matches")
                                .or(Predicates.hasLocalName("assert-serialization-error"))
                                .or(Predicates.hasLocalName("any-of"))
                                .or(Predicates.hasLocalName("all-of")))))
                        .exists();

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

    private String Convert(String testString) {

        // Found in fn/abs.xml
        testString = testString.replace("xs:integer","integer");
        testString = testString.replace("xs:int","integer");
        testString = testString.replace("xs:double", "double");

        // Found in math/math-acos.xml
        testString = testString.replace("math:","");

        // Replace with regex checks!
        testString = testString.replace("fn:","");

        return testString;
    }
}
