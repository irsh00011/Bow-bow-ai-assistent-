"""BOW BOW backend — FastAPI + OpenRouter + Activepieces.

Real implementation, not a mock. Without OPENROUTER_API_KEY it refuses
to answer instead of faking a reply. Tool calls from the model are
validated against an allowlist; there is no arbitrary shell execution.
Activepieces is the only automation engine: the backend triggers its
flows by webhook. No other automation platform is used anywhere here.
"""
import json
import os
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"


def _http_client(timeout: int) -> httpx.AsyncClient:
    """httpx client that uses the runtime egress proxy explicitly.
    Default trust_env parsing chokes on this VM's NO_PROXY entries
    (httpx.InvalidURL on '[::1]'), so proxy handling is done here and
    nowhere else. TLS stays verified, using the runtime egress CA
    bundle from SSL_CERT_FILE (the proxy re-signs TLS with its own CA).
    The proxy URL comes from the environment and is never logged."""
    proxy = os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY")
    verify = os.environ.get("SSL_CERT_FILE") or True
    return httpx.AsyncClient(timeout=timeout, trust_env=False,
                             proxy=proxy or None, verify=verify)
# Default: free, multilingual, tool-calling model (verified 2026-11-08).
# Change via OPENROUTER_MODEL env — no code rewrite needed.
DEFAULT_MODEL = "google/gemma-4-31b-it:free"
FALLBACK_MODEL = "openrouter/free"

ALLOWED_TOOLS = [
    "get_emails", "summarize_emails", "search_emails", "create_email_draft",
    "get_calendar_events", "create_calendar_event",
    "search_drive_files", "create_task", "complete_task",
    "create_reminder", "request_phone_call",
]

# Activepieces is the permanent automation engine. Each flow is triggered
# by its webhook URL from the environment; a flow with no URL configured
# is reported as not_configured, never faked as run. Flow E is
# schedule-triggered inside Activepieces, so no chat tool maps to it.
# Tools with no flow yet (create_email_draft, search_drive_files,
# request_phone_call) are handled directly by the backend in their own
# phases and trigger no flow.
FLOW_MAP = {
    "get_emails": "A", "summarize_emails": "A", "search_emails": "A",
    "get_calendar_events": "D", "create_calendar_event": "D",
    "create_task": "B", "complete_task": "B",
    "create_reminder": "C",
}
FLOW_NAMES = {
    "A": "Gmail Summary",
    "B": "Task Completion",
    "C": "Reminder",
    "D": "Calendar",
    "E": "Follow-up",
}


def _flow_webhooks() -> dict[str, str]:
    return {
        letter: os.environ.get(f"ACTIVEPIECES_WEBHOOK_{letter}", "")
        for letter in "ABCDE"
    }


async def call_activepieces_flow(flow: str, payload: dict[str, Any]) -> dict[str, Any]:
    """POST a payload to one Activepieces flow webhook.

    Returns a status dict. Missing URL -> not_configured. Any HTTP
    failure -> failed with the status code. Never raises for a missing
    flow, and never includes the webhook URL in the result.
    """
    url = _flow_webhooks().get(flow, "")
    if not url:
        return {"flow": flow, "name": FLOW_NAMES[flow], "status": "not_configured"}
    try:
        async with _http_client(30) as client:
            r = await client.post(url, json=payload)
    except httpx.HTTPError as e:
        return {"flow": flow, "name": FLOW_NAMES[flow], "status": "failed",
                "detail": f"unreachable: {type(e).__name__}"}
    if r.status_code >= 400:
        return {"flow": flow, "name": FLOW_NAMES[flow], "status": "failed",
                "detail": f"HTTP {r.status_code}"}
    try:
        result = r.json()
    except ValueError:
        result = {"raw": r.text[:300]}
    return {"flow": flow, "name": FLOW_NAMES[flow], "status": "triggered",
            "result": result}

SYSTEM_PROMPT = (
    "You are BOW BOW, a personal assistant for Mohammed Irshad. "
    "Reply in the language the user spoke: Tamil if Tamil, English if English, "
    "natural Tanglish if mixed. Keep replies short and spoken-style. "
    "You may only request tools from the provided list, with valid arguments. "
    "Never invent email contents, calendar events, or task results. "
    "Reading and drafting are allowed automatically; sending, deleting, "
    "paying, or calling needs the user's confirmation — say so and wait."
)

