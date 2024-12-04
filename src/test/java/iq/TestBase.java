package iq;

import driver.TestCase;
import driver.TestDriver;
import net.sf.saxon.s9api.XdmNode;
import org.rumbledb.api.Item;
import org.rumbledb.api.Rumble;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.config.RumbleRuntimeConfiguration;
import org.rumbledb.context.Name;
import org.rumbledb.exceptions.RumbleException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

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
        String convertedTestString = this.testCase.convertedTestString;
        XdmNode assertion = this.testCase.assertion;
        Rumble rumble = new Rumble(
                new RumbleRuntimeConfiguration(
                        new String[] {
                            "--output-format",
                            "json"
                        }
                )
        );
        checkAssertion(convertedTestString, assertion, rumble);
    }

    private List<Item> runQuery(String query, Rumble rumble) {
        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
        return resultAsList;
    }

    private List<Item> runNestedQuery(List<Item> resultAsList, String query, Rumble rumble) {
        RumbleRuntimeConfiguration configuration = new RumbleRuntimeConfiguration(
                new String[] {
                    "--output-format",
                    "json"
                }
        );
        configuration.setExternalVariableValue(
            Name.createVariableInNoNamespace("result"),
            resultAsList
        );
        String assertExpression = "declare variable $result external;" + query;
        Rumble rumbleInstance = new Rumble(configuration);
        return runQuery(assertExpression, rumbleInstance);
    }

    public void checkAssertion(String convertedTestString, XdmNode assertion, Rumble rumble) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;
        switch (tag) {
            case "assert-empty":
                results = runQuery(convertedTestString, rumble);
                assertTrue(results.isEmpty());
            case "assert":
                secondQuery = "declare variable $result := ("+convertedTestString+"); "+assertion.getStringValue();
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "not":
                secondQuery = "declare variable $result := ("+convertedTestString+"); "+assertion.getStringValue();
                assertFalseSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-eq":
                secondQuery = "(" + convertedTestString + ") eq (" + assertion.getStringValue() + ")";
                assertTrueSingleElement(runQuery(secondQuery, rumble));
                break;
            case "assert-deep-eq":
                secondQuery = "deep-equal((" + convertedTestString + "), (" + assertion.getStringValue() + "))";
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
                assertEquals("not exactly one result", 1, results.size());
                assertEquals("wrong string value", assertion.getStringValue(), results.get(0).getStringValue());
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
                fail("any-of not yet implemented");
                break;
            case "assert-type":
                secondQuery = "(" + convertedTestString + ") instance of " + assertion.getStringValue();
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
                    assertEquals(
                        "correctly threw error but with wrong error code",
                        assertion.attribute("code"),
                        re.getErrorCode()
                    );
                } catch (Exception e) {
                    fail("non-rumble exception encountered");
                }
                break;
            default:
                fail("unhandled assertion case");
        }
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
                + assertion.getStringValue()
                + ")))";
        List<Item> results = runQuery(assertExpression, rumble);
        assertTrueSingleElement(results);
    }
}
