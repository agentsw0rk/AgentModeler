"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { ToolConfig, ToolParameter } from "@/types/koog";

export function ToolProperties({ nodeId, data }: { nodeId: string; data: ToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<ToolConfig>) => updateNodeData(nodeId, field);

  const addParameter = () => {
    const newParam: ToolParameter = {
      id: `param-${Date.now()}`,
      name: "",
      type: "String",
      description: "",
      required: true,
    };
    update({ parameters: [...data.parameters, newParam] });
  };

  const updateParameter = (paramId: string, field: Partial<ToolParameter>) => {
    update({
      parameters: data.parameters.map((p) =>
        p.id === paramId ? { ...p, ...field } : p
      ),
    });
  };

  const removeParameter = (paramId: string) => {
    update({ parameters: data.parameters.filter((p) => p.id !== paramId) });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="tool-name">Name</Label>
        <Input id="tool-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="myTool" />
      </div>
      <div>
        <Label htmlFor="tool-desc">Description</Label>
        <Textarea id="tool-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={3} />
      </div>
      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Parameters</Label>
          <Button variant="outline" size="sm" onClick={addParameter}>+ Parameter</Button>
        </div>
        {data.parameters.map((param) => (
          <div key={param.id} className="border rounded p-2 mb-2 space-y-2">
            <div className="flex gap-2">
              <Input value={param.name} onChange={(e) => updateParameter(param.id, { name: e.target.value })} placeholder="paramName" className="flex-1" />
              <Select value={param.type} onValueChange={(v) => updateParameter(param.id, { type: v as ToolParameter["type"] })}>
                <SelectTrigger className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="String">String</SelectItem>
                  <SelectItem value="Int">Int</SelectItem>
                  <SelectItem value="Float">Float</SelectItem>
                  <SelectItem value="Boolean">Boolean</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="ghost" size="sm" onClick={() => removeParameter(param.id)}>×</Button>
            </div>
            <Input value={param.description} onChange={(e) => updateParameter(param.id, { description: e.target.value })} placeholder="Description" />
          </div>
        ))}
      </div>
    </div>
  );
}