TOOLS_SPEC = [
    {"type": "function", "function": {
        "name": "get_emails",
        "description": "List recent Gmail messages (unread first).",
        "parameters": {"type": "object", "properties": {
            "limit": {"type": "integer", "default": 10},
            "unread_only": {"type": "boolean", "default": True}},
         "required": []}}},
    {"type": "function", "function": {
        "name": "search_emails",
        "description": "Search Gmail by sender, subject, or date.",
        "parameters": {"type": "object", "properties": {
            "query": {"type": "string"},
            "sender": {"type": "string"},
            "limit": {"type": "integer", "default": 10}},
         "required": ["query"]}}},
    {"type": "function", "function": {
        "name": "create_email_draft",
        "description": "Prepare a Gmail draft. Does NOT send.",
        "parameters": {"type": "object", "properties": {
            "to": {"type": "string"}, "subject": {"type": "string"},
            "body": {"type": "string"}},
         "required": ["to", "subject", "body"]}}},
    {"type": "function", "function": {
        "name": "get_calendar_events",
        "description": "List upcoming Google Calendar events.",
        "parameters": {"type": "object", "properties": {
            "days": {"type": "integer", "default": 1},
            "date": {"type": "string", "description": "YYYY-MM-DD, optional"}},
         "required": []}}},
    {"type": "function", "function": {
        "name": "create_calendar_event",
        "description": "Create a calendar event. Needs user confirmation first.",
        "parameters": {"type": "object", "properties": {
            "title": {"type": "string"}, "start": {"type": "string"},
            "end": {"type": "string"}, "description": {"type": "string"}},
         "required": ["title", "start"]}}},
    {"type": "function", "function": {
        "name": "search_drive_files",
        "description": "Search Google Drive by name.",
        "parameters": {"type": "object", "properties": {
            "query": {"type": "string"}, "limit": {"type": "integer", "default": 10}},
         "required": ["query"]}}},
    {"type": "function", "function": {
        "name": "create_task",
        "description": "Create a task in BOW BOW memory.",
        "parameters": {"type": "object", "properties": {
            "title": {"type": "string"}, "description": {"type": "string"},
            "priority": {"type": "string", "enum": ["low", "normal", "high"], "default": "normal"}},
         "required": ["title"]}}},
    {"type": "function", "function": {
        "name": "create_reminder",
        "description": "Create a reminder for a time.",
        "parameters": {"type": "object", "properties": {
            "title": {"type": "string"}, "scheduled_at": {"type": "string"}},
         "required": ["title", "scheduled_at"]}}},
    {"type": "function", "function": {
        "name": "request_phone_call",
        "description": "Request an outbound phone call. Needs authorisation.",
        "parameters": {"type": "object", "properties": {
            "message": {"type": "string"}},
         "required": ["message"]}}},
]

app = FastAPI(title="BOW BOW backend", version="0.1.0")


# --- Supabase (BOW BOW's memory) ---
# Server-side only. Prefers SUPABASE_SERVICE_ROLE_KEY; falls back to the
# anon key (migration 001 lets anon read/write tasks, reminders and
# task_history; conversations still need the service key).
def _sb_config():
    url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    service_key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
    anon_key = os.environ.get("SUPABASE_ANON_KEY", "")
    key = service_key or anon_key
    role = "service_role" if service_key else ("anon" if anon_key else "missing")
    return url, key, role


def _sb_user_id() -> str:
    return os.environ.get("BOWBOW_USER_ID", "")


async def _sb_request(method: str, table: str, *, params=None, json_body=None):
    url, key, _ = _sb_config()
    if not url or not key:
        raise HTTPException(
            status_code=503,
            detail="Supabase is not configured (SUPABASE_URL / key missing in backend/.env).",
        )
    headers = {"apikey": key, "Authorization": f"Bearer {key}",
               "Content-Type": "application/json"}
    if method in ("POST", "PATCH"):
        headers["Prefer"] = "return=representation"
    async with _http_client(30) as client:
        r = await client.request(method, f"{url}/rest/v1/{table}",
                                 params=params, json=json_body, headers=headers)
    if r.status_code >= 400:
        raise HTTPException(
            status_code=502,
            detail=f"Supabase {method} {table} failed: HTTP {r.status_code}: {r.text[:200]}",
        )
    try:
        return r.json()
    except ValueError:
        return {}


