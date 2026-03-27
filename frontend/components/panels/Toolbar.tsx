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
import { useEditorStore } from "@/store/editorStore";
import { useHistoryStore } from "@/store/historyStore";
import { ToolsMenuDialog } from "@/components/dialogs/ToolsMenuDialog";
import type { BuiltinToolType } from "@/types/graph";
import { BotIcon, WrenchIcon, UsersIcon, ServerIcon, ClipboardListIcon, ArrowDownToLineIcon, ArrowUpFromLineIcon, NetworkIcon } from "lucide-react";
import { GraphAgentTemplateDialog } from "@/components/dialogs/GraphAgentTemplateDialog";
import { TEMPLATES } from "@/components/graph-agent/templates";
import type { GraphAgentTemplate } from "@/types/graph-agent";

const builtinToolOptions: { type: BuiltinToolType; label: string }[] = [
  { type: "think", label: "Think" },
  { type: "shell", label: "Shell" },
  { type: "filesystem", label: "Filesystem" },
  { type: "fetch", label: "Fetch" },
  { type: "memory", label: "Memory" },
  { type: "todo", label: "Todo" },
  { type: "user_prompt", label: "User Prompt" },
];

export function Toolbar({ onNewWorkspace, mcpConnected = false }: { onNewWorkspace: () => void; mcpConnected?: boolean }) {
  const addNode = useEditorStore((s) => s.addNode);
  const past = useHistoryStore((s) => s.past);
  const future = useHistoryStore((s) => s.future);
  const undo = useHistoryStore((s) => s.undo);
  const redo = useHistoryStore((s) => s.redo);

  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);

  const handleTemplateSelect = (template: GraphAgentTemplate) => {
    const t = TEMPLATES[template];
    addNode("graph-agent", { x: 100, y: 100 });
    const state = useEditorStore.getState();
    const newNode = state.nodes[state.nodes.length - 1];
    if (newNode && newNode.type === "graph-agent") {
      useEditorStore.getState().updateNodeData(newNode.id, {
        strategyNodes: structuredClone(t.nodes),
        strategyEdges: structuredClone(t.edges),
      });
    }
    setTemplateDialogOpen(false);
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
              <BotIcon className="size-4 text-blue-400" /> Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <span className="text-border select-none">|</span>

          <NavigationMenuItem>
            <NavigationMenuTrigger className="gap-2"><WrenchIcon className="size-4 text-green-400" /> Tool</NavigationMenuTrigger>
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
              <UsersIcon className="size-4 shrink-0 text-yellow-400" /> Sub-Agent
            </NavigationMenuLink>
          </NavigationMenuItem>

          <span className="text-border select-none">|</span>

          <NavigationMenuItem>
            <NavigationMenuLink
              className="cursor-pointer gap-2"
              onClick={() => addNode("mcp-server", { x: 100, y: 300 })}
            >
              <ServerIcon className="size-4 shrink-0 text-purple-400" /> MCP Server
            </NavigationMenuLink>
          </NavigationMenuItem>
        </NavigationMenuList>
      </NavigationMenu>

      <Button
        variant="ghost"
        size="sm"
        onClick={() => addNode("planner-agent", { x: 100, y: 400 })}
        title="Add Planner Agent"
      >
        <ClipboardListIcon className="h-4 w-4 mr-1 text-pink-400" />
        Agent Planner
      </Button>

      <span className="text-border select-none">|</span>

      <Button
        variant="ghost"
        size="sm"
        onClick={() => addNode("input", { x: 100, y: 100 })}
        title="Add Input"
      >
        <ArrowDownToLineIcon className="h-4 w-4 mr-1 text-cyan-400" />
        Input
      </Button>

      <Button
        variant="ghost"
        size="sm"
        onClick={() => addNode("output", { x: 100, y: 200 })}
        title="Add Output"
      >
        <ArrowUpFromLineIcon className="h-4 w-4 mr-1 text-orange-400" />
        Output
      </Button>

      <span className="text-border select-none">|</span>

      <Button
        variant="ghost"
        size="sm"
        onClick={() => setTemplateDialogOpen(true)}
        title="Add Graph Agent"
      >
        <NetworkIcon className="h-4 w-4 mr-1 text-indigo-400" />
        Graph Agent
      </Button>

      <GraphAgentTemplateDialog
        open={templateDialogOpen}
        onSelect={handleTemplateSelect}
        onClose={() => setTemplateDialogOpen(false)}
      />

      <div className="flex-1" />
      <ToolsMenuDialog mcpConnected={mcpConnected} />
    </div>
  );
}
