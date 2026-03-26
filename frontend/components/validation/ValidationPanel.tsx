"use client";

import { useValidationStore } from "@/store/validationStore";
import { Badge } from "@/components/ui/badge";

export function ValidationPanel() {
  const errors = useValidationStore((s) => s.errors);

  const errorCount = errors.filter((e) => e.severity === "error").length;
  const warningCount = errors.filter((e) => e.severity === "warning").length;

  return (
    <div className="w-64 border-l flex flex-col bg-background shrink-0">
      <div className="px-3 py-1.5 border-b bg-muted/30 flex items-center gap-2">
        <span className="text-xs font-medium">Validation</span>
        {errors.length === 0 ? (
          <Badge variant="secondary" className="text-xs">OK</Badge>
        ) : (
          <div className="flex gap-1">
            {errorCount > 0 && <Badge variant="destructive" className="text-xs">{errorCount}</Badge>}
            {warningCount > 0 && <Badge variant="secondary" className="text-xs">{warningCount}</Badge>}
          </div>
        )}
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {errors.length === 0 ? (
          <div className="text-xs text-muted-foreground text-center mt-4">
            No issues found
          </div>
        ) : (
          errors.map((err, i) => (
            <div key={i} className="flex items-start gap-1.5 text-xs py-1">
              <Badge variant={err.severity === "error" ? "destructive" : "secondary"} className="text-[10px] shrink-0 mt-0.5">
                {err.severity === "error" ? "E" : "W"}
              </Badge>
              <div>
                <span className="text-muted-foreground">[{err.nodeId}] </span>
                <span>{err.message}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
