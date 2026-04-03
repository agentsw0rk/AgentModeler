"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { AgentConfig, ModelProvider } from "@/types/koog";
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
      <div>
        <Label>Model Provider</Label>
        <Select value={data.modelProvider} onValueChange={(v) => update({ modelProvider: v as ModelProvider })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="openai">OpenAI</SelectItem>
            <SelectItem value="anthropic">Anthropic</SelectItem>
            <SelectItem value="google">Google</SelectItem>
            <SelectItem value="bedrock">AWS Bedrock</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label htmlFor="agent-model-id">Model ID (optional)</Label>
        <Input id="agent-model-id" value={data.modelId || ""} onChange={(e) => update({ modelId: e.target.value })} placeholder="gpt-4o" />
      </div>
      <div>
        <Label htmlFor="agent-temperature">Temperature (optional)</Label>
        <Input id="agent-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
      <A2AConfigSection nodeId={nodeId} a2aConfig={data.a2aConfig} />
    </div>
  );
}
