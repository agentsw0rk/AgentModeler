"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { InputNodeConfig, IODataType } from "@/types/io";
import { IO_DATA_TYPE_LABELS } from "@/types/io";

export function InputProperties({ nodeId, data }: { nodeId: string; data: InputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<InputNodeConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="input-name">Name</Label>
        <Input id="input-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="query" />
      </div>
      <div>
        <Label htmlFor="input-desc">Description</Label>
        <Textarea id="input-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What this input represents" />
      </div>
      <div>
        <Label>Data Type</Label>
        <Select value={data.dataType} onValueChange={(v) => update({ dataType: v as IODataType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {Object.entries(IO_DATA_TYPE_LABELS).map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center justify-between">
        <Label>Required</Label>
        <button
          onClick={() => update({ required: !data.required })}
          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${data.required ? "bg-primary" : "bg-muted"}`}
        >
          <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${data.required ? "translate-x-4.5" : "translate-x-0.5"}`} />
        </button>
      </div>
      {!data.required && (
        <div>
          <Label htmlFor="input-default">Default Value</Label>
          <Input id="input-default" value={data.defaultValue || ""} onChange={(e) => update({ defaultValue: e.target.value || undefined })} placeholder="Default value" />
        </div>
      )}
    </div>
  );
}
