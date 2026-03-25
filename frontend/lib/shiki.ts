import { createHighlighter, type Highlighter } from "shiki";

let highlighterPromise: Promise<Highlighter> | null = null;

export function getHighlighter(): Promise<Highlighter> {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ["one-dark-pro"],
      langs: ["kotlin", "groovy", "dockerfile", "properties", "yaml"],
    });
  }
  return highlighterPromise;
}

const langMap: Record<string, string> = {
  kotlin: "kotlin",
  dockerfile: "dockerfile",
  properties: "properties",
  yaml: "yaml",
  text: "text",
};

export function mapLanguage(lang: string): string {
  return langMap[lang] ?? "text";
}
