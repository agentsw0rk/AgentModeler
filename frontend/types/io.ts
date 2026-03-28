export type IOFieldType = "String" | "Int" | "Double" | "Boolean" | "List" | "Map";

export const IO_FIELD_TYPES: IOFieldType[] = ["String", "Int", "Double", "Boolean", "List", "Map"];

export const IO_FIELD_TYPE_LABELS: Record<IOFieldType, string> = {
  String: "String",
  Int: "Int",
  Double: "Double",
  Boolean: "Boolean",
  List: "List<Any>",
  Map: "Map<String, Any>",
};

export interface IOField {
  id: string;
  name: string;
  type: IOFieldType;
  description: string;
  optional: boolean;
}

export interface InputNodeConfig {
  className?: string;
  fields: IOField[];
}

export interface OutputNodeConfig {
  className?: string;
  fields: IOField[];
}

/** Migrate old flat format to new fields format */
export function migrateIONodeData(data: Record<string, unknown>): InputNodeConfig | OutputNodeConfig {
  if (Array.isArray(data.fields)) {
    return data as unknown as InputNodeConfig | OutputNodeConfig;
  }
  // Legacy format: { name, description, dataType, required?, defaultValue? }
  const legacyTypeMap: Record<string, IOFieldType> = {
    string: "String",
    number: "Double",
    boolean: "Boolean",
    object: "Map",
    array: "List",
  };
  const field: IOField = {
    id: crypto.randomUUID(),
    name: (data.name as string) || "",
    type: legacyTypeMap[(data.dataType as string) || "string"] || "String",
    description: (data.description as string) || "",
    optional: data.required === false,
  };
  const result: InputNodeConfig | OutputNodeConfig = {
    className: undefined,
    fields: field.name ? [field] : [],
  };
  return result;
}
