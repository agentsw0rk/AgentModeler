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

export class ExecuteMultipleToolsReteNode extends ClassicPreset.Node {
  width = 220;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Execute Multi Tools");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class SendMultipleResultsReteNode extends ClassicPreset.Node {
  width = 220;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Send Multi Results");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class CompressHistoryReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Compress History");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class LLMStreamingReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Streaming");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class LLMStructuredReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Structured");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class DoNothingReteNode extends ClassicPreset.Node {
  width = 160;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Do Nothing");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class CustomReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Custom");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export type StrategyNode = StartReteNode | FinishReteNode | LLMRequestReteNode | ExecuteToolReteNode | SendToolResultReteNode | ConditionReteNode | ExecuteMultipleToolsReteNode | SendMultipleResultsReteNode | CompressHistoryReteNode | LLMStreamingReteNode | LLMStructuredReteNode | DoNothingReteNode | CustomReteNode;
export type StrategyConnection = ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node>;
