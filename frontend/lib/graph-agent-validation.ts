import type { GraphNode, WorkspaceGraph } from "@/types/graph";
import type { ValidationError } from "./validation";

export function validateGraphAgent(node: GraphNode, graph: WorkspaceGraph): ValidationError[] {
  const errors: ValidationError[] = [];
  const d = node.data as any;
  const nodeId = node.id;

  if (!d.name?.trim()) {
    errors.push({ nodeId, severity: "error", message: "Graph Agent requires a name" });
  }
  if (!d.systemPrompt?.trim()) {
    errors.push({ nodeId, severity: "error", message: "Graph Agent requires a system prompt" });
  }

  const strategyNodes: any[] = d.strategyNodes || [];
  const strategyEdges: any[] = d.strategyEdges || [];

  const startCount = strategyNodes.filter((sn: any) => sn.type === "node-start").length;
  const finishCount = strategyNodes.filter((sn: any) => sn.type === "node-finish").length;

  if (startCount !== 1) errors.push({ nodeId, severity: "error", message: "Graph Agent must have exactly one node-start" });
  if (finishCount !== 1) errors.push({ nodeId, severity: "error", message: "Graph Agent must have exactly one node-finish" });

  const startNode = strategyNodes.find((sn: any) => sn.type === "node-start");
  const finishNode = strategyNodes.find((sn: any) => sn.type === "node-finish");

  if (startNode) {
    const startOutgoing = strategyEdges.filter((se: any) => se.source === startNode.id);
    if (startOutgoing.length === 0) {
      errors.push({ nodeId, severity: "error", message: "node-start must have at least one outgoing edge" });
    }
  }

  if (startNode && finishNode) {
    const adj = new Map<string, string[]>();
    for (const se of strategyEdges) {
      if (!adj.has(se.source)) adj.set(se.source, []);
      adj.get(se.source)!.push(se.target);
    }
    const visited = new Set<string>();
    const queue = [startNode.id];
    while (queue.length > 0) {
      const current = queue.shift()!;
      if (visited.has(current)) continue;
      visited.add(current);
      for (const next of adj.get(current) || []) queue.push(next);
    }
    if (!visited.has(finishNode.id)) {
      errors.push({ nodeId, severity: "error", message: "node-finish must be reachable from node-start" });
    }
  }

  for (const sn of strategyNodes) {
    if (sn.type === "node-execute-tool" && !sn.data?.toolName?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-execute-tool "${sn.data?.label || sn.id}" requires a toolName` });
    }
    if (sn.type === "node-condition" && !sn.data?.condition?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-condition "${sn.data?.label || sn.id}" requires a condition` });
    }
    if (sn.type === "node-execute-multiple-tools" && !sn.data?.toolName?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-execute-multiple-tools "${sn.data?.label || sn.id}" requires a toolName` });
    }
    if (sn.type === "node-custom" && !sn.data?.body?.trim()) {
      errors.push({ nodeId, severity: "warning", message: `Custom node "${sn.data?.label || sn.id}" has no body code` });
    }
    if (sn.type === "node-subgraph") {
      if (!sn.data?.subgraphName?.trim()) {
        errors.push({ nodeId, severity: "error", message: `Subgraph node "${sn.data?.label || sn.id}" requires a name` });
      }
      const innerNodes = sn.data?.subgraphNodes || [];
      const innerEdges = sn.data?.subgraphEdges || [];
      const innerStartCount = innerNodes.filter((n: any) => n.type === "node-start").length;
      const innerFinishCount = innerNodes.filter((n: any) => n.type === "node-finish").length;
      if (innerNodes.length > 0) {
        if (innerStartCount !== 1) errors.push({ nodeId, severity: "error", message: `Subgraph "${sn.data?.subgraphName || sn.id}" must have exactly one node-start` });
        if (innerFinishCount !== 1) errors.push({ nodeId, severity: "error", message: `Subgraph "${sn.data?.subgraphName || sn.id}" must have exactly one node-finish` });
      }
      if (sn.data?.subgraphToolSelection === "SPECIFIC" && (!sn.data?.subgraphTools || sn.data.subgraphTools.length === 0)) {
        errors.push({ nodeId, severity: "warning", message: `Subgraph "${sn.data?.subgraphName || sn.id}" has SPECIFIC tool selection but no tools selected` });
      }
    }
  }

  const conditionNodeIds = new Set(
    strategyNodes.filter((sn: any) => sn.type === "node-condition").map((sn: any) => sn.id)
  );

  for (const se of strategyEdges) {
    if (!se.edgeType) {
      errors.push({ nodeId, severity: "error", message: "Strategy edge requires an edgeType" });
    }
    // Only require condition expression for edges from node-condition nodes
    if (se.edgeType === "onCondition" && conditionNodeIds.has(se.source) && !se.condition?.trim()) {
      errors.push({ nodeId, severity: "error", message: "onCondition edge from condition node requires a condition expression" });
    }
  }

  if (strategyNodes.length <= 2 && startCount === 1 && finishCount === 1) {
    errors.push({ nodeId, severity: "warning", message: "Graph Agent has an empty strategy (only Start and Finish)" });
  }

  return errors;
}
