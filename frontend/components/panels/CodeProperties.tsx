"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import CodeEditor from "@/components/shared/CodeEditor";
import type { CodeNodeConfig } from "@/types/graph";
import { useEditorStore } from "@/store/editorStore";

interface CodePropertiesProps {
  nodeId: string;
  data: CodeNodeConfig;
}

export default function CodeProperties({ nodeId, data }: CodePropertiesProps) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);

  return (
    <div className="space-y-4 p-1">
      <div>
        <Label htmlFor="code-filename" className="text-xs">File Name</Label>
        <Input
          id="code-filename"
          value={data.fileName}
          onChange={(e) => updateNodeData(nodeId, { fileName: e.target.value })}
          placeholder="Domain"
          className="h-7 text-xs"
        />
        <p className="text-[10px] text-muted-foreground mt-1">{data.fileName ? `${data.fileName}.kt` : "Enter a name"}</p>
      </div>
      <div>
        <Label className="text-xs">Kotlin Code</Label>
        <CodeEditor
          value={data.code}
          onChange={(v) => updateNodeData(nodeId, { code: v })}
          height="300px"
        />
      </div>
    </div>
  );
}
