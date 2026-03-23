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

export type AppNode = AgentReteNode | ToolReteNode;
export type AppConnection = ClassicPreset.Connection<
  ClassicPreset.Node,
  ClassicPreset.Node
> & { isLoop?: boolean };
