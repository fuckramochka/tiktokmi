package mi.tiktokmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The editor of TikTok's own screens: with it on, a touch picks out whatever is under the finger --
 * any view of any screen, not a list of known ones -- and it can be hidden, moved, made larger or
 * smaller, and painted.
 *
 * A view is found the way a person points at it, from the tree on the screen, and is written down
 * by what it can be told apart by: its id where it has one, otherwise what it is and where it
 * sits among its neighbours, together with the same of the two views above it. That is a rule, and
 * a rule holds for every view that answers to it, so a change to one button of a video is a change
 * to the button of every video.
 *
 * Rules are looked for in the tree now and then (a screen is put together again and again) and are
 * kept before each frame, the way the feed's panels are kept, because TikTok sets its views
 * again when it binds the next video. What a view was before a rule touched it is kept on the view
 * and given back when the rule goes.
 */
public final class ViewEditor {

    private ViewEditor() {}

    private static final String PREF = "ui_editor_rules";
    private static final int BASE = 0x7e0c03B0, PRIOR = 0x7e0c03B1, ONCE = 0x7e0c03B2,
            TEXT_WAS = 0x7e0c03B3, FILL_DONE = 0x7e0c03B4, ICON_DONE = 0x7e0c03B5, CLIPPED = 0x7e0c03B6;

    // ------------------------------------------------------------------------ the rules

    /** What is to be done to every view that answers to a rule. */
    static final class Rule {
        final String key, lead;
        String label = "";
        float dx, dy, scale = 1f;
        boolean hidden;
        Integer text, fill, icon;

        Rule(String key, String lead) {
            this.key = key;
            this.lead = lead;
        }

        boolean empty() {
            return dx == 0f && dy == 0f && scale == 1f && !hidden && text == null && fill == null && icon == null;
        }
    }

    private static final Map<String, Rule> rules = new LinkedHashMap<String, Rule>();
    private static final Set<String> leads = new HashSet<String>();
    private static boolean loaded;
    private static final Map<View, Rule> tracked = new WeakHashMap<View, Rule>();
    private static volatile boolean editing;

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized void load() {
        if (loaded) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        loaded = true;
        String saved = prefs.getString(PREF, null);
        if (saved != null) parse(saved);
    }

    /** The rules as the text they are kept in. */
    private static String serialise() {
        try {
            org.json.JSONObject all = new org.json.JSONObject();
            for (Rule rule : rules.values()) {
                if (rule.empty()) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("lead", rule.lead);
                o.put("l", rule.label);
                if (rule.dx != 0f) o.put("dx", rule.dx);
                if (rule.dy != 0f) o.put("dy", rule.dy);
                if (rule.scale != 1f) o.put("s", rule.scale);
                if (rule.hidden) o.put("h", true);
                if (rule.text != null) o.put("t", rule.text.intValue());
                if (rule.fill != null) o.put("f", rule.fill.intValue());
                if (rule.icon != null) o.put("i", rule.icon.intValue());
                all.put(rule.key, o);
            }
            return all.toString();
        } catch (Throwable error) {
            Diary.note("view editor save: " + error);
            return "{}";
        }
    }

