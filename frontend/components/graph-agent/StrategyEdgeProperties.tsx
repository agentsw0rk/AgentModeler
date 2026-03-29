"use client";

import CodeEditor from "@/components/shared/CodeEditor";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyEdge, GraphEdgeType } from "@/types/graph-agent";

export function StrategyEdgeProperties({
  edge,
  onUpdate,
}: {
  edge: GraphStrategyEdge;
  onUpdate: (data: Partial<GraphStrategyEdge>) => void;
}) {
  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Edge Type</Label>
        <Select value={edge.edgeType} onValueChange={(v) => onUpdate({ edgeType: v as GraphEdgeType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {edge.edgeType === "onCondition" && (
        <>
          <div>
            <Label>Condition</Label>
            <CodeEditor
              value={edge.condition ?? ""}
              onChange={(v) => onUpdate({ condition: v })}
              height="80px"
            />
          </div>
          <div>
            <Label>Transformation (optional)</Label>
            <CodeEditor
              value={edge.transformation ?? ""}
              onChange={(v) => onUpdate({ transformation: v || undefined })}
              height="80px"
            />
          </div>
        </>
      )}
    </div>
  );
}
