import { Badge } from "@/components/ui/badge";
import { PlannerAgentConfig } from "@/types/planner";

interface PlanStep {
  actionName: string;
  status: "pending" | "running" | "done" | "failed";
}

export function PlanInspector({ data }: { data: PlannerAgentConfig }) {
  const mockPlan: PlanStep[] = data.actions.map((a) => ({
    actionName: a.name || "Unnamed Action",
    status: "pending" as const,
  }));

  const statusIcons: Record<PlanStep["status"], string> = {
    pending: "\u25cb",
    running: "\u25d4",
    done: "\u25cf",
    failed: "\u2717",
  };

  const statusColors: Record<PlanStep["status"], string> = {
    pending: "text-muted-foreground",
    running: "text-blue-400",
    done: "text-green-400",
    failed: "text-red-400",
  };

  return (
    <div className="p-4 space-y-4 text-sm">
      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Goals</h4>
        {data.goals.length === 0 ? (
          <p className="text-xs text-muted-foreground">No goals defined</p>
        ) : (
          <div className="space-y-1">
            {data.goals.map((goal) => (
              <div key={goal.id} className="flex items-center gap-2">
                <Badge variant="outline" className="text-xs">{goal.priority}</Badge>
                <span>{goal.name || "Unnamed"}</span>
                <span className="text-xs text-muted-foreground">
                  ({goal.conditions.length} condition{goal.conditions.length !== 1 ? "s" : ""})
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Plan Steps</h4>
        {mockPlan.length === 0 ? (
          <p className="text-xs text-muted-foreground">No actions defined</p>
        ) : (
          <div className="space-y-1">
            {mockPlan.map((step, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className={statusColors[step.status]}>{statusIcons[step.status]}</span>
                <span>{step.actionName}</span>
                <span className="text-xs text-muted-foreground">cost: {data.actions[i]?.cost ?? 1}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Initial State</h4>
        {data.initialState.length === 0 ? (
          <p className="text-xs text-muted-foreground">No initial state defined</p>
        ) : (
          <div className="border border-border rounded-md overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-muted/50">
                  <th className="text-left px-2 py-1">Key</th>
                  <th className="text-left px-2 py-1">Value</th>
                </tr>
              </thead>
              <tbody>
                {data.initialState.map((entry, i) => (
                  <tr key={i} className="border-t border-border">
                    <td className="px-2 py-1 font-mono">{entry.key}</td>
                    <td className="px-2 py-1 font-mono">{String(entry.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Status</h4>
        <Badge variant="secondary">Not Started (V1: Static View)</Badge>
      </div>
    </div>
  );
}
