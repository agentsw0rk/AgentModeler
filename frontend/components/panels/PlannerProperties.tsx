import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { PlannerAgentConfig } from "@/types/planner";
import { ModelProvider } from "@/types/koog";
import { useState } from "react";
import { PlannerConfigPanel } from "@/components/planner/PlannerConfigPanel";

export function PlannerProperties({ nodeId, data }: { nodeId: string; data: PlannerAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<PlannerAgentConfig>) => updateNodeData(nodeId, field);
  const [configOpen, setConfigOpen] = useState(false);

  return (
    <>
      <div className="space-y-4 p-4">
        <div>
          <Label>Name</Label>
          <Input value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyPlannerAgent" />
        </div>
        <div>
          <Label>Description</Label>
          <Textarea value={data.description} onChange={(e) => update({ description: e.target.value })} rows={3} placeholder="Describe what this planner agent does..." />
        </div>
        <div>
          <Label>Planner Type</Label>
          <Select value={data.plannerType} onValueChange={(v) => update({ plannerType: v as "llm" | "goap" })}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="llm">LLM Planner</SelectItem>
              <SelectItem value="goap">GOAP Planner</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {data.plannerType === "llm" && (
          <>
            <div>
              <Label>Model Provider</Label>
              <Select value={data.modelProvider} onValueChange={(v) => update({ modelProvider: v as ModelProvider })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="openai">OpenAI</SelectItem>
                  <SelectItem value="anthropic">Anthropic</SelectItem>
                  <SelectItem value="google">Google</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label>Model ID</Label>
              <Input value={data.modelId ?? ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="claude-sonnet-4" />
            </div>
          </>
        )}
        <Button variant="outline" className="w-full" onClick={() => setConfigOpen(true)}>
          Configure Goals, Actions & State
        </Button>
      </div>
      {configOpen && (
        <PlannerConfigPanel nodeId={nodeId} data={data} onClose={() => setConfigOpen(false)} />
      )}
    </>
  );
}
