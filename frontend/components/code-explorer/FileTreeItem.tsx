"use client";

import { FileTreeNode, FileType } from "@/types/vfs";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { ChevronRightIcon, ChevronDownIcon, FileIcon, FolderIcon, FolderOpenIcon } from "lucide-react";

interface FileTreeItemProps {
  node: FileTreeNode;
  depth: number;
}

export function FileTreeItem({ node, depth }: FileTreeItemProps) {
  const expandedFolders = useCodeExplorerStore((s) => s.expandedFolders);
  const toggleFolder = useCodeExplorerStore((s) => s.toggleFolder);
  const openFile = useCodeExplorerStore((s) => s.openFile);
  const activeTabPath = useCodeExplorerStore((s) => s.activeTabPath);

  const isFolder = node.type === FileType.DIRECTORY;
  const isExpanded = expandedFolders.has(node.path);
  const isActive = !isFolder && activeTabPath === node.path;

  const handleClick = () => {
    if (isFolder) {
      toggleFolder(node.path);
    } else {
      openFile(node.path, node.name, detectLanguage(node.name));
    }
  };

  return (
    <div>
      <button
        onClick={handleClick}
        className={`flex items-center gap-1 w-full text-left py-0.5 px-1 text-xs rounded transition-colors ${
          isActive
            ? "bg-primary/15 text-primary"
            : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
        }`}
        style={{ paddingLeft: `${depth * 12 + 4}px` }}
      >
        {isFolder ? (
          <>
            {isExpanded ? (
              <ChevronDownIcon className="size-3 shrink-0" />
            ) : (
              <ChevronRightIcon className="size-3 shrink-0" />
            )}
            {isExpanded ? (
              <FolderOpenIcon className="size-3.5 shrink-0 text-yellow-500" />
            ) : (
              <FolderIcon className="size-3.5 shrink-0 text-yellow-500" />
            )}
          </>
        ) : (
          <>
            <span className="w-3 shrink-0" />
            <FileIcon className="size-3.5 shrink-0" />
          </>
        )}
        <span className="truncate">{node.name}</span>
      </button>
      {isFolder && isExpanded && node.children?.map((child) => (
        <FileTreeItem key={child.path} node={child} depth={depth + 1} />
      ))}
    </div>
  );
}

function detectLanguage(name: string): string {
  if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
  if (name === "Dockerfile") return "dockerfile";
  if (name.endsWith(".properties")) return "properties";
  if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
  return "text";
}
