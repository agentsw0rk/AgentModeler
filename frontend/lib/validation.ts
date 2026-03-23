import { ProjectGraph, BuiltinToolConfig, BUILTIN_TOOL_TYPES } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";

export interface ValidationError {
  nodeId: string;
  severity: "error" | "warning";
  message: string;
}

export function validateGraph(graph: ProjectGraph): ValidationError[] {
  const errors: ValidationError[] = [];

  for (const node of graph.nodes) {
    if (node.type === "agent") {
      const data = node.data as AgentConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Agent requires a name" });
      }
      if (!data.systemPrompt.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Agent requires a system prompt" });
      }
    }

    if (node.type === "tool") {
      const data = node.data as ToolConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Tool requires a name" });
      }

      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "Tool must be connected to an Agent" });
      }

      for (const param of data.parameters) {
        if (!param.name.trim()) {
          errors.push({ nodeId: node.id, severity: "error", message: "Parameter requires a name" });
        }
      }
    }

    if (node.type === "builtin-tool") {
      const data = node.data as BuiltinToolConfig;
      if (!data.toolType || !BUILTIN_TOOL_TYPES.includes(data.toolType)) {
        errors.push({ nodeId: node.id, severity: "error", message: "Built-in tool has invalid type" });
      }
      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "Built-in tool must be connected to an Agent" });
      }
    }
  }

  return errors;
}

export function hasBlockingErrors(errors: ValidationError[]): boolean {
  return errors.some((e) => e.severity === "error");
}
