"use client";

import { useEffect } from "react";
import { useValidationStore } from "@/store/validationStore";
import { Badge } from "@/components/ui/badge";

export function ValidationPanel() {
  const errors = useValidationStore((s) => s.errors);
  const showSuccess = useValidationStore((s) => s.showSuccess);
  const setShowSuccess = useValidationStore((s) => s.setShowSuccess);

  useEffect(() => {
    if (showSuccess) {
      const timer = setTimeout(() => setShowSuccess(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [showSuccess, setShowSuccess]);

  if (errors.length === 0 && !showSuccess) return null;

  if (errors.length === 0 && showSuccess) {
    return (
      <div className="border-t p-2 bg-background">
        <span className="text-sm text-green-500">No issues found ✓</span>
      </div>
    );
  }

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
