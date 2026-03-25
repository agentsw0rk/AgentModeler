"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { Badge } from "@/components/ui/badge";
import { FileTree } from "./FileTree";
import { LoaderIcon } from "lucide-react";

const statusConfig: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
  idle: { label: "idle", variant: "outline" },
  generating: { label: "generating", variant: "secondary" },
  valid: { label: "valid", variant: "default" },
  stale: { label: "stale", variant: "outline" },
  invalid: { label: "invalid", variant: "destructive" },
};

export function CodeExplorerPanel() {
  const isOpen = useCodeExplorerStore((s) => s.isOpen);
  const status = useCodeExplorerStore((s) => s.status);
  const tree = useCodeExplorerStore((s) => s.tree);

  if (!isOpen) return null;

  const config = statusConfig[status] || statusConfig.idle;

  return (
    <div className="w-60 bg-background border-r flex flex-col shrink-0 overflow-hidden">
      <div className="h-8 border-b flex items-center px-2.5 gap-2 shrink-0">
        <span className="text-[10px] font-semibold tracking-wider text-muted-foreground uppercase flex-1">
          Code Explorer
        </span>
        <Badge variant={config.variant} className="h-4 text-[9px] px-1.5 gap-1">
          {status === "generating" && <LoaderIcon className="size-2.5 animate-spin" />}
          {config.label}
        </Badge>
      </div>
      <div className="flex-1 overflow-y-auto">
        {status === "idle" && !tree ? (
          <div className="flex items-center justify-center h-full px-4">
            <p className="text-muted-foreground text-xs text-center">
              Add nodes to your graph to see generated code here
            </p>
          </div>
        ) : (
          <FileTree />
        )}
        {status === "invalid" && tree && (
          <div className="px-2 py-1.5 border-t">
            <p className="text-[10px] text-destructive">
              Graph has validation errors — showing last valid state
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
