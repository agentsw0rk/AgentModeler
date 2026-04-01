"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import type { RagNodeConfig } from "@/types/rag";

function ListEditor({ label, items, onUpdate, placeholder }: { label: string; items: string[]; onUpdate: (items: string[]) => void; placeholder: string }) {
  return (
    <div>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <div className="space-y-1 mt-1">
        {items.map((item, i) => (
          <div key={i} className="flex gap-1 items-center">
            <Input
              value={item}
              onChange={(e) => {
                const updated = [...items];
                updated[i] = e.target.value;
                onUpdate(updated);
              }}
              placeholder={placeholder}
              className="text-xs"
            />
            <Button variant="ghost" size="sm" onClick={() => onUpdate(items.filter((_, j) => j !== i))} className="text-xs px-2">x</Button>
          </div>
        ))}
        <Button variant="outline" size="sm" onClick={() => onUpdate([...items, ""])} className="text-xs">+ Add</Button>
      </div>
    </div>
  );
}

export function RagPropertiesDocumentsTab({ data, onUpdate }: { data: RagNodeConfig; onUpdate: (field: Partial<RagNodeConfig>) => void }) {
  return (
    <div className="space-y-3 pt-2">
      <ListEditor
        label="File Paths"
        items={data.filePaths}
        onUpdate={(filePaths) => onUpdate({ filePaths })}
        placeholder="./docs/products"
      />
      <ListEditor
        label="URL Sources"
        items={data.urlSources}
        onUpdate={(urlSources) => onUpdate({ urlSources })}
        placeholder="https://example.com/docs"
      />
      <div>
        <Label>Max Documents per Query</Label>
        <Input
          type="number"
          min={1}
          max={50}
          value={data.maxDocuments}
          onChange={(e) => onUpdate({ maxDocuments: parseInt(e.target.value) || 5 })}
        />
      </div>
    </div>
  );
}
