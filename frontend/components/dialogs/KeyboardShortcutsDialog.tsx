"use client";

import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { KeyboardIcon } from "lucide-react";

const isMac = typeof navigator !== "undefined" && /Mac/.test(navigator.platform);
const mod = isMac ? "⌘" : "Ctrl";

const shortcuts = [
  { keys: `${mod}+Z`, action: "Undo" },
  { keys: `${mod}+Shift+Z`, action: "Redo" },
  { keys: `${mod}+C`, action: "Copy nodes" },
  { keys: `${mod}+V`, action: "Paste nodes" },
  { keys: "Delete / Backspace", action: "Delete selected" },
];

export function KeyboardShortcutsDialog() {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <KeyboardIcon className="size-4" />
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Keyboard Shortcuts</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {shortcuts.map((s) => (
            <div key={s.keys} className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{s.action}</span>
              <kbd className="px-2 py-0.5 rounded bg-muted text-xs font-mono">{s.keys}</kbd>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
