import { createSignal, createMemo, For, Show } from "solid-js";
import type { SuiteSummary, SlowestCase } from "../lib/analysis";
import { formatPercent, formatDuration, getSuiteClassName } from "../lib/analysis";
import { Copy, Check, AlertCircle } from "./Icons";

type SuiteDetailsPanelProps = {
  suite: SuiteSummary | undefined;
  onViewIssues: () => void;
};

export function SuiteDetailsPanel(props: SuiteDetailsPanelProps) {
  const [copied, setCopied] = createSignal(false);

  const suiteClass = createMemo(() => {
    if (!props.suite) return "";
    return getSuiteClassName(props.suite.name);
  });

  const rerunCommand = createMemo(() => {
    if (!props.suite) return "";
    const name = suiteClass();
    const parser = props.suite.parser;
    if (parser === "default") {
      return `mvn -Dtest=${name} test`;
    }
    return `mvn -Dtest=${name} -Dparser=${parser} test`;
  });

  const handleCopy = () => {
    navigator.clipboard.writeText(rerunCommand());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };



  return (
    <Show
      when={props.suite}
      keyed
      fallback={
        <div class="panel empty-state" style={{ height: "100%", display: "flex", "flex-direction": "column", "justify-content": "center", "align-items": "center", padding: "40px" }}>
          <AlertCircle size={32} />
          <h3>No Suite Selected</h3>
          <p>Select a test suite from the table to inspect its runtime performance and detailed breakdown.</p>
        </div>
      }
    >
      {(suite) => {
        const openCount = suite.fail + suite.error;

        return (
          <div class="panel" style={{ display: "flex", "flex-direction": "column", gap: "18px", padding: "18px", "min-height": "430px" }}>
            <div class="section-header" style={{ "border-bottom": "1px solid var(--border)", "padding-bottom": "12px", "margin-bottom": "4px" }}>
              <div>
                <span class="kicker" style={{ color: "var(--accent)" }}>Suite Performance</span>
                <h2 style={{ "margin-top": "4px", "font-size": "1.25rem", "font-weight": "700" }}>{suite.name}</h2>
              </div>
              <span class="pill pill-parser" style={{ "font-size": "0.65rem", padding: "3px 8px" }}>{suite.parser}</span>
            </div>

            {/* Quick Metrics Grid */}
            <div style={{ display: "grid", "grid-template-columns": "repeat(3, 1fr)", gap: "12px" }}>
              <div style={{ background: "var(--bg)", border: "1px solid var(--border)", padding: "10px", "border-radius": "var(--radius-md)", "text-align": "center" }}>
                <div style={{ "font-size": "0.68rem", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>Total Tests</div>
                <div style={{ "font-size": "1.1rem", "font-weight": "700", "margin-top": "4px" }}>{suite.total}</div>
              </div>
              <div style={{ background: "var(--bg)", border: "1px solid var(--border)", padding: "10px", "border-radius": "var(--radius-md)", "text-align": "center" }}>
                <div style={{ "font-size": "0.68rem", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>Pass Rate</div>
                <div style={{ "font-size": "1.1rem", "font-weight": "700", "margin-top": "4px", color: suite.passRate === 100 ? "var(--pass)" : "var(--fail)" }}>
                  {formatPercent(suite.passRate)}
                </div>
              </div>
              <div style={{ background: "var(--bg)", border: "1px solid var(--border)", padding: "10px", "border-radius": "var(--radius-md)", "text-align": "center" }}>
                <div style={{ "font-size": "0.68rem", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>Duration</div>
                <div style={{ "font-size": "1.1rem", "font-weight": "700", "margin-top": "4px", color: "var(--accent)" }}>
                  {formatDuration(suite.time)}
                </div>
              </div>
            </div>

            {/* Rerun suite command */}
            <div style={{ display: "flex", "flex-direction": "column", gap: "6px" }}>
              <span style={{ "font-size": "0.72rem", "font-weight": "700", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>Rerun Suite</span>
              <div style={{ display: "flex", background: "#0f172a", border: "1px solid #1e293b", "border-radius": "var(--radius-md)", overflow: "hidden" }}>
                <div style={{ flex: 1, padding: "10px 12px", "font-family": "var(--font-mono)", "font-size": "0.75rem", color: "#e2e8f0", "white-space": "nowrap", "overflow-x": "auto" }}>
                  {rerunCommand()}
                </div>
                <button
                  onClick={handleCopy}
                  style={{
                    background: "rgba(255,255,255,0.06)",
                    border: "none",
                    "border-left": "1px solid rgba(255,255,255,0.1)",
                    color: copied() ? "var(--pass)" : "#94a3b8",
                    padding: "0 14px",
                    cursor: "pointer",
                    display: "flex",
                    "align-items": "center",
                    transition: "all 150ms"
                  }}
                  title="Copy command"
                >
                  <Show when={copied()} fallback={<Copy size={14} />}>
                    <Check size={14} />
                  </Show>
                </button>
              </div>
            </div>

            {/* Navigation to issues */}
            <Show when={openCount > 0}>
              <button
                onClick={props.onViewIssues}
                style={{
                  width: "100%",
                  padding: "10px",
                  background: "rgba(198, 40, 40, 0.04)",
                  border: "1px solid rgba(198, 40, 40, 0.15)",
                  color: "var(--fail)",
                  "border-radius": "var(--radius-md)",
                  "font-size": "0.8rem",
                  "font-weight": "600",
                  cursor: "pointer",
                  display: "flex",
                  "justify-content": "center",
                  "align-items": "center",
                  gap: "6px",
                  transition: "all 150ms ease"
                }}
                class="list-item-hover"
              >
                View {openCount} Suite Issues →
              </button>
            </Show>

            {/* Top Slowest Test Cases */}
            <div style={{ display: "flex", "flex-direction": "column", gap: "8px", flex: 1, "margin-top": "4px" }}>
              <span style={{ "font-size": "0.72rem", "font-weight": "700", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>
                Slowest Test Cases (Top 10)
              </span>
              <div style={{ display: "flex", "flex-direction": "column", gap: "8px", "overflow-y": "auto", "max-height": "220px" }}>
                <Show
                  when={suite.slowest && suite.slowest.length > 0}
                  fallback={<div style={{ "font-size": "0.78rem", color: "var(--muted)", padding: "10px", "text-align": "center" }}>No test case runtimes recorded.</div>}
                >
                  <For each={suite.slowest}>
                    {(tc) => {
                      const statusColor = () => {
                        if (tc.status === "PASS") return "var(--pass)";
                        if (tc.status === "FAIL") return "var(--fail)";
                        if (tc.status === "ERROR") return "var(--error)";
                        return "var(--skip)";
                      };

                      return (
                        <div
                          style={{
                            display: "flex",
                            "align-items": "center",
                            "justify-content": "space-between",
                            padding: "8px 10px",
                            background: "rgba(0, 0, 0, 0.01)",
                            border: "1px solid var(--border)",
                            "border-radius": "var(--radius-sm)",
                            "font-size": "0.76rem"
                          }}
                        >
                          <div style={{ display: "flex", "align-items": "center", gap: "8px", flex: 1, overflow: "hidden" }}>
                            <span
                              style={{
                                width: "6px",
                                height: "6px",
                                "border-radius": "50%",
                                background: statusColor(),
                                "flex-shrink": 0
                              }}
                            />
                            <span
                              style={{
                                "font-family": "var(--font-mono)",
                                color: "var(--ink)",
                                overflow: "hidden",
                                "text-overflow": "ellipsis",
                                "white-space": "nowrap"
                              }}
                              title={tc.id}
                            >
                              {tc.id.substring(tc.id.lastIndexOf("]") + 1).trim() || tc.id}
                            </span>
                          </div>
                          <span style={{ "font-family": "var(--font-mono)", color: "var(--muted)", "margin-left": "10px", "flex-shrink": 0 }}>
                            {formatDuration(tc.time)}
                          </span>
                        </div>
                      );
                    }}
                  </For>
                </Show>
              </div>
            </div>
          </div>
        );
      }}
    </Show>
  );
}
