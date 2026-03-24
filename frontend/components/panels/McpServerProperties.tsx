"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { discoverMcpTools } from "@/lib/mcp-client";
import type { McpServerConfig, McpTransportType, McpEnvVar } from "@/types/mcp";

export function McpServerProperties({ nodeId, data }: { nodeId: string; data: McpServerConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<McpServerConfig>) => updateNodeData(nodeId, field);

  const [discovering, setDiscovering] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDiscover = async () => {
    setDiscovering(true);
    setError(null);
    try {
      const result = await discoverMcpTools({
        transportType: data.transportType,
        url: data.url,
        command: data.command,
        args: data.args,
        envVars: data.envVars.map((v) => ({ name: v.name, value: v.value })),
      });
      if (result.error) {
        setError(result.error);
      } else if (result.tools) {
        update({
          discoveredTools: result.tools,
          lastDiscoveredAt: new Date().toISOString(),
        });
      }
    } catch (e: any) {
      setError(e.message || "Discovery failed");
    } finally {
      setDiscovering(false);
    }
  };

  const addEnvVar = () => {
    update({ envVars: [...data.envVars, { id: `env-${Date.now()}`, name: "", value: "" }] });
  };

  const updateEnvVar = (id: string, field: Partial<McpEnvVar>) => {
    update({ envVars: data.envVars.map((v) => (v.id === id ? { ...v, ...field } : v)) });
  };

  const removeEnvVar = (id: string) => {
    update({ envVars: data.envVars.filter((v) => v.id !== id) });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="mcp-name">Server Name</Label>
        <Input id="mcp-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="Google Maps" />
      </div>
      <div>
        <Label>Transport</Label>
        <Select value={data.transportType} onValueChange={(v) => update({ transportType: v as McpTransportType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="sse">SSE</SelectItem>
            <SelectItem value="stdio">STDIO</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {data.transportType === "sse" && (
        <div>
          <Label htmlFor="mcp-url">URL</Label>
          <Input id="mcp-url" value={data.url || ""} onChange={(e) => update({ url: e.target.value })} placeholder="http://localhost:8931" />
        </div>
      )}

      {data.transportType === "stdio" && (
        <>
          <div>
            <Label htmlFor="mcp-command">Command</Label>
            <Input id="mcp-command" value={data.command || ""} onChange={(e) => update({ command: e.target.value })} placeholder="npx" />
          </div>
          <div>
            <Label htmlFor="mcp-args">Arguments (comma-separated)</Label>
            <Input
              id="mcp-args"
              value={(data.args || []).join(", ")}
              onChange={(e) => update({ args: e.target.value.split(",").map((s) => s.trim()).filter(Boolean) })}
              placeholder="@playwright/mcp, --headless"
            />
          </div>
        </>
      )}

      <div>
        <Label className="text-xs text-muted-foreground">Environment Variables</Label>
        <div className="space-y-2 mt-1">
          {data.envVars.map((v) => (
            <div key={v.id} className="flex gap-2 items-center">
              <Input
                value={v.name}
                onChange={(e) => updateEnvVar(v.id, { name: e.target.value })}
                placeholder="KEY"
                className="flex-1 text-xs"
              />
              <Input
                type="password"
                value={v.value || ""}
                onChange={(e) => updateEnvVar(v.id, { value: e.target.value })}
                placeholder="value (for discovery only)"
                className="flex-1 text-xs"
              />
              <Button variant="ghost" size="sm" onClick={() => removeEnvVar(v.id)} className="text-xs px-2">x</Button>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={addEnvVar} className="text-xs">+ Add Variable</Button>
        </div>
      </div>

      <Button onClick={handleDiscover} disabled={discovering} className="w-full" size="sm">
        {discovering ? "Discovering..." : data.discoveredTools.length > 0 ? "Re-Discover Tools" : "Discover Tools"}
      </Button>

      {error && (
        <div className="text-sm text-destructive bg-destructive/10 rounded p-2">{error}</div>
      )}

      {data.discoveredTools.length > 0 && (
        <div>
          <Label className="text-xs text-muted-foreground">Discovered Tools ({data.discoveredTools.length})</Label>
          <div className="mt-1 space-y-1 bg-muted/50 rounded p-2">
            {data.discoveredTools.map((tool) => (
              <div key={tool.name}>
                <div className="text-xs font-medium">{tool.name}</div>
                {tool.description && <div className="text-xs text-muted-foreground">{tool.description}</div>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
