package evaluation;

public final class SelectedTestCaseNotFoundException extends RuntimeException {
    public SelectedTestCaseNotFoundException(String testCaseName) {
        super("No test case found with name '" + testCaseName + "'.");
    }
}
