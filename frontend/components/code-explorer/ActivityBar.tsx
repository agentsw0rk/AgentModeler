"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { CodeIcon } from "lucide-react";

export function ActivityBar() {
  const isOpen = useCodeExplorerStore((s) => s.isOpen);
  const togglePanel = useCodeExplorerStore((s) => s.togglePanel);

  return (
    <div className="flex flex-col items-center w-10 bg-background border-r py-2 gap-3 shrink-0">
      <button
        onClick={togglePanel}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="Code Explorer"
      >
        <CodeIcon className="size-4" />
      </button>
    </div>
  );
}
