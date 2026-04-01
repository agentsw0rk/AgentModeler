"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useEditorStore } from "@/store/editorStore";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { RagNodeConfig } from "@/types/rag";
import { RagPropertiesEmbedderTab } from "./RagPropertiesEmbedderTab";
import { RagPropertiesStorageTab } from "./RagPropertiesStorageTab";
import { RagPropertiesDocumentsTab } from "./RagPropertiesDocumentsTab";
import { RagPropertiesModeTab } from "./RagPropertiesModeTab";

export function RagProperties({ nodeId, data }: { nodeId: string; data: RagNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<RagNodeConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="rag-name">Name</Label>
        <Input id="rag-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="Product Docs" />
      </div>

      <Tabs defaultValue="embedder" className="w-full">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="embedder" className="text-xs">Embedder</TabsTrigger>
          <TabsTrigger value="storage" className="text-xs">Storage</TabsTrigger>
          <TabsTrigger value="documents" className="text-xs">Docs</TabsTrigger>
          <TabsTrigger value="mode" className="text-xs">Mode</TabsTrigger>
        </TabsList>

        <TabsContent value="embedder">
          <RagPropertiesEmbedderTab data={data} onUpdate={update} />
        </TabsContent>
        <TabsContent value="storage">
          <RagPropertiesStorageTab data={data} onUpdate={update} />
        </TabsContent>
        <TabsContent value="documents">
          <RagPropertiesDocumentsTab data={data} onUpdate={update} />
        </TabsContent>
        <TabsContent value="mode">
          <RagPropertiesModeTab data={data} onUpdate={update} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
