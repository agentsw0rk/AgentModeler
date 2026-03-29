"use client";

import { Button } from "@/components/ui/button";
import type { GraphStrategyNodeType } from "@/types/graph-agent";

const nodeButtons: { type: GraphStrategyNodeType; label: string; color: string }[] = [
  { type: "node-llm-request", label: "LLM Request", color: "text-blue-400" },
  { type: "node-execute-tool", label: "Execute Tool", color: "text-emerald-400" },
  { type: "node-llm-send-tool-result", label: "Send Result", color: "text-yellow-400" },
  { type: "node-condition", label: "Condition", color: "text-purple-400" },
  { type: "node-execute-multiple-tools", label: "Exec Multi Tools", color: "text-teal-400" },
  { type: "node-send-multiple-results", label: "Send Multi Results", color: "text-amber-400" },
  { type: "node-compress-history", label: "Compress History", color: "text-slate-400" },
  { type: "node-llm-streaming", label: "LLM Streaming", color: "text-cyan-400" },
  { type: "node-llm-structured", label: "LLM Structured", color: "text-rose-400" },
  { type: "node-do-nothing", label: "Do Nothing", color: "text-gray-400" },
  { type: "node-custom", label: "Custom", color: "text-indigo-400" },
  { type: "node-subgraph", label: "Subgraph", color: "text-orange-400" },
];

export function GraphStrategyToolbar({ onAddNode, hideSubgraph }: { onAddNode: (type: GraphStrategyNodeType) => void; hideSubgraph?: boolean }) {
  return (
    <div className="flex items-center gap-2 px-4 py-2 border-b border-border">
      {nodeButtons.filter(btn => !(hideSubgraph && btn.type === "node-subgraph")).map((btn) => (
        <Button key={btn.type} variant="outline" size="sm" onClick={() => onAddNode(btn.type)}>
          <span className={`mr-1 ${btn.color}`}>+</span>
          {btn.label}
        </Button>
      ))}
    </div>
  );
}
