"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { OutputNodeConfig, IODataType } from "@/types/io";
import { IO_DATA_TYPE_LABELS } from "@/types/io";

export function OutputProperties({ nodeId, data }: { nodeId: string; data: OutputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<OutputNodeConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="output-name">Name</Label>
        <Input id="output-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="result" />
      </div>
      <div>
        <Label htmlFor="output-desc">Description</Label>
        <Textarea id="output-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What this output represents" />
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
    </div>
  );
}
