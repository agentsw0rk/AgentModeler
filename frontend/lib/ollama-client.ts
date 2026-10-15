const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export interface OllamaModel {
  id: string;
  label: string;
  tools: boolean;
}

export interface OllamaModelsResponse {
  models: OllamaModel[];
  error?: string | null;
}

let cached: Promise<OllamaModelsResponse> | null = null;

/** Models installed in the backend's Ollama. Fetched once per page load and shared by all panels. */
export function fetchOllamaModels(): Promise<OllamaModelsResponse> {
  cached ??= fetch(`${API_BASE}/api/ollama/models`)
    .then((r) => (r.ok ? r.json() : { models: [], error: `Backend returned ${r.status}` }))
    .catch(() => ({ models: [], error: "Backend not reachable" }));
  return cached;
}
