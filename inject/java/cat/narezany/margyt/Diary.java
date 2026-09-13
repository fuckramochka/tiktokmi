package cat.narezany.margyt;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mod saw, kept so it can be read off the phone.
 *
 * A mod cannot be attached to a debugger and its user is not going to run
 * logcat. So the few things worth knowing -- did the start-up hook run, which
 * screen was that, what was the view tree made of -- are written down here and
 * shown at the bottom of MargyT's own settings screen.
 *
 * Thirty lines, in memory, never written anywhere.
 */
public final class Diary {

    private Diary() {}

    private static final int KEEP = 30;
    private static final List<String> LINES = new ArrayList<String>();

    public static void note(String line) {
        synchronized (LINES) {
            if (LINES.isEmpty() || !LINES.get(LINES.size() - 1).equals(line)) {
                LINES.add(line);
                while (LINES.size() > KEEP) LINES.remove(0);
            }
        }
    }

    public static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<String>(LINES);
        }
    }
}
