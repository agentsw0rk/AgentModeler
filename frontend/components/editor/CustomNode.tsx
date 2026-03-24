"use client";

import * as React from "react";
import { ClassicPreset } from "rete";
import { Presets } from "rete-react-plugin";
import {
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
  SubAgentReteNode,
  McpServerReteNode,
} from "@/lib/rete-nodes";
import type { Schemes } from "@/lib/rete-setup";

const nodeColors: Record<string, { border: string; glow: string; badge: string; badgeText: string }> = {
  agent:        { border: "rgba(59, 130, 246, 0.4)",  glow: "rgba(59, 130, 246, 0.12)", badge: "rgba(59, 130, 246, 0.15)",  badgeText: "#60a5fa" },
  tool:         { border: "rgba(16, 185, 129, 0.4)",  glow: "rgba(16, 185, 129, 0.10)", badge: "rgba(16, 185, 129, 0.15)", badgeText: "#34d399" },
  "builtin-tool": { border: "rgba(16, 185, 129, 0.3)", glow: "rgba(16, 185, 129, 0.08)", badge: "rgba(16, 185, 129, 0.12)", badgeText: "#6ee7b7" },
  "sub-agent":  { border: "rgba(251, 191, 36, 0.4)",  glow: "rgba(251, 191, 36, 0.10)", badge: "rgba(251, 191, 36, 0.15)", badgeText: "#fbbf24" },
  "mcp-server": { border: "rgba(168, 85, 247, 0.4)",  glow: "rgba(168, 85, 247, 0.10)", badge: "rgba(168, 85, 247, 0.15)", badgeText: "#c084fc" },
};

const nodeTypeLabels: Record<string, string> = {
  agent: "AGENT",
  tool: "TOOL",
  "builtin-tool": "BUILT-IN",
  "sub-agent": "SUB-AGENT",
  "mcp-server": "MCP SERVER",
};

function getNodeType(data: Schemes["Node"]): string {
  if (data instanceof AgentReteNode) return "agent";
  if (data instanceof ToolReteNode) return "tool";
  if (data instanceof BuiltinToolReteNode) return "builtin-tool";
  if (data instanceof SubAgentReteNode) return "sub-agent";
  if (data instanceof McpServerReteNode) return "mcp-server";
  return "tool";
}

type NodeExtraData = { width?: number; height?: number };

export function CustomNode(props: {
  data: Schemes["Node"] & NodeExtraData;
  emit: (props: any) => void;
}) {
  const { data, emit } = props;
  const nodeType = getNodeType(data);
  const colors = nodeColors[nodeType] || nodeColors.tool;
  const typeLabel = nodeTypeLabels[nodeType] || "NODE";

  const inputs = Object.entries(data.inputs || {});
  const outputs = Object.entries(data.outputs || {});

  return (
    <div
      style={{
        background: "rgba(15, 22, 41, 0.95)",
        border: `1px solid ${colors.border}`,
        borderRadius: 10,
        padding: 0,
        minWidth: data.width || 200,
        boxShadow: `0 0 20px ${colors.glow}`,
        fontFamily: "var(--font-sans), system-ui, sans-serif",
        backdropFilter: "blur(8px)",
      }}
    >
      {/* Header */}
      <div style={{ padding: "8px 12px 6px", borderBottom: `1px solid ${colors.border}` }}>
        <div
          style={{
            fontSize: 9,
            fontWeight: 700,
            letterSpacing: "0.08em",
            color: colors.badgeText,
            background: colors.badge,
            display: "inline-block",
            padding: "1px 6px",
            borderRadius: 4,
            marginBottom: 4,
          }}
        >
          {typeLabel}
        </div>
        <div style={{ fontSize: 13, fontWeight: 500, color: "#e2e8f0" }}>
          {data.label}
        </div>
      </div>

      {/* Sockets */}
      <div style={{ padding: "6px 0" }}>
        {inputs.map(([key, input]) =>
          input ? (
            <div
              key={key}
              style={{
                display: "flex",
                alignItems: "center",
                padding: "3px 12px",
                fontSize: 11,
                color: "#94a3b8",
              }}
            >
              <Presets.classic.RefSocket
                name="input-socket"
                emit={emit}
                side="input"
                nodeId={data.id}
                socketKey={key}
                payload={(input as ClassicPreset.Input<ClassicPreset.Socket>).socket}
              />
              <span style={{ marginLeft: 8 }}>{(input as ClassicPreset.Input<ClassicPreset.Socket>).label || key}</span>
            </div>
          ) : null
        )}
        {outputs.map(([key, output]) =>
          output ? (
            <div
              key={key}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "flex-end",
                padding: "3px 12px",
                fontSize: 11,
                color: "#94a3b8",
              }}
            >
              <span style={{ marginRight: 8 }}>{(output as ClassicPreset.Output<ClassicPreset.Socket>).label || key}</span>
              <Presets.classic.RefSocket
                name="output-socket"
                emit={emit}
                side="output"
                nodeId={data.id}
                socketKey={key}
                payload={(output as ClassicPreset.Output<ClassicPreset.Socket>).socket}
              />
            </div>
          ) : null
        )}
      </div>
    </div>
  );
}

export function CustomSocket(props: { data: ClassicPreset.Socket }) {
  return (
    <div
      style={{
        width: 12,
        height: 12,
        borderRadius: "50%",
        background: "rgba(59, 130, 246, 0.3)",
        border: "2px solid rgba(59, 130, 246, 0.6)",
        boxShadow: "0 0 6px rgba(59, 130, 246, 0.3)",
      }}
    />
  );
}

export function CustomConnection() {
  const { path } = Presets.classic.useConnection();

  if (!path) return null;

  return (
    <svg data-testid="connection" style={{ overflow: "visible", position: "absolute", pointerEvents: "none", width: "100%", height: "100%" }}>
      <path
        d={path}
        stroke="rgba(59, 130, 246, 0.5)"
        strokeWidth={2}
        fill="none"
      />
    </svg>
  );
}
