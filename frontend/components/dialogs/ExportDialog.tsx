"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph, hasBlockingErrors } from "@/lib/validation";
import { exportToKotlin } from "@/lib/export-client";
import { ProjectGraph } from "@/types/graph";

export function ExportDialog() {
  const [status, setStatus] = useState<"idle" | "exporting" | "done" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState("");
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const setErrors = useValidationStore((s) => s.setErrors);

  const handleExport = async () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    const errors = validateGraph(graph);
    setErrors(errors);

    if (hasBlockingErrors(errors)) {
      setStatus("error");
      setErrorMsg("Fix validation errors before exporting.");
      return;
    }

    setStatus("exporting");
    try {
      const blob = await exportToKotlin(graph);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${projectName || "koog-agent"}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      setStatus("done");
    } catch (e: unknown) {
      setStatus("error");
      setErrorMsg(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Dialog>
      <DialogTrigger render={<Button size="sm" />}>
        Export Kotlin
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Export Koog Agent</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Export your agent configuration as compilable Kotlin code.
          </p>
          {status === "error" && <p className="text-sm text-destructive">{errorMsg}</p>}
          {status === "done" && <p className="text-sm text-green-600">Export successful!</p>}
          <Button onClick={handleExport} disabled={status === "exporting"}>
            {status === "exporting" ? "Exporting..." : "Export"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
