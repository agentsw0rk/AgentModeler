import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

const nextConfig: NextConfig = {
  turbopack: {
    root: process.cwd(),
  },
  // Proxy all API calls to the Spring backend so the browser only talks to
  // its own origin (no CORS).
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ];
  },
  experimental: {
    // Default is 30s idle, which would cut the SSE stream (/api/editor/events)
    // and long LLM calls (review, build). 0 is not "disabled" here, so use 1h.
    proxyTimeout: 60 * 60 * 1000,
  },
};

export default nextConfig;
