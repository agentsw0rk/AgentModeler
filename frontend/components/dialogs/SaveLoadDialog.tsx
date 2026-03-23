"use client";

import { useRef } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { deserializeProject, downloadProject } from "@/lib/persistence";
import { ProjectGraph } from "@/types/graph";

export function SaveLoadDialog() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const reset = useEditorStore((s) => s.reset);

  const handleSave = () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    downloadProject(graph);
  };

  const handleLoad = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const graph = deserializeProject(e.target?.result as string);
        reset();
        useEditorStore.setState({
          nodes: graph.nodes,
          edges: graph.edges,
          projectName: graph.name,
        });
      } catch (err: unknown) {
        alert(`Failed to load project: ${err instanceof Error ? err.message : String(err)}`);
      }
    };
    reader.readAsText(file);
  };

  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        Save/Load
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Project</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <Button onClick={handleSave} className="w-full">Save Project (JSON)</Button>
          <div>
            <Button variant="outline" onClick={() => fileInputRef.current?.click()} className="w-full">
              Load Project
            </Button>
            <input ref={fileInputRef} type="file" accept=".json" onChange={handleLoad} className="hidden" />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
