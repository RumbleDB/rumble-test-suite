import { Show, createMemo, createSignal, For } from "solid-js";
import { Search, CheckCircle, AlertCircle, Play, Check } from "./Icons";
import { HighlightText } from "./HighlightText";
import { getSingleTestCaseCommand } from "../lib/analysis";
import type { ViewModel, StatusFilter, ParserMode } from "../lib/analysis";

type ChangesTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  activeStatus: StatusFilter;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  parserMode: ParserMode;
  copiedKey: string | null;
  handleCopyCommand: (command: string, key: string) => void;
};

export function ChangesTab(props: ChangesTabProps) {
  const [expandedRegressionId, setExpandedRegressionId] = createSignal<string | null>(null);

  // Filtered regressions & improvements
  const filteredRegressions = createMemo(() => {
    const query = props.searchQuery.trim().toLowerCase();
    return (props.viewModel.regressions ?? []).filter((item) => {
      if (props.activeStatus !== "ALL" && item.status !== props.activeStatus) {
        return false;
      }
      if (props.activeSuite !== "ALL" && item.suite !== props.activeSuite) {
        return false;
      }
      if (!query) {
        return true;
      }
      const matchText = [item.suite, item.status, item.id, item.message, item.description, item.query, item.expected].filter(Boolean).join("\n").toLowerCase();
      return matchText.includes(query);
    });
  });

  const filteredImprovements = createMemo(() => {
    const query = props.searchQuery.trim().toLowerCase();
    return (props.viewModel.improvements ?? []).filter((item) => {
      if (props.activeSuite !== "ALL" && item.suite !== props.activeSuite) {
        return false;
      }
      if (!query) {
        return true;
      }
      const matchText = [item.suite, item.id].filter(Boolean).join("\n").toLowerCase();
      return matchText.includes(query);
    });
  });

  return (
    <div class="tab-content-animate" style={{ display: "flex", "flex-direction": "column", gap: "16px" }}>
      {/* Search & Filters Toolbar */}
      <div class="toolbar">
        <div class="search-input-wrapper">
          <Search size={14} />
          <input
            type="text"
            class="search-input"
            placeholder="Search regressions, improvements..."
            value={props.searchQuery}
            onInput={(e) => props.setSearchQuery(e.currentTarget.value)}
          />
        </div>
        <button
          class="btn-clear"
          disabled={props.searchQuery.trim() === ""}
          onClick={() => props.setSearchQuery("")}
        >
          Clear Search
        </button>
      </div>

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
                  const singleTestRerun = () => getSingleTestCaseCommand(item.suite, item.id, props.parserMode);
                  const isCopied = () => props.copiedKey === item.id;

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
                        <span class="change-item-suite">
                          <HighlightText text={item.suite} query={props.searchQuery} />
                        </span>
                      </div>
                      <span class="change-item-id" style={{ "font-weight": "700" }}>
                        <HighlightText text={item.id} query={props.searchQuery} />
                      </span>

                      <Show when={item.message}>
                        <div class="change-item-msg">
                          <HighlightText text={item.message} query={props.searchQuery} />
                        </div>
                      </Show>

                      <Show when={expandedRegressionId() === item.id}>
                        <div style={{ "margin-top": "4px", "border-top": "1px solid rgba(255,255,255,0.06)", "padding-top": "8px", "display": "flex", "flex-direction": "column", gap: "6px" }}>
                          <Show when={item.description}>
                            <p style={{ margin: "0", "font-size": "0.78rem", color: "var(--muted)", "line-height": "1.4", "font-style": "italic" }}>
                              <HighlightText text={item.description} query={props.searchQuery} />
                            </p>
                          </Show>
                          <Show when={item.query} fallback={<p style={{ margin: "0", "font-size": "0.75rem", color: "var(--muted)" }}>No query text available</p>}>
                            <div style={{ display: "flex", "flex-direction": "column", gap: "6px", width: "100%" }}>
                              <div style={{ background: "#0f172a", border: "1px solid #1e293b", padding: "10px", "border-radius": "6px", overflow: "auto" }}>
                                <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#a5b4fc", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                  <HighlightText text={item.query} query={props.searchQuery} />
                                </pre>
                              </div>
                              <Show when={item.expected}>
                                <div style={{ display: "flex", "flex-direction": "column", gap: "2px" }}>
                                  <span style={{ "font-size": "0.7rem", color: "var(--muted)", "font-weight": "600", "text-transform": "uppercase", "letter-spacing": "0.05em" }}>Expected Result:</span>
                                  <div style={{ background: "#1e293b", border: "1px solid #334155", padding: "8px 10px", "border-radius": "6px", overflow: "auto" }}>
                                    <pre style={{ margin: "0", "font-family": "var(--font-mono)", "font-size": "0.78rem", color: "#cbd5e1", "white-space": "pre-wrap", "word-break": "break-all" }}>
                                      <HighlightText text={item.expected} query={props.searchQuery} />
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
                          props.handleCopyCommand(singleTestRerun(), item.id);
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
                      <span class="change-item-suite">
                        <HighlightText text={item.suite} query={props.searchQuery} />
                      </span>
                    </div>
                    <span class="change-item-id">
                      <HighlightText text={item.id} query={props.searchQuery} />
                    </span>
                  </div>
                )}
              </For>
            </div>
          </Show>
        </div>
      </div>
    </div>
  );
}