def _require_user_id() -> str:
    user_id = _sb_user_id()
    if not user_id:
        raise HTTPException(
            status_code=503,
            detail="BOWBOW_USER_ID is not set in backend/.env — the user's "
            "Supabase user UUID is required for task/reminder rows.",
        )
    return user_id


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    language: str = "auto"  # ta | en | tanglish | auto
    user_id: str = "mohamed"


class ChatResponse(BaseModel):
    reply: str
    model: str
    tool_calls: list[dict[str, Any]] = []
    flows: list[dict[str, Any]] = []
    language: str


def _model() -> str:
    return os.environ.get("OPENROUTER_MODEL", DEFAULT_MODEL)


@app.get("/health")
def health():
    key = os.environ.get("OPENROUTER_API_KEY", "")
    webhooks = _flow_webhooks()
    sb_url, _, sb_role = _sb_config()
    return {
        "status": "ok",
        "openrouter": "configured" if key else "missing_key",
        "model": _model(),
        "allowed_tools": ALLOWED_TOOLS,
        "activepieces": {
            "engine": "activepieces",
            "flows_configured": sum(1 for v in webhooks.values() if v),
            "flows_total": len(webhooks),
        },
        "supabase": {
            "configured": bool(sb_url) and sb_role != "missing",
            "role": sb_role,
            "user_id_configured": bool(_sb_user_id()),
        },
    }


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    description: str = ""
    priority: str = "normal"


class TaskComplete(BaseModel):
    execution_result: dict[str, Any] = {}


