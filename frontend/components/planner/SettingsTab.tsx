import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PlannerAgentConfig } from "@/types/planner";
import { ModelProvider } from "@/types/koog";

export function SettingsTab({
  data,
  onUpdate,
}: {
  data: PlannerAgentConfig;
  onUpdate: (field: Partial<PlannerAgentConfig>) => void;
}) {
  return (
    <div className="space-y-4">
      <div>
        <Label>Planner Type</Label>
        <Select value={data.plannerType} onValueChange={(v) => onUpdate({ plannerType: v as "llm" | "goap" })}>
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
            <Select value={data.modelProvider} onValueChange={(v) => onUpdate({ modelProvider: v as ModelProvider })}>
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
            <Input
              value={data.modelId ?? ""}
              onChange={(e) => onUpdate({ modelId: e.target.value || undefined })}
              placeholder="claude-sonnet-4"
            />
          </div>
          <div>
            <Label>Temperature</Label>
            <Input
              type="number"
              step="0.1"
              min="0"
              max="2"
              value={data.temperature ?? ""}
              onChange={(e) => onUpdate({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })}
              placeholder="0.7"
            />
          </div>
        </>
      )}
      <div>
        <Label>Max Replan Attempts</Label>
        <Input
          type="number"
          min="0"
          max="10"
          value={data.maxReplanAttempts}
          onChange={(e) => onUpdate({ maxReplanAttempts: parseInt(e.target.value) || 3 })}
        />
      </div>
    </div>
  );
}
