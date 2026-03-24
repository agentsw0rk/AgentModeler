"use client";

import { useState, useEffect } from "react";
import { Toolbar } from "@/components/panels/Toolbar";
import { Canvas } from "@/components/editor/Canvas";
import { PropertiesPanel } from "@/components/panels/PropertiesPanel";
import { ValidationPanel } from "@/components/validation/ValidationPanel";
import { PreviewPanel } from "@/components/preview/PreviewPanel";
import { NewProjectDialog } from "@/components/dialogs/NewProjectDialog";
import { useEditorStore, DEFAULT_PROJECT_NAME } from "@/store/editorStore";
import { copySelectedNodes, pasteNodes } from "@/store/clipboardStore";

export default function EditorPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isInitial, setIsInitial] = useState(false);
  const nodes = useEditorStore((s) => s.nodes);
  const projectName = useEditorStore((s) => s.projectName);
  const reset = useEditorStore((s) => s.reset);
  const setProjectName = useEditorStore((s) => s.setProjectName);

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
    if (nodes.length === 0 && projectName === DEFAULT_PROJECT_NAME) {
      setIsInitial(true);
      setDialogOpen(true);
    }
  }, []);

  const handleCreateProject = (name: string) => {
    reset();
    setProjectName(name);
  };

  const handleNewProjectRequest = () => {
    if (nodes.length > 0) {
      if (!window.confirm("Unsaved changes will be lost. Continue?")) return;
    }
    setIsInitial(false);
    setDialogOpen(true);
  };

  return (
    <div className="flex flex-col h-screen">
      <Toolbar onNewProject={handleNewProjectRequest} />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
        <PropertiesPanel />
      </div>
      <PreviewPanel />
      <ValidationPanel />
      <NewProjectDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        isInitial={isInitial}
        onCreateProject={handleCreateProject}
      />
    </div>
  );
}
