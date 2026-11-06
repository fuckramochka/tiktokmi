package mi.tiktokmi;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * A beta's notes to logcat, for what cannot be seen from the code: how long a chat folder
 * takes, what the inbox's list is made of, what colours the share sheet's bottom row has.
 * Under the tag TikTok Mi, each line begins with "diag". To be taken out before a release.
 */
final class Diag {

    private Diag() {}

    static final boolean ON = false;
    private static int said;

    private static java.io.Writer file;

    /** A line to logcat, and to Android/data/<app>/files/margyt-diag.txt, which a phone may keep out of logcat. */
    static synchronized void say(String line) {
        if (!ON || said > 800) return;
        said++;
        android.util.Log.e("TikTok Mi", "diag " + line);
        try {
            if (file == null) {
                java.io.File dir = Margy.context().getExternalFilesDir(null);
                file = new java.io.OutputStreamWriter(new java.io.FileOutputStream(new java.io.File(dir, "margyt-diag.txt"), true), "UTF-8");
            }
            file.write(System.currentTimeMillis() + " " + line + "\n");
            file.flush();
        } catch (Throwable ignored) {
        }
    }

    private static String id(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "-";
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable unknown) {
            return Integer.toHexString(id);
        }
    }

    // ------------------------------------------------------------ the inbox's list

    private static boolean adapterTold;

    /** What the inbox's adapter is, and what it keeps its chats in. */
    static void adapter(final ViewGroup list) {
        if (!ON || adapterTold) return;
        adapterTold = true;
        list.postDelayed(new Runnable() {
            @Override public void run() {
                adapterNow(list);
            }
        }, 1500L);
    }

    private static void adapterNow(ViewGroup list) {
        try {
            Object adapter = list.getClass().getMethod("getAdapter").invoke(list);
            if (adapter == null) { say("inbox adapter: none"); return; }
            StringBuilder chain = new StringBuilder();
            for (Class<?> c = adapter.getClass(); c != null && c != Object.class; c = c.getSuperclass()) chain.append(c.getName()).append(" < ");
            say("inbox adapter: " + chain);
            items(adapter);
        } catch (Throwable error) {
            say("inbox adapter: " + error);
        }
    }

    private static Object read(Object object, String name) throws Exception {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException next) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** What the chats of the inbox's list are: each kind of item, how many, and what one of each is made of. */
    private static void items(Object adapter) throws Exception {
        List<?> list = (List<?>) read(read(adapter, "LL"), "LJFF");
        java.util.LinkedHashMap<String, Object> first = new java.util.LinkedHashMap<String, Object>();
        java.util.LinkedHashMap<String, Integer> many = new java.util.LinkedHashMap<String, Integer>();
        for (Object wrapper : list) {
            Object model = wrapper == null ? null : read(wrapper, "LIZ");
            String kind = model == null ? "null" : model.getClass().getName();
            Integer seen = many.get(kind);
            many.put(kind, seen == null ? 1 : seen + 1);
            if (!first.containsKey(kind)) first.put(kind, model);
        }
        say("inbox items " + list.size() + ": " + many);
        for (java.util.Map.Entry<String, Object> one : first.entrySet()) {
            if (one.getValue() == null) continue;
            deep(one.getValue(), "item " + one.getKey(), 0);
        }
    }

    private static void deep(Object object, String where, int depth) {
        try {
            for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object value = field.get(object);
                    String kind = value == null ? field.getType().getName() : value.getClass().getName();
                    boolean plain = value == null || value instanceof CharSequence || value instanceof Number
                            || value instanceof Boolean || value.getClass().isEnum() || value instanceof Character;
                    String shown = value == null ? "null" : plain ? shortly(value).replace('\n', ' ')
                            : value instanceof java.util.Collection ? "size " + ((java.util.Collection<?>) value).size() : "(object)";
                    if (shown.length() > 160) shown = shown.substring(0, 160);
                    say(where + " ." + field.getName() + " " + kind + " = " + shown);
                    if (value != null && depth < 1 && !kind.startsWith("java.") && !kind.startsWith("android.")
                            && !kind.startsWith("kotlin.") && !(value instanceof CharSequence) && !(value instanceof Number)
                            && !(value instanceof Boolean) && !value.getClass().isEnum())
                        deep(value, where + "." + field.getName(), depth + 1);
                }
            }
        } catch (Throwable error) {
            say(where + ": " + error);
        }
    }

    private static void describeFields(Object object, String where, int depth) throws Exception {
        for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                String kind = value == null ? field.getType().getName() : value.getClass().getName();
                String more = "";
                if (value instanceof List) {
                    List<?> items = (List<?>) value;
                    more = " size " + items.size();
                    for (int i = 0; i < items.size() && i < 2; i++) {
                        Object item = items.get(i);
                        more += " [" + (item == null ? "null" : item.getClass().getName()) + "]";
                        if (item != null && depth == 0 && i == 0) {
                            say(where + "." + field.getName() + "[0] " + item.getClass().getName() + ": " + shortly(item));
                            describeItem(item, where + "." + field.getName() + "[0]");
                        }
                    }
                } else if (value != null && depth == 0 && !(value instanceof String) && !kind.startsWith("java.") && !kind.startsWith("android.")
                        && !kind.startsWith("kotlin.") && !value.getClass().isPrimitive()) {
                    // one level into things the adapter holds: a differ, a data source
                    say(where + "." + field.getName() + ": " + kind);
                    describeFields(value, where + "." + field.getName(), depth + 1);
                    continue;
                }
                say(where + "." + field.getName() + ": " + kind + more);
            }
        }
    }

    private static String shortly(Object item) {
        String text = String.valueOf(item);
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    /** The fields of one chat of the list, with what is in them, a level deep. */
    private static void describeItem(Object item, String where) {
        try {
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object value = field.get(item);
                    String kind = value == null ? field.getType().getName() : value.getClass().getName();
                    say(where + " ." + field.getName() + " " + kind + " = " + (value == null ? "null" : shortly(value).replace('\n', ' ')));
                }
            }
        } catch (Throwable error) {
            say(where + ": " + error);
        }
    }

    // ------------------------------------------------------------ the share sheet's bottom row

    private static long sheetTold;

    /** The colours of each cell of the sheet's rows of apps and of actions, as they are drawn. */
    static void sheet(final View anywhere) {
        if (!ON) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sheetTold < 5000L) return;
        sheetTold = now;
        anywhere.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    View root = anywhere.getRootView();
                    for (String name : new String[] {"dx_", "a5x"}) {
                        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
                        View row = id == 0 ? null : root.findViewById(id);
                        say("sheet row " + name + ": " + (row == null ? "not found" : row.getClass().getName() + " " + ((ViewGroup) row).getChildCount() + " cells"));
                        if (row instanceof ViewGroup) {
                            ViewGroup cells = (ViewGroup) row;
                            for (int i = 0; i < cells.getChildCount() && i < 3; i++) cell(cells.getChildAt(i), "  ", 0);
                        }
                    }
                } catch (Throwable error) {
                    say("sheet: " + error);
                }
            }
        }, 700L);
    }

    private static String colour(android.content.res.ColorStateList list) {
        return list == null ? "none" : Integer.toHexString(list.getDefaultColor());
    }

    private static void cell(View view, String indent, int depth) {
        if (view == null || depth > 4) return;
        StringBuilder line = new StringBuilder(indent).append(view.getClass().getName()).append(" #").append(id(view))
                .append(" alpha ").append(view.getAlpha()).append(" vis ").append(view.getVisibility())
                .append(" ").append(view.getWidth()).append('x').append(view.getHeight());
        android.graphics.drawable.Drawable ground = view.getBackground();
        if (ground != null) {
            line.append(" bg ").append(ground.getClass().getSimpleName());
            if (ground instanceof android.graphics.drawable.ColorDrawable) line.append(' ').append(Integer.toHexString(((android.graphics.drawable.ColorDrawable) ground).getColor()));
            if (ground instanceof android.graphics.drawable.GradientDrawable) line.append(' ').append(colour(((android.graphics.drawable.GradientDrawable) ground).getColor()));
            if (ground.getColorFilter() != null) line.append(" bgfilter ").append(ground.getColorFilter());
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            android.graphics.drawable.Drawable picture = image.getDrawable();
            line.append(" img ").append(picture == null ? "none" : picture.getClass().getName())
                    .append(" tint ").append(colour(image.getImageTintList()))
                    .append(" filter ").append(image.getColorFilter())
                    .append(" imgalpha ").append(image.getImageAlpha());
            if (picture != null) line.append(" dfilter ").append(picture.getColorFilter()).append(" dalpha ").append(picture.getAlpha());
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            line.append(" text '").append(text.getText()).append("' colour ").append(Integer.toHexString(text.getCurrentTextColor()))
                    .append(" list ").append(colour(text.getTextColors())).append(" size ").append(text.getTextSize())
                    .append(" paintalpha ").append(text.getPaint().getAlpha());
        }
        say("sheet " + line);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) cell(group.getChildAt(i), indent + "  ", depth + 1);
        }
    }
}
