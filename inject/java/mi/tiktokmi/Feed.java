package mi.tiktokmi;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;

import java.util.ArrayList;
import java.util.List;

/**
 * The advertisements, taken out of the page before anything sees it.
 *
 * TikTok's feed arrives as a `FeedItemList`, and every post in it says for
 * itself whether it is an advertisement -- `Aweme.isAd()`, a real name in a
 * real model. So the ads are dropped where the page is read rather than hidden
 * on each screen that might draw one: `FeedItemList.getItems()` is rewritten
 * to come through here, and what comes back is the same list without them.
 *
 * Answering `isAd()` with false instead would be the wrong lever. The post
 * would still be in the feed; it would simply stop being labelled as an
 * advertisement, which is worse than leaving it alone.
 *
 * Whatever cannot be understood is passed through untouched. A feed with no
 * ads is a preference; a feed that does not load is a broken app.
 */
public final class Feed {

    private Feed() {}

    public static final String KEY = "hide_ads";
    public static final String KEY_HIDE_LIVES = "hide_lives";

    private static volatile Boolean cached;
    private static volatile Boolean cachedLives;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true;  // default true on install
        boolean on = true;
        try {
            on = prefs.getBoolean(KEY, true);
        } catch (Throwable ignored) {
        }
        cached = on;
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
    }

    public static boolean isHideLivesEnabled() {
        Boolean known = cachedLives;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true;  // default true on install
        boolean on = true;
        try {
            on = prefs.getBoolean(KEY_HIDE_LIVES, true);
        } catch (Throwable ignored) {
        }
        cachedLives = on;
        return on;
    }

    public static void setHideLivesEnabled(boolean enabled) {
        cachedLives = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_HIDE_LIVES, enabled).apply();
    }

    public static boolean isLiveAweme(Aweme aweme) {
        if (aweme == null) return false;
        try {
            if (aweme.isLive()) return true;
        } catch (Throwable ignored) {
        }
        try {
            int type = aweme.getAwemeType();
            if (type == 101 || type == 102) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static SharedPreferences prefs() {
        try {
            android.content.Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** How many have been dropped since the process started, for the diary. */
    private static int dropped;

    // ------------------------------------------------ where the call lands

    /**
     * Every `FeedItemList.getItems()` in TikTok's bytecode comes here first.
     *
     * The list is only rebuilt when there is something to leave out -- the
     * ordinary page comes back as the very object the app asked for, which
     * keeps this out of the way of everything that reads a feed and is not
     * looking for advertisements or live streams.
     */
    public static List getItems(FeedItemList page) {
        if (page == null) return null;
        List items = page.getItems();
        if (items == null) return null;

        boolean dropAds = isEnabled();
        boolean dropLives = isHideLivesEnabled();
        if (!dropAds && !dropLives) return items;

        try {
            int toDrop = 0;
            for (Object item : items) {
                if (item instanceof Aweme) {
                    Aweme aweme = (Aweme) item;
                    if (dropAds && aweme.isAd()) {
                        toDrop++;
                    } else if (dropLives && isLiveAweme(aweme)) {
                        toDrop++;
                    }
                }
            }
            if (toDrop == 0) return items;

            List kept = new ArrayList(items.size() - toDrop);
            for (Object item : items) {
                if (item instanceof Aweme) {
                    Aweme aweme = (Aweme) item;
                    if (dropAds && aweme.isAd()) continue;
                    if (dropLives && isLiveAweme(aweme)) continue;
                }
                kept.add(item);
            }
            dropped += toDrop;
            Diary.note("feed: " + toDrop + " item(s) dropped (ads/lives), " + dropped + " so far");
            return kept;
        } catch (Throwable error) {
            Diary.note("feed: leaving the page alone, " + error);
            return items;
        }
    }
}
