#!/usr/bin/env python3
"""In-memory stand-in for warehouse-service's stock ledger API (/internal/v1/stock), used by
scripts/native-smoke.sh where warehouse-service is not running. Standard library only.

It follows docs INVENTORY_OWNERSHIP_CONTRACT.md section 2 closely enough for the smoke test:
reservations (deduct all or nothing, 409 for the first short product, 404 for an unknown one,
idempotent per reservationId), confirm, release, admin adjustments, stock items and movements.
Every stock request needs "Authorization: Bearer $STUB_TOKEN".

Test hooks (no token):
  GET /__stub/requests       every stock request seen: method, path, body, X-Request-Id, traceparent
  GET /__stub/items/<id>     the ledger item of a product, or 404
"""
import json
import os
import re
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("STUB_TOKEN", "local-backend-token")
PORT = int(os.environ.get("STUB_PORT", "4003"))

lock = threading.Lock()
items = {}          # productId -> {"quantity": int, "ledgerVersion": int}
movements = []      # dicts in InventoryMovementDto shape plus ledgerVersion
reservations = {}   # reservationId -> {"lines": {pid: qty}, "status": str, "result": [...], "movementIds": [...]}
requests_seen = []
next_movement_id = [1]


def now():
    return datetime.now(timezone.utc).replace(tzinfo=None).isoformat(timespec="microseconds")


def write(product_id, order_id, kind, delta, actor, reason, request_id=None):
    item = items[product_id]
    item["quantity"] += delta
    item["ledgerVersion"] += 1
    movement = {"id": next_movement_id[0], "productId": product_id, "orderId": order_id, "type": kind,
                "delta": delta, "quantityAfter": item["quantity"], "actor": actor, "reason": reason,
                "requestId": request_id, "createdAt": now(), "ledgerVersion": item["ledgerVersion"]}
    next_movement_id[0] += 1
    movements.append(movement)
    return movement


