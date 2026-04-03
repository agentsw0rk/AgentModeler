"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { RagNodeConfig, RagEmbedderProvider } from "@/types/rag";
import { OPENAI_EMBEDDING_MODELS, OLLAMA_EMBEDDING_MODELS } from "@/lib/rag-defaults";

export function RagPropertiesEmbedderTab({ data, onUpdate }: { data: RagNodeConfig; onUpdate: (field: Partial<RagNodeConfig>) => void }) {
  const models = data.embedderProvider === "OpenAI" ? OPENAI_EMBEDDING_MODELS : OLLAMA_EMBEDDING_MODELS;

  return (
    <div className="space-y-3 pt-2">
      <div>
        <Label>Provider</Label>
        <Select
          value={data.embedderProvider}
          onValueChange={(v) => {
            const provider = v as RagEmbedderProvider;
            const defaultModel = provider === "OpenAI" ? "TextEmbedding3Large" : "NOMIC_EMBED_TEXT";
            onUpdate({ embedderProvider: provider, embedderModel: defaultModel });
          }}
        >
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="OpenAI">OpenAI</SelectItem>
            <SelectItem value="Ollama">Ollama</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label>Model</Label>
        <Select value={data.embedderModel ?? undefined} onValueChange={(v) => { if (v != null) onUpdate({ embedderModel: v }); }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {models.map((m) => (
              <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label>API Key Variable</Label>
        <Input
          value={data.apiKeyVariable}
          onChange={(e) => onUpdate({ apiKeyVariable: e.target.value })}
          placeholder="OPENAI_API_KEY"
        />
      </div>
    </div>
  );
}
