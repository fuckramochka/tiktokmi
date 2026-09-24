package mi.tiktokmi;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

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
    public static final String KEY_HIDE_PHOTOS = "hide_photos";
    public static final String KEY_HIDE_STORIES = "hide_stories";
    public static final String KEY_OLED_CLEAN = "oled_clean_mode";

    private static volatile Boolean cached;
    private static volatile Boolean cachedLives;
    private static volatile Boolean cachedPhotos;
    private static volatile Boolean cachedStories;
    private static volatile Boolean cachedOled;

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

    public static boolean isHidePhotosEnabled() {
        Boolean known = cachedPhotos;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY_HIDE_PHOTOS, false);
        } catch (Throwable ignored) {
        }
        cachedPhotos = on;
        return on;
    }

    public static void setHidePhotosEnabled(boolean enabled) {
        cachedPhotos = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_HIDE_PHOTOS, enabled).apply();
    }

    public static boolean isHideStoriesEnabled() {
        Boolean known = cachedStories;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY_HIDE_STORIES, false);
        } catch (Throwable ignored) {
        }
        cachedStories = on;
        return on;
    }

    public static void setHideStoriesEnabled(boolean enabled) {
        cachedStories = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_HIDE_STORIES, enabled).apply();
    }

    public static boolean isOledCleanEnabled() {
        Boolean known = cachedOled;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY_OLED_CLEAN, false);
        } catch (Throwable ignored) {
        }
        cachedOled = on;
        return on;
    }

    public static void setOledCleanEnabled(boolean enabled) {
        cachedOled = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_OLED_CLEAN, enabled).apply();
    }

    private static final int OLED_TAG = 0x4D61726A;
    private static final int OLED_BUDGET = 2000;
    private static int oledSeen;

    /**
     * Protect AMOLED/OLED screens from burn-in by making static feed HUD elements
     * semi-transparent while leaving interaction completely intact.
     */
    public static void applyOled(View root) {
        if (root == null) return;
        try {
            oledSeen = 0;
            boolean enabled = isOledCleanEnabled();
            walkOled(root, enabled, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void walkOled(View view, boolean enabled, int depth) {
        if (view == null || depth > 30 || ++oledSeen > OLED_BUDGET) return;
        try {
            if (enabled) {
                int id = view.getId();
                if (id != View.NO_ID) {
                    String name = view.getResources().getResourceEntryName(id);
                    if (name != null) {
                        String lower = name.toLowerCase(java.util.Locale.US);
                        if (lower.contains("interact") || lower.contains("action_bar")
                                || lower.contains("right_layout") || lower.contains("desc")
                                || lower.contains("author") || lower.contains("music_cover")
                                || lower.contains("feed_share") || lower.contains("feed_comment")
                                || lower.contains("feed_like")) {
                            view.setAlpha(0.6f);
                            view.setTag(OLED_TAG, Boolean.TRUE);
                        }
                    }
                }
            } else if (Boolean.TRUE.equals(view.getTag(OLED_TAG))) {
                view.setAlpha(1.0f);
                view.setTag(OLED_TAG, null);
            }
        } catch (Throwable ignored) {
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                walkOled(group.getChildAt(i), enabled, depth + 1);
            }
        }
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
        boolean dropPhotos = isHidePhotosEnabled();
        boolean dropStories = isHideStoriesEnabled();
        if (!dropAds && !dropLives && !dropPhotos && !dropStories) return items;

        try {
            int toDrop = 0;
            for (Object item : items) {
                if (item instanceof Aweme) {
                    Aweme aweme = (Aweme) item;
                    if (dropAds && aweme.isAd()) {
                        toDrop++;
                    } else if (dropLives && isLiveAweme(aweme)) {
                        toDrop++;
                    } else if (dropPhotos && aweme.getAwemeType() == 68) {
                        toDrop++;
                    } else if (dropStories && aweme.getAwemeType() == 40) {
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
                    if (dropPhotos && aweme.getAwemeType() == 68) continue;
                    if (dropStories && aweme.getAwemeType() == 40) continue;
                }
                kept.add(item);
            }
            dropped += toDrop;
            Diary.note("feed: " + toDrop + " item(s) dropped, " + dropped + " so far");
            return kept;
        } catch (Throwable error) {
            Diary.note("feed: leaving the page alone, " + error);
            return items;
        }
    }
}
