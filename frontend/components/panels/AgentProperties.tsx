"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ModelFields } from "@/components/shared/ModelFields";
import { useEditorStore } from "@/store/editorStore";
import { AgentConfig } from "@/types/koog";
import { A2AConfigSection } from "@/components/a2a/A2AConfigSection";

export function AgentProperties({ nodeId, data }: { nodeId: string; data: AgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<AgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="agent-name">Name</Label>
        <Input id="agent-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyAgent" />
      </div>
      <div>
        <Label htmlFor="agent-prompt">System Prompt</Label>
        <Textarea id="agent-prompt" value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} placeholder="You are a helpful assistant." rows={4} />
      </div>
      <ModelFields provider={data.modelProvider} modelId={data.modelId} onChange={update} />
      <div>
        <Label htmlFor="agent-temperature">Temperature (optional)</Label>
        <Input id="agent-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
      <A2AConfigSection nodeId={nodeId} a2aConfig={data.a2aConfig} />
    </div>
  );
}
