import { AgentConfig, ToolConfig } from "./koog";

export type NodeType = "agent" | "tool";

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
}

export interface ProjectGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}
