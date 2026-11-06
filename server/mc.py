"""Who may play on MiCraft, the Minecraft server for the people who support TikTok Mi.

The server lets anybody's launcher in, pirated ones too, so a name proves
nothing there. This proves it instead, through the one account that is already
trusted: Telegram, where being in the supporters' group is what a supporter is.

    a name is tied once, in the bot      /mc <nick> from a phone, no link and no
                                        second device; or the link the game shows
    a player joins                      the plugin asks   POST /mc/join
    a name that is tied                 has a code sent to that Telegram account
    a name nobody has tied              waits, and the plugin asks again until it is
    the player types the code           the plugin asks   POST /mc/check

Once a name is tied to an account nobody else can use it, and an account can
tie only a couple of names. The same address within half a day is let in
without asking again. Banning bans the Telegram account, so a new name does
not get a griefer back in.

The plugin proves itself with a key in `mc.key`, which only the game server
has. Nothing here is for the public.
"""

import hashlib
import html
import os
import re
import secrets
import sqlite3
import threading
import time

import patcher

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "mc.db")
KEY_FILE = os.path.join(HERE, "mc.key")

# Bedrock players come through Geyser and Floodgate with a dot before the gamertag
NICK = re.compile(r"^\.?[A-Za-z0-9_]{3,16}$")
#: a code is good for five minutes and five wrong guesses; a start link for a quarter of an hour
CODE_LIVES = 300
PENDING_LIVES = 900
TRIES = 5
#: the same address is let in again for this long without a new code
SESSION = 12 * 3600
#: names one Telegram account may tie to itself
NICKS_EACH = 2
#: whether only supporters may play; since 2026-10-25 anyone with Telegram may, and
#: being a supporter gives a tag in the game instead
ONLY_SUPPORTERS = False
#: the least time between two codes for one name, and joins from one address in a minute
RESEND = 30
JOINS_MINUTE = 30
CHANNEL = "https://t.me/dktiktokmi"

SCHEMA = """
    CREATE TABLE IF NOT EXISTS mc_link (
        nick TEXT PRIMARY KEY, shown TEXT NOT NULL, tg INTEGER NOT NULL,
        name TEXT NOT NULL DEFAULT '', ip TEXT NOT NULL DEFAULT '',
        verified INTEGER NOT NULL DEFAULT 0, made INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS mc_code (
        nick TEXT PRIMARY KEY, code TEXT NOT NULL, tg INTEGER NOT NULL,
        ip TEXT NOT NULL DEFAULT '', made INTEGER NOT NULL,
        tries INTEGER NOT NULL DEFAULT 0, bind INTEGER NOT NULL DEFAULT 0,
        name TEXT NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS mc_pending (
        token TEXT PRIMARY KEY, nick TEXT NOT NULL, shown TEXT NOT NULL,
        ip TEXT NOT NULL DEFAULT '', made INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS mc_ban (
        tg INTEGER PRIMARY KEY, reason TEXT NOT NULL DEFAULT '',
        nick TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL
    );
"""

_local = threading.local()
_rate = {}
_rate_lock = threading.Lock()

#: seams for the tests: what asks Telegram, and who counts as a supporter
telegram = patcher._telegram
supporter = patcher.supporter
bot_name = patcher.bot_name


def db():
    link = getattr(_local, "link", None)
    if link is None or getattr(_local, "path", None) != DB:
        link = sqlite3.connect(DB, timeout=10)
        link.executescript(SCHEMA)
        _local.link = link
        _local.path = DB
    return link


def key():
    try:
        with open(KEY_FILE) as handle:
            return handle.read().strip()
    except OSError:
        return ""


def _allowed(header):
    known = key()
    return bool(known) and isinstance(header, str) and secrets.compare_digest(known, header.strip())


