"use client";

import { useEditorStore } from "@/store/editorStore";
import { AgentProperties } from "./AgentProperties";
import { ToolProperties } from "./ToolProperties";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { BuiltinToolProperties } from "./BuiltinToolProperties";
import { BuiltinToolConfig } from "@/types/graph";

export function PropertiesPanel() {
  const selectedNodeId = useEditorStore((s) => s.selectedNodeId);
  const nodes = useEditorStore((s) => s.nodes);
  const selectedNode = nodes.find((n) => n.id === selectedNodeId);

  if (!selectedNode) {
    return (
      <div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">
        Select a node to edit its properties.
      </div>
    );
  }

  return (
    <ScrollArea className="w-80 border-l bg-background">
      <div className="p-4">
        <h3 className="font-semibold mb-4 capitalize">
          {selectedNode.type === "builtin-tool" ? "Built-in Tool" : selectedNode.type} Properties
        </h3>
        {selectedNode.type === "agent" && (
          <AgentProperties nodeId={selectedNode.id} data={selectedNode.data as AgentConfig} />
        )}
        {selectedNode.type === "tool" && (
          <ToolProperties nodeId={selectedNode.id} data={selectedNode.data as ToolConfig} />
        )}
        {selectedNode.type === "builtin-tool" && (
          <BuiltinToolProperties nodeId={selectedNode.id} data={selectedNode.data as BuiltinToolConfig} />
        )}
      </div>
    </ScrollArea>
  );
}
