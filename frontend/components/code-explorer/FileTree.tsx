"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { FileTreeItem } from "./FileTreeItem";

export function FileTree() {
  const tree = useCodeExplorerStore((s) => s.tree);

  if (!tree || !tree.children) {
    return null;
  }

  return (
    <div className="py-1">
      {tree.children.map((node) => (
        <FileTreeItem key={node.path} node={node} depth={0} />
      ))}
    </div>
  );
}
