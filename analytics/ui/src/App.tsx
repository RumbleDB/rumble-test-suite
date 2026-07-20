import { Show, createSignal, onMount } from "solid-js";
import type { AnalysisPayload, ParserMode, StatusFilter, ViewModel } from "./lib/analysis";
import { buildViewModel, findIssueKeyForCase } from "./lib/analysis";
import { ShieldAlert } from "./components/Icons";
import type { TabType } from "./components/HeaderNav";
import { HeaderNav } from "./components/HeaderNav";
import { OverviewTab } from "./components/OverviewTab";
import { SuitesTab } from "./components/SuitesTab";
import { IssuesTab } from "./components/IssuesTab";
import { ChangesTab } from "./components/ChangesTab";

function readEmbeddedAnalysis(): AnalysisPayload {
  const payload = document.getElementById("initial-analysis-data");
  if (!payload?.textContent) {
    throw new Error("This report does not contain embedded analysis data.");
  }
  return JSON.parse(payload.textContent) as AnalysisPayload;
}

export default function App() {
  const [viewModel, setViewModel] = createSignal<ViewModel | null>(null);
  const [loadError, setLoadError] = createSignal("");
  const [activeTab, setActiveTab] = createSignal<TabType>("overview");
  
  // Filtering and selection states
  const [activeSuite, setActiveSuite] = createSignal("ALL");
  const [activeStatus, setActiveStatus] = createSignal<StatusFilter>("ALL");
  const [searchQuery, setSearchQuery] = createSignal("");
  const [sortBy, setSortBy] = createSignal<"count" | "count-asc" | "suite" | "message">("count");
  
  // Issue explorer / diagnostic states
  const [selectedIssueKey, setSelectedIssueKey] = createSignal<string | null>(null);
  const [copiedKey, setCopiedKey] = createSignal<string | null>(null);

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

  // Handle active suite selection (navigate to suites tab)
  const handleSelectSuite = (suite: string) => {
    setActiveSuite(suite);
    setActiveTab("suites");
  };

  // Handle active issue selection (navigate to issues tab)
  const handleSelectIssue = (key: string) => {
    setSelectedIssueKey(key);
    setActiveTab("issues");
  };

  // Handle direct navigation to issue from Changes tab
  const handleJumpToIssue = (suiteName: string, caseId: string) => {
    setActiveSuite(suiteName);
    const model = viewModel();
    if (model) {
      const issueKey = findIssueKeyForCase(model, suiteName, caseId);
      if (issueKey) {
        setSelectedIssueKey(issueKey);
      }
    }
    setActiveTab("issues");
  };

  // Handle active status selection from health legend (navigate to issues tab)
  const handleSelectStatus = (status: StatusFilter) => {
    setActiveStatus(status);
    setActiveTab("issues");
  };

  // Copy command helper
  const handleCopyCommand = async (command: string, key: string) => {
    await navigator.clipboard.writeText(command);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  return (
    <div class="page-shell-topnav">
      <Show
        when={viewModel()}
        fallback={
          <div class="page-center" style={{ width: "100%", display: "flex", "justify-content": "center", "align-items": "center", height: "100vh" }}>
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

          return (
            <>
              {/* TOP NAVIGATION HEADER */}
              <HeaderNav
                viewModel={model}
                activeTab={activeTab()}
                setActiveTab={setActiveTab}
              />

              {/* MAIN CONTENT AREA */}
              <main class="main-content-topnav">
                <div class="page-body-topnav">
                  <Show when={activeTab() === "overview"}>
                    <OverviewTab
                      viewModel={model}
                      activeSuite={activeSuite()}
                      onSelectSuite={handleSelectSuite}
                      onSelectIssue={handleSelectIssue}
                      onSelectStatus={handleSelectStatus}
                      onViewAllChanges={() => setActiveTab("changes")}
                    />
                  </Show>

                  <Show when={activeTab() === "suites"}>
                    <SuitesTab
                      viewModel={model}
                      activeSuite={activeSuite()}
                      onSelectSuite={handleSelectSuite}
                      onViewIssues={() => setActiveTab("issues")}
                    />
                  </Show>

                  <Show when={activeTab() === "issues"}>
                    <IssuesTab
                      viewModel={model}
                      activeSuite={activeSuite()}
                      setActiveSuite={setActiveSuite}
                      activeStatus={activeStatus()}
                      setActiveStatus={setActiveStatus}
                      searchQuery={searchQuery()}
                      setSearchQuery={setSearchQuery}
                      sortBy={sortBy()}
                      setSortBy={setSortBy}
                      selectedIssueKey={selectedIssueKey()}
                      setSelectedIssueKey={setSelectedIssueKey}
                      copiedKey={copiedKey()}
                      handleCopyCommand={handleCopyCommand}
                    />
                  </Show>

                  <Show when={activeTab() === "changes"}>
                    <ChangesTab
                      viewModel={model}
                      activeSuite={activeSuite()}
                      activeStatus={activeStatus()}
                      searchQuery={searchQuery()}
                      setSearchQuery={setSearchQuery}
                      copiedKey={copiedKey()}
                      handleCopyCommand={handleCopyCommand}
                    />
                  </Show>
                </div>
              </main>
            </>
          );
        }}
      </Show>
    </div>
  );
}
