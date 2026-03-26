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
import { CustomNode, CustomSocket, CustomConnection } from "@/components/editor/CustomNode";
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
  BuiltinToolReteNode,
  SubAgentReteNode,
  McpServerReteNode,
  A2AAgentReteNode,
} from "./rete-nodes";

export type Schemes = GetSchemes<AppNode, AppConnection>;
type AreaExtra = ReactArea2D<Schemes> | ContextMenuExtra;

export async function createEditor(container: HTMLElement) {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });

  render.addPreset(ReactPresets.classic.setup({
    customize: {
      node() { return CustomNode as any; },
      socket() { return CustomSocket as any; },
      connection() { return CustomConnection as any; },
    },
  }));
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

  // Connection validation:
  // - Tool/BuiltinTool output -> Agent or SubAgent input
  // - SubAgent output -> Agent input (NOT SubAgent)
  editor.addPipe((context) => {
    if (context.type === "connectioncreate") {
      const { source, target } = context.data;
      const sourceNode = editor.getNode(source);
      const targetNode = editor.getNode(target);

      const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
      const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
      const isMcpSource = sourceNode instanceof McpServerReteNode;
      const isAgentTarget = targetNode instanceof AgentReteNode;
      const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

      // Tool/Builtin -> Agent or SubAgent: OK
      if (isToolSource && (isAgentTarget || isSubAgentTarget)) {
        return context;
      }
      // SubAgent -> Agent: OK
      if (isSubAgentSource && isAgentTarget) {
        return context;
      }
      // MCP -> Agent or SubAgent: OK
      if (isMcpSource && (isAgentTarget || isSubAgentTarget)) {
        return context;
      }
      // A2A Agent -> Agent: OK
      const isA2ASource = sourceNode instanceof A2AAgentReteNode;
      if (isA2ASource && isAgentTarget) {
        return context;
      }
      // Everything else: blocked
      return undefined;
    }
    return context;
  });

  return { editor, area, connection, render };
}
