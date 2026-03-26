"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { buildWorkspaceGraph } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
import { SaveLoadDialog } from "./SaveLoadDialog";
import { WorkspaceSettingsDialog } from "./WorkspaceSettingsDialog";
import { ExportDialog } from "./ExportDialog";
import { KeyboardShortcutsDialog } from "./KeyboardShortcutsDialog";
import {
  MenuIcon,
  CheckCircleIcon,
  SaveIcon,
  SettingsIcon,
  PackageIcon,
  KeyboardIcon,
  WifiIcon,
} from "lucide-react";

export function ToolsMenuDialog({ mcpConnected = false }: { mcpConnected?: boolean }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [subDialog, setSubDialog] = useState<"save" | "settings" | "export" | "keyboard" | null>(null);
  const setErrors = useValidationStore((s) => s.setErrors);

  const handleValidate = () => {
    const graph = buildWorkspaceGraph();
    const errors = validateGraph(graph);
    setErrors(errors);
    setMenuOpen(false);
  };

  const openSubDialog = (dialog: "save" | "settings" | "export" | "keyboard") => {
    setMenuOpen(false);
    // Small delay to allow menu dialog to close before opening sub-dialog
    setTimeout(() => setSubDialog(dialog), 150);
  };

  return (
    <>
      <Dialog open={menuOpen} onOpenChange={setMenuOpen}>
        <DialogTrigger render={<Button variant="outline" size="sm" />}>
          <MenuIcon className="size-4" />
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Tools</DialogTitle>
          </DialogHeader>
          <div className="grid gap-1">
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={handleValidate}>
              <CheckCircleIcon className="size-4 text-muted-foreground" />
              Validate
            </Button>
            <div className="border-t my-1" />
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={() => openSubDialog("save")}>
              <SaveIcon className="size-4 text-muted-foreground" />
              Save / Load
            </Button>
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={() => openSubDialog("settings")}>
              <SettingsIcon className="size-4 text-muted-foreground" />
              Workspace Settings
            </Button>
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={() => openSubDialog("export")}>
              <PackageIcon className="size-4 text-muted-foreground" />
              Export
            </Button>
            <div className="border-t my-1" />
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={() => openSubDialog("keyboard")}>
              <KeyboardIcon className="size-4 text-muted-foreground" />
              Keyboard Shortcuts
            </Button>
            <div className="border-t my-1" />
            <div className="flex items-center gap-3 px-4 h-10 text-sm text-muted-foreground">
              <WifiIcon className="size-4" />
              MCP Status
              <span className={`inline-block h-2 w-2 rounded-full ml-auto ${mcpConnected ? "bg-green-400" : "bg-gray-500"}`} />
              <span className="text-xs">{mcpConnected ? "Connected" : "Disconnected"}</span>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Sub-dialogs rendered with controlled open state */}
      <SaveLoadDialogControlled open={subDialog === "save"} onOpenChange={(open) => !open && setSubDialog(null)} />
      <WorkspaceSettingsDialogControlled open={subDialog === "settings"} onOpenChange={(open) => !open && setSubDialog(null)} />
      <ExportDialogControlled open={subDialog === "export"} onOpenChange={(open) => !open && setSubDialog(null)} />
      <KeyboardShortcutsDialogControlled open={subDialog === "keyboard"} onOpenChange={(open) => !open && setSubDialog(null)} />
    </>
  );
}

// Controlled wrappers that render the dialog content without their own trigger
function SaveLoadDialogControlled({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return <SaveLoadDialog open={open} onOpenChange={onOpenChange} />;
}

function WorkspaceSettingsDialogControlled({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return <WorkspaceSettingsDialog open={open} onOpenChange={onOpenChange} />;
}

function ExportDialogControlled({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return <ExportDialog open={open} onOpenChange={onOpenChange} />;
}

function KeyboardShortcutsDialogControlled({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return <KeyboardShortcutsDialog open={open} onOpenChange={onOpenChange} />;
}
