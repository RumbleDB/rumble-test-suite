import { Show, createMemo, createSignal, For, createEffect } from "solid-js";
import { Search, AlertCircle, ChevronLeft, ChevronRight, Copy, Check, Play } from "./Icons";
import { HighlightText } from "./HighlightText";
import { getParserCommand, getSingleTestCaseCommand } from "../lib/analysis";
import type { ViewModel, StatusFilter, ParserMode, IssueRow } from "../lib/analysis";

type IssuesTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  setActiveSuite: (suite: string) => void;
  activeStatus: StatusFilter;
  setActiveStatus: (status: StatusFilter) => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  sortBy: "count" | "count-asc" | "suite" | "message";
  setSortBy: (sort: "count" | "count-asc" | "suite" | "message") => void;
  selectedIssueKey: string | null;
  setSelectedIssueKey: (key: string | null) => void;
  parserMode: ParserMode;
  setParserMode: (mode: ParserMode) => void;
  copiedKey: string | null;
  handleCopyCommand: (command: string, key: string) => void;
};

export function IssuesTab(props: IssuesTabProps) {
  // Derived filter options
  const suiteOptions = createMemo<string[]>(() => [
    "ALL",
    ...props.viewModel.suites.map((suite) => suite.name)
  ]);

  // Filtered issue rows
  const filteredIssues = createMemo(() => {
    const query = props.searchQuery.trim().toLowerCase();
    const list = props.viewModel.issueRows.filter((item) => {
      if (props.activeStatus !== "ALL" && item.status !== props.activeStatus) {
        return false;
      }
      if (props.activeSuite !== "ALL" && item.suite !== props.activeSuite) {
        return false;
      }
      if (!query) {
        return true;
      }
      const casesSearchText = item.cases.map(c => [c.id, c.description, c.query].filter(Boolean).join("\n")).join("\n");
      return [item.suite, item.status, item.message, casesSearchText].join("\n").toLowerCase().includes(query);
    });

    return [...list].sort((a, b) => {
      const mode = props.sortBy;
      if (mode === "count") {
        return b.count - a.count;
      } else if (mode === "count-asc") {
        return a.count - b.count;
      } else if (mode === "suite") {
        return a.suite.localeCompare(b.suite);
      } else if (mode === "message") {
        return a.message.localeCompare(b.message);
      }
      return 0;
    });
  });

  // Selected issue details
  const selectedIssue = createMemo(() => {
    const issues = filteredIssues();
    if (issues.length === 0) return null;
    return issues.find((item) => item.key === props.selectedIssueKey) ?? issues[0];
  });

  const handleClearFilters = () => {
    props.setActiveSuite("ALL");
    props.setActiveStatus("ALL");
    props.setSearchQuery("");
    props.setSortBy("count");
  };

  const hasActiveFilters = () => {
    return props.activeSuite !== "ALL" || props.activeStatus !== "ALL" || props.searchQuery.trim() !== "" || props.sortBy !== "count";
  };

  return (
    <div class="issues-view-wrapper tab-content-animate">
      {/* Search & Filters Toolbar */}
      <div class="toolbar">
        <div class="search-input-wrapper">
          <Search size={14} />
          <input
            type="text"
            class="search-input"
            placeholder="Search issues, messages..."
            value={props.searchQuery}
            onInput={(e) => props.setSearchQuery(e.currentTarget.value)}
          />
        </div>

        <select
          class="select-filter"
          value={props.activeSuite}
          onChange={(e) => props.setActiveSuite(e.currentTarget.value)}
        >
          <For each={suiteOptions()}>
            {(option) => <option value={option}>{option === "ALL" ? "All Suites" : option}</option>}
          </For>
        </select>

        <select
          class="select-filter"
          value={props.activeStatus}
          onChange={(e) => props.setActiveStatus(e.currentTarget.value as StatusFilter)}
        >
          <option value="ALL">All Statuses</option>
          <option value="PASS">Pass</option>
          <option value="FAIL">Fail</option>
          <option value="ERROR">Error</option>
          <option value="SKIP">Skip</option>
        </select>

        <select
          class="select-filter"
          value={props.sortBy}
          onChange={(e) => props.setSortBy(e.currentTarget.value as any)}
        >
          <option value="count">Sort: Cases (High → Low)</option>
          <option value="count-asc">Sort: Cases (Low → High)</option>
          <option value="suite">Sort: Suite Name</option>
          <option value="message">Sort: Exception Message</option>
        </select>

        <button
          class="btn-clear"
          disabled={!hasActiveFilters()}
          onClick={handleClearFilters}
        >
          Clear Filters
        </button>
      </div>

      {/* Split Layout Container */}
      <div class="issues-layout">
        {/* Left Column: Issues Card List */}
        <div class="issues-column-left">
          <div class="detail-section-title" style={{ "margin-bottom": "8px" }}>
            <span>Issues Matches</span>
            <span class="sidebar-badge" style={{ background: "var(--border)", color: "var(--ink)", "font-weight": "700" }}>
              {filteredIssues().length}
            </span>
          </div>

          <div class="panel" style={{ display: "flex", "flex-direction": "column", flex: 1, "min-height": 0, padding: "18px" }}>
            <div class="issues-list-container">
              <Show
                when={filteredIssues().length > 0}
                fallback={
                  <div class="panel empty-state">
                    <AlertCircle size={32} />
                    <h3>No issues match current filter</h3>
                    <p>Try clearing active search or selection filters.</p>
                  </div>
                }
              >
                <For each={filteredIssues()}>
                  {(issue) => (
                    <button
                      onClick={() => props.setSelectedIssueKey(issue.key)}
                      class="issue-card"
                      classList={{ "issue-card-active": selectedIssue()?.key === issue.key }}
                    >
                      <div class="issue-card-header">
                        <div class="issue-card-meta">
                          <span class={`pill pill-${issue.status.toLowerCase()}`}>{issue.status}</span>
                          <span class="pill pill-parser">{issue.parser}</span>
                          <span class="issue-card-suite">
                            <HighlightText text={issue.suite} query={props.searchQuery} />
                          </span>
                        </div>
                        <span class="issue-card-count">{issue.count} cases</span>
                      </div>
                      <div class="issue-card-msg">
                        <HighlightText text={issue.message} query={props.searchQuery} />
                      </div>
                    </button>
                  )}
                </For>
              </Show>
            </div>
          </div>
        </div>

        {/* Right Column: Diagnostics details */}
        <div class="issues-column-right">
          <div class="detail-section-title" style={{ "margin-bottom": "8px" }}>
            <span>Issue Diagnostics</span>
          </div>

          <Show
            when={selectedIssue()}
            keyed
            fallback={
              <div class="panel empty-state" style={{ height: "100%", display: "flex", "flex-direction": "column", "justify-content": "center", "align-items": "center" }}>
                <AlertCircle size={32} />
                <h3>No Issue Selected</h3>
                <p>Select an issue group from the left panel to begin diagnostic analysis.</p>
              </div>
            }
          >
            {(issue) => {
              // Interactive local search inside the affected test cases list
              const initialLocalSearch = () => {
                const q = props.searchQuery.trim().toLowerCase();
                if (!q) return "";
                const matchesAny = issue.cases.some(c =>
                  c.id.toLowerCase().includes(q) ||
                  (c.description && c.description.toLowerCase().includes(q)) ||
                  (c.query && c.query.toLowerCase().includes(q))
                );
                return matchesAny ? props.searchQuery : "";
              };

              const [affectedSearch, setAffectedSearch] = createSignal(initialLocalSearch());
              createEffect(() => {
                setAffectedSearch(initialLocalSearch());
              });

              const [page, setPage] = createSignal(1);
              const [expandedCaseId, setExpandedCaseId] = createSignal<string | null>(null);
              const itemsPerPage = 20;

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

              const rerunCommand = createMemo(() => getParserCommand(issue, props.parserMode));

              return (
                <div class="panel" style={{ display: "flex", "flex-direction": "column", gap: "18px", padding: "18px" }}>
                  {/* Error Signature Card header */}
                  <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                    <div style={{ display: "flex", "justify-content": "space-between", "align-items": "center" }}>
                      <div style={{ display: "flex", gap: "8px", "align-items": "center" }}>
                        <span style={{ "font-size": "0.72rem", "font-weight": "800", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>
                          Error Signature
                        </span>
                        <span class="pill pill-parser" style={{ "font-size": "0.62rem", padding: "1px 6px" }}>{issue.parser}</span>
                      </div>
                      <span style={{ "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "var(--muted)" }}>{issue.suite}</span>
                    </div>
                    <div class="detail-msg-box" style={{ "font-family": "var(--font-mono)", "font-size": "0.82rem", color: "#f8fafc", "word-break": "break-all", "white-space": "pre-wrap" }}>
                      {issue.message}
                    </div>
                  </div>

                  {/* Rerun suite command card */}
                  <div style={{ display: "flex", "flex-direction": "column", gap: "8px" }}>
                    <div style={{ display: "flex", "justify-content": "space-between", "align-items": "center" }}>
                      <span style={{ "font-size": "0.72rem", "font-weight": "800", "text-transform": "uppercase", "letter-spacing": "0.05em", color: "var(--muted)" }}>
                        Rerun Entire Suite
                      </span>
                      <div class="parser-selector" style={{ display: "flex", gap: "2px", background: "rgba(0,0,0,0.03)", padding: "2px", "border-radius": "6px", border: "1px solid var(--border)" }}>
                        <button
                          class="parser-btn"
                          classList={{ "parser-btn-active": props.parserMode === "jsoniq" }}
                          onClick={() => props.setParserMode("jsoniq")}
                        >
                          JSONiq
                        </button>
                        <button
                          class="parser-btn"
                          classList={{ "parser-btn-active": props.parserMode === "xquery" }}
                          onClick={() => props.setParserMode("xquery")}
                        >
                          XQuery
                        </button>
                        <button
                          class="parser-btn"
                          classList={{ "parser-btn-active": props.parserMode === "default" }}
                          onClick={() => props.setParserMode("default")}
                        >
                          Default
                        </button>
                      </div>
                    </div>
                    <div class="code-snippet-box">
                      <div class="code-snippet-text">
                        {rerunCommand()}
                      </div>
                      <button
                        onClick={() => props.handleCopyCommand(rerunCommand(), issue.key)}
                        class="code-snippet-btn"
                        style={{
                          color: props.copiedKey === issue.key ? "var(--pass)" : ""
                        }}
                        title="Copy command"
                      >
                        <Show when={props.copiedKey === issue.key} fallback={<Copy size={14} />}>
                          <Check size={14} />
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
                        fallback={
                          <div class="empty-state" style={{ padding: "20px" }}>
                            <h3>No Matching Testcases</h3>
                            <p style={{ "font-size": "0.78rem" }}>Try clearing or widening the filter query.</p>
                          </div>
                        }
                      >
                        {(c) => {
                          const singleTestRerun = () => getSingleTestCaseCommand(issue.suite, c.id, props.parserMode);
                          const isSingleCopied = () => props.copiedKey === c.id;

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
                                    <HighlightText text={c.id} query={affectedSearch()} />
                                  </span>
                                </div>
                                <button
                                  class="affected-rerun-btn"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    props.handleCopyCommand(singleTestRerun(), c.id);
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
                                      <HighlightText text={c.description} query={affectedSearch()} />
                                    </p>
                                  </Show>

                                  <Show when={c.status === "FAIL" && (c.message || c.detail)}>
                                    <div style={{ display: "flex", "flex-direction": "column", gap: "2px", "margin-bottom": "4px" }}>
                                      <span style={{ "font-size": "0.68rem", color: "var(--fail)", "font-weight": "800", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Failure Message:</span>
                                      <div style={{ background: "var(--fail-light)", border: "1px solid rgba(198, 40, 40, 0.15)", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                        <Show when={c.message}>
                                          <div style={{ "font-weight": "700", "font-size": "0.78rem", color: "var(--fail)", "margin-bottom": c.detail ? "4px" : "0", "white-space": "pre-wrap" }}>
                                            {c.message}
                                          </div>
                                        </Show>
                                        <Show when={c.detail}>
                                          <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.75rem", color: "var(--fail)", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                            {c.detail}
                                          </pre>
                                        </Show>
                                      </div>
                                    </div>
                                  </Show>

                                  <Show when={c.status === "ERROR" && (c.type || c.message || c.detail)}>
                                    <div style={{ display: "flex", "flex-direction": "column", gap: "2px", "margin-bottom": "4px" }}>
                                      <span style={{ "font-size": "0.68rem", color: "var(--error)", "font-weight": "800", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Error Details:</span>
                                      <div style={{ background: "var(--error-light)", border: "1px solid rgba(239, 108, 0, 0.15)", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                        <Show when={c.type || c.message}>
                                          <div style={{ "font-weight": "700", "font-size": "0.78rem", color: "var(--error)", "margin-bottom": c.detail ? "4px" : "0", "white-space": "pre-wrap" }}>
                                            {c.type}{c.message ? `: ${c.message}` : ""}
                                          </div>
                                        </Show>
                                        <Show when={c.detail}>
                                          <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.75rem", color: "var(--error)", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                            {c.detail}
                                          </pre>
                                        </Show>
                                      </div>
                                    </div>
                                  </Show>

                                  <Show when={c.status === "SKIP" && c.message}>
                                    <div style={{ display: "flex", "flex-direction": "column", gap: "2px", "margin-bottom": "4px" }}>
                                      <span style={{ "font-size": "0.68rem", color: "var(--skip)", "font-weight": "800", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Skip Reason:</span>
                                      <div style={{ background: "var(--skip-light)", border: "1px solid rgba(120, 144, 156, 0.15)", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                        <div style={{ "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "var(--skip)", "white-space": "pre-wrap" }}>
                                          {c.message}
                                        </div>
                                      </div>
                                    </div>
                                  </Show>

                                  <Show when={c.query} fallback={<p style={{ margin: "4px 0 0 0", "font-size": "0.75rem", color: "var(--muted)" }}>No query text available</p>}>
                                    <div style={{ display: "flex", "flex-direction": "column", gap: "6px", width: "100%" }}>
                                      <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: "10px", "border-radius": "6px", overflow: "auto" }}>
                                        <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#a5b4fc", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                          <HighlightText text={c.query} query={affectedSearch()} />
                                        </pre>
                                      </div>
                                      <Show when={c.expected}>
                                        <div style={{ display: "flex", "flex-direction": "column", gap: "2px" }}>
                                          <span style={{ "font-size": "0.7rem", color: "var(--muted)", "font-weight": "600", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Expected Result:</span>
                                          <div style={{ background: "#1e293b", border: "1px solid #334155", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                            <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#cbd5e1", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                              <HighlightText text={c.expected} query={affectedSearch()} />
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
  );
}
