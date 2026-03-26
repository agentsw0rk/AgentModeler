import { WorkspaceGraph, BuiltinToolConfig, BUILTIN_TOOL_TYPES } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerConfig } from "@/types/mcp";
import { PlannerAgentConfig } from "@/types/planner";
import { A2AAgentConfig } from "@/types/a2a";

export interface ValidationError {
  nodeId: string;
  severity: "error" | "warning";
  message: string;
}

export function validateGraph(graph: WorkspaceGraph): ValidationError[] {
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
      // A2A config validation
      const agentData = node.data as AgentConfig;
      if (agentData.a2aConfig?.enabled) {
        const card = agentData.a2aConfig.agentCard;
        if (!card?.name?.trim()) {
          errors.push({ nodeId: node.id, severity: "error", message: "A2A AgentCard requires a name" });
        }
        if (!card?.url?.trim()) {
          errors.push({ nodeId: node.id, severity: "error", message: "A2A AgentCard requires a URL" });
        }
        if (!card?.skills?.length) {
          errors.push({ nodeId: node.id, severity: "warning", message: "A2A AgentCard has no skills defined" });
        }
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

    if (node.type === "mcp-server") {
      const data = node.data as McpServerConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "MCP Server requires a name" });
      }
      const validTransports = ["sse", "stdio"];
      if (!validTransports.includes(data.transportType)) {
        errors.push({ nodeId: node.id, severity: "error", message: "MCP Server has invalid transport type" });
      } else if (data.transportType === "sse" && !(data.url || "").trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "MCP Server (SSE) requires a URL" });
      } else if (data.transportType === "stdio" && !(data.command || "").trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "MCP Server (STDIO) requires a command" });
      }

      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "MCP Server must be connected to an Agent" });
      }

      if (data.discoveredTools.length === 0) {
        errors.push({ nodeId: node.id, severity: "warning", message: "MCP Server has no discovered tools — click Discover to fetch tools" });
      }

      // Warning: MCP connected to sub-agent
      const mcpTargets = graph.edges
        .filter((e) => e.source === node.id)
        .map((e) => graph.nodes.find((n) => n.id === e.target));
      for (const target of mcpTargets) {
        if (target?.type === "sub-agent") {
          errors.push({ nodeId: node.id, severity: "warning", message: "MCP Server connected to Sub-Agent will only be exported for the main Agent" });
        }
      }
    }

    if (node.type === "planner-agent") {
      const d = node.data as PlannerAgentConfig;
      if (!d.name?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have a name" });
      }
      if (!d.goals || d.goals.length === 0) {
        errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have at least one goal" });
      }
      if (!d.actions || d.actions.length === 0) {
        errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have at least one action" });
      }
      if (d.plannerType === "llm" && !d.modelProvider) {
        errors.push({ nodeId: node.id, severity: "error", message: "LLM planner requires a model provider" });
      }
      const goalNames = new Set<string>();
      for (const goal of d.goals ?? []) {
        if (goal.name && goalNames.has(goal.name)) {
          errors.push({ nodeId: node.id, severity: "error", message: `Duplicate goal name: '${goal.name}'` });
        }
        goalNames.add(goal.name);
        if (!goal.conditions || goal.conditions.length === 0) {
          errors.push({ nodeId: node.id, severity: "warning", message: `Goal '${goal.name || "unnamed"}' has no conditions` });
        }
        for (const cond of goal.conditions ?? []) {
          if (!cond.key?.trim()) {
            errors.push({ nodeId: node.id, severity: "error", message: "Condition key must not be empty" });
          }
        }
      }
      const actionNames = new Set<string>();
      for (const action of d.actions ?? []) {
        if (action.name && actionNames.has(action.name)) {
          errors.push({ nodeId: node.id, severity: "error", message: `Duplicate action name: '${action.name}'` });
        }
        actionNames.add(action.name);
        if (!action.preconditions || action.preconditions.length === 0) {
          errors.push({ nodeId: node.id, severity: "warning", message: `Action '${action.name || "unnamed"}' has no preconditions` });
        }
        if (!action.effects || action.effects.length === 0) {
          errors.push({ nodeId: node.id, severity: "warning", message: `Action '${action.name || "unnamed"}' has no effects` });
        }
        for (const cond of action.preconditions ?? []) {
          if (!cond.key?.trim()) {
            errors.push({ nodeId: node.id, severity: "error", message: "Condition key must not be empty" });
          }
        }
        for (const eff of action.effects ?? []) {
          if (!eff.key?.trim()) {
            errors.push({ nodeId: node.id, severity: "error", message: "Effect key must not be empty" });
          }
        }
      }
    }

    if (node.type === "a2a-agent") {
      const data = node.data as A2AAgentConfig;
      if (!data.name?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent requires a name" });
      }
      if (!data.url?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent requires a URL" });
      }
      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent must be connected to an Agent" });
      }
      if ((data.timeout ?? 0) <= 0) {
        errors.push({ nodeId: node.id, severity: "warning", message: "A2A Agent timeout should be greater than 0" });
      }
    }

    // Check: tool connected to multiple agents/sub-agents
    if (node.type === "tool" || node.type === "builtin-tool" || node.type === "mcp-server") {
      const connectedTargets = graph.edges
        .filter((e) => e.source === node.id)
        .map((e) => graph.nodes.find((n) => n.id === e.target))
        .filter((n) => n?.type === "agent" || n?.type === "sub-agent" || n?.type === "planner-agent");
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
