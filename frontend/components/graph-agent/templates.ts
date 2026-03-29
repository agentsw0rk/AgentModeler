import type { GraphStrategyNode, GraphStrategyEdge, GraphAgentTemplate } from "@/types/graph-agent";

interface TemplateDefinition {
  label: string;
  description: string;
  nodes: GraphStrategyNode[];
  edges: GraphStrategyEdge[];
}

let nextTemplateId = 1;
function sid() { return `sn-${nextTemplateId++}`; }

function createEmpty(): TemplateDefinition {
  const startId = sid();
  const finishId = sid();
  return {
    label: "Empty",
    description: "Start and Finish only",
    nodes: [
      { id: startId, type: "node-start", position: { x: 100, y: 200 }, data: {} },
      { id: finishId, type: "node-finish", position: { x: 500, y: 200 }, data: {} },
    ],
    edges: [],
  };
}

function createToolCalling(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "Tool-Calling Agent",
    description: "LLM calls tools in a loop until it responds with text",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: execId, type: "node-execute-tool", position: { x: 500, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 500, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 750, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: llmId, target: finishId, edgeType: "onAssistantMessage" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: sendId, target: finishId, edgeType: "onAssistantMessage" },
    ],
  };
}

function createReactLoop(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const condId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "ReAct Loop",
    description: "LLM reasons, then decides to act or finish",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: condId, type: "node-condition", position: { x: 450, y: 200 }, data: { label: "shouldAct", condition: "hasToolCall" } },
      { id: execId, type: "node-execute-tool", position: { x: 650, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 650, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 850, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: condId, edgeType: "onAssistantMessage" },
      { id: sid(), source: condId, target: execId, edgeType: "onCondition", condition: "hasToolCall" },
      { id: sid(), source: condId, target: finishId, edgeType: "onCondition", condition: "!hasToolCall" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: condId, edgeType: "onAssistantMessage" },
      { id: sid(), source: sendId, target: execId, edgeType: "onToolCall" },
    ],
  };
}

function createToolLoopFinish(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "Tool Loop with Finish",
    description: "Tools loop back to LLM, assistant message finishes",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: execId, type: "node-execute-tool", position: { x: 500, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 500, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 750, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: llmId, target: finishId, edgeType: "onAssistantMessage" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: sendId, target: finishId, edgeType: "onAssistantMessage" },
    ],
  };
}

export const TEMPLATES: Record<GraphAgentTemplate, TemplateDefinition> = {
  "empty": createEmpty(),
  "tool-calling": createToolCalling(),
  "react-loop": createReactLoop(),
  "tool-loop-finish": createToolLoopFinish(),
};
