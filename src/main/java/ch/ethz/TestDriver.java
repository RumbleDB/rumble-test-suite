package ch.ethz;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.commons.lang.StringUtils;
import org.apache.spark.sql.SparkSession;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.context.Name;
import org.rumbledb.exceptions.RumbleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TestDriver {
    private String testsRepositoryScriptFileName = "get-tests-repository.sh";
    private String catalogFileName = "catalog.xml";
    private Path testsRepositoryDirectoryPath;
    // Set this field if you want to run a specific test set that starts with string below
    private String testSetToTest = ""; // json
    // Set this field if you want to run a specific test case that starts with string below
    private String testCaseToTest = ""; // math-acos-005, json-doc-002, math-acos-002
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

    // For JSON-doc
    private Map<String, String> URItoPathLookupTable = new HashMap<>();
    private String jsonDocName = "fn/json-doc";

    void execute() {
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

        // TODO remove the skip once https://github.com/RumbleDB/rumble/issues/805 is fixed
        if (testSetFileName.contains(jsonDocName))
            return;
            //prepareJsonDocEnvironment(testSetDocNode);

        if (testSetToTest.equals("") || testSetFileName.contains(testSetToTest)) {
            resetCounters();
            for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
                this.processTestCase(testCase, xpc);
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
                    // TODO check what happens when we include dependencies on the TEST SET LEVEL
                    //dependencies.addAll(testCase.getParent().select(Steps.child("dependency")).asList());

                    if (dependencies != null && dependencies.size() != 0) {
                        boolean allDependenciesSatisfied = false;
                        for (XdmNode dependencyNode : dependencies) {
                            String type = dependencyNode.attribute("type");
                            String value = dependencyNode.attribute("value");
                            if (type == null || value == null){
                                // TODO Maybe separate in another file in future (or just contain all skips in one)
                                Constants.SKIPPED_TESTS_SB.append(testCaseName + dependencyNode.toString() + "\n");
                                numberOfSkipped++;
                                return;
                            }

                            // TODO implement all possible dependency check
                            switch (type){
                                // Check if not the XSLT (isApplicable original method)
                                case "spec" :
                                {
                                    //if (!value.contains("XSLT") && !value.contains("XT")) {
                                    if (!(value.contains("XQ") || value.contains("XP"))){
                                        Constants.SKIPPED_TESTS_SB.append(testCaseName + dependencyNode.toString() + "\n");
                                        numberOfSkipped++;
                                        return;
                                    }
                                    else {
                                        allDependenciesSatisfied = true;
                                        break;
                                    }
                                }
                                case "higherOrderFunctions" :
                                {
                                    // Supported by Rumble
                                    allDependenciesSatisfied = true;
                                    break;
                                }
                                case "xsd-version" :
                                {
                                    // Rumble doesn't care about schema
                                    allDependenciesSatisfied = true;
                                    break;
                                }
                                default:
                                {
                                    Constants.DEPENDENCY_TESTS_SB.append(testCaseName + dependencyNode.toString() + "\n");
                                    numberOfDependencies++;
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // This exception means there are no dependencies and we can proceed with running the query
                }

                String testString = testNode.getStringValue();

                // TODO figure out alternative results afterwards - this is if then else or...
                // Place above try catch block to have assertion available in the catch!
                XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);

                try {
                    // Hard Coded converter
                    String convertedTestString = Convert(testString);

                    // JsonDoc converter
                    if (testCaseName.startsWith("json-doc")) {
                        String uri = StringUtils.substringBetween(convertedTestString, "('", "')");
                        String jsonDocFilename = URItoPathLookupTable.get(uri);
                        String fullAbsoluteJsonDocPath = testsRepositoryDirectoryPath.resolve("fn/" + jsonDocFilename).toString();
                        // TODO Think about handling the ' in hardcoded manner before in convert!
                        convertedTestString = convertedTestString.replace("'" + uri + "'",
                                                             "\"" + "file:" + fullAbsoluteJsonDocPath + "\"");
                    }

                    // Execute query
                    List<Item> resultAsList = runQuery(convertedTestString, rumbleInstance);

                    TestPassOrFail(checkAssertion(resultAsList, assertion), testCaseName, convertedTestString.equals(testString));
                } catch (UnsupportedTypeException ute) {
                    numberOfUnsupportedTypes++;
                    Constants.UNSUPPORTED_TYPE_SB.append(testCaseName + "\n");
                } catch (RumbleException re) {
                    CheckForErrorCode(re, assertion, testCaseName);
                } catch (Exception e){
                        numberOfCrashes++;
                        Constants.CRASHED_TESTS_SB.append(testCaseName + "\n");
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
            // Not logging these as they are only in the list below!
            numberOfSkipped++;
        }
    }

    private void TestPassOrFail(boolean conditionToEvaluate, String testCaseName, boolean isQueryUnchanged) {
        // Was merged into single code for both AssertError and also the part in processTestCase.
        if (conditionToEvaluate) {
            if (isQueryUnchanged) {
                numberOfSuccess++;
                Constants.SUCCESS_TESTS_SB.append(testCaseName + "\n");
            }
            else{
                numberOfManaged++;
                Constants.MANAGED_TESTS_SB.append(testCaseName + "\n");
            }
        } else {
            numberOfFails++;
            Constants.FAILED_TESTS_SB.append(testCaseName + "\n");
        }
    }

    private void CheckForErrorCode(RumbleException e, XdmNode assertion, String testCaseName) {
        String tag = assertion.getNodeName().getLocalName();
        if (tag.equals("error")){
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
                seenSingleErrorInAny = true;
                XdmNode childNode = (XdmNode)childIterator.next();
                String childTag = childNode.getNodeName().getLocalName();
                if (childTag.equals("error")){
                    // We cannot use AsserError as we can check for multiple error codes and then log for each of them
                    String expectedError = assertion.attribute("code");
                    if (!Arrays.asList(supportedErrorCodes).contains(expectedError))
                        seenUnsupportedCode = true;
                    else
                        foundSingleMatch = e.getErrorCode().equals(expectedError);
                }
            }
            if (foundSingleMatch){
                numberOfSuccess++;
                return;
            }
            else if (seenUnsupportedCode){
                numberOfUnsupportedErrorCodes++;
                Constants.UNSUPPORTED_ERRORS_SB.append(testCaseName + "\n");
                return;
            }
            else if (seenSingleErrorInAny){
                numberOfFails++;
                Constants.FAILED_TESTS_SB.append(testCaseName + "\n");
                return;
            }

            // If it has any but we are not supposed to compare Error codes (did not see single one), then it is a Crash
        }
        numberOfCrashes++;
        Constants.CRASHED_TESTS_SB.append(testCaseName + "\n");
    }

    private void AssertError(XdmNode assertion, String testCaseName, String errorCode) {
        String expectedError = assertion.attribute("code");
        if (!Arrays.asList(supportedErrorCodes).contains(expectedError)){
            numberOfUnsupportedErrorCodes++;
            Constants.UNSUPPORTED_ERRORS_SB.append(testCaseName + "\n");
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
            default:
                return false;
        }
    }

    private boolean AssertDeepEq(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String assertExpression = "deep-equal(" + Convert(assertion.getStringValue()) + ",$result)";
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
        // TODO maybe both to lower string
        String assertExpression = Convert(assertion.getStringValue());
        List<String> lines = resultAsList.stream().map(x -> x.serialize()).collect(Collectors.toList());
        return assertExpression.equals(String.join("\n", lines));

//        return AssertEq(resultAsList, assertion);
//
//        String expectedResult = "string($result) eq " + "\"" + Convert(assertion.getStringValue() + "\"");
//        return runNestedQuery(resultAsList, expectedResult);
    }

    private String Convert(String testString) throws UnsupportedTypeException {
        // Converting assertion is done in all respective assert methods
        // What was found in fn/abs.xml and math/math-acos.xml is now replaced with convert types
        testString = ConvertTypes(testString);

        // Verify this
        testString = testString.replace("'", "\"");

        // Replace with Regex Checks
        testString = testString.replace("fn:","");
        testString = testString.replace("math:","");
        testString = testString.replace("map:","");
        testString = testString.replace("array:","");
        //testString = testString.replace("op:",""); // doesn't exist
        //testString = testString.replace("prod:",""); // doesn't exist

        // Found in math acos, asin, atan, cos, exp, exp10, log, log10, pow, sin, sqrt, tan (tests 7, 8, 9 usually)
        //testString = testString.replace("double('NaN')", "double(\"NaN\")");
        //testString = testString.replace("double('INF')", "double(\"Infinity\")");
        //testString = testString.replace("double('-INF')", "double(\"-Infinity\")");
        testString = testString.replace("INF", "Infinity");

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
        String assertExpression = assertion.getStringValue();
        switch (assertExpression){
            case "xs:atomic":
                return resultAsList.get(0).isAtomic();
            case "xs:anyURI":
                return resultAsList.get(0).isAnyURI();
            case "xs:boolean":
                return resultAsList.get(0).isBoolean();
            case "xs:byte":
                throw new UnsupportedTypeException();
            case "xs:date":
                return resultAsList.get(0).isDate();
            case "xs:dateTime":
                return resultAsList.get(0).isDateTime();
            case "xs:dateTimeStamp":
                throw new UnsupportedTypeException();
            case "xs:dayTimeDuration":
                return resultAsList.get(0).isDayTimeDuration();
            case "xs:decimal":
                return resultAsList.get(0).isDecimal();
            case "xs:double":
                return resultAsList.get(0).isDouble();
            case "xs:duration":
                return resultAsList.get(0).isDuration();
            case "xs:float":
                throw new UnsupportedTypeException();
            case "xs:gDay":
                throw new UnsupportedTypeException();
            case "xs:gMonth":
                throw new UnsupportedTypeException();
            case "xs:gYear":
                throw new UnsupportedTypeException();
            case "xs:gYearMonth":
                throw new UnsupportedTypeException();
            case "xs:hexBinary":
                return resultAsList.get(0).isHexBinary();
            case "xs:int":
                return resultAsList.get(0).isInt();
            case "xs:integer":
                return resultAsList.get(0).isInteger();
            case "xs:long":
                throw new UnsupportedTypeException();
            case "xs:negativeInteger":
                throw new UnsupportedTypeException();
            case "xs:nonPositiveInteger":
                throw new UnsupportedTypeException();
            case "xs:nonNegativeInteger":
                throw new UnsupportedTypeException();
            case "xs:positiveInteger":
                throw new UnsupportedTypeException();
            case "xs:short":
                throw new UnsupportedTypeException();
            case "xs:string":
                return resultAsList.get(0).isString();
            case "xs:time":
                return resultAsList.get(0).isTime();
            case "xs:unsignedByte":
                throw new UnsupportedTypeException();
            case "xs:unsignedInt":
                throw new UnsupportedTypeException();
            case "xs:unsignedLong":
                throw new UnsupportedTypeException();
            case "xs:unsignedShort":
                throw new UnsupportedTypeException();
            case "xs:yearMonthDuration":
                return resultAsList.get(0).isYearMonthDuration();
            default:
                throw new UnsupportedTypeException();
        }
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
            "math-exp-008",  // We need to also somehow convert the assertion to JSONiq xs:double('INF') vs Infinity
            "math-exp10-007", // We need to also somehow convert the assertion to JSONiq xs:double('INF') vs Infinity
            "math-log-008", // We need to also somehow convert the assertion to JSONiq xs:double('INF') vs Infinity
            "math-log10-008", // We need to also somehow convert the assertion to JSONiq xs:double('INF') vs Infinity
            "json-doc-error-028" // Exception in Spark when populating list
            // "math-pow.xml" // Has a lot of xs:double('INF')
            // "abs.xml" // Abs always returns double and he wants Integer

    };

    private String ConvertTypes(String testString) throws UnsupportedTypeException {
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

        // Not mentioned in the list but existing in the tests
        if (testString.contains("xs:untypedAtomic")) throw new UnsupportedTypeException();
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
