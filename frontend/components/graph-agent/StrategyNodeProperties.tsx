"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import CodeEditor from "@/components/shared/CodeEditor";
import type { GraphStrategyNode } from "@/types/graph-agent";

const TYPE_OPTIONS = ["String", "Message.Response", "ReceivedToolResult", "List<Message.Response>", "Any"];

function TypeSelect({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const isCustom = !TYPE_OPTIONS.includes(value) && value !== "";
  const [showCustom, setShowCustom] = useState(isCustom);

  return (
    <div>
      <Label>{label}</Label>
      <Select
        value={showCustom ? "__custom__" : value}
        onValueChange={(v) => {
          if (v === "__custom__") {
            setShowCustom(true);
            onChange("");
          } else {
            setShowCustom(false);
            onChange(v ?? "");
          }
        }}
      >
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          {TYPE_OPTIONS.map((t) => (
            <SelectItem key={t} value={t}>{t}</SelectItem>
          ))}
          <SelectItem value="__custom__">Custom...</SelectItem>
        </SelectContent>
      </Select>
      {showCustom && (
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="e.g. MyDataClass"
          className="mt-1 h-7 text-xs"
        />
      )}
    </div>
  );
}

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

      {node.type === "node-execute-multiple-tools" && (
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
          <CodeEditor
            value={d.condition ?? ""}
            onChange={(v) => onUpdate({ condition: v })}
            height="80px"
          />
        </div>
      )}

      {node.type === "node-llm-request" && (
        <div>
          <Label>Temperature Override</Label>
          <Input type="number" step="0.1" min="0" max="2" value={d.temperature ?? ""} onChange={(e) => onUpdate({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="Default" />
        </div>
      )}

      {node.type === "node-compress-history" && (
        <div>
          <Label>Compression Strategy</Label>
          <Input value={d.compressionStrategy ?? ""} onChange={(e) => onUpdate({ compressionStrategy: e.target.value })} placeholder="summarize" />
        </div>
      )}

      {node.type === "node-llm-structured" && (
        <div>
          <Label>Output Schema (Type)</Label>
          <CodeEditor
            value={d.outputSchema ?? ""}
            onChange={(v) => onUpdate({ outputSchema: v })}
            height="80px"
          />
        </div>
      )}

      {node.type === "node-custom" && (
        <>
          <TypeSelect
            label="Input Type"
            value={d.inputType ?? "String"}
            onChange={(v) => onUpdate({ inputType: v })}
          />
          <TypeSelect
            label="Output Type"
            value={d.outputType ?? "String"}
            onChange={(v) => onUpdate({ outputType: v })}
          />
          <div>
            <Label>Body</Label>
            <CodeEditor
              value={d.body ?? ""}
              onChange={(v) => onUpdate({ body: v })}
              height="200px"
            />
          </div>
        </>
      )}
    </div>
  );
}
