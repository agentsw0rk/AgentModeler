import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PlannerGoal, StateCondition } from "@/types/planner";
import { Trash2Icon, PlusIcon, ChevronDownIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";

function ConditionEditor({
  conditions,
  onChange,
  label,
}: {
  conditions: StateCondition[];
  onChange: (conditions: StateCondition[]) => void;
  label: string;
}) {
  const addCondition = () => {
    onChange([...conditions, { key: "", operator: "equals", value: "" }]);
  };

  const updateCondition = (index: number, field: Partial<StateCondition>) => {
    const updated = conditions.map((c, i) => (i === index ? { ...c, ...field } : c));
    onChange(updated);
  };

  const removeCondition = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label className="text-xs text-muted-foreground">{label}</Label>
        <Button variant="ghost" size="sm" onClick={addCondition} className="h-6 px-2">
          <PlusIcon className="h-3 w-3" />
        </Button>
      </div>
      {conditions.map((cond, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={cond.key}
            onChange={(e) => updateCondition(i, { key: e.target.value })}
          />
          <Select value={cond.operator} onValueChange={(v) => updateCondition(i, { operator: v as StateCondition["operator"] })}>
            <SelectTrigger className="h-7 text-xs w-24"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="equals">=</SelectItem>
              <SelectItem value="notEquals">!=</SelectItem>
              <SelectItem value="greaterThan">&gt;</SelectItem>
              <SelectItem value="lessThan">&lt;</SelectItem>
            </SelectContent>
          </Select>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(cond.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateCondition(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeCondition(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
    </div>
  );
}

export { ConditionEditor };

export function GoalsTab({
  goals,
  onChange,
}: {
  goals: PlannerGoal[];
  onChange: (goals: PlannerGoal[]) => void;
}) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const addGoal = () => {
    const id = `goal-${Date.now()}`;
    onChange([...goals, { id, name: "", conditions: [], priority: 0 }]);
    setExpandedId(id);
  };

  const updateGoal = (id: string, field: Partial<PlannerGoal>) => {
    onChange(goals.map((g) => (g.id === id ? { ...g, ...field } : g)));
  };

  const removeGoal = (id: string) => {
    onChange(goals.filter((g) => g.id !== id));
  };

  return (
    <div className="space-y-3">
      {goals.map((goal) => (
        <div key={goal.id} className="border border-border rounded-md p-3 space-y-2">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={() => setExpandedId(expandedId === goal.id ? null : goal.id)}
            >
              {expandedId === goal.id ? <ChevronDownIcon className="h-3 w-3" /> : <ChevronRightIcon className="h-3 w-3" />}
            </Button>
            <Input
              className="h-7 text-sm flex-1"
              placeholder="Goal name"
              value={goal.name}
              onChange={(e) => updateGoal(goal.id, { name: e.target.value })}
            />
            <Input
              className="h-7 text-xs w-16"
              type="number"
              placeholder="Priority"
              value={goal.priority}
              onChange={(e) => updateGoal(goal.id, { priority: parseInt(e.target.value) || 0 })}
            />
            <Button variant="ghost" size="sm" onClick={() => removeGoal(goal.id)} className="h-7 px-1">
              <Trash2Icon className="h-3 w-3" />
            </Button>
          </div>
          {expandedId === goal.id && (
            <ConditionEditor
              label="Conditions (all must be true)"
              conditions={goal.conditions}
              onChange={(conditions) => updateGoal(goal.id, { conditions })}
            />
          )}
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addGoal} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add Goal
      </Button>
    </div>
  );
}
