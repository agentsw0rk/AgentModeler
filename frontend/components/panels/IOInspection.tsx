"use client";

import { useEditorStore } from "@/store/editorStore";
import { Badge } from "@/components/ui/badge";
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";

const AGENT_TYPES = new Set(["agent", "sub-agent", "planner-agent", "graph-agent"]);

export function IOInspection({ nodeId }: { nodeId: string }) {
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const setSelectedNodeIds = useEditorStore((s) => s.setSelectedNodeIds);

  const node = nodes.find((n) => n.id === nodeId);
  if (!node || !AGENT_TYPES.has(node.type)) return null;

  // Input nodes connect TO this agent (input is source, agent is target)
  const inputNodeIds = edges.filter((e) => e.target === nodeId).map((e) => e.source);
  const inputs = nodes.filter((n) => inputNodeIds.includes(n.id) && n.type === "input");

  // Output nodes connect FROM this agent (agent is source, output is target)
  const outputNodeIds = edges.filter((e) => e.source === nodeId).map((e) => e.target);
  const outputs = nodes.filter((n) => outputNodeIds.includes(n.id) && n.type === "output");

  if (inputs.length === 0 && outputs.length === 0) return null;

  return (
    <div className="mt-4 pt-4 border-t space-y-3">
      {inputs.length > 0 && (
        <div>
          <div className="flex items-center gap-1.5 mb-1.5">
            <span className="text-xs font-medium text-muted-foreground">Inputs</span>
            <Badge variant="secondary" className="text-[10px] bg-cyan-500/20 text-cyan-400">{inputs.length}</Badge>
          </div>
          <div className="space-y-1">
            {inputs.map((n) => {
              const d = n.data as InputNodeConfig;
              const fieldCount = d.fields?.length ?? 0;
              const hasOptional = d.fields?.some((f) => f.optional) ?? false;
              return (
                <button
                  key={n.id}
                  onClick={() => setSelectedNodeIds([n.id])}
                  className="w-full flex items-center gap-1.5 text-xs px-2 py-1 rounded hover:bg-muted/50 text-left transition-colors"
                >
                  <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 shrink-0" />
                  <span className="truncate font-medium">{d.className || "Input"}</span>
                  <span className="text-muted-foreground ml-auto shrink-0">
                    {fieldCount} {fieldCount === 1 ? "field" : "fields"}
                  </span>
                  {hasOptional && (
                    <span className="text-[10px] text-muted-foreground shrink-0">opt</span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}
      {outputs.length > 0 && (
        <div>
          <div className="flex items-center gap-1.5 mb-1.5">
            <span className="text-xs font-medium text-muted-foreground">Outputs</span>
            <Badge variant="secondary" className="text-[10px] bg-orange-500/20 text-orange-400">{outputs.length}</Badge>
          </div>
          <div className="space-y-1">
            {outputs.map((n) => {
              const d = n.data as OutputNodeConfig;
              const fieldCount = d.fields?.length ?? 0;
              return (
                <button
                  key={n.id}
                  onClick={() => setSelectedNodeIds([n.id])}
                  className="w-full flex items-center gap-1.5 text-xs px-2 py-1 rounded hover:bg-muted/50 text-left transition-colors"
                >
                  <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shrink-0" />
                  <span className="truncate font-medium">{d.className || "Output"}</span>
                  <span className="text-muted-foreground ml-auto shrink-0">
                    {fieldCount} {fieldCount === 1 ? "field" : "fields"}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
