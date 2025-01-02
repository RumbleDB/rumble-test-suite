package driver;

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

public class TestDriver {
    private Path testsRepositoryDirectoryPath;
    private String currentTestSet;
    private final List<Object[]> allTests = new ArrayList<>();

    // For JSON-doc
    private final Map<String, String> URItoPathLookupTable = new HashMap<>();

    /**
     * method that collects all the testcases into a local variable allowing getAllTests() to be called later
     */
    public void execute(String testFolder) throws IOException, SaxonApiException, InterruptedException {
        getTestsRepository();
        processCatalog(testFolder);
    }

    /**
     * method that returns all collected testcases. execute() needs to be called beforehand
     */
    public List<Object[]> getAllTests() {
        return this.allTests;
    }

    /**
     * method that clones the git repository containing the tests and assigns testsRepositoryScriptFileName
     */
    public void getTestsRepository() throws IOException, InterruptedException {
        System.out.println("Running sh script to obtain the required tests repository!");

        String testsRepositoryScriptFileName = "get-tests-repository.sh";
        ProcessBuilder pb = new ProcessBuilder(
                Constants.WORKING_DIRECTORY_PATH.resolve(testsRepositoryScriptFileName).toString()
        );

        Process p = pb.start();
        final int exitValue = p.waitFor();

        if (exitValue == 0) {
            testsRepositoryDirectoryPath = Constants.WORKING_DIRECTORY_PATH.resolve("qt3tests");
            System.out.println("Tests repository obtained!");
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

        // testsets are defined with regex, allowing for example the split of fn into two
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
        URItoPathLookupTable.clear();

        String testSetFileName = testSetNode.attribute("file");
        this.currentTestSet = testSetFileName;
        File testSetFile = new File(testsRepositoryDirectoryPath.resolve(testSetFileName).toString());
        XdmNode testSetDocNode = catalogBuilder.build(testSetFile);

        prepareURIMapping(testSetDocNode, testSetFileName.split("/")[0]);

        for (XdmNode testCase : testSetDocNode.select(Steps.descendant("test-case")).asList()) {
            this.processTestCase(testCase, xpc);
        }
    }

    /**
     * method that prepares the mapping of URIs to local files for testcases
     */
    private void prepareURIMapping(XdmNode testSetDocNode, String bigTestSet) {
        // TODO right now this just creates one big mapping for all tests in set. It should be a mapping per environment
        List<XdmNode> environments = testSetDocNode.select(Steps.child("test-set"))
            .asNode()
            .select(Steps.child("environment"))
            .asList();
        for (XdmNode environment : environments) {
            List<XdmNode> resources = environment.select(Steps.descendant("resource")).asList();
            for (XdmNode resource : resources) {
                String file = testsRepositoryDirectoryPath.resolve(bigTestSet)
                    .resolve(resource.attribute("file"))
                    .toString();
                String uri = resource.attribute("uri");
                URItoPathLookupTable.put(uri, file);
            }
        }
    }


    private void processTestCase(XdmNode testCase, XPathCompiler xpc) throws SaxonApiException {
        String currentTestCase = testCase.attribute("name");

        // check if testcase is skipped
        if (
            Constants.skippedTestSets.contains(this.currentTestSet)
                || Constants.skippedTestCases.contains(currentTestCase)
        ) {
            allTests.add(
                new Object[] {
                    new TestCase(null, null, "testcase or testset on skiplist"),
                    currentTestSet,
                    currentTestCase }
            );
            return;
        }

        XdmNode testNode = testCase.select(Steps.child("test")).asNode();
        StringBuilder testString = new StringBuilder();

        // TODO: this is very incomplete. We only look for <param> and handle those, but the rest of the env is ignored
        // for now
        List<XdmNode> environments = testCase.select(Steps.child("environment")).asList();
        if (environments != null && !environments.isEmpty()) {
            XdmNode environment = environments.get(0);
            Iterator<XdmNode> externalVariables = environment.children("param").iterator();
            boolean modified = false;
            while (externalVariables.hasNext()) {
                XdmNode param = externalVariables.next();
                String name = param.attribute("name");
                String select = param.attribute("select");
                if (name != null && select != null) {
                    testString.append("let $").append(name).append(" := ").append(select).append(" ");
                    modified = true;
                }
            }
            if (modified)
                testString.append("return ");
        }

        testString.append(testNode.getStringValue());
        XdmNode assertion = (XdmNode) xpc.evaluateSingle("result/*[1]", testCase);

        String finalTestString = testString.toString();

        // converts testcases from URI to local path
        for (Map.Entry<String, String> fileLookup : URItoPathLookupTable.entrySet()) {
            if (finalTestString.contains(fileLookup.getKey())) {
                finalTestString = finalTestString.replace(fileLookup.getKey(), fileLookup.getValue());
            }
        }

        // check for dependencies and stop if we dont support it
        String caseDependency = checkDependencies(testCase);
        if (caseDependency != null)
            caseDependency = "DEPENDENCY " + caseDependency;

        allTests.add(
            new Object[] {
                new TestCase(finalTestString, assertion, caseDependency),
                currentTestSet,
                currentTestCase }
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
                    if (value.contains("1.0")) {
                        return (testCaseName + dependencyNode);
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

                    // We can think about adding this because some tests have two versions and we generally only try to
                    // support 3.1. But it removes a lot of tests so for now I think its overkill
                    // if (value.equals("XQ10+")) {
                    // return (testCaseName + dependencyNode);
                    // }
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

        // check if it uses module
        if (testCase.select(Steps.child("module")).exists()) {
            return "module requirement not implemented";
        }

        return null;
    }
}
