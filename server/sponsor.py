"""Sponsor segments: the part of a video where its author reads somebody's advert.

People mark the part; when enough of them have marked about the same part, every
phone with the mod skips it and shows it red on the bar. The rules, as asked for:

- only an account that has proved itself (the bio code) may mark;
- a part that is half the video or more is never taken on marks alone;
- a part is taken for everyone once QUORUM different accounts have marked about
  the same stretch, its ends within NEAR_MS of one another;
- the owner's mark (whoever holds the "owner" badge) is taken at once, and the
  owner's "this is not an advert" takes a part away at once.

A mark may carry the sound's prints of the part (a list of numbers the mod works out
from the audio), kept with a part that is taken: these adverts are mostly the same
recording put into many videos, and the prints let a phone know one it hears again.
"""

import json
import statistics
import time

use = {}

QUORUM = 3
NEAR_MS = 2500
LONGEST = 0.5
SHORTEST_MS = 1000
LONGEST_VIDEO_MS = 3 * 3600 * 1000
PER_HOUR = 40
MOST_IDS = 100
MOST_PRINTS = 12000


def prepare(db):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS sb_report (
            id     INTEGER PRIMARY KEY,
            aid    TEXT NOT NULL,
            uid    TEXT NOT NULL,
            start  INTEGER NOT NULL,
            stop   INTEGER NOT NULL,
            length INTEGER NOT NULL,
            print  TEXT NOT NULL DEFAULT '',
            wrong  INTEGER NOT NULL DEFAULT 0,
            at     INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS sb_report_aid ON sb_report (aid);
        CREATE INDEX IF NOT EXISTS sb_report_uid ON sb_report (uid, at);
        CREATE TABLE IF NOT EXISTS sb_segment (
            id     INTEGER PRIMARY KEY,
            aid    TEXT NOT NULL,
            start  INTEGER NOT NULL,
            stop   INTEGER NOT NULL,
            by     TEXT NOT NULL,
            print  TEXT NOT NULL DEFAULT '',
            at     INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS sb_segment_aid ON sb_segment (aid);
    """)
    db.commit()


def aid_ok(aid):
    return isinstance(aid, str) and aid.isdigit() and 5 <= len(aid) <= 24


def owner(db, uid):
    return db.execute("SELECT 1 FROM held WHERE uid = ? AND badge = 'owner'", (uid,)).fetchone() is not None


def shown(db, aid):
    return [[r[0], r[1], r[2]] for r in db.execute(
        "SELECT start, stop, id FROM sb_segment WHERE aid = ? ORDER BY start", (aid,))]


def segments(ids):
    """GET /sb?ids=a,b,c: the parts taken for each of these videos."""
    ids = [one for one in ids if aid_ok(one)][:MOST_IDS]
    out = {}
    if not ids:
        return 200, {"segments": out}
    db = use["connect"]()
    marks = ",".join("?" * len(ids))
    for aid, start, stop, sid in db.execute(
            "SELECT aid, start, stop, id FROM sb_segment WHERE aid IN (%s) ORDER BY start" % marks, ids):
        out.setdefault(aid, []).append([start, stop, sid])
    db.close()
    return 200, {"segments": out}


def prints(since):
    """GET /sb/prints: the sound of every advert that has been taken and has one."""
    db = use["connect"]()
    rows = db.execute(
        "SELECT id, stop - start, print, at FROM sb_segment WHERE print != '' AND at > ?"
        " ORDER BY at DESC LIMIT 500", (since,)).fetchall()
    db.close()
    out = []
    newest = since
    for sid, length, made, at in rows:
        try:
            out.append({"id": sid, "length": length, "hashes": json.loads(made)})
        except ValueError:
            continue
        newest = max(newest, at)
    return 200, {"prints": out, "until": newest}


def near(a, b):
    return abs(a[0] - b[0]) <= NEAR_MS and abs(a[1] - b[1]) <= NEAR_MS


def settle(db, aid):
    """The parts the marks on a video agree on, taken again from all its marks."""
    rows = db.execute(
        "SELECT uid, start, stop, length, print, wrong FROM sb_report WHERE aid = ? ORDER BY at", (aid,)).fetchall()
    marks = [r for r in rows if not r[5] and (r[2] - r[1]) < LONGEST * r[3]]
    wrongs = [r for r in rows if r[5]]
    groups = []
    for uid, start, stop, length, made, _ in marks:
        for group in groups:
            middle = (statistics.median(g[1] for g in group), statistics.median(g[2] for g in group))
            if near((start, stop), middle):
                group.append((uid, start, stop, made))
                break
        else:
            groups.append([(uid, start, stop, made)])
    db.execute("DELETE FROM sb_segment WHERE aid = ? AND by = 'crowd'", (aid,))
    now = int(time.time())
    for group in groups:
        people = {g[0] for g in group}
        if len(people) < QUORUM:
            continue
        start = int(statistics.median(g[1] for g in group))
        stop = int(statistics.median(g[2] for g in group))
        against = {w[0] for w in wrongs if near((w[1], w[2]), (start, stop))}
        # enough people saying it is no advert, or the owner saying so once
        if len(against) >= QUORUM or any(owner(db, one) for one in against):
            continue
        made = next((g[3] for g in group if g[3]), "")
        db.execute("INSERT INTO sb_segment (aid, start, stop, by, print, at) VALUES (?,?,?,?,?,?)",
                   (aid, start, stop, "crowd", made, now))


def report(body, ip):
    """POST /sb/report: a mark, or a "this is not an advert" (wrong: true)."""
    db = use["connect"]()
    try:
        uid = use["vouched"](db, body)
        if not uid:
            return 403, {"error": "prove"}
        aid = body.get("aid")
        if not aid_ok(aid):
            return 400, {"error": "no such video"}
        try:
            start, stop, length = int(body.get("start")), int(body.get("stop")), int(body.get("length"))
        except (TypeError, ValueError):
            return 400, {"error": "start, stop and length are milliseconds"}
        if not (0 <= start < stop <= length + 500 and 0 < length <= LONGEST_VIDEO_MS) or stop - start < SHORTEST_MS:
            return 400, {"error": "that is not a part of this video"}
        now = int(time.time())
        recent = db.execute("SELECT COUNT(*) FROM sb_report WHERE uid = ? AND at > ?", (uid, now - 3600)).fetchone()[0]
        if recent >= PER_HOUR:
            return 429, {"error": "too many marks, try again later"}
        wrong = 1 if body.get("wrong") else 0
        made = ""
        hashes = body.get("print")
        if isinstance(hashes, list) and 0 < len(hashes) <= MOST_PRINTS and all(isinstance(h, int) for h in hashes):
            made = json.dumps(hashes)
        # one mark of a person for one stretch of a video: a new one replaces the old
        for rid, s, e in db.execute("SELECT id, start, stop FROM sb_report WHERE aid = ? AND uid = ?", (aid, uid)).fetchall():
            if near((s, e), (start, stop)):
                db.execute("DELETE FROM sb_report WHERE id = ?", (rid,))
        db.execute("INSERT INTO sb_report (aid, uid, start, stop, length, print, wrong, at) VALUES (?,?,?,?,?,?,?,?)",
                   (aid, uid, start, stop, length, made, wrong, now))
        taken = False
        if owner(db, uid):
            for sid, s, e in db.execute("SELECT id, start, stop FROM sb_segment WHERE aid = ?", (aid,)).fetchall():
                if near((s, e), (start, stop)) or (s < stop and start < e):
                    db.execute("DELETE FROM sb_segment WHERE id = ?", (sid,))
            if not wrong:
                db.execute("INSERT INTO sb_segment (aid, start, stop, by, print, at) VALUES (?,?,?,?,?,?)",
                           (aid, start, stop, "owner", made, now))
                taken = True
        else:
            settle(db, aid)
            taken = any(near((s, e), (start, stop)) for s, e, _ in shown(db, aid)) and not wrong
        db.commit()
        return 200, {"taken": taken, "segments": shown(db, aid)}
    finally:
        db.close()
