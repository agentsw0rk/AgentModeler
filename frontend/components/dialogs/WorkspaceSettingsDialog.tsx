"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { OtelExporterType } from "@/types/graph";
import { SettingsIcon } from "lucide-react";

const ENDPOINT_PLACEHOLDERS: Record<OtelExporterType, string> = {
  http: "http://localhost:4318/v1/traces",
  grpc: "http://localhost:4317",
  logging: "",
};

export function WorkspaceSettingsDialog() {
  const [open, setOpen] = useState(false);
  const otelConfig = useEditorStore((s) => s.otelConfig);
  const updateOtelConfig = useEditorStore((s) => s.updateOtelConfig);

  const showEndpoint = otelConfig.exporterType !== "logging";
  const showAuth = otelConfig.exporterType !== "logging";

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <SettingsIcon className="size-4" />
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Workspace Settings</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <Label htmlFor="otel-enabled">OpenTelemetry</Label>
            <button
              id="otel-enabled"
              role="switch"
              aria-checked={otelConfig.enabled}
              onClick={() => updateOtelConfig({ enabled: !otelConfig.enabled })}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                otelConfig.enabled ? "bg-blue-600" : "bg-muted"
              }`}
            >
              <span
                className={`inline-block size-4 transform rounded-full bg-white transition-transform ${
                  otelConfig.enabled ? "translate-x-6" : "translate-x-1"
                }`}
              />
            </button>
          </div>

          {otelConfig.enabled && (
            <div className="space-y-4 border-t pt-4">
              <div>
                <Label>Exporter Type</Label>
                <Select
                  value={otelConfig.exporterType}
                  onValueChange={(v) => updateOtelConfig({ exporterType: v as OtelExporterType })}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="http">HTTP (OTLP)</SelectItem>
                    <SelectItem value="grpc">gRPC (OTLP)</SelectItem>
                    <SelectItem value="logging">Logging (Console)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {showEndpoint && (
                <div>
                  <Label htmlFor="otel-endpoint">Endpoint</Label>
                  <Input
                    id="otel-endpoint"
                    value={otelConfig.endpoint ?? ""}
                    onChange={(e) => updateOtelConfig({ endpoint: e.target.value || undefined })}
                    placeholder={ENDPOINT_PLACEHOLDERS[otelConfig.exporterType]}
                  />
                </div>
              )}

              <div>
                <Label htmlFor="otel-service-name">Service Name</Label>
                <Input
                  id="otel-service-name"
                  value={otelConfig.serviceName ?? ""}
                  onChange={(e) => updateOtelConfig({ serviceName: e.target.value || undefined })}
                  placeholder="my-agent-service"
                />
              </div>

              <div>
                <Label htmlFor="otel-service-version">Service Version</Label>
                <Input
                  id="otel-service-version"
                  value={otelConfig.serviceVersion ?? ""}
                  onChange={(e) => updateOtelConfig({ serviceVersion: e.target.value || undefined })}
                  placeholder="1.0.0"
                />
              </div>

              {showAuth && (
                <div>
                  <Label htmlFor="otel-auth">Authorization Header (optional)</Label>
                  <Input
                    id="otel-auth"
                    value={otelConfig.authorizationHeader ?? ""}
                    onChange={(e) => updateOtelConfig({ authorizationHeader: e.target.value || undefined })}
                    placeholder="Bearer ..."
                  />
                </div>
              )}

              <div className="flex items-center justify-between">
                <div>
                  <Label htmlFor="otel-verbose">Verbose Mode</Label>
                  <p className="text-xs text-muted-foreground">Show LLM messages in cleartext</p>
                </div>
                <button
                  id="otel-verbose"
                  role="switch"
                  aria-checked={otelConfig.verbose}
                  onClick={() => updateOtelConfig({ verbose: !otelConfig.verbose })}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    otelConfig.verbose ? "bg-blue-600" : "bg-muted"
                  }`}
                >
                  <span
                    className={`inline-block size-4 transform rounded-full bg-white transition-transform ${
                      otelConfig.verbose ? "translate-x-6" : "translate-x-1"
                    }`}
                  />
                </button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
