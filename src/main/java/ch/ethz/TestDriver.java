package ch.ethz;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.commons.lang.StringUtils;
import org.apache.spark.sql.SparkSession;
import org.apache.http.impl.client.HttpClients;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.context.Name;
import org.rumbledb.exceptions.RumbleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TestDriver {
    private String testsRepositoryScriptFileName = "get-tests-repository.sh";
    private String catalogFileName = "catalog.xml";
    private Path testsRepositoryDirectoryPath;
    // Set this field if you want to run a specific test set that starts with string below
    private String testSetToTest = ""; //
    // Set this field if you want to run a specific test case that starts with string below
    private String testCaseToTest = ""; //
    private SparkSession sparkSession;
    private Rumble rumbleInstance;
    private int numberOfFails;
    private int numberOfSuccess;
    private String resultVariableName = "result";
    private int numberOfSkipped;
    private int numberOfCrashes;
    private int numberOfDependencies;
    private int numberOfUnsupportedTypes;
    private int numberOfUnsupportedErrorCodes;
    private int numberOfProcessedTestCases;
    private int numberOfManaged;
    private List<String> testSetsToSkip;

    // For running a specific query when testing the XQuery parser for Rumble
    private String queryToTest = "";

    // For JSON-doc
    private Map<String, String> URItoPathLookupTable = new HashMap<>();
    private String jsonDocName = "fn/json-doc";

    void execute() {
        getTestsRepository();
        initializeSparkAndRumble();
        if (!queryToTest.equals(""))
        {
            List<Item> resultAsList = runQuery(queryToTest, rumbleInstance);
            List<String> lines = resultAsList.stream().map(x -> x.serialize()).collect(Collectors.toList());
            System.out.println(String.join(" ", lines));
            return;
        }

        else {
            try {
                testSetsToSkip = Files.readAllLines(Constants.WORKING_DIRECTORY_PATH.resolve(Constants.TEST_SETS_TO_SKIP_FILENAME), Charset.defaultCharset());
                processCatalog(new File(testsRepositoryDirectoryPath.resolve(catalogFileName).toString()));
            } catch (SaxonApiException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getTestsRepository(){
        if (Constants.USE_CONVERTED_TEST_SUITE){
            Path convertedTestSuitesDirectory = Constants.WORKING_DIRECTORY_PATH.resolve(Constants.OUTPUT_TEST_SUITE_DIRECTORY);
            File[] allConvertedTestSuiteDirectories = new File(convertedTestSuitesDirectory.toString()).listFiles();
            Arrays.sort(allConvertedTestSuiteDirectories, Comparator.reverseOrder());
            testsRepositoryDirectoryPath = allConvertedTestSuiteDirectories[0].toPath();
        }
        else {
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
    }

    private void initializeSparkAndRumble() {
        // Initialize configuration - the instance will be the same as in org.rumbledb.cli.Main.java (one for shell)
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
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode) throws SaxonApiException{

        // TODO skip creating an Environment - its mainly for HE, EE, PE I think

        String testSetFileName = testSetNode.attribute("file");
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        if (testSetFileName.contains(jsonDocName))
            prepareJsonDocEnvironment(testSetDocNode);

        if (testSetToTest.equals("") || testSetFileName.contains(testSetToTest)) {
            resetCounters();
            for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
                if (!testSetsToSkip.contains(testSetFileName))
                    this.processTestCase(testCase, xpc);
                else
                    LogSkipped(testCase.attribute("name"));
                numberOfProcessedTestCases++;
            }
            System.out.println(testSetFileName + " Success: " + numberOfSuccess + " Managed: " + numberOfManaged + " Fails: " + numberOfFails +
                                                 " Skipped: " + numberOfSkipped + " Dependencies: " + numberOfDependencies +
                                                 " Crashes: " + numberOfCrashes + " UnsupportedTypes: " + numberOfUnsupportedTypes +
                                                 " UnsupportedErrors: " + numberOfUnsupportedErrorCodes);
            int sum = (numberOfSuccess + numberOfManaged + numberOfFails + numberOfSkipped + numberOfDependencies
            + numberOfCrashes + numberOfUnsupportedTypes + numberOfUnsupportedErrorCodes);
            String checkMatching = sum == numberOfProcessedTestCases ? "OK" : "NOT";
            Constants.TEST_CASE_SB.append(String.format(Constants.TEST_CASE_TEMPLATE, testSetFileName, numberOfSuccess,
                    numberOfManaged, numberOfFails, numberOfSkipped, numberOfDependencies, numberOfCrashes,
                    numberOfUnsupportedTypes, numberOfUnsupportedErrorCodes, sum, numberOfProcessedTestCases, checkMatching));
        }
    }

    private void prepareJsonDocEnvironment(XdmNode testSetDocNode) {
        // For some reason we have to access the first one and then we will see the environments
        List<XdmNode> environments = testSetDocNode.children().iterator().next().select(Steps.child("environment")).asList();
        for (XdmNode environment : environments){
            List<XdmNode> resources = environment.select(Steps.descendant("resource")).asList();
            for (XdmNode resource : resources){
                String file = resource.attribute("file");
                String uri = resource.attribute("uri");
                URItoPathLookupTable.put(uri,file);
            }
        }
    }

    private void resetCounters() {
        numberOfSuccess = 0;
        numberOfFails = 0;
        numberOfSkipped = 0;
        numberOfDependencies = 0;
        numberOfCrashes = 0;
        numberOfUnsupportedTypes = 0;
        numberOfUnsupportedErrorCodes = 0;
        numberOfProcessedTestCases = 0;
        numberOfManaged = 0;
    }

    private void processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String testCaseName = testCase.attribute("name");
        if (!Arrays.asList(skipTestCaseList).contains(testCaseName)) {
            if (testCaseToTest.equals("") || testCaseName.contains(testCaseToTest)) {
                XdmNode resultNode = testCase.select(Steps.child("result")).asNode();
                XdmNode testNode = testCase.select(Steps.child("test")).asNode();

                try {
                    // Prevent executing any query that has dependencies that we do not fulfill

                    List<XdmNode> dependencies = testCase.select(Steps.child("dependency")).asList();
                    dependencies.addAll(testCase.getParent().select(Steps.child("dependency")).asList());

                    if (dependencies != null && dependencies.size() != 0) {
                        boolean allDependenciesSatisfied = false;
                        for (XdmNode dependencyNode : dependencies) {
                            String type = dependencyNode.attribute("type");
                            String value = dependencyNode.attribute("value");
                            if (type == null || value == null){
                                // Should never happen
                                LogSkipped(testCaseName + dependencyNode.toString());
                                return;
                            }

                            // In Saxon they implemented it within ensureDependencySatisfied
                            // Here we are referring to instructions provided by Ghislain Fourny on Mon 11/9, 12:16 PM
                            switch (type){
                                case "calendar" :
                                {
                                    // CB - I don't think we support any other calendar
                                    LogDependency(testCaseName + dependencyNode.toString());
                                    return;
                                }
                                case "unicode-version" :
                                {
                                    // 7.0,3.1.1,5.2,6.0,6.2 - We will need to look at the tests. I am not sure which unicode Java 8 supports. For now you can keep them all.

                                    break;
                                }
                                case "unicode-normalization-form" :
                                {
                                    // NFD,NFKD,NFKC,FULLY-NORMALIZED - We will need to play it by ear.
                                    //LogDependency(testCaseName + dependencyNode.toString());
                                    //return;
                                    break;
                                }
                                case "format-integer-sequence" :
                                {
                                    // ⒈,Α,α -  I am not sure what this is, I would need to see the tests.
                                    LogDependency(testCaseName + dependencyNode.toString());
                                    return;

                                }
                                case "xml-version" :
                                {
                                    // Rumble doesn't care about xml version, its XML specific
                                    // TODO maybe it influences Saxon environment processor (check 197 in QT3TestDriverHE)

                                    break;
                                }
                                case "xsd-version" :
                                {
                                    // Rumble doesn't care about schema, its XML specific
                                    // TODO maybe it influences Saxon environment processor (check 221 in QT3TestDriverHE)

                                    break;
                                }
                                case "feature" :
                                {
//                                    schemaValidation (XML specific)
//                                    schemaImport (XML specific)
//                                    advanced-uca-fallback (we don't support other collations)
//                                    non_empty_sequence_collection (we don't support collection() yet)
//                                    collection-stability (we don't support collection() yet)
//                                    directory-as-collection-uri (we don't support collection() yet)
//                                    non_unicode_codepoint_collation (we don't support other collations)
//                                    simple-uca-fallback (we don't support other collations)
//                                    olson-timezone (not supported yet)
//                                    fn-format-integer-CLDR (not supported yet)
//                                    xpath-1.0-compatibility (we are not backwards compatible with XPath 1.0)
//                                    fn-load-xquery-module (not supported yet)
//                                    fn-transform-XSLT (not supported yet)
//                                    namespace-axis (XML specific)
//                                    infoset-dtd (XML specific)
//                                    serialization
//                                    fn-transform-XSLT30 (not supported yet)
//                                    remote_http (not sure what this is, do you have an example?)
//                                    typedData (not sure what this is, do you have an example?)
//                                    schema-location-hint (XML specific)
                                    // Only three below are supported by Rumble. Included staticTyping myself as +20 pass, 30 fail, 20 unsupported types but no crashes!
                                    if (!(value.contains("higherOrderFunctions") || value.contains("moduleImport") ||
                                            value.contains("arbitraryPrecisionDecimal") || value.contains("staticTyping"))){
                                        LogDependency(testCaseName + dependencyNode.toString());
                                        return;
                                    }

                                    break;
                                }
                                case "default-language" :
                                {
                                    // fr-CA not supported - we just support en
                                    if (!value.contains("en")){
                                        LogDependency(testCaseName + dependencyNode.toString());
                                        return;
                                    }

                                    break;
                                }
                                case "language" :
                                {
                                    // xib,de,fr,it not supported - we just support en
                                    if (!value.contains("en")){
                                        LogDependency(testCaseName + dependencyNode.toString());
                                        return;
                                    }

                                    break;
                                }
                                // Check if not the XSLT (isApplicable original method)
                                case "spec" :
                                {
                                    // XP30+,XQ10+,XQ30+,XQ30,XQ31+,XP31+,XP31,XQ31,XP20,XQ10,XP20+,XP30 is ok, XT30+ not
                                    //if (!value.contains("XSLT") && !value.contains("XT")) {
                                    if (!(value.contains("XQ") || value.contains("XP"))){
                                        LogDependency(testCaseName + dependencyNode.toString());
                                        return;
                                    }

                                    break;
                                }
                                case "limit" :
                                {
                                    // year_lt_0 - I am not sure I don't think we have this limit.
                                    LogDependency(testCaseName + dependencyNode.toString());
                                    return;
                                }
                                default:
                                {
                                    LogDependency(testCaseName + dependencyNode.toString());
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // This exception means there are no dependencies and we can proceed with running the query
                }

                String testString = "";

                List<XdmNode> environments = testCase.select(Steps.child("environment")).asList();

                if (environments != null && environments.size() > 0) {
                    XdmNode environment = environments.get(0);
                    Iterator externalVariables = environment.children("param").iterator();
                    if (externalVariables.hasNext()) {
                        while (externalVariables.hasNext()) {
                            XdmNode param = (XdmNode) externalVariables.next();
                            String name = param.attribute("name");
                            String source = param.attribute("source");

                            // TODO Check what source is for to handle in else
                            if (source == null) {
                                String select = param.attribute("select");
                                //value = xpc.evaluate(select, (XdmItem) null);
                                testString += "let $" + name + " := " + select + " ";
                            }
                            else{
                                //runNestedQuery(source, assertion);
                                // Variable name should be the name and not hardcoded result
                            }
                        }
                        testString += "return ";
                    }
                }

                testString += testNode.getStringValue();

                // TODO figure out alternative results afterwards - this is if then else or...
                // Place above try catch block to have assertion available in the catch!
                XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);

                try {
                    // Hard Coded converter
                    String convertedTestString = Convert(testString);

                    // JsonDoc converter
                    if (testCaseName.startsWith("json-doc")) {
                        String uri = StringUtils.substringBetween(convertedTestString, "(\"", "\")");
                        String jsonDocFilename = URItoPathLookupTable.get(uri);
                        String fullAbsoluteJsonDocPath = testsRepositoryDirectoryPath.resolve("fn/" + jsonDocFilename).toString();
                        convertedTestString = convertedTestString.replace(uri,"file:" + fullAbsoluteJsonDocPath);
                    }

                    // Execute query
                    List<Item> resultAsList = runQuery(convertedTestString, rumbleInstance);

                    TestPassOrFail(checkAssertion(resultAsList, assertion), testCaseName, convertedTestString.equals(testString));
                } catch (UnsupportedTypeException ute) {
                    LogUnsupportedType(testCaseName);
                } catch (RumbleException re) {
                    CheckForErrorCode(re, assertion, testCaseName);
                } catch (Exception e){
                    LogCrash(testCaseName);
                }
            }
            // TODO check this results value
//        boolean needSerializedResult = resultNode.select(Steps.descendant("assert-serialization-error")).exists() || resultNode.select(Steps.descendant("serialization-matches")).exists();
//        boolean needResultValue = needSerializedResult &&
//                resultNode.select(Steps.descendant(Predicates.isElement())
//                        .where(Predicates.not(Predicates.hasLocalName("serialization-matches")
//                                .or(Predicates.hasLocalName("assert-serialization-error"))
//                                .or(Predicates.hasLocalName("any-of"))
//                                .or(Predicates.hasLocalName("all-of")))))
//                        .exists();
        }
        else {
            // Also visible in the list skipTestCaseList below
            LogSkipped(testCaseName);
        }
    }

    private void TestPassOrFail(boolean conditionToEvaluate, String testCaseName, boolean isQueryUnchanged) {
        // Was merged into single code for both AssertError and also the part in processTestCase.
        if (conditionToEvaluate) {
            if (isQueryUnchanged) {
                LogSuccess(testCaseName);
            }
            else{
                LogManaged(testCaseName);
            }
        } else {
            LogFail(testCaseName);
        }
    }

    private void LogSuccess(String lineText) {
        numberOfSuccess++;
        Constants.SUCCESS_TESTS_SB.append(lineText + "\n");
    }

    private void LogManaged(String lineText) {
        numberOfManaged++;
        Constants.MANAGED_TESTS_SB.append(lineText + "\n");
    }

    private void LogFail(String lineText) {
        numberOfFails++;
        Constants.FAILED_TESTS_SB.append(lineText + "\n");
    }

    private void LogSkipped(String lineText){
        numberOfSkipped++;
        Constants.SKIPPED_TESTS_SB.append(lineText + "\n");
    }

    private void LogDependency(String lineText){
        numberOfDependencies++;
        Constants.DEPENDENCY_TESTS_SB.append(lineText + "\n");
    }

    private void LogCrash(String lineText){
        numberOfCrashes++;
        Constants.CRASHED_TESTS_SB.append(lineText + "\n");
    }

    private void LogUnsupportedType(String lineText){
        numberOfUnsupportedTypes++;
        Constants.UNSUPPORTED_TYPE_SB.append(lineText + "\n");
    }

    private void LogUnsupportedErrorCode(String lineText){
        numberOfUnsupportedErrorCodes++;
        Constants.UNSUPPORTED_ERRORS_SB.append(lineText + "\n");
    }

    private void CheckForErrorCode(RumbleException e, XdmNode assertion, String testCaseName) {
        String tag = assertion.getNodeName().getLocalName();
        // Logic for even though we only support SENR0001
        if (tag.equals("error") || tag.equals("assert-serialization-error")){
            AssertError(assertion, testCaseName, e.getErrorCode());
            return;
        }
        else if (tag.equals("any-of")){
            Iterator childIterator = assertion.children("*").iterator();
            boolean foundSingleMatch = false;
            boolean seenUnsupportedCode = false;
            boolean seenSingleErrorInAny = false;

            while (childIterator.hasNext() && !foundSingleMatch)
            {
                XdmNode childNode = (XdmNode)childIterator.next();
                String childTag = childNode.getNodeName().getLocalName();
                if (childTag.equals("error") || childTag.equals("assert-serialization-error")){
                    seenSingleErrorInAny = true;
                    // We cannot use AsserError as we can check for multiple error codes and then log for each of them
                    String expectedError = assertion.attribute("code");
                    if (!Arrays.asList(supportedErrorCodes).contains(expectedError))
                        seenUnsupportedCode = true;
                    else
                        foundSingleMatch = e.getErrorCode().equals(expectedError);
                }
            }
            if (foundSingleMatch){
                LogSuccess(testCaseName);
                return;
            }
            else if (seenUnsupportedCode){
                LogUnsupportedErrorCode(testCaseName);
                return;
            }
            else if (seenSingleErrorInAny){
                LogFail(testCaseName);
                return;
            }

            // If it has any but we are not supposed to compare Error codes (did not see single one), then it is a Crash
        }
        LogCrash(testCaseName);
    }

    private void AssertError(XdmNode assertion, String testCaseName, String errorCode) {
        String expectedError = assertion.attribute("code");
        if (!Arrays.asList(supportedErrorCodes).contains(expectedError)){
            LogUnsupportedErrorCode(testCaseName);
        }
        else {
            TestPassOrFail(errorCode.equals(expectedError), testCaseName, true);
        }
    }

    private boolean checkAssertion(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String tag = assertion.getNodeName().getLocalName();
        // It can fail same as in main one since nested queries can fail in the same way, same reason!
        // However, these exceptions will be caught outside and we should not worry about them here
        // Whenever we get error, we will end up in the exception block. No point in having case "error" here
        switch(tag) {
            case "assert-empty":
                return AssertEmpty(resultAsList);
            case "assert":
                return Assert(resultAsList, assertion);
            case "assert-eq":
                return AssertEq(resultAsList, assertion);
            case "assert-deep-eq":
                return AssertDeepEq(resultAsList, assertion);
            case "assert-true":
                return AssertTrue(resultAsList);
            case "assert-false":
                return !AssertTrue(resultAsList);
            case "assert-string-value":
                return AssertStringValue(resultAsList, assertion);
            case "all-of":
                return AssertAllOf(resultAsList, assertion);
            case "any-of":
                return AssertAnyOf(resultAsList, assertion);
            case "assert-type":
                return AssertType(resultAsList, assertion);
            case "assert-count":
                return AssertCount(resultAsList, assertion);
            case "not":
                return AssertNot(resultAsList, assertion);
            case "assert-permutation":
                return AssertPermutation(resultAsList, assertion);
                // error codes and serialization-error codes are not handled here as they always cause exceptions
                // "assert-message", "assert-warning", "assert-result-document", "assert-serialization" do not exist
                // "assert-xml", "serialization-matches" missing
            default:
                return false;
        }
    }

    private boolean AssertPermutation(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String assertExpression =
            "declare function allpermutations($sequence as item*) as array* {\n" +
                " if(count($sequence) le 1)\n" +
                " then\n" +
                "   [ $sequence ]\n" +
                " else\n" +
                "   for $i in 1 to count($sequence)\n" +
                "   let $first := $sequence[$i]\n" +
                "   let $others :=\n" +
                "     for $s in $sequence\n" +
                "     count $c\n" +
                "     where $c ne $i\n" +
                "     return $s\n" +
                "   for $recursive in allpermutations($others)\n" +
                "   return [ $first, $recursive[]]\n" +
                "};\n" +
                "\n" +
                "some $a in allpermutations($result) " +
                    "satisfies deep-equal($a[], ((" + Convert(assertion.getStringValue()) + ")))";
        return runNestedQuery(resultAsList, assertExpression);
    }

    private boolean AssertNot(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        // According to analysis and way QT3 Test Suite was implemented, it is always one!
        XdmNode childUnderNot = assertion.select(Steps.child("*")).asList().get(0);
        return !checkAssertion(resultAsList, childUnderNot);
    }

    private boolean AssertCount(List<Item> resultAsList, XdmNode assertion) {
        // I do not know how to create nested query here
        int assertExpression = Integer.parseInt(assertion.getStringValue());
        return  resultAsList.size() == assertExpression;
    }

    private boolean AssertDeepEq(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String assertExpression = "deep-equal((" + Convert(assertion.getStringValue()) + "),$result)";
        return runNestedQuery(resultAsList, assertExpression);
    }

    private boolean AssertAnyOf(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        Iterator childIterator = assertion.children("*").iterator();

        while (childIterator.hasNext())
        {
            if (checkAssertion(resultAsList, (XdmNode)childIterator.next()))
                return true;
        }
        return false;
    }

    private boolean AssertEq(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String expectedResult = "$result eq " + Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private boolean Assert(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String expectedResult = Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private boolean runNestedQuery(List<Item> resultAsList, String expectedResult){
        RumbleRuntimeConfiguration configuration = new RumbleRuntimeConfiguration(
                new String[]{
                        "--output-format","json"
                });

        configuration.setExternalVariableValue(
                Name.createVariableInNoNamespace(resultVariableName),
                resultAsList);

        String assertExpression = "declare variable $result external;" + expectedResult;

        Rumble rumbleInstance = new Rumble(configuration);

        List<Item> nestedResult = runQuery(assertExpression, rumbleInstance);

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

    private boolean AssertStringValue(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
         String expectedResult = "string-join($result ! string($$), \" \") eq " + "\"" + Convert(assertion.getStringValue() + "\"");
         return runNestedQuery(resultAsList, expectedResult);
    }

    private String Convert(String testString) throws UnsupportedTypeException {
        if (!Constants.TO_CONVERT)
            return testString;

        // Converting assertion is done in all respective assert methods
        // What was found in fn/abs.xml and math/math-acos.xml is now replaced with convert types
        testString = ConvertAtomicTypes(testString);
        testString = ConvertNonAtomicTypes(testString);

        // TODO Verify this
        testString = testString.replace("'", "\"");

        // Replace with Regex Checks
        testString = testString.replace("fn:","");
        testString = testString.replace("math:","");
        testString = testString.replace("map:","");
        testString = testString.replace("array:","");
        testString = testString.replace("xs:",""); // This should be handled with all the types before
        //testString = testString.replace("op:",""); // doesn't exist
        //testString = testString.replace("prod:",""); // doesn't exist

        // Found in math acos, asin, atan, cos, exp, exp10, log, log10, pow, sin, sqrt, tan (tests 7, 8, 9 usually)
        //testString = testString.replace("double('NaN')", "double(\"NaN\")");
        //testString = testString.replace("double('INF')", "double(\"Infinity\")");
        //testString = testString.replace("double('-INF')", "double(\"-Infinity\")");
        //testString = testString.replace("INF", "Infinity");

        // XML notation
        testString = testString.replace(". ","$$ ");

        return testString;
    }

    private boolean AssertAllOf(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        Iterator childIterator = assertion.children("*").iterator();

        while (childIterator.hasNext())
        {
            if (!checkAssertion(resultAsList, (XdmNode)childIterator.next()))
                return false;
        }
        return true;
    }

    private boolean AssertType(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        // Convert will already take care of not allowed conversions and throw Unsupported Type error Exception
        // We do not need the same Switch case here as if no Exception is thrown, we can simply run the query
        String expectedResult = "$result instance of " + Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private List<Item> runQuery(String query, Rumble rumbleInstance){
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
            "fn-distinct-values-2"
    };

    private String ConvertAtomicTypes(String testString) throws UnsupportedTypeException {
        // List complies with Supported Types list available at https://rumble.readthedocs.io/en/latest/JSONiq/
        testString = testString.replace("xs:atomic","atomic");
        testString = testString.replace("xs:anyURI","anyURI");
        testString = testString.replace("xs:base64Binary","base64Binary");
        testString = testString.replace("xs:boolean","boolean");
        if (testString.contains("xs:byte")) throw new UnsupportedTypeException();
        testString = testString.replace("xs:date","date");
        testString = testString.replace("xs:dateTime","dateTime");
        if (testString.contains("xs:dateTimeStamp")) throw new UnsupportedTypeException();
        testString = testString.replace("xs:dayTimeDuration","dayTimeDuration");
        testString = testString.replace("xs:decimal","decimal");
        testString = testString.replace("xs:double","double");
        testString = testString.replace("xs:duration","duration");
        if (testString.contains("xs:float")) throw new UnsupportedTypeException();
        if (testString.contains("xs:gDay")) throw new UnsupportedTypeException();
        if (testString.contains("xs:gMonth")) throw new UnsupportedTypeException();
        if (testString.contains("xs:gYear")) throw new UnsupportedTypeException();
        if (testString.contains("xs:gYearMonth")) throw new UnsupportedTypeException();
        testString = testString.replace("xs:hexBinary","hexBinary");
        // If we put replace for xs:int before xs:integer. In case that we have xs:integer we would get integereger
        testString = testString.replace("xs:integer","integer");
        // int is 32bits, integer is infinite. It is okay to do this conversion now
        testString = testString.replace("xs:int","integer");
        if (testString.contains("xs:long")) throw new UnsupportedTypeException();
        if (testString.contains("xs:negativeInteger")) throw new UnsupportedTypeException();
        if (testString.contains("xs:nonPositiveInteger")) throw new UnsupportedTypeException();
        if (testString.contains("xs:nonNegativeInteger")) throw new UnsupportedTypeException();
        if (testString.contains("xs:positiveInteger")) throw new UnsupportedTypeException();
        if (testString.contains("xs:short")) throw new UnsupportedTypeException();
        testString = testString.replace("xs:string","string");
        testString = testString.replace("xs:time","time");
        if (testString.contains("xs:unsignedByte")) throw new UnsupportedTypeException();
        if (testString.contains("xs:unsignedInt")) throw new UnsupportedTypeException();
        if (testString.contains("xs:unsignedLong")) throw new UnsupportedTypeException();
        if (testString.contains("xs:unsignedShort")) throw new UnsupportedTypeException();
        testString = testString.replace("xs:yearMonthDuration","yearMonthDuration");

        // Not mentioned in the list but existing in the tests. Not sure whether these are atomic types
        if (testString.contains("xs:untypedAtomic")) throw new UnsupportedTypeException();
        if (testString.contains("xs:dateTimeStamp")) throw new UnsupportedTypeException();
        if (testString.contains("xs:anyAtomicType")) throw new UnsupportedTypeException();
        if (testString.contains("xs:error")) throw new UnsupportedTypeException();
        if (testString.contains("xs:normalizedString")) throw new UnsupportedTypeException();
        if (testString.contains("xs:numeric")) throw new UnsupportedTypeException();
        if (testString.contains("xs:token")) throw new UnsupportedTypeException();
        if (testString.contains("xs:NMTOKEN")) throw new UnsupportedTypeException();
        if (testString.contains("xs:NCName")) throw new UnsupportedTypeException();
        if (testString.contains("xs:Name")) throw new UnsupportedTypeException();
        if (testString.contains("xs:language")) throw new UnsupportedTypeException();
        if (testString.contains("xs:ENTITY")) throw new UnsupportedTypeException();
        if (testString.contains("xs:ID")) throw new UnsupportedTypeException();
        if (testString.contains("xs:IDREF")) throw new UnsupportedTypeException();
        if (testString.contains("xs:anyType")) throw new UnsupportedTypeException();
        if (testString.contains("xs:anySimpleType")) throw new UnsupportedTypeException();
        if (testString.contains("xs:untyped")) throw new UnsupportedTypeException();
        if (testString.contains("xs:doesNotExist")) throw new UnsupportedTypeException();
        if (testString.contains("xs:NOTEXIST")) throw new UnsupportedTypeException();
        if (testString.contains("xs:doesNotExistExampleCom")) throw new UnsupportedTypeException();
        if (testString.contains("xs:name")) throw new UnsupportedTypeException();
        if (testString.contains("xs:untypedAny")) throw new UnsupportedTypeException();
        if (testString.contains("xs:undefinedType")) throw new UnsupportedTypeException();
        if (testString.contains("xs:unknownType")) throw new UnsupportedTypeException();
        if (testString.contains("xs:qname")) throw new UnsupportedTypeException();

        return testString;
    }

    private String ConvertNonAtomicTypes(String testString) throws UnsupportedTypeException {
        // testString = testString.replace("array()","array"); // TODO check single file ArrowPostfix-022
        // Also array(+), array(?), array()*, array()+, array()? do not exist
        testString = testString.replace("array(*)","array*");

        // Will cover all the subclasses - item()+, item()* and item()+. item(anything here) does not exist
        testString = testString.replace("item()","item");

        // These are not types but instantiations of boolean handled differently
        testString = testString.replace("true()","true");
        testString = testString.replace("false()","false");


        // 7 kinds of XML nodes // TODO how to do regex check because in between can be something different than *
        if (testString.contains("document()")) throw new UnsupportedTypeException();
        if (testString.contains("document(*)")) throw new UnsupportedTypeException();
        if (testString.contains("element()")) throw new UnsupportedTypeException();
        if (testString.contains("element(*)")) throw new UnsupportedTypeException();
        if (testString.contains("attribute()")) throw new UnsupportedTypeException();
        if (testString.contains("attribute(*)")) throw new UnsupportedTypeException();
        if (testString.contains("text()")) throw new UnsupportedTypeException();
        if (testString.contains("text(*)")) throw new UnsupportedTypeException(); // '*' is not allowed inside text()
        if (testString.contains("comment()")) throw new UnsupportedTypeException();
        if (testString.contains("comment(*)")) throw new UnsupportedTypeException(); // '*' is not allowed inside text()
        if (testString.contains("processing-instruction()")) throw new UnsupportedTypeException();
        if (testString.contains("processing-instruction(*)")) throw new UnsupportedTypeException(); // '*' is not allowed inside text()
        if (testString.contains("QName")) throw new UnsupportedTypeException(); // The xs:QName constructor function must be passed exactly one argument, not zero.

        // TODO some more that I found out
        if (testString.contains("map(*)")) throw new UnsupportedTypeException();
        testString = testString.replace("map(","object(");
        testString = testString.replace("map{","{");
        testString = testString.replace("map {"," {");
        if (testString.contains("node()")) throw new UnsupportedTypeException();
        if (testString.contains("empty-sequence()")) throw new UnsupportedTypeException();
        if (testString.contains("xs:NOTATION")) throw new UnsupportedTypeException();

        return testString;
    }

    private class UnsupportedTypeException extends Throwable {
    }

    private String[] supportedErrorCodes = new String[]{
            "FOAR0001", "FOCA0002", "FODC0002", "FOFD1340", "FOFD1350", "JNDY0003", "JNTY0004", "JNTY0024", "JNTY0018",
            "RBDY0005", "RBML0001", "RBML0002", "RBML0003", "RBML0004", "RBML0005", "RBST0001", "RBST0002", "RBST0003",
            "RBST0004", "SENR0001", "XPDY0002", "XPDY0050", "XPDY0130", "XPST0003", "XPST0008", "XPST0017", "XPST0080",
            "XPST0081", "XPTY0004", "XQDY0054", "XQST0016", "XQST0031", "XQST0033", "XQST0034", "XQST0038", "XQST0039",
            "XQST0047", "XQST0048", "XQST0049", "XQST0052", "XQST0059", "XQST0069", "XQST0088", "XQST0089", "XQST0094"
    };
}
