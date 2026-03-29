import { create } from "zustand";
import { ValidationError } from "@/lib/validation";

interface ValidationState {
  errors: ValidationError[];
  showSuccess: boolean;
  setErrors: (errors: ValidationError[]) => void;
  clearErrors: () => void;
  setShowSuccess: (show: boolean) => void;
  errorsForNode: (nodeId: string) => ValidationError[];
  buildErrors: ValidationError[];
  buildLoading: boolean;
  setBuildErrors: (errors: ValidationError[]) => void;
  clearBuildErrors: () => void;
  setBuildLoading: (loading: boolean) => void;
}

export const useValidationStore = create<ValidationState>((set, get) => ({
  errors: [],
  showSuccess: false,
  setErrors: (errors) => set({ errors, showSuccess: errors.length === 0 }),
  clearErrors: () => set({ errors: [], showSuccess: false }),
  setShowSuccess: (show) => set({ showSuccess: show }),
  errorsForNode: (nodeId) => get().errors.filter((e) => e.nodeId === nodeId),
  buildErrors: [],
  buildLoading: false,
  setBuildErrors: (errors) => set({ buildErrors: errors }),
  clearBuildErrors: () => set({ buildErrors: [] }),
  setBuildLoading: (loading) => set({ buildLoading: loading }),
}));