    /** The rules are what the text says. */
    private static void parse(String saved) {
        try {
            rules.clear();
            org.json.JSONObject all = new org.json.JSONObject(saved);
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                org.json.JSONObject o = all.getJSONObject(key);
                Rule rule = new Rule(key, o.optString("lead"));
                rule.label = o.optString("l");
                rule.dx = (float) o.optDouble("dx", 0);
                rule.dy = (float) o.optDouble("dy", 0);
                rule.scale = (float) o.optDouble("s", 1);
                rule.hidden = o.optBoolean("h", false);
                if (o.has("t")) rule.text = o.getInt("t");
                if (o.has("f")) rule.fill = o.getInt("f");
                if (o.has("i")) rule.icon = o.getInt("i");
                if (!rule.empty()) rules.put(key, rule);
            }
            reindex();
        } catch (Throwable error) {
            Diary.note("view editor rules: " + error);
        }
    }

    private static synchronized void save() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(PREF, serialise()).apply();
    }

    // ---- going back: each change first keeps a copy of the rules as they were

    private static final ArrayList<String> past = new ArrayList<String>(), future = new ArrayList<String>();

    /** Before a change: what is now can be come back to. */
    static void remember() {
        past.add(serialise());
        if (past.size() > 80) past.remove(0);
        future.clear();
    }

    static boolean canUndo() {
        return !past.isEmpty();
    }

    static boolean canRedo() {
        return !future.isEmpty();
    }

    static void undo() {
        if (past.isEmpty()) return;
        future.add(serialise());
        restore(past.remove(past.size() - 1));
    }

    static void redo() {
        if (future.isEmpty()) return;
        past.add(serialise());
        restore(future.remove(future.size() - 1));
    }

    /** The rules go back to a copy, and every view that was following one follows what is now. */
    private static void restore(String copy) {
        parse(copy);
        for (View v : new ArrayList<View>(tracked.keySet())) {
            if (v == null) continue;
            Rule rule = ruleOf(v);
            if (rule == null || rule.empty()) {
                release(v);
                tracked.remove(v);
            } else {
                tracked.put(v, rule);
                enforce(v, rule);
            }
        }
        save();
    }

    private static void reindex() {
        leads.clear();
        for (Rule rule : rules.values()) leads.add(rule.lead);
    }

    // ------------------------------------------------------------- how a view is told apart

    private static final SparseArray<String> idNames = new SparseArray<String>();
    private static final Map<Class<?>, String> classNames = new HashMap<Class<?>, String>();

    /** "#id" for a view that has one, "~Class" for one that has not: what it is called before its neighbours are asked. */
    private static String lead(View v) {
        int id = v.getId();
        if (id != View.NO_ID && id != 0) {
            String name = idNames.get(id);
            if (name == null) {
                try {
                    name = "#" + v.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {
                    name = "";
                }
                idNames.put(id, name);
            }
            if (name.length() > 0) return name;
        }
        Class<?> c = v.getClass();
        String name = classNames.get(c);
        if (name == null) {
            name = "~" + c.getName();
            classNames.put(c, name);
        }
        return name;
    }

    private static String seg(View v) {
        String lead = lead(v);
        if (lead.charAt(0) == '#') return lead;
        Object up = v.getParent();
        return up instanceof ViewGroup ? lead + "[" + ((ViewGroup) up).indexOfChild(v) + "]" : lead;
    }

    private static int sameKind(View v) {
        Object up = v.getParent();
        if (!(up instanceof ViewGroup)) return 0;
        ViewGroup group = (ViewGroup) up;
        int n = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View other = group.getChildAt(i);
            if (other == v) return n;
            if (other.getClass() == v.getClass()) n++;
        }
        return n;
    }

    static String keyOf(View v) {
        String lead = lead(v);
        if (lead.charAt(0) == '#') return lead;
        StringBuilder key = new StringBuilder(lead).append('{').append(sameKind(v)).append('}');
        Object up = v.getParent();
        for (int i = 0; i < 5 && up instanceof View; i++) {
            View view = (View) up;
            String name = lead(view);
            key.append('<').append(name);
            if (name.charAt(0) == '#') break;
            key.append('{').append(sameKind(view)).append('}');
            up = view.getParent();
        }
        return key.toString();
    }

    private static Rule ruleOf(View v) {
        Rule rule = rules.get(keyOf(v));
        return rule != null ? rule : rules.get(legacyKey(v));
    }

    private static String legacyKey(View v) {
        StringBuilder key = new StringBuilder(seg(v));
        Object up = v.getParent();
        for (int i = 0; i < 2 && up instanceof View; i++) {
            key.append('<').append(seg((View) up));
            up = ((View) up).getParent();
        }
        return key.toString();
    }

    /** What a person is shown of a view: its id, or what it is. */
    static String title(View v) {
        String lead = lead(v);
        if (lead.charAt(0) == '#') return lead;
        String name = lead.substring(1);
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ---------------------------------------------------------------- keeping the rules

    /** Before each frame: every view that answers to a rule is as the rule says. */
    static void frame() {
        if (tracked.isEmpty()) return;
        try {
            List<View> views = new ArrayList<View>(tracked.keySet());
            for (View v : views) {
                Rule rule = tracked.get(v);
                if (v != null && rule != null) enforce(v, rule);
            }
        } catch (Throwable ignored) {
            // a view taken apart in the middle of a frame is looked at again on the next
        }
    }

    private static void enforce(View v, Rule rule) {
        float[] base = (float[]) v.getTag(BASE);
        if (base == null) {
            base = new float[] {v.getTranslationX(), v.getTranslationY(), v.getScaleX(), v.getScaleY()};
            v.setTag(BASE, base);
        }
        hide(v, rule.hidden);
        float tx = base[0] + rule.dx, ty = base[1] + rule.dy, sx = base[2] * rule.scale, sy = base[3] * rule.scale;
        if (v.getTranslationX() != tx) v.setTranslationX(tx);
        if (v.getTranslationY() != ty) v.setTranslationY(ty);
        if (v.getScaleX() != sx) v.setScaleX(sx);
        if (v.getScaleY() != sy) v.setScaleY(sy);
        // a view that is moved or grown is not cut off at the edge of what it lies in
        if (rule.dx != 0f || rule.dy != 0f || rule.scale > 1f) unclip(v);
        if (rule.text != null && v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getTag(TEXT_WAS) == null) t.setTag(TEXT_WAS, t.getTextColors());
            if (t.getCurrentTextColor() != rule.text.intValue()) t.setTextColor(rule.text.intValue());
        }
        if (rule.fill != null) {
            Drawable d = v.getBackground();
            Object[] done = (Object[]) v.getTag(FILL_DONE);
            if (d != null && (done == null || done[0] != d || ((Integer) done[1]).intValue() != rule.fill.intValue())) {
                d.mutate().setTintList(ColorStateList.valueOf(rule.fill.intValue()));
                v.setTag(FILL_DONE, new Object[] {d, rule.fill});
            }
        }
        if (rule.icon != null && v instanceof ImageView) {
            ImageView i = (ImageView) v;
            Object[] done = (Object[]) i.getTag(ICON_DONE);
            Drawable d = i.getDrawable();
            if (d != null && (done == null || done[0] != d || ((Integer) done[1]).intValue() != rule.icon.intValue())) {
                i.setColorFilter(rule.icon.intValue(), android.graphics.PorterDuff.Mode.SRC_IN);
                i.setTag(ICON_DONE, new Object[] {d, rule.icon});
            }
        }
    }

    /** The views above this one are not to cut off what hangs out of them; each is put back as it was by release. */
    private static void unclip(View v) {
        Object up = v.getParent();
        for (int i = 0; i < 40 && up instanceof ViewGroup; i++) {
            ViewGroup group = (ViewGroup) up;
            if (group.getClipChildren() && group.getTag(CLIPPED) == null) {
                group.setTag(CLIPPED, Boolean.TRUE);
                group.setClipChildren(false);
            }
            up = group.getParent();
        }
    }

    private static void reclip(View v) {
        Object up = v.getParent();
        for (int i = 0; i < 40 && up instanceof ViewGroup; i++) {
            ViewGroup group = (ViewGroup) up;
            if (group.getTag(CLIPPED) != null) {
                group.setTag(CLIPPED, null);
                group.setClipChildren(true);
            }
            up = group.getParent();
        }
    }

    private static void hide(View v, boolean hide) {
        if (hide) {
            if (v.getVisibility() != View.INVISIBLE) {
                v.setTag(PRIOR, Integer.valueOf(v.getVisibility()));
                v.setVisibility(View.INVISIBLE);
            }
        } else {
            Object before = v.getTag(PRIOR);
            if (before instanceof Integer) {
                v.setTag(PRIOR, null);
                if (v.getVisibility() == View.INVISIBLE) v.setVisibility(((Integer) before).intValue());
            }
        }
    }

    /** Gives a view back what it was before any rule touched it. */
    private static void release(View v) {
        hide(v, false);
        reclip(v);
        float[] base = (float[]) v.getTag(BASE);
        if (base != null) {
            v.setTranslationX(base[0]);
            v.setTranslationY(base[1]);
            v.setScaleX(base[2]);
            v.setScaleY(base[3]);
            v.setTag(BASE, null);
        }
        Object was = v.getTag(TEXT_WAS);
        if (was instanceof ColorStateList && v instanceof TextView) ((TextView) v).setTextColor((ColorStateList) was);
        v.setTag(TEXT_WAS, null);
        Object[] fill = (Object[]) v.getTag(FILL_DONE);
        if (fill != null && fill[0] instanceof Drawable) ((Drawable) fill[0]).setTintList(null);
        v.setTag(FILL_DONE, null);
        if (v.getTag(ICON_DONE) != null && v instanceof ImageView) ((ImageView) v).clearColorFilter();
        v.setTag(ICON_DONE, null);
    }

    /** A rule has changed or gone: the views that answer to it are brought up to date. */
    private static void refresh(Rule rule) {
        List<View> views = new ArrayList<View>(tracked.keySet());
        for (View v : views) {
            if (v == null || tracked.get(v) != rule) continue;
            if (rule.empty() || rules.get(rule.key) != rule) {
                release(v);
                tracked.remove(v);
            } else {
                enforce(v, rule);
            }
        }
    }

    /** Looks through a screen for the views the rules are about. */
    private static void walk(View v, int depth) {
        if (depth > 60) return;
        if (leads.contains(lead(v)) && !tracked.containsKey(v)) {
            Rule rule = ruleOf(v);
            if (rule != null) {
                tracked.put(v, rule);
                enforce(v, rule);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), depth + 1);
        }
    }

    // ---------------------------------------------------------------------- the switch

    public static boolean isEditing() {
        return editing;
    }

    /** From the settings: the next screen of TikTok comes up with the editor on it. */
    public static void begin() {
        load();
        editing = true;
        Activity top = Watch.here();
        if (top != null) attach(top);
    }

    static void end() {
        editing = false;
    }

    public static int count() {
        load();
        int n = 0;
        for (Rule rule : rules.values()) if (!rule.empty()) n++;
        return n;
    }

    public static void resetAll() {
        load();
        if (!rules.isEmpty()) remember();
        List<Rule> all = new ArrayList<Rule>(rules.values());
        rules.clear();
        reindex();
        for (Rule rule : all) refresh(rule);
        save();
    }

    // ----------------------------------------------------------------- on each screen

    private static final class Watching implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> decor;
        long looked;
        boolean owed;
        final Runnable later = new Runnable() {
            @Override public void run() {
                owed = false;
                look();
            }
        };

        Watching(View decor) {
            this.decor = new WeakReference<View>(decor);
        }

        @Override
        public void onGlobalLayout() {
            View view = decor.get();
            if (view == null || rules.isEmpty()) return;
            long now = SystemClock.uptimeMillis();
            if (now - looked < 90L) {
                if (!owed) {
                    owed = true;
                    view.postDelayed(later, 100L);
                }
                return;
            }
            look();
        }

        void look() {
            View view = decor.get();
            if (view == null || !view.isAttachedToWindow()) return;
            looked = SystemClock.uptimeMillis();
            try {
                walk(view, 0);
            } catch (Throwable error) {
                Diary.detail("view editor: " + error);
            }
        }

        @Override
        public boolean onPreDraw() {
            if (!rules.isEmpty() && SystemClock.uptimeMillis() - looked >= 90L) look();
            frame();
            return true;
        }
    }

    /** A screen came up: the rules are kept on it, and the editor is put over it if it is on. */
    static void attach(Activity activity) {
        try {
            if (activity == null) return;
            String screen = activity.getClass().getName();
            if (screen.startsWith("mi.tiktokmi") || screen.startsWith("cat.narezany")) return;
            load();
            View decor = activity.getWindow().getDecorView();
            if (decor.getTag(ONCE) == null) {
                Watching watching = new Watching(decor);
                ViewTreeObserver observer = decor.getViewTreeObserver();
                observer.addOnGlobalLayoutListener(watching);
                observer.addOnPreDrawListener(watching);
                decor.setTag(ONCE, watching);
            }
            Watching watching = (Watching) decor.getTag(ONCE);
            watching.look();
            Overlay shown = overlayOn(decor);
            if (editing && shown == null) {
                ((ViewGroup) decor).addView(new Overlay(activity), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else if (!editing && shown != null) {
                ((ViewGroup) decor).removeView(shown);
            }
        } catch (Throwable error) {
            Diary.note("view editor attach: " + error);
        }
    }

    private static Overlay overlayOn(View decor) {
        if (!(decor instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) decor;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            if (group.getChildAt(i) instanceof Overlay) return (Overlay) group.getChildAt(i);
        }
        return null;
    }

    // ------------------------------------------------------------------- the editor itself

    private static final int PICK = 0, MOVE = 1, USE = 2;

    // -------------------------------------------------------------- drawn icons

    private static final String UNDO = "M280-200v-80h284q63 0 109.5-40T720-420q0-60-46.5-100T564-560H312l104 104-56 56-200-200 200-200 56 56-104 104h252q97 0 166.5 63T800-420q0 94-69.5 157T564-200H280Z";
    private static final String REDO = "M396-200q-97 0-166.5-63T160-420q0-94 69.5-157T396-640h252L544-744l56-56 200 200-200 200-56-56 104-104H396q-63 0-109.5 40T240-420q0 60 46.5 100T396-280h284v80H396Z";
    private static final String MORE = "M480-345 240-585l56-56 184 184 184-184 56 56-240 240Z";
    private static final String LESS = "m296-345-56-56 240-240 240 240-56 56-184-184-184 184Z";
    private static final String CHECK = "M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z";
    private static final String UP = "M440-160v-487L216-423l-56-57 320-320 320 320-56 57-224-224v487h-80Z";
    private static final String DOWN = "M440-800v487L216-537l-56 57 320 320 320-320-56-57-224 224v-487h-80Z";
    private static final String LEFT = "m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z";
    private static final String RIGHT = "M647-440H160v-80h487L423-744l56-56 320 320-320 320-56-56 224-224Z";
    private static final String GRID = "M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h133v-133H200v133Zm213 0h134v-133H413v133Zm214 0h133v-133H627v133ZM200-413h133v-134H200v134Zm213 0h134v-134H413v134Zm214 0h133v-134H627v134ZM200-627h133v-133H200v133Zm213 0h134v-133H413v133Zm214 0h133v-133H627v133Z";
    private static final String FOCUS = "M480-360q-50 0-85-35t-35-85q0-50 35-85t85-35q50 0 85 35t35 85q0 50-35 85t-85 35ZM200-120q-33 0-56.5-23.5T120-200v-160h80v160h160v80H200Zm400 0v-80h160v-160h80v160q0 33-23.5 56.5T760-120H600ZM120-600v-160q0-33 23.5-56.5T200-840h160v80H200v160h-80Zm640 0v-160H600v-80h160q33 0 56.5 23.5T840-760v160h-80Z";
    private static final String HOME = "M440-122q-121-15-200.5-105.5T160-440q0-66 26-126.5T260-672l57 57q-38 34-57.5 79T240-440q0 88 56 155.5T440-202v80Zm80 0v-80q87-16 143.5-83T720-440q0-100-70-170t-170-70h-3l44 44-56 56-140-140 140-140 56 56-44 44h3q134 0 227 93t93 227q0 121-79.5 211.5T520-122Z";
    private static final String STEP = "M160-160v-80h640v80H160Zm0-160v-80h640v80H160Zm0-160v-80h640v80H160Zm0-160v-80h640v80H160Z";
    private static final String BLANK = "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Z";
    private static final Map<String, Path> drawn = new HashMap<String, Path>();

    private static synchronized Path pathOf(String data) {
        Path known = drawn.get(data);
        if (known != null) return known;
        final Path path = new Path();
        NavPlan.trace(data, new NavPlan.Pen() {
            @Override public void move(float x, float y) { path.moveTo(x, y); }
            @Override public void line(float x, float y) { path.lineTo(x, y); }
            @Override public void curve(float x1, float y1, float x2, float y2, float x, float y) {
                path.cubicTo(x1, y1, x2, y2, x, y);
            }
            @Override public void close() { path.close(); }
        });
        drawn.put(data, path);
        return path;
    }

    private static Path gripPath() {
        Path path = new Path();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) path.addCircle(360f + col * 240f, -720f + row * 240f, 62f, Path.Direction.CW);
        }
        return path;
    }

    /** A picture drawn from path data, in a box of 960. */
    private static final class Icon extends View {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context context, Path path, int colour) {
            super(context);
            this.path = path;
            paint.setColor(colour);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int side = Math.min(getWidth(), getHeight());
            canvas.save();
            canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
            canvas.scale(side / 960f, side / 960f);
            canvas.translate(0f, 960f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    /** A box that is not taller than a share of the screen: a card with much in it scrolls. */
    private static final class Capped extends android.widget.ScrollView {
        private final int most;

        Capped(Context context, int most) {
            super(context);
            this.most = most;
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(most, MeasureSpec.AT_MOST));
        }
    }

    /** Where the card is, kept from one screen to the next: nothing before it is first moved. */
    private static int cardX = -1, cardY = -1;
    private static boolean collapsed;
    /** How many squares across the grid has; the first is no grid at all. */
    private static final int[] GRIDS = {0, 4, 8, 12, 16, 24};
    private static int grid;
    /** The chosen element's centre and the lines from its corners. */
    private static boolean guides;
    /** How far one press of an arrow moves the element, in dp. */
    private static final int[] STEPS = {1, 5, 20};
    private static int stepAt;
    private static int remembered = PICK;

    private static final int INK = 0xFFE6E0E9, MUTED = 0xFFCAC4D0, DANGER = 0xFFF2B8B5;

    private static final class Overlay extends FrameLayout {
        private final Activity activity;
        private final float unit;
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), glass = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LinearLayout card;
        private final int slop;
        private int mode = remembered;
        private final List<WeakReference<View>> hits = new ArrayList<WeakReference<View>>();
        private int cursor;
        private float lastX, lastY, downX, downY;
        private boolean moved;
        private float dragDx, dragDy, dragLeft, dragTop;
        private final android.view.ScaleGestureDetector pinch;
        private final int[] where = new int[2];
        private final Rect box = new Rect();

        Overlay(Activity activity) {
            super(activity);
            this.activity = activity;
            unit = activity.getResources().getDisplayMetrics().density;
            slop = ViewConfiguration.get(activity).getScaledTouchSlop();
            setWillNotDraw(false);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(2f * unit);
            glass.setStyle(Paint.Style.FILL);
            pinch = new android.view.ScaleGestureDetector(activity, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(android.view.ScaleGestureDetector d) {
                    if (mode != MOVE || selected() == null) return false;
                    remember();
                    return true;
                }

                @Override
                public boolean onScale(android.view.ScaleGestureDetector d) {
                    View v = selected();
                    if (v == null) return false;
                    Rule rule = ruleFor(v);
                    rule.scale = Math.max(0.3f, Math.min(3f, rule.scale * d.getScaleFactor()));
                    refresh(rule);
                    invalidate();
                    return true;
                }

                @Override
                public void onScaleEnd(android.view.ScaleGestureDetector d) {
                    save();
                    rebuild();
                }
            });
            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable back = new GradientDrawable();
            back.setColor(0xF2211F26);
            back.setCornerRadius(24f * unit);
            card.setBackground(back);
            card.setPadding(dp(12), dp(8), dp(12), dp(12));
            card.setClickable(true);
            addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rebuild();
        }

        private int dp(float v) {
            return Math.round(v * unit);
        }

        private int accent() {
            return Accent.colour() | 0xFF000000;
        }

        private View selected() {
            if (cursor < 0 || cursor >= hits.size()) return null;
            return hits.get(cursor).get();
        }

        // ---- drawing the frame round what is chosen
        @Override
        protected void onDraw(Canvas canvas) {
            drawGrid(canvas);
            View v = mode == USE ? null : selected();
            if (v == null || !v.isAttachedToWindow()) return;
            int[] at = new int[2];
            v.getLocationOnScreen(at);
            getLocationOnScreen(where);
            box.set(at[0] - where[0], at[1] - where[1], Math.round(at[0] - where[0] + v.getWidth() * v.getScaleX()),
                    Math.round(at[1] - where[1] + v.getHeight() * v.getScaleY()));
            glass.setColor((accent() & 0x00FFFFFF) | 0x33000000);
            frame.setColor(accent());
            canvas.drawRect(box, glass);
            canvas.drawRect(box, frame);
            if (guides) drawGuides(canvas);
        }

        private final Paint thin = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** Squares over the whole screen, so many across; nothing when the grid is off. */
        private void drawGrid(Canvas canvas) {
            int across = GRIDS[grid];
            if (across == 0 || getWidth() == 0) return;
            float cell = getWidth() / (float) across;
            thin.setStyle(Paint.Style.STROKE);
            thin.setStrokeWidth(Math.max(1f, unit * 0.6f));
            for (int i = 1; i < across; i++) {
                // the middle line a little stronger, the rest faint
                thin.setColor(across % 2 == 0 && i == across / 2 ? 0x99FFFFFF : 0x40FFFFFF);
                canvas.drawLine(i * cell, 0, i * cell, getHeight(), thin);
            }
            int down = (int) Math.ceil(getHeight() / cell);
            for (int i = 1; i < down; i++) {
                thin.setColor(0x40FFFFFF);
                canvas.drawLine(0, i * cell, getWidth(), i * cell, thin);
            }
            thin.setColor(0x99FFFFFF);
            canvas.drawLine(0, getHeight() / 2f, getWidth(), getHeight() / 2f, thin);
        }

        /** The chosen element's centre, and lines from its corners straight across and straight down the screen. */
        private void drawGuides(Canvas canvas) {
            int colour = accent();
            thin.setStyle(Paint.Style.STROKE);
            thin.setStrokeWidth(Math.max(1f, unit * 0.8f));
            thin.setColor((colour & 0x00FFFFFF) | 0xAA000000);
            thin.setPathEffect(new android.graphics.DashPathEffect(new float[]{6f * unit, 4f * unit}, 0f));
            // from every corner: at 90° (up and down the screen) and at 180° (across it)
            canvas.drawLine(box.left, 0, box.left, getHeight(), thin);
            canvas.drawLine(box.right, 0, box.right, getHeight(), thin);
            canvas.drawLine(0, box.top, getWidth(), box.top, thin);
            canvas.drawLine(0, box.bottom, getWidth(), box.bottom, thin);
            thin.setPathEffect(null);
            // the centre: a cross through it and a dot on it
            float cx = box.exactCenterX(), cy = box.exactCenterY();
            thin.setColor(colour);
            thin.setStrokeWidth(Math.max(1f, unit));
            canvas.drawLine(cx, box.top, cx, box.bottom, thin);
            canvas.drawLine(box.left, cy, box.right, cy, thin);
            mark.setStyle(Paint.Style.FILL);
            mark.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, 4.5f * unit, mark);
            mark.setColor(colour);
            canvas.drawCircle(cx, cy, 3f * unit, mark);
            // where things are, in dp, beside the lines
            mark.setTextSize(10f * unit);
            mark.setFakeBoldText(true);
            label(canvas, Math.round(box.left / unit) + "", box.left + 3 * unit, 14 * unit);
            label(canvas, Math.round(box.right / unit) + "", box.right + 3 * unit, 14 * unit);
            label(canvas, Math.round(box.top / unit) + "", 4 * unit, box.top - 3 * unit);
            label(canvas, Math.round(box.bottom / unit) + "", 4 * unit, box.bottom + 12 * unit);
            label(canvas, Math.round(cx / unit) + ", " + Math.round(cy / unit), cx + 7 * unit, cy - 7 * unit);
        }

        private void label(Canvas canvas, String words, float x, float y) {
            float w = mark.measureText(words);
            mark.setColor(0xCC000000);
            canvas.drawRoundRect(x - 2 * unit, y - 10 * unit, x + w + 2 * unit, y + 3 * unit, 3 * unit, 3 * unit, mark);
            mark.setColor(0xFFFFFFFF);
            canvas.drawText(words, x, y, mark);
        }

        // ---- touches
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (mode == USE) return false;
            int act = e.getActionMasked();
            if (mode == MOVE) {
                if (act == MotionEvent.ACTION_DOWN) card.animate().alpha(0.2f).setDuration(120L).start();
                else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) card.animate().alpha(1f).setDuration(160L).start();
                pinch.onTouchEvent(e);
                if (pinch.isInProgress()) {
                    moved = true;
                    return true;
                }
                if (act == MotionEvent.ACTION_POINTER_UP) {
                    downX = lastX = e.getRawX();
                    downY = lastY = e.getRawY();
                    return true;
                }
            }
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = e.getRawX();
                    downY = lastY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    boolean was = moved;
                    if (Math.abs(e.getRawX() - downX) > slop || Math.abs(e.getRawY() - downY) > slop) moved = true;
                    if (mode == MOVE && moved) {
                        View v = selected();
                        if (v != null) {
                            Rule rule = ruleFor(v);
                            // one step back is the whole drag
                            if (!was) {
                                remember();
                                dragDx = rule.dx;
                                dragDy = rule.dy;
                                Rect start = boxOf(v);
                                dragLeft = start.left;
                                dragTop = start.top;
                            }
                            float wantX = e.getRawX() - downX, wantY = e.getRawY() - downY;
                            float cell = cell();
                            if (cell > 0f) {
                                // with a grid the edge sticks to its lines
                                wantX = nearestLine(dragLeft + wantX, cell) - dragLeft;
                                wantY = nearestLine(dragTop + wantY, cell) - dragTop;
                            }
                            rule.dx = dragDx + wantX;
                            rule.dy = dragDy + wantY;
                            refresh(rule);
                            invalidate();
                        }
                    }
                    lastX = e.getRawX();
                    lastY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mode == MOVE && moved) {
                        save();
                        rebuild();
                    }
                    if (mode == PICK && !moved) pick(e.getRawX(), e.getRawY());
                    return true;
                default:
                    return true;
            }
        }

        /** Everything on the screen under a point, the topmost and smallest first. */
        private void pick(float x, float y) {
            hits.clear();
            cursor = 0;
            View root = activity.getWindow().getDecorView();
            if (root instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) root;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    View child = group.getChildAt(i);
                    if (child != this) collect(child, (int) x, (int) y, 0);
                }
            }
            // the smallest first: a view that covers the whole page is rarely what was meant
            final Rect size = new Rect();
            final Map<View, Long> area = new HashMap<View, Long>();
            for (WeakReference<View> hit : hits) {
                View v = hit.get();
                area.put(v, v != null && v.getGlobalVisibleRect(size) ? (long) size.width() * size.height() : Long.MAX_VALUE);
            }
            java.util.Collections.sort(hits, (m, n) -> Long.compare(area.get(m.get()), area.get(n.get())));
            rebuild();
            invalidate();
        }

        private void collect(View v, int x, int y, int depth) {
            if (depth > 60 || v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.02f) return;
            if (!v.getGlobalVisibleRect(box) || !box.contains(x, y)) return;
            if (v instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) v;
                for (int i = group.getChildCount() - 1; i >= 0; i--) collect(group.getChildAt(i), x, y, depth + 1);
            }
            // not a container that is the whole screen: there is nothing to be had from those
            int w = getWidth(), h = getHeight();
            if (v.getWidth() >= w * 0.98f && v.getHeight() >= h * 0.98f) return;
            hits.add(new WeakReference<View>(v));
        }

        private Rule ruleFor(View v) {
            String key = keyOf(v);
            Rule rule = ruleOf(v);
            if (rule == null) {
                rule = new Rule(key, lead(v));
                rule.label = title(v);
                rules.put(key, rule);
                reindex();
            }
            tracked.put(v, rule);
            return rule;
        }

        // ---- where the card is
        private void place() {
            FrameLayout.LayoutParams at = (FrameLayout.LayoutParams) card.getLayoutParams();
            int wide = getWidth() > 0 ? getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            at.width = collapsed ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.min(wide - dp(20), dp(380));
            at.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (cardX < 0) {
                at.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                at.leftMargin = 0;
                at.topMargin = 0;
                at.bottomMargin = dp(30);
            } else {
                at.gravity = Gravity.TOP | Gravity.START;
                at.leftMargin = cardX;
                at.topMargin = cardY;
                at.bottomMargin = 0;
            }
            card.setLayoutParams(at);
            card.post(this::clampCard);
        }

        /** The card is not left where it cannot be reached. */
        private void clampCard() {
            if (cardX < 0 || card.getWidth() == 0 || getWidth() == 0) return;
            int x = Math.max(0, Math.min(cardX, getWidth() - card.getWidth()));
            int y = Math.max(0, Math.min(cardY, getHeight() - card.getHeight()));
            if (x != cardX || y != cardY) {
                cardX = x;
                cardY = y;
                FrameLayout.LayoutParams at = (FrameLayout.LayoutParams) card.getLayoutParams();
                at.leftMargin = x;
                at.topMargin = y;
                card.setLayoutParams(at);
            }
        }

        /** Holding the top of the card, or the grip, moves it. */
        private final View.OnTouchListener dragging = new View.OnTouchListener() {
            float lx, ly;
            boolean dragged;

            @Override
            public boolean onTouch(View header, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lx = e.getRawX();
                        ly = e.getRawY();
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragged && Math.abs(e.getRawX() - lx) < slop && Math.abs(e.getRawY() - ly) < slop) return true;
                        dragged = true;
                        if (cardX < 0) {
                            cardX = card.getLeft();
                            cardY = card.getTop();
                            place();
                        }
                        cardX += Math.round(e.getRawX() - lx);
                        cardY += Math.round(e.getRawY() - ly);
                        lx = e.getRawX();
                        ly = e.getRawY();
                        FrameLayout.LayoutParams at = (FrameLayout.LayoutParams) card.getLayoutParams();
                        at.gravity = Gravity.TOP | Gravity.START;
                        at.leftMargin = cardX;
                        at.topMargin = cardY;
                        at.bottomMargin = 0;
                        card.setLayoutParams(at);
                        clampCard();
                        return true;
                    default:
                        return true;
                }
            }
        };

        // ---- the card
        private void rebuild() {
            card.removeAllViews();
            card.addView(header());
            if (!collapsed) card.addView(body());
            place();
        }

        private View header() {
            LinearLayout head = new LinearLayout(activity);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setOnTouchListener(dragging);
            Icon grip = new Icon(activity, gripPath(), MUTED);
            head.addView(grip, new LinearLayout.LayoutParams(dp(22), dp(32)));
            if (collapsed) {
                // a small bar: what is wanted most without opening the card
                TextView which = button(modeName(mode), true, () -> mode(mode == PICK ? USE : PICK));
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) which.getLayoutParams();
                p.setMargins(dp(4), 0, dp(6), 0);
                head.addView(which);
            } else {
                TextView title = text(Local.of("Редактор"), 16, INK, true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                p.setMargins(dp(6), 0, dp(6), 0);
                head.addView(title, p);
            }
            head.addView(iconButton(pathOf(UNDO), canUndo(), () -> {
                undo();
                rebuild();
                invalidate();
            }));
            if (!collapsed) {
                head.addView(iconButton(pathOf(REDO), canRedo(), () -> {
                    redo();
                    rebuild();
                    invalidate();
                }));
            }
            head.addView(iconButton(pathOf(collapsed ? LESS : MORE), true, () -> {
                collapsed = !collapsed;
                rebuild();
            }));
            if (collapsed) head.addView(iconButton(pathOf(CHECK), true, this::finish));
            return head;
        }

        private void finish() {
            end();
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
        }

        private View body() {
            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            View v = selected();

            // what a touch does now
            body.addView(label(Local.of("РЕЖИМ")));
            body.addView(segments());
            body.addView(text(hint(), 12, MUTED, false, 2), spaced(0, 4));

            if (v == null) {
                body.addView(text(mode == USE ? "" : Local.of("Ничего не выбрано. Нажмите на любой элемент TikTok на экране."),
                        13, INK, false, 3), spaced(0, 10));
            } else {
                body.addView(label(Local.of("ВЫБРАННЫЙ ЭЛЕМЕНТ")));
                body.addView(tile(v));
                LinearLayout acts = row();
                acts.addView(button(Local.of("Скрыть"), false, () -> edit(r -> r.hidden = true)));
                acts.addView(sizeControl());
                body.addView(acts);
                body.addView(label(Local.of("ПОЗИЦИЯ")));
                body.addView(position(v));
                LinearLayout paints = row();
                if (v instanceof TextView) paints.addView(button(Local.of("Текст"), false, () -> colour(0)));
                if (v.getBackground() != null) paints.addView(button(Local.of("Фон"), false, () -> colour(1)));
                if (v instanceof ImageView) paints.addView(button(Local.of("Значок"), false, () -> colour(2)));
                if (paints.getChildCount() > 0) {
                    body.addView(label(Local.of("ЦВЕТ")));
                    body.addView(paints);
                }
                Rule rule = ruleOf(v);
                if (rule != null && !rule.empty()) {
                    body.addView(spacer(8));
                    TextView reset = button(Local.of("Вернуть элемент как был"), false, this::resetSelected);
                    reset.setTextColor(DANGER);
                    body.addView(reset);
                }
            }

            body.addView(label(Local.of("СЕТКА")));
            String[] grids = new String[GRIDS.length];
            grids[0] = Local.of("Выкл");
            for (int i = 1; i < GRIDS.length; i++) grids[i] = String.valueOf(GRIDS[i]);
            body.addView(choice(pathOf(GRID), grids, grid, which -> {
                grid = which;
                rebuild();
                invalidate();
            }));
            body.addView(toggle(pathOf(FOCUS), Local.of("Центр и линии от углов"), guides, () -> {
                guides = !guides;
                rebuild();
                invalidate();
            }));

            body.addView(label(Local.of("ВСЁ ПРИЛОЖЕНИЕ")));
            LinearLayout foot = row();
            int hidden = 0;
            for (Rule rule : rules.values()) if (!rule.empty()) hidden++;
            foot.addView(button(Local.of("Изменённые") + (hidden > 0 ? " (" + hidden + ")" : ""), false, this::hiddenList));
            foot.addView(button(Local.of("Сбросить всё"), false, this::askResetAll));
            body.addView(foot);
            body.addView(spacer(10));
            TextView done = button(Local.of("Готово"), true, this::finish);
            done.setPadding(dp(12), dp(11), dp(12), dp(11));
            LinearLayout.LayoutParams fill = (LinearLayout.LayoutParams) done.getLayoutParams();
            fill.width = ViewGroup.LayoutParams.MATCH_PARENT;
            fill.setMargins(0, 0, 0, 0);
            body.addView(done);

            Capped scroll = new Capped(activity, Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.62f));
            scroll.addView(body);
            return scroll;
        }

        private String modeName(int m) {
            return m == PICK ? Local.of("Выбор") : m == MOVE ? Local.of("Сдвиг") : Local.of("Нажатие");
        }

        private String hint() {
            if (mode == PICK) return Local.of("Нажмите на элемент на экране, чтобы выбрать его.");
            if (mode == MOVE) return Local.of("Тяните выбранный элемент пальцем, куда нужно.");
            return Local.of("Касания идут в TikTok: листайте и переходите на другие экраны. Правки остаются.");
        }

        /** The three modes as one control, the chosen one filled. */
        private View segments() {
            LinearLayout track = new LinearLayout(activity);
            track.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(22));
            back.setColor(0x26FFFFFF);
            track.setBackground(back);
            track.setPadding(dp(3), dp(3), dp(3), dp(3));
            String[] names = {Local.of("Выбрать"), Local.of("Сдвинуть"), Local.of("Нажимать")};
            for (int i = 0; i < names.length; i++) {
                final int which = i;
                TextView t = new TextView(activity);
                t.setText(names[i]);
                t.setTextSize(13);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setTextColor(mode == i ? 0xFFFFFFFF : INK);
                t.setPadding(dp(4), dp(9), dp(4), dp(9));
                if (mode == i) {
                    GradientDrawable on = new GradientDrawable();
                    on.setCornerRadius(dp(19));
                    on.setColor(accent());
                    t.setBackground(on);
                }
                t.setOnClickListener(x -> mode(which));
                track.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(4);
            track.setLayoutParams(p);
            return track;
        }

        /** What is chosen: its name, what it is, and a way through what lies under the finger. */
        private View tile(View v) {
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(12), dp(8), dp(8), dp(8));
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(16));
            back.setColor(0x1AFFFFFF);
            tile.setBackground(back);
            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            Rule rule = ruleOf(v);
            boolean changed = rule != null && !rule.empty();
            words.addView(text(title(v) + (changed ? "  ●" : ""), 15, INK, true));
            String name = v.getClass().getName();
            words.addView(text(name.substring(name.lastIndexOf('.') + 1) + " · " + Math.round(v.getWidth() / unit)
                    + "×" + Math.round(v.getHeight() / unit) + "dp" + (changed ? " · " + Local.of("изменён") : ""), 12, MUTED, false));
            tile.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (hits.size() > 1) {
                LinearLayout walk = new LinearLayout(activity);
                walk.setOrientation(LinearLayout.VERTICAL);
                walk.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout arrows = new LinearLayout(activity);
                arrows.addView(round(pathOf(DOWN), false, () -> step(-1)));
                arrows.addView(round(pathOf(UP), false, () -> step(1)));
                walk.addView(arrows);
                walk.addView(text(Local.of("вложенный / родитель") + " " + (cursor + 1) + "/" + hits.size(), 10, MUTED, false));
                tile.addView(walk);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(4);
            tile.setLayoutParams(p);
            return tile;
        }

        /** Where the element is, in dp, and a cross of arrows that moves it by a step or from line to line of the grid. */
        private View position(View v) {
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            Rect at = boxOf(v);
            Rule rule = ruleOf(v);
            float dx = rule == null ? 0f : rule.dx, dy = rule == null ? 0f : rule.dy;

            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(figures("X", Math.round(at.left / unit), "Y", Math.round(at.top / unit)));
            words.addView(text(Local.of("центр") + "  " + Math.round(at.exactCenterX() / unit) + ", "
                    + Math.round(at.exactCenterY() / unit) + " dp", 12, MUTED, false), spaced(0, 6));
            words.addView(text(Local.of("сдвиг") + "  " + Math.round(dx / unit) + ", " + Math.round(dy / unit) + " dp", 12, MUTED, false), spaced(0, 2));
            box.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // up on top; left, back to where it was, right; down below
            LinearLayout pad = new LinearLayout(activity);
            pad.setOrientation(LinearLayout.VERTICAL);
            pad.setGravity(Gravity.CENTER_HORIZONTAL);
            pad.addView(round(pathOf(UP), false, () -> nudge(0, -1)));
            LinearLayout middle = new LinearLayout(activity);
            middle.addView(round(pathOf(LEFT), false, () -> nudge(-1, 0)));
            middle.addView(round(pathOf(HOME), true, () -> edit(r -> {
                r.dx = 0f;
                r.dy = 0f;
            })));
            middle.addView(round(pathOf(RIGHT), false, () -> nudge(1, 0)));
            pad.addView(middle);
            pad.addView(round(pathOf(DOWN), false, () -> nudge(0, 1)));
            box.addView(pad);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(6);
            box.setLayoutParams(p);

            LinearLayout all = new LinearLayout(activity);
            all.setOrientation(LinearLayout.VERTICAL);
            all.addView(box);
            if (GRIDS[grid] == 0) {
                String[] steps = new String[STEPS.length];
                for (int i = 0; i < STEPS.length; i++) steps[i] = STEPS[i] + " dp";
                all.addView(choice(pathOf(STEP), steps, stepAt, which -> {
                    stepAt = which;
                    rebuild();
                }));
            } else {
                all.addView(text(Local.of("Сдвиг идёт по линиям сетки"), 12, MUTED, false), spaced(0, 8));
            }
            return all;
        }

        /** Two numbers with their letters, the numbers large. */
        private View figures(String a, int x, String b, int y) {
            LinearLayout line = new LinearLayout(activity);
            line.setGravity(Gravity.BOTTOM);
            line.addView(text(a, 12, MUTED, true));
            line.addView(text(" " + x, 20, INK, true));
            TextView second = text(b, 12, MUTED, true);
            second.setPadding(dp(14), 0, 0, 0);
            line.addView(second);
            line.addView(text(" " + y, 20, INK, true));
            line.addView(text(" dp", 12, MUTED, false));
            return line;
        }

        /** Where a view is on this overlay, as it is drawn. */
        private Rect boxOf(View v) {
            int[] at = new int[2];
            v.getLocationOnScreen(at);
            getLocationOnScreen(where);
            return new Rect(at[0] - where[0], at[1] - where[1], Math.round(at[0] - where[0] + v.getWidth() * v.getScaleX()),
                    Math.round(at[1] - where[1] + v.getHeight() * v.getScaleY()));
        }

        private float cell() {
            int across = GRIDS[grid];
            return across == 0 || getWidth() == 0 ? 0f : getWidth() / (float) across;
        }

        /** One press of an arrow: with a grid, the edge goes to the next line of it; without, a step. */
        private void nudge(int sx, int sy) {
            View v = selected();
            if (v == null) return;
            final float cell = cell();
            final float mx, my;
            if (cell > 0f) {
                Rect at = boxOf(v);
                mx = sx == 0 ? 0f : nextLine(at.left, sx, cell) - at.left;
                my = sy == 0 ? 0f : nextLine(at.top, sy, cell) - at.top;
            } else {
                mx = sx * STEPS[stepAt] * unit;
                my = sy * STEPS[stepAt] * unit;
            }
            edit(r -> {
                r.dx += mx;
                r.dy += my;
            });
        }

        /** The next line of the grid from a place, one way or the other; a place already on a line goes to the one after. */
        private float nextLine(float from, int way, float cell) {
            double lines = from / cell;
            double next = way > 0 ? Math.floor(lines + 0.01) + 1 : Math.ceil(lines - 0.01) - 1;
            return (float) (next * cell);
        }

        /** The nearest line of the grid to a place. */
        private float nearestLine(float from, float cell) {
            return Math.round(from / cell) * cell;
        }

        /** A row of choices with an icon before it, the chosen one filled. */
        private View choice(Path icon, String[] names, int chosen, final java.util.function.IntConsumer pick) {
            LinearLayout line = new LinearLayout(activity);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(new Icon(activity, icon, MUTED), new LinearLayout.LayoutParams(dp(20), dp(20)));
            LinearLayout track = new LinearLayout(activity);
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(20));
            back.setColor(0x26FFFFFF);
            track.setBackground(back);
            track.setPadding(dp(3), dp(3), dp(3), dp(3));
            for (int i = 0; i < names.length; i++) {
                final int which = i;
                TextView t = new TextView(activity);
                t.setText(names[i]);
                t.setTextSize(12);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setTextColor(chosen == i ? 0xFFFFFFFF : INK);
                t.setPadding(dp(2), dp(7), dp(2), dp(7));
                if (chosen == i) {
                    GradientDrawable on = new GradientDrawable();
                    on.setCornerRadius(dp(17));
                    on.setColor(accent());
                    t.setBackground(on);
                }
                t.setOnClickListener(x -> pick.accept(which));
                track.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.leftMargin = dp(10);
            line.addView(track, tp);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(8);
            line.setLayoutParams(p);
            return line;
        }

        /** A whole-width row with an icon, words and a mark for whether it is on. */
        private View toggle(Path icon, String words, boolean on, final Runnable flip) {
            LinearLayout line = new LinearLayout(activity);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(16));
            back.setColor(on ? (accent() & 0x00FFFFFF) | 0x55000000 : 0x1AFFFFFF);
            line.setBackground(back);
            line.addView(new Icon(activity, icon, on ? 0xFFFFFFFF : MUTED), new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView t = text(words, 13, INK, on);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.leftMargin = dp(10);
            line.addView(t, tp);
            line.addView(new Icon(activity, pathOf(on ? CHECK : BLANK), on ? 0xFFFFFFFF : MUTED), new LinearLayout.LayoutParams(dp(20), dp(20)));
            line.setOnClickListener(x -> flip.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(8);
            line.setLayoutParams(p);
            return line;
        }

        /** A round tonal button with an icon in it. */
        private View round(Path icon, boolean strong, final Runnable action) {
            FrameLayout b = new FrameLayout(activity);
            GradientDrawable back = new GradientDrawable();
            back.setShape(GradientDrawable.OVAL);
            back.setColor(strong ? accent() : 0x26FFFFFF);
            b.setBackground(back);
            b.addView(new Icon(activity, icon, strong ? 0xFFFFFFFF : INK), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            b.setOnClickListener(x -> action.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
            p.setMargins(dp(2), dp(2), dp(2), dp(2));
            b.setLayoutParams(p);
            return b;
        }

        /** Smaller, the size it is, larger. */
        private View sizeControl() {
            View v = selected();
            Rule rule = v == null ? null : ruleOf(v);
            float now = rule == null ? 1f : rule.scale;
            LinearLayout box = new LinearLayout(activity);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.addView(button("−", false, () -> edit(r -> r.scale = Math.max(0.3f, Math.round((r.scale - 0.1f) * 10f) / 10f))));
            TextView size = text(String.format(java.util.Locale.US, "×%.1f", now), 13, INK, false);
            size.setGravity(Gravity.CENTER);
            box.addView(size, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(button("+", false, () -> edit(r -> r.scale = Math.min(3f, Math.round((r.scale + 0.1f) * 10f) / 10f))));
            return box;
        }

        private void step(int by) {
            if (hits.isEmpty()) return;
            cursor = (cursor + by + hits.size()) % hits.size();
            rebuild();
            invalidate();
        }

        private void mode(int next) {
            mode = next;
            remembered = next;
            rebuild();
            invalidate();
        }

        private interface Change {
            void apply(Rule rule);
        }

        private void edit(Change change) {
            View v = selected();
            if (v == null) return;
            remember();
            Rule rule = ruleFor(v);
            change.apply(rule);
            refresh(rule);
            save();
            rebuild();
            invalidate();
        }

        private void resetSelected() {
            View v = selected();
            if (v == null) return;
            remember();
            Rule rule = ruleOf(v);
            if (rule != null) rules.remove(rule.key);
            if (rule != null) {
                reindex();
                refresh(rule);
                // the view that was chosen no longer answers to anything
                release(v);
                tracked.remove(v);
                save();
            }
            rebuild();
            invalidate();
        }

        private void hiddenList() {
            final List<Rule> changed = new ArrayList<Rule>();
            for (Rule rule : rules.values()) if (!rule.empty()) changed.add(rule);
            if (changed.isEmpty()) {
                Toast.makeText(activity, Local.of("Изменённых элементов нет"), Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[changed.size()];
            for (int i = 0; i < names.length; i++) {
                Rule rule = changed.get(i);
                StringBuilder what = new StringBuilder();
                if (rule.hidden) what.append(Local.of("скрыт")).append(' ');
                if (rule.dx != 0f || rule.dy != 0f) what.append(Local.of("сдвинут")).append(' ');
                if (rule.scale != 1f) what.append(Local.of("размер")).append(' ');
                if (rule.text != null || rule.fill != null || rule.icon != null) what.append(Local.of("цвет"));
                names[i] = rule.label + "  ·  " + what.toString().trim();
            }
            new AlertDialog.Builder(activity).setTitle(Local.of("Вернуть как было"))
                    .setItems(names, (d, which) -> {
                        remember();
                        Rule rule = changed.get(which);
                        rules.remove(rule.key);
                        reindex();
                        refresh(rule);
                        save();
                        rebuild();
                        invalidate();
                    }).setNegativeButton(Local.of("Закрыть"), null).show();
        }

        private void askResetAll() {
            new AlertDialog.Builder(activity).setTitle(Local.of("Сбросить все правки интерфейса?"))
                    .setMessage(Local.of("Это можно отменить кнопкой «назад» в шапке редактора."))
                    .setPositiveButton(Local.of("Сбросить"), (d, w) -> {
                        resetAll();
                        hits.clear();
                        rebuild();
                        invalidate();
                    }).setNegativeButton(Local.of("Отмена"), null).show();
        }

        // ---- colours
        private static final int[] SWATCHES = {0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
                0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A, 0xFF9E9E9E};

        private void colour(final int which) {
            final View v = selected();
            if (v == null) return;
            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(20), dp(8), dp(20), 0);
            final EditText hex = new EditText(activity);
            hex.setHint("#RRGGBB");
            hex.setSingleLine(true);
            HorizontalScrollView scroll = new HorizontalScrollView(activity);
            LinearLayout swatches = new LinearLayout(activity);
            scroll.addView(swatches);
            for (final int c : SWATCHES) {
                View dot = new View(activity);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(c);
                d.setStroke(dp(1), 0x66888888);
                dot.setBackground(d);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
                p.setMargins(0, dp(8), dp(10), dp(8));
                dot.setOnClickListener(x -> hex.setText(String.format(java.util.Locale.US, "#%06X", c & 0xFFFFFF)));
                swatches.addView(dot, p);
            }
            body.addView(scroll);
            body.addView(hex);
            final String[] labels = {Local.of("Цвет текста"), Local.of("Цвет фона"), Local.of("Цвет значка")};
            new AlertDialog.Builder(activity).setTitle(labels[which]).setView(body)
                    .setPositiveButton(Local.of("Применить"), (d, w) -> {
                        try {
                            final int c = Color.parseColor(hex.getText().toString().trim()) | 0xFF000000;
                            edit(r -> {
                                if (which == 0) r.text = c;
                                else if (which == 1) r.fill = c;
                                else r.icon = c;
                            });
                        } catch (Throwable wrong) {
                            Toast.makeText(activity, Local.of("Нужен цвет вида #RRGGBB"), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNeutralButton(Local.of("Убрать цвет"), (d, w) -> {
                        edit(r -> {
                            if (which == 0) r.text = null;
                            else if (which == 1) r.fill = null;
                            else r.icon = null;
                        });
                        // what the view was painted before is given back at once
                        release(v);
                        View again = selected();
                        Rule rule = again == null ? null : ruleOf(again);
                        if (rule != null) enforce(again, rule);
                    })
                    .setNegativeButton(Local.of("Отмена"), null).show();
        }

        // ---- the pieces of the card
        private LinearLayout row() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, 0);
            return row;
        }

        private View spacer(int height) {
            View gap = new View(activity);
            gap.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
            return gap;
        }

        private LinearLayout.LayoutParams spaced(int left, int top) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(dp(left), dp(top), 0, 0);
            return p;
        }

        /** The small capitals over a group of controls. */
        private TextView label(String words) {
            TextView t = text(words, 11, MUTED, true);
            t.setLetterSpacing(0.08f);
            t.setPadding(dp(2), dp(12), 0, 0);
            return t;
        }

        private TextView text(String words, float sp, int colour, boolean bold) {
            return text(words, sp, colour, bold, 1);
        }

        private TextView text(String words, float sp, int colour, boolean bold, int lines) {
            TextView t = new TextView(activity);
            t.setText(words);
            t.setTextSize(sp);
            t.setTextColor(colour);
            if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            if (lines == 1) t.setSingleLine(true);
            else t.setMaxLines(lines);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            return t;
        }

        private View iconButton(Path path, boolean live, final Runnable action) {
            FrameLayout box = new FrameLayout(activity);
            box.addView(new Icon(activity, path, INK), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            box.setAlpha(live ? 1f : 0.3f);
            if (live) box.setOnClickListener(x -> action.run());
            box.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
            return box;
        }

        private TextView button(String label, boolean strong, final Runnable action) {
            TextView t = new TextView(activity);
            t.setText(label);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setTextColor(strong ? 0xFFFFFFFF : INK);
            t.setPadding(dp(14), dp(9), dp(14), dp(9));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(20));
            d.setColor(strong ? accent() : 0x33FFFFFF);
            t.setBackground(d);
            t.setOnClickListener(x -> action.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, dp(6), 0);
            t.setLayoutParams(p);
            return t;
        }
    }
}
