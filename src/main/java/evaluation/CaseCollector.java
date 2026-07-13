package evaluation;

import net.sf.saxon.s9api.*;
import net.sf.saxon.s9api.streams.Steps;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CaseCollector {
    private static final Set<String> SUPPORTED_SPECS = Set.of(
        "XP20+",
        "XP30+",
        "XP31",
        "XP31+",
        "XQ10+",
        "XQ30+",
        "XQ31",
        "XQ31+"
    );
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
    public void execute(String testFolder) throws IOException, SaxonApiException, InterruptedException {
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

    /**
     * method that clones the git repository containing the tests and assigns
     * testsRepositoryScriptFileName
     */
    public void getTestsRepository() throws IOException, InterruptedException {
        String testsRepositoryScriptFileName = "get-tests-repository.sh";
        ProcessBuilder pb = new ProcessBuilder(
                Constants.WORKING_DIRECTORY_PATH.resolve(testsRepositoryScriptFileName).toString()
        );

        Process p = pb.start();
        final int exitValue = p.waitFor();

        if (exitValue == 0) {
            testsRepositoryDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve("qt3tests");
        } else {
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            StringBuilder result = new StringBuilder();
            while ((line = stderr.readLine()) != null) {
                result.append(line);
            }
            throw new IOException("Error with get-tests-repository.sh script" + result);
        }

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
                        new TestCase(null, null, "Testcase/set on skiplist", null, null, null, null),
                        currentTestSet,
                        currentTestCase
                )
            );
            return;
        }

        // the directory containing this test-set's own XML file; relative resource
        // hrefs in the test query must resolve against this
        Path testSetDirectory = testsRepositoryDirectoryPath.resolve(currentTestSet).getParent();
        String staticBaseUri = toDirectoryUri(testSetDirectory);

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
                        testSetDirectory
                );
            }
        }

        if (environment != null) {
            if (environment.isStaticBaseUriUndefined()) {
                // the test case requires that no static base URI is available
                staticBaseUri = null;
            } else if (environment.getStaticBaseUri() != null) {
                // the environment declares an explicit static base URI that overrides the testset-directory default
                staticBaseUri = environment.getStaticBaseUri();
            }
        }

        // check for possible skip reasons
        String skipReason = null;
        DependencyCheckResult dependencies = checkDependencies(testCase);
        if (dependencies.skipReason != null) {
            skipReason = "dependency " + dependencies.skipReason;
        }

        XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);
        String testString = testCase.select(Steps.child("test")).asNode().getStringValue();

        allTests.add(
            new CollectedTestCase(
                    new TestCase(
                            testString,
                            assertion,
                            skipReason,
                            environment,
                            dependencies.xmlVersion,
                            dependencies.defaultFormattingLanguage,
                            staticBaseUri
                    ),
                    currentTestSet,
                    currentTestCase
            )
        );
    }

    private static String toDirectoryUri(Path directory) {
        String uri = directory.toUri().toString();
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        return uri + ".";
    }

    /**
     * method that takes a testcase and returns dependency-derived skip and configuration information
     */
    private DependencyCheckResult checkDependencies(XdmNode testCase) {
        String testCaseName = testCase.attribute("name");
        List<XdmNode> dependencies = testCase.select(Steps.child("dependency")).asList();
        dependencies.addAll(testCase.getParent().select(Steps.child("dependency")).asList());

        DependencyCheckResult result = new DependencyCheckResult();
        if (dependencies.isEmpty()) {
            return result;
        }
        for (XdmNode dependencyNode : dependencies) {
            String type = dependencyNode.attribute("type");
            String value = dependencyNode.attribute("value");
            if (type == null || value == null) {
                throw new RuntimeException("Empty dependency encountered");
            }

            switch (type) {
                case "calendar": {
                    break;
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
                    result.skipReason = type + " " + value;
                    return result;
                }
                case "xml-version": {
                    if ("1.0".equals(value) || "1.1".equals(value)) {
                        result.xmlVersion = value;
                    }
                    break;
                }
                case "xsd-version": {
                    // Rumble doesn't care about schema, its XML specific
                    // TODO maybe it influences Saxon environment processor (check 221 in
                    // QT3TestDriverHE)
                    if (value.contains("1.0")) {
                        result.skipReason = type + " " + value;
                        return result;
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
                    // olson-timezone (partly supported by the datetime formatting builtins, unskip for now)
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
                            || value.contains("olson-timezone")
                            || value.contains("serialization"))
                    ) {
                        result.skipReason = type + " " + value;
                        return result;
                    }
                    break;
                }
                case "default-language": {
                    String satisfied = dependencyNode.attribute("satisfied");
                    if (!"false".equals(satisfied)) {
                        result.defaultFormattingLanguage = value;
                    }
                    break;
                }
                case "language": {
                    break;
                }
                // Check if not the XSLT (isApplicable original method)
                case "spec": {
                    if (!isSupportedSpecDependency(value)) {
                        result.skipReason = type + " " + value;
                        return result;
                    }
                    break;
                }
                case "limit": {
                    // year_lt_0 - I am not sure I don't think we have this limit.
                    result.skipReason = type + " " + value;
                    return result;
                }
                default: {
                    System.out.println(
                        "WARNING: unconsidered dependency " + type + " in " + testCaseName + "; removing testcase"
                    );
                    result.skipReason = type + " " + value;
                    return result;
                }
            }
        }
        // all dependencies are okay

        // check if it uses module
        if (testCase.select(Steps.child("module")).exists()) {
            result.skipReason = "module requirement not implemented";
        }

        return result;
    }

    static boolean isSupportedSpecDependency(String value) {
        List<String> specs = Arrays.asList(value.trim().split("\\s+"));
        return specs.stream().anyMatch(SUPPORTED_SPECS::contains);
    }

    private static final class DependencyCheckResult {
        private String skipReason;
        private String xmlVersion;
        private String defaultFormattingLanguage;
    }
}
