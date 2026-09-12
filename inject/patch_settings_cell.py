#!/usr/bin/env python3
"""Add the MargyT row to TikTok's own settings list.

The list is not a layout. `ContentGroupVM.o53()` returns an ordered list of
keys from the enum X/1EVx, and `ContentGroupVM.m53()` turns each key into a
cell view model through a packed-switch on the key's ordinal. The switch is
indexed through X/1EVv, the usual Kotlin `WhenMappings` array.

So a new row means four edits, in this order:

  1. X/1EVx        a new enum constant, MARGYT, and room for it in $VALUES
  2. X/1EVv        map its ordinal to a fresh switch index
  3. ContentGroupVM  a branch for that index, and the key at the top of the list
  4. the cell      MargyTVM.smali, dropped in beside the stock ones

Everything is located by content rather than by line, and every edit refuses to
apply twice, so this can be re-run over a tree that is already patched.
"""

import glob
import os
import pathlib
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "work/full"
HERE = pathlib.Path(__file__).parent

ENUM = "LX/1EVx;"
CONST = "MARGYT"
CELL = "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/content/cell/MargyTVM;"
TITLE_ID = "0x7f119eca"
TITLE_NAME = "margyt_settings"
TITLE_TEXT = "MargyT"


def find(pattern):
    hits = glob.glob(os.path.join(ROOT, pattern))
    if len(hits) != 1:
        raise SystemExit("expected exactly one %s, found %d" % (pattern, len(hits)))
    return pathlib.Path(hits[0])


def edit(path, change):
    text = path.read_text(encoding="utf-8")
    new = change(text)
    if new is None:
        print("  %-22s already patched" % path.name)
        return False
    path.write_text(new, encoding="utf-8")
    print("  %-22s patched" % path.name)
    return True


def once(text, needle, replacement, what):
    if text.count(needle) != 1:
        raise SystemExit("%s: expected one %r, found %d"
                         % (what, needle[:60], text.count(needle)))
    return text.replace(needle, replacement)


# ------------------------------------------------------------------ 1. enum

def patch_enum(text):
    if CONST in text:
        return None

    # the constant itself
    text = once(
        text,
        ".field public static final enum SECTION_HEADER:%s" % ENUM,
        ".field public static final enum %s:%s\n\n"
        ".field public static final enum SECTION_HEADER:%s" % (CONST, ENUM, ENUM),
        "enum field",
    )

    # $VALUES has to fit one more
    text = once(
        text,
        "    const/16 v0, 0x15\n\n    .line 479\n    .line 480\n    new-array v0, v0, [%s" % ENUM,
        "    const/16 v0, 0x16\n\n    .line 479\n    .line 480\n    new-array v0, v0, [%s" % ENUM,
        "$VALUES size",
    )

    # Built last, right before the array is published: by that point every
    # register the rest of <clinit> was using is dead, so v1..v9 are free.
    #
    # Constructor is (name, key, a, b, c, ordinal, flag, int) -- the sixth
    # argument is the ordinal, and ours is 0x15, one past SECTION_HEADER's run.
    build = (
        "    new-instance v1, %s\n\n"
        '    const-string v2, "%s"\n\n'
        '    const-string v3, "margyt"\n\n'
        '    const-string v4, "margyt"\n\n'
        '    const-string v5, "margyt"\n\n'
        "    const/4 v6, 0x0\n\n"
        "    const/16 v7, 0x15\n\n"
        "    const/4 v8, 0x0\n\n"
        "    const/16 v9, 0x10\n\n"
        "    invoke-direct/range {v1 .. v9}, %s-><init>("
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;IZI)V\n\n"
        "    sput-object v1, %s->%s:%s\n\n"
        "    const/16 v2, 0x15\n\n"
        "    aput-object v1, v0, v2\n\n"
        % (ENUM, CONST, ENUM, ENUM, CONST, ENUM)
    )
    return once(
        text,
        "    sput-object v0, %s->LLJJIJI:[%s" % (ENUM, ENUM),
        build + "    sput-object v0, %s->LLJJIJI:[%s" % (ENUM, ENUM),
        "$VALUES publish",
    )


