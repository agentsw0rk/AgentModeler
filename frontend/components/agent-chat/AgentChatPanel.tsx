"use client";

import { useRef, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAgentChatStore, ToolCallInfo } from "@/store/agentChatStore";
import { sendAgentMessage, resetAgentChat, AgentChatSseEvent } from "@/lib/agent-chat-client";
import { ChevronDownIcon, ChevronRightIcon, RotateCcwIcon } from "lucide-react";

function ToolCallDetails({ toolCalls }: { toolCalls: ToolCallInfo[] }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="mt-1">
      <button
        className="text-xs text-muted-foreground flex items-center gap-1 hover:text-foreground"
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        Tool Calls ({toolCalls.length})
      </button>
      {expanded && (
        <div className="mt-1 space-y-1">
          {toolCalls.map((tc, i) => (
            <div key={i} className="text-xs bg-muted/50 rounded p-2 font-mono">
              <span className="text-blue-400">{tc.tool}</span>
              <span className="text-muted-foreground">(</span>
              <span className="text-foreground">{JSON.stringify(tc.args, null, 0)}</span>
              <span className="text-muted-foreground">)</span>
              {tc.result && (
                <div className="text-green-400 mt-0.5 truncate">{"\u2192"} {tc.result}</div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function AgentChatPanel() {
  const messages = useAgentChatStore((s) => s.messages);
  const status = useAgentChatStore((s) => s.status);
  const addUserMessage = useAgentChatStore((s) => s.addUserMessage);
  const addAgentMessage = useAgentChatStore((s) => s.addAgentMessage);
  const setStatus = useAgentChatStore((s) => s.setStatus);
  const reset = useAgentChatStore((s) => s.reset);

  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || status === "streaming") return;

    setInput("");
    addUserMessage(text);
    setStatus("streaming");

    const collectedToolCalls: ToolCallInfo[] = [];

    try {
      abortRef.current = await sendAgentMessage(text, (event: AgentChatSseEvent) => {
        switch (event.type) {
          case "tool-call":
            if (event.tool) {
              collectedToolCalls.push({
                tool: event.tool,
                args: event.args || {},
                result: event.result || "",
              });
            }
            break;
          case "agent-message":
            addAgentMessage(event.content || "", collectedToolCalls);
            setStatus("idle");
            break;
          case "stream-complete":
            setStatus("idle");
            break;
          case "error":
            addAgentMessage(`Error: ${event.message || "Unknown error"}`, collectedToolCalls);
            setStatus("error");
            break;
        }
      });
    } catch (e) {
      addAgentMessage(`Error: ${e instanceof Error ? e.message : String(e)}`, []);
      setStatus("error");
    }
  };

  const handleReset = async () => {
    abortRef.current?.abort();
    reset();
    try {
      await resetAgentChat();
    } catch {
      // ignore
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {messages.length === 0 && (
          <div className="text-xs text-muted-foreground text-center mt-8">
            Describe what you want to build. The agent will create the graph structure for you.
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[85%] rounded-lg px-3 py-2 text-sm ${
                msg.role === "user"
                  ? "bg-primary text-primary-foreground rounded-br-sm"
                  : "bg-muted rounded-bl-sm"
              }`}
            >
              <div className="whitespace-pre-wrap">{msg.text}</div>
              {msg.toolCalls && msg.toolCalls.length > 0 && (
                <ToolCallDetails toolCalls={msg.toolCalls} />
              )}
            </div>
          </div>
        ))}
        {status === "streaming" && (
          <div className="flex justify-start">
            <div className="bg-muted rounded-lg px-3 py-2 text-sm rounded-bl-sm">
              <span className="animate-pulse">{"\u258A"}</span>
            </div>
          </div>
        )}
        <div ref={scrollRef} />
      </div>
      <div className="border-t p-2 flex gap-2">
        <Button variant="ghost" size="sm" className="h-8 w-8 p-0 shrink-0" onClick={handleReset} title="Reset chat">
          <RotateCcwIcon className="size-3.5" />
        </Button>
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Describe what to build..."
          className="h-8 text-sm"
          disabled={status === "streaming"}
        />
        <Button size="sm" className="h-8 shrink-0" onClick={handleSend} disabled={status === "streaming" || !input.trim()}>
          Send
        </Button>
      </div>
    </div>
  );
}
