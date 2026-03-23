"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { SubAgentConfig, DelegationMode } from "@/types/subagent";
import type { ModelProvider } from "@/types/koog";

export function SubAgentProperties({ nodeId, data }: { nodeId: string; data: SubAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<SubAgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="sa-name">Name</Label>
        <Input id="sa-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="ResearchAgent" />
      </div>
      <div>
        <Label htmlFor="sa-description">Description</Label>
        <Textarea id="sa-description" value={data.description} onChange={(e) => update({ description: e.target.value })} placeholder="Describes what this Sub-Agent can do" rows={2} />
      </div>
      <div>
        <Label htmlFor="sa-prompt">System Prompt</Label>
        <Textarea id="sa-prompt" value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} placeholder="You are a specialist in..." rows={4} />
      </div>
      <div>
        <Label htmlFor="sa-input-desc">Input Description</Label>
        <Input id="sa-input-desc" value={data.inputDescription} onChange={(e) => update({ inputDescription: e.target.value })} placeholder="What does this Sub-Agent expect as input?" />
      </div>
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
        <Label htmlFor="sa-model-id">Model ID (optional)</Label>
        <Input id="sa-model-id" value={data.modelId || ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="gpt-4o" />
      </div>
      <div>
        <Label htmlFor="sa-temperature">Temperature (optional)</Label>
        <Input id="sa-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
      <div>
        <Label>Delegation Mode</Label>
        <Select value={data.delegationMode} onValueChange={(v) => update({ delegationMode: v as DelegationMode })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="automatic">Automatic</SelectItem>
            <SelectItem value="manual" disabled>Manual (coming soon)</SelectItem>
            <SelectItem value="fallback" disabled>Fallback (coming soon)</SelectItem>
            <SelectItem value="specialized" disabled>Specialized (coming soon)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
