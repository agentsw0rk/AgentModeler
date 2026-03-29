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
  const [editingSubgraphId, setEditingSubgraphId] = useState<string | null>(null);
  const [selectedSubgraphNodeId, setSelectedSubgraphNodeId] = useState<string | null>(null);
  const [selectedSubgraphEdgeId, setSelectedSubgraphEdgeId] = useState<string | null>(null);

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
      "node-execute-multiple-tools": "executeMultipleTools",
      "node-send-multiple-results": "sendMultipleResults",
      "node-compress-history": "compressHistory",
      "node-llm-streaming": "llmStreaming",
      "node-llm-structured": "llmStructured",
      "node-do-nothing": "doNothing",
      "node-custom": "custom",
      "node-subgraph": "subgraph",
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

  // Subgraph editing
  const editingSubgraphNode = editingSubgraphId ? data.strategyNodes.find(sn => sn.id === editingSubgraphId) : null;
  const subgraphNodes = editingSubgraphNode?.data.subgraphNodes || [];
  const subgraphEdges = editingSubgraphNode?.data.subgraphEdges || [];

  const handleNodeDoubleClick = (snId: string) => {
    const sn = data.strategyNodes.find(n => n.id === snId);
    if (sn?.type === "node-subgraph") {
      if (!sn.data.subgraphNodes || sn.data.subgraphNodes.length === 0) {
        handleUpdateStrategyNodeData(snId, {
          subgraphNodes: [
            { id: `sn-${nextStrategyNodeId++}`, type: "node-start", position: { x: 100, y: 200 }, data: {} },
            { id: `sn-${nextStrategyNodeId++}`, type: "node-finish", position: { x: 600, y: 200 }, data: {} },
          ],
          subgraphEdges: [],
        });
      }
      setEditingSubgraphId(snId);
      setSelectedStrategyNodeId(null);
      setSelectedStrategyEdgeId(null);
    }
  };

  const handleAddSubgraphNode = (type: GraphStrategyNodeType) => {
    if (!editingSubgraphId) return;
    const id = `sn-${nextStrategyNodeId++}`;
    const subgraphDefaultLabels: Record<string, string> = {
      "node-llm-request": "llmRequest",
      "node-execute-tool": "executeTool",
      "node-llm-send-tool-result": "sendResult",
      "node-condition": "condition",
      "node-execute-multiple-tools": "executeMultipleTools",
      "node-send-multiple-results": "sendMultipleResults",
      "node-compress-history": "compressHistory",
      "node-llm-streaming": "llmStreaming",
      "node-llm-structured": "llmStructured",
      "node-do-nothing": "doNothing",
      "node-custom": "custom",
    };
    const newNode: GraphStrategyNode = {
      id,
      type,
      position: { x: 300, y: 200 },
      data: { label: subgraphDefaultLabels[type] || type },
    };
    const innerNodes = [...subgraphNodes, newNode];
    handleUpdateStrategyNodeData(editingSubgraphId, { subgraphNodes: innerNodes });
  };

  const handleSubgraphNodesChanged = (nodes: GraphStrategyNode[]) => {
    if (editingSubgraphId) handleUpdateStrategyNodeData(editingSubgraphId, { subgraphNodes: nodes });
  };

  const handleSubgraphEdgesChanged = (edges: GraphStrategyEdge[]) => {
    if (editingSubgraphId) handleUpdateStrategyNodeData(editingSubgraphId, { subgraphEdges: edges });
  };

  const handleSubgraphNodeSelected = (snId: string | null) => {
    setSelectedSubgraphNodeId(snId);
    setSelectedSubgraphEdgeId(null);
  };

  const handleSubgraphEdgeSelected = (edgeId: string | null) => {
    setSelectedSubgraphEdgeId(edgeId);
    setSelectedSubgraphNodeId(null);
  };

  const selectedSubgraphNode = subgraphNodes.find(sn => sn.id === selectedSubgraphNodeId);
  const selectedSubgraphEdge = subgraphEdges.find(se => se.id === selectedSubgraphEdgeId);

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
          onNodeDoubleClick={handleNodeDoubleClick}
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

      {editingSubgraphId && editingSubgraphNode && (
        <div className="fixed inset-y-0 right-0 w-[70%] bg-background border-l-2 border-orange-500/40 z-[60] flex flex-col shadow-2xl">
          <div className="flex items-center justify-between px-4 py-3 border-b border-border">
            <div className="flex items-center gap-2 text-sm">
              <button className="text-muted-foreground hover:text-foreground" onClick={() => setEditingSubgraphId(null)}>Strategy</button>
              <span className="text-muted-foreground">/</span>
              <span className="text-orange-400">Subgraph: {editingSubgraphNode.data.subgraphName || "Unnamed"}</span>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setEditingSubgraphId(null)} className="h-7 w-7 p-0">
              <XIcon className="h-4 w-4" />
            </Button>
          </div>

          <GraphStrategyToolbar onAddNode={handleAddSubgraphNode} hideSubgraph />

          <div className="flex-1 flex overflow-hidden">
            <GraphStrategyCanvas
              strategyNodes={subgraphNodes}
              strategyEdges={subgraphEdges}
              onNodeSelected={handleSubgraphNodeSelected}
              onEdgeSelected={handleSubgraphEdgeSelected}
              onNodesChanged={handleSubgraphNodesChanged}
              onEdgesChanged={handleSubgraphEdgesChanged}
            />

            <div className="w-64 border-l bg-background overflow-y-auto">
              {selectedSubgraphNode && (
                <div>
                  <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Node Properties</div>
                  <StrategyNodeProperties
                    node={selectedSubgraphNode}
                    connectedTools={connectedToolNames}
                    onUpdate={(dataUpdate) => {
                      const updatedInnerNodes = subgraphNodes.map(sn =>
                        sn.id === selectedSubgraphNode.id ? { ...sn, data: { ...sn.data, ...dataUpdate } } : sn
                      );
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphNodes: updatedInnerNodes });
                    }}
                  />
                  {selectedSubgraphNode.type !== "node-start" && selectedSubgraphNode.type !== "node-finish" && (
                    <div className="px-4 pb-4">
                      <Button variant="destructive" size="sm" className="w-full" onClick={() => {
                        const updatedNodes = subgraphNodes.filter(sn => sn.id !== selectedSubgraphNode.id);
                        const updatedEdges = subgraphEdges.filter(se => se.source !== selectedSubgraphNode.id && se.target !== selectedSubgraphNode.id);
                        handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphNodes: updatedNodes, subgraphEdges: updatedEdges });
                        setSelectedSubgraphNodeId(null);
                      }}>
                        Delete Node
                      </Button>
                    </div>
                  )}
                </div>
              )}
              {selectedSubgraphEdge && (
                <div>
                  <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Edge Properties</div>
                  <StrategyEdgeProperties
                    edge={selectedSubgraphEdge}
                    onUpdate={(edgeUpdate) => {
                      const updatedInnerEdges = subgraphEdges.map(se =>
                        se.id === selectedSubgraphEdge.id ? { ...se, ...edgeUpdate } : se
                      );
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphEdges: updatedInnerEdges });
                    }}
                    onDelete={() => {
                      const updatedInnerEdges = subgraphEdges.filter(se => se.id !== selectedSubgraphEdge.id);
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphEdges: updatedInnerEdges });
                      setSelectedSubgraphEdgeId(null);
                    }}
                  />
                </div>
              )}
              {!selectedSubgraphNode && !selectedSubgraphEdge && (
                <div className="p-4 text-sm text-muted-foreground">Select a node or edge to edit properties.</div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
