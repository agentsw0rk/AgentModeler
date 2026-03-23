import { ProjectGraph } from "@/types/graph";
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
  }

  return errors;
}

export function hasBlockingErrors(errors: ValidationError[]): boolean {
  return errors.some((e) => e.severity === "error");
}
