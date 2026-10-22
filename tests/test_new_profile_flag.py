import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from margyt import dexpatch  # noqa: E402


class NewProfileFlagTest(unittest.TestCase):
    def test_every_read_of_tiktoks_kept_answer_asks_the_switch(self):
        smali = (".method public static final invoke$6538(Lkotlin/jvm/internal/AFwS227S0000000_3;)Ljava/lang/Object;\n"
                 "    .registers 2\n"
                 "    sget-boolean v0, LX/071Z;->LIZ:Z\n"
                 "    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;\n")
        rules = [rule for rule in dexpatch.model_rules() if rule[0] == "071Z.LIZ"]
        self.assertEqual(len(rules), 1)
        _label, pattern, target = rules[0]
        out, hits = pattern.subn(target, smali)
        self.assertEqual(hits, 1)
        self.assertIn("    invoke-static {}, Lcat/narezany/margyt/Flags;->newProfile()Z\n\n    move-result v0\n", out)
        self.assertNotIn("sget-boolean", out)
        self.assertIn("Lcat/narezany/margyt/Flags;->newProfile()Z", dexpatch.rewrite_targets())

    def test_the_switch_answers_early_and_leaves_tiktoks_group_alone(self):
        with open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Flags.java"), encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("public static boolean newProfile()", java)
        # the group decides the banner on top: forcing it took the banner away
        self.assertNotIn('made.put("profile_left_align_ab_group", one)', java)
        # with no context yet the table is not built, and the profile is still answered
        self.assertIn("if (known == null) return early(key);", java)
        self.assertIn("shared_prefs/\" + Margy.PREFS + \".xml\"", java)
        with open(os.path.join(ROOT, "margyt/build.py"), encoding="utf-8") as handle:
            self.assertIn('feature_hits["native new profile"] += counts.get("071Z.LIZ",0)', handle.read())


if __name__ == "__main__":
    unittest.main()
