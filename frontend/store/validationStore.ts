import { create } from "zustand";
import { ValidationError } from "@/lib/validation";

interface ValidationState {
  errors: ValidationError[];
  showSuccess: boolean;
  setErrors: (errors: ValidationError[]) => void;
  clearErrors: () => void;
  setShowSuccess: (show: boolean) => void;
  errorsForNode: (nodeId: string) => ValidationError[];
}

export const useValidationStore = create<ValidationState>((set, get) => ({
  errors: [],
  showSuccess: false,
  setErrors: (errors) => set({ errors, showSuccess: errors.length === 0 }),
  clearErrors: () => set({ errors: [], showSuccess: false }),
  setShowSuccess: (show) => set({ showSuccess: show }),
  errorsForNode: (nodeId) => get().errors.filter((e) => e.nodeId === nodeId),
}));
