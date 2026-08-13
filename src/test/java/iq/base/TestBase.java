package iq.base;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;

import evaluation.*;
import evaluation.conversion.XQueryMainModuleRewriter;
import net.sf.saxon.s9api.XdmNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.rumbledb.api.Item;
import org.rumbledb.config.RumbleConfiguration;
import org.rumbledb.exceptions.RumbleException;

public class TestBase {
    private static final String PERMUTATION_ASSERTION_QUERY =
            """
                let $actual := (
                %s
                )
                let $expected := (
                %s
                )
                return count($actual) eq count($expected)
                  and (
                    every $item in $actual
                    satisfies count(
                      for $candidate in $actual
                      where deep-equal($candidate, $item)
                      return $candidate
                    ) eq count(
                      for $candidate in $expected
                      where deep-equal($candidate, $item)
                      return $candidate
                    )
                  )
                """;

    private final boolean useXQueryParser;
    /** The configuration for the Rumble runtimes spinned up for this test case. */
    private final RumbleConfiguration rumbleConfig;

    protected TestBase() {
        this.useXQueryParser = useXQueryParserFromConfiguration();
        this.rumbleConfig = RumbleConfiguration.builder()
                .configureOutput(o -> o.outputFormat("json"))
                .configureRuntime(r -> r.materializationCap(1000000000))
                .configureSemantics(s -> s.queryLanguage(this.useXQueryParser ? "xquery31" : "jsoniq40"))
                .build();
    }

    public static List<CollectedTestCase> getData(String testSuite) throws Exception {
        return getData(testSuite, useXQueryParserFromConfiguration());
    }

    public static List<CollectedTestCase> getData(String testSuite, boolean useXQueryParser) throws Exception {
        CaseCollector testDriver =
                new CaseCollector(useXQueryParserFromConfiguration(), TestCaseSelection.fromSystemProperties());
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
                        "Unsupported parser selection '" + configuredParser + "'. Use jsoniq or xquery.");
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
        checkAssertion(
                assertion,
                new AssertionContext(
                        testString,
                        environment,
                        useXQueryParser,
                        this.rumbleConfig,
                        testCase.xmlVersion,
                        testCase.defaultFormattingLanguage,
                        testCase.staticTyping,
                        testCase.staticBaseUri),
                testCase.testSetDirectory);
    }

    private void checkAssertion(XdmNode assertion, AssertionContext context, Path testSetDirectory) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;

        switch (tag) {
            case "assert-empty":
                results = context.getPrimaryResult();
                assertTrue(results.isEmpty());
                break;
            case "assert":
                secondQuery =
                        declareResultVariableFromTestExpression(context.getTestString(), assertion.getStringValue());
                assertTrueSingleElement(context.runQuery(secondQuery));
                break;
            case "not":
                List<XdmNode> nestedAssertions = new ArrayList<>();
                for (XdmNode nestedAssertion : assertion.children("*")) {
                    nestedAssertions.add(nestedAssertion);
                }
                if (!nestedAssertions.isEmpty()) {
                    assertEquals(1, nestedAssertions.size(), "not assertion must contain exactly one nested assertion");
                    try {
                        checkAssertion(nestedAssertions.get(0), context, testSetDirectory);
                        fail("Nested assertion inside not succeeded");
                    } catch (AssertionError e) {
                        // Expected: the nested assertion should fail.
                    }
                } else {
                    secondQuery = declareResultVariableFromTestExpression(
                            context.getTestString(), assertion.getStringValue());
                    assertFalseSingleElement(context.runQuery(secondQuery));
                }
                break;
            case "assert-eq":
                secondQuery = XQueryMainModuleRewriter.rewriteProgram(
                        context.getTestString(),
                        program -> "((" + program + ") eq (" + assertion.getStringValue() + "))");
                assertTrueSingleElement(context.runQuery(secondQuery));
                break;
            case "assert-deep-eq":
                secondQuery = XQueryMainModuleRewriter.rewriteProgram(
                        context.getTestString(),
                        program -> "deep-equal((" + program + "), (" + assertion.getStringValue() + "))");
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
                String actual = results.stream().map(Item::getStringValue).collect(Collectors.joining(" "));

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
                    checkAssertion(individualAssertion, context, testSetDirectory);
                }
                break;
            case "any-of":
                boolean success = false;
                List<Throwable> errors = new ArrayList<>();
                for (XdmNode individualAssertion : assertion.children("*")) {
                    try {
                        checkAssertion(individualAssertion, context, testSetDirectory);
                        success = true;
                    } catch (UnsupportedOperationException e) {
                        // A harness limitation is an error, not a failed alternative assertion.
                        throw e;
                    } catch (AssertionError | Exception e) {
                        // specific assertion has failed
                        errors.add(e);
                    }
                }
                assertTrue(success, "All assertions in any-of failed");
                break;
            case "assert-type":
                secondQuery = XQueryMainModuleRewriter.rewriteProgram(
                        context.getTestString(),
                        program -> "(" + program + ") instance of " + assertion.getStringValue());
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
                String expectedXml = "<assert-xml>" + assertionText(assertion, testSetDirectory) + "</assert-xml>";

                DiffBuilder diffBuilder =
                        DiffBuilder.compare(expectedXml).withTest(actualXml).ignoreWhitespace();
                if ("true".equals(assertion.attribute("ignore-prefixes"))) {
                    diffBuilder.withDifferenceEvaluator((comparison, outcome) ->
                            comparison.getType() == ComparisonType.NAMESPACE_PREFIX ? ComparisonResult.EQUAL : outcome);
                }
                Diff diff = diffBuilder.build();

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
                throw new UnsupportedOperationException(tag + " assertion is not implemented by the test harness");
        }
    }

    static String assertionText(XdmNode assertion, Path testSetDirectory) {
        String file = assertion.attribute("file");
        if (file == null) {
            return assertion.getStringValue();
        }
        Path filePath = testSetDirectory.resolve(file);
        try {
            return Files.readString(filePath);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read expected assertion file: " + filePath, exception);
        }
    }

    private void assertExpectedError(XdmNode assertion, QueryEvaluation evaluation) {
        RumbleException error = evaluation.getError();
        if (error == null && assertion.getNodeName().getLocalName().equals("assert-serialization-error")) {
            try {
                evaluation.getSerializedResult();
                fail("Expected to throw error but ran without error");
            } catch (RumbleException serializationError) {
                error = serializationError;
            }
        }
        if (error == null) {
            fail("Expected to throw error but ran without error");
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
     * Runs the given query and returns the concatenated serialization of all items
     * in the result.
     */
    private String serializeQueryResult(AssertionContext context) {
        return context.getPrimarySerialization();
    }

    private void assertPermutation(XdmNode assertion, AssertionContext context) {
        String assertExpression = XQueryMainModuleRewriter.rewriteProgram(
                context.getTestString(),
                program -> PERMUTATION_ASSERTION_QUERY.formatted(program, assertion.getStringValue()));
        List<Item> results = context.runQuery(assertExpression);
        assertTrueSingleElement(results);
    }

    private static String normalizeSpace(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String declareResultVariableFromTestExpression(String query, String assertionExpression) {
        return XQueryMainModuleRewriter.rewriteProgram(
                query,
                program -> "declare variable $result := (" + program + ");\nboolean(" + assertionExpression + ")");
    }
}
