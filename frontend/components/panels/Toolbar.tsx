"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { usePreviewStore } from "@/store/previewStore";
import { validateGraph } from "@/lib/validation";
import { ExportDialog } from "@/components/dialogs/ExportDialog";
import { SaveLoadDialog } from "@/components/dialogs/SaveLoadDialog";
import { ProjectGraph } from "@/types/graph";
import type { BuiltinToolType } from "@/types/graph";

const builtinToolOptions: { type: BuiltinToolType; label: string }[] = [
  { type: "think", label: "Think" },
  { type: "shell", label: "Shell" },
  { type: "filesystem", label: "Filesystem" },
  { type: "fetch", label: "Fetch" },
  { type: "memory", label: "Memory" },
  { type: "todo", label: "Todo" },
  { type: "user_prompt", label: "User Prompt" },
];

export function Toolbar({ onNewProject }: { onNewProject: () => void }) {
  const addNode = useEditorStore((s) => s.addNode);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const setErrors = useValidationStore((s) => s.setErrors);
  const previewIsOpen = usePreviewStore((s) => s.isOpen);
  const togglePreview = usePreviewStore((s) => s.toggleOpen);
  const resetPreview = usePreviewStore((s) => s.reset);
  const [toolMenuOpen, setToolMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setToolMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleValidate = () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    const errors = validateGraph(graph);
    setErrors(errors);
  };

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={onNewProject}>
        New Project
      </Button>
      <div className="w-px h-6 bg-border" />
      <Button variant="outline" size="sm" onClick={() => addNode("agent", { x: 100, y: 100 })}>
        + Agent
      </Button>
      <div className="relative" ref={menuRef}>
        <Button variant="outline" size="sm" onClick={() => setToolMenuOpen(!toolMenuOpen)}>
          + Tool ▾
        </Button>
        {toolMenuOpen && (
          <div className="absolute top-full left-0 mt-1 w-44 bg-popover border rounded-md shadow-md z-50 py-1">
            <button
              className="w-full text-left px-3 py-1.5 text-sm hover:bg-accent"
              onClick={() => { addNode("tool", { x: 300, y: 100 }); setToolMenuOpen(false); }}
            >
              Custom Tool
            </button>
            <div className="border-t my-1" />
            {builtinToolOptions.map((opt) => (
              <button
                key={opt.type}
                className="w-full text-left px-3 py-1.5 text-sm hover:bg-accent"
                onClick={() => {
                  addNode("builtin-tool", { x: 300, y: 100 }, opt.type);
                  setToolMenuOpen(false);
                }}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}
      </div>
      <Button variant="outline" size="sm" onClick={() => addNode("sub-agent", { x: 200, y: 200 })}>
        + Sub-Agent
      </Button>
      <Button variant="outline" size="sm" onClick={() => addNode("mcp-server", { x: 100, y: 300 })}>
        + MCP Server
      </Button>
      <div className="flex-1" />
      <Button variant="outline" size="sm" onClick={handleValidate}>
        Validate
      </Button>
      <Button
        variant={previewIsOpen ? "default" : "outline"}
        size="sm"
        onClick={() => {
          if (!previewIsOpen) {
            resetPreview();
          }
          togglePreview();
        }}
      >
        {previewIsOpen ? "■ Preview" : "▶ Preview"}
      </Button>
      <SaveLoadDialog />
      <ExportDialog />
    </div>
  );
}
