"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { ToolConfig, ToolFunction, ToolParameter, ToolParamType } from "@/types/koog";
import { TOOL_PARAM_TYPES, TOOL_PARAM_TYPE_LABELS } from "@/types/koog";
import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react";

export function ToolProperties({ nodeId, data }: { nodeId: string; data: ToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const [expandedFnIds, setExpandedFnIds] = useState<Set<string>>(new Set());
  const [expandedParamIds, setExpandedParamIds] = useState<Set<string>>(new Set());
  const [dragFnIdx, setDragFnIdx] = useState<number | null>(null);

  const update = (field: Partial<ToolConfig>) => updateNodeData(nodeId, field);
  const updateFunctions = (functions: ToolFunction[]) => update({ functions });

  const addFunction = () => {
    const fn: ToolFunction = {
      id: crypto.randomUUID(),
      name: "",
      description: "",
      parameters: [],
    };
    updateFunctions([...data.functions, fn]);
    setExpandedFnIds((s) => new Set(s).add(fn.id));
  };

  const updateFunction = (id: string, patch: Partial<ToolFunction>) => {
    updateFunctions(data.functions.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  };

  const removeFunction = (id: string) => {
    updateFunctions(data.functions.filter((f) => f.id !== id));
    setExpandedFnIds((s) => { const next = new Set(s); next.delete(id); return next; });
  };

  const toggleFnExpand = (id: string) => {
    setExpandedFnIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleFnDragStart = (idx: number) => setDragFnIdx(idx);
  const handleFnDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragFnIdx === null || dragFnIdx === idx) return;
    const reordered = [...data.functions];
    const [moved] = reordered.splice(dragFnIdx, 1);
    reordered.splice(idx, 0, moved);
    updateFunctions(reordered);
    setDragFnIdx(idx);
  };
  const handleFnDragEnd = () => setDragFnIdx(null);

  const addParam = (fnId: string) => {
    const param: ToolParameter = {
      id: crypto.randomUUID(),
      name: "",
      type: "String",
      description: "",
      required: true,
    };
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, { parameters: [...fn.parameters, param] });
    setExpandedParamIds((s) => new Set(s).add(param.id));
  };

  const updateParam = (fnId: string, paramId: string, patch: Partial<ToolParameter>) => {
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, {
      parameters: fn.parameters.map((p) => (p.id === paramId ? { ...p, ...patch } : p)),
    });
  };

  const removeParam = (fnId: string, paramId: string) => {
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, { parameters: fn.parameters.filter((p) => p.id !== paramId) });
    setExpandedParamIds((s) => { const next = new Set(s); next.delete(paramId); return next; });
  };

  const toggleParamExpand = (id: string) => {
    setExpandedParamIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="tool-name">ToolSet Name</Label>
        <Input
          id="tool-name"
          value={data.name}
          onChange={(e) => update({ name: e.target.value })}
          placeholder="MoneyTransferTools"
        />
      </div>

      <div>
        <Label htmlFor="tool-desc">ToolSet Description</Label>
        <Textarea
          id="tool-desc"
          value={data.description}
          onChange={(e) => update({ description: e.target.value })}
          rows={2}
          placeholder="Tools for money transfer operations."
          className="text-xs"
        />
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Functions</Label>
          <button onClick={addFunction} className="flex items-center gap-1 text-xs text-primary hover:underline">
            <Plus className="w-3 h-3" /> Add Function
          </button>
        </div>

        {data.functions.length === 0 && (
          <p className="text-xs text-muted-foreground">No functions yet. Add a function to define a tool.</p>
        )}

        <div className="space-y-1">
          {data.functions.map((fn, fnIdx) => {
            const fnExpanded = expandedFnIds.has(fn.id);
            return (
              <div
                key={fn.id}
                draggable
                onDragStart={() => handleFnDragStart(fnIdx)}
                onDragOver={(e) => handleFnDragOver(e, fnIdx)}
                onDragEnd={handleFnDragEnd}
                className="border rounded-md bg-muted/30"
              >
                <div className="flex items-center gap-1 px-2 py-1.5 cursor-pointer" onClick={() => toggleFnExpand(fn.id)}>
                  <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab shrink-0" />
                  <button className="shrink-0">
                    {fnExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                  </button>
                  <span className="text-xs font-medium truncate">{fn.name || "unnamed"}</span>
                  <span className="text-[10px] text-muted-foreground ml-auto shrink-0">
                    {fn.parameters.length} {fn.parameters.length === 1 ? "param" : "params"}
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); removeFunction(fn.id); }}
                    className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>

                {fnExpanded && (
                  <div className="px-3 pb-3 space-y-2 border-t">
                    <div className="pt-2">
                      <Label className="text-xs">Function Name</Label>
                      <Input
                        value={fn.name}
                        onChange={(e) => updateFunction(fn.id, { name: e.target.value })}
                        placeholder="getBalance"
                        className="h-7 text-xs"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Description (LLMDescription)</Label>
                      <Textarea
                        value={fn.description}
                        onChange={(e) => updateFunction(fn.id, { description: e.target.value })}
                        rows={2}
                        placeholder="Returns the current balance."
                        className="text-xs"
                      />
                    </div>

                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <Label className="text-xs">Parameters</Label>
                        <button onClick={() => addParam(fn.id)} className="flex items-center gap-1 text-[10px] text-primary hover:underline">
                          <Plus className="w-2.5 h-2.5" /> Add
                        </button>
                      </div>

                      {fn.parameters.length === 0 && (
                        <p className="text-[10px] text-muted-foreground">No parameters.</p>
                      )}

                      <div className="space-y-1">
                        {fn.parameters.map((param) => {
                          const paramExpanded = expandedParamIds.has(param.id);
                          return (
                            <div key={param.id} className="border rounded bg-background/50">
                              <div
                                className="flex items-center gap-1 px-2 py-1 cursor-pointer"
                                onClick={() => toggleParamExpand(param.id)}
                              >
                                <button className="shrink-0">
                                  {paramExpanded ? <ChevronDown className="w-2.5 h-2.5" /> : <ChevronRight className="w-2.5 h-2.5" />}
                                </button>
                                <span className="text-[11px] font-medium truncate">{param.name || "unnamed"}</span>
                                <span className="text-[10px] text-muted-foreground ml-auto shrink-0">{param.type}</span>
                                <button
                                  onClick={(e) => { e.stopPropagation(); removeParam(fn.id, param.id); }}
                                  className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                                >
                                  <Trash2 className="w-2.5 h-2.5" />
                                </button>
                              </div>

                              {paramExpanded && (
                                <div className="px-2 pb-2 space-y-1.5 border-t">
                                  <div className="pt-1.5">
                                    <Label className="text-[10px]">Name</Label>
                                    <Input
                                      value={param.name}
                                      onChange={(e) => updateParam(fn.id, param.id, { name: e.target.value })}
                                      placeholder="userId"
                                      className="h-6 text-[11px]"
                                    />
                                  </div>
                                  <div>
                                    <Label className="text-[10px]">Type</Label>
                                    <Select value={param.type} onValueChange={(v) => updateParam(fn.id, param.id, { type: v as ToolParamType })}>
                                      <SelectTrigger className="h-6 text-[11px]"><SelectValue /></SelectTrigger>
                                      <SelectContent>
                                        {TOOL_PARAM_TYPES.map((t) => (
                                          <SelectItem key={t} value={t}>{TOOL_PARAM_TYPE_LABELS[t]}</SelectItem>
                                        ))}
                                      </SelectContent>
                                    </Select>
                                  </div>
                                  <div>
                                    <Label className="text-[10px]">Description</Label>
                                    <Input
                                      value={param.description}
                                      onChange={(e) => updateParam(fn.id, param.id, { description: e.target.value })}
                                      placeholder="The unique identifier of the user."
                                      className="h-6 text-[11px]"
                                    />
                                  </div>
                                  <div className="flex items-center justify-between">
                                    <Label className="text-[10px]">Required</Label>
                                    <button
                                      onClick={() => updateParam(fn.id, param.id, { required: !param.required, defaultValue: param.required ? "" : undefined })}
                                      className={`relative inline-flex h-4 w-7 items-center rounded-full transition-colors ${param.required ? "bg-primary" : "bg-muted"}`}
                                    >
                                      <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${param.required ? "translate-x-3.5" : "translate-x-0.5"}`} />
                                    </button>
                                  </div>
                                  {!param.required && (
                                    <div>
                                      <Label className="text-[10px]">Default Value</Label>
                                      <Input
                                        value={param.defaultValue || ""}
                                        onChange={(e) => updateParam(fn.id, param.id, { defaultValue: e.target.value || undefined })}
                                        placeholder="false"
                                        className="h-6 text-[11px]"
                                      />
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
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
