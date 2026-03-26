"use client";

import { useCallback, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { usePreviewStore, generateStepId, generateMsgId } from "@/store/previewStore";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph, hasBlockingErrors } from "@/lib/validation";
import { ExecutionTimeline } from "./ExecutionTimeline";
import { ChatArea } from "./ChatArea";
import { ExportReviewPanel } from "@/components/review/ExportReviewPanel";
import { AgentChatPanel } from "@/components/agent-chat/AgentChatPanel";
import { PlanInspector } from "@/components/planner/PlanInspector";
import { WorkspaceGraph } from "@/types/graph";
import { PreviewEvent } from "@/types/preview";
import { PlannerAgentConfig } from "@/types/planner";
import * as realClient from "@/lib/preview-client";
import * as mockClient from "@/lib/preview-mock";

const statusBadge: Record<string, { label: string; variant: "default" | "secondary" | "destructive" }> = {
  idle: { label: "Idle", variant: "secondary" },
  connecting: { label: "Connecting...", variant: "default" },
  running: { label: "Running", variant: "default" },
  success: { label: "Done", variant: "secondary" },
  error: { label: "Error", variant: "destructive" },
};

export function PreviewPanel() {
  const status = usePreviewStore((s) => s.status);
  const sessionId = usePreviewStore((s) => s.sessionId);
  const useMock = usePreviewStore((s) => s.useMock);
  const activeTab = usePreviewStore((s) => s.activeTab);
  const setActiveTab = usePreviewStore((s) => s.setActiveTab);
  const toggleMock = usePreviewStore((s) => s.toggleMock);
  const reset = usePreviewStore((s) => s.reset);
  const setStatus = usePreviewStore((s) => s.setStatus);
  const setSessionId = usePreviewStore((s) => s.setSessionId);
  const setError = usePreviewStore((s) => s.setError);
  const addMessage = usePreviewStore((s) => s.addMessage);
  const addStep = usePreviewStore((s) => s.addStep);
  const updateStep = usePreviewStore((s) => s.updateStep);

  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const workspaceName = useEditorStore((s) => s.workspaceName);
  const selectedNodeIds = useEditorStore((s) => s.selectedNodeIds);
  const setErrors = useValidationStore((s) => s.setErrors);

  const isPlannerSelected = (() => {
    if (selectedNodeIds.length !== 1) return false;
    const selectedId = selectedNodeIds[0];
    const node = nodes.find((n) => n.id === selectedId);
    return node?.type === "planner-agent";
  })();
  const selectedPlannerData = (() => {
    if (!isPlannerSelected) return null;
    const selectedId = selectedNodeIds[0];
    return nodes.find((n) => n.id === selectedId)?.data as PlannerAgentConfig | null;
  })();

  const abortRef = useRef<AbortController | null>(null);
  const currentStepRef = useRef<string | null>(null);

  const client = useMock ? mockClient : realClient;

  const handleEvent = useCallback((event: PreviewEvent) => {
    switch (event.type) {
      case "session-created":
        setSessionId(event.data.sessionId);
        break;
      case "agent-initialized": {
        const stepId = generateStepId();
        addStep({ id: stepId, type: "init", label: "Agent initialized", status: "done", durationMs: event.data.durationMs });
        break;
      }
      case "llm-call-started": {
        const stepId = generateStepId();
        currentStepRef.current = stepId;
        addStep({ id: stepId, type: "llm", label: `LLM call (${event.data.model})`, status: "running" });
        break;
      }
      case "llm-call-completed":
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, {
            status: "done",
            durationMs: event.data.durationMs,
            detail: `${event.data.tokens} tokens`,
          });
          currentStepRef.current = null;
        }
        break;
      case "tool-call-started": {
        const stepId = generateStepId();
        currentStepRef.current = stepId;
        addStep({ id: stepId, type: "tool", label: `Tool: ${event.data.tool}`, status: "running" });
        break;
      }
      case "tool-call-completed":
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, {
            status: "done",
            durationMs: event.data.durationMs,
            detail: event.data.result || undefined,
          });
          currentStepRef.current = null;
        }
        break;
      case "agent-message":
        addMessage({ id: generateMsgId(), role: "agent", content: event.data.content });
        break;
      case "error":
        setError(event.data.message);
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, { status: "error" });
          currentStepRef.current = null;
        }
        break;
      case "stream-complete":
        setStatus("success");
        break;
    }
  }, [setSessionId, addStep, updateStep, addMessage, setError, setStatus]);

  const handleSendMessage = useCallback(async (message: string) => {
    const graph: WorkspaceGraph = { version: 1, name: workspaceName, nodes, edges };

    addMessage({ id: generateMsgId(), role: "user", content: message });

    if (!sessionId) {
      const errors = validateGraph(graph);
      setErrors(errors);
      if (hasBlockingErrors(errors)) {
        setError("Fix validation errors before running preview.");
        return;
      }

      setStatus("connecting");
      try {
        const abort = await client.startPreview(graph, message, handleEvent);
        abortRef.current = abort;
        setStatus("running");
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    } else {
      setStatus("running");
      try {
        await client.sendMessage(sessionId, message);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }
  }, [sessionId, nodes, edges, workspaceName, client, handleEvent, setStatus, setError, addMessage, setErrors]);

  const handleStop = useCallback(async () => {
    abortRef.current?.abort();
    if (sessionId) {
      try { await client.stopPreview(sessionId); } catch { /* ignore */ }
    }
    reset();
  }, [sessionId, client, reset]);

  const [panelHeight, setPanelHeight] = useState(300);
  const dragRef = useRef<{ startY: number; startHeight: number } | null>(null);

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragRef.current = { startY: e.clientY, startHeight: panelHeight };

    const handleDragMove = (ev: MouseEvent) => {
      if (!dragRef.current) return;
      const delta = dragRef.current.startY - ev.clientY;
      const newHeight = Math.max(120, Math.min(600, dragRef.current.startHeight + delta));
      setPanelHeight(newHeight);
    };

    const handleDragEnd = () => {
      dragRef.current = null;
      document.removeEventListener("mousemove", handleDragMove);
      document.removeEventListener("mouseup", handleDragEnd);
    };

    document.addEventListener("mousemove", handleDragMove);
    document.addEventListener("mouseup", handleDragEnd);
  }, [panelHeight]);

  const badge = statusBadge[status] || statusBadge.idle;

  return (
    <div className="border-t flex flex-col" style={{ height: panelHeight }}>
      <div
        className="h-1.5 cursor-row-resize bg-transparent hover:bg-blue-500/30 active:bg-blue-500/50 transition-colors shrink-0"
        onMouseDown={handleDragStart}
      />
      <div className="flex items-center justify-between px-3 py-1.5 border-b bg-muted/30">
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1">
            <button
              className={`text-xs px-2 py-0.5 rounded ${activeTab === "preview" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
              onClick={() => setActiveTab("preview")}
            >
              Preview
            </button>
            <button
              className={`text-xs px-2 py-0.5 rounded ${activeTab === "review" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
              onClick={() => setActiveTab("review")}
            >
              Export Review
            </button>
            <button
              className={`text-xs px-2 py-0.5 rounded ${activeTab === "agent-chat" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
              onClick={() => setActiveTab("agent-chat")}
            >
              Agent Chat
            </button>
          </div>
          <Badge variant={badge.variant}>{badge.label}</Badge>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            className="h-6 text-xs"
            onClick={toggleMock}
          >
            {useMock ? "Mock ✓" : "Mock"}
          </Button>
          {(status === "running" || status === "connecting") && (
            <Button
              variant="outline"
              size="sm"
              className="h-6 text-xs text-destructive"
              onClick={handleStop}
            >
              Stop
            </Button>
          )}
        </div>
      </div>
      {activeTab === "preview" && isPlannerSelected && selectedPlannerData ? (
        <div className="flex-1 overflow-y-auto">
          <PlanInspector data={selectedPlannerData} />
        </div>
      ) : activeTab === "preview" ? (
        <div className="flex flex-1 overflow-hidden">
          <div className="w-56 border-r overflow-y-auto">
            <ExecutionTimeline />
          </div>
          <ChatArea onSendMessage={handleSendMessage} />
        </div>
      ) : activeTab === "agent-chat" ? (
        <AgentChatPanel />
      ) : (
        <ExportReviewPanel />
      )}
    </div>
  );
}
