package evaluation;

public record CollectedTestCase(TestCase testCase, String testSetName, String testCaseName) {
    @Override
    public String toString() {
        return this.testSetName + ":" + this.testCaseName;
    }
}
