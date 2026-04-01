import type { RagNodeConfig } from "@/types/rag";

export const defaultRagData: RagNodeConfig = {
  name: "",
  embedderProvider: "OpenAI",
  embedderModel: "TextEmbeddingAda3Large",
  apiKeyVariable: "OPENAI_API_KEY",
  storageType: "InMemory",
  storagePath: "./vector-storage",
  customStorageClass: "",
  similarityThreshold: 0.7,
  filePaths: [],
  urlSources: [],
  maxDocuments: 5,
  injectAsContext: true,
  provideAsTool: false,
  toolDescription: "Search for relevant documents",
};

export const OPENAI_EMBEDDING_MODELS = [
  { value: "TextEmbeddingAda3Large", label: "text-embedding-3-large" },
  { value: "TextEmbeddingAda3Small", label: "text-embedding-3-small" },
];

export const OLLAMA_EMBEDDING_MODELS = [
  { value: "NOMIC_EMBED_TEXT", label: "nomic-embed-text" },
];
