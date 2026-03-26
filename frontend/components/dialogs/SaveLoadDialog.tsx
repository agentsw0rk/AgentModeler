"use client";

import { useRef } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore, buildWorkspaceGraph, DEFAULT_OTEL_CONFIG } from "@/store/editorStore";
import { deserializeWorkspace, downloadWorkspace } from "@/lib/persistence";

export function SaveLoadDialog({ open, onOpenChange }: { open?: boolean; onOpenChange?: (open: boolean) => void } = {}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const reset = useEditorStore((s) => s.reset);
  const isControlled = open !== undefined;

  const handleSave = () => {
    const graph = buildWorkspaceGraph();
    downloadWorkspace(graph);
  };

  const handleLoad = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const graph = deserializeWorkspace(e.target?.result as string);
        reset();
        useEditorStore.setState({
          nodes: graph.nodes,
          edges: graph.edges,
          workspaceName: graph.name,
          otelConfig: graph.otelConfig ?? { ...DEFAULT_OTEL_CONFIG },
        });
      } catch (err: unknown) {
        alert(`Failed to load workspace: ${err instanceof Error ? err.message : String(err)}`);
      }
    };
    reader.readAsText(file);
  };

  return (
    <Dialog open={isControlled ? open : undefined} onOpenChange={isControlled ? onOpenChange : undefined}>
      {!isControlled && (
        <DialogTrigger render={<Button variant="outline" size="sm" />}>
          Save/Load
        </DialogTrigger>
      )}
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Workspace</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <Button onClick={handleSave} className="w-full">Save Workspace (JSON)</Button>
          <div>
            <Button variant="outline" onClick={() => fileInputRef.current?.click()} className="w-full">
              Load Workspace
            </Button>
            <input ref={fileInputRef} type="file" accept=".json" onChange={handleLoad} className="hidden" />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
