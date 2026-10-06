import { For, Show } from "solid-js";
import { PassRateGauge, SuitesBarChart, IssueDistributionChart } from "./DashboardCharts";
import { GapBar } from "./FeatureGapsTab";
import { formatPercent, formatDuration, statusSegments } from "../lib/analysis";
import type { ViewModel, StatusFilter, SkipCategory, StatusSegment } from "../lib/analysis";

type OverviewTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  onSelectSuite: (name: string) => void;
  onSelectIssue: (key: string) => void;
  onSelectStatus: (status: StatusFilter) => void;
  onSelectGap: (category: SkipCategory | "ALL", key: string | null) => void;
  onViewAllChanges: () => void;
};

export function OverviewTab(props: OverviewTabProps) {
  const openProblems = () => props.viewModel.totals.fail + props.viewModel.totals.error;
  const skipCounts = () => props.viewModel.totals.skipCategories;
  const notApplicable = () => skipCounts()["not-applicable"] + skipCounts()["other-spec"];
  const segments = () => statusSegments(props.viewModel.totals).filter((segment) => segment.count > 0);
  const missingFeatures = () =>
    props.viewModel.dependencyRows.filter((row) => row.category === "missing-feature");
  const topMissingFeatures = () => missingFeatures().slice(0, 6);

  // Passing tests have no detail view; failures and errors open their groups, skips their feature gaps.
  const selectSegment = (segment: StatusSegment) => {
    if (segment.key === "FAIL" || segment.key === "ERROR") {
      props.onSelectStatus(segment.key);
    } else if (segment.key !== "PASS") {
      props.onSelectGap(segment.key, null);
    }
  };

  return (
    <div class="tab-content-animate" style={{ display: "flex", "flex-direction": "column", gap: "24px" }}>
      {/* STAT CARDS IN OVERVIEW */}
      <section class="stat-grid">
        <div class="panel stat-card stat-card-total">
          <span class="stat-label">Total Tests</span>
          <strong class="stat-value">{props.viewModel.totals.total}</strong>
          <span class="stat-hint">
            {props.viewModel.totals.pass} passing · {formatDuration(props.viewModel.totals.time)} runtime
          </span>
        </div>
        <div class="panel stat-card stat-card-pass">
          <span class="stat-label">Pass Rate</span>
          <strong
            class="stat-value"
            style={{ color: props.viewModel.totals.passRate > 85 ? "var(--pass)" : "var(--fail)" }}
          >
            {formatPercent(props.viewModel.totals.passRate)}
          </strong>
          <span class="stat-hint" title="Passing share of all tests except not-applicable and other-spec skips">
            {formatPercent(props.viewModel.totals.applicablePassRate)} of applicable tests
          </span>
        </div>
        <div class="panel stat-card stat-card-fail">
          <span class="stat-label">Failures</span>
          <strong
            class="stat-value"
            style={{ color: props.viewModel.totals.fail > 0 ? "var(--fail)" : "var(--muted)" }}
          >
            {props.viewModel.totals.fail}
          </strong>
          <span class="stat-hint">Unmet assertions</span>
        </div>
        <div class="panel stat-card stat-card-error">
          <span class="stat-label">Errors</span>
          <strong
            class="stat-value"
            style={{ color: props.viewModel.totals.error > 0 ? "var(--error)" : "var(--muted)" }}
          >
            {props.viewModel.totals.error}
          </strong>
          <span class="stat-hint">Exceptions occurred</span>
        </div>
        <div
          class="panel stat-card stat-card-missing"
          style={{ cursor: "pointer" }}
          onClick={() => props.onSelectGap("missing-feature", null)}
          title="Skipped because RumbleDB does not implement a required feature"
        >
          <span class="stat-label">Missing Features</span>
          <strong class="stat-value" style={{ color: "var(--skip-missing-ink)" }}>
            {skipCounts()["missing-feature"]}
          </strong>
          <span class="stat-hint">skipped tests · {missingFeatures().length} features</span>
        </div>
        <div
          class="panel stat-card stat-card-na"
          style={{ cursor: "pointer" }}
          onClick={() => props.onSelectGap("ALL", null)}
          title="Skipped because the test does not apply to RumbleDB"
        >
          <span class="stat-label">Not Applicable</span>
          <strong class="stat-value" style={{ color: "var(--muted)" }}>{notApplicable()}</strong>
          <span class="stat-hint">
            {skipCounts()["not-applicable"]} negative/alternative · {skipCounts()["other-spec"]} other spec
          </span>
        </div>
        <Show when={skipCounts().unclassified > 0}>
          <div class="panel stat-card stat-card-skip">
            <span class="stat-label">Unclassified Skips</span>
            <strong class="stat-value" style={{ color: "var(--skip)" }}>{skipCounts().unclassified}</strong>
            <span class="stat-hint">Skip reason not structured</span>
          </div>
        </Show>
      </section>

      <div class="dashboard-grid">
        <div class="column">
          {/* Left: Overall Health & Status Split */}
          <section class="panel">
            <div class="section-header">
              <div>
                <h2>Overall Health</h2>
                <p class="section-subtitle">Pass/fail composition of the entire test suite</p>
              </div>
            </div>

            <div class="status-bar-wrapper">
              <PassRateGauge
                passRate={props.viewModel.totals.passRate}
                total={props.viewModel.totals.total}
                pass={props.viewModel.totals.pass}
              />

              <div class="status-bar-bar">
                <For each={segments()}>
                  {(segment) => (
                    <div
                      class="status-bar-segment"
                      style={{ width: `${(segment.count / Math.max(props.viewModel.totals.total, 1)) * 100}%`, background: segment.color }}
                      title={`${segment.label}: ${segment.count}`}
                    />
                  )}
                </For>
              </div>

              <div class="chart-legend-grid">
                <For each={segments()}>
                  {(segment) => (
                    <div
                      class="chart-legend-item"
                      style={{ cursor: segment.key === "PASS" ? "default" : "pointer" }}
                      onClick={() => selectSegment(segment)}
                    >
                      <span class="legend-dot" style={{ background: segment.color }} />
                      <span>
                        {segment.label} ({segment.count})
                      </span>
                    </div>
                  )}
                </For>
              </div>
            </div>
          </section>

          {/* Left: Missing features ranked by skipped tests */}
          <section class="panel">
            <div class="section-header">
              <div>
                <h2>Top Missing Features</h2>
                <p class="section-subtitle">Features whose absence skips the most tests</p>
              </div>
            </div>
            <Show
              when={topMissingFeatures().length > 0}
              fallback={<div class="empty-state" style={{ padding: "16px" }}>No tests are skipped for missing features.</div>}
            >
              <div style={{ display: "flex", "flex-direction": "column", gap: "10px", "margin-top": "10px" }}>
                <For each={topMissingFeatures()}>
                  {(row) => (
                    <div class="issue-card" onClick={() => props.onSelectGap("missing-feature", row.key)}>
                      <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem", gap: "8px" }}>
                        <span style={{ "font-family": "var(--font-mono)", "font-weight": "700", color: "var(--ink)" }}>{row.label}</span>
                        <span style={{ color: "var(--muted)", "white-space": "nowrap" }}>
                          {row.count} tests · {row.exclusive} sole blocker
                        </span>
                      </div>
                      <GapBar row={row} max={topMissingFeatures()[0].count} />
                    </div>
                  )}
                </For>
                <Show when={missingFeatures().length > topMissingFeatures().length}>
                  <button class="btn-view-more" onClick={() => props.onSelectGap("missing-feature", null)}>
                    View all missing features ({missingFeatures().length}) →
                  </button>
                </Show>
              </div>
            </Show>
          </section>

          {/* Left: Change Digest Summary */}
          <section class="panel">
            <div class="section-header">
              <div>
                <h2>Baseline Shifts</h2>
                <p class="section-subtitle">Comparison against reference test run</p>
              </div>
              <div class="section-metric">
                <span>Net Shift</span>
                <strong
                  style={{
                    color:
                      props.viewModel.regressions.length > props.viewModel.improvements.length
                        ? "var(--fail)"
                        : "var(--pass)",
                  }}
                >
                  {props.viewModel.improvements.length - props.viewModel.regressions.length > 0 ? "+" : ""}
                  {props.viewModel.improvements.length - props.viewModel.regressions.length}
                </strong>
              </div>
            </div>

            <div class="change-digest-grid">
              <div>
                <span class="kicker" style={{ color: "var(--fail)", "margin-bottom": "8px" }}>
                  Regressions ({props.viewModel.regressions.length})
                </span>
                <Show
                  when={props.viewModel.regressions.length > 0}
                  fallback={<div class="empty-state" style={{ padding: "16px" }}>No new regressions detected.</div>}
                >
                  <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                    <For each={props.viewModel.regressions.slice(0, 3)}>
                      {(item) => (
                        <div class="change-item" style={{ padding: "10px", "border-radius": "8px" }}>
                          <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem" }}>
                            <span class="pill pill-fail">{item.status}</span>
                            <span class="change-item-suite">{item.suite}</span>
                          </div>
                          <span class="change-item-id" style={{ "font-size": "0.8rem" }}>
                            {item.id}
                          </span>
                        </div>
                      )}
                    </For>
                    <Show when={props.viewModel.regressions.length > 3}>
                      <button class="btn-view-more" onClick={props.onViewAllChanges}>
                        View all regressions ({props.viewModel.regressions.length}) →
                      </button>
                    </Show>
                  </div>
                </Show>
              </div>

              <div>
                <span class="kicker" style={{ color: "var(--pass)", "margin-bottom": "8px" }}>
                  Improvements ({props.viewModel.improvements.length})
                </span>
                <Show
                  when={props.viewModel.improvements.length > 0}
                  fallback={<div class="empty-state" style={{ padding: "16px" }}>No improvements vs baseline.</div>}
                >
                  <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                    <For each={props.viewModel.improvements.slice(0, 3)}>
                      {(item) => (
                        <div class="change-item" style={{ padding: "10px", "border-radius": "8px" }}>
                          <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem" }}>
                            <span class="pill pill-pass">FIXED</span>
                            <span class="change-item-suite">{item.suite}</span>
                          </div>
                          <span class="change-item-id" style={{ "font-size": "0.8rem" }}>
                            {item.id}
                          </span>
                        </div>
                      )}
                    </For>
                    <Show when={props.viewModel.improvements.length > 3}>
                      <button class="btn-view-more" onClick={props.onViewAllChanges}>
                        View all improvements ({props.viewModel.improvements.length}) →
                      </button>
                    </Show>
                  </div>
                </Show>
              </div>
            </div>
          </section>
        </div>

        <div class="column">
          {/* Right: Suite Breakdown stacked bars */}
          <section class="panel" style={{ "max-height": "430px", "overflow-y": "auto" }}>
            <div class="section-header">
              <div>
                <h2>Suite Breakdown</h2>
                <p class="section-subtitle">Test metrics parsed per sub-suite</p>
              </div>
            </div>
            <SuitesBarChart
              suites={props.viewModel.suites}
              activeSuite={props.activeSuite}
              onSelectSuite={props.onSelectSuite}
            />
          </section>

          {/* Right: Top Failure distribution */}
          <section class="panel">
            <div class="section-header">
              <div>
                <h2>Top Failure Areas</h2>
                <p class="section-subtitle">Highest volume issue signatures</p>
              </div>
            </div>
            <IssueDistributionChart
              issueRows={props.viewModel.issueRows}
              onSelectIssue={props.onSelectIssue}
            />
          </section>
        </div>
      </div>
    </div>
  );
}
