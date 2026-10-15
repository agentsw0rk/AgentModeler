"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useReviewStore } from "@/store/reviewStore";
import { FindingSeverity } from "@/types/review";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

const statusConfig: Record<string, { label: string; variant: "default" | "secondary" | "destructive" }> = {
  pass: { label: "PASS", variant: "default" },
  warning: { label: "WARNING", variant: "secondary" },
  critical: { label: "CRITICAL", variant: "destructive" },
  unavailable: { label: "UNAVAILABLE", variant: "secondary" },
};

const severityColors: Record<FindingSeverity, string> = {
  critical: "text-red-500",
  warning: "text-yellow-500",
  suggestion: "text-blue-400",
};

const severityIcons: Record<FindingSeverity, string> = {
  critical: "✗",
  warning: "⚠",
  suggestion: "→",
};

export function ExportReviewPanel() {
  const status = useReviewStore((s) => s.status);
  const result = useReviewStore((s) => s.result);
  const downloadUrl = useReviewStore((s) => s.downloadUrl);
  const reviewJsonUrl = useReviewStore((s) => s.reviewJsonUrl);
  const error = useReviewStore((s) => s.error);
  const [expandedFiles, setExpandedFiles] = useState<Set<string>>(new Set());

  const toggleFile = (file: string) => {
    setExpandedFiles((prev) => {
      const next = new Set(prev);
      if (next.has(file)) next.delete(file);
      else next.add(file);
      return next;
    });
  };

  if (status === "idle") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">Use "Export + Review" to generate and review your agent code</p>
      </div>
    );
  }

  if (status === "exporting") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">Exporting...</p>
      </div>
    );
  }

  if (status === "reviewing") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">LLM is reviewing your code...</p>
      </div>
    );
  }

  if (status === "failed") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-destructive text-xs">{error || "Review failed"}</p>
      </div>
    );
  }

  if (!result) return null;

  const config = statusConfig[result.status] || statusConfig.unavailable;

  const grouped: Record<FindingSeverity, typeof result.findings> = {
    critical: result.findings.filter((f) => f.severity === "critical"),
    warning: result.findings.filter((f) => f.severity === "warning"),
    suggestion: result.findings.filter((f) => f.severity === "suggestion"),
  };

  return (
    <div className="flex-1 overflow-y-auto p-3 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge variant={config.variant}>{config.label}</Badge>
          <span className="text-sm">{result.summary}</span>
        </div>
        <div className="flex gap-1">
          {downloadUrl && (
            <a href={`${API_BASE}${downloadUrl}`} download>
              <Button variant="outline" size="sm" className="h-6 text-xs">ZIP</Button>
            </a>
          )}
          {reviewJsonUrl && (
            <a href={`${API_BASE}${reviewJsonUrl}`} download>
              <Button variant="outline" size="sm" className="h-6 text-xs">JSON</Button>
            </a>
          )}
        </div>
      </div>

      {(["critical", "warning", "suggestion"] as FindingSeverity[]).map((severity) =>
        grouped[severity].length > 0 ? (
          <div key={severity} className="space-y-1">
            <div className={`text-xs font-semibold uppercase ${severityColors[severity]}`}>
              {severity} ({grouped[severity].length})
            </div>
            {grouped[severity].map((finding, i) => (
              <div key={i} className="flex gap-2 text-xs">
                <span className={severityColors[finding.severity]}>{severityIcons[finding.severity]}</span>
                <div>
                  {finding.file && (
                    <button
                      className="font-mono text-muted-foreground hover:text-foreground mr-1"
                      onClick={() => toggleFile(finding.file!)}
                    >
                      [{finding.file}]
                    </button>
                  )}
                  <span className="text-muted-foreground mr-1">[{finding.category}]</span>
                  <span>{finding.message}</span>
                </div>
              </div>
            ))}
          </div>
        ) : null
      )}

      {result.findings.length === 0 && (
        <p className="text-sm text-green-500">No issues found — code looks good!</p>
      )}

      <div className="text-[10px] text-muted-foreground pt-2 border-t">
        Model: {result.metadata.model} · {result.metadata.reviewedFiles} files reviewed
        {result.metadata.skippedFiles > 0 && ` · ${result.metadata.skippedFiles} skipped`}
        {" "}· {(result.metadata.durationMs / 1000).toFixed(1)}s
      </div>
    </div>
  );
}
