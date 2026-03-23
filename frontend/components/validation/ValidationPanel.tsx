"use client";

import { useValidationStore } from "@/store/validationStore";
import { Badge } from "@/components/ui/badge";

export function ValidationPanel() {
  const errors = useValidationStore((s) => s.errors);

  if (errors.length === 0) return null;

  return (
    <div className="border-t p-2 bg-background space-y-1 max-h-40 overflow-auto">
      {errors.map((err, i) => (
        <div key={i} className="flex items-center gap-2 text-sm">
          <Badge variant={err.severity === "error" ? "destructive" : "secondary"}>
            {err.severity}
          </Badge>
          <span className="text-muted-foreground">[{err.nodeId}]</span>
          <span>{err.message}</span>
        </div>
      ))}
    </div>
  );
}
