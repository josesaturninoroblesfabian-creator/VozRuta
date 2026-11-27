"""VozRuta test service. Start behind HTTPS for connections outside localhost."""
import hmac
import json
import logging
import os
import re
import sqlite3
import time
import urllib.error
import urllib.request
import uuid
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

STAGES = ("ROUTE", "APPROACH", "RAMP", "ASSIST", "TRAVEL", "ARRIVED")
STOPS = frozenset(("Mercado Viejo", "Hospital Regional", "Mercado Nuevo", "Amarilis",
                   "UNHEVAL", "Rosavero", "Yanag / El Cuartel", "Entrada a Conchamarca", "Mercado de Ambo"))
SUPPORT = frozenset(("apoyo de abordaje", "guía verbal y hasta el asiento", "rampa y anclaje de silla"))
ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
FIELD_MASK = ",".join((
    "routes.distanceMeters", "routes.duration", "routes.polyline.encodedPolyline",
    "routes.travelAdvisory.transitFare", "routes.legs.steps.distanceMeters",
    "routes.legs.steps.navigationInstruction.instructions",
    "routes.legs.steps.transitDetails.stopDetails",
    "routes.legs.steps.transitDetails.transitLine",
    "routes.legs.steps.transitDetails.localizedValues.departureTime",
))


class RequestError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def required_text(payload, key, max_length):
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip() or len(value) > max_length:
        raise RequestError(400, "Revisa los datos ingresados.")
    return value.strip()


def request_id(value):
    try:
        parsed = uuid.UUID(value, version=4)
        if str(parsed) == value:
            return value
    except (ValueError, TypeError):
        pass
    raise RequestError(400, "Identificador de solicitud inválido.")


def waypoint(value):
    if re.fullmatch(r"[+-]?\d+(\.\d+)?,\s*[+-]?\d+(\.\d+)?", value):
        latitude, longitude = map(float, value.split(","))
        if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
            raise RequestError(400, "Revisa los límites de las coordenadas.")
        return {"location": {"latLng": {"latitude": latitude, "longitude": longitude}}}
    return {"address": value}


class Service(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, database, rider, operator, routes_key=""):
        if min(len(rider), len(operator)) < 24 or rider == operator:
            raise ValueError("Usa dos tokens diferentes de al menos 24 caracteres.")
        self.database = database
        self.tokens = {"rider": ("Bearer " + rider).encode(), "operator": ("Bearer " + operator).encode()}
        self.routes_key = routes_key
        self.quota_lock = Lock()
        self.recent_searches = []
        with closing(self.connect()) as conn, conn:
            conn.execute("PRAGMA journal_mode = WAL")
            conn.execute("""
                CREATE TABLE IF NOT EXISTS boarding (
                    id TEXT PRIMARY KEY, board TEXT NOT NULL, "exit" TEXT NOT NULL,
                    time TEXT NOT NULL, profile TEXT NOT NULL, stage INTEGER NOT NULL,
                    created INTEGER NOT NULL
                )
            """)
        super().__init__(address, Handler)

    def connect(self):
        conn = sqlite3.connect(self.database, timeout=5)
        conn.row_factory = sqlite3.Row
        return conn

    def relay_routes(self, payload):
        mode = payload.get("mode")
        if mode not in ("WALK", "TRANSIT"):
            raise RequestError(400, "Modo de transporte inválido.")
        origin = waypoint(required_text(payload, "origin", 220))
        destination = waypoint(required_text(payload, "destination", 220))
        if not self.routes_key:
            raise RequestError(503, "El servidor requiere una clave de Google Routes para consultar rutas.")
        with self.quota_lock:
            now = time.monotonic()
            self.recent_searches = [item for item in self.recent_searches if now - item < 60]
            if len(self.recent_searches) >= 20:
                raise RequestError(429, "Hay demasiadas búsquedas. Reintenta en un minuto.")
            self.recent_searches.append(now)
        body = {"origin": origin, "destination": destination, "travelMode": mode,
                "languageCode": "es", "units": "METRIC"}
        if mode == "TRANSIT":
            body["computeAlternativeRoutes"] = True
        req = urllib.request.Request(ROUTES_URL, json.dumps(body).encode(), {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": self.routes_key,
            "X-Goog-FieldMask": FIELD_MASK,
        })
        try:
            with urllib.request.urlopen(req, timeout=13) as response:
                routes = json.load(response).get("routes", [])
        except urllib.error.HTTPError as exc:
            if exc.code in (400, 404):
                raise RequestError(422, "Revisa que origen y destino incluyan ciudad y país.") from None
            raise RequestError(502, "La fuente de rutas no está respondiendo a la consulta.") from None
        except (urllib.error.URLError, TimeoutError, ValueError):
            raise RequestError(502, "La fuente de rutas no está disponible.") from None
        return {"routes": routes}


