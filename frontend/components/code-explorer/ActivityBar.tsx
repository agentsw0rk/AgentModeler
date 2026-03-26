"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { useA2AInspectorStore } from "@/store/a2aInspectorStore";
import { CodeIcon, ArrowRightLeftIcon } from "lucide-react";

export function ActivityBar() {
  const isCodeOpen = useCodeExplorerStore((s) => s.isOpen);
  const toggleCode = useCodeExplorerStore((s) => s.togglePanel);
  const isA2AOpen = useA2AInspectorStore((s) => s.isOpen);
  const toggleA2A = useA2AInspectorStore((s) => s.togglePanel);

  return (
    <div className="flex flex-col items-center w-10 bg-background border-r py-2 gap-3 shrink-0">
      <button
        onClick={toggleCode}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isCodeOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="Code Explorer"
      >
        <CodeIcon className="size-4" />
      </button>
      <button
        onClick={toggleA2A}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isA2AOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="A2A Inspector"
      >
        <ArrowRightLeftIcon className="size-4" />
      </button>
    </div>
  );
}
