"""The group chatterbox: off until switched on, learns no links or names, answers on topic."""
import os
import pathlib
import random
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import chatter  # noqa: E402


class ChatterTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        chatter.DB = os.path.join(self.folder.name, "chatter.db")
        chatter.prepare()
        chatter._last.clear()

    def tearDown(self):
        self.folder.cleanup()

    def test_off_until_switched_on(self):
        self.assertIsNone(chatter.hear(1, "привет всем", spoken_to=True))
        self.assertEqual(chatter.state(1), (False, chatter.CHANCE, 0))

    def test_learns_and_answers_on_topic(self):
        chatter.switch(1, True)
        for line in ("в маргите нашли вирус, удалите его", "удалите вирус маргит срочно",
                     "маргит лучший мод для тиктока"):
            chatter.hear(1, line)
        random.seed(1)
        # "вирусом" is found through "вирус", a word of its own message starts the answer
        remark = chatter.say(1, "что там с вирусом?")
        self.assertIsNotNone(remark)
        self.assertTrue(any(w.startswith(("вирус", "удалите", "маргит", "в")) for w in remark.split()))
        self.assertIsNone(chatter.say(1, "ыыы ааа"))
        self.assertIsNotNone(chatter.say(1, "ыыы ааа", anyhow=True))

    def test_keeps_no_links_or_names(self):
        chatter.switch(1, True)
        chatter.learn(1, "смотри @someone https://t.me/x www.example.com вот")
        db = chatter.connect()
        kept = [r[0] for r in db.execute("SELECT a || ' ' || b || ' ' || c FROM link WHERE chat = 1")]
        db.close()
        self.assertFalse(any("@" in k or "t.me" in k or "www." in k or "http" in k for k in kept), kept)

    def test_chance_bounds_quiet_time_and_forget(self):
        chatter.switch(1, True)
        self.assertEqual(chatter.set_chance(1, 99), 50)
        self.assertEqual(chatter.set_chance(1, 0), 1)
        chatter.learn(1, "раз два три")
        chatter.forget(1)
        self.assertEqual(chatter.state(1)[2], 0)
        # off again: nothing more is learned
        chatter.switch(1, False)
        chatter.hear(1, "четыре пять шесть", spoken_to=True)
        self.assertEqual(chatter.state(1)[2], 0)


if __name__ == "__main__":
    unittest.main()
