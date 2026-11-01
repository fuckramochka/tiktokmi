package cat.narezany.margyt;
// The way in is from TT Extras 1.0.4 (NativeLimits.comments), with its author's permission.

final class LongComments {

    private static final String CONFIG = "com.ss.android.ugc.aweme.comment.commentlist.ui.CommentCharLimitConfig";
    private static GhostReflect.Patch patch;
    private static String failed;

    private LongComments() {}

    static synchronized void apply(boolean on) {
        try {
            if (!on) {
                if (patch != null) patch.restore();
                patch = null;
                return;
            }
            if (patch != null) return;
            Object lazy = GhostReflect.stat("X.0onX", "LIZIZ");
            Object original = GhostReflect.call(lazy, "getValue");
            if (original == null || !original.getClass().getName().equals(CONFIG)
                    || !lazy.getClass().getName().equals("X.0CPb")) {
                throw new IllegalStateException("unexpected comment configuration");
            }
            Object copy = CommentLimit.withLimit(original, Integer.MAX_VALUE);
            synchronized (lazy) {
                patch = new GhostReflect.Patch(lazy, GhostReflect.field(lazy.getClass(), "LLJJIII"), copy);
            }
            failed = null;
        } catch (Throwable error) {
            String said = error.getClass().getSimpleName() + ": " + error.getMessage();
            if (!said.equals(failed)) Diary.note("long comments: " + said);
            failed = said;
        }
    }
}
