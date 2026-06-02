package evaluation;

public final class DuplicateSelectedTestCaseException extends RuntimeException {
    public DuplicateSelectedTestCaseException(String testCaseName) {
        super("Multiple test cases found with name '" + testCaseName + "'.");
    }
}
