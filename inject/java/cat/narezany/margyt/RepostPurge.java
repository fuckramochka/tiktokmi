package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes back every repost the person has made, one after another.
 *
 * Nothing here talks to TikTok's servers by itself. The list of reposts and the
 * taking back of one are TikTok's own calls -- the ones its repost tab and its
 * repost button make -- and they are called the way the app calls them, so they
 * are signed and sent by the app's own network stack, as the person's own taps
 * would be. What the mod adds is only the loop, and the manners of a loop: a
 * pause between two calls, a stop when the server starts refusing, and a button
 * that ends it.
 */
final class RepostPurge {

    private RepostPurge() {}

    /** Reposts asked for at a time, and the most pages that are ever read. */
    private static final int PAGE = 50, PAGES = 400;
    /** The wait between two reposts taken back. */
    private static final long PAUSE = 1500L;
    /** After this many refusals in a row the server is not asked again. */
    private static final int REFUSALS = 3;

    private static volatile boolean counting, running, cancelled;

    private static final class Entry {
        String item = "", repost = "";
        int carrier;
    }

    // ------------------------------------------------------------ the question

    /** The settings row: count them, ask, and only on a yes take them back. */
    static void begin(final Activity activity) {
        if (counting || running) return;
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) {
            Toast.makeText(activity, Local.of("Не удалось определить аккаунт"), Toast.LENGTH_LONG).show();
            return;
        }
        counting = true;
        cancelled = false;
        // the same page as the taking back: a count that takes a while should be seen to be going
        final Waiting waiting = screen(activity, Local.of("Считаем ваши репосты..."));
        waiting.dialog.show();
        new Thread(new Runnable() {
            @Override public void run() {
                List<Entry> found = null;
                String failure = null;
                try {
                    found = list(uid, activity, waiting);
                } catch (Throwable error) {
                    failure = String.valueOf(error);
                    Diary.note("repost purge: list failed, " + error);
                }
                final List<Entry> entries = found;
                final String failed = failure;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        counting = false;
                        try {
                            if (waiting.dialog.isShowing()) waiting.dialog.dismiss();
                        } catch (Throwable ignored) {
                        }
                        if (activity.isFinishing() || cancelled) return;
                        if (failed != null) {
                            Popup.show(activity, Local.of("Репосты"), Local.of("Не удалось получить список репостов. Откройте TikTok, зайдите в свой профиль и попробуйте ещё раз."));
                        } else if (entries.isEmpty()) {
                            Popup.show(activity, Local.of("Репосты"), Local.of("У вас нет репостов"));
                        } else {
                            confirm(activity, entries);
                        }
                    }
                });
            }
        }, "margyt-repost-count").start();
    }

    private static void confirm(final Activity activity, final List<Entry> entries) {
        Popup.ask(activity,
                Local.of("Вы точно согласны на удаление всех репостов?"),
                Local.of("У вас сейчас репостов: ") + entries.size(),
                Local.of("Да"), new Runnable() {
                    @Override public void run() {
                        remove(activity, entries);
                    }
                },
                Local.of("Нет"), null,
                null, null);
    }

    // ------------------------------------------------------------ TikTok's calls

    /** One page as TikTok answered it. */
    private static final class Page {
        List<?> posts;
        boolean more;
        Throwable failure;
    }

    /**
     * Every repost of the account, a page after a page.
     *
     * The next page starts a whole page further on, whatever came back: the server leaves
     * out of a page the videos that are gone or private, so a page of twenty may bring four,
     * and TikTok's own repost tab moves on by twenty all the same (RepostListViewModel: the
     * offset plus the count asked for). Moving on by what came back asked for the same
     * reposts again and stopped at the first short page. The count ends when the server says
     * there is no more; a repost seen twice is kept once.
     */
    private static List<Entry> list(String uid, final Activity activity, final Waiting waiting) throws Throwable {
        final Object service = Class.forName("com.ss.android.ugc.aweme.upvote.service.UpvoteServiceImpl")
                .getConstructor().newInstance();
        final Method fetch = service.getClass().getMethod("LJFF", String.class, long.class, int.class, Integer.class);
        final Method first = Class.forName("X.19ZK").getMethod("LIZLLL");   // the stream's blockingFirst
        Map<String, Entry> out = new LinkedHashMap<String, Entry>();

        long offset = 0;
        int pages = 0, barren = 0;
        while (pages < PAGES && !cancelled && !activity.isFinishing()) {
            Page page = page(service, fetch, first, uid, offset);
            if (page.failure != null) {
                // a first page that fails is a failure; a later one ends the count with what is sure
                if (pages == 0) throw page.failure;
                Diary.note("repost purge: page " + pages + " failed, " + page.failure);
                break;
            }
            pages++;
            int before = out.size();
            take(out, page);
            offset += PAGE;
            barren = out.size() == before ? barren + 1 : 0;
            shown(activity, waiting, out.size());
            if (!page.more || barren >= 6) break;
        }
        Diary.note("repost purge: " + out.size() + " reposts found in " + pages + " pages");
        return new ArrayList<Entry>(out.values());
    }

    private static Page page(Object service, Method fetch, Method first, String uid, long offset) {
        Page page = new Page();
        try {
            Object answer = call(first, call(fetch, service, uid, offset, PAGE, null));
            page.posts = answer == null ? null : (List<?>) NativeRead.field(answer, "repostList");
            page.more = answer != null && Boolean.TRUE.equals(NativeRead.field(answer, "hasMore"));
        } catch (Throwable error) {
            page.failure = error;
        }
        return page;
    }

    private static void take(Map<String, Entry> out, Page page) {
        if (page.posts == null) return;
        for (Object post : page.posts) {
            Entry entry = entry(post);
            if (entry == null) continue;
            String key = entry.item + "/" + entry.repost;
            if (out.containsKey(key)) continue;
            out.put(key, entry);
            if (out.size() == 1) {
                Diary.note("repost purge: first is item " + entry.item + ", repost " + entry.repost
                        + ", carrier " + entry.carrier + ", info " + describe(NativeRead.get(post, "getUpvoteInfo")));
            }
        }
    }

    /** What has been counted so far, under the ring. */
    private static void shown(Activity activity, final Waiting waiting, final int counted) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!cancelled) waiting.words.setText(Local.of("Считаем ваши репосты...") + "\n" + counted);
            }
        });
    }

    private static Entry entry(Object post) {
        String item = NativeRead.aid(post);
        Object info = NativeRead.get(post, "getUpvoteInfo");
        Entry entry = new Entry();
        entry.item = item;
        if (info != null) {
            String own = NativeRead.string(NativeRead.field(info, "itemId"));
            if (own.length() > 0) entry.item = own;
            entry.repost = NativeRead.string(NativeRead.field(info, "upvoteId"));
            Object carrier = NativeRead.field(info, "carrierType");
            if (carrier instanceof Number) entry.carrier = ((Number) carrier).intValue();
        }
        return entry.item.length() == 0 ? null : entry;
    }

    /** TikTok's call to take one repost back; it returns once the server has answered. */
    private static void take(Entry entry, Object operator, Method delete, Method wait) throws Throwable {
        Object response = call(wait, call(delete, operator, entry.carrier, entry.item, entry.repost));
        Diary.note("repost purge: " + entry.item + " -> " + describe(response));
    }

    private static Object call(Method method, Object on, Object... arguments) throws Throwable {
        try {
            return method.invoke(on, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }

    private static String describe(Object thing) {
        if (thing == null) return "null";
        String text = String.valueOf(thing);
        return thing.getClass().getSimpleName() + (text.length() > 160 ? text.substring(0, 160) : text);
    }

    // ------------------------------------------------------------ taking back

    private static void remove(final Activity activity, final List<Entry> entries) {
        if (running) return;
        running = true;
        cancelled = false;

        final Waiting waiting = screen(activity, Local.of("Удаляем ваши репосты..."));
        final Dialog dialog = waiting.dialog;
        dialog.show();

        new Thread(new Runnable() {
            @Override public void run() {
                int done = 0, refused = 0;
                String why = null;
                try {
                    Object operator = Class.forName("X.0pPL").getConstructor().newInstance();
                    Method delete = operator.getClass().getMethod("LLLLL", Integer.class, String.class, String.class);
                    Method wait = Class.forName("X.19X3").getMethod("LJ");   // the single's blockingGet
                    for (Entry entry : entries) {
                        if (cancelled || activity.isFinishing()) break;
                        try {
                            take(entry, operator, delete, wait);
                            done++;
                            refused = 0;
                        } catch (Throwable error) {
                            refused++;
                            Diary.note("repost purge: refused, " + error);
                            if (refused >= REFUSALS) {
                                why = String.valueOf(error);
                                break;
                            }
                        }
                        long until = SystemClock.uptimeMillis() + PAUSE;
                        while (!cancelled && SystemClock.uptimeMillis() < until) Thread.sleep(50);
                    }
                } catch (Throwable error) {
                    why = String.valueOf(error);
                    Diary.note("repost purge: " + error);
                }
                final int removed = done;
                final boolean stopped = why != null;
                final boolean cancelledNow = cancelled;
                running = false;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try {
                            if (dialog.isShowing()) dialog.dismiss();
                        } catch (Throwable ignored) {
                        }
                        if (activity.isFinishing()) return;
                        String message = Local.of("Удалено репостов: ") + removed + " / " + entries.size();
                        if (stopped) message += "\n" + Local.of("TikTok перестал отвечать на запросы. Подождите немного и запустите ещё раз.");
                        else if (cancelledNow) message += "\n" + Local.of("Остановлено");
                        Popup.show(activity, Local.of("Репосты"), message);
                    }
                });
            }
        }, "margyt-repost-purge").start();
    }

    // ------------------------------------------------------------ the screen

    /** A whole page: the turning ring in the middle, what is being done under it, and the way out. */
    private static Waiting screen(final Activity activity, String text) {
        Skin skin = Skin.remembered(activity);
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(skin.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(skin.page);
        page.setPadding(dp(activity, 32), dp(activity, 32), dp(activity, 32), dp(activity, 32));

        Ring ring = new Ring(activity);
        page.addView(ring, new LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56)));

        final TextView words = new TextView(activity);
        words.setText(text);
        words.setTextColor(skin.text);
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        words.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        under.topMargin = dp(activity, 24);
        page.addView(words, under);

        TextView cancel = new TextView(activity);
        cancel.setText(Local.of("Отмена"));
        cancel.setTextColor(Accent.colour() | 0xFF000000);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(dp(activity, 48));
        cancel.setMinWidth(dp(activity, 120));
        cancel.setPadding(dp(activity, 24), 0, dp(activity, 24), 0);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(dp(activity, 1), (skin.text & 0x00FFFFFF) | 0x66000000);
        outline.setCornerRadius(dp(activity, 24));
        cancel.setBackground(outline);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cancelled = true;
                words.setText(Local.of("Останавливаем..."));
                v.setEnabled(false);
                v.setAlpha(0.5f);
            }
        });
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = dp(activity, 40);
        page.addView(cancel, below);

        dialog.setContentView(page);
        dialog.setCanceledOnTouchOutside(false);
        // back is the same as the button: the work stops after the repost it is on
        dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override public void onCancel(android.content.DialogInterface d) {
                cancelled = true;
            }
        });
        return new Waiting(dialog, words);
    }

    /** The page that is on the screen, and the words under its ring. */
    private static final class Waiting {
        final Dialog dialog;
        final TextView words;

        Waiting(Dialog dialog, TextView words) {
            this.dialog = dialog;
            this.words = words;
        }
    }

    /**
     * Material 3 Expressive's wavy circular progress indicator, the indeterminate one:
     * a pale ring for the track, and on it an arc drawn as a wave, with rounded ends,
     * that grows from its tail and lets the tail catch up while the whole of it turns.
     * The wave itself travels along the arc.
     */
    private static final class Ring extends View {
        private static final long SPIN = 1568L, CYCLE = 1333L, RIPPLE = 900L;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final android.graphics.Path wave = new android.graphics.Path();

        Ring(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        private static float ease(float t) {
            // the standard curve: slow out, slow in
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }

        @Override protected void onDraw(Canvas canvas) {
            float unit = getResources().getDisplayMetrics().density;
            float stroke = 4f * unit, amplitude = 1.6f * unit;
            float centre = getWidth() / 2f;
            float radius = centre - stroke / 2f - amplitude;
            int accent = Accent.colour() | 0xFF000000;

            long now = SystemClock.uptimeMillis();
            float turn = (now % SPIN) / (float) SPIN * 360f;
            float within = (now % CYCLE) / (float) CYCLE;
            long laps = now / CYCLE;
            float head = within < 0.5f ? 250f * ease(within * 2f) : 250f;
            float tail = within < 0.5f ? 0f : 250f * ease((within - 0.5f) * 2f);
            float start = turn + tail + (laps % 360) * 250f;
            float sweep = 30f + head - tail;

            // the track: the whole ring, pale, with a gap kept round the arc
            float gap = (float) Math.toDegrees((stroke * 1.6f) / radius);
            paint.setStrokeWidth(stroke);
            paint.setColor((accent & 0x00FFFFFF) | 0x38000000);
            box.set(centre - radius, centre - radius, centre + radius, centre + radius);
            float room = 360f - sweep - 2f * gap;
            if (room > 0f) canvas.drawArc(box, start + sweep + gap, room, false, paint);

            // the arc: a sine laid round the circle, a wave every ~14dp of the way round
            int waves = Math.max(6, Math.round((float) (2 * Math.PI * radius) / (14f * unit)));
            float ripple = (now % RIPPLE) / (float) RIPPLE * (float) (2 * Math.PI);
            wave.rewind();
            for (float a = 0f; a <= sweep; a += 2f) {
                double angle = Math.toRadians(start + a);
                double along = (angle - Math.toRadians(start)) * waves - ripple;
                // the wave dies away towards both ends, so the ends are round and not ragged
                float edge = Math.min(1f, Math.min(a, sweep - a) / 14f);
                float r = radius + amplitude * edge * (float) Math.sin(along);
                float x = centre + r * (float) Math.cos(angle), y = centre + r * (float) Math.sin(angle);
                if (a == 0f) wave.moveTo(x, y);
                else wave.lineTo(x, y);
            }
            paint.setColor(accent);
            canvas.drawPath(wave, paint);
            postInvalidateOnAnimation();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
