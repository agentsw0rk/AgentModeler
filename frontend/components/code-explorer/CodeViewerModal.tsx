"use client";

import { useEffect, useState, useCallback } from "react";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { useEditorStore } from "@/store/editorStore";
import { getFileContent } from "@/lib/vfs-client";
import { getHighlighter, mapLanguage } from "@/lib/shiki";
import { XIcon, LoaderIcon } from "lucide-react";

export function CodeViewerModal() {
  const openTabs = useCodeExplorerStore((s) => s.openTabs);
  const activeTabPath = useCodeExplorerStore((s) => s.activeTabPath);
  const fileCache = useCodeExplorerStore((s) => s.fileCache);
  const setFileContent = useCodeExplorerStore((s) => s.setFileContent);
  const setActiveTab = useCodeExplorerStore((s) => s.setActiveTab);
  const closeTab = useCodeExplorerStore((s) => s.closeTab);
  const closeAllTabs = useCodeExplorerStore((s) => s.closeAllTabs);
  const status = useCodeExplorerStore((s) => s.status);
  const workspaceName = useEditorStore((s) => s.workspaceName);

  const [highlightedHtml, setHighlightedHtml] = useState<string>("");
  const [loading, setLoading] = useState(false);

  const activeTab = openTabs.find((t) => t.path === activeTabPath);
  const activeContent = activeTabPath ? fileCache.get(activeTabPath) : null;

  // Load file content on tab activation
  useEffect(() => {
    if (!activeTabPath || fileCache.has(activeTabPath)) return;

    let cancelled = false;
    setLoading(true);
    getFileContent(workspaceName, activeTabPath)
      .then((content) => {
        if (!cancelled) {
          setFileContent(activeTabPath, content);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [activeTabPath, workspaceName, fileCache, setFileContent]);

  // Highlight code
  useEffect(() => {
    if (!activeContent) {
      setHighlightedHtml("");
      return;
    }

    let cancelled = false;
    getHighlighter().then((highlighter) => {
      if (cancelled) return;
      const lang = mapLanguage(activeContent.language);
      const supported = highlighter.getLoadedLanguages().includes(lang);
      if (supported) {
        const html = highlighter.codeToHtml(activeContent.content, {
          lang,
          theme: "one-dark-pro",
        });
        setHighlightedHtml(html);
      } else {
        // Fallback: render as plain text without Shiki highlighting
        const escaped = activeContent.content
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;");
        setHighlightedHtml(
          `<pre class="p-4 m-0"><code class="leading-relaxed">${escaped}</code></pre>`
        );
      }
    });

    return () => {
      cancelled = true;
    };
  }, [activeContent]);

  // Close on Escape
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === "Escape" && openTabs.length > 0) {
        closeAllTabs();
      }
    },
    [openTabs.length, closeAllTabs]
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  if (openTabs.length === 0) return null;

  const lineCount = activeContent?.content.split("\n").length ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60"
        onClick={() => {
          closeAllTabs();
        }}
      />

      {/* Modal */}
      <div className="relative w-[720px] max-h-[80vh] bg-background border rounded-lg shadow-2xl flex flex-col overflow-hidden">
        {/* Header with tabs */}
        <div className="h-9 bg-muted/30 border-b flex items-center px-2 gap-1 shrink-0 overflow-x-auto">
          {openTabs.map((tab) => (
            <button
              key={tab.path}
              onClick={() => setActiveTab(tab.path)}
              className={`flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-t transition-colors shrink-0 ${
                tab.path === activeTabPath
                  ? "bg-background text-foreground border-b-2 border-primary"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <span className="truncate max-w-32">{tab.name}</span>
              <XIcon
                className="size-3 opacity-50 hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  closeTab(tab.path);
                }}
              />
            </button>
          ))}
        </div>

        {/* Code content */}
        <div className="flex-1 overflow-auto p-0">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <LoaderIcon className="size-5 animate-spin text-muted-foreground" />
            </div>
          ) : highlightedHtml ? (
            <div
              className="text-xs [&>pre]:!bg-transparent [&>pre]:p-4 [&>pre]:m-0 [&_code]:leading-relaxed"
              dangerouslySetInnerHTML={{ __html: highlightedHtml }}
            />
          ) : (
            <div className="flex items-center justify-center h-48 text-muted-foreground text-xs">
              No content
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="h-7 bg-muted/30 border-t flex items-center px-3 gap-3 text-[10px] text-muted-foreground shrink-0">
          <span>{activeTab?.language ?? "text"}</span>
          <span className="text-border">|</span>
          <span>{lineCount} lines</span>
          <span className="text-border">|</span>
          <span>UTF-8</span>
          <span className="flex-1" />
          {status === "valid" && (
            <span className="text-primary bg-primary/10 px-1.5 py-0.5 rounded text-[9px]">
              last valid state
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
