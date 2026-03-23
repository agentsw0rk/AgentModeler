"use client";

import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";

export function Toolbar() {
  const addNode = useEditorStore((s) => s.addNode);

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={() => addNode("agent", { x: 100, y: 100 })}>
        + Agent
      </Button>
      <Button variant="outline" size="sm" onClick={() => addNode("tool", { x: 300, y: 100 })}>
        + Tool
      </Button>
    </div>
  );
}
