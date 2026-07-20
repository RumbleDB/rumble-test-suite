import { Show, createMemo, createSignal, For } from "solid-js";
import { Search, CheckCircle, AlertCircle, Play, Check, Copy, ExternalLink, ChevronDown, ChevronRight } from "./Icons";
import { HighlightText } from "./HighlightText";
import { decodeExpectedResult, getSingleTestCaseCommand } from "../lib/analysis";
import type { ViewModel, StatusFilter, ParserMode } from "../lib/analysis";

type ChangesTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  activeStatus: StatusFilter;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  copiedKey: string | null;
  handleCopyCommand: (command: string, key: string) => void;
  onJumpToIssue: (suite: string, caseId: string) => void;
};

export function ChangesTab(props: ChangesTabProps) {
  const [expandedRegressionId, setExpandedRegressionId] = createSignal<string | null>(null);
  const [copiedCodeKey, setCopiedCodeKey] = createSignal<string | null>(null);

  const copyText = (text: string, key: string, e: Event) => {
    e.stopPropagation();
    navigator.clipboard.writeText(text);
    setCopiedCodeKey(key);
    setTimeout(() => setCopiedCodeKey(null), 2000);
  };

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
      const matchText = [
        item.suite,
        item.status,
        item.id,
        item.message,
        item.detail,
        item.description,
        item.query,
        item.expected,
      ]
        .filter(Boolean)
        .join("\n")
        .toLowerCase();
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
            placeholder="Search regressions by ID, error cause, query text..."
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
        {/* Regressions Panel */}
        <div class="panel">
          <div class="change-panel-header">
            <div>
              <h3>New Regressions</h3>
              <p class="section-subtitle">Tests failing or erroring compared to baseline</p>
            </div>
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
                <p>All test baselines are passing in this scope.</p>
              </div>
            }
          >
            <div class="change-list">
              <For each={filteredRegressions()}>
                {(item) => {
                  const suiteObj = props.viewModel.suites.find((s) => s.name === item.suite);
                  const singleTestRerun = () => getSingleTestCaseCommand(item.suite, item.id, suiteObj?.parser);
                  const isCopiedCmd = () => props.copiedKey === item.id;
                  const isExpanded = () => expandedRegressionId() === item.id;

                  const toggleExpand = (e: Event) => {
                    e.stopPropagation();
                    setExpandedRegressionId(isExpanded() ? null : item.id);
                  };

                  return (
                    <div class="change-item">
                      {/* Header bar - Click toggles collapse */}
                      <div class="change-item-header" onClick={toggleExpand}>
                        <div class="change-item-header-left">
                          <button class="btn-expand-toggle" aria-label="Toggle details">
                            <Show when={isExpanded()} fallback={<ChevronRight size={14} />}>
                              <ChevronDown size={14} />
                            </Show>
                          </button>
                          <span class={`pill pill-${item.status.toLowerCase()}`}>{item.status}</span>
                          <span class="change-item-suite">
                            <HighlightText text={item.suite} query={props.searchQuery} />
                          </span>
                        </div>

                        <button
                          class="btn-jump-issue"
                          onClick={(e) => {
                            e.stopPropagation();
                            props.onJumpToIssue(item.suite, item.id);
                          }}
                          title="View in Failure Groups diagnostic view"
                        >
                          <ExternalLink size={12} /> View in Failure Groups
                        </button>
                      </div>

                      {/* Test Case ID */}
                      <div class="change-item-id-row" onClick={toggleExpand}>
                        <span class="change-item-id">
                          <HighlightText text={item.id} query={props.searchQuery} />
                        </span>
                      </div>

                      {/* Primary Error Message Callout */}
                      <Show when={item.message}>
                        <div class="change-item-msg-box" onClick={(e) => e.stopPropagation()}>
                          <div class="msg-box-header">
                            <span class="msg-box-label">Failure Cause:</span>
                            <Show when={item.type}>
                              <span class="pill pill-error" style={{ "font-size": "0.65rem", padding: "1px 6px" }}>
                                {item.type}
                              </span>
                            </Show>
                          </div>
                          <div class="msg-box-content">
                            <HighlightText text={item.message} query={props.searchQuery} />
                          </div>
                        </div>
                      </Show>

                      {/* Expanded Details Section - Text Selection Allowed without Collapsing */}
                      <Show when={isExpanded()}>
                        <div class="change-item-details" onClick={(e) => e.stopPropagation()}>
                          {/* Optional Test Description */}
                          <Show when={item.description}>
                            <div class="detail-block">
                              <span class="detail-label">Description</span>
                              <p class="detail-description">
                                <HighlightText text={item.description} query={props.searchQuery} />
                              </p>
                            </div>
                          </Show>

                          {/* Error Stack Trace / Cause Detail */}
                          <Show when={item.detail && item.detail !== item.message}>
                            <div class="detail-block">
                              <div class="detail-block-header">
                                <span class="detail-label">Full Stack Trace / Cause</span>
                                <button
                                  class="btn-copy-sm"
                                  onClick={(e) => copyText(item.detail || "", `trace-${item.id}`, e)}
                                >
                                  <Show when={copiedCodeKey() === `trace-${item.id}`} fallback={<><Copy size={11} /> Copy Trace</>}>
                                    <Check size={11} /> Copied!
                                  </Show>
                                </button>
                              </div>
                              <div class="code-box">
                                <pre class="code-pre trace-pre">
                                  <HighlightText text={item.detail || ""} query={props.searchQuery} />
                                </pre>
                              </div>
                            </div>
                          </Show>

                          {/* Test Query Code */}
                          <Show when={item.query}>
                            <div class="detail-block">
                              <div class="detail-block-header">
                                <span class="detail-label">Test Query</span>
                                <button
                                  class="btn-copy-sm"
                                  onClick={(e) => copyText(item.query || "", `query-${item.id}`, e)}
                                >
                                  <Show when={copiedCodeKey() === `query-${item.id}`} fallback={<><Copy size={11} /> Copy Query</>}>
                                    <Check size={11} /> Copied!
                                  </Show>
                                </button>
                              </div>
                              <div class="code-box">
                                <pre class="code-pre query-pre">
                                  <HighlightText text={item.query || ""} query={props.searchQuery} />
                                </pre>
                              </div>
                            </div>
                          </Show>

                          {/* Expected Result Code */}
                          <Show when={item.expected}>
                            <div class="detail-block">
                              <div class="detail-block-header">
                                <span class="detail-label">Expected Result</span>
                                <button
                                  class="btn-copy-sm"
                                  onClick={(e) => copyText(decodeExpectedResult(item.expected) || "", `exp-${item.id}`, e)}
                                >
                                  <Show when={copiedCodeKey() === `exp-${item.id}`} fallback={<><Copy size={11} /> Copy Expected</>}>
                                    <Check size={11} /> Copied!
                                  </Show>
                                </button>
                              </div>
                              <div class="code-box">
                                <pre class="code-pre expected-pre">
                                  <HighlightText text={decodeExpectedResult(item.expected) || ""} query={props.searchQuery} />
                                </pre>
                              </div>
                            </div>
                          </Show>

                          {/* Single Test Rerun Command */}
                          <div class="detail-block">
                            <span class="detail-label">Single Test Rerun Command</span>
                            <div class="rerun-cmd-box">
                              <code class="rerun-cmd-text">{singleTestRerun()}</code>
                              <button
                                class="btn-copy-rerun"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  props.handleCopyCommand(singleTestRerun(), item.id);
                                }}
                              >
                                <Show when={isCopiedCmd()} fallback={<><Play size={12} /> Copy Command</>}>
                                  <Check size={12} /> Copied!
                                </Show>
                              </button>
                            </div>
                          </div>
                        </div>
                      </Show>
                    </div>
                  );
                }}
              </For>
            </div>
          </Show>
        </div>

        {/* Improvements Panel */}
        <div class="panel">
          <div class="change-panel-header">
            <div>
              <h3>Newly Fixed Cases</h3>
              <p class="section-subtitle">Tests passing now that failed in baseline</p>
            </div>
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
                <p>No tests were resolved compared to baseline in this run.</p>
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