# ------------------------------------------------------------- 2. switch map

def patch_switch_map(text):
    if CONST in text:
        return None

    # Same shape as the twenty-one blocks above it. The array is sized from
    # values().length at runtime, so it has already grown by one.
    #
    # The labels are named rather than numbered. The file's own are hex and run
    # 0x0..0x14 for this array -- and then the second one, built from
    # Lifecycle$Event just below, starts over at 0x15. Taking the "next free"
    # number collides with it, which the assembler catches as
    # "There is already a label with that name".
    block = (
        "    :try_start_margyt\n"
        "    sget-object v0, %s->%s:%s\n\n"
        "    invoke-virtual {v0}, Ljava/lang/Enum;->ordinal()I\n\n"
        "    move-result v1\n\n"
        "    const/16 v0, 0x16\n\n"
        "    aput v0, v2, v1\n\n"
        "    goto :goto_margyt\n"
        "    :try_end_margyt\n"
        "    .catch Ljava/lang/NoSuchFieldError; "
        "{:try_start_margyt .. :try_end_margyt} :catch_margyt\n\n"
        "    :catch_margyt\n"
        "    move-exception v0\n\n"
        "    invoke-static {v0}, Lcom/bytedance/tt/reliability/monitor/catchchecker/"
        "TryCatchGuardChecker;->doCheck(Ljava/lang/Throwable;)V\n\n"
        "    :goto_margyt\n"
        % (ENUM, CONST, ENUM)
    )
    return once(
        text,
        "    sput-object v2, LX/1EVv;->LIZ:[I",
        block + "    sput-object v2, LX/1EVv;->LIZ:[I",
        "switch map",
    )


# ---------------------------------------------------------------- 3. the VM

