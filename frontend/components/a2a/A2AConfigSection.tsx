"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import type { A2AConfig, A2ASkill, AgentCard, A2ACapabilities, A2AServerConfig } from "@/types/a2a";

const defaultA2AConfig: A2AConfig = {
  enabled: false,
  agentCard: {
    name: "",
    description: "",
    url: "",
    version: "1.0.0",
    capabilities: { streaming: false, pushNotifications: false, stateTransitionHistory: false },
    skills: [],
    defaultInputModes: ["text"],
    defaultOutputModes: ["text"],
  },
  server: {
    transportType: "http-jsonrpc",
    port: 9000,
    basePath: "/a2a",
  },
};

function Toggle({ checked, onCheckedChange }: { checked: boolean; onCheckedChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onCheckedChange(!checked)}
      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
        checked ? "bg-blue-500" : "bg-muted"
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow-lg transform transition-transform ${
          checked ? "translate-x-4" : "translate-x-0"
        }`}
      />
    </button>
  );
}

export function A2AConfigSection({ nodeId, a2aConfig }: { nodeId: string; a2aConfig?: A2AConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const config = a2aConfig || defaultA2AConfig;
  const [expanded, setExpanded] = useState(config.enabled);

  const updateConfig = (partial: Partial<A2AConfig>) => {
    updateNodeData(nodeId, { a2aConfig: { ...config, ...partial } });
  };

  const updateCard = (partial: Partial<AgentCard>) => {
    updateConfig({ agentCard: { ...config.agentCard, ...partial } });
  };

  const updateCapabilities = (partial: Partial<A2ACapabilities>) => {
    updateCard({ capabilities: { ...config.agentCard.capabilities, ...partial } });
  };

  const addSkill = () => {
    const skill: A2ASkill = { id: `skill-${Date.now()}`, name: "", description: "", tags: [] };
    updateCard({ skills: [...config.agentCard.skills, skill] });
  };

  const updateSkill = (id: string, field: Partial<A2ASkill>) => {
    updateCard({ skills: config.agentCard.skills.map((s) => (s.id === id ? { ...s, ...field } : s)) });
  };

  const removeSkill = (id: string) => {
    updateCard({ skills: config.agentCard.skills.filter((s) => s.id !== id) });
  };

  return (
    <div className="border rounded-lg p-3 space-y-3">
      <div className="flex items-center justify-between">
        <Label className="text-xs font-semibold">A2A Communication</Label>
        <Toggle
          checked={config.enabled}
          onCheckedChange={(enabled: boolean) => {
            updateConfig({ enabled });
            setExpanded(enabled);
          }}
        />
      </div>

      {config.enabled && expanded && (
        <div className="space-y-3 pt-1">
          <div className="text-xs text-muted-foreground font-medium">Agent Card</div>
          <div>
            <Label className="text-xs">Card Name</Label>
            <Input value={config.agentCard.name} onChange={(e) => updateCard({ name: e.target.value })} placeholder="MyBot" className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Description</Label>
            <Textarea value={config.agentCard.description} onChange={(e) => updateCard({ description: e.target.value })} rows={2} className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">URL</Label>
            <Input value={config.agentCard.url} onChange={(e) => updateCard({ url: e.target.value })} placeholder="http://localhost:9000/a2a" className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Version</Label>
            <Input value={config.agentCard.version} onChange={(e) => updateCard({ version: e.target.value })} placeholder="1.0.0" className="text-xs" />
          </div>

          <div className="text-xs text-muted-foreground font-medium pt-2">Capabilities</div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">Streaming</Label>
              <Toggle checked={config.agentCard.capabilities.streaming} onCheckedChange={(v: boolean) => updateCapabilities({ streaming: v })} />
            </div>
            <div className="flex items-center justify-between">
              <Label className="text-xs">Push Notifications</Label>
              <Toggle checked={config.agentCard.capabilities.pushNotifications} onCheckedChange={(v: boolean) => updateCapabilities({ pushNotifications: v })} />
            </div>
            <div className="flex items-center justify-between">
              <Label className="text-xs">State History</Label>
              <Toggle checked={config.agentCard.capabilities.stateTransitionHistory} onCheckedChange={(v: boolean) => updateCapabilities({ stateTransitionHistory: v })} />
            </div>
          </div>

          <div className="text-xs text-muted-foreground font-medium pt-2">Skills</div>
          {config.agentCard.skills.map((skill) => (
            <div key={skill.id} className="border rounded p-2 space-y-1">
              <div className="flex gap-2">
                <Input value={skill.name} onChange={(e) => updateSkill(skill.id, { name: e.target.value })} placeholder="Skill name" className="flex-1 text-xs" />
                <Button variant="ghost" size="sm" onClick={() => removeSkill(skill.id)} className="text-xs px-2">x</Button>
              </div>
              <Input value={skill.description} onChange={(e) => updateSkill(skill.id, { description: e.target.value })} placeholder="Description" className="text-xs" />
              <Input value={skill.tags.join(", ")} onChange={(e) => updateSkill(skill.id, { tags: e.target.value.split(",").map((t) => t.trim()).filter(Boolean) })} placeholder="Tags (comma-separated)" className="text-xs" />
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={addSkill} className="text-xs w-full">+ Add Skill</Button>

          <div className="text-xs text-muted-foreground font-medium pt-2">Server</div>
          <div>
            <Label className="text-xs">Port</Label>
            <Input type="number" value={config.server.port} onChange={(e) => updateConfig({ server: { ...config.server, port: parseInt(e.target.value) || 9000 } as A2AServerConfig })} className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Base Path</Label>
            <Input value={config.server.basePath} onChange={(e) => updateConfig({ server: { ...config.server, basePath: e.target.value } as A2AServerConfig })} placeholder="/a2a" className="text-xs" />
          </div>
        </div>
      )}
    </div>
  );
}
