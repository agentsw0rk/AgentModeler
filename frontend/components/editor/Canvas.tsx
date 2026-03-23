"use client";

import { useEffect, useRef } from "react";
import { createEditor } from "@/lib/rete-setup";
import { useEditorStore } from "@/store/editorStore";
import { AgentReteNode, ToolReteNode } from "@/lib/rete-nodes";
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
          if (sourceNode instanceof ToolReteNode && targetNode instanceof AgentReteNode) {
            syncing = true;
            useEditorStore.getState().addEdge(sourceNode.storeNodeId, targetNode.storeNodeId);
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
          if (sourceNode instanceof ToolReteNode && targetNode instanceof AgentReteNode) {
            syncing = true;
            const edges = useEditorStore.getState().edges;
            const edge = edges.find(
              (e) => e.source === sourceNode.storeNodeId && e.target === targetNode.storeNodeId
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
            const reteNode = node.type === "agent"
              ? new AgentReteNode(node.id, (node.data as any).name || "Agent")
              : new ToolReteNode(node.id, (node.data as any).name || "Tool");
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
            const newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
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
