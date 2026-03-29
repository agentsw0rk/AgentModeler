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
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Agent");
    this.addInput("inputs", new ClassicPreset.Input(toolSocket, "Inputs", true));
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
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
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Sub-Agent");
    this.addInput("inputs", new ClassicPreset.Input(toolSocket, "Inputs", true));
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
    this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
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
  height = 120;
  public storeNodeId: string;
  constructor(storeNodeId: string, label: string) {
    super(label);
    this.storeNodeId = storeNodeId;
    this.addInput("inputs", new ClassicPreset.Input(toolSocket, "Inputs", true));
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
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

export class InputReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Input");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class OutputReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Output");
    this.addInput("agent", new ClassicPreset.Input(toolSocket, "Agent", true));
  }
}

export class GraphAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Graph Agent");
    this.addInput("inputs", new ClassicPreset.Input(toolSocket, "Inputs", true));
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
  }
}

export class CodeReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public storeNodeId: string, label: string) {
    super(label || "Code");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode | InputReteNode | OutputReteNode | GraphAgentReteNode | CodeReteNode;
export type AppConnection = ClassicPreset.Connection<
  ClassicPreset.Node,
  ClassicPreset.Node
> & { isLoop?: boolean };
