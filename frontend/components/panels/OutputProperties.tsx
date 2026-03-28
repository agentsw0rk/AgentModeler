"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { OutputNodeConfig, IOField, IOFieldType } from "@/types/io";
import { IO_FIELD_TYPE_LABELS, IO_FIELD_TYPES } from "@/types/io";
import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react";

export function OutputProperties({ nodeId, data }: { nodeId: string; data: OutputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const updateFields = (fields: IOField[]) => updateNodeData(nodeId, { fields });

  const addField = () => {
    const field: IOField = {
      id: crypto.randomUUID(),
      name: "",
      type: "String",
      description: "",
      optional: false,
    };
    updateFields([...data.fields, field]);
    setExpandedIds((s) => new Set(s).add(field.id));
  };

  const updateField = (id: string, patch: Partial<IOField>) => {
    updateFields(data.fields.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  };

  const removeField = (id: string) => {
    updateFields(data.fields.filter((f) => f.id !== id));
    setExpandedIds((s) => { const next = new Set(s); next.delete(id); return next; });
  };

  const toggleExpand = (id: string) => {
    setExpandedIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleDragStart = (idx: number) => setDragIdx(idx);
  const handleDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const reordered = [...data.fields];
    const [moved] = reordered.splice(dragIdx, 1);
    reordered.splice(idx, 0, moved);
    updateFields(reordered);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="io-classname">Class Name</Label>
        <Input
          id="io-classname"
          value={data.className || ""}
          onChange={(e) => updateNodeData(nodeId, { className: e.target.value || undefined })}
          placeholder="Auto-generated from agent name"
          className="text-xs"
        />
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Fields</Label>
          <button onClick={addField} className="flex items-center gap-1 text-xs text-primary hover:underline">
            <Plus className="w-3 h-3" /> Add Field
          </button>
        </div>

        {data.fields.length === 0 && (
          <p className="text-xs text-muted-foreground">No fields yet. Add a field to define the output structure.</p>
        )}

        <div className="space-y-1">
          {data.fields.map((field, idx) => {
            const expanded = expandedIds.has(field.id);
            return (
              <div
                key={field.id}
                draggable
                onDragStart={() => handleDragStart(idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDragEnd={handleDragEnd}
                className="border rounded-md bg-muted/30"
              >
                <div className="flex items-center gap-1 px-2 py-1.5 cursor-pointer" onClick={() => toggleExpand(field.id)}>
                  <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab shrink-0" />
                  <button className="shrink-0">
                    {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                  </button>
                  <span className="text-xs font-medium truncate">{field.name || "unnamed"}</span>
                  <span className="text-[10px] text-muted-foreground ml-auto shrink-0">{field.type}{field.optional ? "?" : ""}</span>
                  <button
                    onClick={(e) => { e.stopPropagation(); removeField(field.id); }}
                    className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
                {expanded && (
                  <div className="px-3 pb-3 space-y-2 border-t">
                    <div className="pt-2">
                      <Label className="text-xs">Name</Label>
                      <Input
                        value={field.name}
                        onChange={(e) => updateField(field.id, { name: e.target.value })}
                        placeholder="fieldName"
                        className="h-7 text-xs"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Type</Label>
                      <Select value={field.type} onValueChange={(v) => updateField(field.id, { type: v as IOFieldType })}>
                        <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {IO_FIELD_TYPES.map((t) => (
                            <SelectItem key={t} value={t}>{IO_FIELD_TYPE_LABELS[t]}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <Label className="text-xs">Description (LLMDescription)</Label>
                      <Textarea
                        value={field.description}
                        onChange={(e) => updateField(field.id, { description: e.target.value })}
                        rows={2}
                        placeholder="What this field represents"
                        className="text-xs"
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <Label className="text-xs">Optional</Label>
                      <button
                        onClick={() => updateField(field.id, { optional: !field.optional })}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${field.optional ? "bg-primary" : "bg-muted"}`}
                      >
                        <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${field.optional ? "translate-x-4.5" : "translate-x-0.5"}`} />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
