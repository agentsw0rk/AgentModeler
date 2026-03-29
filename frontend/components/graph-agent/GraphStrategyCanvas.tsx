"use client";

import { useEffect, useRef } from "react";
import { createStrategyEditor } from "@/lib/strategy-rete-setup";
import type { GraphStrategyNode, GraphStrategyEdge } from "@/types/graph-agent";
import {
  StartReteNode, FinishReteNode, LLMRequestReteNode,
  ExecuteToolReteNode, SendToolResultReteNode, ConditionReteNode,
  ExecuteMultipleToolsReteNode, SendMultipleResultsReteNode, CompressHistoryReteNode,
  LLMStreamingReteNode, LLMStructuredReteNode, DoNothingReteNode,
} from "@/lib/strategy-rete-nodes";
import type { StrategySchemes } from "@/lib/strategy-rete-setup";
import type { NodeEditor } from "rete";
import type { AreaPlugin } from "rete-area-plugin";

interface Props {
  strategyNodes: GraphStrategyNode[];
  strategyEdges: GraphStrategyEdge[];
  onNodeSelected: (nodeId: string | null) => void;
  onEdgeSelected: (edgeId: string | null) => void;
  onNodesChanged: (nodes: GraphStrategyNode[]) => void;
  onEdgesChanged: (edges: GraphStrategyEdge[]) => void;
}

let syncing = false;

function autoTypeEdge(sourceType: string, targetType: string): GraphStrategyEdge["edgeType"] {
  if (sourceType === "node-llm-request" && targetType === "node-execute-tool") return "onToolCall";
  if (sourceType === "node-llm-request" && targetType === "node-execute-multiple-tools") return "onMultipleToolCalls";
  if (sourceType === "node-llm-request" && (targetType === "node-finish" || targetType === "node-condition")) return "onAssistantMessage";
  return "onCondition";
}

export function GraphStrategyCanvas(props: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<NodeEditor<StrategySchemes> | null>(null);
  const areaRef = useRef<AreaPlugin<StrategySchemes, any> | null>(null);
  const initializedRef = useRef(false);
  const propsRef = useRef(props);
  propsRef.current = props;
  const handleEdgeSelectRef = useRef<((e: Event) => void) | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    let aborted = false;

    const init = async () => {
      const { editor, area } = await createStrategyEditor(containerRef.current!);

      // If React StrictMode already cleaned up, discard this editor
      if (aborted) {
        area.destroy();
        return;
      }

      editorRef.current = editor;
      areaRef.current = area;
      (window as any).__strategyReteEditor = editor;

      area.addPipe((context) => {
        if (context.type === "nodepicked") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "strategyNodeId" in reteNode) {
            propsRef.current.onEdgeSelected(null);
            propsRef.current.onNodeSelected((reteNode as any).strategyNodeId);
          }
        }
        return context;
      });

      area.addPipe((context) => {
        if (context.type === "nodetranslated") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "strategyNodeId" in reteNode) {
            syncing = true;
            const nodes = propsRef.current.strategyNodes.map((sn) =>
              sn.id === (reteNode as any).strategyNodeId
                ? { ...sn, position: context.data.position }
                : sn
            );
            propsRef.current.onNodesChanged(nodes);
            syncing = false;
          }
        }
        return context;
      });

      editor.addPipe((context) => {
        if (context.type === "connectioncreated") {
          if (syncing) return context;
          syncing = true;
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          if (sourceNode && targetNode && "strategyNodeId" in sourceNode && "strategyNodeId" in targetNode) {
            const sourceSnId = (sourceNode as any).strategyNodeId;
            const targetSnId = (targetNode as any).strategyNodeId;
            const sourceSn = propsRef.current.strategyNodes.find((sn) => sn.id === sourceSnId);
            const targetSn = propsRef.current.strategyNodes.find((sn) => sn.id === targetSnId);
            const edgeType = autoTypeEdge(sourceSn?.type || "", targetSn?.type || "");
            const newEdge: GraphStrategyEdge = {
              id: `se-${Date.now()}`,
              source: sourceSnId,
              target: targetSnId,
              edgeType,
            };
            propsRef.current.onEdgesChanged([...propsRef.current.strategyEdges, newEdge]);
          }
          syncing = false;
        }
        return context;
      });

      handleEdgeSelectRef.current = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        const sourceRete = editor.getNodes().find((n: any) => n.id === detail.source);
        const targetRete = editor.getNodes().find((n: any) => n.id === detail.target);
        if (sourceRete && targetRete && "strategyNodeId" in sourceRete && "strategyNodeId" in targetRete) {
          const srcId = (sourceRete as any).strategyNodeId;
          const tgtId = (targetRete as any).strategyNodeId;
          const edge = propsRef.current.strategyEdges.find(
            (se) => se.source === srcId && se.target === tgtId
          );
          if (edge) {
            propsRef.current.onEdgeSelected(edge.id);
          }
        }
      };
      window.addEventListener("strategy-edge-select", handleEdgeSelectRef.current);

      if (aborted) return;

      await syncToRete(editor, area, propsRef.current.strategyNodes, propsRef.current.strategyEdges);
      initializedRef.current = true;
    };

    init();

    return () => {
      aborted = true;
      if (handleEdgeSelectRef.current) window.removeEventListener("strategy-edge-select", handleEdgeSelectRef.current);
      areaRef.current?.destroy();
      editorRef.current = null;
      areaRef.current = null;
      initializedRef.current = false;
      delete (window as any).__strategyReteEditor;
    };
  }, []);

  useEffect(() => {
    if (!initializedRef.current || syncing || !editorRef.current || !areaRef.current) return;
    syncToRete(editorRef.current, areaRef.current, props.strategyNodes, props.strategyEdges);
  }, [props.strategyNodes, props.strategyEdges]);

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full relative"
      style={{
        minHeight: "400px",
        background: "radial-gradient(circle, rgba(99, 102, 241, 0.04) 1px, transparent 1px), var(--background)",
        backgroundSize: "20px 20px",
      }}
    />
  );
}

