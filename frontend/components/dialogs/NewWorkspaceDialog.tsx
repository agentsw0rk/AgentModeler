"use client";

import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { slugifyWorkspaceName } from "@/lib/slugify";

interface NewWorkspaceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  isInitial?: boolean;
  onCreateWorkspace: (name: string) => void;
}

export function NewWorkspaceDialog({ open, onOpenChange, isInitial = false, onCreateWorkspace }: NewWorkspaceDialogProps) {
  const [name, setName] = useState("");

  useEffect(() => {
    if (open) setName("");
  }, [open]);

  const trimmed = name.trim();
  const slug = slugifyWorkspaceName(trimmed);
  const isValid = slug.length > 0;

  const handleCreate = () => {
    if (!isValid) return;
    onCreateWorkspace(slug);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(value) => onOpenChange(value)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New Workspace</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div>
            <Input
              placeholder="my-awesome-agent"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={64}
              onKeyDown={(e) => { if (e.key === "Enter" && isValid) handleCreate(); }}
              autoFocus
            />
          </div>
          {trimmed && slug && trimmed !== slug && (
            <p className="text-xs text-muted-foreground">
              Workspace name: <span className="font-mono">{slug}</span>
            </p>
          )}
        </div>
        <DialogFooter>
          {!isInitial && (
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
          )}
          <Button onClick={handleCreate} disabled={!isValid}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
