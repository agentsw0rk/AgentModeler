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
  PlannerAgentReteNode,
  A2AAgentReteNode,
} from "@/lib/rete-nodes";
import type { Schemes } from "@/lib/rete-setup";
import { BotIcon, WrenchIcon, UsersIcon, ServerIcon, ClipboardListIcon, ArrowRightLeftIcon } from "lucide-react";
import { useValidationStore } from "@/store/validationStore";

const nodeIcons: Record<string, React.ReactNode> = {
  agent: <BotIcon size={10} />,
  tool: <WrenchIcon size={10} />,
  "builtin-tool": <WrenchIcon size={10} />,
  "sub-agent": <UsersIcon size={10} />,
  "mcp-server": <ServerIcon size={10} />,
  "planner-agent": <ClipboardListIcon size={10} />,
  "a2a-agent": <ArrowRightLeftIcon size={10} />,
};

const nodeColors: Record<string, { border: string; glow: string; badge: string; badgeText: string }> = {
  agent:        { border: "rgba(59, 130, 246, 0.4)",  glow: "rgba(59, 130, 246, 0.12)", badge: "rgba(59, 130, 246, 0.15)",  badgeText: "#60a5fa" },
  tool:         { border: "rgba(16, 185, 129, 0.4)",  glow: "rgba(16, 185, 129, 0.10)", badge: "rgba(16, 185, 129, 0.15)", badgeText: "#34d399" },
  "builtin-tool": { border: "rgba(16, 185, 129, 0.3)", glow: "rgba(16, 185, 129, 0.08)", badge: "rgba(16, 185, 129, 0.12)", badgeText: "#6ee7b7" },
  "sub-agent":  { border: "rgba(251, 191, 36, 0.4)",  glow: "rgba(251, 191, 36, 0.10)", badge: "rgba(251, 191, 36, 0.15)", badgeText: "#fbbf24" },
  "mcp-server": { border: "rgba(168, 85, 247, 0.4)",  glow: "rgba(168, 85, 247, 0.10)", badge: "rgba(168, 85, 247, 0.15)", badgeText: "#c084fc" },
  "planner-agent": {
    border: "rgba(236, 72, 153, 0.4)",
    glow: "rgba(236, 72, 153, 0.12)",
    badge: "rgba(236, 72, 153, 0.15)",
    badgeText: "#ec4899",
  },
  "a2a-agent": {
    border: "rgba(244, 63, 94, 0.4)",
    glow: "rgba(244, 63, 94, 0.12)",
    badge: "rgba(244, 63, 94, 0.15)",
    badgeText: "#fb7185",
  },
};

const nodeTypeLabels: Record<string, string> = {
  agent: "AGENT",
  tool: "TOOL",
  "builtin-tool": "BUILT-IN",
  "sub-agent": "SUB-AGENT",
  "mcp-server": "MCP SERVER",
  "planner-agent": "PLANNER",
  "a2a-agent": "A2A AGENT",
};

function getNodeType(data: Schemes["Node"]): string {
  if (data instanceof AgentReteNode) return "agent";
  if (data instanceof ToolReteNode) return "tool";
  if (data instanceof BuiltinToolReteNode) return "builtin-tool";
  if (data instanceof SubAgentReteNode) return "sub-agent";
  if (data instanceof McpServerReteNode) return "mcp-server";
  if (data instanceof PlannerAgentReteNode) return "planner-agent";
  if (data instanceof A2AAgentReteNode) return "a2a-agent";
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

  const storeNodeId = "storeNodeId" in data ? (data as any).storeNodeId : null;
  const nodeErrors = storeNodeId
    ? useValidationStore.getState().errorsForNode(storeNodeId)
    : [];
  const hasErrors = nodeErrors.some((e) => e.severity === "error");
  const hasWarnings = nodeErrors.some((e) => e.severity === "warning");

  // Read store node data for configured check and subtitles
  const { useEditorStore } = require("@/store/editorStore");
  const storeNode = storeNodeId
    ? useEditorStore.getState().nodes.find((n: any) => n.id === storeNodeId)
    : null;
  const isConfigured = storeNode && (storeNode.data as any).name?.trim();

  const borderColor = hasErrors
    ? "rgba(239, 68, 68, 0.5)"
    : hasWarnings
    ? "rgba(251, 191, 36, 0.5)"
    : colors.border;

  const glowColor = hasErrors
    ? "rgba(239, 68, 68, 0.15)"
    : hasWarnings
    ? "rgba(251, 191, 36, 0.12)"
    : colors.glow;

  let subtitle = "";
  if (storeNode) {
    const d = storeNode.data as any;
    switch (storeNode.type) {
      case "agent":
        if (d.modelProvider) subtitle = `${d.modelProvider}${d.modelId ? " · " + d.modelId : ""}`;
        break;
      case "sub-agent":
        if (d.delegationMode) subtitle = d.delegationMode;
        break;
      case "tool":
        if (d.parameters?.length) subtitle = `${d.parameters.length} parameter${d.parameters.length !== 1 ? "s" : ""}`;
        break;
      case "mcp-server":
        if (d.transportType) subtitle = `${d.transportType.toUpperCase()}${d.url ? " · " + d.url.slice(0, 30) : ""}`;
        break;
      case "planner-agent":
        subtitle = d?.plannerType === "goap" ? "GOAP Planner" : "LLM Planner";
        break;
      case "a2a-agent":
        if (d.url) subtitle = d.url.length > 30 ? d.url.slice(0, 30) + "..." : d.url;
        break;
    }
  }

  const inputs = Object.entries(data.inputs || {});
  const outputs = Object.entries(data.outputs || {});

  return (
    <div
      style={{
        background: "rgba(15, 22, 41, 0.95)",
        border: `1px solid ${borderColor}`,
        borderRadius: 10,
        padding: 0,
        minWidth: data.width || 200,
        boxShadow: `0 0 20px ${glowColor}`,
        fontFamily: "var(--font-sans), system-ui, sans-serif",
        backdropFilter: "blur(8px)",
      }}
    >
      {/* Header */}
      <div style={{ padding: "8px 12px 6px", borderBottom: `1px solid ${borderColor}`, position: "relative" }}>
        <div
          style={{
            fontSize: 9,
            fontWeight: 700,
            letterSpacing: "0.08em",
            color: colors.badgeText,
            background: colors.badge,
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            padding: "1px 6px",
            borderRadius: 4,
            marginBottom: 4,
          }}
        >
          {nodeIcons[nodeType]}
          {typeLabel}
        </div>
        {hasErrors && (
          <span style={{ position: "absolute", top: 4, right: 8, fontSize: 10, color: "#ef4444" }}>✗</span>
        )}
        {!hasErrors && hasWarnings && (
          <span style={{ position: "absolute", top: 4, right: 8, fontSize: 10, color: "#fbbf24" }}>⚠</span>
        )}
        {!hasErrors && !hasWarnings && isConfigured && (
          <span style={{ position: "absolute", top: 4, right: 8, fontSize: 8, color: "#22c55e" }}>●</span>
        )}
        <div style={{ fontSize: 13, fontWeight: 500, color: "#e2e8f0" }}>
          {data.label}
        </div>
        {subtitle && (
          <div style={{ fontSize: 10, color: "#64748b", marginTop: 2 }}>
            {subtitle}
          </div>
        )}
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
