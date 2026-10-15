"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ModelFields } from "@/components/shared/ModelFields";
import { useEditorStore } from "@/store/editorStore";
import type { SubAgentConfig, DelegationMode } from "@/types/subagent";

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
      <ModelFields provider={data.modelProvider} modelId={data.modelId} onChange={update} />
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
