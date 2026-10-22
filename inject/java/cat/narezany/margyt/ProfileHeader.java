package cat.narezany.margyt;

import com.ss.android.ugc.aweme.profile.model.User;
import com.ss.android.ugc.profile.platform.base.data.ProfileComponents;
import com.ss.android.ugc.profile.platform.base.data.ProfileUser;

import java.util.ArrayList;
import java.util.List;

/**
 * The new profile header, whatever the server sent.
 *
 * TikTok's server builds the profile page as a tree of parts, and puts an
 * account's header together one way or the other by the group the account is
 * in: the new header has the avatar beside the name, the old has it on its
 * own above. The flag on the phone does not change that, so an account the
 * server moved back to the old group stayed old with the switch on.
 *
 * The two differ in one move, as an account in each group gets them:
 *
 *     old:  header [ header_avatar, info [ user_info, user_relation_info ], cta, bio ]
 *     new:  header [ info [ user_basic_info [ user_info [ ..., user_relation_info ], header_avatar ] ], cta, bio ]
 *
 * Every page passes through one of TikTok's methods on its way to the screen,
 * the one that pours the account's details into the tree; the build sends
 * those calls here, the tree is moved into the new shape first, and TikTok's
 * method does the rest. Everything else the server put in the page -- the
 * banner, links, whatever is new -- stays where it was.
 */
public final class ProfileHeader {
    private ProfileHeader() {}

    private static volatile java.lang.reflect.Method original;

    /**
     * Remembered once the server has been seen sending this phone's account the
     * old header: the account is in the old group, and the parts of the new
     * profile that go by their own switches (the avatar and the banner above
     * it) are switched on for it too (Flags).
     */
    static final String OLD_GROUP = "profile_old_group";

    static boolean oldGroup() {
        try {
            return Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).getBoolean(OLD_GROUP, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In place of TikTok's X.0sm9.LJIILIIL(user, page, mine, then). */
    public static ProfileUser LJIILIIL(User user, ProfileUser page, boolean mine, kotlin.jvm.functions.Function1 then) {
        try {
            if (page != null && Flags.newProfile() && rearrange(page, mine) && !oldGroup()) {
                Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).edit()
                        .putBoolean(OLD_GROUP, true).apply();
                Flags.forget();
                Diary.note("profile header: the server sends the old header; the banner is built by TikTok from the next start");
            }
        } catch (Throwable error) {
            Diary.note("profile header: " + error);
        }
        try {
            java.lang.reflect.Method method = original;
            if (method == null) {
                method = Class.forName("X.0sm9", true, ProfileHeader.class.getClassLoader()).getDeclaredMethod("LJIILIIL",
                        User.class, ProfileUser.class, boolean.class, kotlin.jvm.functions.Function1.class);
                method.setAccessible(true);
                original = method;
            }
            return (ProfileUser) method.invoke(null, user, page, mine, then);
        } catch (java.lang.reflect.InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException missing) {
            Diary.note("profile header: TikTok's method is gone, " + missing);
            return page;
        }
    }

    /**
     * The old header in the new shape, as TikTok's server sends it to an account
     * in the new group (read off one that has it):
     *
     *     header [ info [ user_basic_info [ (a spacer, on someone else's page),
     *                                       user_info [ name, @name, user_relation_info ],
     *                                       header_avatar ] ], ...the rest as it was ]
     *
     * The avatar goes after the name, on the right, and the counts go inside the
     * name's part. Does nothing to a header already new, or one it does not know.
     */
    static boolean rearrange(ProfileUser page, boolean mine) {
        ProfileComponents header = page.headerComponents;
        if (header == null || header.components == null) return false;
        List<ProfileComponents> parts = new ArrayList<ProfileComponents>(header.components);
        ProfileComponents avatar = named(parts, "header_avatar");
        ProfileComponents info = named(parts, "info");
        if (avatar == null || info == null || info.components == null) return false;
        List<ProfileComponents> inside = new ArrayList<ProfileComponents>(info.components);
        if (named(inside, "user_basic_info") != null) return false;

        ProfileComponents names = named(inside, "user_info");
        if (names == null) {
            // the name parts loose in the info: gathered into one, as the new header has them
            List<ProfileComponents> loose = new ArrayList<ProfileComponents>();
            for (ProfileComponents part : inside)
                if (part != null && ("user_account_name_info".equals(part.componentName) || "user_account_base_info".equals(part.componentName))) loose.add(part);
            if (loose.isEmpty()) return false;
            names = part("user_info", loose);
            inside.add(inside.indexOf(loose.get(0)), names);
            inside.removeAll(loose);
        }
        List<ProfileComponents> nameParts = names.components == null
                ? new ArrayList<ProfileComponents>() : new ArrayList<ProfileComponents>(names.components);
        ProfileComponents counts = named(inside, "user_relation_info");
        if (counts != null) {
            inside.remove(counts);
            if (named(nameParts, "user_relation_info") == null) nameParts.add(counts);
        }
        names.components = nameParts;

        List<ProfileComponents> row = new ArrayList<ProfileComponents>(3);
        if (!mine) row.add(part("", null));
        row.add(names);
        row.add(avatar);
        inside.set(inside.indexOf(names), part("user_basic_info", row));
        info.components = inside;
        parts.remove(avatar);
        header.components = parts;
        return true;
    }

    private static ProfileComponents part(String name, List<ProfileComponents> inside) {
        ProfileComponents made = new ProfileComponents();
        made.componentId = name;
        made.componentName = name;
        made.components = inside;
        return made;
    }

    private static ProfileComponents named(List<? extends ProfileComponents> parts, String name) {
        for (ProfileComponents part : parts) {
            if (part != null && name.equals(part.componentName)) return part;
        }
        return null;
    }
}
