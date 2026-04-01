"use client";

import { useEffect, useRef } from "react";
import { createEditor } from "@/lib/rete-setup";
import { useEditorStore } from "@/store/editorStore";
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode, McpServerReteNode, PlannerAgentReteNode, A2AAgentReteNode, InputReteNode, OutputReteNode, GraphAgentReteNode, CodeReteNode, RagReteNode } from "@/lib/rete-nodes";
import { BuiltinToolType, BUILTIN_TOOL_LABELS, CodeNodeConfig } from "@/types/graph";
import type { Schemes } from "@/lib/rete-setup";
import type { NodeEditor } from "rete";
import type { AreaPlugin } from "rete-area-plugin";

let editorRef: NodeEditor<Schemes> | null = null;
let areaRef: AreaPlugin<Schemes, any> | null = null;
let syncing = false;

export function Canvas() {
  const containerRef = useRef<HTMLDivElement>(null);
  const nodes = useEditorStore((s) => s.nodes);

  useEffect(() => {
    if (!containerRef.current) return;

    const init = async () => {
      const { editor, area } = await createEditor(containerRef.current!);
      editorRef = editor;
      areaRef = area;
      (window as any).__reteEditor = editor;

      // Rete → Zustand: node selection + drag-end detection
      let dragging = false;

      const handlePointerUp = () => {
        if (dragging) {
          dragging = false;
          useEditorStore.getState().commitPosition();
          document.removeEventListener("pointerup", handlePointerUp);
        }
      };

      area.addPipe((context) => {
        if (context.type === "nodepicked") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "storeNodeId" in reteNode) {
            useEditorStore.getState().setSelectedNodeIds([(reteNode as any).storeNodeId]);
            dragging = true;
            document.addEventListener("pointerup", handlePointerUp);
          }
        }
        return context;
      });

      // Rete → Zustand: node drag
      area.addPipe((context) => {
        if (context.type === "nodetranslated") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "storeNodeId" in reteNode) {
            syncing = true;
            useEditorStore.getState().updateNodePosition(
              (reteNode as any).storeNodeId,
              context.data.position
            );
            syncing = false;
          }
        }
        return context;
      });

      // Rete → Zustand: connection created
      editor.addPipe((context) => {
        if (context.type === "connectioncreated") {
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
          const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
          const isMcpSource = sourceNode instanceof McpServerReteNode;
          const isA2ASource = sourceNode instanceof A2AAgentReteNode;
          const isInputSource = sourceNode instanceof InputReteNode;
          const isCodeSource = sourceNode instanceof CodeReteNode;
          const isRagSource = sourceNode instanceof RagReteNode;
          const isAgentSource = sourceNode instanceof AgentReteNode;
          const isPlannerAgentSource = sourceNode instanceof PlannerAgentReteNode;
          const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;
          const isAgentTarget = targetNode instanceof AgentReteNode;
          const isSubAgentTarget = targetNode instanceof SubAgentReteNode;
          const isPlannerAgentTarget = targetNode instanceof PlannerAgentReteNode;
          const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;
          const isOutputTarget = targetNode instanceof OutputReteNode;
          const isToolTarget = targetNode instanceof ToolReteNode || targetNode instanceof BuiltinToolReteNode;

          if (
            ((isToolSource || isSubAgentSource || isMcpSource || isA2ASource || isInputSource || isCodeSource || isRagSource) && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget || isGraphAgentTarget)) ||
            (isCodeSource && isToolTarget) ||
            ((isAgentSource || isSubAgentSource || isPlannerAgentSource || isGraphAgentSource) && isOutputTarget)
          ) {
            syncing = true;
            const sourceStoreId = (sourceNode as any).storeNodeId;
            const targetStoreId = (targetNode as any).storeNodeId;
            useEditorStore.getState().addEdge(sourceStoreId, targetStoreId);
            syncing = false;
          }
        }
        return context;
      });

      // Rete → Zustand: connection removed
      editor.addPipe((context) => {
        if (context.type === "connectionremoved") {
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
          const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
          const isMcpSource = sourceNode instanceof McpServerReteNode;
          const isA2ASource = sourceNode instanceof A2AAgentReteNode;
          const isInputSource = sourceNode instanceof InputReteNode;
          const isCodeSource = sourceNode instanceof CodeReteNode;
          const isRagSource = sourceNode instanceof RagReteNode;
          const isAgentSource = sourceNode instanceof AgentReteNode;
          const isPlannerAgentSource = sourceNode instanceof PlannerAgentReteNode;
          const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;
          const isAgentTarget = targetNode instanceof AgentReteNode;
          const isSubAgentTarget = targetNode instanceof SubAgentReteNode;
          const isPlannerAgentTarget = targetNode instanceof PlannerAgentReteNode;
          const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;
          const isOutputTarget = targetNode instanceof OutputReteNode;
          const isToolTarget = targetNode instanceof ToolReteNode || targetNode instanceof BuiltinToolReteNode;

          if (
            ((isToolSource || isSubAgentSource || isMcpSource || isA2ASource || isInputSource || isCodeSource || isRagSource) && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget || isGraphAgentTarget)) ||
            (isCodeSource && isToolTarget) ||
            ((isAgentSource || isSubAgentSource || isPlannerAgentSource || isGraphAgentSource) && isOutputTarget)
          ) {
            syncing = true;
            const sourceStoreId = (sourceNode as any).storeNodeId;
            const targetStoreId = (targetNode as any).storeNodeId;
            const edges = useEditorStore.getState().edges;
            const edge = edges.find(
              (e) => e.source === sourceStoreId && e.target === targetStoreId
            );
            if (edge) useEditorStore.getState().removeEdge(edge.id);
            syncing = false;
          }
        }
        return context;
      });

      // Zustand → Rete: sync
      useEditorStore.subscribe(async (state) => {
        if (syncing || !editorRef || !areaRef) return;

        const findReteNode = (storeId: string) =>
          editorRef!.getNodes().find((n: any) => "storeNodeId" in n && n.storeNodeId === storeId);

        for (const node of state.nodes) {
          if (!findReteNode(node.id)) {
            let reteNode;
            if (node.type === "agent") {
              reteNode = new AgentReteNode(node.id, (node.data as any).name || "Agent");
            } else if (node.type === "sub-agent") {
              reteNode = new SubAgentReteNode(node.id, (node.data as any).name || "Sub-Agent");
            } else if (node.type === "builtin-tool") {
              const toolType = (node.data as any).toolType || "tool";
              const label = BUILTIN_TOOL_LABELS[toolType as BuiltinToolType] || toolType;
              reteNode = new BuiltinToolReteNode(node.id, label);
            } else if (node.type === "mcp-server") {
              reteNode = new McpServerReteNode(node.id, (node.data as any).name || "MCP Server");
            } else if (node.type === "planner-agent") {
              reteNode = new PlannerAgentReteNode(node.id, (node.data as any).name || "Planner Agent");
            } else if (node.type === "a2a-agent") {
              reteNode = new A2AAgentReteNode(node.id, (node.data as any).name || "A2A Agent");
            } else if (node.type === "input") {
              reteNode = new InputReteNode(node.id, (node.data as any).name || "Input");
            } else if (node.type === "output") {
              reteNode = new OutputReteNode(node.id, (node.data as any).name || "Output");
            } else if (node.type === "graph-agent") {
              reteNode = new GraphAgentReteNode(node.id, (node.data as any).name || "Graph Agent");
            } else if (node.type === "code") {
              const d = node.data as CodeNodeConfig;
              reteNode = new CodeReteNode(node.id, d.fileName || "Code");
            } else if (node.type === "rag") {
              reteNode = new RagReteNode(node.id, (node.data as any).name || "RAG");
            } else {
              reteNode = new ToolReteNode(node.id, (node.data as any).name || "Tool");
            }
            await editorRef!.addNode(reteNode);
            await areaRef!.translate(reteNode.id, node.position);
          }
        }

        const reteNodes = [...editorRef!.getNodes()];
        for (const reteNode of reteNodes) {
          if ("storeNodeId" in reteNode) {
            const storeId = (reteNode as any).storeNodeId;
            if (!state.nodes.find((n) => n.id === storeId)) {
              await editorRef!.removeNode(reteNode.id);
            }
          }
        }

        for (const node of state.nodes) {
          const reteNode = findReteNode(node.id);
          if (reteNode) {
            let newLabel: string;
            if (node.type === "builtin-tool") {
              const toolType = (node.data as any).toolType || "tool";
              newLabel = BUILTIN_TOOL_LABELS[toolType as BuiltinToolType] || toolType;
            } else if (node.type === "code") {
              const d = node.data as CodeNodeConfig;
              if (reteNode.label !== (d.fileName || "Code")) {
                reteNode.label = d.fileName || "Code";
                areaRef!.update("node", reteNode.id);
              }
              continue;
            } else {
              newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
            }
            if (reteNode.label !== newLabel) {
              reteNode.label = newLabel;
              areaRef!.update("node", reteNode.id);
            }
          }
        }

        // Edge sync: remove stale Rete connections
        const reteConnections = editor.getConnections();
        const storeEdgeKeys = new Set(state.edges.map((e) => `${e.source}→${e.target}`));

        for (const conn of reteConnections) {
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          if (!sourceNode || !targetNode) {
            syncing = true;
            await editor.removeConnection(conn.id);
            syncing = false;
            continue;
          }
          const sourceStoreId = "storeNodeId" in sourceNode ? (sourceNode as any).storeNodeId : null;
          const targetStoreId = "storeNodeId" in targetNode ? (targetNode as any).storeNodeId : null;
          if (sourceStoreId && targetStoreId && !storeEdgeKeys.has(`${sourceStoreId}→${targetStoreId}`)) {
            syncing = true;
            await editor.removeConnection(conn.id);
            syncing = false;
          }
        }

        // Edge sync: add missing connections
        const currentReteConnections = editor.getConnections();
        const reteEdgeKeys = new Set(currentReteConnections.map((c) => {
          const sn = editor.getNode(c.source);
          const tn = editor.getNode(c.target);
          const sid = sn && "storeNodeId" in sn ? (sn as any).storeNodeId : null;
          const tid = tn && "storeNodeId" in tn ? (tn as any).storeNodeId : null;
          return `${sid}→${tid}`;
        }));

        for (const edge of state.edges) {
          if (!reteEdgeKeys.has(`${edge.source}→${edge.target}`)) {
            const sourceRete = findReteNode(edge.source);
            const targetRete = findReteNode(edge.target);
            if (sourceRete && targetRete) {
              const outputKey = Object.keys(sourceRete.outputs || {})[0];
              const isInputSource = sourceRete instanceof InputReteNode;
              const isOutputTarget = targetRete instanceof OutputReteNode;
              const inputKey = isOutputTarget ? "agent"
                : isInputSource && targetRete.inputs?.["inputs"] ? "inputs"
                : Object.keys(targetRete.inputs || {})[0];
              if (outputKey && inputKey) {
                syncing = true;
                const { ClassicPreset } = await import("rete");
                const conn = new ClassicPreset.Connection(sourceRete, outputKey, targetRete, inputKey);
                await editor.addConnection(conn as any);
                syncing = false;
              }
            }
          }
        }
      });
    };

    init();

    return () => {
      areaRef?.destroy();
      editorRef = null;
      areaRef = null;
      delete (window as any).__reteEditor;
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full relative"
      style={{
        minHeight: "600px",
        background: "radial-gradient(circle, rgba(59, 130, 246, 0.04) 1px, transparent 1px), var(--background)",
        backgroundSize: "20px 20px",
      }}
    >
      {nodes.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-10">
          <p className="text-muted-foreground text-sm">Add an Agent to get started</p>
        </div>
      )}
    </div>
  );
}
