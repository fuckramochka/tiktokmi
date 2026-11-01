package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folders for the chats of the inbox.
 *
 * A folder is a name and a list of chats. It shows as one more chip beside the ones
 * TikTok has -- Основные, Запросы, Не прочитано -- and a tap on it narrows the list
 * to the chats in it. Chats go into a folder from the menu a long press opens, where a
 * row is added; a folder is made there too, when there is none to choose.
 *
 * Nothing of TikTok's is replaced. The chips and the rows are views the mod adds to
 * what TikTok laid out; the list is narrowed by taking the rows that are not in the
 * folder down to nothing, and giving them back as they were when the folder is left.
 * A row is known by the account it is with when TikTok has said whose it is, and by
 * the name on it when it has not.
 */
final class ChatFolders {

    private ChatFolders() {}

    static final String KEY_ON = "chat_folders_on";
    private static final String KEY = "chat_folders_";

    private static final int TAG_MINE = 0x7e0c0420, TAG_HIDDEN = 0x7e0c0421, TAG_NAME = 0x7e0c0422,
            TAG_HEADER = 0x7e0c0423, TAG_CHIP = 0x7e0c0424, TAG_SWAPPED = 0x7e0c0425,
            TAG_WATCHED = 0x7e0c0426;

    // ------------------------------------------------------------ switched on or off

