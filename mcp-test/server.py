"""
MCP-Server mit SSE-Transport (HTTP).

Starten mit:
    pip install aiohttp
    python server.py

Endpoints:
    GET  /sse          → SSE-Stream (Server→Client)
    POST /messages     → JSON-RPC-Requests (Client→Server)
"""

import asyncio
import json
import uuid

from aiohttp import web

HOST = "0.0.0.0"
PORT = 8811


# ── JSON-RPC Helpers ───────────────────────────────────────────────────

def make_response(id, result):
    return {"jsonrpc": "2.0", "id": id, "result": result}


def make_error(id, code, message):
    return {"jsonrpc": "2.0", "id": id, "error": {"code": code, "message": message}}


# ── Tool-Definitionen ─────────────────────────────────────────────────

TOOLS = [
    {
        "name": "add",
        "description": "Addiert zwei Zahlen.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "a": {"type": "number", "description": "Erste Zahl"},
                "b": {"type": "number", "description": "Zweite Zahl"},
            },
            "required": ["a", "b"],
        },
    },
    {
        "name": "greet",
        "description": "Gibt eine Begrüßung zurück.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "Name der Person"},
            },
            "required": ["name"],
        },
    },
    {
        "name": "reverse",
        "description": "Kehrt einen String um.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "Der umzukehrende Text"},
            },
            "required": ["text"],
        },
    },
]


# ── Tool-Handler ───────────────────────────────────────────────────────

def handle_tool_call(name: str, arguments: dict) -> list[dict]:
    if name == "add":
        result = arguments["a"] + arguments["b"]
        return [{"type": "text", "text": str(result)}]
    elif name == "greet":
        return [{"type": "text", "text": f"Hallo, {arguments['name']}! 👋"}]
    elif name == "reverse":
        return [{"type": "text", "text": arguments["text"][::-1]}]
    else:
        raise ValueError(f"Unknown tool: {name}")


# ── Request-Dispatcher ────────────────────────────────────────────────

def handle_request(msg: dict) -> dict | None:
    method = msg.get("method")
    id = msg.get("id")
    params = msg.get("params", {})

    if method == "initialize":
        return make_response(id, {
            "protocolVersion": "2024-11-05",
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "test-mcp-server", "version": "0.1.0"},
        })

    if method == "notifications/initialized":
        return None

    if method == "tools/list":
        return make_response(id, {"tools": TOOLS})

    if method == "tools/call":
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        try:
            content = handle_tool_call(tool_name, arguments)
            return make_response(id, {"content": content, "isError": False})
        except Exception as e:
            return make_response(id, {
                "content": [{"type": "text", "text": str(e)}],
                "isError": True,
            })

    return make_error(id, -32601, f"Method not found: {method}")


# ── Session-Verwaltung ────────────────────────────────────────────────

sessions: dict[str, asyncio.Queue] = {}


# ── HTTP-Handler ──────────────────────────────────────────────────────

async def handle_sse(request: web.Request) -> web.StreamResponse:
    session_id = str(uuid.uuid4())
    queue: asyncio.Queue = asyncio.Queue()
    sessions[session_id] = queue

    resp = web.StreamResponse()
    resp.content_type = "text/event-stream"
    resp.headers["Cache-Control"] = "no-cache"
    resp.headers["Connection"] = "keep-alive"
    await resp.prepare(request)

    # Dem Client mitteilen, wohin er POST-Requests senden soll
    endpoint_event = f"event: endpoint\ndata: /messages?sessionId={session_id}\n\n"
    await resp.write(endpoint_event.encode())

    try:
        while True:
            data = await queue.get()
            sse_msg = f"event: message\ndata: {json.dumps(data)}\n\n"
            await resp.write(sse_msg.encode())
    except (asyncio.CancelledError, ConnectionResetError):
        pass
    finally:
        sessions.pop(session_id, None)

    return resp


async def handle_messages(request: web.Request) -> web.Response:
    session_id = request.query.get("sessionId")
    if not session_id or session_id not in sessions:
        return web.Response(status=400, text="Invalid or missing sessionId")

    msg = await request.json()
    response = handle_request(msg)

    if response is not None:
        await sessions[session_id].put(response)

    return web.Response(status=202, text="Accepted")


# ── App ───────────────────────────────────────────────────────────────

app = web.Application()
app.router.add_get("/sse", handle_sse)
app.router.add_post("/messages", handle_messages)

if __name__ == "__main__":
    print(f"MCP SSE Server startet auf http://{HOST}:{PORT}")
    print(f"  SSE-Endpoint:     GET  http://localhost:{PORT}/sse")
    print(f"  Message-Endpoint: POST http://localhost:{PORT}/messages?sessionId=<id>")
    web.run_app(app, host=HOST, port=PORT)