def serialize(row):
    item = dict(row)
    item["stage"] = STAGES[item["stage"]]
    item.pop("created")
    return item


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Request paths include passenger capability IDs. Avoid logging them.
        return

    def do_GET(self):
        self.run_request()

    def do_POST(self):
        self.run_request()

    def run_request(self):
        try:
            code, payload = self.dispatch()
        except RequestError as error:
            code, payload = error.code, {"error": str(error)}
        except (sqlite3.Error, OSError, ValueError) as error:
            logging.warning("Service error: %s", type(error).__name__)
            code, payload = 500, {"error": "El servidor no pudo completar la operación."}
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        try:
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
        except (BrokenPipeError, ConnectionResetError):
            return

    def body(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 4096:
                raise RequestError(413, "El tamaño de la solicitud no está permitido.")
            self.connection.settimeout(8)
            payload = json.loads(self.rfile.read(length))
        except (ValueError, TimeoutError, UnicodeDecodeError):
            raise RequestError(400, "Datos de solicitud inválidos.") from None
        if not isinstance(payload, dict):
            raise RequestError(400, "Datos de solicitud inválidos.")
        return payload

    def dispatch(self):
        path = self.path.strip("/").split("/")
        endpoint = path[0]
        if endpoint == "health" and len(path) == 1 and self.command == "GET":
            return 200, {"status": "ok"}
        role = "operator" if endpoint == "operator" else "rider"
        supplied = self.headers.get("Authorization", "").encode("utf-8")
        if not hmac.compare_digest(supplied, self.server.tokens[role]):
            raise RequestError(401, "Credenciales de prueba inválidas.")
        if endpoint == "routes" and len(path) == 1 and self.command == "POST":
            return 200, self.server.relay_routes(self.body())
        if endpoint == "boarding":
            if len(path) == 1 and self.command == "POST":
                return self.create_boarding(self.body())
            if len(path) == 2 and self.command == "GET":
                with closing(self.server.connect()) as conn:
                    return 200, serialize(self.get_boarding(conn, request_id(path[1])))
        if endpoint == "operator":
            if len(path) == 1 and self.command == "GET":
                with closing(self.server.connect()) as conn:
                    rows = conn.execute("SELECT * FROM boarding ORDER BY (stage = 5), created DESC LIMIT 100")
                    return 200, {"requests": [serialize(row) for row in rows]}
            if len(path) == 2 and self.command == "POST":
                return self.advance(request_id(path[1]), self.body())
        raise RequestError(404, "Operación no encontrada.")

    @staticmethod
    def get_boarding(conn, key):
        row = conn.execute("SELECT * FROM boarding WHERE id = ?", (key,)).fetchone()
        if row is None:
            raise RequestError(404, "La solicitud aún no consta en la central.")
        return row

    def create_boarding(self, payload):
        key = request_id(required_text(payload, "id", 36))
        board = required_text(payload, "board", 80)
        exit_stop = required_text(payload, "exit", 80)
        time_label = required_text(payload, "time", 18)
        profile = required_text(payload, "profile", 80)
        if board == exit_stop or board not in STOPS or exit_stop not in STOPS or profile not in SUPPORT:
            raise RequestError(400, "El paradero o el apoyo no pertenecen al piloto.")
        if not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d(?: mañana)?", time_label):
            raise RequestError(400, "El horario de prueba es inválido.")
        with closing(self.server.connect()) as conn, conn:
            result = conn.execute('INSERT OR IGNORE INTO boarding VALUES (?, ?, ?, ?, ?, 0, ?)',
                                  (key, board, exit_stop, time_label, profile, int(time.time())))
            existing = self.get_boarding(conn, key)
            if (existing["board"], existing["exit"], existing["profile"]) != (board, exit_stop, profile):
                raise RequestError(409, "El identificador corresponde a otra solicitud.")
            return (201 if result.rowcount else 200), serialize(existing)

    def advance(self, key, payload):
        stage = payload.get("stage")
        if stage not in STAGES:
            raise RequestError(400, "El estado indicado no existe.")
        index = STAGES.index(stage)
        with closing(self.server.connect()) as conn, conn:
            conn.execute("BEGIN IMMEDIATE")
            item = self.get_boarding(conn, key)
            if index == item["stage"]:
                return 200, serialize(item)
            if index != item["stage"] + 1:
                raise RequestError(409, "Actualiza la bandeja antes de confirmar esta etapa.")
            conn.execute("UPDATE boarding SET stage = ? WHERE id = ?", (index, key))
            return 200, serialize(self.get_boarding(conn, key))


def main():
    logging.basicConfig(level=logging.INFO)
    bind = os.getenv("VOZRUTA_BIND", "127.0.0.1")
    port = int(os.getenv("VOZRUTA_PORT", "8710"))
    database = os.getenv("VOZRUTA_DB", "vozruta.sqlite3")
    with Service((bind, port), database, os.getenv("RIDER_TOKEN", ""),
                 os.getenv("OPERATOR_TOKEN", ""), os.getenv("ROUTES_API_KEY", "")) as server:
        logging.info("VozRuta disponible en %s:%s", bind, port)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            return


if __name__ == "__main__":
    main()
