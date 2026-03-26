import { ClassicPreset } from "rete";

export class ToolSocket extends ClassicPreset.Socket {
  constructor() {
    super("Tool");
  }
  isCompatibleWith(socket: ClassicPreset.Socket): boolean {
    return socket instanceof ToolSocket;
  }
}

export const toolSocket = new ToolSocket();

export class AgentReteNode extends ClassicPreset.Node {
  width = 200;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Agent");
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
  }
}

export class ToolReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Tool");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class BuiltinToolReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Built-in Tool");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class SubAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Sub-Agent");
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class McpServerReteNode extends ClassicPreset.Node {
  width = 240;
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "MCP Server");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class PlannerAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;
  public storeNodeId: string;
  constructor(storeNodeId: string, label: string) {
    super(label);
    this.storeNodeId = storeNodeId;
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
  }
}

export class A2AAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "A2A Agent");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode;
export type AppConnection = ClassicPreset.Connection<
  ClassicPreset.Node,
  ClassicPreset.Node
> & { isLoop?: boolean };
