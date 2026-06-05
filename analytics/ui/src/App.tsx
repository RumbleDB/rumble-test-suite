import { Show, createMemo, createSignal, For, onMount } from "solid-js";
import type { AnalysisPayload, ParserMode, StatusFilter, ViewModel, IssueRow, SuiteSummary, Status } from "./lib/analysis";
import { buildViewModel, formatPercent, formatDuration, getParserCommand, getSingleTestCaseCommand } from "./lib/analysis";
import { 
  ChevronLeft, 
  ChevronRight, 
  Copy, 
  Check, 
  Search, 
  AlertCircle, 
  CheckCircle,
  Play, 
  Terminal, 
  ShieldAlert 
} from "./components/Icons";
import { PassRateGauge, SuitesBarChart, IssueDistributionChart } from "./components/DashboardCharts";
import { SuiteTable } from "./components/SuiteTable";
import { SuiteDetailsPanel } from "./components/SuiteDetailsPanel";

function readEmbeddedAnalysis(): AnalysisPayload {
  const payload = document.getElementById("initial-analysis-data");
  if (!payload?.textContent) {
    throw new Error("This report does not contain embedded analysis data.");
  }
  return JSON.parse(payload.textContent) as AnalysisPayload;
}

type TabType = "overview" | "suites" | "issues" | "changes";

