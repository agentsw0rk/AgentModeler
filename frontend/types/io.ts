export type IODataType = "string" | "number" | "boolean" | "object" | "array";

export const IO_DATA_TYPES: IODataType[] = ["string", "number", "boolean", "object", "array"];

export const IO_DATA_TYPE_LABELS: Record<IODataType, string> = {
  string: "String",
  number: "Number",
  boolean: "Boolean",
  object: "Object",
  array: "Array",
};

export interface InputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
  required: boolean;
  defaultValue?: string;
}

export interface OutputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
}
