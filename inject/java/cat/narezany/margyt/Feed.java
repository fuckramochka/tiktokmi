package cat.narezany.margyt;

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

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;  // too early to know; do not cache it
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY, false);
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
     * looking for advertisements.
     */
    public static List getItems(FeedItemList page) {
        if (page == null) return null;
        List items = page.getItems();
        if (items == null || !isEnabled()) return items;
        try {
            int ads = 0;
            for (Object item : items) {
                if (item instanceof Aweme && ((Aweme) item).isAd()) ads++;
            }
            if (ads == 0) return items;

            List kept = new ArrayList(items.size() - ads);
            for (Object item : items) {
                if (item instanceof Aweme && ((Aweme) item).isAd()) continue;
                kept.add(item);
            }
            dropped += ads;
            Diary.note("feed: " + ads + " ad(s) dropped, " + dropped + " so far");
            return kept;
        } catch (Throwable error) {
            Diary.note("feed: leaving the page alone, " + error);
            return items;
        }
    }
}