export default function App() {
  const [viewModel, setViewModel] = createSignal<ViewModel | null>(null);
  const [loadError, setLoadError] = createSignal("");
  const [activeTab, setActiveTab] = createSignal<TabType>("overview");
  
  // Filtering and selection states
  const [activeSuite, setActiveSuite] = createSignal("ALL");
  const [activeStatus, setActiveStatus] = createSignal<StatusFilter>("ALL");
  const [searchQuery, setSearchQuery] = createSignal("");
  
  // Issue explorer states
  const [selectedIssueKey, setSelectedIssueKey] = createSignal<string | null>(null);
  const [parserMode, setParserMode] = createSignal<ParserMode>("jsoniq");
  const [issuePage, setIssuePage] = createSignal(1);
  const [issueSearch, setIssueSearch] = createSignal("");
  const [copiedKey, setCopiedKey] = createSignal<string | null>(null);
  const [expandedRegressionId, setExpandedRegressionId] = createSignal<string | null>(null);

  onMount(async () => {
    try {
      const data = readEmbeddedAnalysis();
      const model = buildViewModel(data, "embedded analysis");
      setViewModel(model);
      if (model.issueRows.length > 0) {
        setSelectedIssueKey(model.issueRows[0].key);
      }
    } catch (error) {
      // Attempt to load from dev server during development
      try {
        const response = await fetch("/@fs/Users/jimmy/Documents/Thesis/rumble-test-suite/analytics-results/analysis.json");
        if (response.ok) {
          const data = await response.json();
          const model = buildViewModel(data, "dev mode analysis");
          setViewModel(model);
          if (model.issueRows.length > 0) {
            setSelectedIssueKey(model.issueRows[0].key);
          }
        } else {
          setLoadError("Embedded payload not found and dev mode analytics.json could not be loaded.");
        }
      } catch (fetchErr) {
        setLoadError(error instanceof Error ? error.message : String(error));
      }
    }
  });

  // Derived filter options
  const suiteOptions = createMemo<string[]>(() => [
    "ALL", 
    ...(viewModel()?.suites.map((suite) => suite.name) ?? [])
  ]);

  // Handle active suite selection
  const handleSelectSuite = (suite: string) => {
    setActiveSuite(suite);
    setActiveTab("suites");
  };

  // Handle active issue selection
  const handleSelectIssue = (key: string) => {
    setSelectedIssueKey(key);
    setActiveTab("issues");
  };

  // Filtered issue rows
  const filteredIssues = createMemo(() => {
    const query = searchQuery().trim().toLowerCase();
    return (viewModel()?.issueRows ?? []).filter((item) => {
      if (activeStatus() !== "ALL" && item.status !== activeStatus()) {
        return false;
      }
      if (activeSuite() !== "ALL" && item.suite !== activeSuite()) {
        return false;
      }
      if (!query) {
        return true;
      }
      const casesSearchText = item.cases.map(c => [c.id, c.description, c.query].filter(Boolean).join("\n")).join("\n");
      return [item.suite, item.status, item.message, casesSearchText].join("\n").toLowerCase().includes(query);
    });
  });

  // Selected issue details
  const selectedIssue = createMemo(() => {
    const issues = filteredIssues();
    if (issues.length === 0) return null;
    return issues.find((item) => item.key === selectedIssueKey()) ?? issues[0];
  });

  // Filtered regressions & improvements
  const filteredRegressions = createMemo(() =>
    (viewModel()?.regressions ?? []).filter((item) => {
      if (activeStatus() !== "ALL" && item.status !== activeStatus()) {
        return false;
      }
      if (activeSuite() !== "ALL" && item.suite !== activeSuite()) {
        return false;
      }
      return true;
    })
  );

  const filteredImprovements = createMemo(() =>
    (viewModel()?.improvements ?? []).filter((item) => {
      if (activeSuite() !== "ALL" && item.suite !== activeSuite()) {
        return false;
      }
      return true;
    })
  );

  // Copy command helpers
  const handleCopyCommand = async (command: string, key: string) => {
    await navigator.clipboard.writeText(command);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleClearFilters = () => {
    setActiveSuite("ALL");
    setActiveStatus("ALL");
    setSearchQuery("");
  };

  const hasActiveFilters = createMemo(() => {
    return activeSuite() !== "ALL" || activeStatus() !== "ALL" || searchQuery().trim() !== "";
  });

  return (
    <div class="page-shell">
      <main class="page">
        <Show
          when={viewModel()}
          fallback={
            <div class="page-center">
              <section class="panel empty-state" style={{ "max-width": "500px" }}>
                <ShieldAlert size={48} stroke="var(--error)" />
                <h1>Failed to Load Report</h1>
                <p>{loadError()}</p>
                <p class="empty-state-detail">Please build the report using the build tool or run tests first.</p>
              </section>
            </div>
          }
        >
          {(modelAccessor) => {
            const model = modelAccessor();
            
            const openProblems = () => model.totals.fail + model.totals.error;

            return (
              <>
                {/* HERO HEADER */}
                <header class="hero-header">
                  <div class="brand">
                    <Terminal size={14} /> <span>Rumble Test Analytics</span>
                  </div>
                  <h1 class="hero-title">Rumble Test Dashboard</h1>
                  <p class="subtitle">
                    Comprehensive diagnostic health review of Rumble test runs. 
                    Showing test suite pass rates, regression detection, and aggregated issue patterns.
                  </p>
                </header>

                {/* NAVIGATION TABS */}
                <nav class="nav-tabs">
                  <button 
                    class={`tab-btn ${activeTab() === "overview" ? "tab-btn-active" : ""}`}
                    onClick={() => setActiveTab("overview")}
                  >
                    Overview
                  </button>
                  <button 
                    class={`tab-btn ${activeTab() === "suites" ? "tab-btn-active" : ""}`}
                    onClick={() => setActiveTab("suites")}
                  >
                    Suites ({model.suites.length})
                  </button>
                  <button 
                    class={`tab-btn ${activeTab() === "issues" ? "tab-btn-active" : ""}`}
                    onClick={() => setActiveTab("issues")}
                  >
                    Issues ({model.issueRows.length})
                  </button>
                  <button 
                    class={`tab-btn ${activeTab() === "changes" ? "tab-btn-active" : ""}`}
                    onClick={() => setActiveTab("changes")}
                  >
                    Changes ({model.regressions.length + model.improvements.length})
                  </button>
                </nav>

                {/* STAT CARDS */}
                <section class="stat-grid">
                  <div class="panel stat-card">
                    <span class="stat-label">Total Tests</span>
                    <strong class="stat-value">{model.totals.total}</strong>
                    <span class="stat-hint">{model.totals.pass} passing tests</span>
                  </div>
                  <div class="panel stat-card">
                    <span class="stat-label">Pass Rate</span>
                    <strong class="stat-value" style={{ color: model.totals.passRate > 85 ? "var(--pass)" : "var(--fail)" }}>
                      {formatPercent(model.totals.passRate)}
                    </strong>
                    <span class="stat-hint">{openProblems()} outstanding issues</span>
                  </div>
                  <div class="panel stat-card">
                    <span class="stat-label">Failures</span>
                    <strong class="stat-value" style={{ color: model.totals.fail > 0 ? "var(--fail)" : "var(--muted)" }}>
                      {model.totals.fail}
                    </strong>
                    <span class="stat-hint">Unmet assertions</span>
                  </div>
                  <div class="panel stat-card">
                    <span class="stat-label">Errors</span>
                    <strong class="stat-value" style={{ color: model.totals.error > 0 ? "var(--error)" : "var(--muted)" }}>
                      {model.totals.error}
                    </strong>
                    <span class="stat-hint">Exceptions occurred</span>
                  </div>
                  <div class="panel stat-card">
                    <span class="stat-label">Skips</span>
                    <strong class="stat-value" style={{ color: "var(--skip)" }}>
                      {model.totals.skip}
                    </strong>
                    <span class="stat-hint">Omitted test cases</span>
                  </div>
                  <div class="panel stat-card">
                    <span class="stat-label">Total Runtime</span>
                    <strong class="stat-value" style={{ color: "var(--accent)" }}>
                      {formatDuration(model.totals.time)}
                    </strong>
                    <span class="stat-hint">Suite execution duration</span>
                  </div>
                </section>

                {/* TAB CONTENTS */}
                <Show when={activeTab() === "overview"}>
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
                            passRate={model.totals.passRate} 
                            total={model.totals.total}
                            pass={model.totals.pass}
                          />
                          
                          <div class="status-bar-bar">
                            <div 
                              class="status-bar-segment" 
                              style={{ width: `${(model.totals.pass / model.totals.total) * 100}%`, background: "var(--pass)" }} 
                              title={`Pass: ${model.totals.pass}`}
                            />
                            <div 
                              class="status-bar-segment" 
                              style={{ width: `${(model.totals.fail / model.totals.total) * 100}%`, background: "var(--fail)" }} 
                              title={`Fail: ${model.totals.fail}`}
                            />
                            <div 
                              class="status-bar-segment" 
                              style={{ width: `${(model.totals.error / model.totals.total) * 100}%`, background: "var(--error)" }} 
                              title={`Error: ${model.totals.error}`}
                            />
                            <div 
                              class="status-bar-segment" 
                              style={{ width: `${(model.totals.skip / model.totals.total) * 100}%`, background: "var(--skip)" }} 
                              title={`Skip: ${model.totals.skip}`}
                            />
                          </div>

                          <div class="chart-legend-grid">
                            <div class="chart-legend-item" onClick={() => setActiveStatus("PASS")}>
                              <span class="legend-dot" style={{ background: "var(--pass)" }} />
                              <span>Pass ({model.totals.pass})</span>
                            </div>
                            <div class="chart-legend-item" onClick={() => setActiveStatus("FAIL")}>
                              <span class="legend-dot" style={{ background: "var(--fail)" }} />
                              <span>Fail ({model.totals.fail})</span>
                            </div>
                            <div class="chart-legend-item" onClick={() => setActiveStatus("ERROR")}>
                              <span class="legend-dot" style={{ background: "var(--error)" }} />
                              <span>Error ({model.totals.error})</span>
                            </div>
                            <div class="chart-legend-item" onClick={() => setActiveStatus("SKIP")}>
                              <span class="legend-dot" style={{ background: "var(--skip)" }} />
                              <span>Skip ({model.totals.skip})</span>
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
                            <strong style={{ color: model.regressions.length > model.improvements.length ? "var(--fail)" : "var(--pass)" }}>
                              {model.improvements.length - model.regressions.length > 0 ? "+" : ""}
                              {model.improvements.length - model.regressions.length}
                            </strong>
                          </div>
                        </div>

                        <div class="change-digest-grid">
                          <div>
                            <span class="kicker" style={{ color: "var(--fail)", "margin-bottom": "8px" }}>
                              Regressions ({model.regressions.length})
                            </span>
                            <Show 
                              when={model.regressions.length > 0}
                              fallback={<div class="empty-state" style={{ padding: "16px" }}>No new regressions detected.</div>}
                            >
                              <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                                <For each={model.regressions.slice(0, 3)}>{(item) => (
                                  <div class="change-item" style={{ padding: "10px", "border-radius": "8px" }}>
                                    <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem" }}>
                                      <span class="pill pill-fail">{item.status}</span>
                                      <span class="change-item-suite">{item.suite}</span>
                                    </div>
                                    <span class="change-item-id" style={{ "font-size": "0.8rem" }}>{item.id}</span>
                                  </div>
                                )}</For>
                                <Show when={model.regressions.length > 3}>
                                  <button class="tab-btn" style={{ padding: "4px", "font-size": "0.8rem" }} onClick={() => setActiveTab("changes")}>
                                    View all regressions
                                  </button>
                                </Show>
                              </div>
                            </Show>
                          </div>

                          <div>
                            <span class="kicker" style={{ color: "var(--pass)", "margin-bottom": "8px" }}>
                              Improvements ({model.improvements.length})
                            </span>
                            <Show 
                              when={model.improvements.length > 0}
                              fallback={<div class="empty-state" style={{ padding: "16px" }}>No improvements vs baseline.</div>}
                            >
                              <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                                <For each={model.improvements.slice(0, 3)}>{(item) => (
                                  <div class="change-item" style={{ padding: "10px", "border-radius": "8px" }}>
                                    <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem" }}>
                                      <span class="pill pill-pass">FIXED</span>
                                      <span class="change-item-suite">{item.suite}</span>
                                    </div>
                                    <span class="change-item-id" style={{ "font-size": "0.8rem" }}>{item.id}</span>
                                  </div>
                                )}</For>
                                <Show when={model.improvements.length > 3}>
                                  <button class="tab-btn" style={{ padding: "4px", "font-size": "0.8rem" }} onClick={() => setActiveTab("changes")}>
                                    View all improvements
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
                          suites={model.suites}
                          activeSuite={activeSuite()}
                          onSelectSuite={handleSelectSuite}
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
                          issueRows={model.issueRows}
                          onSelectIssue={handleSelectIssue}
                        />
                      </section>
                    </div>
                  </div>
                </Show>

                <Show when={activeTab() === "suites"}>
                  <div class="dashboard-grid">
                    <div class="column" style={{ flex: "1.4" }}>
                      <section class="panel">
                        <div class="section-header">
                          <div>
                            <h2>Test Suites</h2>
                            <p class="section-subtitle">Execution performance and composition per suite</p>
                          </div>
                        </div>
                        <SuiteTable 
                          suites={model.suites}
                          activeSuite={activeSuite() === "ALL" && model.suites.length > 0 ? model.suites[0].name : activeSuite()}
                          onSelectSuite={handleSelectSuite}
                        />
                      </section>
                    </div>

                    <div class="column" style={{ flex: "1" }}>
                      <SuiteDetailsPanel
                        suite={model.suites.find(s => s.name === (activeSuite() === "ALL" && model.suites.length > 0 ? model.suites[0].name : activeSuite()))}
                        onViewIssues={() => {
                          setActiveTab("issues");
                        }}
                      />
                    </div>
                  </div>
                </Show>

                <Show when={activeTab() === "issues"}>
                  <div class="panel" style={{ padding: "18px" }}>
                    {/* Toolbar Filters */}
                    <div class="section-header" style={{ "border-bottom": "1px solid rgba(255,255,255,0.04)", "padding-bottom": "14px", "margin-bottom": "14px" }}>
                      <div class="toolbar">
                        <div class="search-input-wrapper">
                          <Search size={16} />
                          <input 
                            type="text" 
                            class="search-input" 
                            placeholder="Search exception messages, testcase IDs..."
                            value={searchQuery()}
                            onInput={(e) => setSearchQuery(e.currentTarget.value)}
                          />
                        </div>

                        <select 
                          class="select-filter"
                          value={activeSuite()} 
                          onChange={(e) => setActiveSuite(e.currentTarget.value)}
                        >
                          <For each={suiteOptions()}>
                            {(option) => <option value={option}>{option === "ALL" ? "All Suites" : option}</option>}
                          </For>
                        </select>

                        <select 
                          class="select-filter"
                          value={activeStatus()} 
                          onChange={(e) => setActiveStatus(e.currentTarget.value as StatusFilter)}
                        >
                          <option value="ALL">All Statuses</option>
                          <option value="PASS">Pass</option>
                          <option value="FAIL">Fail</option>
                          <option value="ERROR">Error</option>
                          <option value="SKIP">Skip</option>
                        </select>

                        <button 
                          class="btn-clear" 
                          disabled={!hasActiveFilters()}
                          onClick={handleClearFilters}
                        >
                          Clear Filters
                        </button>
                      </div>
                    </div>

                    {/* Split Layout */}
                    <div class="issues-layout">
                      {/* Left: Issue Cards List */}
                      <div class="column">
                        <div class="detail-section-title">
                          <span>Issues Matches</span>
                          <span>{filteredIssues().length} issues</span>
                        </div>
                        
                        <div class="issues-list-container">
                          <Show 
                            when={filteredIssues().length > 0}
                            fallback={
                              <div class="empty-state">
                                <AlertCircle size={32} />
                                <h3>No issues match current filter</h3>
                                <p>Try clearing active search or selection filters.</p>
                              </div>
                            }
                          >
                            <For each={filteredIssues()}>
                              {(issue) => (
                                <button 
                                  onClick={() => setSelectedIssueKey(issue.key)}
                                  class={`issue-card ${selectedIssue()?.key === issue.key ? "issue-card-active" : ""}`}
                                >
                                  <div class="issue-card-header">
                                    <div class="issue-card-meta">
                                      <span class={`pill pill-${issue.status.toLowerCase()}`}>{issue.status}</span>
                                      <span class="pill pill-parser">{issue.parser}</span>
                                      <span class="issue-card-suite">{issue.suite}</span>
                                    </div>
                                    <span class="issue-card-count">{issue.count} cases</span>
                                  </div>
                                  <div class="issue-card-msg">{issue.message}</div>
                                </button>
                              )}
                            </For>
                          </Show>
                        </div>
                      </div>

                      {/* Right: Issue Detail diagnostics */}
                      <div class="column">
                        <div class="detail-section-title">
                          <span>Issue Diagnostics</span>
                        </div>

                        <Show 
                          when={selectedIssue()} 
                          keyed
                          fallback={
                            <div class="panel empty-state">
                              <AlertCircle size={32} />
                              <h3>No Issue Selected</h3>
                              <p>Select an issue group from the left panel to begin diagnostic analysis.</p>
                            </div>
                          }
                        >
                          {(issue) => {
                            
                            // Interactive local search inside the affected test cases list
                            const [affectedSearch, setAffectedSearch] = createSignal("");
                            const [page, setPage] = createSignal(1);
                            const [expandedCaseId, setExpandedCaseId] = createSignal<string | null>(null);
                            const itemsPerPage = 8;

                            const matchingTestCases = createMemo(() => {
                              const q = affectedSearch().trim().toLowerCase();
                              if (!q) return issue.cases;
                              return issue.cases.filter(c => 
                                c.id.toLowerCase().includes(q) || 
                                (c.description && c.description.toLowerCase().includes(q)) || 
                                (c.query && c.query.toLowerCase().includes(q))
                              );
                            });

                            const totalPages = createMemo(() => Math.max(Math.ceil(matchingTestCases().length / itemsPerPage), 1));
                            
                            const paginatedTestCases = createMemo(() => {
                              const start = (page() - 1) * itemsPerPage;
                              return matchingTestCases().slice(start, start + itemsPerPage);
                            });

                            const rerunCommand = createMemo(() => getParserCommand(issue, parserMode()));

                            return (
                              <div class="detail-card">
                                {/* Error Signature */}
                                <div class="detail-msg-box">
                                  <div style={{ display: "flex", "justify-content": "space-between", "font-size": "0.78rem", "margin-bottom": "10px", "border-bottom": "1px solid rgba(255,255,255,0.06)", "padding-bottom": "8px", "align-items": "center" }}>
                                    <div style={{ display: "flex", gap: "8px", "align-items": "center" }}>
                                      <span class="kicker" style={{ color: issue.status === "ERROR" ? "#fb923c" : "#f87171", "margin-bottom": "0" }}>
                                        {issue.status} signature
                                      </span>
                                      <span class="pill pill-parser">{issue.parser}</span>
                                    </div>
                                    <span style={{ "font-family": "var(--font-mono)", color: "#94a3b8" }}>{issue.suite}</span>
                                  </div>
                                  <pre style="white-space: pre-wrap; font-family: var(--font-mono); font-size: 0.85rem; line-height: 1.4">
                                    {issue.message}
                                  </pre>
                                </div>

                                {/* Maven rerun tools */}
                                <div class="detail-rerun-box">
                                  <div class="rerun-header">
                                    <h4>Rerun Entire Suite</h4>
                                    <div class="parser-selector">
                                      <button 
                                        class={`parser-btn ${parserMode() === "jsoniq" ? "parser-btn-active" : ""}`}
                                        onClick={() => setParserMode("jsoniq")}
                                      >
                                        JSONiq
                                      </button>
                                      <button 
                                        class={`parser-btn ${parserMode() === "xquery" ? "parser-btn-active" : ""}`}
                                        onClick={() => setParserMode("xquery")}
                                      >
                                        XQuery
                                      </button>
                                      <button 
                                        class={`parser-btn ${parserMode() === "default" ? "parser-btn-active" : ""}`}
                                        onClick={() => setParserMode("default")}
                                      >
                                        Default
                                      </button>
                                    </div>
                                  </div>

                                  <div class="command-box">
                                    <code>{rerunCommand()}</code>
                                    <button 
                                      class={`btn-copy ${copiedKey() === "suite" ? "btn-copy-active" : ""}`}
                                      onClick={() => handleCopyCommand(rerunCommand(), "suite")}
                                    >
                                      <Show when={copiedKey() === "suite"} fallback={<><Copy size={14} /> Copy</>}>
                                        <Check size={14} /> Copied
                                      </Show>
                                    </button>
                                  </div>
                                </div>

                                {/* Affected Testcases list */}
                                <div style={{ display: "flex", "flex-direction": "column", gap: "10px" }}>
                                  <div style={{ display: "flex", "justify-content": "space-between", "align-items": "center" }}>
                                    <span class="kicker">Impacted Tests ({matchingTestCases().length})</span>
                                    <div class="search-input-wrapper" style={{ width: "160px" }}>
                                      <Search size={12} />
                                      <input 
                                        type="text" 
                                        class="search-input" 
                                        style={{ padding: "6px 10px 6px 30px", "font-size": "0.78rem" }}
                                        placeholder="Filter list..."
                                        value={affectedSearch()}
                                        onInput={(e) => {
                                          setAffectedSearch(e.currentTarget.value);
                                          setPage(1);
                                          setExpandedCaseId(null);
                                        }}
                                      />
                                    </div>
                                  </div>

                                  <div class="affected-list">
                                    <For 
                                      each={paginatedTestCases()}
                                      fallback={<div class="empty-state" style={{ padding: "20px" }}>No matching testcase IDs.</div>}
                                    >
                                      {(c) => {
                                        const singleTestRerun = () => getSingleTestCaseCommand(issue.suite, c.id, parserMode());
                                        const isSingleCopied = () => copiedKey() === c.id;

                                        return (
                                          <div class="affected-list-item" style={{ "flex-direction": "column", "align-items": "stretch", gap: "6px" }}>
                                            <div 
                                              style={{ display: "flex", "justify-content": "space-between", "align-items": "center", cursor: "pointer" }}
                                              onClick={() => setExpandedCaseId(expandedCaseId() === c.id ? null : c.id)}
                                            >
                                              <div style={{ display: "flex", "align-items": "center", gap: "8px", overflow: "hidden" }}>
                                                <span style={{ color: "var(--muted)", "font-size": "0.7rem", width: "12px", "text-align": "center", "user-select": "none" }}>
                                                  {expandedCaseId() === c.id ? "▼" : "▶"}
                                                </span>
                                                <span style={{ overflow: "hidden", "text-overflow": "ellipsis", "white-space": "nowrap", "font-weight": "600", color: "var(--ink)" }} title={c.id}>
                                                  {c.id}
                                                </span>
                                              </div>
                                              <button 
                                                class="affected-rerun-btn"
                                                onClick={(e) => {
                                                  e.stopPropagation();
                                                  handleCopyCommand(singleTestRerun(), c.id);
                                                }}
                                                title="Copy command to run just this test"
                                              >
                                                <Show when={isSingleCopied()} fallback={<><Play size={10} style={{ "margin-right": "4px" }} /> Rerun</>}>
                                                  Copied
                                                </Show>
                                              </button>
                                            </div>
                                            
                                            <Show when={expandedCaseId() === c.id}>
                                              <div style={{ "padding-left": "20px", "padding-bottom": "4px", "display": "flex", "flex-direction": "column", gap: "6px" }}>
                                                <Show when={c.description}>
                                                  <p style={{ margin: "4px 0 8px 0", "font-size": "0.78rem", color: "var(--muted)", "line-height": "1.4", "font-style": "italic" }}>
                                                    {c.description}
                                                  </p>
                                                </Show>
                                                <Show when={c.query} fallback={<p style={{ margin: "4px 0 0 0", "font-size": "0.75rem", color: "var(--muted)" }}>No query text available</p>}>
                                                   <div style={{ display: "flex", "flex-direction": "column", gap: "6px", width: "100%" }}>
                                                     <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: "10px", "border-radius": "6px", overflow: "auto" }}>
                                                       <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#a5b4fc", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                                         {c.query}
                                                       </pre>
                                                     </div>
                                                     <Show when={c.expected}>
                                                       <div style={{ display: "flex", "flex-direction": "column", gap: "2px" }}>
                                                         <span style={{ "font-size": "0.7rem", color: "var(--muted)", "font-weight": "600", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Expected Result:</span>
                                                         <div style={{ background: "#1e293b", border: "1px solid #334155", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                                           <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#cbd5e1", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                                             {c.expected}
                                                           </pre>
                                                         </div>
                                                       </div>
                                                     </Show>
                                                   </div>
                                                </Show>
                                              </div>
                                            </Show>
                                          </div>
                                        );
                                      }}
                                    </For>
                                  </div>

                                  {/* Pagination controls */}
                                  <Show when={totalPages() > 1}>
                                    <div class="pagination">
                                      <span>Page {page()} of {totalPages()}</span>
                                      <div class="pagination-buttons">
                                        <button 
                                          class="pagination-btn" 
                                          disabled={page() === 1}
                                          onClick={() => setPage(p => p - 1)}
                                        >
                                          <ChevronLeft size={12} />
                                        </button>
                                        <button 
                                          class="pagination-btn" 
                                          disabled={page() === totalPages()}
                                          onClick={() => setPage(p => p + 1)}
                                        >
                                          <ChevronRight size={12} />
                                        </button>
                                      </div>
                                    </div>
                                  </Show>
                                </div>
                              </div>
                            );
                          }}
                        </Show>
                      </div>
                    </div>
                  </div>
                </Show>

                <Show when={activeTab() === "changes"}>
                  <div class="change-digest-grid">
                    {/* Regressions list */}
                    <div class="panel">
                      <div class="change-panel-header">
                        <h3>New Regressions</h3>
                        <span class="change-panel-count change-panel-count-regression">
                          {filteredRegressions().length}
                        </span>
                      </div>

                      <Show 
                        when={filteredRegressions().length > 0}
                        fallback={
                          <div class="empty-state">
                            <CheckCircle size={32} stroke="var(--pass)" />
                            <h3>No Regressions Found</h3>
                            <p>All previously failing/erroring baselines are clean in this run scope.</p>
                          </div>
                        }
                      >
                        <div class="change-list">
                          <For each={filteredRegressions()}>
                            {(item) => {
                              const singleTestRerun = () => getSingleTestCaseCommand(item.suite, item.id, parserMode());
                              const isCopied = () => copiedKey() === item.id;

                              return (
                                <div 
                                    class="change-item"
                                    style={{ cursor: "pointer", display: "flex", "flex-direction": "column", gap: "6px" }}
                                    onClick={() => setExpandedRegressionId(expandedRegressionId() === item.id ? null : item.id)}
                                  >
                                    <div class="change-item-top">
                                      <div style={{ display: "flex", gap: "8px", "align-items": "center" }}>
                                        <span style={{ color: "var(--muted)", "font-size": "0.7rem", "user-select": "none" }}>
                                          {expandedRegressionId() === item.id ? "▼" : "▶"}
                                        </span>
                                        <span class={`pill pill-${item.status.toLowerCase()}`}>{item.status}</span>
                                      </div>
                                      <span class="change-item-suite">{item.suite}</span>
                                    </div>
                                    <span class="change-item-id" style={{ "font-weight": "700" }}>{item.id}</span>
                                    
                                    <Show when={item.message}>
                                      <div class="change-item-msg">{item.message}</div>
                                    </Show>

                                    <Show when={expandedRegressionId() === item.id}>
                                      <div style={{ "margin-top": "4px", "border-top": "1px solid rgba(255,255,255,0.06)", "padding-top": "8px", "display": "flex", "flex-direction": "column", gap: "6px" }}>
                                        <Show when={item.description}>
                                          <p style={{ margin: "0", "font-size": "0.78rem", color: "var(--muted)", "line-height": "1.4", "font-style": "italic" }}>
                                            {item.description}
                                          </p>
                                        </Show>
                                        <Show when={item.query} fallback={<p style={{ margin: "0", "font-size": "0.75rem", color: "var(--muted)" }}>No query text available</p>}>
                                          <div style={{ display: "flex", "flex-direction": "column", gap: "6px", width: "100%" }}>
                                            <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: "10px", "border-radius": "6px", overflow: "auto" }}>
                                              <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#a5b4fc", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                                {item.query}
                                              </pre>
                                            </div>
                                            <Show when={item.expected}>
                                              <div style={{ display: "flex", "flex-direction": "column", gap: "2px" }}>
                                                <span style={{ "font-size": "0.7rem", color: "var(--muted)", "font-weight": "600", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Expected Result:</span>
                                                <div style={{ background: "#1e293b", border: "1px solid #334155", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                                  <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#cbd5e1", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                                    {item.expected}
                                                  </pre>
                                                </div>
                                              </div>
                                            </Show>
                                          </div>
                                        </Show>
                                      </div>
                                    </Show>
                                    
                                    <button 
                                      class="btn-clear" 
                                      style={{ padding: "6px 12px", "font-size": "0.78rem", "align-self": "flex-end", "margin-top": "4px" }}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleCopyCommand(singleTestRerun(), item.id);
                                      }}
                                    >
                                      <Show when={isCopied()} fallback={<><Play size={10} /> Copy Rerun Command</>}>
                                        <Check size={10} /> Copied Rerun Command
                                      </Show>
                                    </button>
                                  </div>
                              );
                            }}
                          </For>
                        </div>
                      </Show>
                    </div>

                    {/* Improvements list */}
                    <div class="panel">
                      <div class="change-panel-header">
                        <h3>Newly Fixed Cases</h3>
                        <span class="change-panel-count change-panel-count-improvement">
                          {filteredImprovements().length}
                        </span>
                      </div>

                      <Show 
                        when={filteredImprovements().length > 0}
                        fallback={
                          <div class="empty-state">
                            <AlertCircle size={32} />
                            <h3>No Improvements Registered</h3>
                            <p>No tests regressed but none were newly resolved vs baseline either.</p>
                          </div>
                        }
                      >
                        <div class="change-list">
                          <For each={filteredImprovements()}>
                            {(item) => (
                              <div class="change-item">
                                <div class="change-item-top">
                                  <span class="pill pill-pass">FIXED</span>
                                  <span class="change-item-suite">{item.suite}</span>
                                </div>
                                <span class="change-item-id">{item.id}</span>
                              </div>
                            )}
                          </For>
                        </div>
                      </Show>
                    </div>
                  </div>
                </Show>
              </>
            );
          }}
        </Show>
      </main>
    </div>
  );
}
