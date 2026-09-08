package evaluation;

public final class TestCaseSelection {
    private static final String TEST_CASE_PROPERTY = "test.case";

    private final String selectedTestCaseName;
    private int matchCount;

    TestCaseSelection(String selectedTestCaseName) {
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
            throw new IllegalArgumentException("System property '" + TEST_CASE_PROPERTY + "' must not be blank.");
        }
        return trimmedTestCase;
    }

    public boolean isSpecificCaseSelected() {
        return this.selectedTestCaseName != null;
    }

    public boolean shouldRun(String testCaseName) {
        return this.selectedTestCaseName == null || this.selectedTestCaseName.equals(testCaseName);
    }

    public void observeCatalogCase(String testCaseName) {
        if (this.selectedTestCaseName == null || !this.selectedTestCaseName.equals(testCaseName)) {
            return;
        }
        this.matchCount++;
        if (this.matchCount > 1) {
            throw new DuplicateSelectedTestCaseException(this.selectedTestCaseName);
        }
    }

    public void verifyResolved() {
        if (this.selectedTestCaseName != null && this.matchCount == 0) {
            throw new SelectedTestCaseNotFoundException(this.selectedTestCaseName);
        }
    }
}
