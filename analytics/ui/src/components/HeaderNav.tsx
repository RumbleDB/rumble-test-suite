import { Terminal, LayoutDashboard, FolderOpen, AlertOctagon, RefreshCw } from "./Icons";
import { formatPercent } from "../lib/analysis";
import type { ViewModel } from "../lib/analysis";

export type TabType = "overview" | "suites" | "issues" | "changes";

type HeaderNavProps = {
  viewModel: ViewModel;
  activeTab: TabType;
  setActiveTab: (tab: TabType) => void;
};

export function HeaderNav(props: HeaderNavProps) {
  const openProblems = () => props.viewModel.totals.fail + props.viewModel.totals.error;

  return (
    <header class="header-nav">
      <div class="header-nav-container">
        {/* Brand Logo & Title */}
        <div class="header-nav-brand">
          <div class="brand-icon">
            <Terminal size={20} />
          </div>
          <div class="brand-text">
            <span class="brand-name">RumbleDB</span>
            <span class="brand-subtitle">Analytics Dashboard</span>
          </div>
        </div>

        {/* Top Navigation Tabs */}
        <nav class="header-nav-tabs">
          <button
            class="header-nav-tab"
            classList={{ "header-nav-tab-active": props.activeTab === "overview" }}
            onClick={() => props.setActiveTab("overview")}
          >
            <LayoutDashboard size={16} />
            <span>Overview</span>
          </button>

          <button
            class="header-nav-tab"
            classList={{ "header-nav-tab-active": props.activeTab === "suites" }}
            onClick={() => props.setActiveTab("suites")}
          >
            <FolderOpen size={16} />
            <span>Test Suites</span>
            <span class="header-nav-badge">{props.viewModel.suites.length}</span>
          </button>

          <button
            class="header-nav-tab"
            classList={{ "header-nav-tab-active": props.activeTab === "issues" }}
            onClick={() => props.setActiveTab("issues")}
          >
            <AlertOctagon size={16} />
            <span>Failure Groups</span>
            <span
              class="header-nav-badge"
              classList={{ "badge-alert": props.viewModel.issueRows.length > 0 }}
            >
              {props.viewModel.issueRows.length}
            </span>
          </button>

          <button
            class="header-nav-tab"
            classList={{ "header-nav-tab-active": props.activeTab === "changes" }}
            onClick={() => props.setActiveTab("changes")}
          >
            <RefreshCw size={16} />
            <span>Baseline Changes</span>
            <span class="header-nav-badge">
              {props.viewModel.regressions.length + props.viewModel.improvements.length}
            </span>
          </button>
        </nav>

        {/* Overall Health Quick Metric Widget */}
        <div class="header-nav-stats">
          <div class="stat-pill">
            <span class="stat-pill-label">Pass Rate:</span>
            <span
              class="stat-pill-value"
              style={{
                color: props.viewModel.totals.passRate >= 85 ? "var(--pass)" : "var(--fail)",
              }}
            >
              {formatPercent(props.viewModel.totals.passRate)}
            </span>
          </div>

          <div class="stat-pill stat-pill-muted">
            <span class="stat-pill-label">Issues:</span>
            <span class="stat-pill-value">{openProblems()}</span>
            <span class="stat-pill-denom">/ {props.viewModel.totals.total}</span>
          </div>
        </div>
      </div>
    </header>
  );
}
