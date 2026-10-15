"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ModelProvider, MODEL_OPTIONS, PROVIDER_LABELS } from "@/types/koog";
import { fetchOllamaModels, OllamaModelsResponse } from "@/lib/ollama-client";

const CUSTOM = "__custom__";

interface ModelFieldsProps {
  provider: ModelProvider;
  modelId?: string;
  onChange: (patch: { modelProvider?: ModelProvider; modelId?: string }) => void;
}

/** Model provider + model selection shared by all agent types. */
export function ModelFields({ provider, modelId, onChange }: ModelFieldsProps) {
  return (
    <>
      <div>
        <Label>Model Provider</Label>
        {/* A model id only makes sense for its provider, so switching resets it */}
        <Select value={provider} onValueChange={(v) => onChange({ modelProvider: v as ModelProvider, modelId: undefined })}>
          <SelectTrigger><SelectValue>{(v: ModelProvider) => PROVIDER_LABELS[v] ?? v}</SelectValue></SelectTrigger>
          <SelectContent>
            {(Object.keys(PROVIDER_LABELS) as ModelProvider[]).map((p) => (
              <SelectItem key={p} value={p}>{PROVIDER_LABELS[p]}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label>Model</Label>
        {provider === "ollama" ? (
          <OllamaModelSelect modelId={modelId} onChange={(id) => onChange({ modelId: id })} />
        ) : (
          <Select value={modelId || ""} onValueChange={(v) => onChange({ modelId: v || undefined })}>
            <SelectTrigger><SelectValue placeholder="Default" /></SelectTrigger>
            <SelectContent>
              {MODEL_OPTIONS[provider].map((m) => (
                <SelectItem key={m.id} value={m.id}>{m.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>
    </>
  );
}

function OllamaModelSelect({ modelId, onChange }: { modelId?: string; onChange: (id: string | undefined) => void }) {
  const [installed, setInstalled] = useState<OllamaModelsResponse | null>(null);
  const [customMode, setCustomMode] = useState(false);

  useEffect(() => {
    let active = true;
    fetchOllamaModels().then((r) => active && setInstalled(r));
    return () => { active = false; };
  }, []);

  // Installed models first (they work right away), then suggestions not yet pulled
  const installedIds = new Set(installed?.models.map((m) => m.id));
  const options = [
    ...(installed?.models ?? []).map((m) => ({ id: m.id, label: m.tools ? m.label : `${m.label} — no tool calling` })),
    ...MODEL_OPTIONS.ollama.filter((m) => !installedIds.has(m.id)),
  ];
  const isCustom = customMode || (!!modelId && !options.some((m) => m.id === modelId));
  const isInstalled = !!modelId && installedIds.has(modelId);

  return (
    <div className="space-y-2">
      <Select
        value={isCustom ? CUSTOM : modelId || ""}
        onValueChange={(v) => {
          setCustomMode(v === CUSTOM);
          if (v !== CUSTOM) onChange(v || undefined);
        }}
      >
        <SelectTrigger><SelectValue placeholder="Default (llama3.2)" /></SelectTrigger>
        <SelectContent>
          {options.map((m) => (
            <SelectItem key={m.id} value={m.id}>{m.label}</SelectItem>
          ))}
          <SelectItem value={CUSTOM}>Other model…</SelectItem>
        </SelectContent>
      </Select>
      {isCustom && (
        <Input
          aria-label="Ollama model tag"
          value={modelId ?? ""}
          onChange={(e) => onChange(e.target.value.trim() || undefined)}
          placeholder="e.g. mistral:7b"
        />
      )}
      {installed?.error && (
        <p className="text-xs text-muted-foreground">Ollama not reachable — showing suggested models only.</p>
      )}
      {modelId && installed && !installed.error && !isInstalled && (
        <p className="text-xs text-muted-foreground">
          Not installed yet: <code>ollama pull {modelId}</code>
        </p>
      )}
    </div>
  );
}
