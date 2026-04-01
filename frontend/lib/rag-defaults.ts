import type { RagNodeConfig } from "@/types/rag";

export const defaultRagData: RagNodeConfig = {
  name: "",
  embedderProvider: "OpenAI",
  embedderModel: "TextEmbedding3Large",
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
  { value: "TextEmbedding3Large", label: "text-embedding-3-large" },
  { value: "TextEmbedding3Small", label: "text-embedding-3-small" },
  { value: "TextEmbeddingAda002", label: "text-embedding-ada-002" },
];

export const OLLAMA_EMBEDDING_MODELS = [
  { value: "NOMIC_EMBED_TEXT", label: "nomic-embed-text" },
];
