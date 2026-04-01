export type RagEmbedderProvider = "OpenAI" | "Ollama";
export type RagStorageType = "InMemory" | "File" | "Custom";

export interface RagNodeConfig {
  name: string;
  embedderProvider: RagEmbedderProvider;
  embedderModel: string;
  apiKeyVariable: string;
  storageType: RagStorageType;
  storagePath: string;
  customStorageClass: string;
  similarityThreshold: number;
  filePaths: string[];
  urlSources: string[];
  maxDocuments: number;
  injectAsContext: boolean;
  provideAsTool: boolean;
  toolDescription: string;
}
