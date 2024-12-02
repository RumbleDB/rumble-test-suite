package driver;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;
import org.apache.commons.lang.StringUtils;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.context.Name;
import org.rumbledb.exceptions.RumbleException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class TestDriver {
    private Path testsRepositoryDirectoryPath;
    private Rumble rumbleInstance;
    private int numberOfFails;
    private int numberOfSuccess;
    private int numberOfSkipped;
    private int numberOfCrashes;
    private int numberOfDependencies;
    private int numberOfUnsupportedTypes;
    private int numberOfUnsupportedErrorCodes;
    private int numberOfProcessedTestCases;
    private int numberOfManaged;

    public static String currentTestCase;
    public static String currentTestSet;
    public static List<Object[]> allTests = new ArrayList<>();


    public final StringBuffer TEST_CASE_SB = new StringBuffer();
    public final StringBuffer UNSUPPORTED_TYPE_SB = new StringBuffer();
    public final StringBuffer CRASHED_TESTS_SB = new StringBuffer();
    public final StringBuffer FAILED_TESTS_SB = new StringBuffer();
    public final StringBuffer DEPENDENCY_TESTS_SB = new StringBuffer();
    public final StringBuffer UNSUPPORTED_ERRORS_SB = new StringBuffer();
    public final StringBuffer SKIPPED_TESTS_SB = new StringBuffer();
    public final StringBuffer SUCCESS_TESTS_SB = new StringBuffer();
    public final StringBuffer MANAGED_TESTS_SB = new StringBuffer();
    public final StringBuffer BROKEN_TESTS_SB = new StringBuffer();

    // For JSON-doc
    private final Map<String, String> URItoPathLookupTable = new HashMap<>();

    public void execute() throws IOException, SaxonApiException {
        getTestsRepository();
        initializeSparkAndRumble();

        processCatalog(new File(testsRepositoryDirectoryPath.resolve("catalog.xml").toString()));

        logResults();
    }

    private void getTestsRepository() {
        if (Constants.USE_CONVERTED_TEST_SUITE) {
            // use converted test suite from converter
            Path convertedTestSuitesDirectory = Constants.WORKING_DIRECTORY_PATH.resolve(
                Constants.OUTPUT_TEST_SUITE_DIRECTORY
            );
            File[] allConvertedTestSuiteDirectories = new File(convertedTestSuitesDirectory.toString()).listFiles();
            Arrays.sort(allConvertedTestSuiteDirectories, Comparator.reverseOrder());
            testsRepositoryDirectoryPath = allConvertedTestSuiteDirectories[0].toPath();
        } else {
            // use pure test suite, still needs converter to run before
            testsRepositoryDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve("qt3tests");
            System.out.println("Tests repository obtained!");
        }
    }

    private void initializeSparkAndRumble() {
        // Initialize configuration - the instance will be the same as in org.rumbledb.cli.Main.java (one for shell)
        rumbleInstance = new Rumble(
                new RumbleRuntimeConfiguration(
                        new String[] {
                            "--output-format",
                            "json"
                        }
                )
        );
    }

    private void processCatalog(File catalogFile) throws SaxonApiException, IOException {
        Processor testDriverProcessor = new Processor(false);
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

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode)
            throws SaxonApiException,
                IOException {

        List<String> testSetsToSkip = Files.readAllLines(
            Constants.WORKING_DIRECTORY_PATH.resolve("TestSetsToSkip.txt"),
            Charset.defaultCharset()
        );

        String testSetFileName = testSetNode.attribute("file");
        currentTestSet = testSetFileName;
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        String jsonDocName = "fn/json-doc";
        if (testSetFileName.contains(jsonDocName))
            prepareJsonDocEnvironment(testSetDocNode);

        resetCounters();
        for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
            if (!testSetsToSkip.contains(testSetFileName))
                this.processTestCase(testCase, xpc);
            else
                LogSkipped(testCase.attribute("name"));
            numberOfProcessedTestCases++;
        }
        System.out.println(
            testSetFileName
                + " Success: "
                + numberOfSuccess
                + " Managed: "
                + numberOfManaged
                + " Fails: "
                + numberOfFails
                +
                " Skipped: "
                + numberOfSkipped
                + " Dependencies: "
                + numberOfDependencies
                +
                " Crashes: "
                + numberOfCrashes
                + " UnsupportedTypes: "
                + numberOfUnsupportedTypes
                +
                " UnsupportedErrors: "
                + numberOfUnsupportedErrorCodes
        );
        int sum = (numberOfSuccess
            + numberOfManaged
            + numberOfFails
            + numberOfSkipped
            + numberOfDependencies
            + numberOfCrashes
            + numberOfUnsupportedTypes
            + numberOfUnsupportedErrorCodes);
        String checkMatching = sum == numberOfProcessedTestCases ? "OK" : "NOT";
        TEST_CASE_SB.append(
            String.format(
                "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s\n",
                testSetFileName,
                numberOfSuccess,
                numberOfManaged,
                numberOfFails,
                numberOfSkipped,
                numberOfDependencies,
                numberOfCrashes,
                numberOfUnsupportedTypes,
                numberOfUnsupportedErrorCodes,
                sum,
                numberOfProcessedTestCases,
                checkMatching
            )
        );
    }

    private void prepareJsonDocEnvironment(XdmNode testSetDocNode) {
        // For some reason we have to access the first one, and then we will see the environments
        List<XdmNode> environments = testSetDocNode.children()
            .iterator()
            .next()
            .select(Steps.child("environment"))
            .asList();
        for (XdmNode environment : environments) {
            List<XdmNode> resources = environment.select(Steps.descendant("resource")).asList();
            for (XdmNode resource : resources) {
                String file = resource.attribute("file");
                String uri = resource.attribute("uri");
                URItoPathLookupTable.put(uri, file);
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

    private void processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException, IOException {
        String testCaseName = testCase.attribute("name");
        currentTestCase = testCaseName;
        // check if testcase is skipped
        List<String> testCasesToSkip = Files.readAllLines(
            Constants.WORKING_DIRECTORY_PATH.resolve("TestCasesToSkip.txt"),
            Charset.defaultCharset()
        );
        if (testCasesToSkip.contains(testCaseName)) {
            LogSkipped(testCaseName);
            return;
        }
        XdmNode testNode = testCase.select(Steps.child("test")).asNode();

        // check for dependencies and stop if we dont support it
        String caseDependency = checkDependencies(testCase);
        if (caseDependency != null) {
            LogDependency(caseDependency);
            return;
        }

        StringBuilder testString = new StringBuilder();

        // setup environments
        List<XdmNode> environments = testCase.select(Steps.child("environment")).asList();
        if (environments != null && !environments.isEmpty()) {
            XdmNode environment = environments.get(0);
            Iterator<XdmNode> externalVariables = environment.children("param").iterator();
            if (externalVariables.hasNext()) {
                while (externalVariables.hasNext()) {
                    XdmNode param = externalVariables.next();
                    String name = param.attribute("name");
                    String source = param.attribute("source");

                    if (source == null) {
                        String select = param.attribute("select");
                        testString.append("let $").append(name).append(" := ").append(select).append(" ");
                    } else {
                        // TODO Check what source is for to handle in else
                    }
                }
                testString.append("return ");
            }
        }

        testString.append(testNode.getStringValue());

        // TODO figure out alternative results afterwards - this is if then else or...
        // Place above try catch block to have assertion available in the catch!
        XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);

        // check types and convert
        String convertedTestString;
        try {
            convertedTestString = Convert(testString.toString());
        } catch (UnsupportedTypeException e) {
            // unsupported type encountered, testcase is skipped
            LogUnsupportedType(testCaseName);
            return;
        }

        // run test case
        try {
            // JsonDoc converter
            if (testCaseName.startsWith("json-doc")) {
                String uri = StringUtils.substringBetween(convertedTestString, "(\"", "\")");
                String jsonDocFilename = URItoPathLookupTable.get(uri);
                String fullAbsoluteJsonDocPath = testsRepositoryDirectoryPath.resolve("fn/" + jsonDocFilename)
                    .toString();
                convertedTestString = convertedTestString.replace(uri, "file:" + fullAbsoluteJsonDocPath);
            }

            // Execute query
            List<Item> resultAsList = runQuery(convertedTestString, rumbleInstance);

            TestPassOrFail(
                checkAssertion(resultAsList, assertion),
                testCaseName,
                convertedTestString.contentEquals(testString)
            );
        } catch (UnsupportedTypeException ute) {
            // unsupported type encountered in assertion
            LogUnsupportedType(testCaseName);
        } catch (RumbleException re) {
            CheckForErrorCode(re, assertion, testCaseName);
        } catch (Exception | Error e) {
            LogCrash(testCaseName);
        }
    }

    /**
     * method that takes a testcase and returns
     * a String with the dependency that is problematic
     * or null if there is no problematic dependency
     */
    private String checkDependencies(XdmNode testCase) {
        String testCaseName = testCase.attribute("name");
        List<XdmNode> dependencies = testCase.select(Steps.child("dependency")).asList();
        dependencies.addAll(testCase.getParent().select(Steps.child("dependency")).asList());

        if (dependencies.isEmpty())
            return null;
        for (XdmNode dependencyNode : dependencies) {
            String type = dependencyNode.attribute("type");
            String value = dependencyNode.attribute("value");
            if (type == null || value == null) {
                throw new RuntimeException("Empty dependency encountered");
            }

            switch (type) {
                case "calendar": {
                    // CB - I don't think we support any other calendar
                    return (testCaseName + dependencyNode);
                }
                case "unicode-version": {
                    // 7.0,3.1.1,5.2,6.0,6.2 - We will need to look at the tests. I am not sure which
                    // unicode Java 8 supports. For now you can keep them all.
                    break;
                }
                case "unicode-normalization-form": {
                    // NFD,NFKD,NFKC,FULLY-NORMALIZED - We will need to play it by ear.
                    // LogDependency(testCaseName + dependencyNode.toString());
                    // return;
                    break;
                }
                case "format-integer-sequence": {
                    // ⒈,Α,α - I am not sure what this is, I would need to see the tests.
                    return (testCaseName + dependencyNode);
                }
                case "xml-version": {
                    // Rumble doesn't care about xml version, its XML specific
                    // TODO maybe it influences Saxon environment processor (check 197 in
                    // QT3TestDriverHE)
                    break;
                }
                case "xsd-version": {
                    // Rumble doesn't care about schema, its XML specific
                    // TODO maybe it influences Saxon environment processor (check 221 in
                    // QT3TestDriverHE)
                    break;
                }
                case "feature": {
                    // schemaValidation (XML specific)
                    // schemaImport (XML specific)
                    // advanced-uca-fallback (we don't support other collations)
                    // non_empty_sequence_collection (we don't support collection() yet)
                    // collection-stability (we don't support collection() yet)
                    // directory-as-collection-uri (we don't support collection() yet)
                    // non_unicode_codepoint_collation (we don't support other collations)
                    // simple-uca-fallback (we don't support other collations)
                    // olson-timezone (not supported yet)
                    // fn-format-integer-CLDR (not supported yet)
                    // xpath-1.0-compatibility (we are not backwards compatible with XPath 1.0)
                    // fn-load-xquery-module (not supported yet)
                    // fn-transform-XSLT (not supported yet)
                    // namespace-axis (XML specific)
                    // infoset-dtd (XML specific)
                    // serialization
                    // fn-transform-XSLT30 (not supported yet)
                    // remote_http (not sure what this is, do you have an example?)
                    // typedData (not sure what this is, do you have an example?)
                    // schema-location-hint (XML specific)
                    // Only three below are supported by Rumble. Included staticTyping myself as +20
                    // pass, 30 fail, 20 unsupported types but no crashes!
                    if (
                        !(value.contains("higherOrderFunctions")
                            || value.contains("moduleImport")
                            ||
                            value.contains("arbitraryPrecisionDecimal")
                            || value.contains("staticTyping"))
                    ) {
                        return (testCaseName + dependencyNode);
                    }
                    break;
                }
                case "default-language": {
                    // fr-CA not supported - we just support en
                    if (!value.contains("en")) {
                        return (testCaseName + dependencyNode);
                    }
                    break;
                }
                case "language": {
                    // xib,de,fr,it not supported - we just support en
                    if (!value.contains("en")) {
                        return (testCaseName + dependencyNode);
                    }
                    break;
                }
                // Check if not the XSLT (isApplicable original method)
                case "spec": {
                    // XP30+,XQ10+,XQ30+,XQ30,XQ31+,XP31+,XP31,XQ31,XP20,XQ10,XP20+,XP30 is ok, XT30+
                    // not
                    // if (!value.contains("XSLT") && !value.contains("XT")) {
                    if (!(value.contains("XQ") || value.contains("XP"))) {
                        return (testCaseName + dependencyNode);
                    }
                    break;
                }
                case "limit": {
                    // year_lt_0 - I am not sure I don't think we have this limit.
                    return (testCaseName + dependencyNode);
                }
                default: {
                    System.out.println(
                        "WARNING: unconsidered dependency " + type + " in " + testCaseName + "; removing testcase"
                    );
                    return (testCaseName + dependencyNode);
                }
            }
        }
        // all dependencies are okay
        return null;
    }

    private void TestPassOrFail(boolean conditionToEvaluate, String testCaseName, boolean isQueryUnchanged) {
        // Was merged into single code for both AssertError and also the part in processTestCase.
        if (conditionToEvaluate) {
            if (isQueryUnchanged) {
                LogSuccess(testCaseName);
            } else {
                LogManaged(testCaseName);
            }
        } else {
            LogFail(testCaseName);
        }
    }

    private void LogSuccess(String lineText) {
        numberOfSuccess++;
        SUCCESS_TESTS_SB.append(lineText + "\n");
    }

    private void LogManaged(String lineText) {
        numberOfManaged++;
        MANAGED_TESTS_SB.append(lineText + "\n");
    }

    private void LogFail(String lineText) {
        numberOfFails++;
        FAILED_TESTS_SB.append(lineText + "\n");
    }

    private void LogSkipped(String lineText) {
        numberOfSkipped++;
        SKIPPED_TESTS_SB.append(lineText + "\n");
    }

    private void LogDependency(String lineText) {
        numberOfDependencies++;
        DEPENDENCY_TESTS_SB.append(lineText + "\n");
    }

    private void LogCrash(String lineText) {
        numberOfCrashes++;
        CRASHED_TESTS_SB.append(lineText + "\n");
    }

    private void LogUnsupportedType(String lineText) {
        numberOfUnsupportedTypes++;
        UNSUPPORTED_TYPE_SB.append(lineText + "\n");
    }

    private void LogUnsupportedErrorCode(String lineText) {
        numberOfUnsupportedErrorCodes++;
        UNSUPPORTED_ERRORS_SB.append(lineText + "\n");
    }

    private void CheckForErrorCode(RumbleException e, XdmNode assertion, String testCaseName) {
        String tag = assertion.getNodeName().getLocalName();
        // Logic for even though we only support SENR0001
        if (tag.equals("error") || tag.equals("assert-serialization-error")) {
            CustomAssertError(assertion, testCaseName, e.getErrorCode());
            return;
        } else if (tag.equals("any-of")) {
            Iterator<XdmNode> childIterator = assertion.children("*").iterator();
            boolean foundSingleMatch = false;
            boolean seenUnsupportedCode = false;
            boolean seenSingleErrorInAny = false;

            while (childIterator.hasNext() && !foundSingleMatch) {
                XdmNode childNode = childIterator.next();
                String childTag = childNode.getNodeName().getLocalName();
                if (childTag.equals("error") || childTag.equals("assert-serialization-error")) {
                    seenSingleErrorInAny = true;
                    // We cannot use AsserError as we can check for multiple error codes and then log for each of them
                    String expectedError = assertion.attribute("code");
                    if (!Arrays.asList(Constants.supportedErrorCodes).contains(expectedError))
                        seenUnsupportedCode = true;
                    else
                        foundSingleMatch = e.getErrorCode().equals(expectedError);
                }
            }
            if (foundSingleMatch) {
                LogSuccess(testCaseName);
                return;
            } else if (seenUnsupportedCode) {
                LogUnsupportedErrorCode(testCaseName);
                return;
            } else if (seenSingleErrorInAny) {
                LogFail(testCaseName);
                return;
            }

            // If it has any but we are not supposed to compare Error codes (did not see single one), then it is a Crash
        }
        LogCrash(testCaseName);
    }

    private void CustomAssertError(XdmNode assertion, String testCaseName, String errorCode) {
        String expectedError = assertion.attribute("code");
        if (!Arrays.asList(Constants.supportedErrorCodes).contains(expectedError)) {
            LogUnsupportedErrorCode(testCaseName);
        } else {
            TestPassOrFail(errorCode.equals(expectedError), testCaseName, true);
        }
    }

    private boolean checkAssertion(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String tag = assertion.getNodeName().getLocalName();
        // It can fail same as in main one since nested queries can fail in the same way, same reason!
        // However, these exceptions will be caught outside and we should not worry about them here
        // Whenever we get error, we will end up in the exception block. No point in having case "error" here
        switch (tag) {
            case "assert-empty":
                return CustomAssertEmpty(resultAsList);
            case "assert":
                return CustomAssert(resultAsList, assertion);
            case "assert-eq":
                return CustomAssertEq(resultAsList, assertion);
            case "assert-deep-eq":
                return CustomAssertDeepEq(resultAsList, assertion);
            case "assert-true":
                return CustomAssertTrue(resultAsList);
            case "assert-false":
                return !CustomAssertTrue(resultAsList);
            case "assert-string-value":
                return CustomAssertStringValue(resultAsList, assertion);
            case "all-of":
                return CustomAssertAllOf(resultAsList, assertion);
            case "any-of":
                return CustomAssertAnyOf(resultAsList, assertion);
            case "assert-type":
                return CustomAssertType(resultAsList, assertion);
            case "assert-count":
                return CustomAssertCount(resultAsList, assertion);
            case "not":
                return CustomAssertNot(resultAsList, assertion);
            case "assert-permutation":
                return CustomAssertPermutation(resultAsList, assertion);
            // error codes are not handled here as they always cause exceptions
            // "assert-message", "assert-warning", "assert-result-document", "assert-serialization" do not exist
            // "assert-xml", "serialization-matches" missing
            default:
                return false;
        }
    }

    private boolean CustomAssertPermutation(List<Item> resultAsList, XdmNode assertion)
            throws UnsupportedTypeException {
        String assertExpression =
            "declare function allpermutations($sequence as item*) as array* {\n"
                +
                " if(count($sequence) le 1)\n"
                +
                " then\n"
                +
                "   [ $sequence ]\n"
                +
                " else\n"
                +
                "   for $i in 1 to count($sequence)\n"
                +
                "   let $first := $sequence[$i]\n"
                +
                "   let $others :=\n"
                +
                "     for $s in $sequence\n"
                +
                "     count $c\n"
                +
                "     where $c ne $i\n"
                +
                "     return $s\n"
                +
                "   for $recursive in allpermutations($others)\n"
                +
                "   return [ $first, $recursive[]]\n"
                +
                "};\n"
                +
                "\n"
                +
                "some $a in allpermutations($result) "
                +
                "satisfies deep-equal($a[], (("
                + Convert(assertion.getStringValue())
                + ")))";
        return runNestedQuery(resultAsList, assertExpression);
    }

    private boolean CustomAssertNot(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        // According to analysis and way QT3 Test Suite was implemented, it is always one!
        XdmNode childUnderNot = assertion.select(Steps.child("*")).asList().get(0);
        return !checkAssertion(resultAsList, childUnderNot);
    }

    private boolean CustomAssertCount(List<Item> resultAsList, XdmNode assertion) {
        // I do not know how to create nested query here
        int assertExpression = Integer.parseInt(assertion.getStringValue());
        return resultAsList.size() == assertExpression;
    }

    private boolean CustomAssertDeepEq(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String assertExpression = "deep-equal((" + Convert(assertion.getStringValue()) + "),$result)";
        return runNestedQuery(resultAsList, assertExpression);
    }

    private boolean CustomAssertAnyOf(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {

        for (XdmNode xdmItems : assertion.children("*")) {
            if (checkAssertion(resultAsList, xdmItems))
                return true;
        }
        return false;
    }

    private boolean CustomAssertEq(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String expectedResult = "$result eq " + Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private boolean CustomAssert(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String expectedResult = Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private boolean runNestedQuery(List<Item> resultAsList, String expectedResult) {
        RumbleRuntimeConfiguration configuration = new RumbleRuntimeConfiguration(
                new String[] {
                    "--output-format",
                    "json"
                }
        );

        String resultVariableName = "result";
        configuration.setExternalVariableValue(
            Name.createVariableInNoNamespace(resultVariableName),
            resultAsList
        );

        String assertExpression = "declare variable $result external;" + expectedResult;

        Rumble rumbleInstance = new Rumble(configuration);

        List<Item> nestedResult = runQuery(assertExpression, rumbleInstance);

        return CustomAssertTrue(nestedResult);
    }


    private boolean CustomAssertTrue(List<Item> resultAsList) {
        allTests.add(new Object[] { resultAsList, currentTestSet, currentTestCase });
        if (resultAsList.size() != 1)
            return false;
        if (!resultAsList.get(0).isBoolean())
            return false;

        return resultAsList.get(0).getBooleanValue();
    }

    private boolean CustomAssertEmpty(List<Item> resultAsList) {
        return resultAsList.isEmpty();
    }

    private boolean CustomAssertStringValue(List<Item> resultAsList, XdmNode assertion)
            throws UnsupportedTypeException {
        String expectedResult = "string-join($result ! string($$), \" \") eq "
            + "\""
            + Convert(assertion.getStringValue() + "\"");
        return runNestedQuery(resultAsList, expectedResult);
    }

    private String Convert(String testString) throws UnsupportedTypeException {
        testString = ConvertAtomicTypes(testString);
        testString = ConvertNonAtomicTypes(testString);

        // TODO Verify this
        testString = testString.replace("'", "\"");

        // Replace with Regex Checks
        testString = testString.replace("fn:", "");
        testString = testString.replace("math:", "");
        testString = testString.replace("map:", "");
        testString = testString.replace("array:", "");
        testString = testString.replace("xs:", ""); // This should be handled with all the types before

        // XML notation
        testString = testString.replace(". ", "$$ ");

        return testString;
    }

    private boolean CustomAssertAllOf(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {

        for (XdmNode xdmItems : assertion.children("*")) {
            if (!checkAssertion(resultAsList, xdmItems))
                return false;
        }
        return true;
    }

    private boolean CustomAssertType(List<Item> resultAsList, XdmNode assertion) throws UnsupportedTypeException {
        String expectedResult = "$result instance of " + Convert(assertion.getStringValue());
        return runNestedQuery(resultAsList, expectedResult);
    }

    private List<Item> runQuery(String query, Rumble rumbleInstance) {
        SequenceOfItems queryResult = rumbleInstance.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
        return resultAsList;
    }

    private String ConvertAtomicTypes(String testString) throws UnsupportedTypeException {
        for (Map.Entry<String, String> entry : Constants.atomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        for (String target : Constants.unsupportedTypes) {
            if (testString.contains(target))
                throw new UnsupportedTypeException();
        }
        return testString;
    }

    private String ConvertNonAtomicTypes(String testString) {
        for (Map.Entry<String, String> entry : Constants.nonAtomicTypeConversions.entrySet()) {
            testString = testString.replace(entry.getKey(), entry.getValue());
        }
        return testString;
    }

    private static class UnsupportedTypeException extends Throwable {
    }


    private void logResults() throws IOException {
        Path logDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve("results");

        // For comparing with the previous statistics
        File[] allLogDirectories = new File(logDirectoryPath.toString()).listFiles();
        Arrays.sort(allLogDirectories, Comparator.reverseOrder());
        Path lastSuccessPath = allLogDirectories[0].toPath().resolve("Success.txt");
        Path lastManagedPath = allLogDirectories[0].toPath().resolve("Managed.txt");
        Path lastCrashesPath = allLogDirectories[0].toPath().resolve("Crashes.txt");
        Charset charset = Charset.defaultCharset();
        List<String> allPreviousPassedTests = null;
        List<String> allPreviousCrashedTests = null;
        try {
            allPreviousPassedTests = Files.readAllLines(lastSuccessPath, charset);
            allPreviousPassedTests.addAll(Files.readAllLines(lastManagedPath, charset));
            allPreviousCrashedTests = Files.readAllLines(lastCrashesPath, charset);
        } catch (IOException e) {
            // First time it will fail and we will check for null
        }
        // Instantiate with new ArrayList, otherwise you cannot do addAll since asList returns non-resizable
        List<String> allCurrentPassedTests = new ArrayList<>(
                Arrays.asList(SUCCESS_TESTS_SB.toString().split("\n"))
        );
        allCurrentPassedTests.addAll(Arrays.asList(MANAGED_TESTS_SB.toString().split("\n")));

        if (allPreviousPassedTests != null) {
            for (String passedTest : allPreviousPassedTests) {
                if (
                    !allCurrentPassedTests.contains(passedTest)
                        && !passedTest.contains("List of all test cases")
                ) {
                    BROKEN_TESTS_SB.append(passedTest + "\n");
                }
            }
        }

        // Slightly repetitive, might be refactored
        List<String> allCurrentCrashedTests = new ArrayList<>(
                Arrays.asList(CRASHED_TESTS_SB.toString().split("\n"))
        );
        if (allPreviousCrashedTests != null) {
            BROKEN_TESTS_SB.append(
                "\n" + "Tests that were not crashing before, but are now and not in list above:" + "\n"
            );
            for (String crashedTest : allCurrentCrashedTests)
                if (
                    !allPreviousCrashedTests.contains(crashedTest)
                        && !crashedTest.contains("List of all test cases")
                        && !allPreviousPassedTests.contains(crashedTest)
                ) {
                    BROKEN_TESTS_SB.append(crashedTest + "\n");
                }
        }

        // Create directory for new statistics
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        Path logSubDirectoryPath = logDirectoryPath.resolve(timeStamp);
        File logSubDirectory = new File(logSubDirectoryPath.toString());
        if (!logSubDirectory.exists())
            logSubDirectory.mkdirs();

        Log(
            logSubDirectoryPath.resolve("Statistics.csv").toString(),
            "TestSetFileName,Success,Managed,Fails,Skipped,Dependencies,Crashes,UnsupportedTypes,UnsupportedErrorCodes,Sum,Processed,Matches\n",
            TEST_CASE_SB
        );
        Log(
            logSubDirectoryPath.resolve("UnsupportedTypes.txt").toString(),
            "List of all test cases:\n",
            UNSUPPORTED_TYPE_SB
        );
        Log(
            logSubDirectoryPath.resolve("Crashes.txt").toString(),
            "List of all test cases:\n",
            CRASHED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Fails.txt").toString(),
            "List of all test cases:\n",
            FAILED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Dependencies.txt").toString(),
            "List of all test cases:\n",
            DEPENDENCY_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("UnsupportedErrorCodes.txt").toString(),
            "List of all test cases:\n",
            UNSUPPORTED_ERRORS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Skipped.txt").toString(),
            "List of all test cases:\n",
            SKIPPED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Success.txt").toString(),
            "List of all test cases:\n",
            SUCCESS_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("Managed.txt").toString(),
            "List of all test cases:\n",
            MANAGED_TESTS_SB
        );
        Log(
            logSubDirectoryPath.resolve("BrokenWithLatestImplementation.txt").toString(),
            "List of test cases that were passing before but not anymore:\n",
            BROKEN_TESTS_SB
        );
    }

    private static void Log(String testCaseFilePath, String header, StringBuffer stringBuffer) throws IOException {
        PrintWriter summedWorkerThreads = new PrintWriter(testCaseFilePath);
        summedWorkerThreads.write(header);
        summedWorkerThreads.close();
        Files.write(Paths.get(testCaseFilePath), stringBuffer.toString().getBytes(), StandardOpenOption.APPEND);
    }
}
