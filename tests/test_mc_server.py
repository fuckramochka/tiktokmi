import os
import pathlib
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import mc  # noqa: E402


class Fake:
    """Telegram and the supporters' group, as the tests want them."""

    def __init__(self):
        self.sent = []
        self.deliver = True
        self.supporters = {101, 202}

    def telegram(self, what, **fields):
        if what == "sendMessage":
            self.sent.append((fields["chat_id"], fields["text"]))
            return {"ok": self.deliver}
        return {}

    def code(self):
        return self.sent[-1][1].split("<code>")[1].split("</code>")[0]


class McTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        mc.DB = os.path.join(self.dir.name, "mc.db")
        mc.KEY_FILE = os.path.join(self.dir.name, "mc.key")
        with open(mc.KEY_FILE, "w") as handle:
            handle.write("secret")
        mc._rate.clear()
        self.fake = Fake()
        mc.telegram = self.fake.telegram
        mc.supporter = lambda who: who in self.fake.supporters
        mc.bot_name = lambda: "margybot"

    def tearDown(self):
        mc._local.link = None
        self.dir.cleanup()

    def ask(self, path, **body):
        return mc.handle(path, body, "secret")

    def first_login(self, nick="Margy", who=101, ip="1.1.1.1"):
        code, out = self.ask("/mc/join", nick=nick, ip=ip)
        self.assertEqual(out["state"], "register")
        token = out["link"].split("start=m_")[1]
        said = mc.claim(token, who, "Маргарита")
        self.assertIn("Твой код", said)
        return said.split("<code>")[1].split("</code>")[0]

    def test_the_key_is_asked_for(self):
        self.assertEqual(mc.handle("/mc/join", {"nick": "Margy"}, "wrong")[0], 403)
        self.assertEqual(mc.handle("/mc/join", {"nick": "Margy"}, "")[0], 403)

    def test_a_new_name_goes_through_the_bot_and_is_tied(self):
        code = self.first_login()
        _, out = self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        self.assertEqual(out["state"], "ok")
        # the same address a minute later gets in without a code
        _, out = self.ask("/mc/join", nick="margy", ip="1.1.1.1")
        self.assertEqual(out["state"], "ok")
        self.assertEqual(self.fake.sent, [])

    def test_a_tied_name_gets_its_code_in_telegram_from_a_new_address(self):
        code = self.first_login()
        self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        _, out = self.ask("/mc/join", nick="Margy", ip="9.9.9.9")
        self.assertEqual(out, {"state": "code", "fresh": True})
        self.assertEqual(self.fake.sent[-1][0], 101)
        _, out = self.ask("/mc/check", nick="Margy", ip="9.9.9.9", code=self.fake.code())
        self.assertEqual(out["state"], "ok")

    def test_a_wrong_code_runs_out(self):
        code = self.first_login()
        wrong = "0000" if code != "0000" else "1111"
        states = [self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=wrong)[1]["state"] for _ in range(5)]
        self.assertEqual(states, ["wrong"] * 4 + ["expired"])
        # and the right one is no good any more
        self.assertEqual(self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)[1]["state"], "none")

    def test_somebody_who_is_not_a_supporter_is_refused(self):
        _, out = self.ask("/mc/join", nick="Stranger", ip="2.2.2.2")
        said = mc.claim(out["link"].split("start=m_")[1], 999, "Чужой")
        self.assertIn("только для поддержавших", said)

    def test_a_name_belongs_to_one_account(self):
        code = self.first_login()
        self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        _, out = self.ask("/mc/join", nick="Margy", ip="3.3.3.3")
        self.fake.sent.clear()
        # another supporter opens a start link for the same name
        mc.db().execute("INSERT INTO mc_pending (token, nick, shown, ip, made) VALUES ('t', 'margy', 'Margy', '3.3.3.3', strftime('%s','now'))")
        mc.db().commit()
        self.assertIn("уже занят", mc.claim("t", 202, "Другой"))

    def test_an_account_ties_two_names_and_no_more(self):
        for nick in ("One_", "Two_"):
            code = self.first_login(nick=nick)
            self.assertEqual(self.ask("/mc/check", nick=nick, ip="1.1.1.1", code=code)[1]["state"], "ok")
        _, out = self.ask("/mc/join", nick="Three", ip="1.1.1.1")
        self.assertIn("только 2", mc.claim(out["link"].split("start=m_")[1], 101, "Маргарита"))

    def test_a_ban_follows_the_account_to_a_new_name(self):
        code = self.first_login()
        self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        _, out = self.ask("/mc/ban", nick="Margy", reason="grief")
        self.assertEqual(out["names"], ["Margy"])
        self.assertEqual(self.ask("/mc/join", nick="Margy", ip="1.1.1.1")[1]["state"], "banned")
        _, out = self.ask("/mc/join", nick="Fresh", ip="1.1.1.1")
        self.assertIn("заблокирован", mc.claim(out["link"].split("start=m_")[1], 101, "Маргарита"))
        self.ask("/mc/unban", nick="Margy")
        # a ban also ends the session, so a code is asked for again
        self.assertEqual(self.ask("/mc/join", nick="Margy", ip="1.1.1.1")[1]["state"], "code")

    def test_leaving_the_group_closes_the_door(self):
        code = self.first_login()
        self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        self.fake.supporters.discard(101)
        self.assertEqual(self.ask("/mc/join", nick="Margy", ip="1.1.1.1")[1]["state"], "nosupport")

    def test_a_blocked_bot_falls_back_to_a_start_link(self):
        code = self.first_login()
        self.ask("/mc/check", nick="Margy", ip="1.1.1.1", code=code)
        self.fake.deliver = False
        _, out = self.ask("/mc/join", nick="Margy", ip="8.8.8.8")
        self.assertEqual(out["state"], "register")
        self.fake.deliver = True
        said = mc.claim(out["link"].split("start=m_")[1], 101, "Маргарита")
        self.assertIn("Твой код", said)

    def test_names_are_checked_and_joins_are_limited(self):
        self.assertEqual(self.ask("/mc/join", nick="a b", ip="1.1.1.1")[0], 400)
        for _ in range(mc.JOINS_MINUTE):
            self.ask("/mc/join", nick="Spam", ip="7.7.7.7")
        self.assertEqual(self.ask("/mc/join", nick="Spam", ip="7.7.7.7")[0], 429)

    def test_a_name_tied_in_the_bot_gets_its_code_when_the_player_joins(self):
        said = mc.bind(101, "Маргарита", "Phone_Player")
        self.assertIn("привязан", said)
        # nothing yet asked for a code, and the first join sends one
        self.assertEqual(self.fake.sent, [])
        _, out = self.ask("/mc/join", nick="phone_player", ip="4.4.4.4")
        self.assertEqual(out, {"state": "code", "fresh": True})
        self.assertEqual(self.fake.sent[-1][0], 101)
        _, out = self.ask("/mc/check", nick="Phone_Player", ip="4.4.4.4", code=self.fake.code())
        self.assertEqual(out["state"], "ok")

    def test_a_waiting_player_is_not_given_a_new_link_each_time_but_a_tied_name_is_noticed(self):
        _, one = self.ask("/mc/join", nick="Waiter", ip="5.5.5.5")
        _, two = self.ask("/mc/join", nick="Waiter", ip="5.5.5.5")
        self.assertEqual(one["state"], "register")
        self.assertEqual(one["link"], two["link"])
        self.assertEqual(one["bot"], "margybot")
        self.assertEqual(mc.db().execute("SELECT COUNT(*) FROM mc_pending").fetchone()[0], 1)
        mc.bind(101, "Маргарита", "Waiter")
        _, three = self.ask("/mc/join", nick="Waiter", ip="5.5.5.5")
        self.assertEqual(three["state"], "code")

    def test_the_bot_refuses_what_it_should(self):
        self.assertIn("от 3 до 16", mc.bind(101, "М", "a b"))
        self.assertIn("только для поддержавших", mc.bind(999, "Чужой", "Stranger"))
        mc.bind(101, "М", "Taken_Nick")
        self.assertIn("уже занят", mc.bind(202, "Другой", "taken_nick"))
        self.assertIn("уже привязан к тебе", mc.bind(101, "М", "Taken_Nick"))
        mc.bind(101, "М", "Second")
        self.assertIn("только 2", mc.bind(101, "М", "Third"))
        self.assertIn("Taken_Nick", mc.mine(101))
        mc.ban({"tg": 202})
        self.assertIn("заблокирован", mc.bind(202, "Другой", "Banned_Nick"))

    def test_a_name_can_be_taken_off_an_account_by_its_owner_only(self):
        mc.bind(101, "М", "Mine_Nick")
        self.assertIn("такого ника у тебя нет", mc.unbind(202, "Mine_Nick").lower())
        self.assertIn("больше не привязан", mc.unbind(101, "mine_nick"))
        self.assertEqual(self.ask("/mc/join", nick="Mine_Nick", ip="6.6.6.6")[1]["state"], "register")

    def test_an_old_link_is_no_good(self):
        _, out = self.ask("/mc/join", nick="Late", ip="1.1.1.1")
        token = out["link"].split("start=m_")[1]
        mc.db().execute("UPDATE mc_pending SET made = made - ?", (mc.PENDING_LIVES + 5,))
        mc.db().commit()
        self.assertIn("уже не действует", mc.claim(token, 101, "Маргарита"))


if __name__ == "__main__":
    unittest.main()
