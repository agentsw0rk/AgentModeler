import React, { useEffect, useRef } from "react";
import { useEditorStore, buildWorkspaceGraph } from "@/store/editorStore";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { generateCode, getFileTree } from "@/lib/vfs-client";
import { GenerationStatus } from "@/types/vfs";

const DEBOUNCE_MS = 800;

export function useCodeGeneration() {
  const abortRef = useRef<AbortController | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const unsubscribe = useEditorStore.subscribe((state, prev) => {
      const changed =
        state.nodes !== prev.nodes ||
        state.edges !== prev.edges ||
        state.workspaceName !== prev.workspaceName ||
        state.otelConfig !== prev.otelConfig ||
        state.activeBackend !== prev.activeBackend;

      if (!changed) return;

      const explorerState = useCodeExplorerStore.getState();

      if (!explorerState.isOpen) {
        if (explorerState.status !== "idle") {
          explorerState.markStale();
        }
        return;
      }

      explorerState.markStale();

      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (abortRef.current) abortRef.current.abort();

      timeoutRef.current = setTimeout(() => {
        triggerGeneration(abortRef);
      }, DEBOUNCE_MS);
    });

    return () => {
      unsubscribe();
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (abortRef.current) abortRef.current.abort();
    };
  }, []);

  // Regenerate on panel open if stale
  useEffect(() => {
    const unsubscribe = useCodeExplorerStore.subscribe((state, prev) => {
      if (state.isOpen && !prev.isOpen) {
        if (state.status === "stale" || state.status === "idle") {
          triggerGeneration(abortRef);
        }
      }
    });
    return unsubscribe;
  }, []);
}

async function triggerGeneration(
  abortRef: React.MutableRefObject<AbortController | null>,
) {
  const controller = new AbortController();
  abortRef.current = controller;
  const { signal } = controller;

  const store = useCodeExplorerStore.getState();
  store.setStatus("generating");

  const graph = buildWorkspaceGraph();
  const { workspaceName, activeBackend } = useEditorStore.getState();

  try {
    const result = await generateCode(workspaceName, graph, signal, activeBackend);

    if (result.status === GenerationStatus.INVALID) {
      useCodeExplorerStore.getState().setStatus("invalid");
      return;
    }

    const treeResponse = await getFileTree(workspaceName, signal);
    const explorerStore = useCodeExplorerStore.getState();
    explorerStore.setTree(treeResponse.root, treeResponse.generatedAt);
    explorerStore.clearFileCache();
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") return;
    useCodeExplorerStore.getState().setStatus("stale");
  }
}