    static boolean enabled() {
        if (Version.LITE) return false;
        Context context = Margy.context();
        if (context == null) return false;
        try {
            return AccountAppearance.prefs(context).getBoolean(KEY_ON, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void setEnabled(boolean on) {
        Context context = Margy.context();
        if (context == null) return;
        AccountAppearance.prefs(context).edit().putBoolean(KEY_ON, on).apply();
        if (!on) active = null;
        poke();
    }

    // ------------------------------------------------------------ the folders

    private static final class Folder {
        final String id;
        String name;
        /** The chats in it: how each is known, and what it was called when it went in. */
        final LinkedHashMap<String, String> chats = new LinkedHashMap<String, String>();

        Folder(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final ArrayList<Folder> folders = new ArrayList<Folder>();
    private static String loadedFor;
    /** The folder the list is narrowed to; none when it is TikTok's own. */
    private static volatile String active;

    private static String storeKey() {
        String me = Account.id();
        return KEY + (me == null || me.length() == 0 ? "guest" : me);
    }

    private static synchronized void load() {
        String key = storeKey();
        if (key.equals(loadedFor)) return;
        Context context = Margy.context();
        if (context == null) return;
        loadedFor = key;
        folders.clear();
        active = null;
        try {
            JSONArray all = new JSONArray(AccountAppearance.prefs(context).getString(key, "[]"));
            for (int i = 0; i < all.length() && i < 40; i++) {
                JSONObject o = all.getJSONObject(i);
                Folder folder = new Folder(o.getString("id"), o.getString("name"));
                JSONArray chats = o.optJSONArray("chats");
                for (int k = 0; chats != null && k < chats.length() && k < 500; k++) {
                    JSONObject chat = chats.getJSONObject(k);
                    folder.chats.put(chat.getString("k"), chat.optString("n"));
                }
                folders.add(folder);
            }
        } catch (Throwable error) {
            Diary.note("chat folders: " + error);
        }
    }

    private static synchronized void save() {
        Context context = Margy.context();
        if (context == null) return;
        try {
            JSONArray all = new JSONArray();
            for (Folder folder : folders) {
                JSONObject o = new JSONObject();
                o.put("id", folder.id);
                o.put("name", folder.name);
                JSONArray chats = new JSONArray();
                for (Map.Entry<String, String> chat : folder.chats.entrySet()) {
                    chats.put(new JSONObject().put("k", chat.getKey()).put("n", chat.getValue()));
                }
                o.put("chats", chats);
                all.put(o);
            }
            AccountAppearance.prefs(context).edit().putString(storeKey(), all.toString()).apply();
        } catch (Throwable error) {
            Diary.note("chat folders save: " + error);
        }
    }

    private static Folder folder(String id) {
        if (id == null) return null;
        for (Folder folder : folders) if (folder.id.equals(id)) return folder;
        return null;
    }

    // ------------------------------------------------------------ whose a row is

    private static final Map<String, String> uidByName = new LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 2000;
        }
    };

    /** TikTok asked for the name of an account while it dressed a row: now it is known whose the name is. */
    static void seen(String name, String uid) {
        if (name == null || uid == null || !uid.matches("[0-9]{1,24}")) return;
        String clean = clean(name);
        if (clean.length() == 0) return;
        synchronized (uidByName) {
            uidByName.put(clean, uid);
        }
    }

    /** A name as it is compared: without the marks that say which way it reads, and without the blanks round it. */
    private static String clean(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '‎' || c == '‏' || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩')) continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    private static int nameId = -1;

    private static int nameId(Context context) {
        if (nameId == -1) nameId = context.getResources().getIdentifier("user_name", "id", context.getPackageName());
        return nameId;
    }

    /** The name written on a row of a chat, or none when it is not a chat. */
    private static TextView nameOf(View row) {
        Object known = row.getTag(TAG_NAME);
        if (known instanceof TextView) return (TextView) known;
        int id = nameId(row.getContext());
        if (id == 0) return null;
        View found = row.findViewById(id);
        if (found instanceof TextView) {
            row.setTag(TAG_NAME, found);
            return (TextView) found;
        }
        return null;
    }

    /** How a row is known: by the account, or by the name when the account is not known. Null for a row that is no chat. */
    private static String keyOf(View row) {
        TextView name = nameOf(row);
        if (name == null || name.getText() == null) return null;
        return keyOfName(name.getText().toString());
    }

    /** How a chat is known from the name written on it, wherever it is written. */
    static String keyOfName(String written) {
        if (written == null) return null;
        String text = clean(written);
        if (text.length() == 0) return null;
        String uid;
        synchronized (uidByName) {
            uid = uidByName.get(text);
            if (uid == null) {
                // a name with a mark added to it (a badge, an emoji): the longest known name it begins
                // with, when what follows is no word -- "a, b, c" of a group is not the account "a"
                int best = 0;
                for (Map.Entry<String, String> known : uidByName.entrySet()) {
                    String candidate = known.getKey();
                    if (candidate.length() > best && candidate.length() >= 2 && text.startsWith(candidate)
                            && marksOnly(text, candidate.length())) {
                        best = candidate.length();
                        uid = known.getValue();
                    }
                }
            }
        }
        return uid != null ? uid : "n:" + text;
    }

    /** True when nothing but blanks and signs stands in the text from the given place on. */
    private static boolean marksOnly(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ',') return false;
        }
        return true;
    }

    // ------------------------------------------------------------ for the share sheet

    /** The folders there are, each as its id and its name; none when the folders are switched off. */
    static List<String[]> list() {
        List<String[]> out = new ArrayList<String[]>();
        if (!enabled()) return out;
        load();
        synchronized (ChatFolders.class) {
            for (Folder folder : folders) out.add(new String[] {folder.id, folder.name});
        }
        return out;
    }

    /** How many chats a folder has. */
    static synchronized int size(String id) {
        Folder folder = folder(id);
        return folder == null ? 0 : folder.chats.size();
    }

    /**
     * Whether the person whose name is written is in the folder: by the account when
     * it is known whose the name is, and else by the name the chat had when it went in.
     */
    static synchronized boolean holds(String id, String written) {
        Folder folder = folder(id);
        if (folder == null || written == null) return false;
        String key = keyOfName(written);
        if (key != null && folder.chats.containsKey(key)) return true;
        String text = clean(written);
        for (String name : folder.chats.values()) {
            if (name == null || name.length() < 1) continue;
            String known = clean(name);
            if (known.length() > 0 && (text.equals(known) || (text.startsWith(known) && marksOnly(text, known.length()))
                    || (known.startsWith(text) && text.length() >= 1 && marksOnly(known, text.length())))) return true;
        }
        return false;
    }

    private static String labelOf(View row) {
        TextView name = nameOf(row);
        return name == null || name.getText() == null ? "" : clean(name.getText().toString());
    }

    // ------------------------------------------------------------ attaching

    static void attach(final Activity activity) {
        if (Version.LITE) return;
        try {
            final View decor = activity.getWindow().getDecorView();
            if (decor.getTag(TAG_MINE) != null) return;
            decor.setTag(TAG_MINE, Boolean.TRUE);
            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    try {
                        frame(activity);
                    } catch (Throwable error) {
                        Diary.detail("chat folders: " + error);
                        Diag.say("chat folders frame: " + android.util.Log.getStackTraceString(error));
                    }
                    return true;
                }
            });
            watchTouches(activity);
        } catch (Throwable error) {
            Diary.note("chat folders attach: " + error);
        }
    }

    /** Looks again at once, whatever the pace. */
    private static void poke() {
        lastPass = 0;
        Activity activity = Watch.here();
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override public void run() {
                decor.invalidate();
            }
        });
    }

    // ------------------------------------------------------------ every frame

    private static long lastPass, menuFrom, menuUntil;
    private static WeakReference<ViewGroup> inbox = new WeakReference<ViewGroup>(null);
    private static String pressedKey = "", pressedLabel = "";

    private static ViewGroup inbox() {
        ViewGroup list = inbox.get();
        return list != null && list.isAttachedToWindow() ? list : null;
    }

    private static void frame(Activity activity) {
        long now = SystemClock.uptimeMillis();
        boolean gated = now >= menuFrom && now <= menuUntil;
        if (!enabled()) {
            if (loadedFor != null && (active != null || lastPass != -1)) {
                active = null;
                ViewGroup list = inbox();
                if (list != null) {
                    sweep(list);
                    View strip = stripOf(list);
                    if (strip != null) chips(activity, (HorizontalScrollView) strip);
                }
                lastPass = -1;
            }
            return;
        }
        ViewGroup list = inbox();
        if (active != null && list != null) {
            long started = SystemClock.uptimeMillis();
            sweep(list);
            long took = SystemClock.uptimeMillis() - started;
            if (Diag.ON && (diagFrames < 12 || took > 4)) {
                diagFrames++;
                Diag.say("folder frame +" + (started - diagFrom) + " ms, sweep " + took + " ms, children " + list.getChildCount() + ", rows " + rows(list));
            }
        }
        if (lastPass != -1 && now - lastPass < (gated ? 0L : 150L)) return;
        lastPass = now;
        load();
        View decor = activity.getWindow().getDecorView();
        look(activity, decor);
        overlays(activity, decor);
    }

    /** What a long press opens: looked for while it is being waited for. */
    private static void overlays(Activity activity, View decor) {
        long now = SystemClock.uptimeMillis();
        if (now >= menuFrom && now <= menuUntil) menu(activity, decor);
    }

    /**
     * The menu comes a few frames after the touch, and when it has come nothing
     * is drawn for a while, so it is looked for on a clock as well as on every frame.
     */
    private static void waitFor(final Activity activity) {
        final View decor = activity.getWindow().getDecorView();
        decor.postDelayed(new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                if (!(now >= menuFrom && now <= menuUntil)) return;
                try {
                    overlays(activity, decor);
                } catch (Throwable error) {
                }
                waitFor(activity);
            }
        }, 90L);
    }

    private static boolean isRecycler(View view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("androidx.recyclerview.widget.RecyclerView")) return true;
        }
        return false;
    }

    private static ViewGroup recyclerAbove(View view) {
        Object up = view.getParent();
        for (int i = 0; i < 12 && up instanceof View; i++) {
            View candidate = (View) up;
            if (candidate instanceof ViewGroup && isRecycler(candidate)) return (ViewGroup) candidate;
            up = candidate.getParent();
        }
        return null;
    }

    /** The inbox is the list whose rows carry a name and which has the strip of chips in it. */
    private static void look(Activity activity, View decor) {
        int id = nameId(activity);
        View any = id == 0 ? null : decor.findViewById(id);
        if (any == null) return;
        ViewGroup list = recyclerAbove(any);
        if (list == null) return;
        View strip = stripOf(list);
        if (strip == null) return;
        if (inbox.get() != list) {
            inbox = new WeakReference<ViewGroup>(list);
            Diag.adapter(list);
            // back to an inbox that was left in a folder: its new list is narrowed too
            if (active != null) {
                final String chosen = active;
                final ViewGroup shownList = list;
                list.post(new Runnable() {
                    @Override public void run() {
                        if (chosen.equals(active)) Narrowed.narrow(shownList, chosen);
                    }
                });
            }
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) {
                    if (active != null) shape(child);
                }

                @Override public void onChildViewRemoved(View parent, View child) {}
            });
        }
        chips(activity, (HorizontalScrollView) strip);
    }

    /** The strip of chips: a horizontal scroller with a row of buttons in it, somewhere in the list's first rows. */
    private static View stripOf(ViewGroup list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (nameOf(row) != null) continue;
            View found = strip(row, 0);
            if (found != null) return found;
        }
        return null;
    }

    private static View strip(View view, int depth) {
        if (view == null || depth > 8) return null;
        if (view instanceof HorizontalScrollView) {
            HorizontalScrollView scroller = (HorizontalScrollView) view;
            if (scroller.getChildCount() == 1 && scroller.getChildAt(0) instanceof LinearLayout) {
                LinearLayout holder = (LinearLayout) scroller.getChildAt(0);
                int buttons = 0;
                for (int i = 0; i < holder.getChildCount(); i++) {
                    View child = holder.getChildAt(i);
                    if (child instanceof LinearLayout && (child.isClickable() || child.getTag(TAG_CHIP) != null)) buttons++;
                }
                if (buttons >= 2) {
                    return scroller;
                }
            }
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = strip(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ narrowing the list

    private static boolean header(View row) {
        if (row.getTag(TAG_HEADER) != null) return true;
        if (nameOf(row) != null) return false;
        boolean is = holdsHeader(row, 0);
        if (is) row.setTag(TAG_HEADER, Boolean.TRUE);
        return is;
    }

    /** What stands above the chats -- the stories, the chips -- is not a chat and is not taken away. */
    private static boolean holdsHeader(View view, int depth) {
        if (depth > 6) return false;
        if (view instanceof HorizontalScrollView || isRecycler(view)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (holdsHeader(group.getChildAt(i), depth + 1)) return true;
        }
        return false;
    }

    private static void sweep(ViewGroup list) {
        for (int i = 0; i < list.getChildCount(); i++) shape(list.getChildAt(i));
    }

    /** Takes a row down to nothing when the folder does not want it, and gives it back as it was when it does. */
    private static void shape(View row) {
        Folder folder = folder(active);
        boolean hide = false;
        // with TikTok's list itself narrowed, every row there is is wanted
        if (folder != null && !Narrowed.on() && !header(row)) {
            String key = keyOf(row);
            hide = key == null || !folder.chats.containsKey(key);
        }
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        Object kept = row.getTag(TAG_HIDDEN);
        if (hide) {
            if (kept == null) {
                row.setTag(TAG_HIDDEN, new int[] {margins.height, margins.topMargin, margins.bottomMargin, row.getVisibility()});
            }
            if (margins.height != 0 || margins.topMargin != 0 || margins.bottomMargin != 0) {
                margins.height = 0;
                margins.topMargin = 0;
                margins.bottomMargin = 0;
                row.setLayoutParams(margins);
            }
            // A row that is still as tall as it was stays drawn until the list has been laid out again:
            // made invisible at once, it would leave a gap of its old height while TikTok is still busy.
            boolean laid = row.getHeight() == 0 || System.currentTimeMillis() >= settleUntil;
            if (laid && row.getVisibility() != View.INVISIBLE) row.setVisibility(View.INVISIBLE);
        } else if (kept instanceof int[]) {
            int[] was = (int[]) kept;
            margins.height = was[0];
            margins.topMargin = was[1];
            margins.bottomMargin = was[2];
            row.setVisibility(was[3]);
            row.setTag(TAG_HIDDEN, null);
            row.setLayoutParams(margins);
        }
    }

    private static volatile long settleUntil;

    private static long diagFrom;
    private static int diagFrames;

    private static void narrow(String id) {
        diagFrom = SystemClock.uptimeMillis();
        diagFrames = 0;
        ViewGroup known = inbox();
        Diag.say("folder narrow to " + id + ": children " + (known == null ? -1 : known.getChildCount()) + ", rows " + (known == null ? -1 : rows(known)));
        active = id;
        settleUntil = System.currentTimeMillis() + 1500L;
        final ViewGroup list = inbox();
        if (list != null && Narrowed.narrow(list, id)) {
            View strip = stripOf(list);
            Activity activity = Watch.here();
            if (strip instanceof HorizontalScrollView && activity != null) chips(activity, (HorizontalScrollView) strip);
            sweep(list);
        } else if (list != null) {
            // The chips change now and are drawn on this frame. The list is narrowed on the next:
            // taking the other rows down to nothing makes TikTok make and fill the rows that were
            // not on the screen, which takes a good part of a second, and nothing is drawn meanwhile.
            View strip = stripOf(list);
            Activity activity = Watch.here();
            if (strip instanceof HorizontalScrollView && activity != null) chips(activity, (HorizontalScrollView) strip);
            final String chosen = id;
            list.post(new Runnable() {
                @Override public void run() {
                    if (active == null || !active.equals(chosen)) return;
                    sweep(list);
                    scrollTo(list, 0);
                    fill(list, chosen, -1, 0, 0);
                }
            });
        }
        poke();
        Folder folder = folder(id);
        if (folder != null && folder.chats.isEmpty()) {
            tell(Local.of("В папке пока пусто. Удерживайте диалог и выберите «Добавить в папку»"));
        }
    }

    private static void scrollTo(ViewGroup list, int position) {
        try {
            list.getClass().getMethod("scrollToPosition", int.class).invoke(list, position);
        } catch (Throwable ignored) {
        }
    }

    /** How many rows the list has, loaded or not yet shown; -1 when it cannot be asked. */
    private static int rows(ViewGroup list) {
        try {
            Object adapter = list.getClass().getMethod("getAdapter").invoke(list);
            if (adapter == null) return -1;
            Object count = Class.forName("androidx.recyclerview.widget.RecyclerView$Adapter").getMethod("getItemCount").invoke(adapter);
            return count instanceof Integer ? (Integer) count : -1;
        } catch (Throwable error) {
            return -1;
        }
    }

    /**
     * A list whose other rows have been taken down to nothing does not scroll, and TikTok loads
     * the next chats only when it does: the chats of the folder that are further down would not
     * come. So the list is taken to its end, and back, until every chat of the folder has been
     * seen or there are no more to load.
     */
    private static void fill(final ViewGroup list, final String id, final int before, final int quiet, final int steps) {
        // the first look comes as soon as the list has been laid out: usually every chat is there already
        list.postDelayed(new Runnable() {
            @Override public void run() {
                Folder folder = folder(active);
                if (folder == null || !folder.id.equals(id) || !list.isAttachedToWindow()) return;
                int found = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    String key = keyOf(list.getChildAt(i));
                    if (key != null && folder.chats.containsKey(key)) found++;
                }
                int now = rows(list);
                int still = now == before ? quiet + 1 : 0;
                Diag.say("folder fill step " + steps + " +" + (SystemClock.uptimeMillis() - diagFrom) + " ms: found " + found + " of " + folder.chats.size() + ", rows " + now + ", children " + list.getChildCount());
                if (found >= folder.chats.size() || now < 0 || still >= 3 || steps >= 8) {
                    scrollTo(list, 0);
                    return;
                }
                scrollTo(list, now - 1);
                fill(list, id, now, still, steps + 1);
            }
        }, steps == 0 ? 120L : 350L);
    }

    // ------------------------------------------------------------ narrowing TikTok's list itself

    /**
     * The inbox's list is TikTok's own kind of ListAdapter (X.1Hw7): what it shows is a list given
     * to its differ (X.1Hw8), which works out what changed and tells the list. A folder hands the
     * differ the list without the chats that are not in it, so TikTok makes and fills only the rows
     * of the folder. Taking the other rows down to nothing in place made it make and fill every
     * chat it had loaded, a hundred and more, which held the screen still for two seconds.
     *
     * TikTok hands the differ a new list whenever something changes. A listener of the differ hears
     * of it: the newest whole list is kept, and the folder's part of it is handed over again. Leaving
     * the folder hands back the newest whole list. Written for TikTok 47.2.41's names; if they are not
     * there, the folder falls back to taking rows down to nothing.
     */
    private static final class Narrowed {
        static Object differ;
        static java.lang.reflect.Method submit;
        static java.lang.reflect.Field backing;
        /** The newest list TikTok gave, and the one handed over for the folder. */
        static List<?> whole, ours;
        static WeakReference<ViewGroup> shown = new WeakReference<ViewGroup>(null);
        private static final Map<Class<?>, java.lang.reflect.Field> sessions = new java.util.HashMap<Class<?>, java.lang.reflect.Field>();
        private static java.lang.reflect.Field model;

        static boolean on() {
            return ours != null && differ != null;
        }

        private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field found = c.getDeclaredField(name);
                    found.setAccessible(true);
                    return found;
                } catch (NoSuchFieldException next) {
                }
            }
            throw new NoSuchFieldException(name);
        }

        /** Finds the differ of the list, and listens to it once. */
        private static boolean bind(ViewGroup list) throws Exception {
            Object adapter = list.getClass().getMethod("getAdapter").invoke(list);
            if (adapter == null) return false;
            Class<?> base = null;
            for (Class<?> c = adapter.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals("X.1Hw7")) base = c;
            if (base == null) return false;
            Object found = field(base, "LL").get(adapter);
            if (found == null) return false;
            if (found == differ) return true;
            Class<?> type = found.getClass();
            java.lang.reflect.Method giving = type.getDeclaredMethod("LIZIZ", List.class, Runnable.class);
            giving.setAccessible(true);
            java.lang.reflect.Field held = field(type, "LJ");
            // the differ's own executor is asked to work out each new list: a new whole list is
            // narrowed to the folder there, before it is ever shown
            Object config = field(type, "LIZIZ").get(found);
            java.lang.reflect.Field executing = field(config.getClass(), "LIZ");
            Object executor = executing.get(config);
            if (executor instanceof java.util.concurrent.Executor && !(executor instanceof Gate))
                executing.set(config, new Gate((java.util.concurrent.Executor) executor));
            Object listeners = field(type, "LIZLLL").get(found);
            Class<?> face = Class.forName("X.16bj", false, type.getClassLoader());
            Object ear = Proxy.newProxyInstance(face.getClassLoader(), new Class<?>[] {face}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("equals")) return proxy == args[0];
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("toString")) return "MargyT folders";
                if (name.equals("LIZ")) committed();
                return null;
            });
            @SuppressWarnings("unchecked") List<Object> ears = (List<Object>) listeners;
            ears.add(ear);
            differ = found;
            submit = giving;
            backing = held;
            whole = null;
            ours = null;
            shown = new WeakReference<ViewGroup>(list);
            return true;
        }

        /** Hands the differ's work on, after narrowing a new whole list of TikTok's to the folder. */
        private static final class Gate implements java.util.concurrent.Executor {
            private final java.util.concurrent.Executor real;

            Gate(java.util.concurrent.Executor real) {
                this.real = real;
            }

            @Override public void execute(Runnable work) {
                try {
                    if (active != null && ours != null) intercept(work, active);
                } catch (Throwable error) {
                    Diag.say("folders gate: " + error);
                }
                real.execute(work);
            }
        }

        /** In the differ's work, the list it is going to: TikTok's whole list becomes the folder's part of it. */
        private static void intercept(Runnable work, String id) throws Exception {
            Object current = backing.get(differ);
            for (Class<?> c = work.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field one : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(one.getModifiers())) continue;
                    one.setAccessible(true);
                    Object value = one.get(work);
                    if (!(value instanceof List) || value == current || value == ours) continue;
                    whole = (List<?>) value;
                    List<Object> part = part(id, whole);
                    ours = part;
                    one.set(work, part);
                    return;
                }
            }
        }

        /** The differ took a list: if it was not the folder's, it is TikTok's newest whole list. */
        private static void committed() {
            try {
                Object now = backing.get(differ);
                if (now == null || now == ours) return;
                whole = (List<?>) now;
                if (ours == null || active == null) return;
                ViewGroup list = shown.get();
                if (list != null) list.post(new Runnable() {
                    @Override public void run() {
                        if (active != null && ours != null) give(active);
                    }
                });
            } catch (Throwable error) {
                Diag.say("folders committed: " + error);
            }
        }

        /** The name on a chat of the list, or null for what is not a chat. */
        private static String chatName(Object wrapper) throws Exception {
            if (model == null) model = field(wrapper.getClass(), "LIZ");
            Object item = model.get(wrapper);
            if (item == null) return null;
            Class<?> kind = item.getClass();
            java.lang.reflect.Field session;
            if (sessions.containsKey(kind)) {
                session = sessions.get(kind);
            } else {
                // a person's chat keeps its card in an X.0T89, a group's in an X.0T8A: both have the
                // name written in LLJJJ
                session = null;
                for (Class<?> c = kind; c != null && session == null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field one : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(one.getModifiers()) || one.getType().isPrimitive()) continue;
                        if (!one.getType().getName().startsWith("X.")) continue;
                        try {
                            if (field(one.getType(), "LLJJJ").getType() != String.class) continue;
                        } catch (NoSuchFieldException none) {
                            continue;
                        }
                        one.setAccessible(true);
                        session = one;
                        break;
                    }
                }
                sessions.put(kind, session);
            }
            if (session == null) return null;
            Object info = session.get(item);
            if (info == null) return null;
            Object name = field(info.getClass(), "LLJJJ").get(info);
            return name instanceof String ? (String) name : null;
        }

        /**
         * What stays over a folder's chats: the strip of stories and the chips. Everything else that is
         * not a chat (activity, system notices, TikTok Shop, the spinner of the next page) stays out.
         */
        private static boolean header(Object wrapper) throws Exception {
            if (model == null) model = field(wrapper.getClass(), "LIZ");
            Object item = model.get(wrapper);
            if (item == null) return false;
            String kind = item.getClass().getName();
            return kind.equals("com.ss.android.ugc.aweme.inbox.widget.multi.InboxHorizontalTopListCombinePod") || kind.equals("X.0TD7");
        }

        /** The stories, the chips and the folder's chats of a whole list. */
        private static List<Object> part(String id, List<?> from) throws Exception {
            List<Object> out = new ArrayList<Object>();
            for (Object wrapper : from) {
                if (wrapper == null) continue;
                String name = chatName(wrapper);
                if (name == null ? header(wrapper) : holds(id, name)) out.add(wrapper);
            }
            return out;
        }

        private static void give(String id) {
            try {
                List<?> from = whole;
                if (from == null) return;
                List<Object> out = part(id, from);
                ours = out;
                final ViewGroup list = shown.get();
                submit.invoke(differ, out, new Runnable() {
                    @Override public void run() {
                        if (list != null) scrollTo(list, 0);
                    }
                });
            } catch (Throwable error) {
                Diag.say("folders give: " + android.util.Log.getStackTraceString(error));
                restore();
            }
        }

        /** The list is narrowed to the folder; false when TikTok's list is not of the kind known. */
        static boolean narrow(ViewGroup list, String id) {
            try {
                if (!bind(list)) return false;
                if (id == null) {
                    restore();
                    return true;
                }
                Object now = backing.get(differ);
                if (now != null && now != ours) whole = (List<?>) now;
                if (whole == null) return false;
                ours = new ArrayList<Object>();
                give(id);
                return ours != null;
            } catch (Throwable error) {
                Diag.say("folders narrow: " + android.util.Log.getStackTraceString(error));
                ours = null;
                return false;
            }
        }

        /** The whole list goes back. */
        static void restore() {
            List<?> back = whole;
            boolean was = ours != null;
            ours = null;
            if (!was || back == null || differ == null) return;
            try {
                submit.invoke(differ, back, null);
            } catch (Throwable error) {
                Diag.say("folders restore: " + error);
            }
        }
    }

    // ------------------------------------------------------------ the chips

    private static final class Look {
        Drawable.ConstantState background;
        int ink;
    }

    private static Look selectedLook, idleLook;

    private static TextView firstText(View view, int depth) {
        if (view instanceof TextView) return (TextView) view;
        if (depth > 3 || !(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = firstText(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static Look lookOf(View chip) {
        Look look = new Look();
        Drawable background = chip.getBackground();
        look.background = background == null ? null : background.getConstantState();
        TextView text = firstText(chip, 0);
        look.ink = text == null ? 0xFF000000 : text.getCurrentTextColor();
        return look;
    }

    private static void ink(View view, int colour) {
        if (view instanceof TextView) ((TextView) view).setTextColor(colour);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) ink(group.getChildAt(i), colour);
        }
    }

    /** Puts a look on a chip: its background, and the colour of the words. */
    private static void dress(View chip, Look look) {
        if (look == null) return;
        if (look.background != null) chip.setBackground(look.background.newDrawable().mutate());
        ink(chip, look.ink);
    }

    private static void chips(final Activity activity, HorizontalScrollView strip) {
        LinearLayout holder = (LinearLayout) strip.getChildAt(0);
        ArrayList<LinearLayout> theirs = new ArrayList<LinearLayout>(), mine = new ArrayList<LinearLayout>();
        for (int i = 0; i < holder.getChildCount(); i++) {
            View child = holder.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            if (child.getTag(TAG_CHIP) != null) mine.add((LinearLayout) child);
            else theirs.add((LinearLayout) child);
        }
        if (theirs.isEmpty()) return;

        // what a chosen chip and a chip that is not look like, as TikTok has them now
        boolean swapped = false;
        for (LinearLayout chip : theirs) if (chip.getTag(TAG_SWAPPED) != null) swapped = true;
        if (!swapped && active == null) learn(theirs);

        // the chips of TikTok, taught to leave the folder when one is touched
        for (LinearLayout chip : theirs) {
            if (chip.getTag(TAG_WATCHED) != null) continue;
            chip.setTag(TAG_WATCHED, Boolean.TRUE);
            chip.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && active != null) leave();
                    return false;
                }
            });
        }

        // the folders: one chip each, after TikTok's -- none when the folders are switched off
        List<Folder> wanted = enabled() ? new ArrayList<Folder>(folders) : new ArrayList<Folder>();
        boolean same = mine.size() == wanted.size();
        for (int i = 0; same && i < mine.size(); i++) {
            Folder folder = wanted.get(i);
            Object tag = mine.get(i).getTag(TAG_CHIP);
            TextView text = firstText(mine.get(i), 0);
            same = folder.id.equals(tag) && text != null && folder.name.contentEquals(text.getText());
        }
        if (!same) {
            for (LinearLayout chip : mine) holder.removeView(chip);
            mine.clear();
            LinearLayout proto = prototype(theirs);
            if (proto != null) {
                // right after the first chip of TikTok's: the strip goes on past the edge of the screen,
                // and what is put at its end would have to be looked for
                int at = Math.min(holder.getChildCount(), holder.indexOfChild(theirs.get(0)) + 1);
                for (Folder folder : wanted) {
                    LinearLayout chip = chip(activity, proto, folder);
                    holder.addView(chip, at++);
                    mine.add(chip);
                }
            }
        }

        // while a folder is chosen, the chip of TikTok that looked chosen does not
        for (LinearLayout chip : theirs) {
            Object saved = chip.getTag(TAG_SWAPPED);
            // TikTok draws its chips again now and then, its own chosen one chosen again: it is put back each time
            if (active != null && selectedLook != null && idleLook != null && inkOf(chip) == selectedLook.ink) {
                if (saved == null) chip.setTag(TAG_SWAPPED, lookOf(chip));
                dress(chip, idleLook);
            } else if (active == null && saved instanceof Look) {
                dress(chip, (Look) saved);
                chip.setTag(TAG_SWAPPED, null);
            }
        }
        for (LinearLayout chip : mine) {
            boolean chosen = chip.getTag(TAG_CHIP).equals(active);
            Look look = chosen ? selectedLook : idleLook;
            if (look != null) dress(chip, look);
            else if (chosen) {
                GradientDrawable fallback = new GradientDrawable();
                fallback.setCornerRadius(chip.getHeight() / 2f);
                fallback.setColor((Accent.colour() & 0xFFFFFF) | 0x33000000);
                chip.setBackground(fallback);
                ink(chip, Accent.colour() | 0xFF000000);
            }
        }
    }

    private static int inkOf(View chip) {
        TextView text = firstText(chip, 0);
        return text == null ? 0 : text.getCurrentTextColor();
    }

    /** The one chip that is chosen is the one whose words are not the colour of the others'. */
    private static void learn(List<LinearLayout> theirs) {
        if (theirs.size() < 2) return;
        LinearLayout odd = null;
        int oddCount = 0;
        for (LinearLayout chip : theirs) {
            int same = 0;
            for (LinearLayout other : theirs) if (inkOf(other) == inkOf(chip)) same++;
            if (same == 1) {
                odd = chip;
                oddCount++;
            }
        }
        for (LinearLayout chip : theirs) {
            if (chip != odd || oddCount != 1) {
                idleLook = lookOf(chip);
                break;
            }
        }
        if (oddCount == 1 && odd != null) selectedLook = lookOf(odd);
    }

    /** A chip of TikTok's with one piece of text in it, which a new chip is made after. */
    private static LinearLayout prototype(List<LinearLayout> theirs) {
        LinearLayout best = null;
        for (LinearLayout chip : theirs) {
            if (firstText(chip, 0) == null) continue;
            if (best == null || chip.getChildCount() < best.getChildCount()) best = chip;
        }
        return best;
    }

    private static LinearLayout chip(final Activity activity, LinearLayout proto, final Folder folder) {
        Context context = proto.getContext();
        LinearLayout chip = new LinearLayout(context);
        chip.setTag(TAG_CHIP, folder.id);
        chip.setOrientation(proto.getOrientation());
        chip.setGravity(proto.getGravity());
        chip.setPadding(proto.getPaddingLeft(), proto.getPaddingTop(), proto.getPaddingRight(), proto.getPaddingBottom());
        ViewGroup.LayoutParams params = proto.getLayoutParams();
        LinearLayout.LayoutParams place = params instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) params)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // TikTok's chips may be given the width they were laid out at; a folder is as wide as its name
        place.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        place.weight = 0f;
        chip.setLayoutParams(place);
        Drawable background = proto.getBackground();
        if (background != null && background.getConstantState() != null) {
            chip.setBackground(background.getConstantState().newDrawable().mutate());
        }
        TextView sample = firstText(proto, 0);
        TextView text = new TextView(context);
        text.setText(folder.name);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, sample.getTextSize());
        text.setTypeface(sample.getTypeface());
        text.setTextColor(sample.getCurrentTextColor());
        text.setGravity(sample.getGravity());
        text.setSingleLine(true);
        text.setIncludeFontPadding(sample.getIncludeFontPadding());
        text.setLetterSpacing(sample.getLetterSpacing());
        text.setTextScaleX(sample.getTextScaleX());
        text.getPaint().setFakeBoldText(sample.getPaint().isFakeBoldText());
        text.setPadding(sample.getPaddingLeft(), sample.getPaddingTop(), sample.getPaddingRight(), sample.getPaddingBottom());
        Fonts.apply(text);
        ViewGroup.LayoutParams textParams = sample.getLayoutParams();
        LinearLayout.LayoutParams inside = textParams instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) textParams)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inside.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        inside.weight = 0f;
        chip.addView(text, inside);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(folder.name);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                narrow(folder.id.equals(active) ? null : folder.id);
            }
        });
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                manage(activity, folder);
                return true;
            }
        });
        return chip;
    }

    private static void tell(String words) {
        Context context = Margy.context();
        if (context != null) android.widget.Toast.makeText(context, words, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** The strip goes back to its start, where the folders are. */
    private static void scrollToStart() {
        final ViewGroup list = inbox();
        if (list == null) return;
        list.postDelayed(new Runnable() {
            @Override public void run() {
                View strip = stripOf(list);
                if (strip instanceof HorizontalScrollView) ((HorizontalScrollView) strip).smoothScrollTo(0, 0);
            }
        }, 250L);
    }

    private static void leave() {
        active = null;
        Narrowed.restore();
        ViewGroup list = inbox();
        if (list != null) sweep(list);
        poke();
    }

    // ------------------------------------------------------------ touches

    private static void watchTouches(final Activity activity) {
        Window window = activity.getWindow();
        final Window.Callback base = window.getCallback();
        if (base == null) return;
        window.setCallback((Window.Callback) Proxy.newProxyInstance(base.getClass().getClassLoader(),
                new Class<?>[] {Window.Callback.class}, (proxy, method, args) -> {
                    if (method.getName().equals("dispatchTouchEvent") && args != null && args.length == 1
                            && args[0] instanceof MotionEvent) {
                        try {
                            touched(activity, (MotionEvent) args[0]);
                        } catch (Throwable ignored) {
                        }
                        try {
                            VideoActions.touched(activity, (MotionEvent) args[0]);
                        } catch (Throwable ignored) {
                        }
                    }
                    try {
                        return method.invoke(base, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause() == null ? error : error.getCause();
                    }
                }));
    }

    private static boolean inside(View view, float x, float y) {
        if (view == null || !view.isShown()) return false;
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return x >= at[0] && x < at[0] + view.getWidth() && y >= at[1] && y < at[1] + view.getHeight();
    }

    private static void touched(Activity activity, MotionEvent event) {
        if (!enabled()) return;
        int action = event.getActionMasked();
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedKey = "";
            pressedLabel = "";
            ViewGroup list = inbox();
            if (list != null && inside(list, event.getRawX(), event.getRawY())) {
                for (int i = 0; i < list.getChildCount(); i++) {
                    View row = list.getChildAt(i);
                    if (!inside(row, event.getRawX(), event.getRawY())) continue;
                    String key = keyOf(row);
                    if (key != null) {
                        pressedKey = key;
                        pressedLabel = labelOf(row);
                        long hold = ViewConfiguration.getLongPressTimeout();
                        menuFrom = now + hold - 80L;
                        menuUntil = now + hold + 2600L;
                        waitFor(activity);
                    }
                    break;
                }
            }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // let go before the menu opened: it was a tap
            if (now < menuFrom) menuUntil = 0L;
        }
    }

    // ------------------------------------------------------------ the menu of a long press

    /** The menu a long press opens: a column of buttons, each with words on it, that is not in the list itself. */
    private static LinearLayout menuColumn(View view, int depth) {
        if (view == null || depth > 45 || isRecycler(view)) return null;
        if (view instanceof LinearLayout && view.getTag(TAG_MINE) == null) {
            LinearLayout column = (LinearLayout) view;
            if (column.getOrientation() == LinearLayout.VERTICAL && column.getChildCount() >= 4) {
                boolean all = true;
                for (int i = 0; i < column.getChildCount() && all; i++) {
                    View child = column.getChildAt(i);
                    all = child instanceof LinearLayout && child.isClickable() && firstText(child, 0) != null;
                }
                if (all) return column;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = menuColumn(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void menu(final Activity activity, View decor) {
        if (pressedKey.length() == 0) return;
        LinearLayout column = menuColumn(decor, 0);
        if (column == null) return;
        column.setTag(TAG_MINE, Boolean.TRUE);
        // the row to copy: one that has a picture, so that the words stand where the others' do
        LinearLayout proto = null;
        for (int i = 0; i < column.getChildCount() && proto == null; i++) {
            LinearLayout candidate = (LinearLayout) column.getChildAt(i);
            for (int k = 0; k < candidate.getChildCount(); k++) {
                if (candidate.getChildAt(k) instanceof ImageView) {
                    proto = candidate;
                    break;
                }
            }
        }
        if (proto == null) proto = (LinearLayout) column.getChildAt(column.getChildCount() - 1);
        final String key = pressedKey, label = pressedLabel;
        LinearLayout row = copy(proto, Local.of("Добавить в папку"), "folder", new View.OnClickListener() {
            @Override public void onClick(View v) {
                activity.onBackPressed();
                pick(activity, key, label);
            }
        });
        column.addView(row, Math.min(3, column.getChildCount()));
        column.requestLayout();
    }

    /** A row like another row of the same menu, with words and a picture of its own. */
    private static LinearLayout copy(LinearLayout proto, String words, String glyph, View.OnClickListener click) {
        Context context = proto.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(proto.getOrientation());
        row.setGravity(proto.getGravity());
        row.setPadding(proto.getPaddingLeft(), proto.getPaddingTop(), proto.getPaddingRight(), proto.getPaddingBottom());
        ViewGroup.LayoutParams params = proto.getLayoutParams();
        row.setLayoutParams(params instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) params)
                : new ViewGroup.LayoutParams(params));
        Drawable background = proto.getBackground();
        if (background != null && background.getConstantState() != null) {
            row.setBackground(background.getConstantState().newDrawable().mutate());
        }
        TextView sample = firstText(proto, 0);
        int ink = sample == null ? 0xFF000000 : sample.getCurrentTextColor();
        for (int i = 0; i < proto.getChildCount(); i++) {
            View child = proto.getChildAt(i);
            ViewGroup.LayoutParams childParams = child.getLayoutParams();
            LinearLayout.LayoutParams copied = childParams instanceof LinearLayout.LayoutParams
                    ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) childParams)
                    : new LinearLayout.LayoutParams(childParams);
            if (child instanceof ImageView) {
                ImageView picture = new ImageView(context);
                picture.setScaleType(((ImageView) child).getScaleType());
                picture.setImageDrawable(SettingsGlyph.make(glyph, ink));
                row.addView(picture, copied);
            } else if (child instanceof TextView) {
                TextView text = new TextView(context);
                TextView from = (TextView) child;
                text.setText(words);
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, from.getTextSize());
                text.setTypeface(from.getTypeface());
                text.setTextColor(from.getCurrentTextColor());
                text.setGravity(from.getGravity());
                text.setSingleLine(true);
                text.setIncludeFontPadding(from.getIncludeFontPadding());
                text.setPadding(from.getPaddingLeft(), from.getPaddingTop(), from.getPaddingRight(), from.getPaddingBottom());
                row.addView(text, copied);
            } else {
                row.addView(new View(context), copied);
            }
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(click);
        row.setTag(TAG_MINE, Boolean.TRUE);
        return row;
    }

    // ------------------------------------------------------------ the windows of the mod

    /** A new folder: a name, and then it is a chip. When a chat is given it is the first in it. */
    private static void create(final Activity activity, final String[] chat) {
        final Panel panel = Panel.with(activity, Skin.remembered(activity), Local.of("Новая папка"));
        final android.widget.EditText name = panel.field(Local.of("Название папки"));
        panel.primaryKeepOpen(Local.of("Создать"), new Panel.Click() {
            @Override public void on() {
                String text = name.getText().toString().trim();
                if (text.length() == 0) {
                    name.setError(Local.of("Введите название"));
                    return;
                }
                if (folders.size() >= 12) {
                    name.setError(Local.of("Папок может быть не больше 12"));
                    return;
                }
                if (text.length() > 24) text = text.substring(0, 24);
                Folder folder = new Folder(Long.toString(System.currentTimeMillis(), 36), text);
                if (chat != null) folder.chats.put(chat[0], chat[1]);
                folders.add(folder);
                save();
                panel.close();
                poke();
                scrollToStart();
                tell(chat != null ? Local.of("Добавлено в папку «") + text + "»" : Local.of("Папка создана: ") + text);
            }
        });
        panel.quiet(Local.of("Отмена"), null).show();
    }

    /** Which folders a chat is in, each to be ticked or not. */
    private static void pick(final Activity activity, final String key, final String label) {
        load();
        final Panel panel = Panel.with(activity, Skin.remembered(activity), Local.of("Добавить в папку"));
        if (label.length() > 0) panel.subtitle(label);
        for (final Folder folder : new ArrayList<Folder>(folders)) {
            final boolean in = folder.chats.containsKey(key);
            panel.option(folder.name, in, false, new Panel.Click() {
                @Override public void on() {
                    if (in) folder.chats.remove(key);
                    else folder.chats.put(key, label);
                    save();
                    if (active != null) {
                        ViewGroup list = inbox();
                        if (list != null) sweep(list);
                    }
                    tell(in ? Local.of("Убрано из папки «") + folder.name + "»" : Local.of("Добавлено в папку «") + folder.name + "»");
                }
            });
        }
        panel.item("add", Local.of("Новая папка"), null, new Panel.Click() {
            @Override public void on() {
                create(activity, new String[] {key, label});
            }
        });
        panel.quiet(Local.of("Закрыть"), null).show();
    }

    /** A folder held down: what can be done with it. */
    private static void manage(final Activity activity, final Folder folder) {
        final Panel panel = Panel.with(activity, Skin.remembered(activity), folder.name);
        panel.subtitle(Local.of("Чатов в папке: ") + folder.chats.size());
        panel.item("edit", Local.of("Переименовать"), null, new Panel.Click() {
            @Override public void on() {
                rename(activity, folder);
            }
        });
        panel.item("group", Local.of("Состав папки"), null, new Panel.Click() {
            @Override public void on() {
                members(activity, folder);
            }
        });
        panel.danger("block", Local.of("Удалить папку"), Local.of("Чаты останутся на месте"), new Panel.Click() {
            @Override public void on() {
                folders.remove(folder);
                if (folder.id.equals(active)) leave();
                save();
                poke();
            }
        });
        panel.quiet(Local.of("Закрыть"), null).show();
    }

    private static void rename(final Activity activity, final Folder folder) {
        final Panel panel = Panel.with(activity, Skin.remembered(activity), Local.of("Название папки"));
        final android.widget.EditText name = panel.field(Local.of("Название папки"));
        name.setText(folder.name);
        name.setSelection(name.getText().length());
        panel.primaryKeepOpen(Local.of("Сохранить"), new Panel.Click() {
            @Override public void on() {
                String text = name.getText().toString().trim();
                if (text.length() == 0) {
                    name.setError(Local.of("Введите название"));
                    return;
                }
                folder.name = text.length() > 24 ? text.substring(0, 24) : text;
                save();
                panel.close();
                poke();
            }
        });
        panel.quiet(Local.of("Отмена"), null).show();
    }

    private static void members(final Activity activity, final Folder folder) {
        Panel panel = Panel.with(activity, Skin.remembered(activity), folder.name);
        if (folder.chats.isEmpty()) panel.text(Local.of("В папке нет чатов. Удерживайте диалог в списке и выберите «Добавить в папку»."));
        for (final Map.Entry<String, String> chat : new ArrayList<Map.Entry<String, String>>(folder.chats.entrySet())) {
            final String key = chat.getKey();
            String shown = chat.getValue().length() == 0 ? key : chat.getValue();
            panel.option(shown, true, false, new Panel.Click() {
                @Override public void on() {
                    folder.chats.remove(key);
                    save();
                    ViewGroup list = inbox();
                    if (list != null) sweep(list);
                    members(activity, folder);
                }
            });
        }
        panel.quiet(Local.of("Закрыть"), null).show();
    }
}
