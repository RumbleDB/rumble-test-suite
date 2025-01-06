package iq.base;

import driver.*;
import net.sf.saxon.s9api.XdmNode;
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

    public TestBase(TestCase testCase, String testSetName, String testCaseName) {
        this.testCase = testCase;
        this.testSetName = testSetName;
        this.testCaseName = testCaseName;
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
        convertedTestString = Converter.convert(testString);

        XdmNode assertion = this.testCase.assertion;
        Rumble rumble = new Rumble(
                new RumbleRuntimeConfiguration(
                        new String[] {
                            "--output-format",
                            "json"
                        }
                )
        );
        System.out.println("[[originalAssertion|" + assertion + "]]");
        try {
            if (checkAssertion(convertedTestString, assertion, rumble)) {
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
            if (Constants.skipErrorCodes.contains(e.getErrorCode())) {
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

    private List<Item> runQuery(String query, Rumble rumble) {
        System.out.println("[[query|" + query + "]]");
        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
        return resultAsList;
    }

    public boolean checkAssertion(String convertedTestString, XdmNode assertion, Rumble rumble) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;
        switch (tag) {
            case "assert-empty":
                results = runQuery(convertedTestString, rumble);
                assertTrue(results.isEmpty());
                break;
            case "assert":
                secondQuery = "declare variable $result := ("
                    + convertedTestString
                    + "); "
                    + Converter.convert(assertion.getStringValue());
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "not":
                secondQuery = "declare variable $result := ("
                    + convertedTestString
                    + "); "
                    + Converter.convert(assertion.getStringValue());
                assertFalseSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-eq":
                secondQuery = "("
                    + convertedTestString
                    + ") eq ("
                    + Converter.convert(assertion.getStringValue())
                    + ")";
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-deep-eq":
                secondQuery = "deep-equal(("
                    + convertedTestString
                    + "), ("
                    + Converter.convert(assertion.getStringValue())
                    + "))";
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-true":
                results = runQuery(convertedTestString, rumble);
                assertTrueSingleElement(results);
                break;
            case "assert-false":
                results = runQuery(convertedTestString, rumble);
                assertFalseSingleElement(results);
                break;
            case "assert-string-value":
                results = runQuery(convertedTestString, rumble);
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
                    checkAssertion(convertedTestString, individualAssertion, subRumble);
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
                        checkAssertion(convertedTestString, individualAssertion, subRumble);
                        success = true;
                    } catch (RumbleException e) {
                        if (Constants.skipErrorCodes.contains(e.getErrorCode())) {
                            // we want these to be caught outside so we skip the testcase
                            throw e;
                        } else {
                            errors.add(e);
                        }
                    } catch (AssertionError | Exception e) {
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
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-count":
                results = runQuery(convertedTestString, rumble);
                int count = Integer.parseInt(assertion.getStringValue());
                assertEquals("wrong count", results.size(), count);
                break;
            case "assert-permutation":
                assertPermutation(convertedTestString, assertion, rumble);
                break;
            case "error":
                try {
                    runQuery(convertedTestString, rumble);
                    fail("expected to throw error but ran without error");
                } catch (RumbleException re) {
                    if (!Constants.supportedErrorCodes.contains(re.getErrorCode())) {
                        System.out.println("[[category|SKIP]]");
                        assumeTrue("unsupported errorcode: " + re.getErrorCode(), false);
                    }
                    if (Constants.skipErrorCodes.contains(re.getErrorCode())) {
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

    private void assertPermutation(String convertedTestString, XdmNode assertion, Rumble rumble) {
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
        List<Item> results = runQuery(assertExpression, rumble);
        assertTrueSingleElement(results);
    }
}
