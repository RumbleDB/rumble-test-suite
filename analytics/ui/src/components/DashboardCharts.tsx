import { For, Show } from "solid-js";
import type { SuiteSummary, IssueRow } from "../lib/analysis";
import { formatPercent, formatDuration } from "../lib/analysis";

type PassRateGaugeProps = {
  passRate: number;
  total: number;
  pass: number;
};

export function PassRateGauge(props: PassRateGaugeProps) {
  const radius = 64;
  const strokeWidth = 6;
  const circumference = 2 * Math.PI * radius;
  
  // Calculate dash offset: full is 0, empty is circumference
  const strokeDashoffset = () => circumference - (props.passRate / 100) * circumference;

  return (
    <div class="chart-container">
      <svg width="160" height="160" viewBox="0 0 160 160" class="radial-gauge">
        {/* Track circle */}
        <circle
          cx="80"
          cy="80"
          r={radius}
          fill="none"
          stroke="var(--border)"
          stroke-width={strokeWidth}
        />
        
        {/* Progress circle */}
        <circle
          cx="80"
          cy="80"
          r={radius}
          fill="none"
          stroke={props.passRate === 100 ? "var(--pass)" : "var(--accent)"}
          stroke-width={strokeWidth}
          stroke-dasharray={circumference.toString()}
          stroke-dashoffset={strokeDashoffset().toString()}
          stroke-linecap="round"
          transform="rotate(-90 80 80)"
          style={{
            transition: "stroke-dashoffset 800ms cubic-bezier(0.4, 0, 0.2, 1)"
          }}
        />
      </svg>
      <div class="gauge-center-text">
        <span class="gauge-val" style={{ "font-size": "1.6rem", "font-weight": "800", "letter-spacing": "-0.02em" }}>{formatPercent(props.passRate)}</span>
        <span class="gauge-lbl" style={{ "font-size": "0.7rem", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>Pass Rate</span>
      </div>
    </div>
  );
}

type SuitesBarChartProps = {
  suites: SuiteSummary[];
  activeSuite: string;
  onSelectSuite: (name: string) => void;
};

export function SuitesBarChart(props: SuitesBarChartProps) {
  // Sort suites by total test count descending
  const sortedSuites = () => [...props.suites].slice(0, 10);

  return (
    <div class="suites-container" style={{ "margin-top": "10px" }}>
      <For each={sortedSuites()}>
        {(suite) => {
          const total = Math.max(suite.total, 1);
          const passPct = (suite.pass / total) * 100;
          const failPct = (suite.fail / total) * 100;
          const errPct = (suite.error / total) * 100;
          const skipPct = (suite.skip / total) * 100;

          const isActive = () => props.activeSuite === suite.name;

          return (
            <div 
              onClick={() => props.onSelectSuite(suite.name)}
              class={`suite-card ${isActive() ? "suite-card-active" : ""}`}
              style={{ padding: "12px 14px", gap: "8px" }}
            >
              <div style={{ display: "flex", "justify-content": "space-between", "align-items": "center" }}>
                <div style={{ display: "flex", "align-items": "center", gap: "8px" }}>
                  <span style={{ "font-size": "0.88rem", "font-weight": "700", color: "var(--ink)" }}>
                    {suite.name}
                  </span>
                  <span class="pill pill-parser" style={{ "font-size": "0.6rem", "padding": "2px 6px" }}>{suite.parser}</span>
                </div>
                <span style={{ "font-size": "0.78rem", color: "var(--muted)" }}>
                  {suite.pass}/{suite.total} passed ({formatPercent(suite.passRate)}) &middot; {formatDuration(suite.time)}
                </span>
              </div>
              
              {/* Stacked bar showing suite breakdown */}
              <div style={{ display: "flex", "flex-direction": "column", gap: "4px" }}>
                <div class="mini-progress" style={{ height: "8px" }}>
                  <div 
                    class="mini-progress-segment" 
                    style={{ 
                      width: `${passPct}%`, 
                      background: "var(--pass)",
                      transition: "width 500ms ease" 
                    }} 
                    title={`${suite.pass} Pass`}
                  />
                  <div 
                    class="mini-progress-segment" 
                    style={{ 
                      width: `${failPct}%`, 
                      background: "var(--fail)",
                      transition: "width 500ms ease" 
                    }} 
                    title={`${suite.fail} Fail`}
                  />
                  <div 
                    class="mini-progress-segment" 
                    style={{ 
                      width: `${errPct}%`, 
                      background: "var(--error)",
                      transition: "width 500ms ease" 
                    }} 
                    title={`${suite.error} Error`}
                  />
                  <div 
                    class="mini-progress-segment" 
                    style={{ 
                      width: `${skipPct}%`, 
                      background: "var(--skip)",
                      transition: "width 500ms ease" 
                    }} 
                    title={`${suite.skip} Skip`}
                  />
                </div>
              </div>
            </div>
          );
        }}
      </For>
    </div>
  );
}

type IssueDistributionChartProps = {
  issueRows: IssueRow[];
  onSelectIssue: (key: string) => void;
};

export function IssueDistributionChart(props: IssueDistributionChartProps) {
  // Take top 5 issue groups by count
  const topIssues = () => props.issueRows.slice(0, 5);
  const maxCount = () => Math.max(...topIssues().map(i => i.count), 1);

  return (
    <div style={{ display: "flex", "flex-direction": "column", gap: "12px", "margin-top": "10px" }}>
      <Show 
        when={topIssues().length > 0} 
        fallback={<div class="empty-state" style={{ padding: "20px" }}>No failures or errors detected in this scope.</div>}
      >
        <For each={topIssues()}>
          {(issue) => {
            const barWidth = () => (issue.count / maxCount()) * 100;
            const barColor = () => issue.status === "ERROR" ? "var(--error)" : "var(--fail)";

            return (
              <div 
                onClick={() => props.onSelectIssue(issue.key)}
                class="issue-card"
              >
                <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem", "font-weight": "600" }}>
                  <span class="issue-card-suite" style={{ color: "var(--ink)", "font-weight": "700" }}>{issue.suite}</span>
                  <span style={{ color: "var(--muted)" }}>{issue.count} cases</span>
                </div>
                
                <div 
                  class="issue-card-msg" 
                  style={{ 
                    "font-size": "0.8rem", 
                    "-webkit-line-clamp": "1", 
                    color: "var(--muted)",
                    "margin-bottom": "4px" 
                  }}
                >
                  {issue.message}
                </div>

                <div class="mini-progress" style={{ height: "4px", background: "#f1f5f9" }}>
                  <div 
                    class="mini-progress-segment" 
                    style={{ 
                      width: `${barWidth()}%`, 
                      background: barColor(),
                      transition: "width 500ms ease" 
                    }} 
                  />
                </div>
              </div>
            );
          }}
        </For>
      </Show>
    </div>
  );
}
