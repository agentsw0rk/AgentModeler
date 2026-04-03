import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { GraphAgentConfig } from "@/types/graph-agent";
import { ModelProvider } from "@/types/koog";
import { useState } from "react";
import { GraphStrategyEditor } from "@/components/graph-agent/GraphStrategyEditor";

export function GraphAgentProperties({ nodeId, data }: { nodeId: string; data: GraphAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<GraphAgentConfig>) => updateNodeData(nodeId, field);
  const [editorOpen, setEditorOpen] = useState(false);

  return (
    <>
      <div className="space-y-4">
        <div>
          <Label>Name</Label>
          <Input value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyGraphAgent" />
        </div>
        <div>
          <Label>Description</Label>
          <Textarea value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="Describe what this agent does..." />
        </div>
        <div>
          <Label>System Prompt</Label>
          <Textarea value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} rows={4} placeholder="You are a helpful assistant..." />
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
          <Label>Model ID</Label>
          <Input value={data.modelId ?? ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="claude-sonnet-4" />
        </div>
        <div>
          <Label>Temperature</Label>
          <Input type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="0.7" />
        </div>
        <div className="text-xs text-muted-foreground">
          {data.strategyNodes.length} strategy nodes, {data.strategyEdges.length} edges
        </div>
        <Button variant="outline" className="w-full" onClick={() => setEditorOpen(true)}>
          Edit Strategy Graph
        </Button>
      </div>
      {editorOpen && (
        <GraphStrategyEditor nodeId={nodeId} data={data} onClose={() => setEditorOpen(false)} />
      )}
    </>
  );
}
