import { create } from "zustand";
import { ReviewResult } from "@/types/review";

interface ReviewState {
  status: "idle" | "exporting" | "reviewing" | "done" | "failed";
  result: ReviewResult | null;
  downloadUrl: string | null;
  reviewJsonUrl: string | null;
  error: string | null;
  setExporting: () => void;
  setReviewing: (downloadUrl: string, reviewJsonUrl: string) => void;
  setResult: (result: ReviewResult) => void;
  setFailed: (error: string) => void;
  reset: () => void;
}

export const useReviewStore = create<ReviewState>((set) => ({
  status: "idle",
  result: null,
  downloadUrl: null,
  reviewJsonUrl: null,
  error: null,
  setExporting: () => set({ status: "exporting", error: null }),
  setReviewing: (downloadUrl, reviewJsonUrl) => set({ status: "reviewing", downloadUrl, reviewJsonUrl }),
  setResult: (result) => set({ status: "done", result }),
  setFailed: (error) => set({ status: "failed", error }),
  reset: () => set({ status: "idle", result: null, downloadUrl: null, reviewJsonUrl: null, error: null }),
}));
