"use client";

import { useA2AInspectorStore } from "@/store/a2aInspectorStore";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { A2ATaskStatus } from "@/types/a2a";

const statusColors: Record<A2ATaskStatus, string> = {
  submitted: "text-blue-400",
  working: "text-yellow-400",
  "input-required": "text-purple-400",
  completed: "text-green-400",
  failed: "text-red-400",
  canceled: "text-gray-400",
};

export function A2AInspectorPanel() {
  const isOpen = useA2AInspectorStore((s) => s.isOpen);
  const messages = useA2AInspectorStore((s) => s.messages);
  const taskEvents = useA2AInspectorStore((s) => s.taskEvents);
  const activeTab = useA2AInspectorStore((s) => s.activeTab);
  const setActiveTab = useA2AInspectorStore((s) => s.setActiveTab);
  const clear = useA2AInspectorStore((s) => s.clear);

  if (!isOpen) return null;

  return (
    <div className="w-72 border-l bg-background flex flex-col shrink-0">
      <div className="flex items-center justify-between px-3 py-2 border-b">
        <span className="text-xs font-semibold">A2A Inspector</span>
        <Button variant="ghost" size="sm" onClick={clear} className="text-xs h-6 px-2">Clear</Button>
      </div>
      <div className="flex gap-1 px-3 pt-2">
        <Button
          variant={activeTab === "messages" ? "default" : "ghost"}
          size="sm"
          onClick={() => setActiveTab("messages")}
          className="text-xs h-6"
        >
          Messages
        </Button>
        <Button
          variant={activeTab === "tasks" ? "default" : "ghost"}
          size="sm"
          onClick={() => setActiveTab("tasks")}
          className="text-xs h-6"
        >
          Tasks
        </Button>
      </div>
      <ScrollArea className="flex-1 p-3">
        {activeTab === "messages" && (
          <div className="space-y-2">
            {messages.length === 0 && (
              <p className="text-xs text-muted-foreground">No messages yet. A2A communication events will appear here during preview.</p>
            )}
            {messages.map((msg) => (
              <div key={msg.id} className="border rounded p-2 space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium ${msg.role === "user" ? "text-blue-400" : "text-green-400"}`}>
                    {msg.role === "user" ? "SENT" : "RECEIVED"}
                  </span>
                  <span className="text-xs text-muted-foreground">{new Date(msg.timestamp).toLocaleTimeString()}</span>
                </div>
                {msg.parts.map((part, i) => (
                  <div key={i} className="text-xs text-foreground">{part.content}</div>
                ))}
              </div>
            ))}
          </div>
        )}
        {activeTab === "tasks" && (
          <div className="space-y-2">
            {taskEvents.length === 0 && (
              <p className="text-xs text-muted-foreground">No task events yet. Task status updates will appear here.</p>
            )}
            {taskEvents.map((event) => (
              <div key={event.id} className="border rounded p-2 space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium ${statusColors[event.status]}`}>
                    {event.status.toUpperCase()}
                  </span>
                  <span className="text-xs text-muted-foreground">Task: {event.taskId.slice(0, 8)}</span>
                </div>
                <div className="text-xs text-muted-foreground">{new Date(event.timestamp).toLocaleTimeString()}</div>
                {event.message && event.message.parts.map((part, i) => (
                  <div key={i} className="text-xs text-foreground">{part.content}</div>
                ))}
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}