def patch_group(text):
    if CELL in text:
        return None

    # One more arm on the switch table.
    text = once(
        text,
        "        :pswitch_14\n    .end packed-switch",
        "        :pswitch_14\n        :pswitch_margyt\n    .end packed-switch",
        "packed-switch table",
    )

    # The branch itself, cloned from LANGUAGE (:pswitch_a). Only the class
    # changes: LX/0CTX is keyed to the state type LX/0CSz, which our cell
    # shares, and the two dispatcher indices are generic plumbing.
    branch = (
        "    :pswitch_margyt\n"
        "    const/16 v0, 0x155\n\n"
        "    invoke-static {v0}, Lkotlin/jvm/internal/AFwS208S0000000_4;"
        "->get$arr$(I)Lkotlin/jvm/internal/AFwS208S0000000_4;\n\n"
        "    move-result-object v8\n\n"
        "    const-class v0, %s\n\n"
        "    invoke-static {v0}, LX/16Xy;->LIZ(Ljava/lang/Class;)LX/170x;\n\n"
        "    move-result-object v1\n\n"
        "    new-instance v4, LX/01zX;\n\n"
        "    const/16 v0, 0x47\n\n"
        "    invoke-direct {v4, v1, v0}, LX/01zX;-><init>(Ljava/lang/Object;I)V\n\n"
        "    sget-object v9, LX/0CTX;->LL:LX/0CTX;\n\n"
        "    new-instance v2, Lcom/bytedance/assem/arch/viewModel/AssemVMLazy;\n\n"
        "    invoke-static {p1, v1}, LX/0SDd;->LJIIJ(Landroidx/lifecycle/LifecycleOwner;"
        "LX/14ur;)Lkotlin/jvm/functions/Function0;\n\n"
        "    move-result-object v3\n\n"
        "    new-instance v5, LY/AObjectS58S0000000_1;\n\n"
        "    const/4 v0, 0x1\n\n"
        "    invoke-direct {v5, v0}, LY/AObjectS58S0000000_1;-><init>(I)V\n\n"
        "    invoke-static {p1}, LX/0SDd;->LJII(Landroidx/lifecycle/LifecycleOwner;)"
        "LY/AObjectS88S0110000_1;\n\n"
        "    move-result-object v6\n\n"
        "    invoke-static {p1}, LX/0SDd;->LJIIIZ(Landroidx/lifecycle/ViewModelStoreOwner;)"
        "LY/AObjectS90S0110000_4;\n\n"
        "    move-result-object v7\n\n"
        "    invoke-direct/range {v2 .. v9}, Lcom/bytedance/assem/arch/viewModel/"
        "AssemVMLazy;-><init>(Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;"
        "LY/AObjectS58S0000000_1;LY/AObjectS88S0110000_1;LY/AObjectS90S0110000_4;"
        "Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;)V\n\n"
        "    invoke-virtual {v2}, Lcom/bytedance/assem/arch/viewModel/AssemVMLazy;"
        "->LIZ()Lcom/bytedance/assem/arch/viewModel/AssemViewModel;\n\n"
        "    move-result-object v0\n\n"
        "    check-cast v0, Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/"
        "BaseCellSettingsVM;\n\n"
        "    return-object v0\n\n"
        % CELL
    )
    text = once(
        text,
        "    :pswitch_data_0\n",
        branch + "    :pswitch_data_0\n",
        "switch branch",
    )

    # First row in the list. This is the curated path; when the other branch of
    # o53() is live the list comes straight from getEntries() and ours lands
    # last instead, by ordinal.
    return once(
        text,
        "    .line 18\n    sget-object v0, %s->SECTION_HEADER:%s" % (ENUM, ENUM),
        "    .line 18\n"
        "    sget-object v0, %s->%s:%s\n\n"
        "    invoke-virtual {v1, v0}, LX/1Brc;->add(Ljava/lang/Object;)Z\n\n"
        "    sget-object v0, %s->SECTION_HEADER:%s" % (ENUM, CONST, ENUM, ENUM, ENUM),
        "list order",
    )


# ------------------------------------------------------------ 4. resources

def patch_strings(text):
    if TITLE_NAME in text:
        return None
    return once(
        text,
        "</resources>",
        '    <string name="%s">%s</string>\n</resources>' % (TITLE_NAME, TITLE_TEXT),
        "strings.xml",
    )


def patch_public(text):
    if TITLE_NAME in text:
        return None
    if TITLE_ID in text:
        raise SystemExit("id %s is already taken -- pick another" % TITLE_ID)
    return once(
        text,
        "</resources>",
        '    <public type="string" name="%s" id="%s" />\n</resources>'
        % (TITLE_NAME, TITLE_ID),
        "public.xml",
    )


def main():
    enum = find("smali*/X/1EVx.smali")
    switch = find("smali*/X/1EVv.smali")
    group = find("smali*/com/ss/android/ugc/aweme/setting/ui/rvmpcompose/"
                 "group/content/ContentGroupVM.smali")

    edit(enum, patch_enum)
    edit(switch, patch_switch_map)
    edit(group, patch_group)
    edit(pathlib.Path(ROOT) / "res/values/strings.xml", patch_strings)
    edit(pathlib.Path(ROOT) / "res/values/public.xml", patch_public)

    # The cell goes next to the stock ones so it lands in the same dex.
    cells = find("smali*/com/ss/android/ugc/aweme/setting/ui/rvmpcompose/"
                 "group/content/cell/LanguageVM.smali").parent
    target = cells / "MargyTVM.smali"
    target.write_text((HERE / "smali/MargyTVM.smali").read_text(encoding="utf-8"),
                      encoding="utf-8")
    print("  %-22s written to %s" % ("MargyTVM.smali", cells.parts[-6]))


if __name__ == "__main__":
    main()
