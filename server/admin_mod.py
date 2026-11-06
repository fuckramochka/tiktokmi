"""The panel's powers from inside TikTok, for the owner, on a phone he approved.

Three things have to agree before anything is done:

- the account: proved by writing into its bio (``vouched``) and wearing the
  owner badge, the same as the old ``/verdict`` asked;
- the device: a key made in the phone's keystore, which never leaves it,
  shown to the server once and approved with a code sent to the owner's
  Telegram;
- the request: signed by that key, made in the last two minutes and never
  seen before.

A stolen token alone does nothing, and neither does a copied request. A lost
phone is struck off in the panel, or from another approved phone.
"""

import base64
import hashlib
import json
import secrets
import threading
import time

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

#: how old a signed request may be, either way, in seconds
FRESH = 120
#: how long a code from Telegram is good for
CODE_LIVES = 600
#: wrong codes before a new one has to be asked for
CODE_TRIES = 5
#: at most one code a minute, so asking does not flood the chat
CODE_EVERY = 60

#: filled in by badges.py: connect, vouched, sane, telegram, owner_tg
use = {}


def prepare(db):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS admin_device (
            id        TEXT PRIMARY KEY,
            uid       TEXT NOT NULL,
            key       TEXT NOT NULL,
            name      TEXT NOT NULL DEFAULT '',
            made      INTEGER NOT NULL,
            approved  INTEGER NOT NULL DEFAULT 0,
            code      TEXT NOT NULL DEFAULT '',
            code_made INTEGER NOT NULL DEFAULT 0,
            tries     INTEGER NOT NULL DEFAULT 0,
            last      INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS admin_nonce (
            nonce TEXT PRIMARY KEY,
            at    INTEGER NOT NULL
        );
    """)


def device_id(der):
    return hashlib.sha256(der).hexdigest()[:24]


def _tell(text):
    """A line to the owner's Telegram, without making the phone wait for it."""
    def send():
        try:
            use["telegram"]("sendMessage", chat_id=use["owner_tg"], text=text, parse_mode="HTML")
        except Exception:
            pass
    threading.Thread(target=send, daemon=True).start()


def _owner(db, uid):
    return db.execute("SELECT 1 FROM held WHERE uid = ? AND badge = 'owner'", (uid,)).fetchone() is not None


def _escape(text):
    return str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def device(body):
    """A phone asks to be the owner's: remembered, and a code goes to Telegram."""
    db = use["connect"]()
    try:
        uid = use["vouched"](db, body)
        if not uid:
            return 403, {"error": "prove"}
        if not _owner(db, uid):
            return 403, {"error": "owner only"}
        try:
            der = base64.b64decode(body.get("key", ""), validate=True)
            key = serialization.load_der_public_key(der)
        except Exception:
            return 400, {"error": "key"}
        if not isinstance(key, ec.EllipticCurvePublicKey) or key.curve.name != "secp256r1":
            return 400, {"error": "key"}
        which = device_id(der)
        name = " ".join(str(body.get("name") or "").split())[:60] or "?"
        now = int(time.time())
        row = db.execute("SELECT uid, approved, code_made FROM admin_device WHERE id = ?", (which,)).fetchone()
        if row and row[0] == uid and row[1]:
            return 200, {"state": "approved", "device": which}
        if row and row[0] != uid:
            return 403, {"error": "owner only"}
        if row and now - row[2] < CODE_EVERY:
            return 200, {"state": "code", "device": which}
        code = "%06d" % secrets.randbelow(1000000)
        db.execute("INSERT INTO admin_device (id, uid, key, name, made, code, code_made, tries)"
                   " VALUES (?, ?, ?, ?, ?, ?, ?, 0)"
                   " ON CONFLICT(id) DO UPDATE SET name=excluded.name, code=excluded.code,"
                   " code_made=excluded.code_made, tries=0",
                   (which, uid, base64.b64encode(der).decode(), name, now, code, now))
        db.commit()
        _tell("Админка TikTok Mi просит устройство <b>%s</b>.\nКод: <code>%s</code>\n\n"
              "Если это не ты, никому его не показывай." % (_escape(name), code))
        return 200, {"state": "code", "device": which}
    finally:
        db.close()


def _signed(db, body):
    """The request inside, if the account, the device and the signature all agree."""
    text, sig = body.get("signed"), body.get("sig")
    if not isinstance(text, str) or not isinstance(sig, str) or len(text) > 4096:
        return None, None, (400, {"error": "signed"})
    try:
        said = json.loads(text)
    except ValueError:
        return None, None, (400, {"error": "signed"})
    if not isinstance(said, dict):
        return None, None, (400, {"error": "signed"})
    uid = use["vouched"](db, said)
    if not uid:
        return None, None, (403, {"error": "prove"})
    if not _owner(db, uid):
        return None, None, (403, {"error": "owner only"})
    row = db.execute("SELECT id, uid, key, approved, code, code_made, tries, name FROM admin_device WHERE id = ?",
                     (str(said.get("device", "")),)).fetchone()
    if not row or row[1] != uid:
        return None, None, (403, {"error": "device"})
    now = int(time.time())
    at = said.get("at")
    if not isinstance(at, int) or abs(now - at) > FRESH:
        return None, None, (403, {"error": "late"})
    try:
        key = serialization.load_der_public_key(base64.b64decode(row[2]))
        key.verify(base64.b64decode(sig), text.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
    except (InvalidSignature, ValueError, TypeError):
        return None, None, (403, {"error": "signature"})
    nonce = str(said.get("nonce", ""))
    if not 16 <= len(nonce) <= 64:
        return None, None, (400, {"error": "nonce"})
    db.execute("DELETE FROM admin_nonce WHERE at < ?", (now - 2 * FRESH,))
    try:
        db.execute("INSERT INTO admin_nonce (nonce, at) VALUES (?, ?)", (nonce, now))
    except Exception:
        return None, None, (403, {"error": "again"})
    db.execute("UPDATE admin_device SET last = ? WHERE id = ?", (now, row[0]))
    db.commit()
    return said, row, None


def act(body):
    """One signed request from an owner's phone."""
    db = use["connect"]()
    try:
        said, row, refused = _signed(db, body)
        if refused:
            return refused
        op = said.get("op")
        if op == "confirm":
            return _confirm(db, row, str(said.get("code", "")))
        if not row[3]:
            return 403, {"error": "device"}
        target = str(said.get("target", ""))
        if op in ("look", "give", "take", "verdict", "gradient") and not use["sane"](target):
            return 400, {"error": "target"}
        device_name = row[7]
        if op == "look":
            return 200, _look(db, target)
        if op == "give" or op == "take":
            badge = str(said.get("badge", ""))
            if not db.execute("SELECT 1 FROM badge WHERE id = ?", (badge,)).fetchone():
                return 400, {"error": "badge"}
            if op == "give":
                db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at) VALUES (?, ?, ?)",
                           (target, badge, int(time.time())))
            else:
                db.execute("DELETE FROM held WHERE uid = ? AND badge = ?", (target, badge))
            db.commit()
            _tell("Админка (%s): %s значок <b>%s</b> → <code>%s</code>"
                  % (_escape(device_name), "выдан" if op == "give" else "снят", _escape(badge), target))
            return 200, _look(db, target)
        if op == "verdict":
            good = said.get("good")
            if good is None:
                db.execute("DELETE FROM verdict WHERE uid = ?", (target,))
            else:
                reason = " ".join(str(said.get("reason") or "").split())[:300]
                db.execute("INSERT INTO verdict (uid, good, reason, at) VALUES (?, ?, ?, ?)"
                           " ON CONFLICT(uid) DO UPDATE SET good=excluded.good,"
                           " reason=excluded.reason, at=excluded.at",
                           (target, 1 if good else 0, reason, int(time.time())))
            db.commit()
            return 200, _look(db, target)
        if op == "gradient":
            db.execute("DELETE FROM gradient WHERE uid = ?", (target,))
            db.commit()
            return 200, _look(db, target)
        if op == "devices":
            rows = db.execute("SELECT id, name, approved, made, last FROM admin_device WHERE uid = ?"
                              " ORDER BY made", (row[1],)).fetchall()
            return 200, {"devices": [{"id": r[0], "name": r[1], "approved": bool(r[2]), "made": r[3],
                                      "last": r[4], "this": r[0] == row[0]} for r in rows]}
        if op == "forget":
            gone = str(said.get("which", ""))
            db.execute("DELETE FROM admin_device WHERE id = ? AND uid = ?", (gone, row[1]))
            db.commit()
            _tell("Админка: устройство <code>%s</code> отключено с %s" % (_escape(gone), _escape(device_name)))
            return 200, {"ok": True}
        return 400, {"error": "op"}
    finally:
        db.close()


