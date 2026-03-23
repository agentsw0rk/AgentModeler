"use client";

import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
import { ExportDialog } from "@/components/dialogs/ExportDialog";
import { SaveLoadDialog } from "@/components/dialogs/SaveLoadDialog";
import { ProjectGraph } from "@/types/graph";

export function Toolbar() {
  const addNode = useEditorStore((s) => s.addNode);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const setErrors = useValidationStore((s) => s.setErrors);

  const handleValidate = () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    const errors = validateGraph(graph);
    setErrors(errors);
  };

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={() => addNode("agent", { x: 100, y: 100 })}>
        + Agent
      </Button>
      <Button variant="outline" size="sm" onClick={() => addNode("tool", { x: 300, y: 100 })}>
        + Tool
      </Button>
      <div className="flex-1" />
      <Button variant="outline" size="sm" onClick={handleValidate}>
        Validate
      </Button>
      <SaveLoadDialog />
      <ExportDialog />
    </div>
  );
}
