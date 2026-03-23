"use client";

import { useEffect, useRef } from "react";
import { createEditor } from "@/lib/rete-setup";
import { useEditorStore } from "@/store/editorStore";
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode } from "@/lib/rete-nodes";
import { BuiltinToolType, BUILTIN_TOOL_LABELS } from "@/types/graph";
import type { Schemes } from "@/lib/rete-setup";
import type { NodeEditor } from "rete";
import type { AreaPlugin } from "rete-area-plugin";

let editorRef: NodeEditor<Schemes> | null = null;
let areaRef: AreaPlugin<Schemes, any> | null = null;
let syncing = false;

export function Canvas() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const init = async () => {
      const { editor, area } = await createEditor(containerRef.current!);
      editorRef = editor;
      areaRef = area;

      // Rete → Zustand: node selection
      area.addPipe((context) => {
        if (context.type === "nodepicked") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "storeNodeId" in reteNode) {
            useEditorStore.getState().setSelectedNodeId((reteNode as any).storeNodeId);
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
          const isAgentTarget = targetNode instanceof AgentReteNode;
          const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

          if ((isToolSource || isSubAgentSource) && (isAgentTarget || isSubAgentTarget)) {
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
          const isAgentTarget = targetNode instanceof AgentReteNode;
          const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

          if ((isToolSource || isSubAgentSource) && (isAgentTarget || isSubAgentTarget)) {
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
            } else {
              reteNode = new ToolReteNode(node.id, (node.data as any).name || "Tool");
            }
            await editorRef!.addNode(reteNode);
            await areaRef!.translate(reteNode.id, node.position);
          }
        }

        for (const reteNode of editorRef!.getNodes()) {
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
            } else {
              newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
            }
            if (reteNode.label !== newLabel) {
              reteNode.label = newLabel;
              areaRef!.update("node", reteNode.id);
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
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full bg-muted/30"
      style={{ minHeight: "600px" }}
    />
  );
}
