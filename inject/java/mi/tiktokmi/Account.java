package mi.tiktokmi;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.IAccountUserService;

/**
 * Who is signed in, learned by listening rather than by asking.
 *
 * The id is worth having: a username changes and the id does not, so it is
 * what a profile link is built from and what anyone reporting a problem should
 * be able to copy out.
 *
 * Asking for it directly would mean reaching TikTok's account service, and the
 * only ways in are obfuscated methods on obfuscated classes -- names that will
 * not survive the next release. The service's own interface, though, is a real
 * name, and the app asks it who is signed in constantly. So the calls are
 * rewritten to come through here: the real answer is fetched, remembered, and
 * handed back untouched. Nothing is changed; the mod simply overhears it.
 *
 * Kept in the mod's own settings so the screen has something to show before
 * the app has asked anyone anything.
 */
public final class Account {

    private Account() {}

    public static final String KEY_ID = "account_id";
    public static final String KEY_SEC_ID = "account_sec_id";
    public static final String KEY_USERNAME = "account_username";

    private static volatile String id;
    private static volatile String secId;
    private static volatile String username;

    // ------------------------------------------------ where the calls land

    public static com.ss.android.ugc.aweme.profile.model.User getCurUser(IAccountUserService service) {
        if (service == null) return null;
        com.ss.android.ugc.aweme.profile.model.User user = service.getCurUser();
        if (user != null) {
            rememberUser(user);
        }
        return user;
    }

    public static void rememberUser(com.ss.android.ugc.aweme.profile.model.User user) {
        if (user == null) return;
        try {
            String uid = user.getUid();
            if (uid != null && !uid.isEmpty()) remember(KEY_ID, uid);
            String sec = user.getSecUid();
            if (sec != null && !sec.isEmpty()) remember(KEY_SEC_ID, sec);
            String uName = user.getUniqueId();
            if (uName != null && !uName.isEmpty()) remember(KEY_USERNAME, uName);
        } catch (Throwable ignored) {}
    }

    public static String getCurUserId(IAccountUserService service) {
        if (service == null) return null;
        String answer = service.getCurUserId();
        remember(KEY_ID, answer);
        return answer;
    }

    public static String getCurSecUserId(IAccountUserService service) {
        if (service == null) return null;
        String answer = service.getCurSecUserId();
        remember(KEY_SEC_ID, answer);
        return answer;
    }

    private static void remember(String key, String value) {
        if (value == null || value.length() == 0) return;
        boolean numeric = KEY_ID.equals(key);
        String known = numeric ? id : secId;
        if (value.equals(known)) return;  // the common case: nothing to write
        if (numeric) {
            id = value;
        } else {
            secId = value;
        }
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) prefs.edit().putString(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------- what the screen asks

    /** The numeric id, or null if the app has not asked for it yet. */
    public static String id() {
        String known = id;
        if (known != null) return known;
        return stored(KEY_ID);
    }

    /** The long opaque id -- the one in a profile link. */
    public static String secId() {
        String known = secId;
        if (known != null) return known;
        return stored(KEY_SEC_ID);
    }

    /** The @username handle, or null if unknown. */
    public static String username() {
        String known = username;
        if (known != null) return known;
        return stored(KEY_USERNAME);
    }

    private static String stored(String key) {
        try {
            SharedPreferences prefs = prefs();
            if (prefs == null) return null;
            String value = prefs.getString(key, null);
            return value == null || value.length() == 0 ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs() {
        android.content.Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
    }
}
