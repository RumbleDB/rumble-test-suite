import { Terminal, LayoutDashboard, FolderOpen, AlertOctagon, RefreshCw } from "./Icons";
import { formatPercent } from "../lib/analysis";
import type { ViewModel } from "../lib/analysis";

export type TabType = "overview" | "suites" | "issues" | "changes";

type SidebarProps = {
  viewModel: ViewModel;
  activeTab: TabType;
  setActiveTab: (tab: TabType) => void;
};

export function Sidebar(props: SidebarProps) {
  const openProblems = () => props.viewModel.totals.fail + props.viewModel.totals.error;

  return (
    <aside class="sidebar">
      <div class="sidebar-brand">
        <Terminal size={18} />
        <span>Rumble Analytics</span>
      </div>

      <nav class="sidebar-nav">
        <button
          class="sidebar-nav-btn"
          classList={{ "sidebar-nav-btn-active": props.activeTab === "overview" }}
          onClick={() => props.setActiveTab("overview")}
        >
          <LayoutDashboard size={18} />
          <span>Overview</span>
        </button>

        <button
          class="sidebar-nav-btn"
          classList={{ "sidebar-nav-btn-active": props.activeTab === "suites" }}
          onClick={() => props.setActiveTab("suites")}
        >
          <FolderOpen size={18} />
          <span>Test Suites</span>
          <span class="sidebar-badge">{props.viewModel.suites.length}</span>
        </button>

        <button
          class="sidebar-nav-btn"
          classList={{ "sidebar-nav-btn-active": props.activeTab === "issues" }}
          onClick={() => props.setActiveTab("issues")}
        >
          <AlertOctagon size={18} />
          <span>Failure Issues</span>
          <span class="sidebar-badge">{props.viewModel.issueRows.length}</span>
        </button>

        <button
          class="sidebar-nav-btn"
          classList={{ "sidebar-nav-btn-active": props.activeTab === "changes" }}
          onClick={() => props.setActiveTab("changes")}
        >
          <RefreshCw size={18} />
          <span>Changes</span>
          <span class="sidebar-badge">
            {props.viewModel.regressions.length + props.viewModel.improvements.length}
          </span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.72rem", color: "#64748b" }}>
          <span>Overall Pass Rate</span>
          <span
            style={{
              "font-weight": "700",
              color: props.viewModel.totals.passRate > 85 ? "var(--pass)" : "var(--fail)",
            }}
          >
            {formatPercent(props.viewModel.totals.passRate)}
          </span>
        </div>
        <div style={{ height: "4px", width: "100%", background: "#1e293b", "border-radius": "2px", overflow: "hidden", "margin-top": "4px" }}>
          <div style={{ height: "100%", width: `${props.viewModel.totals.passRate}%`, background: "var(--pass)" }} />
        </div>
        <span style={{ "font-size": "0.68rem", color: "#475569", "margin-top": "4px" }}>
          {openProblems()} problems / {props.viewModel.totals.total} tests
        </span>
      </div>
    </aside>
  );
}
