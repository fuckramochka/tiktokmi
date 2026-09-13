package cat.narezany.margyt;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * The mod's way into a running TikTok, and it is not a patch at all.
 *
 * Android instantiates every content provider an app declares before the
 * application's own onCreate, whether anything ever queries it or not. So a
 * provider that answers nothing is a start-up hook TikTok's code knows nothing
 * about and cannot move: no Application class to patch, no method to find again
 * after the next release.
 *
 * All it does is remember the context and ask to be told when an activity
 * appears.
 */
public final class MargyProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        Margy.attach(context);
        Diary.note("start-up hook ran");
        try {
            Context application = context.getApplicationContext();
            if (application instanceof Application) {
                ((Application) application).registerActivityLifecycleCallbacks(new SettingsRow());
                Diary.note("watching for the settings screen");
            } else {
                Diary.note("no application yet: " + application);
            }
        } catch (Throwable error) {
            // the mod failing to start is not a reason for the app not to
            Diary.note("hook failed: " + error);
        }
        try {
            Badges.start(context);
        } catch (Throwable error) {
            Diary.note("badges failed to start: " + error);
        }
        try {
            Plugins.startAll(context);
        } catch (Throwable error) {
            Diary.note("plugins failed to start: " + error);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
