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

        List<Item> resultAsList = runQuery(convertedTestString);
        checkAssertion(convertedTestString, assertion);
    }

    private List<Item> runQuery(String query) {
        Rumble rumble = new Rumble(
                new RumbleRuntimeConfiguration(
                        new String[] {
                            "--output-format",
                            "json"
                        }
                )
        );
        return runQuery(query, rumble);
    }

    private List<Item> runQuery(String query, Rumble rumble) {
        SequenceOfItems queryResult = rumble.runQuery(query);
        List<Item> resultAsList = new ArrayList<>();
        queryResult.populateListWithWarningOnlyIfCapReached(resultAsList);
        return resultAsList;
    }

    private List<Item> runNestedQuery(List<Item> resultAsList, String query) {
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

    public void checkAssertion(String convertedTestString, XdmNode assertion) {
        String tag = assertion.getNodeName().getLocalName();
        String secondQuery;
        List<Item> results;
        switch (tag) {
            case "assert-empty":
                results = runQuery(convertedTestString);
                assertTrue(results.isEmpty());
            case "assert":
                fail("assert not yet implemented");
                break;
            case "assert-eq":
                results = runQuery(convertedTestString);
                secondQuery = "$result eq " + assertion.getStringValue();
                assertTrueSingleElement(runNestedQuery(results, secondQuery));
                break;
            case "assert-deep-eq":
                results = runQuery(convertedTestString);
                secondQuery = "deep-equal((" + assertion.getStringValue() + "),$result)";
                List<Item> newRes = runNestedQuery(results, secondQuery);
                assertTrueSingleElement(newRes);
                break;
            case "assert-true":
                results = runQuery(convertedTestString);
                assertTrueSingleElement(results);
                break;
            case "assert-false":
                results = runQuery(convertedTestString);
                assertEquals("not exactly one result", 1, results.size());
                assertTrue("result is not boolean", results.get(0).isBoolean());
                assertFalse("result is not false", results.get(0).getBooleanValue());
                break;
            case "assert-string-value":
                results = runQuery(convertedTestString);
                assertEquals("not exactly one result", 1, results.size());
                assertEquals("wrong string value", assertion.getStringValue(), results.get(0).getStringValue());
                break;
            case "all-of":
                fail("all-of not yet implemented");
                break;
            // return CustomAssertAllOf(resultAsList, assertion);
            case "any-of":
                fail("any-of not yet implemented");
                break;
            // return CustomAssertAnyOf(resultAsList, assertion);
            case "assert-type":
                fail("assert-type not yet implemented");
                break;
            // return CustomAssertType(resultAsList, assertion);
            case "assert-count":
                results = runQuery(convertedTestString);
                int count = Integer.parseInt(assertion.getStringValue());
                assertEquals("wrong count", results.size(), count);
                break;
            case "not":
                fail("not not yet implemented");
                break;
            // break CustomAssertNot(resultAsList, assertion);
            case "assert-permutation":
                fail("assert-permutation not yet implemented");
                break;
            // break CustomAssertPermutation(resultAsList, assertion);
            case "error":
                try {
                    runQuery(convertedTestString);
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
            case "assert-serialization-error":
                // TODO
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
}
