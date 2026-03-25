import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PlannerAction, StateEffect } from "@/types/planner";
import { ConditionEditor } from "./GoalsTab";
import { Trash2Icon, PlusIcon, ChevronDownIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";

function EffectsEditor({
  effects,
  onChange,
}: {
  effects: StateEffect[];
  onChange: (effects: StateEffect[]) => void;
}) {
  const addEffect = () => {
    onChange([...effects, { key: "", value: "" }]);
  };

  const updateEffect = (index: number, field: Partial<StateEffect>) => {
    const updated = effects.map((e, i) => (i === index ? { ...e, ...field } : e));
    onChange(updated);
  };

  const removeEffect = (index: number) => {
    onChange(effects.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label className="text-xs text-muted-foreground">Effects</Label>
        <Button variant="ghost" size="sm" onClick={addEffect} className="h-6 px-2">
          <PlusIcon className="h-3 w-3" />
        </Button>
      </div>
      {effects.map((effect, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={effect.key}
            onChange={(e) => updateEffect(i, { key: e.target.value })}
          />
          <span className="text-xs text-muted-foreground px-1">=</span>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(effect.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateEffect(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeEffect(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
    </div>
  );
}

export function ActionsTab({
  actions,
  onChange,
}: {
  actions: PlannerAction[];
  onChange: (actions: PlannerAction[]) => void;
}) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const addAction = () => {
    const id = `action-${Date.now()}`;
    onChange([...actions, { id, name: "", description: "", preconditions: [], effects: [], cost: 1 }]);
    setExpandedId(id);
  };

  const updateAction = (id: string, field: Partial<PlannerAction>) => {
    onChange(actions.map((a) => (a.id === id ? { ...a, ...field } : a)));
  };

  const removeAction = (id: string) => {
    onChange(actions.filter((a) => a.id !== id));
  };

  return (
    <div className="space-y-3">
      {actions.map((action) => (
        <div key={action.id} className="border border-border rounded-md p-3 space-y-2">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={() => setExpandedId(expandedId === action.id ? null : action.id)}
            >
              {expandedId === action.id ? <ChevronDownIcon className="h-3 w-3" /> : <ChevronRightIcon className="h-3 w-3" />}
            </Button>
            <Input
              className="h-7 text-sm flex-1"
              placeholder="Action name"
              value={action.name}
              onChange={(e) => updateAction(action.id, { name: e.target.value })}
            />
            <Input
              className="h-7 text-xs w-16"
              type="number"
              placeholder="Cost"
              value={action.cost}
              onChange={(e) => updateAction(action.id, { cost: parseInt(e.target.value) || 1 })}
            />
            <Button variant="ghost" size="sm" onClick={() => removeAction(action.id)} className="h-7 px-1">
              <Trash2Icon className="h-3 w-3" />
            </Button>
          </div>
          {expandedId === action.id && (
            <div className="space-y-3 pl-8">
              <Textarea
                className="text-xs"
                rows={2}
                placeholder="Action description..."
                value={action.description}
                onChange={(e) => updateAction(action.id, { description: e.target.value })}
              />
              <ConditionEditor
                label="Preconditions"
                conditions={action.preconditions}
                onChange={(preconditions) => updateAction(action.id, { preconditions })}
              />
              <EffectsEditor
                effects={action.effects}
                onChange={(effects) => updateAction(action.id, { effects })}
              />
            </div>
          )}
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addAction} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add Action
      </Button>
    </div>
  );
}
