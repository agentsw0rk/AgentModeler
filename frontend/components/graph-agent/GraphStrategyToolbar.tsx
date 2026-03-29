"use client";

import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
} from "@/components/ui/navigation-menu";
import type { GraphStrategyNodeType } from "@/types/graph-agent";

export function GraphStrategyToolbar({ onAddNode, hideSubgraph }: { onAddNode: (type: GraphStrategyNodeType) => void; hideSubgraph?: boolean }) {
  return (
    <div className="flex items-center gap-2 px-4 py-2 border-b border-border">
      <NavigationMenu>
        <NavigationMenuList>
          <NavigationMenuItem>
            <NavigationMenuTrigger className="gap-2">
              <span className="text-blue-400">+</span> LLM
            </NavigationMenuTrigger>
            <NavigationMenuContent>
              <div className="w-48">
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-llm-request")}>
                  LLM Request
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-llm-streaming")}>
                  LLM Streaming
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-llm-structured")}>
                  LLM Structured
                </NavigationMenuLink>
              </div>
            </NavigationMenuContent>
          </NavigationMenuItem>

          <NavigationMenuItem>
            <NavigationMenuTrigger className="gap-2">
              <span className="text-emerald-400">+</span> Tools
            </NavigationMenuTrigger>
            <NavigationMenuContent>
              <div className="w-48">
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-execute-tool")}>
                  Execute Tool
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-execute-multiple-tools")}>
                  Execute Multi Tools
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-llm-send-tool-result")}>
                  Send Result
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-send-multiple-results")}>
                  Send Multi Results
                </NavigationMenuLink>
              </div>
            </NavigationMenuContent>
          </NavigationMenuItem>

          <NavigationMenuItem>
            <NavigationMenuTrigger className="gap-2">
              <span className="text-purple-400">+</span> Flow
            </NavigationMenuTrigger>
            <NavigationMenuContent>
              <div className="w-48">
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-condition")}>
                  Condition
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-compress-history")}>
                  Compress History
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-do-nothing")}>
                  Do Nothing
                </NavigationMenuLink>
                <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-custom")}>
                  Custom
                </NavigationMenuLink>
                {!hideSubgraph && (
                  <NavigationMenuLink className="cursor-pointer" onClick={() => onAddNode("node-subgraph")}>
                    Subgraph
                  </NavigationMenuLink>
                )}
              </div>
            </NavigationMenuContent>
          </NavigationMenuItem>
        </NavigationMenuList>
      </NavigationMenu>
    </div>
  );
}
