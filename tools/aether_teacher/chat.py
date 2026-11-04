"""Bounded local inference. Model text has no tool/action authority."""
import http.client
import json
import re
import time
import uuid
from PySide6.QtCore import QObject, QTimer, QUrl, Signal
from PySide6.QtNetwork import QNetworkAccessManager, QNetworkProxy, QNetworkRequest
from tools.aether_teacher.domain import Rejected, compile_profile, encode, require, text


def strict_json(raw):
    def pairs(entries):
        result = {}
        for key, value in entries:
            require(key not in result, "DUPLICATE_JSON_MEMBER")
            result[key] = value
        return result
    try:
        return json.loads(raw, object_pairs_hook=pairs,
                          parse_constant=lambda value: (_ for _ in ()).throw(Rejected("INVALID_JSON_NUMBER")))
    except (ValueError, UnicodeError):
        raise Rejected("INVALID_RUNTIME_RESPONSE") from None


def request_json(path, payload, timeout=90, cancelled=None):
    """Worker-only HTTP helper, also used for runtime readiness and probes."""
    require(path in ("/api/version", "/api/chat", "/api/show", "/api/tags", "/api/ps"))
    connection = http.client.HTTPConnection("127.0.0.1", 11435, timeout=min(timeout, 5))
    try:
        body = None if payload is None else encode(payload)
        require(body is None or len(body) <= 131072, "REQUEST_LIMIT")
        connection.request("GET" if body is None else "POST", path, body=body,
                           headers={"Content-Type":"application/json"})
        if connection.sock:
            connection.sock.settimeout(timeout)
        started = time.monotonic()
        response = connection.getresponse()
        require(response.status == 200, "RUNTIME_REQUEST_FAILED")
        raw = bytearray()
        while chunk := response.read(4096):
            require(not cancelled or not cancelled.is_set(), "CANCELLED")
            raw.extend(chunk)
            require(len(raw) <= 131072, "RUNTIME_RESPONSE_LIMIT")
            require(time.monotonic() - started <= timeout, "RUNTIME_DEADLINE")
        require(time.monotonic() - started <= timeout, "RUNTIME_DEADLINE")
        result = strict_json(raw)
        require(isinstance(result, dict), "INVALID_RUNTIME_RESPONSE")
        return result
    except (OSError, http.client.HTTPException):
        raise Rejected("RUNTIME_UNAVAILABLE") from None
    finally:
        connection.close()


def chat_payload(model_id, profile, context):
    require(isinstance(model_id, str) and re.fullmatch(r"(?:llama3\.1:8b|aether-candidate:[a-z0-9-]{1,80})", model_id), "UNAPPROVED_MODEL")
    require(2 <= len(context) <= 12 and context[0] == {"role":"system", "content":compile_profile(profile)}, "INVALID_CONTEXT")
    require(context[-1].get("role") == "user", "INVALID_CONTEXT")
    total = 0
    for index, turn in enumerate(context):
        role = "system" if index == 0 else "user" if index % 2 else "assistant"
        require(set(turn) == {"role", "content"} and turn["role"] == role, "INVALID_CONTEXT")
        text(turn["content"], 16000 if role == "assistant" else 4000, 1)
        total += len(turn["content"])
    require(total <= 16000, "CONTEXT_LIMIT")
    return {"model":model_id, "messages":list(context), "stream":False, "keep_alive":"2m",
            "options":{"num_predict":1024, "num_ctx":8192, "temperature":0.3}}


def reply_text(result):
    require(isinstance(result, dict) and result.get("done") is True, "INCOMPLETE_REPLY")
    message = result.get("message")
    require(isinstance(message, dict) and message.get("role") == "assistant", "INVALID_REPLY")
    return text(message.get("content"), 16000, 1)


def profile_patch(raw, revision):
    result = strict_json(raw)
    require(isinstance(result, dict) and set(result) == {"profileRevision", "job", "personality", "boundaries"}, "INVALID_PROFILE_PROPOSAL")
    require(type(result["profileRevision"]) is int and result["profileRevision"] == revision, "STALE_PROFILE_PROPOSAL")
    for key in ("job", "personality", "boundaries"):
        text(result[key], 1000)
    return result


class ChatClient(QObject):
    completed = Signal(str, str)
    failed = Signal(str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.network = QNetworkAccessManager(self)
        self.network.setProxy(QNetworkProxy(QNetworkProxy.ProxyType.NoProxy))
        self.reply = None
        self.buffer = bytearray()
        self.timer = QTimer(self)
        self.timer.setSingleShot(True)
        self.timer.timeout.connect(lambda: self.cancel("RUNTIME_DEADLINE"))
        self.operation = None
        self.error = None

    def send(self, model_id, profile, context, json_format=False):
        require(self.reply is None, "CHAT_BUSY")
        payload = chat_payload(model_id, profile, context)
        if json_format:
            payload["format"] = "json"
        request = QNetworkRequest(QUrl("http://127.0.0.1:11435/api/chat"))
        request.setHeader(QNetworkRequest.KnownHeaders.ContentTypeHeader, "application/json")
        request.setAttribute(QNetworkRequest.Attribute.RedirectPolicyAttribute,
                             QNetworkRequest.RedirectPolicy.ManualRedirectPolicy)
        request.setTransferTimeout(90000)
        self.buffer.clear()
        self.operation = str(uuid.uuid4())
        self.error = None
        self.reply = self.network.post(request, encode(payload))
        self.reply.setReadBufferSize(131073)
        self.reply.readyRead.connect(self._read)
        self.reply.finished.connect(self._finish)
        self.timer.start(90000)
        return self.operation

    def _read(self):
        self.buffer.extend(bytes(self.reply.readAll()))
        if len(self.buffer) > 131072:
            self.cancel("RUNTIME_RESPONSE_LIMIT")

    def cancel(self, reason="CANCELLED"):
        if self.reply is not None:
            self.error = reason
            self.reply.abort()

    def _finish(self):
        reply = self.reply
        self.timer.stop()
        self._read()
        self.reply = None
        try:
            require(self.error is None, self.error or "RUNTIME_REQUEST_FAILED")
            require(reply.attribute(QNetworkRequest.Attribute.HttpStatusCodeAttribute) == 200, "RUNTIME_UNAVAILABLE")
            result = reply_text(strict_json(self.buffer))
        except Rejected as error:
            self.failed.emit(self.operation, str(error))
        else:
            self.completed.emit(self.operation, result)
        finally:
            reply.deleteLater()
            self.buffer.clear()
