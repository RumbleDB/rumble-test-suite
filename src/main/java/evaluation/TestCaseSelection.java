package evaluation;

public final class TestCaseSelection {
    private static final String TEST_CASE_PROPERTY = "test.case";

    private final String selectedTestCaseName;
    private int matchCount;

    private TestCaseSelection(String selectedTestCaseName) {
        this.selectedTestCaseName = selectedTestCaseName;
    }

    public static TestCaseSelection fromSystemProperties() {
        return new TestCaseSelection(readSelectedTestCaseName());
    }

    private static String readSelectedTestCaseName() {
        String configuredTestCase = System.getProperty(TEST_CASE_PROPERTY);
        if (configuredTestCase == null) {
            return null;
        }

        String trimmedTestCase = configuredTestCase.trim();
        if (trimmedTestCase.isEmpty()) {
            throw new IllegalArgumentException(
                    "System property '" + TEST_CASE_PROPERTY + "' must not be blank."
            );
        }
        return trimmedTestCase;
    }

    public boolean shouldRun(String testCaseName) {
        if (this.selectedTestCaseName == null) {
            return true;
        }
        if (!this.selectedTestCaseName.equals(testCaseName)) {
            return false;
        }

        this.matchCount++;
        if (this.matchCount > 1) {
            throw new DuplicateSelectedTestCaseException(this.selectedTestCaseName);
        }
        return true;
    }

    public void verifyResolved() {
        if (this.selectedTestCaseName != null && this.matchCount == 0) {
            throw new SelectedTestCaseNotFoundException(this.selectedTestCaseName);
        }
    }
}
