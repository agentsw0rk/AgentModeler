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
import type { ContextMenuExtra } from "rete-context-menu-plugin";
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
  SubAgentReteNode,
  McpServerReteNode,
  PlannerAgentReteNode,
  A2AAgentReteNode,
  InputReteNode,
  OutputReteNode,
  GraphAgentReteNode,
  CodeReteNode,
  RagReteNode,
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

  // Plugin order matters
  editor.use(area);
  area.use(connection);
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
      // A2A Agent -> Agent or PlannerAgent: OK
      const isA2ASource = sourceNode instanceof A2AAgentReteNode;
      const isPlannerAgentTarget = targetNode instanceof PlannerAgentReteNode;
      if (isA2ASource && (isAgentTarget || isPlannerAgentTarget)) {
        return context;
      }
      // Input -> Agent or SubAgent or PlannerAgent: OK
      const isInputSource = sourceNode instanceof InputReteNode;
      if (isInputSource && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget)) {
        return context;
      }
      // Agent or SubAgent or PlannerAgent -> Output: OK
      const isOutputTarget = targetNode instanceof OutputReteNode;
      const isAgentSource = sourceNode instanceof AgentReteNode;
      const isSubAgentSource2 = sourceNode instanceof SubAgentReteNode;
      const isPlannerAgentSource = sourceNode instanceof PlannerAgentReteNode;
      if ((isAgentSource || isSubAgentSource2 || isPlannerAgentSource) && isOutputTarget) {
        return context;
      }
      // Tool/Builtin/SubAgent/MCP/Input -> GraphAgent: OK
      const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;
      if ((isToolSource || isSubAgentSource || isMcpSource || isInputSource) && isGraphAgentTarget) {
        return context;
      }
      if (isA2ASource && isGraphAgentTarget) {
        return context;
      }
      // GraphAgent -> Output: OK
      const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;
      if (isGraphAgentSource && isOutputTarget) {
        return context;
      }
      // RAG -> Agent, SubAgent, PlannerAgent, GraphAgent: OK
      if (sourceNode instanceof RagReteNode && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget || isGraphAgentTarget)) {
        return context;
      }
      // Code -> Agent/Tool
      if (sourceNode instanceof CodeReteNode) {
        if (targetNode instanceof AgentReteNode || targetNode instanceof ToolReteNode || targetNode instanceof SubAgentReteNode || targetNode instanceof PlannerAgentReteNode || targetNode instanceof GraphAgentReteNode) {
          return context;
        }
        return undefined;
      }
      // Everything else: blocked
      return undefined;
    }
    return context;
  });

  return { editor, area, connection, render };
}
