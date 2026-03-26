"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { A2AAgentConfig, A2AAuthType, A2ACommunicationMode } from "@/types/a2a";

export function A2AAgentProperties({ nodeId, data }: { nodeId: string; data: A2AAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<A2AAgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="a2a-name">Name</Label>
        <Input id="a2a-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="RemoteAgent" />
      </div>
      <div>
        <Label htmlFor="a2a-desc">Description</Label>
        <Textarea id="a2a-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What does this remote agent do?" />
      </div>
      <div>
        <Label htmlFor="a2a-url">URL</Label>
        <Input id="a2a-url" value={data.url} onChange={(e) => update({ url: e.target.value })} placeholder="http://remote-agent:8080/a2a" />
      </div>
      <div>
        <Label>Auth Type</Label>
        <Select value={data.authType} onValueChange={(v) => update({ authType: v as A2AAuthType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="none">None</SelectItem>
            <SelectItem value="bearer">Bearer Token</SelectItem>
            <SelectItem value="api-key">API Key</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {data.authType !== "none" && (
        <div>
          <Label htmlFor="a2a-token">{data.authType === "bearer" ? "Bearer Token" : "API Key"}</Label>
          <Input id="a2a-token" type="password" value={data.authToken || ""} onChange={(e) => update({ authToken: e.target.value })} placeholder="••••••••" />
        </div>
      )}
      <div>
        <Label htmlFor="a2a-timeout">Timeout (ms)</Label>
        <Input id="a2a-timeout" type="number" min="1000" step="1000" value={data.timeout} onChange={(e) => update({ timeout: parseInt(e.target.value) || 30000 })} />
      </div>
      <div>
        <Label>Communication Mode</Label>
        <Select value={data.communicationMode} onValueChange={(v) => update({ communicationMode: v as A2ACommunicationMode })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="message">Message (direct response)</SelectItem>
            <SelectItem value="task">Task (status tracking)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