def _fast(kind, who, most, within=60):
    """True when `who` has done `kind` more than `most` times in `within` seconds."""
    now = time.time()
    with _rate_lock:
        for stale in [k for k, v in _rate.items() if now - v[1] > 3600]:
            del _rate[stale]
        k = "%s:%s" % (kind, who)
        n, since = _rate.get(k, (0, now))
        if now - since > within:
            n, since = 0, now
        _rate[k] = (n + 1, since)
        return n + 1 > most


def _clean(nick):
    nick = nick if isinstance(nick, str) else ""
    return nick if NICK.match(nick) else None


def _ip(value):
    return value[:64] if isinstance(value, str) else ""


def _banned(tg):
    return db().execute("SELECT reason FROM mc_ban WHERE tg = ?", (int(tg),)).fetchone()


def _code():
    return "%04d" % secrets.randbelow(10000)


def _send(tg, text):
    answer = telegram("sendMessage", chat_id=int(tg), text=text, parse_mode="HTML",
                      disable_web_page_preview="true")
    return bool(answer and answer.get("ok"))


def _link_for(token):
    name = bot_name()
    return "https://t.me/%s?start=m_%s" % (name, token) if name else ""


def _start(nick, shown, ip):
    """What a name nobody has tied has to do: tie it in the bot, or open a one-time link to it.

    The plugin asks again every few seconds while it waits, and a link made a
    moment ago is the same link: asking is not a reason to make another.
    """
    now = int(time.time())
    link = db()
    row = link.execute("SELECT token FROM mc_pending WHERE nick = ? AND ip = ? AND made > ?",
                       (nick, ip, now - PENDING_LIVES // 2)).fetchone()
    if row is None:
        token = secrets.token_urlsafe(9)
        link.execute("DELETE FROM mc_pending WHERE nick = ?", (nick,))
        link.execute("INSERT INTO mc_pending (token, nick, shown, ip, made) VALUES (?,?,?,?,?)",
                     (token, nick, shown, ip, now))
        link.commit()
    else:
        token = row[0]
    return {"state": "register", "bot": bot_name(), "link": _link_for(token)}


# ------------------------------------------------------------ what the plugin asks

_swept = [0]


def _sweep(now):
    """Old codes and start links, cleared out every ten minutes at the most."""
    if now - _swept[0] < 600:
        return
    _swept[0] = now
    link = db()
    link.execute("DELETE FROM mc_code WHERE made < ?", (now - 2 * CODE_LIVES,))
    link.execute("DELETE FROM mc_pending WHERE made < ?", (now - 2 * PENDING_LIVES,))
    link.commit()


def join(body):
    _sweep(int(time.time()))
    shown = _clean(body.get("nick"))
    if not shown:
        return 400, {"error": "nick"}
    nick, ip, now = shown.lower(), _ip(body.get("ip")), int(time.time())
    if _fast("join", ip or nick, JOINS_MINUTE):
        return 429, {"state": "wait", "seconds": 30}
    row = db().execute("SELECT tg, name, ip, verified FROM mc_link WHERE nick = ?", (nick,)).fetchone()
    if row is None:
        return 200, _start(nick, shown, ip)
    tg, name, was, verified = row
    ban = _banned(tg)
    if ban is not None:
        return 200, {"state": "banned", "reason": ban[0]}
    if ONLY_SUPPORTERS and not supporter(tg):
        return 200, {"state": "nosupport"}
    if ip and ip == was and now - verified < SESSION:
        return 200, {"state": "ok"}
    held = db().execute("SELECT made FROM mc_code WHERE nick = ? AND bind = 0", (nick,)).fetchone()
    if held and now - held[0] < RESEND:
        return 200, {"state": "code", "fresh": False}
    code = _code()
    if not _send(tg, "Привет, <b>%s</b>!\n\nНа MiCraft заходят под ником <b>%s</b>.\n"
                     "Твой код: <code>%s</code>\n\nВведи его в игре. Если это не ты, ничего не делай, "
                     "код сам перестанет работать." % (html.escape(name or "друг"), html.escape(shown), code)):
        # the account blocked the bot or deleted the chat: tie it again through a start link
        return 200, _start(nick, shown, ip)
    link = db()
    link.execute("INSERT OR REPLACE INTO mc_code (nick, code, tg, ip, made, tries, bind, name) VALUES (?,?,?,?,?,0,0,?)",
                 (nick, code, int(tg), ip, now, name or ""))
    link.commit()
    return 200, {"state": "code", "fresh": True}


def check(body):
    shown = _clean(body.get("nick"))
    code = str(body.get("code") or "").strip()
    if not shown or not re.fullmatch(r"[0-9]{4}", code):
        return 400, {"error": "code"}
    nick, ip, now = shown.lower(), _ip(body.get("ip")), int(time.time())
    link = db()
    row = link.execute("SELECT code, tg, made, tries, bind, name FROM mc_code WHERE nick = ?", (nick,)).fetchone()
    if row is None:
        return 200, {"state": "none"}
    good, tg, made, tries, bind, name = row
    if now - made > CODE_LIVES or tries >= TRIES:
        link.execute("DELETE FROM mc_code WHERE nick = ?", (nick,))
        link.commit()
        return 200, {"state": "expired"}
    if not secrets.compare_digest(good, code):
        link.execute("UPDATE mc_code SET tries = tries + 1 WHERE nick = ?", (nick,))
        link.commit()
        left = TRIES - tries - 1
        if left <= 0:
            link.execute("DELETE FROM mc_code WHERE nick = ?", (nick,))
            link.commit()
            return 200, {"state": "expired"}
        return 200, {"state": "wrong", "left": left}
    ban = _banned(tg)
    if ban is not None:
        return 200, {"state": "banned", "reason": ban[0]}
    if ONLY_SUPPORTERS and not supporter(tg):
        return 200, {"state": "nosupport"}
    owner = link.execute("SELECT tg FROM mc_link WHERE nick = ?", (nick,)).fetchone()
    if owner is not None and owner[0] != tg:
        link.execute("DELETE FROM mc_code WHERE nick = ?", (nick,))
        link.commit()
        return 200, {"state": "taken"}
    if owner is None:
        mine = link.execute("SELECT COUNT(*) FROM mc_link WHERE tg = ?", (tg,)).fetchone()[0]
        if mine >= NICKS_EACH:
            link.execute("DELETE FROM mc_code WHERE nick = ?", (nick,))
            link.commit()
            return 200, {"state": "toomany", "most": NICKS_EACH}
        link.execute("INSERT INTO mc_link (nick, shown, tg, name, ip, verified, made) VALUES (?,?,?,?,?,?,?)",
                     (nick, shown, tg, name, ip, now, now))
    else:
        link.execute("UPDATE mc_link SET shown = ?, ip = ?, verified = ? WHERE nick = ?", (shown, ip, now, nick))
    link.execute("DELETE FROM mc_code WHERE nick = ?", (nick,))
    link.commit()
    return 200, {"state": "ok"}


def _tg_of(body):
    if isinstance(body.get("tg"), int):
        return body["tg"]
    nick = _clean(body.get("nick"))
    if not nick:
        return None
    row = db().execute("SELECT tg FROM mc_link WHERE nick = ?", (nick.lower(),)).fetchone()
    return row[0] if row else None


def ban(body):
    tg = _tg_of(body)
    if tg is None:
        return 404, {"error": "no such player"}
    reason = " ".join(str(body.get("reason") or "").split())[:200]
    link = db()
    names = [r[0] for r in link.execute("SELECT shown FROM mc_link WHERE tg = ?", (tg,))]
    link.execute("INSERT OR REPLACE INTO mc_ban (tg, reason, nick, at) VALUES (?,?,?,?)",
                 (tg, reason, ",".join(names), int(time.time())))
    link.execute("UPDATE mc_link SET verified = 0 WHERE tg = ?", (tg,))
    link.commit()
    return 200, {"names": names}


def unban(body):
    tg = _tg_of(body)
    if tg is None:
        return 404, {"error": "no such player"}
    link = db()
    link.execute("DELETE FROM mc_ban WHERE tg = ?", (tg,))
    link.commit()
    return 200, {}


def info(body):
    tg = _tg_of(body)
    if tg is None:
        return 404, {"error": "no such player"}
    link = db()
    names = [r[0] for r in link.execute("SELECT shown FROM mc_link WHERE tg = ?", (tg,))]
    ban = _banned(tg)
    return 200, {"tg": tg, "names": names, "banned": ban is not None, "reason": ban[0] if ban else "",
                 "supporter": bool(supporter(tg))}


def unlink(body):
    nick = _clean(body.get("nick"))
    if not nick:
        return 400, {"error": "nick"}
    link = db()
    link.execute("DELETE FROM mc_link WHERE nick = ?", (nick.lower(),))
    link.commit()
    return 200, {}


#: how sending to many is taken off the request; the tests send at once
later = lambda work: threading.Thread(target=work, daemon=True).start()


def notify(body):
    """News from the game for everyone who tied a name (a meteor is coming), but not for those
    already in the game or who turned such news off: the plugin names them in `skip`.

    At most once in four minutes, so a key that leaked cannot flood people's chats.
    """
    text = str(body.get("text") or "").strip()[:1000]
    if not text:
        return 400, {"error": "text"}
    skip = body.get("skip") if isinstance(body.get("skip"), list) else []
    skip = {n.lower() for n in skip if isinstance(n, str)}
    if _fast("notify", "game", 1, within=240):
        return 429, {"error": "too often"}
    accounts = {}
    for nick, tg in db().execute("SELECT nick, tg FROM mc_link WHERE verified > 0"):
        accounts.setdefault(tg, []).append(nick)
    to = [tg for tg, nicks in accounts.items() if not any(n in skip for n in nicks) and not _banned(tg)]
    words = html.escape(text)

    def send_all():
        for tg in to:
            _send(tg, words)
            time.sleep(0.05)

    later(send_all)
    return 200, {"sent": len(to)}


ROUTES = {"/mc/join": join, "/mc/check": check, "/mc/ban": ban, "/mc/unban": unban,
          "/mc/info": info, "/mc/unlink": unlink, "/mc/notify": notify}


def handle(path, body, header):
    """What the HTTP server calls for /mc/*. (status, answer)."""
    if not _allowed(header):
        return 403, {"error": "no"}
    route = ROUTES.get(path)
    if route is None:
        return 404, {"error": "no such thing"}
    return route(body)


# ------------------------------------------------------------ what the bot tells

def _mine(who):
    return [r[0] for r in db().execute("SELECT shown FROM mc_link WHERE tg = ? ORDER BY made", (int(who),))]


def mine(who):
    """The words for somebody who asked which names are theirs."""
    names = _mine(who)
    if not names:
        return ("Ники MiCraft ещё не привязаны. Пришли ник в Minecraft одним сообщением "
                "(или командой <code>/mc ник</code>).")
    return ("Твои ники: <b>%s</b>.\n\nПривязать ещё один: <code>/mc ник</code>. "
            "Убрать: <code>/mcdel ник</code>." % html.escape(", ".join(names)))


def bind(who, name, text):
    """Somebody sent the Minecraft name they play under. The words to answer them with."""
    who = int(who)
    shown = _clean((text or "").strip())
    if not shown:
        return ("Ник в Minecraft это от 3 до 16 знаков: английские буквы, цифры и подчёркивание. "
                "Пришли его ещё раз.")
    nick = shown.lower()
    if _banned(who) is not None:
        return "Этот аккаунт заблокирован на MiCraft."
    if ONLY_SUPPORTERS and not supporter(who):
        return ("MiCraft только для поддержавших TikTok Mi. Когда окажешься в группе поддержавших, "
                "напиши мне ник ещё раз. Как поддержать: %s" % CHANNEL)
    link = db()
    owner = link.execute("SELECT tg FROM mc_link WHERE nick = ?", (nick,)).fetchone()
    if owner is not None and owner[0] != who:
        return "Ник <b>%s</b> уже занят другим аккаунтом." % html.escape(shown)
    if owner is not None:
        return "Ник <b>%s</b> уже привязан к тебе. Заходи на сервер, код придёт сюда." % html.escape(shown)
    if len(_mine(who)) >= NICKS_EACH:
        return ("К одному аккаунту можно привязать только %d ника. Убери лишний: <code>/mcdel ник</code>. "
                "Сейчас привязаны: %s." % (NICKS_EACH, html.escape(", ".join(_mine(who)))))
    now = int(time.time())
    link.execute("INSERT INTO mc_link (nick, shown, tg, name, ip, verified, made) VALUES (?,?,?,?,'',0,?)",
                 (nick, shown, who, (name or "")[:64], now))
    link.execute("DELETE FROM mc_pending WHERE nick = ?", (nick,))
    link.commit()
    return ("Готово, ник <b>%s</b> привязан.\n\nЗаходи на сервер MiCraft. Когда зайдёшь, "
            "я пришлю сюда код, введи его в игре прямо в чат." % html.escape(shown))


def unbind(who, text):
    """Somebody wants one of their names off their account."""
    shown = _clean((text or "").strip())
    if not shown:
        return "Напиши ник, который убрать: <code>/mcdel ник</code>."
    link = db()
    done = link.execute("DELETE FROM mc_link WHERE nick = ? AND tg = ?", (shown.lower(), int(who))).rowcount
    link.execute("DELETE FROM mc_code WHERE nick = ? AND tg = ?", (shown.lower(), int(who)))
    link.commit()
    return ("Ник <b>%s</b> больше не привязан." % html.escape(shown)) if done else "Такого ника у тебя нет."


def claim(token, who, name):
    """Somebody pressed Start with a MiCraft token. The words to answer them with."""
    now = int(time.time())
    link = db()
    row = link.execute("SELECT nick, shown, ip, made FROM mc_pending WHERE token = ?", (str(token),)).fetchone()
    if row is None or now - row[3] > PENDING_LIVES:
        return ("Эта ссылка уже не действует. Зайди на сервер ещё раз, и он выдаст новую.")
    nick, shown, ip, _ = row
    who = int(who)
    if _banned(who) is not None:
        return "Этот аккаунт заблокирован на MiCraft."
    if ONLY_SUPPORTERS and not supporter(who):
        return ("MiCraft только для поддержавших TikTok Mi. Когда окажешься в группе поддержавших, "
                "нажми ссылку ещё раз. Как поддержать: %s" % CHANNEL)
    owner = link.execute("SELECT tg FROM mc_link WHERE nick = ?", (nick,)).fetchone()
    if owner is not None and owner[0] != who:
        return "Ник <b>%s</b> уже занят другим аккаунтом." % html.escape(shown)
    bind = 0
    if owner is None:
        mine = link.execute("SELECT COUNT(*) FROM mc_link WHERE tg = ?", (who,)).fetchone()[0]
        if mine >= NICKS_EACH:
            return "К одному аккаунту можно привязать только %d ника." % NICKS_EACH
        bind = 1
    code = _code()
    link.execute("INSERT OR REPLACE INTO mc_code (nick, code, tg, ip, made, tries, bind, name) VALUES (?,?,?,?,?,0,?,?)",
                 (nick, code, who, ip, now, bind, (name or "")[:64]))
    link.execute("DELETE FROM mc_pending WHERE token = ?", (str(token),))
    link.commit()
    return ("Привет, <b>%s</b>!\n\nНик на MiCraft: <b>%s</b>\nТвой код: <code>%s</code>\n\n"
            "Введи его в игре, прямо в чат. Код живёт 5 минут."
            % (html.escape((name or "друг")[:40]), html.escape(shown), code))