class ReminderCreate(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    scheduled_at: str  # ISO 8601 with timezone, e.g. 2026-11-09T10:00:00+05:30


class ConversationCreate(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    response: str = ""
    language: str = "auto"


@app.get("/tasks")
async def list_tasks(status: str = "", limit: int = 50):
    params = {"select": "*", "order": "created_at.desc",
              "limit": str(max(1, min(limit, 100)))}
    if status:
        params["status"] = f"eq.{status}"
    return await _sb_request("GET", "tasks", params=params)


@app.post("/tasks")
async def create_task(req: TaskCreate):
    row = {"user_id": _require_user_id(), "title": req.title,
           "description": req.description or None,
           "priority": req.priority, "status": "pending"}
    rows = await _sb_request("POST", "tasks", json_body=row)
    return rows[0] if rows else row


@app.post("/tasks/{task_id}/complete")
async def complete_task(task_id: str, req: TaskComplete):
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc).isoformat()
    rows = await _sb_request(
        "PATCH", "tasks", params={"id": f"eq.{task_id}"},
        json_body={"status": "completed", "completed_at": now})
    if not rows:
        raise HTTPException(status_code=404, detail=f"No task {task_id}.")
    await _sb_request("POST", "task_history", json_body={
        "task_id": task_id, "action": "status -> completed",
        "execution_result": req.execution_result})
    return rows[0]


@app.get("/reminders")
async def list_reminders(status: str = "", limit: int = 50):
    params = {"select": "*", "order": "scheduled_at.asc",
              "limit": str(max(1, min(limit, 100)))}
    if status:
        params["status"] = f"eq.{status}"
    return await _sb_request("GET", "reminders", params=params)


@app.post("/reminders")
async def create_reminder(req: ReminderCreate):
    row = {"user_id": _require_user_id(), "title": req.title,
           "scheduled_at": req.scheduled_at, "status": "pending"}
    rows = await _sb_request("POST", "reminders", json_body=row)
    return rows[0] if rows else row


class ReminderUpdate(BaseModel):
    status: str = Field(pattern="^(pending|sent|cancelled)$")


@app.patch("/reminders/{reminder_id}")
async def update_reminder(reminder_id: str, req: ReminderUpdate):
    """Lets the Android app mark a reminder "sent" after it has shown
    the local notification, so it is only shown once. Notifications are
    pull-based since Firebase was dropped — see
    documentation/PHASE10-PREP.md."""
    rows = await _sb_request(
        "PATCH", "reminders", params={"id": f"eq.{reminder_id}"},
        json_body={"status": req.status})
    if not rows:
        raise HTTPException(status_code=404, detail=f"No reminder {reminder_id}.")
    return rows[0]


@app.get("/conversations")
async def list_conversations(limit: int = 20):
    params = {"select": "*", "order": "timestamp.desc",
              "limit": str(max(1, min(limit, 100)))}
    return await _sb_request("GET", "conversations", params=params)


@app.post("/conversations")
async def create_conversation(req: ConversationCreate):
    row = {"user_id": _require_user_id(), "message": req.message,
           "response": req.response or None, "language": req.language}
    rows = await _sb_request("POST", "conversations", json_body=row)
    return rows[0] if rows else row


@app.get("/flows")
def flows():
    """Which Activepieces flows the backend can trigger. Webhook URLs
    are never exposed here, only whether each flow is configured."""
    webhooks = _flow_webhooks()
    return {
        "engine": "activepieces",
        "flows": [
            {"flow": letter, "name": FLOW_NAMES[letter],
             "configured": bool(webhooks[letter])}
            for letter in "ABCDE"
        ],
    }


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    api_key = os.environ.get("OPENROUTER_API_KEY", "")
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENROUTER_API_KEY not configured. Create a key named "
            "BOW-BOW at https://openrouter.ai/keys and set it in backend/.env.",
        )
    headers = {
        "Authorization": f"Bearer {api_key}",
        "HTTP-Referer": "https://github.com/irsh00011/bow-bow-assistant",
        "X-Title": "BOW BOW",
        "Content-Type": "application/json",
    }
    # Try the configured model first, then the free router once on 429.
    # Verified 2026-11-08: gemma-4-31b-it:free can 429 on its shared upstream
    # pool while the router still answers, so one fallback keeps BOW BOW alive.
    models = [_model()]
    if FALLBACK_MODEL not in models:
        models.append(FALLBACK_MODEL)
    r = None
    last_429 = ""
    for model in models:
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": req.message},
            ],
            "tools": TOOLS_SPEC,
            "tool_choice": "auto",
        }
        try:
            async with _http_client(60) as client:
                r = await client.post(OPENROUTER_URL, json=payload, headers=headers)
        except httpx.HTTPError as e:
            raise HTTPException(status_code=502, detail=f"OpenRouter unreachable: {e}")
        if r.status_code != 429:
            break
        last_429 = r.text[:300]
    if r is not None and r.status_code == 429:
        raise HTTPException(
            status_code=429,
            detail="OpenRouter free-model limit hit (50/day without credits, "
            "1,000/day after $10 credit purchase, or the shared pool is busy). "
            f"Upstream said: {last_429}",
        )
    if r is None or r.status_code >= 400:
        raise HTTPException(status_code=502, detail=f"OpenRouter error {r.status_code if r else '?'}: {(r.text[:300] if r else '')}")
    data = r.json()
    choice = data["choices"][0]["message"]
    raw_calls = choice.get("tool_calls") or []
    # Validate: only allowlisted tools, drop anything else.
    calls = []
    for c in raw_calls:
        fn = c.get("function", {})
        if fn.get("name") in ALLOWED_TOOLS:
            calls.append({"name": fn["name"], "arguments": fn.get("arguments", "{}")})
    reply = choice.get("content") or ""
    if not reply and calls:
        reply = "Working on that — checking the tools needed."
    # Trigger the matching Activepieces flow for each tool call, once
    # per flow per turn. A flow with no webhook configured is reported
    # as not_configured, never pretended to have run.
    flows: list[dict[str, Any]] = []
    seen_flows: set[str] = set()
    for call in calls:
        flow = FLOW_MAP.get(call["name"])
        if not flow or flow in seen_flows:
            continue
        seen_flows.add(flow)
        try:
            args = json.loads(call["arguments"])
        except (ValueError, TypeError):
            args = {}
        args["user_id"] = req.user_id
        flows.append(await call_activepieces_flow(flow, args))
    # Best-effort memory: record the exchange in Supabase. A failure
    # here must never break the chat reply.
    try:
        if _sb_config()[0] and _sb_user_id():
            await _sb_request("POST", "conversations", json_body={
                "user_id": _sb_user_id(), "message": req.message,
                "response": reply, "language": req.language})
    except Exception:
        pass
    return ChatResponse(
        reply=reply,
        model=data.get("model", _model()),
        tool_calls=calls,
        flows=flows,
        language=req.language,
    )
