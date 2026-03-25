import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { PlannerAgentConfig } from "@/types/planner";
import { GoalsTab } from "./GoalsTab";
import { ActionsTab } from "./ActionsTab";
import { StateTab } from "./StateTab";
import { SettingsTab } from "./SettingsTab";
import { XIcon } from "lucide-react";

type TabId = "goals" | "actions" | "state" | "settings";

export function PlannerConfigPanel({
  nodeId,
  data,
  onClose,
}: {
  nodeId: string;
  data: PlannerAgentConfig;
  onClose: () => void;
}) {
  const [activeTab, setActiveTab] = useState<TabId>("goals");
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<PlannerAgentConfig>) => updateNodeData(nodeId, field);

  const tabs: { id: TabId; label: string }[] = [
    { id: "goals", label: "Goals" },
    { id: "actions", label: "Actions" },
    { id: "state", label: "State" },
    { id: "settings", label: "Settings" },
  ];

  return (
    <div className="fixed inset-y-0 right-0 w-[55%] bg-background border-l-2 border-pink-500/40 z-50 flex flex-col shadow-2xl">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-sm font-semibold">Planner: {data.name || "Untitled"}</span>
        <Button variant="ghost" size="sm" onClick={onClose} className="h-7 w-7 p-0">
          <XIcon className="h-4 w-4" />
        </Button>
      </div>
      <div className="flex gap-1 px-4 pt-2">
        {tabs.map((tab) => (
          <Button
            key={tab.id}
            variant={activeTab === tab.id ? "default" : "ghost"}
            size="sm"
            onClick={() => setActiveTab(tab.id)}
            className="text-xs"
          >
            {tab.label}
          </Button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === "goals" && (
          <GoalsTab goals={data.goals} onChange={(goals) => update({ goals })} />
        )}
        {activeTab === "actions" && (
          <ActionsTab actions={data.actions} onChange={(actions) => update({ actions })} />
        )}
        {activeTab === "state" && (
          <StateTab entries={data.initialState} onChange={(initialState) => update({ initialState })} />
        )}
        {activeTab === "settings" && (
          <SettingsTab data={data} onUpdate={update} />
        )}
      </div>
    </div>
  );
}
