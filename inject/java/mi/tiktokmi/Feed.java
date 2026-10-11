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
    public static final String KEY_OLED_SHOW_BUTTON = "oled_show_button";
    public static final String KEY_OLED_AUTO_START = "oled_auto_start";
    public static final String KEY_OLED_TRANSPARENCY = "oled_transparency";
    public static final String KEY_KEYWORDS = "feed_keywords";
    public static final String KEY_MAX_CAPTION = "feed_caption_max";
    public static final String KEY_REGION_MATCH = "feed_region_match";

    private static volatile Boolean cached;
    private static volatile Boolean cachedLives;
    private static volatile Boolean cachedPhotos;
    private static volatile Boolean cachedStories;
    private static volatile Boolean cachedOled;
    private static volatile Boolean cachedOledButton;
    private static volatile Boolean cachedOledAuto;
    private static volatile Integer cachedOledTransparency;

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
            if (!on && isOledAutoStartEnabled()) {
                on = true;
            }
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

    public static boolean isOledButtonEnabled() {
        Boolean known = cachedOledButton;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true; // default enabled
        boolean on = true;
        try {
            on = prefs.getBoolean(KEY_OLED_SHOW_BUTTON, true);
        } catch (Throwable ignored) {
        }
        cachedOledButton = on;
        return on;
    }

    public static void setOledButtonEnabled(boolean enabled) {
        cachedOledButton = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_OLED_SHOW_BUTTON, enabled).apply();
    }

    public static boolean isOledAutoStartEnabled() {
        Boolean known = cachedOledAuto;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY_OLED_AUTO_START, false);
        } catch (Throwable ignored) {
        }
        cachedOledAuto = on;
        return on;
    }

    public static void setOledAutoStartEnabled(boolean enabled) {
        cachedOledAuto = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_OLED_AUTO_START, enabled).apply();
    }

    public static int getOledTransparency() {
        Integer known = cachedOledTransparency;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return 80;
        int val = 80;
        try {
            val = prefs.getInt(KEY_OLED_TRANSPARENCY, 80);
        } catch (Throwable ignored) {
        }
        cachedOledTransparency = val;
        return val;
    }

    public static void setOledTransparency(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        cachedOledTransparency = percent;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_OLED_TRANSPARENCY, percent).apply();
    }

    public static float targetOledAlpha() {
        int trans = getOledTransparency();
        return Math.max(0.0f, Math.min(1.0f, (100 - trans) / 100.0f));
    }

    public static void toggleOledClean(android.app.Activity activity) {
        boolean next = !isOledCleanEnabled();
        setOledCleanEnabled(next);
        if (activity != null) {
            applyOled(activity.getWindow().getDecorView());
        }
    }

    // ------------------------------------------------- the word filter

    private static volatile String cachedKeywords;
    private static volatile boolean keywordsLoaded;

    /** The raw blocklist, comma- or newline-separated. Empty means off. */
    public static String keywordsRaw() {
        String known = cachedKeywords;
        if (keywordsLoaded) return known == null ? "" : known;
        String raw = "";
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) raw = prefs.getString(KEY_KEYWORDS, "");
        } catch (Throwable ignored) {
        }
        if (raw == null) raw = "";
        cachedKeywords = raw;
        keywordsLoaded = true;
        return raw;
    }

    public static void setKeywords(String raw) {
        if (raw == null) raw = "";
        cachedKeywords = raw;
        keywordsLoaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_KEYWORDS, raw).apply();
    }

    // ------------------------------------------------- the long posts

    /** Caption lengths to cycle through, in characters. Zero is off. */
    public static final int[] CAPTION_STEPS = {0, 150, 280, 500};

    private static volatile Integer cachedMax;

    public static int maxCaption() {
        Integer known = cachedMax;
        if (known != null) return known.intValue();
        int max = 0;
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) max = prefs.getInt(KEY_MAX_CAPTION, 0);
        } catch (Throwable ignored) {
        }
        cachedMax = Integer.valueOf(max);
        return max;
    }

    public static void cycleMaxCaption() {
        int current = maxCaption();
        int next = CAPTION_STEPS[0];
        for (int i = 0; i < CAPTION_STEPS.length; i++) {
            if (CAPTION_STEPS[i] == current) {
                next = CAPTION_STEPS[(i + 1) % CAPTION_STEPS.length];
                break;
            }
        }
        cachedMax = Integer.valueOf(next);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_MAX_CAPTION, next).apply();
    }

    // ------------------------------------------------- the region match

    private static volatile Boolean cachedRegionMatch;

    public static boolean isRegionMatchEnabled() {
        Boolean known = cachedRegionMatch;
        if (known != null) return known.booleanValue();
        boolean on = false;
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) on = prefs.getBoolean(KEY_REGION_MATCH, false);
        } catch (Throwable ignored) {
        }
        cachedRegionMatch = Boolean.valueOf(on);
        return on;
    }

    public static void setRegionMatchEnabled(boolean enabled) {
        cachedRegionMatch = Boolean.valueOf(enabled);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_REGION_MATCH, enabled).apply();
    }

    /**
     * Whether this build's posts carry their region to compare.
     *
     * The region the device claims is the mod's oldest trick, but a post's
     * own region is a second model getter that no build has promised to keep.
     * It is probed once by name: found, the switch appears and works; not
     * found, there is no switch rather than a switch that does nothing.
     */
    private static volatile Boolean regionProbe;
    private static volatile java.lang.reflect.Method regionMethod;

    public static boolean isRegionMatchAvailable() {
        Boolean known = regionProbe;
        if (known != null) return known.booleanValue();
        boolean found = false;
        try {
            Class<?> aweme = Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme");
            java.lang.reflect.Method getter = aweme.getMethod("getRegion");
            if (getter.getReturnType() == String.class) {
                regionMethod = getter;
                found = true;
            }
        } catch (Throwable ignored) {
            regionMethod = null;
        }
        regionProbe = Boolean.valueOf(found);
        Diary.note("feed: post regions " + (found ? "readable" : "not on this build"));
        return found;
    }

    private static String regionOf(Aweme aweme) {
        try {
            if (!isRegionMatchAvailable() || regionMethod == null) return null;
            Object value = regionMethod.invoke(aweme);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------- reading a caption

    /**
     * A caption, or null when there is nothing to judge by.
     *
     * `getDesc` is the model's own getter, but it is one the build never had
     * to anchor on, so a release that renamed it must not take the ad filter
     * down with it: the absence is remembered after the first miss, and from
     * then on there is simply no caption to match.
     */
    private static volatile Boolean descKnown;

    private static String captionOf(Aweme aweme) {
        if (Boolean.FALSE.equals(descKnown)) return null;
        try {
            String desc = aweme.getDesc();
            if (descKnown == null) descKnown = Boolean.TRUE;
            return desc;
        } catch (LinkageError error) {
            descKnown = Boolean.FALSE;
            Diary.note("feed: captions not readable on this build");
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile boolean sInFilter = false;

    /**
     * Where rewritten calls to Aweme.getDesc() land.
     * Records watched video in local history (which dispatches to Continuation, Localizer, and OfflineActions).
     */
    public static String getDesc(Aweme aweme) {
        if (aweme == null) return null;
        if (!sInFilter) {
            try {
                WatchHistory.onVideoWatched(aweme);
            } catch (Throwable ignored) {}
        }
        return aweme.getDesc();
    }

    private static final int OLED_TAG = 0x4F4C4544; // "OLED", must differ from every other tag
    private static final int OLED_ALPHA_TAG = 0x4F4C4161; // "OLa", original alpha
    private static final int OLED_BUDGET = 2000;
    private static int oledSeen;
    private static final android.util.SparseBooleanArray sOledIdCache = new android.util.SparseBooleanArray();

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
                    boolean isTarget;
                    int cacheIdx = sOledIdCache.indexOfKey(id);
                    if (cacheIdx >= 0) {
                        isTarget = sOledIdCache.valueAt(cacheIdx);
                    } else {
                        String name = view.getResources().getResourceEntryName(id);
                        if (name != null) {
                            String lower = name.toLowerCase(java.util.Locale.US);
                            isTarget = lower.contains("interact") || lower.contains("action_bar")
                                    || lower.contains("right_layout") || lower.contains("desc")
                                    || lower.contains("author") || lower.contains("music_cover")
                                    || lower.contains("feed_share") || lower.contains("feed_comment")
                                    || lower.contains("feed_like");
                        } else {
                            isTarget = false;
                        }
                        sOledIdCache.put(id, isTarget);
                    }
                    if (isTarget) {
                        if (view.getTag(OLED_ALPHA_TAG) == null) {
                            try {
                                view.setTag(OLED_ALPHA_TAG, Float.valueOf(view.getAlpha()));
                            } catch (Throwable skip) {
                            }
                        }
                        view.setAlpha(targetOledAlpha());
                        view.setTag(OLED_TAG, Boolean.TRUE);
                    }
                }
            } else if (Boolean.TRUE.equals(view.getTag(OLED_TAG))) {
                try {
                    Object saved = view.getTag(OLED_ALPHA_TAG);
                    float back = (saved instanceof Float) ? ((Float) saved).floatValue() : 1.0f;
                    view.setAlpha(back);
                } catch (Throwable restore) {
                    try {
                        view.setAlpha(1.0f);
                    } catch (Throwable last) {
                    }
                }
                view.setTag(OLED_TAG, null);
                view.setTag(OLED_ALPHA_TAG, null);
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

    private static final int CLEAN_BTN_TAG = 0x4F4C4542; // "OLEB"

    public static void attachCleanButton(final android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!isOledButtonEnabled()) {
            removeCleanButton(activity);
            return;
        }
        try {
            android.view.ViewGroup decor = (android.view.ViewGroup) activity.getWindow().getDecorView();
            if (decor == null || decor.findViewById(CLEAN_BTN_TAG) != null) return;

            CleanButton btn = new CleanButton(activity);
            btn.setId(CLEAN_BTN_TAG);
            float d = activity.getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            Math.round(46 * d), Math.round(46 * d));
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            lp.leftMargin = Math.round(16 * d);
            lp.topMargin = Math.round(54 * d);
            decor.addView(btn, lp);
        } catch (Throwable ignored) {
        }
    }

    public static void removeCleanButton(android.app.Activity activity) {
        if (activity == null) return;
        try {
            android.view.ViewGroup decor = (android.view.ViewGroup) activity.getWindow().getDecorView();
            if (decor != null) {
                View existing = decor.findViewById(CLEAN_BTN_TAG);
                if (existing != null) decor.removeView(existing);
            }
        } catch (Throwable ignored) {
        }
    }

    static class CleanButton extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CleanButton(final android.content.Context context) {
            super(context);
            bgPaint.setColor(0x99000000);
            bgPaint.setStyle(Paint.Style.FILL);

            iconPaint.setColor(0xFFFFFFFF);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 2.2f);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean next = !isOledCleanEnabled();
                    setOledCleanEnabled(next);
                    if (context instanceof android.app.Activity) {
                        applyOled(((android.app.Activity) context).getWindow().getDecorView());
                    }
                    invalidate();
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = Math.min(w, h) / 2f;
            canvas.drawCircle(w / 2f, h / 2f, r, bgPaint);

            // Draw view-finder focus corners [  ]
            float d = getContext().getResources().getDisplayMetrics().density;
            float pad = 13 * d;
            float corner = 6 * d;
            float l = pad, t = pad, right = w - pad, b = h - pad;

            // Top-left corner
            canvas.drawLine(l, t + corner, l, t, iconPaint);
            canvas.drawLine(l, t, l + corner, t, iconPaint);

            // Top-right corner
            canvas.drawLine(right - corner, t, right, t, iconPaint);
            canvas.drawLine(right, t, right, t + corner, iconPaint);

            // Bottom-left corner
            canvas.drawLine(l, b - corner, l, b, iconPaint);
            canvas.drawLine(l, b, l + corner, b, iconPaint);

            // Bottom-right corner
            canvas.drawLine(right - corner, b, right, b, iconPaint);
            canvas.drawLine(right, b - corner, right, b, iconPaint);
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
        String[] words = words();
        int captionMax = maxCaption();
        String mine = null;
        if (isRegionMatchEnabled() && isRegionMatchAvailable()) {
            try {
                if (Margy.active()) mine = Margy.current()[Margy.ISO];
            } catch (Throwable ignored) {
            }
        }
        if (!dropAds && !dropLives && !dropPhotos && !dropStories
                && words.length == 0 && captionMax <= 0 && mine == null) return items;

        sInFilter = true;
        try {
            int n = items.size();
            boolean[] out = new boolean[n];
            int toDrop = 0;
            int ads = 0, lives = 0, media = 0, wordsHit = 0, regionHit = 0;
            for (int i = 0; i < n; i++) {
                Object item = items.get(i);
                boolean dropIt = (item instanceof Aweme)
                        && drop((Aweme) item, dropAds, dropLives, dropPhotos,
                                dropStories, words, captionMax, mine);
                out[i] = dropIt;
                if (!dropIt) continue;
                toDrop++;
                // best-effort reason counters for the diary, never blocking
                try {
                    Aweme aweme = (Aweme) item;
                    if (dropAds && aweme.isAd()) ads++;
                    else if (dropLives && isLiveAweme(aweme)) lives++;
                    else if ((dropPhotos || dropStories)) media++;
                    else if (mine != null && regionOf(aweme) != null) regionHit++;
                    else wordsHit++;
                } catch (Throwable ignored) {
                }
            }
            if (toDrop == 0) return items;

            List kept = new ArrayList(n - toDrop);
            for (int i = 0; i < n; i++) {
                if (!out[i]) kept.add(items.get(i));
            }
            dropped += toDrop;
            Diary.note("feed: " + toDrop + " item(s) dropped, " + dropped + " so far"
                    + " (ads " + ads + ", lives " + lives + ", media " + media
                    + ", words/long " + wordsHit + ", region " + regionHit + ")");
            return kept;
        } catch (Throwable error) {
            Diary.note("feed: leaving the page alone, " + error);
            return items;
        } finally {
            sInFilter = false;
        }
    }

    /** The blocklist, lowercased and split. Parsed per page; it is short. */
    private static String[] words() {
        try {
            String raw = keywordsRaw();
            if (raw == null || raw.trim().length() == 0) return new String[0];
            String[] parts = raw.toLowerCase(java.util.Locale.US).split("[,\n]");
            java.util.ArrayList<String> out = new java.util.ArrayList<String>();
            for (String part : parts) {
                String word = part.trim();
                if (word.length() > 0) out.add(word);
            }
            return out.toArray(new String[out.size()]);
        } catch (Throwable ignored) {
            return new String[0];
        }
    }

    /**
     * Whether this post stays out of the page.
     *
     * A post that cannot be judged -- no caption to match, no region to
     * compare -- is kept. A filter that cannot see is a filter that does
     * nothing, never one that empties the feed.
     */
    private static boolean drop(Aweme aweme, boolean dropAds, boolean dropLives,
            boolean dropPhotos, boolean dropStories, String[] words,
            int captionMax, String mine) {
        if (dropAds && aweme.isAd()) return true;
        if (dropLives && isLiveAweme(aweme)) return true;
        try {
            if (dropPhotos && aweme.getAwemeType() == 68) return true;
            if (dropStories && aweme.getAwemeType() == 40) return true;
        } catch (Throwable ignored) {
        }
        if (words.length > 0 || captionMax > 0) {
            String caption = captionOf(aweme);
            if (caption != null) {
                if (captionMax > 0 && caption.length() > captionMax) return true;
                if (words.length > 0) {
                    String lower = caption.toLowerCase(java.util.Locale.US);
                    for (String word : words) {
                        if (lower.contains(word)) return true;
                    }
                }
            }
        }
        if (mine != null) {
            String region = regionOf(aweme);
            if (region != null && region.length() > 0
                    && !region.equalsIgnoreCase(mine)) return true;
        }
        return false;
    }
}
