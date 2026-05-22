package iq.base;

import evaluation.*;
import evaluation.conversion.Converter;
import net.sf.saxon.s9api.XdmNode;
import org.junit.AssumptionViolatedException;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.exceptions.RumbleException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class TestBase {
    protected final TestCase testCase;
    protected final String testSetName;
    protected final String testCaseName;
    private final boolean useXQueryParser;
    /** The configuration for the Rumble runtimes spinned up for this test case. */
    private RumbleRuntimeConfiguration rumbleConfig;

    public TestBase(TestCase testCase, String testSetName, String testCaseName, boolean useXQueryParser) {
        this.testCase = testCase;
        this.testSetName = testSetName;
        this.testCaseName = testCaseName;
        this.useXQueryParser = useXQueryParser;
        this.rumbleConfig = new RumbleRuntimeConfiguration(
                useXQueryParser
                    ? new String[] {
                        "--output-format",
                        "json",
                        "--materialization-cap",
                        "1000000000",
                        "--default-language",
                        "xquery31" }
                    : new String[] {
                        "--output-format",
                        "json",
                        "--materialization-cap",
                        "1000000000",
                        "--default-language",
                        "jsoniq40" }
        );
    }

    public static Iterable<Object[]> getData(String testSuite) throws Exception {
        return getData(testSuite, false);
    }

    public static Iterable<Object[]> getData(String testSuite, boolean useXQueryParser) throws Exception {
        CaseCollector testDriver = new CaseCollector(useXQueryParser);
        testDriver.execute(testSuite);
        return testDriver.getAllTests();
    }

    public void testCase() {
        if (this.testCase.skipReason != null) {
            System.out.println("[[category|SKIP]]");
            assumeTrue(this.testCase.skipReason, false);
        }

        String testString = this.testCase.testString;
        System.out.println("[[originalTest|" + testString + "]]");

        XdmNode assertion = this.testCase.assertion;
        Environment environment = this.testCase.environment;
        applyXmlVersionDependencyToConfig();
        Rumble rumble = new Rumble(rumbleConfig);
        System.out.println("[[originalAssertion|" + assertion + "]]");
        try {
            if (checkAssertion(testString, assertion, rumble, environment)) {
                System.out.println("[[category|PASS]]");
            } else {
                System.out.println("VERYBAD");
            }
        } catch (RumbleException e) {
            if (isSkipErrorCode(e.getErrorCode().toString())) {
                System.out.println("[[category|SKIP]]");
                assumeTrue("Skip errorcode: " + e.getErrorCode().toString(), false);
            } else {
                System.out.println("[[category|ERROR]]");
                throw e;
            }
        } catch (AssertionError e) {
            System.out.println("[[category|FAIL]]");
            throw e;
        } catch (Exception e) {
            System.out.println("[[category|ERROR]]");
            throw e;
        }
    }

    private List<Item> runQuery(String query, Rumble rumble, Environment environment) {
        if (environment != null) {
            query = environment.applyToQuery(query);
        }

        if (!useXQueryParser) {
            query = Converter.convert(query);
        }

        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateList(resultAsList, 0);
        return resultAsList;
    }

    public boolean checkAssertion(
            String convertedTestString,
            XdmNode assertion,
            Rumble rumble,
            Environment environment
    ) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;
        QueryParts parts = null;

        switch (tag) {
            case "assert-empty":
                results = runQuery(convertedTestString, rumble, environment);
                assertTrue(results.isEmpty());
                break;
            case "assert":
                secondQuery = declareResultVariableFromTestExpression(
                        convertedTestString,
                        assertion.getStringValue()
                );
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "not":
                secondQuery = declareResultVariableFromTestExpression(
                        convertedTestString,
                        assertion.getStringValue()
                );
                assertFalseSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-eq":
                String assertionQuery = "(" + assertion.getStringValue() + ")";
                List<Item> testCaseResult = runQuery(convertedTestString, rumble, environment);
                List<Item> assertionResult = runQuery(assertionQuery, rumble, environment);

                assertEquals(testCaseResult, assertionResult);
                break;
            case "assert-deep-eq":
                parts = splitLeadingDeclarations(convertedTestString);
                secondQuery = parts.prolog
                        + "\ndeep-equal(("
                        + parts.body
                        + "), ("
                        + assertion.getStringValue()
                        + "))";
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-true":
                results = runQuery(convertedTestString, rumble, environment);
                assertTrueSingleElement(results);
                break;
            case "assert-false":
                results = runQuery(convertedTestString, rumble, environment);
                assertFalseSingleElement(results);
                break;
            case "assert-string-value":
                results = runQuery(convertedTestString, rumble, environment);
                String actual = results.stream().map(Item::serialize).collect(Collectors.joining(" "));

                String expected = assertion.getStringValue();

                boolean normalizeSpace = "true".equals(assertion.attribute("normalize-space"));

                if (normalizeSpace) {
                    actual = normalizeSpace(actual);
                    expected = normalizeSpace(expected);
                }

                assertEquals("Wrong string value", expected, actual);
                break;
            case "all-of":
                for (XdmNode individualAssertion : assertion.children("*")) {
                    applyXmlVersionDependencyToConfig();
                    Rumble subRumble = new Rumble(rumbleConfig);
                    checkAssertion(convertedTestString, individualAssertion, subRumble, environment);
                }
                break;
            case "any-of":
                boolean success = false;
                List<Throwable> errors = new ArrayList<>();
                for (XdmNode individualAssertion : assertion.children("*")) {
                    applyXmlVersionDependencyToConfig();
                    Rumble subRumble = new Rumble(rumbleConfig);
                    try {
                        checkAssertion(convertedTestString, individualAssertion, subRumble, environment);
                        success = true;
                    } catch (RumbleException e) {
                        if (isSkipErrorCode(e.getErrorCode().toString())) {
                            // we want these to be caught outside so we skip the testcase
                            throw e;
                        } else {
                            errors.add(e);
                        }
                    } catch (AssumptionViolatedException e) {
                        // specific assertion has skip reason, we want to pass that on and skip the whole test
                        throw e;
                    } catch (AssertionError | Exception e) {
                        // specific assertion has failed
                        errors.add(e);
                    }
                }
                System.out.println("[[ERRORS|" + errors + "]]");
                assertTrue("All assertions in any-of failed", success);
                break;
            case "assert-type":
                parts = splitLeadingDeclarations(convertedTestString);
                secondQuery = parts.prolog
                        + "\n("
                        + parts.body
                        + ") instance of "
                        + assertion.getStringValue();
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-count":
                results = runQuery(convertedTestString, rumble, environment);
                int count = Integer.parseInt(assertion.getStringValue());
                assertEquals("Wrong count", results.size(), count);
                break;
            case "assert-permutation":
                assertPermutation(convertedTestString, assertion, rumble, environment);
                break;
            case "error":
                try {
                    runQuery(convertedTestString, rumble, environment);
                    fail("Expected to throw error but ran without error");
                } catch (RumbleException re) {
                    if (isSkipErrorCode(re.getErrorCode().toString())) {
                        // we want these to be caught outside so we skip the testcase
                        throw re;
                    }

                    String expectedErrorCode = assertion.attribute("code");
                    if (expectedErrorCode.equals("*")) {
                        // any error code is fine, we just wanted to check that an error is thrown
                        return true;
                    }

                    assertEquals(
                        "Wrong error code",
                        expectedErrorCode,
                        re.getErrorCode().toString()
                    );
                }
                break;
            case "assert-xml":
                results = runQuery(convertedTestString, rumble, environment);
                String actualXml = "<assert-xml>"
                    + results.stream().map(Item::serialize).collect(Collectors.joining(""))
                    + "</assert-xml>";
                String expectedXml = "<assert-xml>" + assertion.getStringValue() + "</assert-xml>";

                Diff diff = DiffBuilder.compare(expectedXml)
                    .withTest(actualXml)
                    .ignoreWhitespace()
                    .build();

                assertFalse("Expected vs actual XML are different:\n" + diff.toString(), diff.hasDifferences());
                break;
            case "assert-serialization":
                String actualSerialization = serializeQueryResult(convertedTestString, rumble, environment);
                String expectedSerialization = assertion.getStringValue();
                assertEquals("Wrong serialization", expectedSerialization, actualSerialization);
                break;
            case "serialization-matches":
                String serializedResult = serializeQueryResult(convertedTestString, rumble, environment);
                String patternString = assertion.getStringValue();
                String flags = assertion.attribute("flags");

                boolean quote = flags != null && flags.contains("q");
                int patternFlags = 0;
                if (flags != null) {
                    if (flags.contains("i")) {
                        patternFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    }
                    if (flags.contains("m")) {
                        patternFlags |= Pattern.MULTILINE;
                    }
                    if (flags.contains("s")) {
                        patternFlags |= Pattern.DOTALL;
                    }
                    if (flags.contains("x")) {
                        patternFlags |= Pattern.COMMENTS;
                    }
                }
                if (quote) {
                    patternString = Pattern.quote(patternString);
                }

                Pattern regex = Pattern.compile(patternString, patternFlags);
                Matcher matcher = regex.matcher(serializedResult);
                assertTrue("Serialization does not match regex", matcher.find());
                break;
            case "assert-serialization-error":
                try {
                    runQuery(convertedTestString, rumble, environment);
                    fail("Expected to throw error but ran without error");
                } catch (RumbleException re) {
                    if (isSkipErrorCode(re.getErrorCode().toString())) {
                        // we want these to be caught outside so we skip the testcase
                        throw re;
                    }

                    assertEquals(
                        "Wrong error code",
                        assertion.attribute("code"),
                        re.getErrorCode().toString()
                    );
                }
                break;
            default:
                // should never happen unless they add a new assertion type
                System.out.println("[[category|SKIP]]");
                assumeTrue(tag + " assertion is new and not implemented", false);
                break;
        }
        return true;
    }

    private void assertTrueSingleElement(List<Item> results) {
        assertEquals("Not exactly one result", 1, results.size());
        assertTrue("Result is not boolean", results.get(0).isBoolean());
        assertTrue("Result is false", results.get(0).getBooleanValue());
    }

    private void assertFalseSingleElement(List<Item> results) {
        assertEquals("Not exactly one result", 1, results.size());
        assertTrue("Result is not boolean", results.get(0).isBoolean());
        assertFalse("Result is true", results.get(0).getBooleanValue());
    }

    /**
     * Runs the given query and returns the concatenated serialization of all items in the result.
     */
    private String serializeQueryResult(String convertedTestString, Rumble rumble, Environment environment) {
        List<Item> results = runQuery(convertedTestString, rumble, environment);
        return results.stream().map(Item::serialize).collect(Collectors.joining());
    }

    // TODO check this, I just took it over for now
    private void assertPermutation(
            String convertedTestString,
            XdmNode assertion,
            Rumble rumble,
            Environment environment
    ) {
        String assertExpression =
            "declare function allpermutations($sequence as item*) as array* {\n"
                + " if(count($sequence) le 1)\n"
                + " then\n"
                + "   [ $sequence ]\n"
                + " else\n"
                + "   for $i in 1 to count($sequence)\n"
                + "   let $first := $sequence[$i]\n"
                + "   let $others :=\n"
                + "     for $s in $sequence\n"
                + "     count $c\n"
                + "     where $c ne $i\n"
                + "     return $s\n"
                + "   for $recursive in allpermutations($others)\n"
                + "   return [ $first, $recursive[]]\n"
                + "};\n"
                + "\n"
                + "some $a in allpermutations("
                + convertedTestString
                + ")"
                + "satisfies deep-equal($a[], (("
                + assertion.getStringValue()
                + ")))";
        List<Item> results = runQuery(assertExpression, rumble, environment);
        assertTrueSingleElement(results);
    }

    /**
     * Returns true if the error code is a skip error code.
     * 
     * @param errorCode The error code to check.
     * @return True if the error code is a skip error code, false otherwise.
     */
    private boolean isSkipErrorCode(String errorCode) {
        // use the xQuery skip reason error codes if we are using the XQuery parser
        return (this.useXQueryParser ? Constants.xQuerySkipReasonErrorCodes : Constants.skipReasonErrorCodes).contains(
            errorCode
        );
    }


    private void applyXmlVersionDependencyToConfig() {
        // default fallback
        this.rumbleConfig.setXmlVersion("1.0");

        String v = this.testCase.xmlVersion;
        if (v != null)
            v = v.trim();

        if ("1.1".equals(v)) {
            this.rumbleConfig.setXmlVersion("1.1");
        } else if ("1.0".equals(v)) {
            this.rumbleConfig.setXmlVersion("1.0");
        }
    }

    private static String normalizeSpace(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }


    private static class QueryParts {
        final String prolog;
        final String body;

        QueryParts(String prolog, String body) {
            this.prolog = prolog;
            this.body = body;
        }
    }

    private static final Pattern LEADING_DECLARATION =
            Pattern.compile("\\G\\s*declare\\s+[^;]*;\\s*", Pattern.DOTALL);

    private static QueryParts splitLeadingDeclarations(String query) {
        Matcher matcher = LEADING_DECLARATION.matcher(query);

        int end = 0;
        while (matcher.find()) {
            end = matcher.end();
        }

        return new QueryParts(
                query.substring(0, end),
                query.substring(end)
        );
    }

    private static String declareResultVariableFromTestExpression(String query, String assertionExpression) {
        QueryParts parts = splitLeadingDeclarations(query);
        return parts.prolog
                + "\ndeclare variable $result := ("
                + parts.body
                + ");\n"
                + assertionExpression;
    }

}
