import { GraphNode, GraphEdge } from "@/types/graph";
import { useEditorStore } from "./editorStore";

interface ClipboardData {
  type: "koog-clipboard";
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export async function copySelectedNodes(): Promise<void> {
  const { nodes, edges, selectedNodeIds } = useEditorStore.getState();
  if (selectedNodeIds.length === 0) return;

  const selectedSet = new Set(selectedNodeIds);
  const copiedNodes = structuredClone(nodes.filter((n) => selectedSet.has(n.id)));
  const copiedEdges = structuredClone(
    edges.filter((e) => selectedSet.has(e.source) && selectedSet.has(e.target))
  );

  const data: ClipboardData = { type: "koog-clipboard", nodes: copiedNodes, edges: copiedEdges };

  try {
    await navigator.clipboard.writeText(JSON.stringify(data));
  } catch {
    // Permission denied or no focus — silent no-op
  }
}

export async function pasteNodes(): Promise<void> {
  let text: string;
  try {
    text = await navigator.clipboard.readText();
  } catch {
    return;
  }

  let data: ClipboardData;
  try {
    data = JSON.parse(text);
    if (data.type !== "koog-clipboard" || !Array.isArray(data.nodes)) return;
  } catch {
    return;
  }

  const idMap = new Map<string, string>();
  const newNodes = data.nodes.map((node) => {
    const newId = crypto.randomUUID();
    idMap.set(node.id, newId);
    return {
      ...node,
      id: newId,
      position: { x: node.position.x + 20, y: node.position.y + 20 },
    };
  });

  const newEdges = (data.edges || [])
    .filter((e) => idMap.has(e.source) && idMap.has(e.target))
    .map((edge) => ({
      ...edge,
      id: crypto.randomUUID(),
      source: idMap.get(edge.source)!,
      target: idMap.get(edge.target)!,
    }));

  useEditorStore.getState().addNodes(newNodes, newEdges);
}
