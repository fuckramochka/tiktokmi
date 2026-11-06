package mi.tiktokmi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * What a file manager opens a .mtui with. It shows nothing itself: the file goes to the
 * mod's settings, which show the style and ask before installing it.
 */
public final class StyleOpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Uri file = getIntent() == null ? null : getIntent().getData();
            if (file != null) {
                Intent open = new Intent(this, SettingsActivity.class);
                // in the data, not an extra: only that way does the permission to read it travel along
                open.setData(file);
                open.putExtra(SettingsActivity.OPEN_STYLE, true);
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(open);
            }
        } catch (Throwable error) {
            Diary.note("style file: " + error);
        }
        finish();
    }
}
