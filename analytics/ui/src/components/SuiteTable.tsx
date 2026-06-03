import { createSignal, createMemo, For } from "solid-js";
import type { SuiteSummary } from "../lib/analysis";
import { formatPercent, formatDuration } from "../lib/analysis";

type SuiteTableProps = {
  suites: SuiteSummary[];
  activeSuite: string;
  onSelectSuite: (name: string) => void;
};

type SortKey = "name" | "total" | "passRate" | "errors" | "time";
type SortOrder = "asc" | "desc";

export function SuiteTable(props: SuiteTableProps) {
  const [sortKey, setSortKey] = createSignal<SortKey>("total");
  const [sortOrder, setSortOrder] = createSignal<SortOrder>("desc");

  const handleSort = (key: SortKey) => {
    if (sortKey() === key) {
      setSortOrder(sortOrder() === "asc" ? "desc" : "asc");
    } else {
      setSortKey(key);
      setSortOrder("desc");
    }
  };

  const sortedSuites = createMemo(() => {
    const list = [...props.suites];
    const key = sortKey();
    const order = sortOrder();

    list.sort((a, b) => {
      let comparison = 0;
      if (key === "name") {
        comparison = a.name.localeCompare(b.name);
      } else if (key === "total") {
        comparison = a.total - b.total;
      } else if (key === "passRate") {
        comparison = a.passRate - b.passRate;
      } else if (key === "errors") {
        comparison = (a.fail + a.error) - (b.fail + b.error);
      } else if (key === "time") {
        comparison = a.time - b.time;
      }

      return order === "asc" ? comparison : -comparison;
    });

    return list;
  });

  const SortIcon = (props: { key: SortKey }) => {
    if (sortKey() !== props.key) {
      return <span style={{ color: "var(--border-hover)", "margin-left": "4px", "font-size": "0.75rem" }}>↕</span>;
    }
    return <span style={{ color: "var(--accent)", "margin-left": "4px", "font-weight": "800", "font-size": "0.75rem" }}>{sortOrder() === "asc" ? "↑" : "↓"}</span>;
  };

  return (
    <div class="table-container">
      <table class="dev-table">
        <thead>
          <tr>
            <th onClick={() => handleSort("name")} style={{ cursor: "pointer", width: "30%" }}>
              Suite Name <SortIcon key="name" />
            </th>
            <th style={{ width: "10%" }}>Parser</th>
            <th onClick={() => handleSort("total")} style={{ cursor: "pointer", "text-align": "right", width: "10%" }}>
              Total <SortIcon key="total" />
            </th>
            <th onClick={() => handleSort("passRate")} style={{ cursor: "pointer", "text-align": "right", width: "12%" }}>
              Pass Rate <SortIcon key="passRate" />
            </th>
            <th onClick={() => handleSort("errors")} style={{ cursor: "pointer", "text-align": "right", width: "10%" }}>
              Issues <SortIcon key="errors" />
            </th>
            <th onClick={() => handleSort("time")} style={{ cursor: "pointer", "text-align": "right", width: "12%" }}>
              Duration <SortIcon key="time" />
            </th>
            <th style={{ "text-align": "left", "padding-left": "24px" }}>Breakdown</th>
          </tr>
        </thead>
        <tbody>
          <For each={sortedSuites()}>
            {(suite) => {
              const openCount = suite.fail + suite.error;
              const isActive = () => props.activeSuite === suite.name;

              return (
                <tr 
                  onClick={() => props.onSelectSuite(suite.name)}
                  class={`table-row ${isActive() ? "table-row-active" : ""}`}
                >
                  <td style={{ "font-weight": "600" }}>{suite.name}</td>
                  <td>
                    <span class="pill pill-parser" style={{ "font-size": "0.62rem", "padding": "2px 6px" }}>
                      {suite.parser}
                    </span>
                  </td>
                  <td style={{ "text-align": "right", "font-family": "var(--font-mono)" }}>{suite.total}</td>
                  <td style={{ "text-align": "right", "font-family": "var(--font-mono)", "font-weight": "700", color: suite.passRate === 100 ? "var(--pass)" : "inherit" }}>
                    {formatPercent(suite.passRate)}
                  </td>
                  <td style={{ "text-align": "right", "font-family": "var(--font-mono)", "font-weight": "700", color: openCount > 0 ? "var(--error)" : "var(--muted)" }}>
                    {openCount}
                  </td>
                  <td style={{ "text-align": "right", "font-family": "var(--font-mono)", color: "var(--muted)" }}>
                    {formatDuration(suite.time)}
                  </td>
                  <td style={{ "padding-left": "24px" }}>
                    <div style={{ display: "flex", "align-items": "center", gap: "10px" }}>
                      <div class="mini-progress" style={{ height: "6px", width: "120px", background: "#f1f5f9" }}>
                        <div class="mini-progress-segment" style={{ width: `${(suite.pass / suite.total) * 100}%`, background: "var(--pass)" }} />
                        <div class="mini-progress-segment" style={{ width: `${(suite.fail / suite.total) * 100}%`, background: "var(--fail)" }} />
                        <div class="mini-progress-segment" style={{ width: `${(suite.error / suite.total) * 100}%`, background: "var(--error)" }} />
                        <div class="mini-progress-segment" style={{ width: `${(suite.skip / suite.total) * 100}%`, background: "var(--skip)" }} />
                      </div>
                      <div class="suite-card-split" style={{ "font-size": "0.72rem", display: "flex", gap: "6px", color: "var(--muted)" }}>
                        <span style={{ color: suite.fail > 0 ? "var(--fail)" : "inherit" }}>{suite.fail}F</span>
                        <span style={{ color: suite.error > 0 ? "var(--error)" : "inherit" }}>{suite.error}E</span>
                        <span style={{ color: suite.skip > 0 ? "var(--skip)" : "inherit" }}>{suite.skip}S</span>
                      </div>
                    </div>
                  </td>
                </tr>
              );
            }}
          </For>
        </tbody>
      </table>
    </div>
  );
}
