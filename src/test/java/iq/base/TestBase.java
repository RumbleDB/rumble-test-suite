package iq.base;

import evaluation.*;
import net.sf.saxon.s9api.XdmNode;
import org.junit.AssumptionViolatedException;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.exceptions.RumbleException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class TestBase {
    protected final TestCase testCase;
    protected final String testSetName;
    protected final String testCaseName;
    private final boolean useXQueryParser;

    public TestBase(TestCase testCase, String testSetName, String testCaseName, boolean useXQueryParser) {
        this.testCase = testCase;
        this.testSetName = testSetName;
        this.testCaseName = testCaseName;
        this.useXQueryParser = useXQueryParser;
    }

    public static Iterable<Object[]> getData(String testSuite) throws Exception {
        CaseCollector testDriver = new CaseCollector();
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
        List<String> config = new ArrayList<>(
                List.of(
                    "--output-format",
                    "json",
                    "--materialization-cap",
                    "1000000000"
                )
        );
        if (useXQueryParser) {
            config.add("--default-language");
            config.add("xquery31");
        }
        Rumble rumble = new Rumble(
                new RumbleRuntimeConfiguration(config.toArray(new String[] {}))
        );
        System.out.println("[[originalAssertion|" + assertion + "]]");
        try {
            if (checkAssertion(testString, assertion, rumble, environment)) {
                System.out.println("[[category|PASS]]");
            } else {
                System.out.println("VERYBAD");
            }
        } catch (RumbleException e) {
            if (Constants.skipReasonErrorCodes.contains(e.getErrorCode())) {
                System.out.println("[[category|SKIP]]");
                assumeTrue("Skip errorcode: " + e.getErrorCode(), false);
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
        System.out.println("[[query|" + query + "]]");
        if (!useXQueryParser) {
            query = Converter.convert(query);
        }
        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateList(resultAsList, 1000000000000);
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
        switch (tag) {
            case "assert-empty":
                results = runQuery(convertedTestString, rumble, environment);
                assertTrue(results.isEmpty());
                break;
            case "assert":
                secondQuery = "declare variable $result := ("
                    + convertedTestString
                    + "); "
                    + assertion.getStringValue();
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "not":
                secondQuery = "declare variable $result := ("
                    + convertedTestString
                    + "); "
                    + assertion.getStringValue();
                assertFalseSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-eq":
                secondQuery = "("
                    + convertedTestString
                    + ") eq ("
                    + assertion.getStringValue()
                    + ")";
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-deep-eq":
                secondQuery = "deep-equal(("
                    + convertedTestString
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
                String serialized = results.stream().map(Item::serialize).collect(Collectors.joining(" "));
                assertEquals("Wrong string value", assertion.getStringValue(), serialized);
                break;
            case "all-of":
                for (XdmNode individualAssertion : assertion.children("*")) {
                    Rumble subRumble = new Rumble(
                            new RumbleRuntimeConfiguration(
                                    new String[] {
                                        "--output-format",
                                        "json"
                                    }
                            )
                    );
                    checkAssertion(convertedTestString, individualAssertion, subRumble, environment);
                }
                break;
            case "any-of":
                boolean success = false;
                List<Throwable> errors = new ArrayList<>();
                for (XdmNode individualAssertion : assertion.children("*")) {
                    Rumble subRumble = new Rumble(
                            new RumbleRuntimeConfiguration(
                                    new String[] {
                                        "--output-format",
                                        "json"
                                    }
                            )
                    );
                    try {
                        checkAssertion(convertedTestString, individualAssertion, subRumble, environment);
                        success = true;
                    } catch (RumbleException e) {
                        if (Constants.skipReasonErrorCodes.contains(e.getErrorCode())) {
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
                secondQuery = "("
                    + convertedTestString
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
                    if (Constants.skipReasonErrorCodes.contains(re.getErrorCode())) {
                        // we want these to be caught outside so we skip the testcase
                        throw re;
                    }

                    assertEquals(
                        "Wrong error code",
                        assertion.attribute("code"),
                        re.getErrorCode()
                    );
                }
                break;
            case "assert-serialization":
            case "serialization-matches":
            case "assert-serialization-error":
            case "assert-xml":
                System.out.println("[[category|SKIP]]");
                assumeTrue("assert-xml not implemented", false);
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
}
