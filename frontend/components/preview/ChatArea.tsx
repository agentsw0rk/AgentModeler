"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { usePreviewStore } from "@/store/previewStore";

interface ChatAreaProps {
  onSendMessage: (message: string) => void;
}

export function ChatArea({ onSendMessage }: ChatAreaProps) {
  const messages = usePreviewStore((s) => s.messages);
  const status = usePreviewStore((s) => s.status);
  const error = usePreviewStore((s) => s.error);
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || status === "running" || status === "connecting") return;
    onSendMessage(trimmed);
    setInput("");
  };

  return (
    <div className="flex flex-col flex-1">
      <div ref={scrollRef} className="flex-1 p-3 overflow-y-auto space-y-2">
        {messages.length === 0 && !error && (
          <div className="flex-1 flex items-center justify-center">
            <p className="text-muted-foreground text-xs">Send a message to start the conversation</p>
          </div>
        )}
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`max-w-[70%] rounded-xl px-3 py-2 text-sm ${
              msg.role === "user"
                ? "ml-auto bg-primary text-primary-foreground rounded-br-sm"
                : "mr-auto bg-muted rounded-bl-sm"
            }`}
          >
            {msg.content}
            {msg.isStreaming && <span className="animate-pulse">▊</span>}
          </div>
        ))}
        {error && (
          <div className="mr-auto max-w-[70%] rounded-xl px-3 py-2 text-sm bg-destructive/10 text-destructive">
            {error}
          </div>
        )}
      </div>
      <div className="p-2 border-t flex gap-2">
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") handleSend(); }}
          placeholder="Nachricht eingeben..."
          disabled={status === "connecting"}
          autoFocus
        />
        <Button
          onClick={handleSend}
          disabled={!input.trim() || status === "running" || status === "connecting"}
          size="sm"
        >
          Senden
        </Button>
      </div>
    </div>
  );
}
