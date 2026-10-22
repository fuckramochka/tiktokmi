import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from margyt import dexpatch  # noqa: E402


class NewProfileFlagTest(unittest.TestCase):
    """The profile is the server's again, as in 2.0.5: only the flag itself."""

    def test_nothing_of_tiktoks_profile_is_rewritten(self):
        labels = [rule[0] for rule in dexpatch.model_rules()]
        self.assertNotIn("071Z.LIZ", labels)
        self.assertNotIn("0sm9->LJIILIIL", labels)
        self.assertEqual(dexpatch.STATIC_FIELD_SOURCES, [])
        self.assertNotIn("Lcat/narezany/margyt/Flags;->newProfile()Z", dexpatch.rewrite_targets())

    def test_the_switch_is_the_flag_and_nothing_beside_it(self):
        with open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Flags.java"), encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn('made.put("profile_left_align", on(prefs, KEY_NEW_PROFILE) ? one : none);', java)
        for part in ("avatar_and_info_merge", "profile_left_align_pronouns", "recommend_card",
                     "avatar_opt", "profile_left_align_ab_group"):
            self.assertNotIn(part, java)
        # before there is a context the mod says nothing, and TikTok keeps its own answer
        self.assertIn("if (known == null) return null;", java)
        self.assertFalse(os.path.exists(os.path.join(ROOT, "inject/java/cat/narezany/margyt/ProfileHeader.java")))


if __name__ == "__main__":
    unittest.main()
