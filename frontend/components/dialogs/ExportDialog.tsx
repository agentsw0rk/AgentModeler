"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore, buildWorkspaceGraph } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph, hasBlockingErrors } from "@/lib/validation";
import { exportToKotlin } from "@/lib/export-client";
import { usePreviewStore } from "@/store/previewStore";
import { useReviewStore } from "@/store/reviewStore";
import { startExportReview, ReviewSSEEvent } from "@/lib/review-client";
import { ReviewResult } from "@/types/review";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export function ExportDialog() {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<"idle" | "exporting" | "done" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState("");
  const workspaceName = useEditorStore((s) => s.workspaceName);
  const setErrors = useValidationStore((s) => s.setErrors);

  const handleExportAndReview = async () => {
    const graph = buildWorkspaceGraph();
    const errors = validateGraph(graph);
    setErrors(errors);
    if (hasBlockingErrors(errors)) {
      setStatus("error");
      setErrorMsg("Fix validation errors before exporting.");
      return;
    }

    // Close dialog and open bottom drawer on review tab
    setOpen(false);
    const previewStore = usePreviewStore.getState();
    if (!previewStore.isOpen) previewStore.toggleOpen();
    previewStore.setActiveTab("review");

    const reviewStore = useReviewStore.getState();
    reviewStore.reset();
    reviewStore.setExporting();

    try {
      await startExportReview(graph, (event: ReviewSSEEvent) => {
        switch (event.type) {
          case "export-ready": {
            const d = event.data as { downloadUrl: string; reviewJsonUrl: string };
            useReviewStore.getState().setReviewing(d.downloadUrl, d.reviewJsonUrl);
            break;
          }
          case "review-result": {
            useReviewStore.getState().setResult(event.data as unknown as ReviewResult);
            const url = useReviewStore.getState().downloadUrl;
            if (url) {
              const a = document.createElement("a");
              a.href = `${API_BASE}${url}`;
              a.download = `${workspaceName || "koog-agent"}.zip`;
              a.click();
            }
            break;
          }
          case "review-failed":
            useReviewStore.getState().setFailed((event.data as { message: string }).message || "Review failed");
            break;
        }
      });
    } catch (e) {
      useReviewStore.getState().setFailed(e instanceof Error ? e.message : String(e));
    }
  };

  const handleExport = async () => {
    const graph = buildWorkspaceGraph();
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
      a.download = `${workspaceName || "koog-agent"}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      setStatus("done");
    } catch (e: unknown) {
      setStatus("error");
      setErrorMsg(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button size="sm" />}>
        Export
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
          <div className="flex gap-2">
            <Button onClick={handleExport} disabled={status === "exporting"}>
              {status === "exporting" ? "Exporting..." : "Export"}
            </Button>
            <Button variant="outline" onClick={handleExportAndReview} disabled={status === "exporting"}>
              Export + Review
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
