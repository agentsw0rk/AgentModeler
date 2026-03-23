import { create } from "zustand";
import { ValidationError } from "@/lib/validation";

interface ValidationState {
  errors: ValidationError[];
  setErrors: (errors: ValidationError[]) => void;
  clearErrors: () => void;
  errorsForNode: (nodeId: string) => ValidationError[];
}

export const useValidationStore = create<ValidationState>((set, get) => ({
  errors: [],
  setErrors: (errors) => set({ errors }),
  clearErrors: () => set({ errors: [] }),
  errorsForNode: (nodeId) => get().errors.filter((e) => e.nodeId === nodeId),
}));
