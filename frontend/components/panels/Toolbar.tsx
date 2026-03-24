"use client";

import { Button } from "@/components/ui/button";
import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
} from "@/components/ui/navigation-menu";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { usePreviewStore } from "@/store/previewStore";
import { useHistoryStore } from "@/store/historyStore";
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
  const past = useHistoryStore((s) => s.past);
  const future = useHistoryStore((s) => s.future);
  const undo = useHistoryStore((s) => s.undo);
  const redo = useHistoryStore((s) => s.redo);

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
      <Button variant="outline" size="sm" onClick={undo} disabled={past.length === 0}>
        Undo
      </Button>
      <Button variant="outline" size="sm" onClick={redo} disabled={future.length === 0}>
        Redo
      </Button>
      <div className="w-px h-6 bg-border" />

      <NavigationMenu>
        <NavigationMenuList>
          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer"
              onClick={() => addNode("agent", { x: 100, y: 100 })}
            >
              + Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <NavigationMenuItem>
            <NavigationMenuTrigger>+ Tool</NavigationMenuTrigger>
            <NavigationMenuContent>
              <div className="w-48">
                <NavigationMenuLink
                  className="cursor-pointer"
                  onClick={() => addNode("tool", { x: 300, y: 100 })}
                >
                  Custom Tool
                </NavigationMenuLink>
                <div className="border-t my-1" />
                {builtinToolOptions.map((opt) => (
                  <NavigationMenuLink
                    key={opt.type}
                    className="cursor-pointer"
                    onClick={() => addNode("builtin-tool", { x: 300, y: 100 }, opt.type)}
                  >
                    {opt.label}
                  </NavigationMenuLink>
                ))}
              </div>
            </NavigationMenuContent>
          </NavigationMenuItem>

          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer"
              onClick={() => addNode("sub-agent", { x: 200, y: 200 })}
            >
              + Sub-Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer"
              onClick={() => addNode("mcp-server", { x: 100, y: 300 })}
            >
              + MCP Server
            </NavigationMenuLink>
          </NavigationMenuItem>
        </NavigationMenuList>
      </NavigationMenu>

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
