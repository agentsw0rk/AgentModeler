"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import type { RagNodeConfig } from "@/types/rag";

export function RagPropertiesModeTab({ data, onUpdate }: { data: RagNodeConfig; onUpdate: (field: Partial<RagNodeConfig>) => void }) {
  const neitherActive = !data.injectAsContext && !data.provideAsTool;

  return (
    <div className="space-y-3 pt-2">
      <div className="flex items-center justify-between">
        <Label>Inject as Context</Label>
        <input
          type="checkbox"
          checked={data.injectAsContext}
          onChange={(e) => onUpdate({ injectAsContext: e.target.checked })}
          className="h-4 w-4 rounded border border-input accent-primary"
        />
      </div>
      <div className="flex items-center justify-between">
        <Label>Provide as Tool</Label>
        <input
          type="checkbox"
          checked={data.provideAsTool}
          onChange={(e) => onUpdate({ provideAsTool: e.target.checked })}
          className="h-4 w-4 rounded border border-input accent-primary"
        />
      </div>
      {neitherActive && (
        <div className="text-xs text-destructive bg-destructive/10 rounded p-2">
          At least one mode must be active.
        </div>
      )}
      {data.provideAsTool && (
        <div>
          <Label>Tool Description</Label>
          <Input
            value={data.toolDescription}
            onChange={(e) => onUpdate({ toolDescription: e.target.value })}
            placeholder="Search for relevant documents"
          />
        </div>
      )}
    </div>
  );
}