function createReteNode(sn: GraphStrategyNode) {
  switch (sn.type) {
    case "node-start": return new StartReteNode(sn.id);
    case "node-finish": return new FinishReteNode(sn.id);
    case "node-llm-request": return new LLMRequestReteNode(sn.id, sn.data.label || "LLM Request");
    case "node-execute-tool": return new ExecuteToolReteNode(sn.id, sn.data.label || "Execute Tool");
    case "node-llm-send-tool-result": return new SendToolResultReteNode(sn.id, sn.data.label || "Send Tool Result");
    case "node-condition": return new ConditionReteNode(sn.id, sn.data.label || "Condition");
    case "node-execute-multiple-tools": return new ExecuteMultipleToolsReteNode(sn.id, sn.data.label || "Execute Multi Tools");
    case "node-send-multiple-results": return new SendMultipleResultsReteNode(sn.id, sn.data.label || "Send Multi Results");
    case "node-compress-history": return new CompressHistoryReteNode(sn.id, sn.data.label || "Compress History");
    case "node-llm-streaming": return new LLMStreamingReteNode(sn.id, sn.data.label || "LLM Streaming");
    case "node-llm-structured": return new LLMStructuredReteNode(sn.id, sn.data.label || "LLM Structured");
    case "node-do-nothing": return new DoNothingReteNode(sn.id, sn.data.label || "Do Nothing");
  }
}

async function syncToRete(
  editor: NodeEditor<StrategySchemes>,
  area: AreaPlugin<StrategySchemes, any>,
  strategyNodes: GraphStrategyNode[],
  strategyEdges: GraphStrategyEdge[]
) {
  const findReteNode = (snId: string) =>
    editor.getNodes().find((n: any) => "strategyNodeId" in n && n.strategyNodeId === snId);

  for (const sn of strategyNodes) {
    if (!findReteNode(sn.id)) {
      const reteNode = createReteNode(sn);
      await editor.addNode(reteNode);
      await area.translate(reteNode.id, sn.position);
    }
  }

  for (const rn of editor.getNodes()) {
    if ("strategyNodeId" in rn) {
      const snId = (rn as any).strategyNodeId;
      if (!strategyNodes.find((sn) => sn.id === snId)) {
        await editor.removeNode(rn.id);
      }
    }
  }

  for (const sn of strategyNodes) {
    const rn = findReteNode(sn.id);
    if (rn) {
      const newLabel = sn.data.label || sn.type.replace("node-", "").replace(/-/g, " ");
      if (rn.label !== newLabel) {
        rn.label = newLabel;
        area.update("node", rn.id);
      }
    }
  }

  const reteConns = editor.getConnections();
  const storeEdgeKeys = new Set(strategyEdges.map((se) => `${se.source}->${se.target}`));

  for (const conn of reteConns) {
    const srcNode = editor.getNode(conn.source);
    const tgtNode = editor.getNode(conn.target);
    if (!srcNode || !tgtNode) {
      syncing = true;
      await editor.removeConnection(conn.id);
      syncing = false;
      continue;
    }
    const srcId = "strategyNodeId" in srcNode ? (srcNode as any).strategyNodeId : null;
    const tgtId = "strategyNodeId" in tgtNode ? (tgtNode as any).strategyNodeId : null;
    if (srcId && tgtId && !storeEdgeKeys.has(`${srcId}->${tgtId}`)) {
      syncing = true;
      await editor.removeConnection(conn.id);
      syncing = false;
    }
  }

  const currentConns = editor.getConnections();
  const reteEdgeKeys = new Set(currentConns.map((c) => {
    const sn = editor.getNode(c.source);
    const tn = editor.getNode(c.target);
    const sid = sn && "strategyNodeId" in sn ? (sn as any).strategyNodeId : null;
    const tid = tn && "strategyNodeId" in tn ? (tn as any).strategyNodeId : null;
    return `${sid}->${tid}`;
  }));

  for (const se of strategyEdges) {
    if (!reteEdgeKeys.has(`${se.source}->${se.target}`)) {
      const srcRete = findReteNode(se.source);
      const tgtRete = findReteNode(se.target);
      if (srcRete && tgtRete) {
        const outKey = Object.keys(srcRete.outputs || {})[0];
        const inKey = Object.keys(tgtRete.inputs || {})[0];
        if (outKey && inKey) {
          syncing = true;
          const { ClassicPreset } = await import("rete");
          const conn = new ClassicPreset.Connection(srcRete, outKey, tgtRete, inKey);
          await editor.addConnection(conn as any);
          syncing = false;
        }
      }
    }
  }
}
