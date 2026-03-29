export type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition"
  | "node-execute-multiple-tools"
  | "node-send-multiple-results"
  | "node-compress-history"
  | "node-llm-streaming"
  | "node-llm-structured"
  | "node-do-nothing"
  | "node-custom"
  | "node-subgraph";

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
  compressionStrategy?: string;
  outputSchema?: string;
  inputType?: string;
  outputType?: string;
  body?: string;
  subgraphName?: string;
  subgraphInputType?: string;
  subgraphOutputType?: string;
  subgraphToolSelection?: "ALL" | "SPECIFIC" | "NONE";
  subgraphTools?: string[];
  subgraphNodes?: GraphStrategyNode[];
  subgraphEdges?: GraphStrategyEdge[];
}

export type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition" | "onMultipleToolCalls";

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
