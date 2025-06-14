package analytics;

/**
 * Runs XQuery queries using Saxon that pre-aggregate XML test reports into JSON files.
 */
public class Analytics extends BaseAnalytics {
    public static void main(String[] args) {
        String scope = args.length > 0 ? args[0] : "";
        new Analytics(scope).run();
    }

    public Analytics(String scope) {
        super("target/surefire-reports", "analytics-pre", "pre-aggregation", scope);
    }

    @Override
    protected String getLogPrefix() {
        return "Pre-aggregation";
    }
}
