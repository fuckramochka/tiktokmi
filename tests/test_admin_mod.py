import base64
import json
import os
import pathlib
import secrets
import sqlite3
import sys
import tempfile
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

from cryptography.hazmat.primitives import hashes, serialization  # noqa: E402
from cryptography.hazmat.primitives.asymmetric import ec  # noqa: E402

import admin_mod  # noqa: E402

OWNER, STRANGER, TARGET = "111", "222", "7551880794956989495"


class Phone:
    """A key like the one the keystore makes, and requests signed with it."""

    def __init__(self, uid=OWNER, token="secret"):
        self.key = ec.generate_private_key(ec.SECP256R1())
        self.der = self.key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.uid, self.token = uid, token
        self.id = admin_mod.device_id(self.der)

    def register(self):
        return admin_mod.device({"uid": self.uid, "token": self.token,
                                 "key": base64.b64encode(self.der).decode(), "name": "Pixel"})

    def signed(self, op, at=None, nonce=None, key=None, **args):
        said = dict(args, uid=self.uid, token=self.token, device=self.id, op=op,
                    at=int(time.time()) if at is None else at, nonce=nonce or secrets.token_hex(16))
        text = json.dumps(said)
        sig = (key or self.key).sign(text.encode(), ec.ECDSA(hashes.SHA256()))
        return {"signed": text, "sig": base64.b64encode(sig).decode()}

    def act(self, op, **args):
        return admin_mod.act(self.signed(op, **args))


class AdminModTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.dir.name, "b.db")
        db = sqlite3.connect(self.path)
        db.executescript("""
            CREATE TABLE badge (id TEXT PRIMARY KEY);
            CREATE TABLE held (uid TEXT, badge TEXT, shown INTEGER DEFAULT 1, place INTEGER DEFAULT 0,
                given_at INTEGER DEFAULT 0, PRIMARY KEY (uid, badge));
            CREATE TABLE verdict (uid TEXT PRIMARY KEY, good INTEGER, reason TEXT, at INTEGER);
            CREATE TABLE gradient (uid TEXT PRIMARY KEY, colours TEXT, changed INTEGER);
            CREATE TABLE owner (uid TEXT PRIMARY KEY, token TEXT, proved INTEGER, name TEXT);
            INSERT INTO badge VALUES ('owner'), ('friend');
            INSERT INTO held (uid, badge) VALUES ('111', 'owner');
            INSERT INTO owner VALUES ('111', 'secret', 1, 'me'), ('222', 'other', 1, 'them');
        """)
        admin_mod.prepare(db)
        db.commit()
        db.close()
        self.told = []

        def vouched(db, body):
            row = db.execute("SELECT token FROM owner WHERE uid = ?", (body.get("uid"),)).fetchone()
            return body.get("uid") if row and row[0] == body.get("token") else None

        admin_mod.use.update(
            connect=lambda: sqlite3.connect(self.path), vouched=vouched,
            sane=lambda uid: isinstance(uid, str) and uid.isdigit(),
            telegram=lambda what, **fields: self.told.append(fields["text"]), owner_tg=1)
        admin_mod._tell = lambda text: self.told.append(text)

    def tearDown(self):
        self.dir.cleanup()

    def code(self):
        return self.told[-1].split("<code>")[1].split("</code>")[0]

    def held(self):
        db = sqlite3.connect(self.path)
        try:
            return [r[0] for r in db.execute("SELECT badge FROM held WHERE uid = ?", (TARGET,))]
        finally:
            db.close()

    def approved(self):
        phone = Phone()
        self.assertEqual(phone.register()[1]["state"], "code")
        self.assertEqual(phone.act("confirm", code=self.code()), (200, {"state": "approved"}))
        return phone

    def test_a_phone_is_approved_with_the_code_from_telegram_and_then_gives_a_badge(self):
        phone = self.approved()
        code, out = phone.act("give", target=TARGET, badge="friend")
        self.assertEqual(code, 200)
        self.assertEqual([b["id"] for b in out["badges"]], ["friend"])
        self.assertEqual(self.held(), ["friend"])
        self.assertEqual(phone.act("take", target=TARGET, badge="friend")[0], 200)
        self.assertEqual(self.held(), [])
        # asking again from an approved phone needs no code
        self.assertEqual(phone.register()[1]["state"], "approved")

    def test_nothing_works_before_the_code(self):
        phone = Phone()
        phone.register()
        self.assertEqual(phone.act("give", target=TARGET, badge="friend"), (403, {"error": "device"}))
        self.assertEqual(phone.act("confirm", code="000000" if self.code() != "000000" else "111111"),
                         (403, {"error": "wrong"}))
        self.assertEqual(self.held(), [])

    def test_wrong_codes_run_out(self):
        phone = Phone()
        phone.register()
        right = self.code()
        wrong = "000000" if right != "000000" else "111111"
        for _ in range(admin_mod.CODE_TRIES):
            phone.act("confirm", code=wrong)
        self.assertEqual(phone.act("confirm", code=right), (403, {"error": "expired"}))

    def test_a_request_is_not_taken_twice_nor_late_nor_signed_by_another_key(self):
        phone = self.approved()
        once = phone.signed("give", target=TARGET, badge="friend")
        self.assertEqual(admin_mod.act(once)[0], 200)
        self.assertEqual(admin_mod.act(once), (403, {"error": "again"}))
        late = phone.signed("give", target=TARGET, badge="friend", at=int(time.time()) - 600)
        self.assertEqual(admin_mod.act(late), (403, {"error": "late"}))
        forged = phone.signed("give", target=TARGET, badge="friend", key=ec.generate_private_key(ec.SECP256R1()))
        self.assertEqual(admin_mod.act(forged), (403, {"error": "signature"}))
        # what was signed cannot be changed afterwards
        changed = phone.signed("take", target=TARGET, badge="friend")
        changed["signed"] = changed["signed"].replace('"take"', '"give"')
        self.assertEqual(admin_mod.act(changed), (403, {"error": "signature"}))

    def test_only_the_owner_and_only_with_the_token(self):
        stranger = Phone(uid=STRANGER, token="other")
        self.assertEqual(stranger.register(), (403, {"error": "owner only"}))
        thief = Phone(token="guess")
        self.assertEqual(thief.register(), (403, {"error": "prove"}))
        # the owner's approved phone, with a stolen token but its own key, still fails on the key
        phone = self.approved()
        stolen = Phone()
        stolen.id = phone.id
        self.assertEqual(stolen.act("give", target=TARGET, badge="friend"), (403, {"error": "signature"}))

    def test_marks_gradients_and_devices(self):
        phone = self.approved()
        code, out = phone.act("verdict", target=TARGET, good=False, reason="  spam  ")
        self.assertEqual(out["verdict"], {"good": False, "reason": "spam"})
        self.assertIsNone(phone.act("verdict", target=TARGET, good=None)[1]["verdict"])
        other = Phone()
        other.register()
        _, listed = phone.act("devices")
        self.assertEqual(sorted(d["this"] for d in listed["devices"]), [False, True])
        self.assertEqual(phone.act("forget", which=other.id)[0], 200)
        self.assertEqual(len(phone.act("devices")[1]["devices"]), 1)
        self.assertEqual(phone.act("give", target=TARGET, badge="nope"), (400, {"error": "badge"}))
        self.assertEqual(phone.act("give", target="x y", badge="friend"), (400, {"error": "target"}))


if __name__ == "__main__":
    unittest.main()
