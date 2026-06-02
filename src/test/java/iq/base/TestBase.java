package iq.base;

import evaluation.*;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.BeforeAll;
import org.opentest4j.TestAbortedException;
import org.rumbledb.api.Item;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.exceptions.RumbleException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TestBase {
    private static final String[] XQUERY_RUNTIME_ARGUMENTS = {
        "--output-format",
        "json",
        "--materialization-cap",
        "1000000000",
        "--default-language",
        "xquery31"
    };

    private static final String[] JSONIQ_RUNTIME_ARGUMENTS = {
        "--output-format",
        "json",
        "--materialization-cap",
        "1000000000",
        "--default-language",
        "jsoniq40"
    };

    private final boolean useXQueryParser;

    protected TestBase() {
        this.useXQueryParser = useXQueryParserFromConfiguration();
    }

    @BeforeAll
    static void initializeSparkSession() {
        SparkTestSession.ensureInitialized();
    }

    public static List<CollectedTestCase> getData(String testSuite) throws Exception {
        return getData(testSuite, useXQueryParserFromConfiguration());
    }

    public static List<CollectedTestCase> getData(String testSuite, boolean useXQueryParser) throws Exception {
        CaseCollector testDriver = new CaseCollector(useXQueryParser);
        testDriver.execute(testSuite);
        return testDriver.getAllTests();
    }

    protected static boolean useXQueryParserFromConfiguration() {
        String configuredParser = System.getProperty("parser");
        if (configuredParser == null || configuredParser.isBlank()) {
            return false;
        }

        switch (configuredParser.toLowerCase()) {
            case "jsoniq":
                return false;
            case "xquery":
                return true;
            default:
                throw new IllegalArgumentException(
                        "Unsupported parser selection '"
                            + configuredParser
                            + "'. Use jsoniq or xquery."
                );
        }
    }

    protected void testCase(CollectedTestCase collectedTestCase) {
        TestCase testCase = collectedTestCase.testCase();
        if (testCase.skipReason != null) {
            assumeTrue(false, testCase.skipReason);
        }

        String testString = testCase.testString;

        XdmNode assertion = testCase.assertion;
        Environment environment = testCase.environment;
        try {
            checkAssertion(
                assertion,
                new AssertionContext(
                        testString,
                        environment,
                        useXQueryParser,
                        testCase.xmlVersion
                )
            );
        } catch (RumbleException e) {
            if (isSkipErrorCode(e.getErrorCode().toString())) {
                assumeTrue(false, "Skip errorcode: " + e.getErrorCode().toString());
            } else {
                throw e;
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    static RumbleRuntimeConfiguration createRumbleRuntimeConfiguration(boolean useXQueryParser) {
        String[] runtimeArguments = useXQueryParser
            ? XQUERY_RUNTIME_ARGUMENTS
            : JSONIQ_RUNTIME_ARGUMENTS;
        return new RumbleRuntimeConfiguration(runtimeArguments.clone());
    }

    private void checkAssertion(XdmNode assertion, AssertionContext context) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;

        switch (tag) {
            case "assert-empty":
                results = context.getPrimaryResult();
                assertTrue(results.isEmpty());
                break;
            case "assert":
                secondQuery = "declare variable $result := ("
                    + context.getTestString()
                    + "); "
                    + assertion.getStringValue();
                assertTrueSingleElement(context.runQuery(secondQuery));
                break;
            case "not":
                secondQuery = "declare variable $result := ("
                    + context.getTestString()
                    + "); "
                    + assertion.getStringValue();
                assertFalseSingleElement(context.runQuery(secondQuery));
                break;
            case "assert-eq":
                String assertionQuery = "(" + assertion.getStringValue() + ")";
                List<Item> testCaseResult = context.getPrimaryResult();
                List<Item> assertionResult = context.runQuery(assertionQuery);

                assertEquals(testCaseResult, assertionResult);
                break;
            case "assert-deep-eq":
                secondQuery = "deep-equal(("
                    + context.getTestString()
                    + "), ("
                    + assertion.getStringValue()
                    + "))";
                assertTrueSingleElement(context.runQuery(secondQuery));
                break;
            case "assert-true":
                results = context.getPrimaryResult();
                assertTrueSingleElement(results);
                break;
            case "assert-false":
                results = context.getPrimaryResult();
                assertFalseSingleElement(results);
                break;
            case "assert-string-value":
                results = context.getPrimaryResult();
                String actual = results.stream().map(Item::serialize).collect(Collectors.joining(" "));

                String expected = assertion.getStringValue();

                boolean normalizeSpace = "true".equals(assertion.attribute("normalize-space"));

                if (normalizeSpace) {
                    actual = normalizeSpace(actual);
                    expected = normalizeSpace(expected);
                }

                assertEquals(expected, actual, "Wrong string value");
                break;
            case "all-of":
                for (XdmNode individualAssertion : assertion.children("*")) {
                    checkAssertion(individualAssertion, context);
                }
                break;
            case "any-of":
                boolean success = false;
                List<Throwable> errors = new ArrayList<>();
                for (XdmNode individualAssertion : assertion.children("*")) {
                    try {
                        checkAssertion(individualAssertion, context);
                        success = true;
                    } catch (RumbleException e) {
                        if (isSkipErrorCode(e.getErrorCode().toString())) {
                            // we want these to be caught outside so we skip the testcase
                            throw e;
                        } else {
                            errors.add(e);
                        }
                    } catch (TestAbortedException e) {
                        // specific assertion has skip reason, we want to pass that on and skip the whole test
                        throw e;
                    } catch (AssertionError | Exception e) {
                        // specific assertion has failed
                        errors.add(e);
                    }
                }
                assertTrue(success, "All assertions in any-of failed");
                break;
            case "assert-type":
                secondQuery = "("
                    + context.getTestString()
                    + ") instance of "
                    + assertion.getStringValue();
                assertTrueSingleElement(context.runQuery(secondQuery));
                break;
            case "assert-count":
                results = context.getPrimaryResult();
                int count = Integer.parseInt(assertion.getStringValue());
                assertEquals(count, results.size(), "Wrong count");
                break;
            case "assert-permutation":
                assertPermutation(assertion, context);
                break;
            case "error":
                assertExpectedError(assertion, context.getPrimaryEvaluation());
                break;
            case "assert-xml":
                results = context.getPrimaryResult();
                String actualXml = "<assert-xml>"
                    + results.stream().map(Item::serialize).collect(Collectors.joining(""))
                    + "</assert-xml>";
                String expectedXml = "<assert-xml>" + assertion.getStringValue() + "</assert-xml>";

                Diff diff = DiffBuilder.compare(expectedXml)
                    .withTest(actualXml)
                    .ignoreWhitespace()
                    .build();

                assertFalse(diff.hasDifferences(), "Expected vs actual XML are different:\n" + diff.toString());
                break;
            case "assert-serialization":
                String actualSerialization = serializeQueryResult(context);
                String expectedSerialization = assertion.getStringValue();
                assertEquals(expectedSerialization, actualSerialization, "Wrong serialization");
                break;
            case "serialization-matches":
                String serializedResult = serializeQueryResult(context);
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
                assertTrue(matcher.find(), "Serialization does not match regex");
                break;
            case "assert-serialization-error":
                assertExpectedError(assertion, context.getPrimaryEvaluation());
                break;
            default:
                // should never happen unless they add a new assertion type
                assumeTrue(false, tag + " assertion is new and not implemented");
                break;
        }
    }

    private void assertExpectedError(XdmNode assertion, QueryEvaluation evaluation) {
        RumbleException error = evaluation.getError();
        if (error == null) {
            fail("Expected to throw error but ran without error");
        }

        if (isSkipErrorCode(error.getErrorCode().toString())) {
            // we want these to be caught outside so we skip the testcase
            throw error;
        }

        String expectedErrorCode = assertion.attribute("code");
        if (!expectedErrorCode.equals("*")) {
            assertEquals(expectedErrorCode, error.getErrorCode().toString(), "Wrong error code");
        }
    }

    private void assertTrueSingleElement(List<Item> results) {
        assertEquals(1, results.size(), "Not exactly one result");
        assertTrue(results.get(0).isBoolean(), "Result is not boolean");
        assertTrue(results.get(0).getBooleanValue(), "Result is false");
    }

    private void assertFalseSingleElement(List<Item> results) {
        assertEquals(1, results.size(), "Not exactly one result");
        assertTrue(results.get(0).isBoolean(), "Result is not boolean");
        assertFalse(results.get(0).getBooleanValue(), "Result is true");
    }

    /**
     * Runs the given query and returns the concatenated serialization of all items in the result.
     */
    private String serializeQueryResult(AssertionContext context) {
        return context.getPrimaryResult().stream().map(Item::serialize).collect(Collectors.joining());
    }

    // TODO check this, I just took it over for now
    private void assertPermutation(
            XdmNode assertion,
            AssertionContext context
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
                + context.getTestString()
                + ")"
                + "satisfies deep-equal($a[], (("
                + assertion.getStringValue()
                + ")))";
        List<Item> results = context.runQuery(assertExpression);
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

    private static String normalizeSpace(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

}
