"""The bot's chatterbox for groups: it learns which word follows which and
now and then says something back.

Not a neural network. Every message in a chat where it is switched on is cut
into words, and for every two words in a row the word after them is counted.
To say something it starts from a word of the message it answers, so a chat
that was on about a virus in TikTok Mi may hear "удалите вирус маргит", and walks
on, each next word picked by how often it came after the two before it.

It is off until an admin of the chat switches it on, and while off it neither
learns nor speaks. What it keeps is these word links, per chat, and "forget"
wipes them. Links and @names are never learned: it is not to tag anybody.
"""

import os
import random
import re
import sqlite3
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "chatter.db")

#: how often it speaks unasked, in percent of messages, unless the chat says otherwise
CHANCE = 8
#: the least time between two unasked remarks in one chat, seconds
QUIET = 20
#: the most words in one remark
LONGEST = 24
#: the most word links kept for one chat; the least recently seen go first
MOST_LINKS = 200000

START = ""      # before the first word, as a and b
END = ""        # after the last word, as c

LINK = re.compile(r"(https?://|www\.|t\.me/)\S+", re.I)
NAME = re.compile(r"@\w+")
EDGE = re.compile(r"^[^\w]+|[^\w]+$", re.U)

_last = {}
_learned = {}
_lock = threading.Lock()


def connect():
    db = sqlite3.connect(DB, timeout=10)
    db.execute("PRAGMA journal_mode=WAL")
    return db


def prepare():
    db = connect()
    db.executescript("""
        CREATE TABLE IF NOT EXISTS chat (
            id INTEGER PRIMARY KEY,
            active INTEGER NOT NULL DEFAULT 0,
            chance INTEGER NOT NULL DEFAULT %d
        );
        CREATE TABLE IF NOT EXISTS link (
            chat INTEGER NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, c TEXT NOT NULL,
            kb TEXT NOT NULL, n INTEGER NOT NULL DEFAULT 1, last INTEGER NOT NULL,
            PRIMARY KEY (chat, a, b, c)
        ) WITHOUT ROWID;
        CREATE INDEX IF NOT EXISTS link_seed ON link (chat, kb);
        CREATE INDEX IF NOT EXISTS link_age ON link (chat, last);
    """ % CHANCE)
    db.commit()
    db.close()


def key(word):
    """A word as it is looked up: small letters, no punctuation round it."""
    return EDGE.sub("", word.lower())


def words(text):
    text = NAME.sub(" ", LINK.sub(" ", text))
    return [w for w in text.lower().split() if len(w) <= 40]


# ------------------------------------------------------------ switching

def state(chat):
    db = connect()
    row = db.execute("SELECT active, chance FROM chat WHERE id = ?", (chat,)).fetchone()
    links = db.execute("SELECT COUNT(*) FROM link WHERE chat = ?", (chat,)).fetchone()[0]
    db.close()
    return (bool(row[0]) if row else False), (row[1] if row else CHANCE), links


def switch(chat, on):
    db = connect()
    db.execute("INSERT INTO chat (id, active) VALUES (?, ?) "
               "ON CONFLICT(id) DO UPDATE SET active = excluded.active", (chat, 1 if on else 0))
    db.commit()
    db.close()


def set_chance(chat, percent):
    percent = max(1, min(50, int(percent)))
    db = connect()
    db.execute("INSERT INTO chat (id, chance) VALUES (?, ?) "
               "ON CONFLICT(id) DO UPDATE SET chance = excluded.chance", (chat, percent))
    db.commit()
    db.close()
    return percent


def forget(chat):
    db = connect()
    db.execute("DELETE FROM link WHERE chat = ?", (chat,))
    db.commit()
    db.close()


# ------------------------------------------------------------ learning

def learn(chat, text):
    said = words(text)
    if not said:
        return
    now = int(time.time())
    chain = [START, START] + said + [END]
    db = connect()
    with db:
        for i in range(len(chain) - 2):
            a, b, c = chain[i], chain[i + 1], chain[i + 2]
            db.execute("INSERT INTO link (chat, a, b, c, kb, n, last) VALUES (?, ?, ?, ?, ?, 1, ?) "
                       "ON CONFLICT(chat, a, b, c) DO UPDATE SET n = n + 1, last = excluded.last",
                       (chat, a, b, c, key(b), now))
    # now and then, a chat that has said a lot loses what it said longest ago
    with _lock:
        _learned[chat] = _learned.get(chat, 0) + 1
        check = _learned[chat] % 200 == 0
    if check:
        count = db.execute("SELECT COUNT(*) FROM link WHERE chat = ?", (chat,)).fetchone()[0]
        if count > MOST_LINKS:
            with db:
                db.execute("DELETE FROM link WHERE (chat, a, b, c) IN (SELECT chat, a, b, c FROM link "
                           "WHERE chat = ? ORDER BY last LIMIT ?)", (chat, count - MOST_LINKS * 9 // 10))
    db.close()


# ------------------------------------------------------------ speaking

def _pick(rows):
    total = sum(n for _c, n in rows)
    at = random.uniform(0, total)
    for c, n in rows:
        at -= n
        if at <= 0:
            return c
    return rows[-1][0]


def say(chat, text, anyhow=False):
    """Something to say to [text], starting from one of its words, or None.

    [anyhow] when the bot was spoken to: then, if no word of the message is
    known, it starts from the beginning of anything it has heard.
    """
    db = connect()
    try:
        # the longer words first, as they say what the message is about; in no set order among them
        seeds = [w for w in {key(w) for w in words(text)} if len(w) >= 3]
        seeds.sort(key=lambda w: (len(w) >= 5, random.random()), reverse=True)
        start = None
        for seed in seeds:
            rows = db.execute("SELECT a, b FROM link WHERE chat = ? AND kb = ? LIMIT 50", (chat, seed)).fetchall()
            if not rows and len(seed) >= 5:
                # Russian words change their ends: "вирусом" finds "вирус" by what they share
                stem = seed[:max(4, len(seed) - 2)]
                rows = db.execute("SELECT a, b FROM link WHERE chat = ? AND kb >= ? AND kb < ? LIMIT 50",
                                  (chat, stem, stem + "\uffff")).fetchall()
            if rows:
                start = random.choice(rows)
                break
        if start is None:
            if not anyhow:
                return None
            start = (START, START)
        a, b = start
        out = [w for w in (a, b) if w]
        for _ in range(LONGEST):
            rows = db.execute("SELECT c, n FROM link WHERE chat = ? AND a = ? AND b = ?", (chat, a, b)).fetchall()
            if not rows:
                break
            c = _pick(rows)
            if c == END:
                break
            out.append(c)
            a, b = b, c
        if not out or " ".join(out) == " ".join(words(text)):
            return None
        return " ".join(out)
    finally:
        db.close()


def hear(chat, text, spoken_to=False):
    """A message in a chat: learned if the chatterbox is on there, and maybe answered."""
    on, chance, _links = state(chat)
    if not on or not text.strip():
        return None
    learn(chat, text)
    now = time.time()
    if not spoken_to:
        if random.randint(1, 100) > chance:
            return None
        with _lock:
            if now - _last.get(chat, 0) < QUIET:
                return None
            _last[chat] = now
    return say(chat, text, anyhow=spoken_to)
