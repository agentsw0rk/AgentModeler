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
import { useState, useEffect } from "react";
import { useEditorStore, buildWorkspaceGraph } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { usePreviewStore } from "@/store/previewStore";
import { useHistoryStore } from "@/store/historyStore";
import { validateGraph } from "@/lib/validation";
import { ExportDialog } from "@/components/dialogs/ExportDialog";
import { KeyboardShortcutsDialog } from "@/components/dialogs/KeyboardShortcutsDialog";
import { SaveLoadDialog } from "@/components/dialogs/SaveLoadDialog";
import type { BuiltinToolType } from "@/types/graph";
import { BotIcon, WrenchIcon, UsersIcon, ServerIcon } from "lucide-react";

const builtinToolOptions: { type: BuiltinToolType; label: string }[] = [
  { type: "think", label: "Think" },
  { type: "shell", label: "Shell" },
  { type: "filesystem", label: "Filesystem" },
  { type: "fetch", label: "Fetch" },
  { type: "memory", label: "Memory" },
  { type: "todo", label: "Todo" },
  { type: "user_prompt", label: "User Prompt" },
];

export function Toolbar({ onNewWorkspace }: { onNewWorkspace: () => void }) {
  const addNode = useEditorStore((s) => s.addNode);
  const setErrors = useValidationStore((s) => s.setErrors);
  const previewIsOpen = usePreviewStore((s) => s.isOpen);
  const togglePreview = usePreviewStore((s) => s.toggleOpen);
  const resetPreview = usePreviewStore((s) => s.reset);
  const past = useHistoryStore((s) => s.past);
  const future = useHistoryStore((s) => s.future);
  const undo = useHistoryStore((s) => s.undo);
  const redo = useHistoryStore((s) => s.redo);

  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const handleValidate = () => {
    const graph = buildWorkspaceGraph();
    const errors = validateGraph(graph);
    setErrors(errors);
  };

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={onNewWorkspace}>
        New Workspace
      </Button>
      <div className="w-px h-6 bg-border" />
      <Button variant="outline" size="sm" onClick={undo} disabled={mounted && past.length === 0}>
        Undo
      </Button>
      <Button variant="outline" size="sm" onClick={redo} disabled={mounted && future.length === 0}>
        Redo
      </Button>
      <div className="w-px h-6 bg-border" />

      <NavigationMenu>
        <NavigationMenuList className="whitespace-nowrap">
          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer gap-2"
              onClick={() => addNode("agent", { x: 100, y: 100 })}
            >
              <BotIcon className="size-4" /> Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <span className="text-border select-none">|</span>

          <NavigationMenuItem>
            <NavigationMenuTrigger className="gap-2"><WrenchIcon className="size-4" /> Tool</NavigationMenuTrigger>
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

          <span className="text-border select-none">|</span>

          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer gap-2"
              onClick={() => addNode("sub-agent", { x: 200, y: 200 })}
            >
              <UsersIcon className="size-4 shrink-0" /> Sub-Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <span className="text-border select-none">|</span>

          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer gap-2"
              onClick={() => addNode("mcp-server", { x: 100, y: 300 })}
            >
              <ServerIcon className="size-4 shrink-0" /> MCP Server
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
      <KeyboardShortcutsDialog />
    </div>
  );
}
