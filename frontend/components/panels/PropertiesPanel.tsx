"use client";

import { useEditorStore } from "@/store/editorStore";
import { AgentProperties } from "./AgentProperties";
import { ToolProperties } from "./ToolProperties";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { BuiltinToolProperties } from "./BuiltinToolProperties";
import { BuiltinToolConfig } from "@/types/graph";
import { SubAgentProperties } from "./SubAgentProperties";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerProperties } from "./McpServerProperties";
import { McpServerConfig } from "@/types/mcp";
import { PlannerProperties } from "./PlannerProperties";
import { PlannerAgentConfig } from "@/types/planner";
import { A2AAgentProperties } from "@/components/a2a/A2AAgentProperties";
import { A2AAgentConfig } from "@/types/a2a";
import { InputProperties } from "./InputProperties";
import { OutputProperties } from "./OutputProperties";
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
import { GraphAgentProperties } from "./GraphAgentProperties";
import { GraphAgentConfig } from "@/types/graph-agent";
import { IOInspection } from "./IOInspection";

export function PropertiesPanel() {
  const selectedNodeIds = useEditorStore((s) => s.selectedNodeIds);
  const nodes = useEditorStore((s) => s.nodes);

  if (selectedNodeIds.length === 0) {
    return (<div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">Select a node to edit its properties.</div>);
  }

  if (selectedNodeIds.length > 1) {
    return (<div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">{selectedNodeIds.length} nodes selected</div>);
  }

  const selectedNode = nodes.find((n) => n.id === selectedNodeIds[0]);

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
          {selectedNode.type === "mcp-server" ? "MCP Server" :
           selectedNode.type === "builtin-tool" ? "Built-in Tool" :
           selectedNode.type === "sub-agent" ? "Sub-Agent" :
           selectedNode.type === "a2a-agent" ? "A2A Agent" :
           selectedNode.type === "input" ? "Input" :
           selectedNode.type === "output" ? "Output" :
           selectedNode.type === "graph-agent" ? "Graph Agent" :
           selectedNode.type} Properties
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
        {selectedNode.type === "sub-agent" && (
          <SubAgentProperties nodeId={selectedNode.id} data={selectedNode.data as SubAgentConfig} />
        )}
        {selectedNode.type === "mcp-server" && (
          <McpServerProperties nodeId={selectedNode.id} data={selectedNode.data as McpServerConfig} />
        )}
        {selectedNode.type === "planner-agent" && (
          <PlannerProperties nodeId={selectedNode.id} data={selectedNode.data as PlannerAgentConfig} />
        )}
        {selectedNode.type === "a2a-agent" && (
          <A2AAgentProperties nodeId={selectedNode.id} data={selectedNode.data as A2AAgentConfig} />
        )}
        {selectedNode.type === "input" && (
          <InputProperties nodeId={selectedNode.id} data={selectedNode.data as InputNodeConfig} />
        )}
        {selectedNode.type === "output" && (
          <OutputProperties nodeId={selectedNode.id} data={selectedNode.data as OutputNodeConfig} />
        )}
        {selectedNode.type === "graph-agent" && (
          <GraphAgentProperties nodeId={selectedNode.id} data={selectedNode.data as GraphAgentConfig} />
        )}
        <IOInspection nodeId={selectedNode.id} />
      </div>
    </ScrollArea>
  );
}
