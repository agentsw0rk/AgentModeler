export type ReviewStatus = "pass" | "warning" | "critical" | "unavailable";
export type FindingSeverity = "critical" | "warning" | "suggestion";

export interface ReviewResult {
  status: ReviewStatus;
  summary: string;
  findings: ReviewFinding[];
  metadata: ReviewMetadata;
}

export interface ReviewFinding {
  severity: FindingSeverity;
  category: string;
  file: string | null;
  message: string;
}

export interface ReviewMetadata {
  model: string;
  reviewedFiles: number;
  skippedFiles: number;
  durationMs: number;
}
