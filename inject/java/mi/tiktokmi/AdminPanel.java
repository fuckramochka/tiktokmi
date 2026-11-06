package mi.tiktokmi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The panel's powers on somebody's profile, for the owner only.
 *
 * Shown to whoever wears the owner badge, and useless to anybody else: the
 * server asks for the badge, the account's token and a signature from a
 * phone approved with a code from Telegram (AdminDevice). The first time on
 * a phone that code is asked for here.
 */
final class AdminPanel {
    private AdminPanel() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Whether the one looking wears the owner's badge: only then is any of this offered. */
    static boolean owner() {
        try {
            Badges.Badge[] worn = Badges.of(Account.id());
            if (worn != null) for (Badges.Badge one : worn) if (one != null && "owner".equals(one.id)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Everything about one account, then what can be done to it. `block` is redrawn after a change. */
    static void open(final Context c, final String target, final View block) {
        Net.away("admin look", () -> {
            try {
                JSONObject look = AdminDevice.act("look", new JSONObject().put("target", target));
                MAIN.post(() -> sheet(c, target, look, block));
            } catch (AdminDevice.Refused refused) {
                if ("device".equals(refused.why)) approve(c, () -> open(c, target, block));
                else MAIN.post(() -> say(refused.why));
            } catch (Throwable error) {
                Diary.note("admin: " + error);
                MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
            }
        });
    }

    private static void say(String why) {
        Screen.say("prove".equals(why) ? Text.VERDICT_PROVE : Text.ADMIN_FAILED);
    }

    // ------------------------------------------------------------ the phone

    /** Make this phone the owner's: a code goes to Telegram and is typed in here. */
    private static void approve(final Context c, final Runnable then) {
        Net.away("admin device", () -> {
            try {
                String state = AdminDevice.register();
                if ("approved".equals(state)) { MAIN.post(then); return; }
                MAIN.post(() -> askCode(c, then));
            } catch (AdminDevice.Refused refused) {
                MAIN.post(() -> say(refused.why));
            } catch (Throwable error) {
                Diary.note("admin device: " + error);
                MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
            }
        });
    }

    private static void askCode(final Context c, final Runnable then) {
        final Panel panel = Panel.with(c, Skin.remembered(c), Text.ADMIN).text(Text.ADMIN_CODE_NOTE);
        final android.widget.EditText code = panel.field(Text.ADMIN_CODE);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        panel.primaryKeepOpen(Text.ADMIN_CONFIRM, () -> {
            final String typed = code.getText().toString().trim();
            panel.busy(true, null);
            Net.away("admin confirm", () -> {
                String trouble = null;
                try {
                    AdminDevice.act("confirm", new JSONObject().put("code", typed));
                } catch (AdminDevice.Refused refused) {
                    trouble = "wrong".equals(refused.why) ? Text.ADMIN_WRONG
                            : "expired".equals(refused.why) ? Text.ADMIN_EXPIRED : Text.ADMIN_FAILED;
                } catch (Throwable error) {
                    Diary.note("admin confirm: " + error);
                    trouble = Text.ADMIN_FAILED;
                }
                final String said = trouble;
                MAIN.post(() -> {
                    if (said != null) { panel.busy(false, said); return; }
                    panel.close();
                    then.run();
                });
            });
        });
        panel.quiet(Text.CANCEL, null).show();
    }

    // ------------------------------------------------------------ the sheet

    private static void sheet(final Context c, final String target, final JSONObject look, final View block) {
        Panel panel = Panel.with(c, Skin.remembered(c), Text.ADMIN);
        String name = look.optString("name", "");
        panel.subtitle((name.length() > 0 ? "@" + name + " · " : "") + target
                + (look.optBoolean("proved") ? "" : " · " + Text.ADMIN_NOT_PROVED));

        Set<String> held = new HashSet<>();
        JSONArray worn = look.optJSONArray("badges");
        if (worn != null) for (int i = 0; i < worn.length(); i++) {
            JSONObject one = worn.optJSONObject(i);
            if (one != null) held.add(one.optString("id"));
        }
        panel.section(Text.ADMIN_BADGES);
        for (final Badges.Badge badge : Badges.all()) {
            if (badge == null) continue;
            final boolean has = held.contains(badge.id);
            String title = badge.title != null && badge.title.length() > 0 ? badge.title : badge.id;
            panel.option(title, has, false, () -> change(c, target, has ? "take" : "give", "badge", badge.id, block));
        }

        final JSONObject verdict = look.optJSONObject("verdict");
        final Boolean now = verdict == null ? null : verdict.optBoolean("good");
        panel.section(Text.ADMIN_MARK);
        panel.option(Text.VERDICT_GOOD, Boolean.TRUE.equals(now), false, () -> reason(c, target, true, verdict, block));
        panel.option(Text.VERDICT_BAD, Boolean.FALSE.equals(now), false, () -> reason(c, target, false, verdict, block));
        panel.option(Text.ADMIN_NO_MARK, now == null, false, () -> {
            if (now != null) change(c, target, "verdict", null, null, block);
        });

        panel.section(null);
        if (look.optBoolean("gradient")) {
            panel.item("palette", Text.ADMIN_GRADIENT, null, () -> change(c, target, "gradient", null, null, block));
        }
        panel.item("link", Text.ADMIN_COPY_ID, target, () -> {
            android.content.ClipboardManager clip = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clip != null) clip.setPrimaryClip(android.content.ClipData.newPlainText("id", target));
            Screen.say(Text.ADMIN_COPIED);
        });
        panel.quiet(Text.CLOSE, null).show();
    }

    /** A mark needs a reason: asked for, then set. */
    private static void reason(final Context c, final String target, final boolean good, JSONObject verdict, final View block) {
        Panel panel = Panel.with(c, Skin.remembered(c), good ? Text.VERDICT_GOOD : Text.VERDICT_BAD);
        final android.widget.EditText why = panel.field(Text.VERDICT_REASON);
        if (verdict != null) why.setText(verdict.optString("reason", ""));
        panel.primary(Text.ADMIN_CONFIRM, () -> change(c, target, "verdict", "good", good, why.getText().toString(), block));
        panel.quiet(Text.CANCEL, null).show();
    }

    private static void change(Context c, String target, String op, String field, Object value, View block) {
        change(c, target, op, field, value, null, block);
    }

    private static void change(final Context c, final String target, final String op, final String field,
                               final Object value, final String reason, final View block) {
        Net.away("admin " + op, () -> {
            try {
                JSONObject args = new JSONObject().put("target", target);
                if (field != null) args.put(field, value);
                if (reason != null) args.put("reason", reason.trim());
                JSONObject look = AdminDevice.act(op, args);
                Badges.refresh();
                MAIN.post(() -> {
                    sheet(c, target, look, block);
                    if (block != null) {
                        // the badges are read again in the background; look once they are in
                        block.postDelayed(() -> ProfileVerdict.apply(block), 1500);
                        block.postDelayed(() -> ProfileVerdict.apply(block), 4000);
                    }
                });
            } catch (AdminDevice.Refused refused) {
                if ("device".equals(refused.why)) approve(c, () -> change(c, target, op, field, value, reason, block));
                else MAIN.post(() -> say(refused.why));
            } catch (Throwable error) {
                Diary.note("admin " + op + ": " + error);
                MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
            }
        });
    }

    // ---------------------------------------------------------- the phones

    /** The phones that may use the admin, from the settings: any can be struck off. */
    static void devices(final Context c) {
        Net.away("admin devices", () -> {
            try {
                JSONObject said = AdminDevice.act("devices", null);
                MAIN.post(() -> listDevices(c, said.optJSONArray("devices")));
            } catch (AdminDevice.Refused refused) {
                if ("device".equals(refused.why)) approve(c, () -> devices(c));
                else MAIN.post(() -> say(refused.why));
            } catch (Throwable error) {
                Diary.note("admin devices: " + error);
                MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
            }
        });
    }

    private static void listDevices(final Context c, JSONArray list) {
        Panel panel = Panel.with(c, Skin.remembered(c), Text.ADMIN_DEVICES);
        panel.section(null);
        if (list != null) for (int i = 0; i < list.length(); i++) {
            final JSONObject one = list.optJSONObject(i);
            if (one == null) continue;
            String detail = one.optBoolean("this") ? Text.ADMIN_THIS
                    : one.optBoolean("approved") ? one.optString("id") : Text.ADMIN_WAITING;
            final String name = one.optString("name", "?");
            panel.item("fingerprint", name, detail, "close", () -> {
                Panel sure = Panel.with(c, Skin.remembered(c), Text.ADMIN_FORGET + "?").text(name);
                sure.primary(Text.ADMIN_FORGET, () -> Net.away("admin forget", () -> {
                    try {
                        AdminDevice.act("forget", new JSONObject().put("which", one.optString("id")));
                        MAIN.post(() -> devices(c));
                    } catch (Throwable error) {
                        Diary.note("admin forget: " + error);
                        MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
                    }
                }));
                sure.quiet(Text.CANCEL, null).show();
            });
        }
        panel.quiet(Text.CLOSE, null).show();
    }
}
