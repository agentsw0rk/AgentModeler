"use client";

import { usePreviewStore } from "@/store/previewStore";

const statusIcons: Record<string, string> = {
  running: "⟳",
  done: "✓",
  error: "✗",
};

const statusColors: Record<string, string> = {
  running: "text-blue-500",
  done: "text-green-500",
  error: "text-red-500",
};

export function ExecutionTimeline() {
  const steps = usePreviewStore((s) => s.steps);

  if (steps.length === 0) {
    return (
      <div className="p-3 text-xs text-muted-foreground">
        Waiting for execution...
      </div>
    );
  }

  return (
    <div className="p-3 space-y-2 overflow-y-auto">
      <div className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">
        Execution Steps
      </div>
      {steps.map((step) => (
        <div key={step.id} className="flex gap-2 items-start text-xs">
          <span className={statusColors[step.status] || "text-muted-foreground"}>
            {statusIcons[step.status] || "○"}
          </span>
          <div>
            <div className="font-medium">{step.label}</div>
            {step.durationMs !== undefined && (
              <div className="text-muted-foreground text-[10px]">
                {step.durationMs < 1000
                  ? `${step.durationMs}ms`
                  : `${(step.durationMs / 1000).toFixed(1)}s`}
              </div>
            )}
            {step.detail && (
              <div className="text-muted-foreground text-[10px]">{step.detail}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
