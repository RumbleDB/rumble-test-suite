package analytics;

/**
 * Runs XQuery queries using Saxon that perform final aggregation of pre-aggregated test reports into JSON files for
 * plotting.
 */
public class AnalyticsAggregation extends BaseAnalytics {
    public static void main(String[] args) {
        String scope = args.length > 0 ? args[0] : "";
        new AnalyticsAggregation(scope).run();
    }

    public AnalyticsAggregation(String scope) {
        super("analytics-pre", "analytics-results", "aggregation", scope);
    }

    @Override
    protected String getLogPrefix() {
        return "Final aggregation";
    }
}