def level(product_id, quantity_field="quantity"):
    item = items[product_id]
    return {"productId": product_id, quantity_field: item["quantity"], "ledgerVersion": item["ledgerVersion"]}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def reply(self, status, body=None):
        payload = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        if body is not None:
            self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(length) or b"{}") if length else {}

    def handle_any(self, method):
        path = self.path.split("?")[0]
        if path == "/__stub/requests":
            with lock:
                return self.reply(200, requests_seen)
        match = re.fullmatch(r"/__stub/items/(\d+)", path)
        if match:
            with lock:
                pid = int(match.group(1))
                return self.reply(200, level(pid)) if pid in items else self.reply(404, {"message": "unknown"})
        body = self.body() if method in ("POST", "PUT") else {}
        with lock:
            requests_seen.append({"method": method, "path": self.path, "body": body,
                                  "requestId": self.headers.get("X-Request-Id"),
                                  "traceparent": self.headers.get("traceparent")})
            if self.headers.get("Authorization") != "Bearer " + TOKEN:
                return self.reply(401, {"message": "Invalid service token"})
            return self.route(method, path, body)

    def route(self, method, path, body):
        if method == "POST" and path == "/internal/v1/stock/reservations":
            return self.reserve(body)
        match = re.fullmatch(r"/internal/v1/stock/reservations/([^/]+)/(confirm|release)", path)
        if method == "POST" and match:
            reservation = reservations.get(match.group(1))
            if reservation is None:
                return self.reply(404, {"message": "Reservation not found"})
            return self.confirm(match.group(1), reservation, body) if match.group(2) == "confirm" \
                else self.release(match.group(1), reservation)
        if method == "POST" and path == "/internal/v1/stock/adjustments":
            return self.adjust(body)
        match = re.fullmatch(r"/internal/v1/stock/items/(\d+)", path)
        if match:
            pid = int(match.group(1))
            if method == "PUT":
                if pid in items:
                    return self.reply(200, level(pid))
                items[pid] = {"quantity": 0, "ledgerVersion": 0}
                quantity = int(body.get("initialQuantity") or 0)
                if quantity > 0:
                    write(pid, None, "INITIAL_STOCK", quantity, body.get("actor"), "Initial stock")
                else:
                    items[pid]["ledgerVersion"] = 1
                return self.reply(201, level(pid))
            if method == "DELETE":
                items.pop(pid, None)
                return self.reply(204)
            if method == "GET":
                return self.reply(200, level(pid)) if pid in items else self.reply(404, {"message": "Not found"})
        if method == "GET" and path == "/internal/v1/stock/movements":
            query = dict(part.split("=", 1) for part in self.path.split("?", 1)[1].split("&"))
            pid, page, size = int(query["productId"]), int(query.get("page", 0)), int(query.get("size", 20))
            mine = sorted((m for m in movements if m["productId"] == pid),
                          key=lambda m: (m["createdAt"], m["id"]), reverse=True)
            content = [{k: v for k, v in m.items() if k != "ledgerVersion"} for m in mine[page * size:(page + 1) * size]]
            return self.reply(200, {"content": content, "totalElements": len(mine), "number": page, "size": size})
        return self.reply(404, {"message": "Not found"})

    def reserve(self, body):
        rid = body["reservationId"]
        lines = {}
        for line in body["lines"]:
            lines[line["productId"]] = lines.get(line["productId"], 0) + line["quantity"]
        prior = reservations.get(rid)
        if prior is not None:
            if prior["lines"] == lines:
                return self.reply(201, {"reservationId": rid, "lines": prior["result"]})
            return self.reply(409, {"message": "reservationId already used with different lines"})
        for pid in sorted(lines):
            if pid not in items:
                return self.reply(404, {"message": f"Product {pid} not found", "productId": pid})
            if items[pid]["quantity"] < lines[pid]:
                return self.reply(409, {"message": f"Insufficient stock for product {pid}", "productId": pid})
        result, ids = [], []
        for pid in sorted(lines):
            movement = write(pid, None, "ORDER_DEDUCTED", -lines[pid], body["username"], "Order deduction")
            ids.append(movement["id"])
            result.append(level(pid, "quantityAfter"))
        reservations[rid] = {"lines": lines, "status": "PENDING_CONFIRMATION", "result": result, "movementIds": ids}
        return self.reply(201, {"reservationId": rid, "lines": result})

    def confirm(self, rid, reservation, body):
        if reservation["status"] == "RELEASED":
            return self.reply(409, {"message": "Reservation was released"})
        reservation["status"] = "CONFIRMED"
        for movement in movements:
            if movement["id"] in reservation["movementIds"]:
                movement["orderId"] = body.get("orderId")
        return self.reply(200, {"reservationId": rid, "lines": reservation["result"]})

    def release(self, rid, reservation):
        if reservation["status"] == "CONFIRMED":
            return self.reply(409, {"message": "Reservation is confirmed"})
        if reservation["status"] == "PENDING_CONFIRMATION":
            for pid, quantity in sorted(reservation["lines"].items()):
                write(pid, None, "ORDER_RESTORED", quantity, "system", "Reservation released")
            reservation["status"] = "RELEASED"
        return self.reply(200, {"reservationId": rid, "status": "RELEASED"})

    def adjust(self, body):
        pid = body["productId"]
        if pid not in items:
            return self.reply(404, {"message": "Product not found"})
        for movement in movements:
            if movement["productId"] == pid and movement["requestId"] == body["requestId"]:
                if movement["delta"] != body["delta"] or movement["reason"] != body.get("reason"):
                    return self.reply(409, {"message": "requestId already used with different payload"})
                return self.reply(201, movement)
        after = items[pid]["quantity"] + body["delta"]
        if after < 0:
            return self.reply(409, {"message": "Insufficient stock"})
        if after > 2147483647:
            return self.reply(409, {"message": "Inventory quantity exceeds supported maximum"})
        return self.reply(201, write(pid, None, "ADMIN_ADJUSTMENT", body["delta"], body.get("actor"),
                                     body.get("reason"), body["requestId"]))

    def do_GET(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")

    def do_PUT(self):
        self.handle_any("PUT")

    def do_DELETE(self):
        self.handle_any("DELETE")


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