def _confirm(db, row, code):
    if row[3]:
        return 200, {"state": "approved"}
    now = int(time.time())
    if not row[4] or now - row[5] > CODE_LIVES or row[6] >= CODE_TRIES:
        return 403, {"error": "expired"}
    if not secrets.compare_digest(row[4], code.strip()):
        db.execute("UPDATE admin_device SET tries = tries + 1 WHERE id = ?", (row[0],))
        db.commit()
        return 403, {"error": "wrong"}
    db.execute("UPDATE admin_device SET approved = 1, code = '' WHERE id = ?", (row[0],))
    db.commit()
    _tell("Админка TikTok Mi: устройство <b>%s</b> подтверждено." % _escape(row[7]))
    return 200, {"state": "approved"}


def _look(db, target):
    held = [{"id": r[0], "shown": bool(r[1])} for r in
            db.execute("SELECT badge, shown FROM held WHERE uid = ? ORDER BY place, given_at", (target,))]
    verdict = db.execute("SELECT good, reason FROM verdict WHERE uid = ?", (target,)).fetchone()
    gradient = db.execute("SELECT colours FROM gradient WHERE uid = ?", (target,)).fetchone()
    owner = db.execute("SELECT proved, name FROM owner WHERE uid = ?", (target,)).fetchone()
    return {
        "uid": target,
        "badges": held,
        "verdict": None if not verdict else {"good": bool(verdict[0]), "reason": verdict[1]},
        "gradient": bool(gradient),
        "proved": bool(owner and owner[0]),
        "name": owner[1] if owner else "",
    }


# ------------------------------------------------------------- the panel

def devices_all(db):
    return db.execute("SELECT id, uid, name, approved, made, last FROM admin_device ORDER BY made").fetchall()


def forget(db, which):
    db.execute("DELETE FROM admin_device WHERE id = ?", (which,))
    db.commit()
