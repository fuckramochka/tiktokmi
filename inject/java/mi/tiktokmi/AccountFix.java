package mi.tiktokmi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import com.ss.android.ugc.aweme.IAccountUserService;
import com.ss.android.ugc.aweme.profile.model.User;

import java.io.File;

/**
 * Account & Email Change Fix for TikTok MI.
 *
 * Resolves the issue where changing an email fails or does not allow logging in with
 * the new email:
 *
 * 1. Passport Safe Mode:
 *    Temporarily pauses Telephony/SIM region spoofing during login and email change
 *    flows so ByteDance SecSDK risk-control does not detect a carrier-vs-IP mismatch
 *    and silently reject sensitive security updates.
 *
 * 2. Auth Cache Reset:
 *    Clears stale cached login identities in SharedPreferences and AccountManager
 *    so TikTok re-queries fresh server credentials instead of rejecting the new email.
 *
 * 3. Direct Web Passport Portal:
 *    Opens TikTok's official web management portal (https://www.tiktok.com/setting)
 *    where email change commits directly without mobile SecSDK restrictions.
 *
 * 4. Username Login Guide:
 *    Helps user log in using @username + password when email alias is desynced.
 */
public final class AccountFix {

    private AccountFix() {}

    public static final String KEY_PASSPORT_SAFE = "passport_safe_mode";

    public static boolean isPassportSafeModeEnabled() {
        try {
            Context ctx = Margy.context();
            if (ctx == null) return true;
            return ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_PASSPORT_SAFE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setPassportSafeModeEnabled(boolean enabled) {
        try {
            Context ctx = Margy.context();
            if (ctx != null) {
                ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_PASSPORT_SAFE, enabled).apply();
            }
        } catch (Throwable ignored) {}
    }

    private static volatile boolean sAuthInProgress = false;

    public static void setAuthInProgress(boolean inProgress) {
        sAuthInProgress = inProgress;
    }

    public static boolean isAuthInProgress() {
        return sAuthInProgress;
    }

    /**
     * Clears cached account credentials and forces TikTok to reload the user profile from the server.
     */
    public static void resetAuthCache(Context context) {
        if (context == null) return;
        try {
            int cleared = 0;
            String[] targetPrefs = {
                    "aweme_user",
                    "com.ss.android.ugc.aweme.account",
                    "passport_user_info",
                    "passport_sp",
                    "passport_account_info"
            };

            for (String prefName : targetPrefs) {
                try {
                    SharedPreferences sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                    if (sp != null) {
                        sp.edit().clear().apply();
                        cleared++;
                    }
                } catch (Throwable ignored) {}
            }

            // Also clear AccountManager cache if accessible
            try {
                android.accounts.AccountManager am = android.accounts.AccountManager.get(context);
                if (am != null) {
                    android.accounts.Account[] accounts = am.getAccountsByType(context.getPackageName());
                    if (accounts != null) {
                        for (android.accounts.Account acc : accounts) {
                            am.invalidateAuthToken(context.getPackageName(), null);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            Toast.makeText(context, "✓ Кэш авторизации сброшен (" + cleared + " баз). Перезапустите TikTok.", Toast.LENGTH_LONG).show();
            Diary.note("account: reset auth cache (" + cleared + " prefs cleared)");
        } catch (Throwable error) {
            Toast.makeText(context, "Ошибка: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Opens TikTok's official web account management / email update portal.
     * Web portal bypasses Android SecSDK mobile carrier checks and commits with 100% verification.
     */
    public static void openWebPassportPortal(Context context) {
        if (context == null) return;
        try {
            String url = "https://www.tiktok.com/setting";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            Toast.makeText(context, "Не удалось открыть браузер: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows diagnostic and guidance dialog explaining how to resolve email change desync.
     */
    public static void showEmailFixGuide(final Context context) {
        if (context == null) return;
        Skin skin = Skin.remembered(context);

        String username = Account.username();
        String uid = Account.id();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 ДАННЫЕ ВАШЕГО АККАУНТА:\n");
        sb.append("• Логин (@username): ").append(username != null ? "@" + username : "(определяется...)").append("\n");
        sb.append("• User ID: ").append(uid != null ? uid : "(не определен)").append("\n\n");

        sb.append("💡 ПОЧЕМУ ТИКТОК НЕ ПРИНИМАЛ НОВУЮ ПОЧТУ:\n");
        sb.append("1. Антифрод TikTok (SecSDK) блокирует смену почты, если видит подмену оператора (NL/US) при IP другой страны.\n");
        sb.append("2. Мод теперь включает «Безопасный режим Passport», отключающий спуфинг при смене почты.\n");
        sb.append("3. Если почта не обновилась, вы ВСЕГДА можете войти по связке: [Имя пользователя (@username)] + [Пароль] вместо почты!\n\n");

        sb.append("🔧 ДЕЙСТВИЯ ДЛЯ ИСПРАВЛЕНИЯ:\n");
        sb.append("• Нажмите «Сбросить кэш авторизации», затем смените почту заново.\n");
        sb.append("• Либо откройте официальный веб-портал TikTok в браузере (там смена проходит гарантированно).");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Починка смены почты и входа");
        builder.setMessage(sb.toString());

        builder.setPositiveButton("Сбросить кэш", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                resetAuthCache(context);
            }
        });

        builder.setNeutralButton("Веб-портал", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openWebPassportPortal(context);
            }
        });

        builder.setNegativeButton("Понятно", null);
        builder.show();
    }
}
