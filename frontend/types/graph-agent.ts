export type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition";

export interface GraphStrategyNode {
  id: string;
  type: GraphStrategyNodeType;
  position: { x: number; y: number };
  data: GraphStrategyNodeData;
}

export interface GraphStrategyNodeData {
  label?: string;
  toolName?: string;
  condition?: string;
  temperature?: number;
}

export type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition";

export interface GraphStrategyEdge {
  id: string;
  source: string;
  target: string;
  edgeType: GraphEdgeType;
  condition?: string;
  transformation?: string;
}

export type GraphAgentTemplate = "empty" | "tool-calling" | "react-loop" | "tool-loop-finish";

export interface GraphAgentConfig {
  name: string;
  description: string;
  systemPrompt: string;
  modelProvider: string;
  modelId?: string;
  temperature?: number;
  strategyNodes: GraphStrategyNode[];
  strategyEdges: GraphStrategyEdge[];
}
