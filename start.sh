#!/bin/bash

# Koog Agent Modeler - Start Backend (8080) + Frontend (3000)

cd "$(dirname "$0")"

echo "Starting Backend (Spring Boot on :8080)..."
./gradlew bootRun &
BACKEND_PID=$!

echo "Starting Frontend (Next.js on :3000)..."
cd frontend && npm run dev &
FRONTEND_PID=$!

cd ..

echo "Starting MCP Test Server..."
./mcp-test/mcp-start.sh &
MCP_PID=$!

trap "echo 'Stopping...'; kill $BACKEND_PID $FRONTEND_PID $MCP_PID 2>/dev/null; exit" INT TERM

echo ""
echo "Backend:  http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "MCP Test: running"
echo ""
echo "Press Ctrl+C to stop all."

wait
