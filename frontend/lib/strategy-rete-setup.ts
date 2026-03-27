import { NodeEditor, GetSchemes } from "rete";
import { createRoot } from "react-dom/client";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets, ReactArea2D } from "rete-react-plugin";
import {
  StrategyNode,
  StrategyConnection,
  StartReteNode,
  FinishReteNode,
} from "./strategy-rete-nodes";

export type StrategySchemes = GetSchemes<StrategyNode, StrategyConnection>;
type StrategyAreaExtra = ReactArea2D<StrategySchemes>;

export async function createStrategyEditor(container: HTMLElement) {
  const editor = new NodeEditor<StrategySchemes>();
  const area = new AreaPlugin<StrategySchemes, StrategyAreaExtra>(container);
  const connection = new ConnectionPlugin<StrategySchemes, StrategyAreaExtra>();
  const render = new ReactPlugin<StrategySchemes, StrategyAreaExtra>({ createRoot });

  render.addPreset(ReactPresets.classic.setup({
    customize: {
      node() {
        const { StrategyCustomNode } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomNode as any;
      },
      socket() {
        const { StrategyCustomSocket } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomSocket as any;
      },
      connection() {
        const { StrategyCustomConnection } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomConnection as any;
      },
    },
  }));
  connection.addPreset(ConnectionPresets.classic.setup());

  editor.use(area);
  area.use(connection);
  area.use(render);

  const selector = AreaExtensions.selector();
  const accumulating = AreaExtensions.accumulateOnCtrl();
  AreaExtensions.selectableNodes(area, selector, { accumulating });
  AreaExtensions.simpleNodesOrder(area);

  editor.addPipe((context) => {
    if (context.type === "connectioncreate") {
      const { source, target } = context.data;
      const sourceNode = editor.getNode(source);
      const targetNode = editor.getNode(target);
      if (targetNode instanceof StartReteNode) return undefined;
      if (sourceNode instanceof FinishReteNode) return undefined;
      return context;
    }
    return context;
  });

  return { editor, area, connection, render };
}
