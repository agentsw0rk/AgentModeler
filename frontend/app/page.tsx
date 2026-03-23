"use client";

import { Toolbar } from "@/components/panels/Toolbar";
import { Canvas } from "@/components/editor/Canvas";

export default function EditorPage() {
  return (
    <div className="flex flex-col h-screen">
      <Toolbar />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
      </div>
    </div>
  );
}
