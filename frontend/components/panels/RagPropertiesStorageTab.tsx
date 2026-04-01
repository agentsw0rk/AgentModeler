"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { RagNodeConfig, RagStorageType } from "@/types/rag";

export function RagPropertiesStorageTab({ data, onUpdate }: { data: RagNodeConfig; onUpdate: (field: Partial<RagNodeConfig>) => void }) {
  return (
    <div className="space-y-3 pt-2">
      <div>
        <Label>Storage Type</Label>
        <Select value={data.storageType} onValueChange={(v) => onUpdate({ storageType: v as RagStorageType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="InMemory">In-Memory</SelectItem>
            <SelectItem value="File">File-based</SelectItem>
            <SelectItem value="Custom">Custom</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {data.storageType === "File" && (
        <div>
          <Label>Storage Path</Label>
          <Input
            value={data.storagePath}
            onChange={(e) => onUpdate({ storagePath: e.target.value })}
            placeholder="./vector-storage"
          />
        </div>
      )}
      {data.storageType === "Custom" && (
        <div>
          <Label>Custom Class (FQN)</Label>
          <Input
            value={data.customStorageClass}
            onChange={(e) => onUpdate({ customStorageClass: e.target.value })}
            placeholder="com.example.MyVectorStorage"
          />
        </div>
      )}
      <div>
        <Label>Similarity Threshold ({data.similarityThreshold})</Label>
        <input
          type="range"
          min="0"
          max="1"
          step="0.05"
          value={data.similarityThreshold}
          onChange={(e) => onUpdate({ similarityThreshold: parseFloat(e.target.value) })}
          className="w-full"
        />
      </div>
    </div>
  );
}
