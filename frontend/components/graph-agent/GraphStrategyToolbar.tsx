"use client";

import { Button } from "@/components/ui/button";
import type { GraphStrategyNodeType } from "@/types/graph-agent";

const nodeButtons: { type: GraphStrategyNodeType; label: string; color: string }[] = [
  { type: "node-llm-request", label: "LLM Request", color: "text-blue-400" },
  { type: "node-execute-tool", label: "Execute Tool", color: "text-emerald-400" },
  { type: "node-llm-send-tool-result", label: "Send Tool Result", color: "text-yellow-400" },
  { type: "node-condition", label: "Condition", color: "text-purple-400" },
];

export function GraphStrategyToolbar({ onAddNode }: { onAddNode: (type: GraphStrategyNodeType) => void }) {
  return (
    <div className="flex items-center gap-2 px-4 py-2 border-b border-border">
      {nodeButtons.map((btn) => (
        <Button key={btn.type} variant="outline" size="sm" onClick={() => onAddNode(btn.type)}>
          <span className={`mr-1 ${btn.color}`}>+</span>
          {btn.label}
        </Button>
      ))}
    </div>
  );
}
