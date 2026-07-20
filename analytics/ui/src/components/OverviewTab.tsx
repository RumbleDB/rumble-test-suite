import { For, Show } from "solid-js";
import { PassRateGauge, SuitesBarChart, IssueDistributionChart } from "./DashboardCharts";
import { formatPercent, formatDuration } from "../lib/analysis";
import type { ViewModel, StatusFilter } from "../lib/analysis";
import type { TabType } from "./HeaderNav";

type OverviewTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  onSelectSuite: (name: string) => void;
  onSelectIssue: (key: string) => void;
  onSelectStatus: (status: StatusFilter) => void;
  onViewAllChanges: () => void;
};

export function OverviewTab(props: OverviewTabProps) {
  const openProblems = () => props.viewModel.totals.fail + props.viewModel.totals.error;

  return (
    <div class="tab-content-animate" style={{ display: "flex", "flex-direction": "column", gap: "24px" }}>
      {/* STAT CARDS IN OVERVIEW */}
      <section class="stat-grid">
        <div class="panel stat-card stat-card-total">
          <span class="stat-label">Total Tests</span>
          <strong class="stat-value">{props.viewModel.totals.total}</strong>
          <span class="stat-hint">{props.viewModel.totals.pass} passing tests</span>
        </div>
        <div class="panel stat-card stat-card-pass">
          <span class="stat-label">Pass Rate</span>
          <strong
            class="stat-value"
            style={{ color: props.viewModel.totals.passRate > 85 ? "var(--pass)" : "var(--fail)" }}
          >
            {formatPercent(props.viewModel.totals.passRate)}
          </strong>
          <span class="stat-hint">{openProblems()} outstanding issues</span>
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
        <div class="panel stat-card stat-card-skip">
          <span class="stat-label">Skips</span>
          <strong class="stat-value" style={{ color: "var(--skip)" }}>
            {props.viewModel.totals.skip}
          </strong>
          <span class="stat-hint">Omitted test cases</span>
        </div>
        <div class="panel stat-card stat-card-time">
          <span class="stat-label">Total Runtime</span>
          <strong class="stat-value" style={{ color: "var(--accent)" }}>
            {formatDuration(props.viewModel.totals.time)}
          </strong>
          <span class="stat-hint">Suite execution duration</span>
        </div>
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
                <div
                  class="status-bar-segment"
                  style={{ width: `${(props.viewModel.totals.pass / props.viewModel.totals.total) * 100}%`, background: "var(--pass)" }}
                  title={`Pass: ${props.viewModel.totals.pass}`}
                />
                <div
                  class="status-bar-segment"
                  style={{ width: `${(props.viewModel.totals.fail / props.viewModel.totals.total) * 100}%`, background: "var(--fail)" }}
                  title={`Fail: ${props.viewModel.totals.fail}`}
                />
                <div
                  class="status-bar-segment"
                  style={{ width: `${(props.viewModel.totals.error / props.viewModel.totals.total) * 100}%`, background: "var(--error)" }}
                  title={`Error: ${props.viewModel.totals.error}`}
                />
                <div
                  class="status-bar-segment"
                  style={{ width: `${(props.viewModel.totals.skip / props.viewModel.totals.total) * 100}%`, background: "var(--skip)" }}
                  title={`Skip: ${props.viewModel.totals.skip}`}
                />
              </div>

              <div class="chart-legend-grid">
                <div class="chart-legend-item" onClick={() => props.onSelectStatus("PASS")}>
                  <span class="legend-dot" style={{ background: "var(--pass)" }} />
                  <span>Pass ({props.viewModel.totals.pass})</span>
                </div>
                <div class="chart-legend-item" onClick={() => props.onSelectStatus("FAIL")}>
                  <span class="legend-dot" style={{ background: "var(--fail)" }} />
                  <span>Fail ({props.viewModel.totals.fail})</span>
                </div>
                <div class="chart-legend-item" onClick={() => props.onSelectStatus("ERROR")}>
                  <span class="legend-dot" style={{ background: "var(--error)" }} />
                  <span>Error ({props.viewModel.totals.error})</span>
                </div>
                <div class="chart-legend-item" onClick={() => props.onSelectStatus("SKIP")}>
                  <span class="legend-dot" style={{ background: "var(--skip)" }} />
                  <span>Skip ({props.viewModel.totals.skip})</span>
                </div>
              </div>
            </div>
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
