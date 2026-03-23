"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useEditorStore } from "@/store/editorStore";
import { BuiltinToolConfig, BUILTIN_TOOL_LABELS } from "@/types/graph";
import type { BuiltinToolType } from "@/types/graph";

const toolDescriptions: Record<BuiltinToolType, string> = {
  think: "Adds a step-by-step reasoning instruction to the agent's system prompt.",
  shell: "Execute shell commands in the agent's environment.",
  filesystem: "Read, write, and list files and directories using Koog built-in file tools.",
  fetch: "Make HTTP requests to external APIs and web services.",
  memory: "Persistent key-value storage backed by SQLite.",
  todo: "In-memory task list management for complex workflows.",
  user_prompt: "Ask users questions and collect interactive input using Koog built-in tools.",
};

export function BuiltinToolProperties({ nodeId, data }: { nodeId: string; data: BuiltinToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<BuiltinToolConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label>Type</Label>
        <div className="text-sm font-medium mt-1">{BUILTIN_TOOL_LABELS[data.toolType]}</div>
      </div>
      <div>
        <Label>Description</Label>
        <p className="text-sm text-muted-foreground mt-1">{toolDescriptions[data.toolType]}</p>
      </div>

      {data.toolType === "shell" && (
        <div>
          <Label htmlFor="bt-workingDir">Working Directory</Label>
          <Input
            id="bt-workingDir"
            value={data.workingDir ?? ""}
            onChange={(e) => update({ workingDir: e.target.value || undefined })}
            placeholder="."
          />
        </div>
      )}

      {data.toolType === "filesystem" && (
        <div>
          <Label htmlFor="bt-rootDir">Root Directory</Label>
          <Input
            id="bt-rootDir"
            value={data.rootDir ?? ""}
            onChange={(e) => update({ rootDir: e.target.value || undefined })}
            placeholder="."
          />
        </div>
      )}

      {data.toolType === "fetch" && (
        <div>
          <Label htmlFor="bt-baseUrl">Base URL</Label>
          <Input
            id="bt-baseUrl"
            value={data.baseUrl ?? ""}
            onChange={(e) => update({ baseUrl: e.target.value || undefined })}
            placeholder="https://api.example.com"
          />
        </div>
      )}

      {data.toolType === "memory" && (
        <div>
          <Label htmlFor="bt-path">Database Path</Label>
          <Input
            id="bt-path"
            value={data.path ?? ""}
            onChange={(e) => update({ path: e.target.value || undefined })}
            placeholder="memory.db"
          />
        </div>
      )}
    </div>
  );
}
