import { ProjectGraph, BuiltinToolConfig, BUILTIN_TOOL_TYPES } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";

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

    if (node.type === "sub-agent") {
      const data = node.data as SubAgentConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a name" });
      }
      if (!data.description.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a description" });
      }
      if (!data.systemPrompt.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a system prompt" });
      }
      if (!data.inputDescription.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires an input description" });
      }

      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent must be connected to the main Agent" });
      }

      // Check not connected to another sub-agent
      const targets = graph.edges
        .filter((e) => e.source === node.id)
        .map((e) => graph.nodes.find((n) => n.id === e.target));
      for (const target of targets) {
        if (target?.type === "sub-agent") {
          errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent cannot delegate to another Sub-Agent" });
        }
      }

      // Warning: sub-agent without tools
      const hasTools = graph.edges.some(
        (e) => e.target === node.id && graph.nodes.some((n) => n.id === e.source && (n.type === "tool" || n.type === "builtin-tool"))
      );
      if (!hasTools) {
        errors.push({ nodeId: node.id, severity: "warning", message: "Sub-Agent has no tools — consider adding tools for specialization" });
      }
    }

    // Check: tool connected to multiple agents/sub-agents
    if (node.type === "tool" || node.type === "builtin-tool") {
      const connectedTargets = graph.edges
        .filter((e) => e.source === node.id)
        .map((e) => graph.nodes.find((n) => n.id === e.target))
        .filter((n) => n?.type === "agent" || n?.type === "sub-agent");
      if (connectedTargets.length > 1) {
        errors.push({ nodeId: node.id, severity: "error", message: "Tool can only be connected to one Agent or Sub-Agent" });
      }
    }
  }

  return errors;
}

export function hasBlockingErrors(errors: ValidationError[]): boolean {
  return errors.some((e) => e.severity === "error");
}
