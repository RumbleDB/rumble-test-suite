package evaluation;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CaseCollector {
    private static final Path TESTS_REPOSITORY_PATH = Constants.WORKING_DIRECTORY_PATH.resolve("qt3tests");

    private Path testsRepositoryDirectoryPath;
    private String currentTestSet;
    private final boolean useXQueryParser;
    private final List<CollectedTestCase> allTests = new ArrayList<>();
    private final TestCaseSelection testCaseSelection;

    public CaseCollector(boolean useXQueryParser, TestCaseSelection testCaseSelection) {
        this.useXQueryParser = useXQueryParser;
        this.testCaseSelection = testCaseSelection;
    }

    // environments in current testset
    private final Map<String, Environment> testSetEnvironments = new HashMap<>();

    // environments defined in catalog
    private final Map<String, Environment> catalogEnvironments = new HashMap<>();

    /**
     * method that collects all the testcases into a local variable allowing
     * getAllTests() to be called later
     */
    public void execute(String testFolder) throws IOException, SaxonApiException {
        getTestsRepository();
        processCatalog(testFolder);

        /// Check if the selected test case was resolved to at least one test. If not, throw an exception.
        this.testCaseSelection.verifyResolved();
    }

    /**
     * method that returns all collected testcases. execute() needs to be called
     * beforehand
     */
    public List<CollectedTestCase> getAllTests() {
        return this.allTests;
    }

    public void getTestsRepository() throws IOException {
        if (!Files.isDirectory(TESTS_REPOSITORY_PATH)) {
            throw new IOException(
                "Missing QT3 test repository at "
                    + TESTS_REPOSITORY_PATH
                    + ". Run ./get-tests-repository.sh before executing tests."
            );
        }

        this.testsRepositoryDirectoryPath = TESTS_REPOSITORY_PATH;
    }

    private String extractXmlVersion(XdmNode testCase) {
        List<XdmNode> dependencies = new ArrayList<>(testCase.select(Steps.child("dependency")).asList());
        dependencies.addAll(testCase.getParent().select(Steps.child("dependency")).asList());

        for (XdmNode dep : dependencies) {
            String type = dep.attribute("type");
            if ("xml-version".equals(type)) {
                return dep.attribute("value");
            }
        }
        return null; // fallback -> default (according to tests: XML10)
    }

    private void processCatalog(String testFolder) throws SaxonApiException {
        File catalogFile = new File(testsRepositoryDirectoryPath.resolve("catalog.xml").toString());
        Processor testDriverProcessor = new Processor(false);
        DocumentBuilder catalogBuilder = testDriverProcessor.newDocumentBuilder();
        catalogBuilder.setLineNumbering(true);
        XdmNode catalogNode = catalogBuilder.build(catalogFile);

        XPathCompiler xpc = testDriverProcessor.newXPathCompiler();
        xpc.setLanguageVersion("3.1");
        xpc.setCaching(true);
        xpc.declareNamespace("", "http://www.w3.org/2010/09/qt-fots-catalog");

        List<XdmNode> environments = catalogNode.select(Steps.descendant("environment")).asList();
        for (XdmNode environment : environments) {
            String envName = environment.attribute("name");
            Environment env = new Environment(environment, testsRepositoryDirectoryPath);
            catalogEnvironments.put(envName, env);
        }

        // testsets are defined with regex, allowing for example the split of fn into
        // two
        // most are just substring matching
        Pattern pattern = Pattern.compile("^" + testFolder);
        for (XdmNode testSet : catalogNode.select(Steps.descendant("test-set")).asList()) {
            Matcher matcher = pattern.matcher(testSet.attribute("file"));
            if (matcher.find()) {
                this.processTestSet(catalogBuilder, xpc, testSet);
            }
        }
    }

    private void processTestSet(DocumentBuilder catalogBuilder, XPathCompiler xpc, XdmNode testSetNode)
            throws SaxonApiException {

        String testSetFileName = testSetNode.attribute("file");
        this.currentTestSet = testSetFileName;
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        prepareTestSetEnvironments(testSetDocNode, testSetFileName.split("/")[0]);

        for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
            this.processTestCase(testCase, xpc);
        }
    }

    /**
     * method that prepares the environments for the whole testset
     */
    private void prepareTestSetEnvironments(XdmNode testSetDocNode, String testSet) {
        testSetEnvironments.clear();
        List<XdmNode> environments = testSetDocNode.select(Steps.child("test-set"))
            .asNode()
            .select(Steps.child("environment"))
            .asList();
        for (XdmNode environment : environments) {
            Environment env = new Environment(environment, testsRepositoryDirectoryPath.resolve(testSet));
            String envName = environment.attribute("name");
            testSetEnvironments.put(envName, env);
        }

    }

    private void processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String currentTestCase = testCase.attribute("name");
        if (!this.testCaseSelection.shouldRun(currentTestCase)) {
            return;
        }

        // check if testcase is skipped
        if (
            Constants.skippedTestSets.contains(this.currentTestSet)
                || Constants.skippedGeneralTestCases.contains(currentTestCase)
                || (!useXQueryParser && Constants.skippedJSONIQTestCases.contains(currentTestCase))
        ) {
            allTests.add(
                new CollectedTestCase(
                        new TestCase(null, null, "Testcase/set on skiplist", null, null),
                        currentTestSet,
                        currentTestCase
                )
            );
            return;
        }

        // get the relevant environment for the testcase
        Environment environment = null;
        List<XdmNode> environments = testCase.select(Steps.child("environment")).asList();
        if (environments != null && !environments.isEmpty()) {
            if (environments.get(0).attribute("ref") != null) {
                // predefined environment from testset or catalog
                String envName = environments.get(0).attribute("ref");
                if (catalogEnvironments.containsKey(envName)) {
                    environment = catalogEnvironments.get(envName);
                } else if (testSetEnvironments.containsKey(envName)) {
                    environment = testSetEnvironments.get(envName);
                } else {
                    throw new RuntimeException("No environment found with name: " + envName);
                }
            } else {
                // environment defined in testcase
                environment = new Environment(
                        environments.get(0),
                        testsRepositoryDirectoryPath.resolve(currentTestSet).getParent() // looks bad but works for now
                );
            }
        }

        // check for possible skip reasons
        String skipReason = null;
        if (environment != null && environment.isUnsupportedCollation()) {
            skipReason = "unsupported collation";
        }
        String caseDependency = checkDependencies(testCase);
        if (caseDependency != null) {
            skipReason = "dependency " + caseDependency;
        }
        String xmlVersion = extractXmlVersion(testCase);

        XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);
        String testString = testCase.select(Steps.child("test")).asNode().getStringValue();

        allTests.add(
            new CollectedTestCase(
                    new TestCase(testString, assertion, skipReason, environment, xmlVersion),
                    currentTestSet,
                    currentTestCase
            )
        );
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
                    return type + " " + value;
                }
                case "unicode-version": {
                    // 7.0,3.1.1,5.2,6.0,6.2 - We will need to look at the tests. I am not sure
                    // which
                    // unicode Java 8 supports. For now you can keep them all.
                    break;
                }
                case "unicode-normalization-form": {
                    // NFD,NFKD,NFKC,FULLY-NORMALIZED - We will need to play it by ear.
                    // LogDependency(testCaseName + type + " " + value);
                    // return;
                    break;
                }
                case "format-integer-sequence": {
                    // ⒈,Α,α - I am not sure what this is, I would need to see the tests.
                    return type + " " + value;
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
                    if (value.contains("1.0")) {
                        return type + " " + value;
                    }
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
                            || value.contains("staticTyping")
                            || value.contains("serialization"))
                    ) {
                        return type + " " + value;
                    }
                    break;
                }
                case "default-language": {
                    // fr-CA not supported - we just support en
                    if (!value.contains("en")) {
                        return type + " " + value;
                    }
                    break;
                }
                case "language": {
                    // xib,de,fr,it not supported - we just support en
                    if (!value.contains("en")) {
                        return type + " " + value;
                    }
                    break;
                }
                // Check if not the XSLT (isApplicable original method)
                case "spec": {
                    // XP30+,XQ10+,XQ30+,XQ31+,XP31+,XP31,XQ31,XP20,XQ10,XP20+ is ok, XT30+
                    // not
                    // if (!value.contains("XSLT") && !value.contains("XT")) {
                    if (!(value.contains("XQ") || value.contains("XP"))) {
                        return type + " " + value;
                    }
                    // Skip XP30, XQ30
                    for (String spec : value.trim().split("\\s+")) {
                        if ("XP30".equals(spec) || "XQ30".equals(spec)) {
                            return type + " " + value;
                        }
                    }

                    // We can think about adding this because some tests have two versions and we
                    // generally only try to
                    // support 3.1. But it removes a lot of tests so for now I think its overkill
                    // if (value.equals("XQ10+")) {
                    // return type + " " + value;
                    // }
                    break;
                }
                case "limit": {
                    // year_lt_0 - I am not sure I don't think we have this limit.
                    return type + " " + value;
                }
                default: {
                    System.out.println(
                        "WARNING: unconsidered dependency " + type + " in " + testCaseName + "; removing testcase"
                    );
                    return type + " " + value;
                }
            }
        }
        // all dependencies are okay

        // check if it uses module
        if (testCase.select(Steps.child("module")).exists()) {
            return "module requirement not implemented";
        }

        return null;
    }
}
