import type { ModelProvider } from "./koog";
import type { A2AConfig } from "./a2a";

export interface PlannerAgentConfig {
  name: string;
  description: string;
  plannerType: "llm" | "goap";
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  maxReplanAttempts: number;
  goals: PlannerGoal[];
  actions: PlannerAction[];
  initialState: StateEntry[];
  a2aConfig?: A2AConfig;
}

export interface PlannerGoal {
  id: string;
  name: string;
  conditions: StateCondition[];
  priority: number;
}

export interface PlannerAction {
  id: string;
  name: string;
  description: string;
  preconditions: StateCondition[];
  effects: StateEffect[];
  cost: number;
}

export interface StateCondition {
  key: string;
  operator: "equals" | "notEquals" | "greaterThan" | "lessThan";
  value: string | number | boolean;
}

export interface StateEffect {
  key: string;
  value: string | number | boolean;
}

export interface StateEntry {
  key: string;
  value: string | number | boolean;
}
