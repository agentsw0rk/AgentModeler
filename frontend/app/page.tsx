"use client";

import { useState, useEffect } from "react";
import { Toolbar } from "@/components/panels/Toolbar";
import { Canvas } from "@/components/editor/Canvas";
import { PropertiesPanel } from "@/components/panels/PropertiesPanel";
import { ValidationPanel } from "@/components/validation/ValidationPanel";
import { PreviewPanel } from "@/components/preview/PreviewPanel";
import { NewWorkspaceDialog } from "@/components/dialogs/NewWorkspaceDialog";
import { useEditorStore, DEFAULT_WORKSPACE_NAME, buildWorkspaceGraph } from "@/store/editorStore";
import { connectEditorSse, EditorSseEvent } from "@/lib/editor-sse-client";
import { syncGraph } from "@/lib/editor-sync-client";
import { copySelectedNodes, pasteNodes } from "@/store/clipboardStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
import { ActivityBar } from "@/components/code-explorer/ActivityBar";
import { CodeExplorerPanel } from "@/components/code-explorer/CodeExplorerPanel";
import { CodeViewerModal } from "@/components/code-explorer/CodeViewerModal";
import { useCodeGeneration } from "@/hooks/useCodeGeneration";
import { A2AInspectorPanel } from "@/components/a2a/A2AInspectorPanel";

export default function EditorPage() {
  useCodeGeneration();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isInitial, setIsInitial] = useState(false);
  const [mcpConnected, setMcpConnected] = useState(false);
  const nodes = useEditorStore((s) => s.nodes);
  const workspaceName = useEditorStore((s) => s.workspaceName);
  const reset = useEditorStore((s) => s.reset);
  const setWorkspaceName = useEditorStore((s) => s.setWorkspaceName);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) return;

      const mod = e.metaKey || e.ctrlKey;

      if (mod && e.key === "z" && !e.shiftKey) {
        e.preventDefault();
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const { useHistoryStore } = require("@/store/historyStore");
        useHistoryStore.getState().undo();
      } else if (mod && e.key === "z" && e.shiftKey) {
        e.preventDefault();
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const { useHistoryStore } = require("@/store/historyStore");
        useHistoryStore.getState().redo();
      } else if (mod && e.key === "c") {
        e.preventDefault();
        copySelectedNodes();
      } else if (mod && e.key === "v") {
        e.preventDefault();
        pasteNodes();
      } else if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        const { selectedNodeIds, removeNode } = useEditorStore.getState();
        for (const id of selectedNodeIds) {
          removeNode(id);
        }
      }
    };

    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, []);

  useEffect(() => {
    let timeout: ReturnType<typeof setTimeout>;
    const unsubscribe = useEditorStore.subscribe((state) => {
      clearTimeout(timeout);
      timeout = setTimeout(() => {
        const graph = { version: 1 as const, name: state.workspaceName, nodes: state.nodes, edges: state.edges };
        const errors = validateGraph(graph);
        useValidationStore.getState().setErrors(errors);
      }, 500);
    });
    return () => { unsubscribe(); clearTimeout(timeout); };
  }, []);

  useEffect(() => {
    if (nodes.length === 0 && workspaceName === DEFAULT_WORKSPACE_NAME) {
      setIsInitial(true);
      setDialogOpen(true);
    }
  }, []);

  useEffect(() => {
    const handleEvent = (event: EditorSseEvent) => {
      const s = useEditorStore.getState();
      switch (event.type) {
        case "node-created":
          if (event.node) s.applyRemoteNodeCreated(event.node);
          break;
        case "node-updated":
          if (event.nodeId && event.data) s.applyRemoteNodeUpdated(event.nodeId, event.data);
          break;
        case "node-deleted":
          if (event.nodeId) s.applyRemoteNodeDeleted(event.nodeId);
          break;
        case "edge-created":
          if (event.edge) s.applyRemoteEdgeCreated(event.edge);
          break;
        case "edge-deleted":
          if (event.edgeId) s.applyRemoteEdgeDeleted(event.edgeId);
          break;
        case "graph-reset":
          if (event.graph) s.applyRemoteGraphReset(event.graph);
          break;
        case "workspace-renamed":
          if (event.name) s.applyRemoteWorkspaceRenamed(event.name);
          break;
      }
    };

    const graph = buildWorkspaceGraph();
    syncGraph(graph)
      .then(() => setMcpConnected(true))
      .catch(() => setMcpConnected(false));

    const disconnect = connectEditorSse((event) => {
      setMcpConnected(true);
      handleEvent(event);
    });

    return () => {
      disconnect();
      setMcpConnected(false);
    };
  }, []);

  const handleCreateWorkspace = (name: string) => {
    reset();
    setWorkspaceName(name);
  };

  const handleNewWorkspaceRequest = () => {
    if (nodes.length > 0) {
      if (!window.confirm("Unsaved changes will be lost. Continue?")) return;
    }
    setIsInitial(false);
    setDialogOpen(true);
  };

  return (
    <div className="flex h-screen">
      <ActivityBar />
      <CodeExplorerPanel />
      <div className="flex flex-col flex-1 overflow-hidden">
        <Toolbar onNewWorkspace={handleNewWorkspaceRequest} mcpConnected={mcpConnected} />
        <div className="flex flex-1 overflow-hidden">
          <Canvas />
          <PropertiesPanel />
          <A2AInspectorPanel />
        </div>
        <div className="flex border-t overflow-hidden" style={{ minHeight: 0 }}>
          <div className="flex-1 min-w-0">
            <PreviewPanel />
          </div>
          <ValidationPanel />
        </div>
      </div>
      <NewWorkspaceDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        isInitial={isInitial}
        onCreateWorkspace={handleCreateWorkspace}
      />
      <CodeViewerModal />
    </div>
  );
}
