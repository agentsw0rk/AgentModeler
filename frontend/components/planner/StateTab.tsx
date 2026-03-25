import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StateEntry } from "@/types/planner";
import { Trash2Icon, PlusIcon } from "lucide-react";

export function StateTab({
  entries,
  onChange,
}: {
  entries: StateEntry[];
  onChange: (entries: StateEntry[]) => void;
}) {
  const addEntry = () => {
    onChange([...entries, { key: "", value: "" }]);
  };

  const updateEntry = (index: number, field: Partial<StateEntry>) => {
    const updated = entries.map((e, i) => (i === index ? { ...e, ...field } : e));
    onChange(updated);
  };

  const removeEntry = (index: number) => {
    onChange(entries.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground mb-2">
        Define the initial state as key-value pairs. Values can be strings, numbers, or booleans.
      </div>
      {entries.map((entry, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={entry.key}
            onChange={(e) => updateEntry(i, { key: e.target.value })}
          />
          <span className="text-xs text-muted-foreground px-1">=</span>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(entry.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateEntry(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeEntry(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addEntry} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add State Entry
      </Button>
    </div>
  );
}
