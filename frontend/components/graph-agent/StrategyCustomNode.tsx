"use client";

import * as React from "react";
import { ClassicPreset } from "rete";
import { Presets } from "rete-react-plugin";
import {
  StartReteNode,
  FinishReteNode,
  LLMRequestReteNode,
  ExecuteToolReteNode,
  SendToolResultReteNode,
  ConditionReteNode,
} from "@/lib/strategy-rete-nodes";
import type { StrategySchemes } from "@/lib/strategy-rete-setup";

const strategyNodeColors: Record<string, { border: string; glow: string; badge: string; badgeText: string }> = {
  "node-start":                { border: "rgba(34, 197, 94, 0.5)",  glow: "rgba(34, 197, 94, 0.12)",  badge: "rgba(34, 197, 94, 0.15)",  badgeText: "#4ade80" },
  "node-finish":               { border: "rgba(239, 68, 68, 0.5)",  glow: "rgba(239, 68, 68, 0.12)",  badge: "rgba(239, 68, 68, 0.15)",  badgeText: "#f87171" },
  "node-llm-request":          { border: "rgba(59, 130, 246, 0.5)", glow: "rgba(59, 130, 246, 0.12)", badge: "rgba(59, 130, 246, 0.15)", badgeText: "#60a5fa" },
  "node-execute-tool":         { border: "rgba(16, 185, 129, 0.5)", glow: "rgba(16, 185, 129, 0.12)", badge: "rgba(16, 185, 129, 0.15)", badgeText: "#34d399" },
  "node-llm-send-tool-result": { border: "rgba(251, 191, 36, 0.5)", glow: "rgba(251, 191, 36, 0.12)", badge: "rgba(251, 191, 36, 0.15)", badgeText: "#fbbf24" },
  "node-condition":            { border: "rgba(168, 85, 247, 0.5)", glow: "rgba(168, 85, 247, 0.12)", badge: "rgba(168, 85, 247, 0.15)", badgeText: "#c084fc" },
};

const strategyNodeLabels: Record<string, string> = {
  "node-start": "START",
  "node-finish": "FINISH",
  "node-llm-request": "LLM REQUEST",
  "node-execute-tool": "EXECUTE TOOL",
  "node-llm-send-tool-result": "SEND RESULT",
  "node-condition": "CONDITION",
};

function getStrategyNodeType(data: StrategySchemes["Node"]): string {
  if (data instanceof StartReteNode) return "node-start";
  if (data instanceof FinishReteNode) return "node-finish";
  if (data instanceof LLMRequestReteNode) return "node-llm-request";
  if (data instanceof ExecuteToolReteNode) return "node-execute-tool";
  if (data instanceof SendToolResultReteNode) return "node-llm-send-tool-result";
  if (data instanceof ConditionReteNode) return "node-condition";
  return "node-start";
}

export function StrategyCustomNode(props: { data: StrategySchemes["Node"] & { width?: number }; emit: (props: any) => void }) {
  const { data, emit } = props;
  const nodeType = getStrategyNodeType(data);
  const colors = strategyNodeColors[nodeType] || strategyNodeColors["node-start"];
  const typeLabel = strategyNodeLabels[nodeType] || "NODE";

  const inputs = Object.entries(data.inputs || {});
  const outputs = Object.entries(data.outputs || {});

  return (
    <div
      style={{
        background: "rgba(15, 22, 41, 0.95)",
        border: `1px solid ${colors.border}`,
        borderRadius: 10,
        padding: 0,
        minWidth: data.width || 160,
        boxShadow: `0 0 16px ${colors.glow}`,
        fontFamily: "var(--font-sans), system-ui, sans-serif",
        backdropFilter: "blur(8px)",
      }}
    >
      <div style={{ padding: "6px 10px 4px", borderBottom: `1px solid ${colors.border}` }}>
        <div
          style={{
            fontSize: 8,
            fontWeight: 700,
            letterSpacing: "0.08em",
            color: colors.badgeText,
            background: colors.badge,
            display: "inline-flex",
            padding: "1px 5px",
            borderRadius: 3,
            marginBottom: 3,
          }}
        >
          {typeLabel}
        </div>
        <div style={{ fontSize: 12, fontWeight: 500, color: "#e2e8f0" }}>
          {data.label}
        </div>
      </div>
      <div style={{ padding: "4px 0" }}>
        {inputs.map(([key, input]) =>
          input ? (
            <div key={key} style={{ display: "flex", alignItems: "center", padding: "2px 10px", fontSize: 10, color: "#94a3b8" }}>
              <Presets.classic.RefSocket name="input-socket" emit={emit} side="input" nodeId={data.id} socketKey={key} payload={(input as ClassicPreset.Input<ClassicPreset.Socket>).socket} />
              <span style={{ marginLeft: 6 }}>{(input as ClassicPreset.Input<ClassicPreset.Socket>).label || key}</span>
            </div>
          ) : null
        )}
        {outputs.map(([key, output]) =>
          output ? (
            <div key={key} style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", padding: "2px 10px", fontSize: 10, color: "#94a3b8" }}>
              <span style={{ marginRight: 6 }}>{(output as ClassicPreset.Output<ClassicPreset.Socket>).label || key}</span>
              <Presets.classic.RefSocket name="output-socket" emit={emit} side="output" nodeId={data.id} socketKey={key} payload={(output as ClassicPreset.Output<ClassicPreset.Socket>).socket} />
            </div>
          ) : null
        )}
      </div>
    </div>
  );
}

export function StrategyCustomSocket(props: { data: ClassicPreset.Socket }) {
  return (
    <div
      style={{
        width: 10,
        height: 10,
        borderRadius: "50%",
        background: "rgba(99, 102, 241, 0.3)",
        border: "2px solid rgba(99, 102, 241, 0.6)",
        boxShadow: "0 0 4px rgba(99, 102, 241, 0.3)",
      }}
    />
  );
}

export function StrategyCustomConnection(props: { data: { id: string; source: string; target: string } }) {
  const { path } = Presets.classic.useConnection();
  const [hovered, setHovered] = React.useState(false);

  if (!path) return null;

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    const event = new CustomEvent("strategy-edge-select", {
      detail: { source: props.data.source, target: props.data.target },
    });
    window.dispatchEvent(event);
  };

  return (
    <svg data-testid="connection" style={{ overflow: "visible", position: "absolute", pointerEvents: "none", width: "100%", height: "100%" }}>
      <path d={path} stroke="transparent" strokeWidth={14} fill="none" style={{ pointerEvents: "stroke", cursor: "pointer" }} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onClick={handleClick} />
      <path d={path} stroke={hovered ? "rgba(129, 140, 248, 0.9)" : "rgba(99, 102, 241, 0.5)"} strokeWidth={hovered ? 3 : 2} fill="none" style={{ pointerEvents: "none", transition: "stroke 0.15s, stroke-width 0.15s" }} />
    </svg>
  );
}
