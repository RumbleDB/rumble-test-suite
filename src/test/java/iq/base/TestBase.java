package iq.base;

import driver.*;
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
        TestDriver testDriver = new TestDriver();
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

        String convertedTestString;
        if (!useXQueryParser) {
            convertedTestString = Converter.convert(testString);
        } else {
            convertedTestString = testString;
        }

        XdmNode assertion = this.testCase.assertion;
        Environment environment = this.testCase.environment;
        Rumble rumble = new Rumble(
                new RumbleRuntimeConfiguration(
                        new String[] {
                            "--output-format",
                            "json",
                            "--materialization-cap",
                            "1000000000"
                        }
                )
        );
        System.out.println("[[originalAssertion|" + assertion + "]]");
        try {
            if (checkAssertion(convertedTestString, assertion, rumble, environment)) {
                // TODO we only check for conversion of test, if we convert only assertion then it is logged as PASS
                // instead of MANAGED
                if (convertedTestString.equals(testString))
                    System.out.println("[[category|PASS]]");
                else
                    System.out.println("[[category|MANAGED]]");
            } else {
                System.out.println("VERYBAD");
            }
        } catch (RumbleException e) {
            if (Constants.skipReasonErrorCodes.contains(e.getErrorCode())) {
                System.out.println("[[category|SKIP]]");
                assumeTrue(e.toString(), false);
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
        if (environment != null)
            query = environment.applyToQuery(query);
        if (this.useXQueryParser) {
            if (!query.startsWith("xquery version")) {
                query = "xquery version \"3.1\"; " + query;
            }
        }
        System.out.println("[[query|" + query + "]]");
        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
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
                    + Converter.convert(assertion.getStringValue());
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "not":
                secondQuery = "declare variable $result := ("
                    + convertedTestString
                    + "); "
                    + Converter.convert(assertion.getStringValue());
                assertFalseSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-eq":
                secondQuery = "("
                    + convertedTestString
                    + ") eq ("
                    + Converter.convert(assertion.getStringValue())
                    + ")";
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-deep-eq":
                secondQuery = "deep-equal(("
                    + convertedTestString
                    + "), ("
                    + Converter.convert(assertion.getStringValue())
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
                assertEquals("wrong string value", assertion.getStringValue(), serialized);
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
                assertTrue("all assertions in any-of failed: " + errors, success);
                break;
            case "assert-type":
                secondQuery = "("
                    + convertedTestString
                    + ") instance of "
                    + Converter.convert(assertion.getStringValue());
                assertTrueSingleElement(runQuery(secondQuery, rumble, environment));
                break;
            case "assert-count":
                results = runQuery(convertedTestString, rumble, environment);
                int count = Integer.parseInt(assertion.getStringValue());
                assertEquals("wrong count", results.size(), count);
                break;
            case "assert-permutation":
                assertPermutation(convertedTestString, assertion, rumble, environment);
                break;
            case "error":
                try {
                    runQuery(convertedTestString, rumble, environment);
                    fail("expected to throw error but ran without error");
                } catch (RumbleException re) {
                    if (!Constants.supportedErrorCodes.contains(re.getErrorCode())) {
                        System.out.println("[[category|SKIP]]");
                        assumeTrue("unsupported errorcode: " + re.getErrorCode(), false);
                    }
                    if (Constants.skipReasonErrorCodes.contains(re.getErrorCode())) {
                        // we want these to be caught outside so we skip the testcase
                        throw re;
                    }

                    assertEquals(
                        "correctly threw error but with wrong error code",
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
                assumeTrue(tag + " assertion not implemented", false);
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
        assertEquals("not exactly one result", 1, results.size());
        assertTrue("result is not boolean", results.get(0).isBoolean());
        assertTrue("result is false", results.get(0).getBooleanValue());
    }

    private void assertFalseSingleElement(List<Item> results) {
        assertEquals("not exactly one result", 1, results.size());
        assertTrue("result is not boolean", results.get(0).isBoolean());
        assertFalse("result is true", results.get(0).getBooleanValue());
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
                + Converter.convert(assertion.getStringValue())
                + ")))";
        List<Item> results = runQuery(assertExpression, rumble, environment);
        assertTrueSingleElement(results);
    }
}
