import { NodeEditor, GetSchemes } from "rete";
import { createRoot } from "react-dom/client";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import {
  ConnectionPlugin,
  Presets as ConnectionPresets,
} from "rete-connection-plugin";
import {
  ReactPlugin,
  Presets as ReactPresets,
  ReactArea2D,
} from "rete-react-plugin";
import {
  ContextMenuPlugin,
  ContextMenuExtra,
  Presets as ContextMenuPresets,
} from "rete-context-menu-plugin";
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
} from "./rete-nodes";

export type Schemes = GetSchemes<AppNode, AppConnection>;
type AreaExtra = ReactArea2D<Schemes> | ContextMenuExtra;

export async function createEditor(container: HTMLElement) {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });

  render.addPreset(ReactPresets.classic.setup());
  render.addPreset(ReactPresets.contextMenu.setup());
  connection.addPreset(ConnectionPresets.classic.setup());

  // Context menu for adding nodes
  const contextMenu = new ContextMenuPlugin<Schemes>({
    items: ContextMenuPresets.classic.setup([
      ["Agent", () => new AgentReteNode("temp", "Agent")],
      ["Tool", () => new ToolReteNode("temp", "Tool")],
    ]),
  });

  // Plugin order matters
  editor.use(area);
  area.use(connection);
  area.use(contextMenu);
  area.use(render);

  // Selection
  const selector = AreaExtensions.selector();
  const accumulating = AreaExtensions.accumulateOnCtrl();
  AreaExtensions.selectableNodes(area, selector, { accumulating });
  AreaExtensions.simpleNodesOrder(area);

  // Connection validation: only Tool output -> Agent input
  editor.addPipe((context) => {
    if (context.type === "connectioncreate") {
      const { source, target } = context.data;
      const sourceNode = editor.getNode(source);
      const targetNode = editor.getNode(target);
      if (
        !(sourceNode instanceof ToolReteNode) ||
        !(targetNode instanceof AgentReteNode)
      ) {
        return undefined;
      }
    }
    return context;
  });

  return { editor, area, connection, render };
}
