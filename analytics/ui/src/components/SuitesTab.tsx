import { SuiteTable } from "./SuiteTable";
import { SuiteDetailsPanel } from "./SuiteDetailsPanel";
import type { ViewModel } from "../lib/analysis";

type SuitesTabProps = {
  viewModel: ViewModel;
  activeSuite: string;
  onSelectSuite: (name: string) => void;
  onViewIssues: () => void;
};

export function SuitesTab(props: SuitesTabProps) {
  // Determine which suite to display details for
  const currentSuite = () => {
    const suites = props.viewModel.suites;
    if (props.activeSuite === "ALL") {
      return suites.length > 0 ? suites[0] : undefined;
    }
    return suites.find((s) => s.name === props.activeSuite);
  };

  return (
    <div class="tab-content-animate">
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
              suites={props.viewModel.suites}
              activeSuite={props.activeSuite}
              onSelectSuite={props.onSelectSuite}
            />
          </section>
        </div>

        <div class="column" style={{ flex: "1" }}>
          <SuiteDetailsPanel
            suite={currentSuite()}
            onViewIssues={props.onViewIssues}
          />
        </div>
      </div>
    </div>
  );
}
