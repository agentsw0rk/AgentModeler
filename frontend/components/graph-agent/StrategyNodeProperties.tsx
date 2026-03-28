"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyNode } from "@/types/graph-agent";

export function StrategyNodeProperties({
  node,
  connectedTools,
  onUpdate,
}: {
  node: GraphStrategyNode;
  connectedTools: string[];
  onUpdate: (data: Partial<GraphStrategyNode["data"]>) => void;
}) {
  const d = node.data;

  if (node.type === "node-start" || node.type === "node-finish") {
    return <div className="text-sm text-muted-foreground p-4">No configurable properties for {node.type === "node-start" ? "Start" : "Finish"} node.</div>;
  }

  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Label</Label>
        <Input value={d.label ?? ""} onChange={(e) => onUpdate({ label: e.target.value })} placeholder="nodeName" />
      </div>

      {node.type === "node-execute-tool" && (
        <div>
          <Label>Tool</Label>
          {connectedTools.length > 0 ? (
            <Select value={d.toolName ?? ""} onValueChange={(v) => onUpdate({ toolName: v || undefined })}>
              <SelectTrigger><SelectValue placeholder="Select tool..." /></SelectTrigger>
              <SelectContent>
                {connectedTools.map((t) => (
                  <SelectItem key={t} value={t}>{t}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <p className="text-xs text-muted-foreground mt-1">
              No tools available. Connect Tool or Built-in Tool nodes to this Graph Agent in the main canvas first.
            </p>
          )}
        </div>
      )}

      {node.type === "node-condition" && (
        <div>
          <Label>Condition</Label>
          <Input value={d.condition ?? ""} onChange={(e) => onUpdate({ condition: e.target.value })} placeholder="result.isNotEmpty()" />
        </div>
      )}

      {node.type === "node-llm-request" && (
        <div>
          <Label>Temperature Override</Label>
          <Input type="number" step="0.1" min="0" max="2" value={d.temperature ?? ""} onChange={(e) => onUpdate({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="Default" />
        </div>
      )}
    </div>
  );
}
