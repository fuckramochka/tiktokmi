package com.ss.android.ugc.aweme.im.streak.api;

/**
 * TikTok's own streak service, in outline.
 *
 * The name of the interface is real and so are the signatures; the three
 * method names are not, and they are this release's. They are written here
 * because a call rewritten into the mod has to be handed back with the same
 * types the verifier saw, and that needs a declaration to compile against.
 *
 * Nothing else in the mod names them. The rules in `dexpatch` are the only
 * other place, and the build reports how many call sites each one matched, so
 * a release that renames them shows up as a zero rather than as silence.
 */
public interface IStreakService {

    /** TikTok 47.0.3 signatures */
    StreakData LJIIZILJ(String conversation, boolean fresh);
    boolean LJJIJIIJI(String conversation);
    boolean LJJJJI(String conversation, boolean flag);
    int LJII(String conversation);
    Integer LJJJJJL(String conversation);
    String LJIJJ(String conversation);

    /** Earlier release signatures */
    StreakData J(String conversation, boolean fresh);
    boolean a0(String conversation);
    boolean h0(String conversation, boolean flag);
    int w(String conversation);
    boolean X(String conversation);
    boolean Y(String conversation);
    Integer l0(String conversation);
    String O(String conversation);
}
