"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import type { GraphAgentConfig, GraphStrategyNode, GraphStrategyEdge, GraphStrategyNodeType } from "@/types/graph-agent";
import { GraphStrategyToolbar } from "./GraphStrategyToolbar";
import { GraphStrategyCanvas } from "./GraphStrategyCanvas";
import { StrategyNodeProperties } from "./StrategyNodeProperties";
import { StrategyEdgeProperties } from "./StrategyEdgeProperties";
import { XIcon } from "lucide-react";

let nextStrategyNodeId = 1000;

export function GraphStrategyEditor({
  nodeId,
  data,
  onClose,
}: {
  nodeId: string;
  data: GraphAgentConfig;
  onClose: () => void;
}) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const edges = useEditorStore((s) => s.edges);
  const nodes = useEditorStore((s) => s.nodes);
  const [selectedStrategyNodeId, setSelectedStrategyNodeId] = useState<string | null>(null);
  const [selectedStrategyEdgeId, setSelectedStrategyEdgeId] = useState<string | null>(null);

  const connectedToolNames: string[] = edges
    .filter((e) => e.target === nodeId)
    .map((e) => nodes.find((n) => n.id === e.source))
    .filter((n) => n && (n.type === "tool" || n.type === "builtin-tool"))
    .map((n) => (n!.data as any).name || (n!.data as any).toolType || "")
    .filter((name) => name);

  const update = (field: Partial<GraphAgentConfig>) => updateNodeData(nodeId, field);

  const handleAddNode = (type: GraphStrategyNodeType) => {
    const id = `sn-${nextStrategyNodeId++}`;
    const defaultLabels: Record<string, string> = {
      "node-llm-request": "llmRequest",
      "node-execute-tool": "executeTool",
      "node-llm-send-tool-result": "sendResult",
      "node-condition": "condition",
    };
    const newNode: GraphStrategyNode = {
      id,
      type,
      position: { x: 300, y: 200 },
      data: { label: defaultLabels[type] || type },
    };
    update({ strategyNodes: [...data.strategyNodes, newNode] });
  };

  const handleNodesChanged = (strategyNodes: GraphStrategyNode[]) => {
    update({ strategyNodes });
  };

  const handleEdgesChanged = (strategyEdges: GraphStrategyEdge[]) => {
    update({ strategyEdges });
  };

  const handleNodeSelected = (snId: string | null) => {
    setSelectedStrategyNodeId(snId);
    setSelectedStrategyEdgeId(null);
  };

  const handleEdgeSelected = (edgeId: string | null) => {
    setSelectedStrategyEdgeId(edgeId);
    setSelectedStrategyNodeId(null);
  };

  const handleUpdateStrategyNodeData = (snId: string, dataUpdate: Partial<GraphStrategyNode["data"]>) => {
    const updatedNodes = data.strategyNodes.map((sn) =>
      sn.id === snId ? { ...sn, data: { ...sn.data, ...dataUpdate } } : sn
    );
    update({ strategyNodes: updatedNodes });
  };

  const handleUpdateEdge = (edgeId: string, edgeUpdate: Partial<GraphStrategyEdge>) => {
    const updatedEdges = data.strategyEdges.map((se) =>
      se.id === edgeId ? { ...se, ...edgeUpdate } : se
    );
    update({ strategyEdges: updatedEdges });
  };

  const handleDeleteStrategyNode = (snId: string) => {
    const sn = data.strategyNodes.find((n) => n.id === snId);
    if (sn?.type === "node-start" || sn?.type === "node-finish") return;
    const updatedNodes = data.strategyNodes.filter((n) => n.id !== snId);
    const updatedEdges = data.strategyEdges.filter((e) => e.source !== snId && e.target !== snId);
    update({ strategyNodes: updatedNodes, strategyEdges: updatedEdges });
    if (selectedStrategyNodeId === snId) setSelectedStrategyNodeId(null);
  };

  const handleDeleteEdge = (edgeId: string) => {
    const updatedEdges = data.strategyEdges.filter((e) => e.id !== edgeId);
    update({ strategyEdges: updatedEdges });
    setSelectedStrategyEdgeId(null);
  };

  const selectedNode = data.strategyNodes.find((sn) => sn.id === selectedStrategyNodeId);
  const selectedEdge = data.strategyEdges.find((se) => se.id === selectedStrategyEdgeId);

  return (
    <div className="fixed inset-y-0 right-0 w-[70%] bg-background border-l-2 border-indigo-500/40 z-50 flex flex-col shadow-2xl">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Workspace</span>
          <span className="text-muted-foreground">/</span>
          <span className="font-semibold">{data.name || "Graph Agent"}</span>
          <span className="text-muted-foreground">/</span>
          <span className="text-indigo-400">Strategy</span>
        </div>
        <Button variant="ghost" size="sm" onClick={onClose} className="h-7 w-7 p-0">
          <XIcon className="h-4 w-4" />
        </Button>
      </div>

      <GraphStrategyToolbar onAddNode={handleAddNode} />

      <div className="flex-1 flex overflow-hidden">
        <GraphStrategyCanvas
          strategyNodes={data.strategyNodes}
          strategyEdges={data.strategyEdges}
          onNodeSelected={handleNodeSelected}
          onEdgeSelected={handleEdgeSelected}
          onNodesChanged={handleNodesChanged}
          onEdgesChanged={handleEdgesChanged}
        />

        <div className="w-64 border-l bg-background overflow-y-auto">
          {selectedNode && (
            <div>
              <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Node Properties</div>
              <StrategyNodeProperties
                node={selectedNode}
                connectedTools={connectedToolNames}
                onUpdate={(dataUpdate) => handleUpdateStrategyNodeData(selectedNode.id, dataUpdate)}
              />
              {selectedNode.type !== "node-start" && selectedNode.type !== "node-finish" && (
                <div className="px-4 pb-4">
                  <Button variant="destructive" size="sm" className="w-full" onClick={() => handleDeleteStrategyNode(selectedNode.id)}>
                    Delete Node
                  </Button>
                </div>
              )}
            </div>
          )}
          {selectedEdge && (
            <div>
              <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Edge Properties</div>
              <StrategyEdgeProperties
                edge={selectedEdge}
                onUpdate={(edgeUpdate) => handleUpdateEdge(selectedEdge.id, edgeUpdate)}
                onDelete={() => handleDeleteEdge(selectedEdge.id)}
              />
            </div>
          )}
          {!selectedNode && !selectedEdge && (
            <div className="p-4 text-sm text-muted-foreground">Select a node or edge to edit properties.</div>
          )}
        </div>
      </div>
    </div>
  );
}
