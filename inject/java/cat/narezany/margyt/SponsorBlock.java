package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Adverts read out by a video's author, skipped like SponsorBlock skips them on YouTube.
 *
 * People with a proved account mark the part of a video where the advert is; the server takes a
 * part for everyone once several of them have marked about the same stretch (the owner's mark at
 * once). While a video plays, its parts are asked for, drawn red on the bar, and jumped over when
 * the video reaches them, with a moment to go back.
 */
final class SponsorBlock {

    private SponsorBlock() {}

    static final String KEY_SKIP = "sb_skip", KEY_MARKS = "sb_marks", KEY_ON = "sb_on", KEY_MOBILE = "sb_mobile";

    /** The whole of it: on unless switched off. */
    static boolean enabled() {
        return flag(KEY_ON, true);
    }

    /** Listening on mobile data too, which costs data: off unless switched on. */
    static boolean mobileOn() {
        return flag(KEY_MOBILE, false);
    }

    private static boolean flag(String key, boolean otherwise) {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(key, otherwise);
        } catch (Throwable ignored) {
            return otherwise;
        }
    }

    static boolean skipOn() {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(KEY_SKIP, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean marksOn() {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(KEY_MARKS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void set(String key, boolean on) {
        try {
            AccountAppearance.prefs(Margy.context()).edit().putBoolean(key, on).apply();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ the parts of videos

    /** The parts of each video asked about: start, stop and the part's id, in milliseconds. */
    private static final Map<String, long[][]> known = new LinkedHashMap<String, long[][]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, long[][]> eldest) {
            return size() > 400;
        }
    };
    private static final Set<String> asking = new HashSet<String>();
    /** Parts the person chose to watch after all. */
    private static final Set<Long> allowed = new HashSet<Long>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String playing = "";
    private static boolean ticking;

    static synchronized long[][] partsOf(String aid) {
        return aid == null ? null : known.get(aid);
    }

    private static synchronized void keep(String aid, long[][] parts) {
        known.put(aid, parts);
        if (parts.length > 0) wake();
    }

    /** Parts came for a video: the watcher, asleep while there was nothing to skip, looks again. */
    private static void wake() {
        main.post(() -> {
            if (!ticking && enabled()) {
                ticking = true;
                main.post(tick);
            }
        });
    }

    /** A video began to play. */
    static void playing(String aid) {
        if (aid == null || aid.length() == 0 || !enabled()) return;
        playing = aid;
        ask(aid, false);
        listen(aid);
        if (!ticking) {
            ticking = true;
            main.post(tick);
        }
    }

    private static void ask(final String aid, boolean again) {
        synchronized (SponsorBlock.class) {
            if ((!again && known.containsKey(aid)) || !asking.add(aid)) return;
        }
        Net.away("sponsor parts", new Runnable() {
            @Override public void run() {
                try {
                    String said = Net.text(Badges.SERVER + "/sb?ids=" + aid);
                    if (said == null) return;
                    JSONArray list = new JSONObject(said).optJSONObject("segments") == null ? null
                            : new JSONObject(said).getJSONObject("segments").optJSONArray(aid);
                    keep(aid, parse(list));
                } catch (Throwable error) {
                    Diary.note("sponsor parts: " + error);
                } finally {
                    synchronized (SponsorBlock.class) {
                        asking.remove(aid);
                    }
                }
            }
        });
    }

    private static long[][] parse(JSONArray list) throws Exception {
        if (list == null) return new long[0][];
        long[][] out = new long[list.length()][];
        for (int i = 0; i < list.length(); i++) {
            JSONArray one = list.getJSONArray(i);
            out[i] = new long[] {one.getLong(0), one.getLong(1), one.optLong(2)};
        }
        return out;
    }

    // ------------------------------------------------------------ skipping

    private static long[] lastSkipped;

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            long next = 1000L;
            try {
                next = look();
            } catch (Throwable ignored) {
            }
            // nothing to skip in the video playing, or the app put away: stop until a video plays again
            if (next < 0) {
                ticking = false;
                return;
            }
            main.postDelayed(this, next);
        }
    };

    /** Looks where the video is; returns how soon to look again. */
    private static long look() {
        if (!enabled() || !skipOn() || Watch.here() == null) return -1;
        Object manager = PlayerTools.manager();
        String aid = PlayerTools.aid();
        long[][] parts = partsOf(aid);
        // still being asked for, or heard: look again soon; none at all: sleep
        if (parts == null) return 600L;
        if (manager == null || parts.length == 0) return -1;
        long position = PlayerTools.position(manager), length = PlayerTools.duration(manager);
        if (position < 0 || length <= 0) return 300L;
        for (long[] part : parts) {
            if (allowed.contains(part[2])) continue;
            if (position >= part[0] && position < part[1] - 400) {
                long to = Math.min(length - 1, part[1]);
                PlayerTools.seekTo(manager, to, length);
                lastSkipped = part;
                undo(aid, part);
                return 600L;
            }
        }
        return 150L;
    }

    /** "Реклама пропущена · Вернуть", for a few seconds over the video. */
    private static void undo(final String aid, final long[] part) {
        Activity activity = Watch.here();
        if (activity == null) return;
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View old = decor.findViewWithTag(TAG_UNDO);
        if (old != null) decor.removeView(old);
        final TextView pill = new TextView(activity);
        pill.setTag(TAG_UNDO);
        float unit = activity.getResources().getDisplayMetrics().density;
        pill.setText(Local.of("Реклама пропущена") + "  ·  " + Local.of("Вернуть"));
        pill.setTextColor(0xFFFFFFFF);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setPadding(Math.round(18 * unit), Math.round(10 * unit), Math.round(18 * unit), Math.round(10 * unit));
        GradientDrawable ground = new GradientDrawable();
        ground.setColor(0xE6202020);
        ground.setCornerRadius(24 * unit);
        pill.setBackground(ground);
        Fonts.apply(pill);
        pill.setOnClickListener(v -> {
            allowed.add(part[2]);
            Object manager = PlayerTools.manager();
            if (manager != null && aid.equals(PlayerTools.aid())) {
                PlayerTools.seekTo(manager, part[0], PlayerTools.duration(manager));
            }
            decor.removeView(pill);
        });
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        place.bottomMargin = Math.round(150 * unit);
        decor.addView(pill, place);
        pill.setAlpha(0f);
        pill.animate().alpha(1f).setDuration(180).start();
        pill.postDelayed(() -> pill.animate().alpha(0f).setDuration(240).withEndAction(() -> decor.removeView(pill)).start(), 4000);
    }

    private static final String TAG_UNDO = "margyt-sponsor-undo";

    // ------------------------------------------------------------ red on the bar

    private static final int TAG_MARKED = 0x7e0c0450;
    private static long lookedForBars;

    static void attach(Activity activity) {
        if (Version.LITE || activity == null || !activity.getClass().getName().equals("com.ss.android.ugc.aweme.main.MainActivity")) return;
        final View root = activity.getWindow().getDecorView();
        if (root.getTag(TAG_MARKED) != null) return;
        root.setTag(TAG_MARKED, Boolean.TRUE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lookedForBars < 400L) return;
            lookedForBars = now;
            try {
                bars(root, 0);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void bars(View view, int depth) {
        if (view == null || depth > 40) return;
        if (view instanceof SeekBar) {
            if (view.getTag(TAG_MARKED) == null) {
                view.setTag(TAG_MARKED, Boolean.TRUE);
                view.getOverlay().add(new Red((SeekBar) view));
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) bars(group.getChildAt(i), depth + 1);
        }
    }

    /** The parts of the video playing, red on its bar. */
    private static final class Red extends Drawable {
        private final WeakReference<SeekBar> bar;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Red(SeekBar bar) {
            this.bar = new WeakReference<SeekBar>(bar);
            setBounds(0, 0, 20000, 20000);
            paint.setColor(0xFFFF3B3B);
        }

        @Override public void draw(Canvas canvas) {
            SeekBar seek = bar.get();
            if (seek == null || !enabled() || !marksOn() || !seek.isShown()) return;
            Object manager = PlayerTools.manager();
            long[][] parts = partsOf(PlayerTools.aid());
            if (manager == null || parts == null || parts.length == 0) return;
            long length = PlayerTools.duration(manager);
            if (length <= 0) return;
            float left = seek.getPaddingLeft(), right = seek.getWidth() - seek.getPaddingRight();
            float middle = seek.getHeight() / 2f, half = seek.getResources().getDisplayMetrics().density * 1.6f;
            for (long[] part : parts) {
                float a = left + (right - left) * Math.min(1f, part[0] / (float) length);
                float b = left + (right - left) * Math.min(1f, part[1] / (float) length);
                box.set(a, middle - half, Math.max(a + half, b), middle + half);
                canvas.drawRoundRect(box, half, half, paint);
            }
        }

        @Override public void setAlpha(int alpha) {}

        @Override public void setColorFilter(ColorFilter filter) {}

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------ hearing adverts in the sound

    static final String KEY_SOUND = "sb_sound";

    static boolean soundOn() {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(KEY_SOUND, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static volatile Hear.Index heard = new Hear.Index();
    private static long printsUntil, printsAsked;
    private static final java.util.concurrent.ExecutorService listener = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final Set<String> listened = new HashSet<String>();
    private static boolean printsLoaded;

    private static java.io.File printsFile() {
        return new java.io.File(Margy.context().getFilesDir(), "margyt-sb-prints.json");
    }

    /** The prints of adverts the server has: kept on the phone, and the new ones asked for now and then. */
    private static void prints() {
        long now = android.os.SystemClock.uptimeMillis();
        if (printsLoaded && now - printsAsked < 30 * 60 * 1000L) return;
        printsAsked = now;
        listener.execute(() -> {
            try {
                JSONObject all = new JSONObject();
                java.io.File file = printsFile();
                if (file.exists()) all = new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8"));
                printsUntil = all.optLong("until");
                String said = Net.text(Badges.SERVER + "/sb/prints?since=" + printsUntil);
                if (said != null) {
                    JSONObject fresh = new JSONObject(said);
                    JSONArray list = all.optJSONArray("prints");
                    if (list == null) list = new JSONArray();
                    JSONArray added = fresh.optJSONArray("prints");
                    for (int i = 0; added != null && i < added.length(); i++) list.put(added.get(i));
                    all.put("prints", list).put("until", Math.max(printsUntil, fresh.optLong("until")));
                    java.nio.file.Files.write(file.toPath(), all.toString().getBytes("UTF-8"));
                }
                Hear.Index index = new Hear.Index();
                JSONArray list = all.optJSONArray("prints");
                Set<Long> seen = new HashSet<Long>();
                for (int i = 0; list != null && i < list.length(); i++) {
                    JSONObject one = list.getJSONObject(i);
                    if (!seen.add(one.getLong("id"))) continue;
                    JSONArray hashes = one.getJSONArray("hashes");
                    int[] flat = new int[hashes.length()];
                    for (int k = 0; k < flat.length; k++) flat[k] = hashes.getInt(k);
                    index.add(one.getLong("id"), one.getLong("length"), flat);
                }
                heard = index;
                printsLoaded = true;
            } catch (Throwable error) {
                Diary.note("sponsor prints: " + error);
            }
        });
    }

    /** On Wi-Fi, or on mobile data when that is switched on: the video is fetched once more, in its smallest size, to be listened to. */
    private static boolean unmetered() {
        try {
            android.net.ConnectivityManager net = (android.net.ConnectivityManager) Margy.context().getSystemService(Context.CONNECTIVITY_SERVICE);
            return net != null && net.getActiveNetwork() != null && !net.isActiveNetworkMetered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The video playing is listened to for the adverts whose prints are known. */
    private static void listen(final String aid) {
        if (!soundOn()) return;
        prints();
        final Object post = PlayerTools.post();
        if (post == null || !aid.equals(NativeRead.aid(post)) || heard.empty() || !(unmetered() || mobileOn())) return;
        synchronized (SponsorBlock.class) {
            if (!listened.add(aid)) return;
        }
        listener.execute(() -> {
            java.io.File file = new java.io.File(Margy.context().getCacheDir(), "margyt-sb-hear-" + aid + ".mp4");
            try {
                // a video scrolled past before its turn is not worth fetching
                if (!aid.equals(PlayerTools.aid())) {
                    synchronized (SponsorBlock.class) { listened.remove(aid); }
                    return;
                }
                long length = NativeRead.number(NativeRead.get(NativeRead.get(post, "getVideo"), "getDuration"));
                if (length > 0 && (length < 8000 || length > 10 * 60 * 1000L)) return;
                if (!fetchSmallest(post, file)) return;
                short[] sound = Hear.sound(file.getAbsolutePath(), 10 * 60 * 1000L);
                java.util.List<long[]> found = Hear.find(Hear.whole(sound), heard);
                if (found.isEmpty()) return;
                synchronized (SponsorBlock.class) {
                    long[][] had = known.get(aid);
                    java.util.ArrayList<long[]> all = new java.util.ArrayList<long[]>();
                    if (had != null) java.util.Collections.addAll(all, had);
                    for (long[] one : found) {
                        boolean same = false;
                        for (long[] was : all) if (Math.abs(was[0] - one[0]) < 2500 && Math.abs(was[1] - one[1]) < 2500) same = true;
                        if (!same) all.add(one);
                    }
                    known.put(aid, all.toArray(new long[0][]));
                }
                wake();
                Diary.note("sponsor: heard " + found.size() + " advert(s) in " + aid);
            } catch (Throwable error) {
                Diary.note("sponsor listen: " + error);
            } finally {
                file.delete();
            }
        });
    }

    /** The video's smallest file, fetched into `into`; true when it is there. */
    static boolean fetchSmallest(Object post, java.io.File into) {
        if (into.length() > 0) return true;
        Object video = NativeRead.get(post, "getVideo");
        Object smallest = DownloadQuality.video(video, NativeRead.get(video, "getPlayAddr"), 2);
        for (String address : DownloadQuality.all(smallest)) {
            if (Net.download(address, into, null)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ marking

    /** A post of photos, which has no advert read out to skip. */
    static boolean photos(Object post) {
        if (post == null) return false;
        if (NativeRead.get(post, "getPhotoModeImageInfo") != null) return true;
        Object images = NativeRead.get(post, "getImageInfos");
        return images instanceof java.util.List && !((java.util.List<?>) images).isEmpty();
    }

    /** From the video's menu: mark where the advert is, or say a red part is no advert. */
    static void mark(final Context context, final Object post) {
        final String aid = NativeRead.aid(post);
        if (aid.length() == 0) {
            Screen.say(Text.VIDEO_NOT_READY);
            return;
        }
        final Object manager = aid.equals(PlayerTools.aid()) ? PlayerTools.manager() : null;
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            Panel.with(context, Skin.remembered(context), Local.of("Нужен подтверждённый аккаунт"))
                    .text(Local.of("Отмечать рекламу могут только подтверждённые аккаунты. Подтвердить можно в настройках мода, в разделе «Значки»."))
                    .primary(Local.of("Понятно"), null).show();
            return;
        }
        long known = manager == null ? 0 : PlayerTools.duration(manager);
        if (known <= 0) known = NativeRead.number(NativeRead.get(NativeRead.get(post, "getVideo"), "getDuration"));
        final long length = known;
        long now = manager == null ? 0 : Math.max(0, PlayerTools.position(manager));
        if (length <= 0) {
            Screen.say(Text.PLAYER_NOT_READY);
            return;
        }
        final Marker marker = new Marker(context, post, length, Math.max(0, now - 1000), Math.min(length, now + 9000));
        Panel panel = Panel.with(context, Skin.remembered(context), Local.of("Где реклама"));
        panel.text(Local.of("Потяни красные края к началу и концу рекламы и нажми ▶, чтобы послушать кусок."));
        panel.view(marker);
        long[][] parts = partsOf(aid);
        if (parts != null) {
            for (final long[] part : parts) {
                if (part[2] < 0) continue;
                panel.item("block", Local.of("Это не реклама"), Local.of("с") + " " + time(part[0]) + " " + Local.of("до") + " " + time(part[1]),
                        () -> send(aid, part[0], part[1], length, true, null));
            }
        }
        panel.primary(Local.of("Отправить"), () -> send(aid, marker.range.start, marker.range.stop, length, false, marker.frames.file));
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static void send(final String aid, final long start, final long stop, final long length, final boolean wrong, final java.io.File sound) {
        final String uid = Account.id();
        final String token = Mine.token();
        // the file goes away with the window: it is moved aside now, and listened to away from the screen
        java.io.File kept = null;
        if (sound != null && sound.length() > 0 && !wrong) {
            java.io.File aside = new java.io.File(sound.getParentFile(), "margyt-sb-send-" + aid + ".mp4");
            if (sound.renameTo(aside)) kept = aside;
        }
        final java.io.File heardFile = kept;
        Net.away("sponsor mark", new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject().put("uid", uid).put("token", token).put("aid", aid)
                            .put("start", start).put("stop", stop).put("length", length).put("wrong", wrong);
                    short[] heardNow = null;
                    if (heardFile != null) {
                        try {
                            heardNow = Hear.sound(heardFile.getAbsolutePath(), Math.min(length, stop + 1000) + 1000);
                        } catch (Throwable error) {
                            Diary.note("sponsor print: " + error);
                        } finally {
                            heardFile.delete();
                        }
                    }
                    if (heardNow != null) {
                        int[] print = Hear.print(heardNow, start, stop);
                        if (print.length > 0) {
                            JSONArray list = new JSONArray();
                            for (int i = 0; i < print.length && i < 12000; i++) list.put(print[i]);
                            body.put("print", list);
                        }
                    }
                    Net.Said said = Net.talk(Badges.SERVER + "/sb/report", body.toString());
                    if (said.ok()) {
                        JSONObject answer = new JSONObject(said.body);
                        keep(aid, parse(answer.optJSONArray("segments")));
                        printsAsked = 0;
                        Screen.say(wrong ? Local.of("Спасибо, учтём")
                                : answer.optBoolean("taken") ? Local.of("Готово, реклама будет пропускаться")
                                : Local.of("Спасибо! Реклама начнёт пропускаться, когда её отметят ещё несколько человек"));
                    } else if (Mine.asksForProof(said)) {
                        Screen.say(Local.of("Сначала подтверди аккаунт в разделе «Значки»"));
                    } else if (said.code == 429) {
                        Screen.say(Local.of("Слишком много отметок, попробуй позже"));
                    } else {
                        Screen.say(Local.of("Не получилось отправить, попробуй позже"));
                    }
                } catch (Throwable error) {
                    Diary.note("sponsor mark: " + error);
                    Screen.say(Local.of("Не получилось отправить, попробуй позже"));
                }
            }
        });
    }

    // ------------------------------------------------------------ the video's frames

    /**
     * The video for the marker: its smallest file is fetched into the cache, and frames are read
     * from it one at a time on a thread of their own, the newest asked for first.
     */
    static final class Frames {
        final java.io.File file;
        private android.media.MediaMetadataRetriever reader;
        private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        private final android.util.LruCache<Long, android.graphics.Bitmap> kept = new android.util.LruCache<Long, android.graphics.Bitmap>(48);
        volatile boolean ready, failed;
        private final long[] wanted = {-1, -1};
        private final int width;

        Frames(Context context, final Object post, final Runnable changed) {
            String aid = NativeRead.aid(post);
            file = new java.io.File(context.getCacheDir(), "margyt-sb-" + aid + ".mp4");
            width = Math.round(context.getResources().getDisplayMetrics().density * 180);
            worker.execute(() -> {
                try {
                    if (!fetchSmallest(post, file)) throw new java.io.IOException("no file");
                    android.media.MediaMetadataRetriever opened = new android.media.MediaMetadataRetriever();
                    opened.setDataSource(file.getAbsolutePath());
                    reader = opened;
                    ready = true;
                } catch (Throwable error) {
                    failed = true;
                    Diary.note("sponsor frames: " + error);
                }
                main.post(changed);
            });
        }

        /** The frame at a time if it is already read; asks for it otherwise. */
        android.graphics.Bitmap at(final long ms, final boolean exact, final boolean strip, final Runnable changed) {
            final long key = (ms / (exact ? 100 : 500)) * 2 + (exact ? 1 : 0);
            final int lane = strip ? 1 : 0;
            android.graphics.Bitmap known = kept.get(key);
            if (known != null || !ready) return known;
            if (wanted[lane] == key) return null;
            wanted[lane] = key;
            worker.execute(() -> {
                if (wanted[lane] != key && !exact) return;
                try {
                    android.media.MediaMetadataRetriever r = reader;
                    if (r == null) return;
                    int option = exact ? android.media.MediaMetadataRetriever.OPTION_CLOSEST : android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
                    android.graphics.Bitmap frame = android.os.Build.VERSION.SDK_INT >= 27
                            ? r.getScaledFrameAtTime(ms * 1000L, option, width, width * 2)
                            : r.getFrameAtTime(ms * 1000L, option);
                    if (frame != null) kept.put(key, frame);
                } catch (Throwable ignored) {
                }
                if (wanted[lane] == key) wanted[lane] = -1;
                main.post(changed);
            });
            return null;
        }

        void close() {
            worker.execute(() -> {
                try {
                    if (reader != null) reader.release();
                } catch (Throwable ignored) {
                }
                reader = null;
                file.delete();
            });
            worker.shutdown();
        }
    }

    // ------------------------------------------------------------ the marker

    /** The marker's window: a small player of the part chosen, and the strip of the whole video under it. */
    static final class Marker extends android.widget.LinearLayout {
        final Frames frames;
        final Range range;
        final Preview preview;

        Marker(Context context, Object post, long length, long start, long stop) {
            super(context);
            setOrientation(VERTICAL);
            frames = new Frames(context, post, this::refresh);
            preview = new Preview(context, this);
            range = new Range(context, length, start, stop, this);
            addView(preview, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(236 * getResources().getDisplayMetrics().density)));
            addView(range, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void refresh() {
            preview.refresh();
            range.invalidate();
        }

        /** An edge moved: the player stops and shows the frame there. */
        void edge(long at, boolean moving, boolean isStart) {
            preview.still(at, !moving, isStart);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            preview.release();
            frames.close();
        }
    }

    /** The small player: the frame at an edge while it is moved, and the chosen part, with its sound, on ▶. */
    static final class Preview extends FrameLayout implements android.view.TextureView.SurfaceTextureListener {
        private final Marker marker;
        private final android.view.TextureView screen;
        private final android.widget.ImageView picture;
        private final TextView button, caption;
        private android.media.MediaPlayer player;
        private boolean prepared, playing, preparing;
        private android.view.Surface surface;
        private long shown;
        private boolean shownStart = true, exact = true;
        private final android.media.AudioManager audio;
        private Object focus;

        Preview(Context context, Marker marker) {
            super(context);
            this.marker = marker;
            float d = context.getResources().getDisplayMetrics().density;
            audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            FrameLayout box = new FrameLayout(context);
            GradientDrawable round = new GradientDrawable();
            round.setColor(0xFF000000);
            round.setCornerRadius(14 * d);
            box.setBackground(round);
            box.setClipToOutline(true);
            box.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            screen = new android.view.TextureView(context);
            screen.setSurfaceTextureListener(this);
            box.addView(screen, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            picture = new android.widget.ImageView(context);
            picture.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            box.addView(picture, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            button = new TextView(context);
            button.setText("▶");
            button.setTextColor(0xFFFFFFFF);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            button.setGravity(Gravity.CENTER);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(0x99000000);
            button.setBackground(disc);
            box.addView(button, new LayoutParams(Math.round(54 * d), Math.round(54 * d), Gravity.CENTER));
            int h = Math.round(200 * d), w = Math.round(h * 9f / 16f);
            addView(box, new LayoutParams(w, h, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            caption = new TextView(context);
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            caption.setGravity(Gravity.CENTER);
            caption.setTextColor(Skin.remembered(context).text);
            Fonts.apply(caption);
            addView(caption, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            box.setOnClickListener(v -> {
                if (playing) pause(); else play();
            });
            shown = marker == null ? 0 : -1;
        }

        void refresh() {
            if (shown < 0) shown = marker.range.start;
            if (!playing) {
                android.graphics.Bitmap frame = marker.frames.at(shown, exact, false, marker::refresh);
                if (frame == null && exact) frame = marker.frames.at(shown, false, false, marker::refresh);
                if (frame != null) picture.setImageBitmap(frame);
            }
            if (!marker.frames.ready && !marker.frames.failed) caption.setText(Local.of("Загружаю кадры…"));
            else if (marker.frames.failed) caption.setText(Local.of("Кадры не загрузились"));
            else if (!playing) caption.setText((shownStart ? Local.of("Начало") : Local.of("Конец")) + " · " + time(shown));
            if (marker.frames.ready && player == null && surface != null) open();
        }

        void still(long at, boolean settled, boolean isStart) {
            if (playing) pause();
            shown = at;
            shownStart = isStart;
            exact = settled;
            picture.setVisibility(VISIBLE);
            refresh();
        }

        private void open() {
            if (preparing || player != null) return;
            preparing = true;
            try {
                android.media.MediaPlayer made = new android.media.MediaPlayer();
                made.setDataSource(marker.frames.file.getAbsolutePath());
                made.setSurface(surface);
                made.setOnPreparedListener(mp -> prepared = true);
                made.setOnErrorListener((mp, what, extra) -> {
                    prepared = false;
                    return true;
                });
                made.prepareAsync();
                player = made;
            } catch (Throwable error) {
                Diary.note("sponsor preview: " + error);
            }
        }

        private final Runnable watch = new Runnable() {
            @Override public void run() {
                if (!playing || player == null) return;
                long at = player.getCurrentPosition();
                caption.setText(Local.of("Играет") + " · " + time(at));
                if (at >= marker.range.stop) {
                    pause();
                    return;
                }
                postDelayed(this, 50);
            }
        };

        private void play() {
            if (!prepared || player == null) {
                Screen.say(Local.of("Секунду, видео ещё загружается"));
                return;
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    android.media.AudioFocusRequest asked = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();
                    audio.requestAudioFocus(asked);
                    focus = asked;
                    player.seekTo(marker.range.start, android.media.MediaPlayer.SEEK_CLOSEST);
                } else {
                    player.seekTo((int) marker.range.start);
                }
                player.start();
                playing = true;
                picture.setVisibility(INVISIBLE);
                button.setText("❚❚");
                post(watch);
            } catch (Throwable error) {
                Diary.note("sponsor play: " + error);
            }
        }

        private void pause() {
            playing = false;
            removeCallbacks(watch);
            try {
                if (player != null && player.isPlaying()) player.pause();
            } catch (Throwable ignored) {
            }
            letGo();
            button.setText("▶");
            picture.setVisibility(VISIBLE);
            refresh();
        }

        private void letGo() {
            if (focus != null && android.os.Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest((android.media.AudioFocusRequest) focus);
            focus = null;
        }

        void release() {
            playing = false;
            removeCallbacks(watch);
            letGo();
            try {
                if (player != null) player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }

        @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture texture, int w, int h) {
            surface = new android.view.Surface(texture);
            if (marker.frames.ready) open();
        }

        @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture texture, int w, int h) {}

        @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture texture) {
            release();
            return true;
        }

        @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) {}
    }

    /** The strip of the whole video's frames, the advert between two red edges. */
    static final class Range extends View {
        final long length;
        long start, stop;
        private final Marker marker;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final android.graphics.Rect from = new android.graphics.Rect();
        private final float d;
        private int held = -1;

        Range(Context context, long length, long start, long stop, Marker marker) {
            super(context);
            this.length = length;
            this.start = start;
            this.stop = Math.max(start + 1000, stop);
            this.marker = marker;
            d = context.getResources().getDisplayMetrics().density;
            words.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Math.round(104 * d));
        }

        private float left() { return 16 * d; }

        private float right() { return getWidth() - 16 * d; }

        private float x(long ms) { return left() + (right() - left()) * ms / (float) length; }

        private long ms(float x) { return Math.max(0, Math.min(length, Math.round((x - left()) / (right() - left()) * length))); }

        private void picture(Canvas canvas, android.graphics.Bitmap frame, RectF into) {
            if (frame == null) {
                paint.setColor(0x33808080);
                canvas.drawRect(into, paint);
                return;
            }
            float scale = Math.max(into.width() / frame.getWidth(), into.height() / frame.getHeight());
            float w = into.width() / scale, h = into.height() / scale;
            int x0 = Math.round((frame.getWidth() - w) / 2f), y0 = Math.round((frame.getHeight() - h) / 2f);
            from.set(x0, y0, Math.round(x0 + w), Math.round(y0 + h));
            paint.setColor(0xFFFFFFFF);
            canvas.drawBitmap(frame, from, into, paint);
        }

        @Override protected void onDraw(Canvas canvas) {
            Skin skin = Skin.remembered(getContext());
            float top = 12 * d, sh = 56 * d;
            int many = Math.max(4, Math.round((right() - left()) / (34 * d)));
            float each = (right() - left()) / many;
            for (int i = 0; i < many; i++) {
                long t = (long) ((i + 0.5f) * length / many);
                picture(canvas, marker.frames.at(t, false, true, marker::refresh), new RectF(left() + i * each, top, left() + (i + 1) * each, top + sh));
            }
            paint.setColor(0x99000000);
            canvas.drawRect(left(), top, x(start), top + sh, paint);
            canvas.drawRect(x(stop), top, right(), top + sh, paint);
            paint.setColor(0xFFFF3B3B);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * d);
            canvas.drawRect(x(start), top + 1.5f * d, x(stop), top + sh - 1.5f * d, paint);
            paint.setStyle(Paint.Style.FILL);
            for (long edge : new long[] {start, stop}) {
                box.set(x(edge) - 7 * d, top - 4 * d, x(edge) + 7 * d, top + sh + 4 * d);
                canvas.drawRoundRect(box, 5 * d, 5 * d, paint);
                paint.setColor(0xFFFFFFFF);
                box.set(x(edge) - 1.5f * d, top + sh / 2f - 10 * d, x(edge) + 1.5f * d, top + sh / 2f + 10 * d);
                canvas.drawRoundRect(box, 1.5f * d, 1.5f * d, paint);
                paint.setColor(0xFFFF3B3B);
            }
            words.setColor(skin.muted());
            words.setTextSize(13 * d);
            canvas.drawText(Local.of("с") + " " + time(start) + " " + Local.of("до") + " " + time(stop), getWidth() / 2f, top + sh + 26 * d, words);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    held = Math.abs(x - x(start)) <= Math.abs(x - x(stop)) ? 0 : 1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // falls through
                case MotionEvent.ACTION_MOVE:
                    long at = ms(x);
                    if (held == 0) start = Math.min(at, stop - 1000);
                    else if (held == 1) stop = Math.max(at, start + 1000);
                    marker.edge(held == 0 ? start : stop, true, held == 0);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (held >= 0) marker.edge(held == 0 ? start : stop, false, held == 0);
                    held = -1;
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
