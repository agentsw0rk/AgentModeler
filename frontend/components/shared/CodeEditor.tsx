"use client";

import dynamic from "next/dynamic";
import type { EditorProps, Monaco } from "@monaco-editor/react";

const MonacoEditor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => (
    <div className="w-full rounded-md border border-border bg-card min-h-[120px] animate-pulse" />
  ),
});

function handleBeforeMount(monaco: Monaco) {
  monaco.editor.defineTheme("modeler-dark", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#00000000",
      "editor.lineHighlightBackground": "#ffffff08",
      "editor.selectionBackground": "#3b82f640",
      "editorCursor.foreground": "#6d8ee8",
    },
  });
}

const DEFAULT_OPTIONS: EditorProps["options"] = {
  minimap: { enabled: false },
  fontSize: 12,
  lineNumbers: "on",
  lineDecorationsWidth: 0,
  lineNumbersMinChars: 3,
  wordWrap: "on",
  scrollBeyondLastLine: false,
  automaticLayout: true,
  bracketPairColorization: { enabled: true },
  autoClosingBrackets: "always",
  tabSize: 4,
  suggestOnTriggerCharacters: true,
  quickSuggestions: true,
  scrollbar: { vertical: "auto", horizontal: "hidden" },
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true,
  overviewRulerBorder: false,
  renderLineHighlight: "line",
  contextmenu: false,
  padding: { top: 8, bottom: 8 },
};

interface CodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  language?: string;
  height?: string;
  readOnly?: boolean;
}

export default function CodeEditor({
  value,
  onChange,
  language = "kotlin",
  height = "120px",
  readOnly = false,
}: CodeEditorProps) {
  return (
    <div className="w-full rounded-md border border-border overflow-hidden resize-y" style={{ height, minHeight: height }}>
      <MonacoEditor
        value={value}
        onChange={(v) => onChange(v ?? "")}
        language={language}
        theme="modeler-dark"
        beforeMount={handleBeforeMount}
        options={{
          ...DEFAULT_OPTIONS,
          readOnly,
        }}
      />
    </div>
  );
}
