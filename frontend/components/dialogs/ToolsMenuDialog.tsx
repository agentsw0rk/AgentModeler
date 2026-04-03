"use client";

import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { buildWorkspaceGraph, useEditorStore } from "@/store/editorStore";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
import { buildProject } from "@/lib/build-client";
import { deleteProject } from "@/lib/vfs-client";
import { fetchGenerators, type GeneratorInfo } from "@/lib/generators-client";
import { SaveLoadDialog } from "./SaveLoadDialog";
import { WorkspaceSettingsDialog } from "./WorkspaceSettingsDialog";
import { ExportDialog } from "./ExportDialog";
import { KeyboardShortcutsDialog } from "./KeyboardShortcutsDialog";
import {
  MenuIcon,
  CheckCircleIcon,
  HammerIcon,
  SaveIcon,
  SettingsIcon,
  PackageIcon,
  KeyboardIcon,
  WifiIcon,
  CpuIcon,
  ChevronDownIcon,
  CheckSquareIcon,
} from "lucide-react";

const PLATFORM_LABELS: Record<string, string> = {
  jvm: "Build (JVM)",
  native: "Build (Native)",
  wasm: "Build (Wasm)",
};

export function ToolsMenuDialog({ mcpConnected = false }: { mcpConnected?: boolean }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [subDialog, setSubDialog] = useState<"save" | "settings" | "export" | "keyboard" | null>(null);
  const [generators, setGenerators] = useState<GeneratorInfo[]>([]);
  const [backendOpen, setBackendOpen] = useState(false);
  const activeBackend = useEditorStore((s) => s.activeBackend);
  const setActiveBackend = useEditorStore((s) => s.setActiveBackend);
  const setErrors = useValidationStore((s) => s.setErrors);
  const setBuildErrors = useValidationStore((s) => s.setBuildErrors);
  const setBuildLoading = useValidationStore((s) => s.setBuildLoading);
  const libraryTargets = useEditorStore((s) => s.libraryTargets);
  const setLibraryTargets = useEditorStore((s) => s.setLibraryTargets);
  const isLibrary = activeBackend === "kmp-library";

  useEffect(() => {
    fetchGenerators().then(setGenerators);
  }, []);

  const activeGen = generators.find((g) => g.id === activeBackend);
  const platforms = activeGen?.supportedPlatforms ?? ["jvm", "native"];

  const handleBackendChange = async (id: string) => {
    if (id === activeBackend) return;
    setActiveBackend(id);
    setBackendOpen(false);

    // Clear VFS and trigger regeneration via store change
    const workspaceName = useEditorStore.getState().workspaceName;
    try {
      await deleteProject(workspaceName);
    } catch {
      // ignore if project doesn't exist
    }
    // The useCodeGeneration hook will detect activeBackend change and regenerate
    useCodeExplorerStore.getState().markStale();
  };

  const handleBuild = async (platform: string) => {
    const graph = buildWorkspaceGraph();
    setBuildLoading(true);
    try {
      const targets = isLibrary ? libraryTargets : undefined;
      const result = await buildProject(graph, platform, activeBackend, targets);
      const errors = result.errors.map((e) => ({
        nodeId: e.nodeId || "",
        severity: e.severity === "error" ? "error" as const : "warning" as const,
        message: `[${e.file}:${e.line}] ${e.message}`,
      }));
      setBuildErrors(errors);
    } catch (err) {
      setBuildErrors([{ nodeId: "", severity: "error", message: (err as Error).message || "Build failed" }]);
    } finally {
      setBuildLoading(false);
    }
  };

  const toggleTarget = (target: string) => {
    const current = libraryTargets;
    if (current.includes(target)) {
      if (current.length > 1) {
        setLibraryTargets(current.filter((t) => t !== target));
      }
    } else {
      setLibraryTargets([...current, target]);
    }
  };

  const handleValidate = () => {
    const graph = buildWorkspaceGraph();
    const errors = validateGraph(graph);
    setErrors(errors);
    setMenuOpen(false);
  };

  const openSubDialog = (dialog: "save" | "settings" | "export" | "keyboard") => {
    setMenuOpen(false);
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
            {/* Backend selector */}
            <div className="px-4 py-2">
              <div className="text-xs text-muted-foreground mb-1.5">Generator Backend</div>
              <div className="relative">
                <button
                  onClick={() => setBackendOpen(!backendOpen)}
                  className="flex items-center justify-between w-full h-9 px-3 rounded-md border border-input bg-background text-sm hover:bg-muted transition-colors"
                >
                  <span className="flex items-center gap-2">
                    <CpuIcon className="size-3.5 text-muted-foreground" />
                    {activeGen?.displayName ?? "Spring Boot (JVM)"}
                  </span>
                  <ChevronDownIcon className="size-3.5 text-muted-foreground" />
                </button>
                {backendOpen && (
                  <div className="absolute z-10 mt-1 w-full rounded-md border bg-popover shadow-md">
                    {generators.map((gen) => (
                      <button
                        key={gen.id}
                        onClick={() => handleBackendChange(gen.id)}
                        className={`flex items-center w-full px-3 py-2 text-sm hover:bg-muted transition-colors first:rounded-t-md last:rounded-b-md ${
                          gen.id === activeBackend ? "bg-muted font-medium" : ""
                        }`}
                      >
                        {gen.displayName}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
            {isLibrary && (
              <div className="px-4 py-2">
                <div className="text-xs text-muted-foreground mb-1.5">Targets</div>
                <div className="grid grid-cols-2 gap-1">
                  {["jvm", "wasmJs", "js", "ios"].map((target) => (
                    <button
                      key={target}
                      onClick={() => toggleTarget(target)}
                      className={`flex items-center gap-2 px-2 py-1.5 rounded text-sm transition-colors ${
                        libraryTargets.includes(target)
                          ? "bg-muted font-medium"
                          : "hover:bg-muted/50 text-muted-foreground"
                      }`}
                    >
                      <CheckSquareIcon
                        className={`size-3.5 ${
                          libraryTargets.includes(target)
                            ? "text-primary"
                            : "text-muted-foreground/30"
                        }`}
                      />
                      {target === "ios" ? "iOS" : target}
                    </button>
                  ))}
                </div>
              </div>
            )}
            <div className="border-t my-1" />
            <Button variant="ghost" className="justify-start gap-3 h-10" onClick={handleValidate}>
              <CheckCircleIcon className="size-4 text-muted-foreground" />
              Validate (Local)
            </Button>
            {isLibrary ? (
              <Button
                variant="ghost"
                className="justify-start gap-3 h-10"
                onClick={() => handleBuild("publish")}
              >
                <PackageIcon className="size-4 text-muted-foreground" />
                Publish to Maven Local
              </Button>
            ) : (
              platforms.map((platform) => (
                <Button
                  key={platform}
                  variant="ghost"
                  className="justify-start gap-3 h-10"
                  onClick={() => handleBuild(platform)}
                >
                  <HammerIcon className="size-4 text-muted-foreground" />
                  {PLATFORM_LABELS[platform] ?? `Build (${platform})`}
                </Button>
              ))
            )}
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
