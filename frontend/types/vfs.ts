export enum FileType {
  FILE = "FILE",
  DIRECTORY = "DIRECTORY",
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: FileType;
  children: FileTreeNode[] | null;
  size: number | null;
}

export interface FileContent {
  path: string;
  name: string;
  content: string;
  language: string;
  size: number;
}

export enum GenerationStatus {
  VALID = "VALID",
  INVALID = "INVALID",
}

export interface GenerationResult {
  status: GenerationStatus;
  fileCount: number | null;
  generatedAt: string | null;
  errors: string[] | null;
}

export interface OpenTab {
  path: string;
  name: string;
  language: string;
}
