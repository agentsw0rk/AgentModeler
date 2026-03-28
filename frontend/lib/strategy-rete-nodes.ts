import { ClassicPreset } from "rete";

export class StrategySocket extends ClassicPreset.Socket {
  constructor() { super("Strategy"); }
  isCompatibleWith(socket: ClassicPreset.Socket): boolean {
    return socket instanceof StrategySocket;
  }
}

export const strategySocket = new StrategySocket();

export class StartReteNode extends ClassicPreset.Node {
  width = 140;
  height = 60;
  constructor(public strategyNodeId: string) {
    super("Start");
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class FinishReteNode extends ClassicPreset.Node {
  width = 140;
  height = 60;
  constructor(public strategyNodeId: string) {
    super("Finish");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
  }
}

export class LLMRequestReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Request");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class ExecuteToolReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Execute Tool");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class SendToolResultReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Send Tool Result");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class ConditionReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Condition");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export type StrategyNode = StartReteNode | FinishReteNode | LLMRequestReteNode | ExecuteToolReteNode | SendToolResultReteNode | ConditionReteNode;
export type StrategyConnection = ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node>;
