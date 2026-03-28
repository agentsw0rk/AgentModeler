"use client";

import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { TEMPLATES } from "@/components/graph-agent/templates";
import type { GraphAgentTemplate } from "@/types/graph-agent";

const templateKeys: GraphAgentTemplate[] = ["empty", "tool-calling", "react-loop", "tool-loop-finish"];

export function GraphAgentTemplateDialog({
  open,
  onSelect,
  onClose,
}: {
  open: boolean;
  onSelect: (template: GraphAgentTemplate) => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Choose Graph Agent Template</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {templateKeys.map((key) => {
            const t = TEMPLATES[key];
            return (
              <Button
                key={key}
                variant="outline"
                className="w-full justify-start h-auto py-3"
                onClick={() => onSelect(key)}
              >
                <div className="text-left">
                  <div className="font-medium">{t.label}</div>
                  <div className="text-xs text-muted-foreground">{t.description}</div>
                </div>
              </Button>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
