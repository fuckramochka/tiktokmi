package mi.tiktokmi;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

/**
 * Miogram Ecosystem Bridge.
 *
 * Connects TikTok MI with Miogram (Telegram mod by @fuckramochka).
 * Allows 1-tap sharing of videos without watermarks, audio tracks, and stickers
 * directly into Miogram without cluttering the device's gallery.
 */
public final class MiogramBridge {

    private MiogramBridge() {}

    public static final String MIOGRAM_PACKAGE = "app.miogram";
    public static final String TG_OFFICIAL_PACKAGE = "org.telegram.messenger";

    /**
     * Checks whether Miogram (or Telegram) is installed on the device.
     */
    public static boolean isMiogramInstalled(Context context) {
        if (context == null) return false;
        android.content.pm.PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(MIOGRAM_PACKAGE, 0);
            return true;
        } catch (Throwable ignored) {
            try {
                pm.getPackageInfo(TG_OFFICIAL_PACKAGE, 0);
                return true;
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    /**
     * Sends a downloaded video or media file directly to Miogram / Telegram chat.
     */
    public static void shareMedia(Context context, File file, String mimeType, String caption) {
        if (context == null || file == null || !file.exists()) {
            Toast.makeText(context, "Файл ще завантажується...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            if (caption != null && !caption.isEmpty()) {
                intent.putExtra(Intent.EXTRA_TEXT, caption);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Prefer Miogram package if installed
            android.content.pm.PackageManager pm = context.getPackageManager();
            try {
                pm.getPackageInfo(MIOGRAM_PACKAGE, 0);
                intent.setPackage(MIOGRAM_PACKAGE);
            } catch (Throwable ignored) {
                try {
                    pm.getPackageInfo(TG_OFFICIAL_PACKAGE, 0);
                    intent.setPackage(TG_OFFICIAL_PACKAGE);
                } catch (Throwable ignored2) {
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Надіслати в Miogram"));
        } catch (Throwable error) {
            // Fallback to standard share
            try {
                Intent fallback = new Intent(Intent.ACTION_SEND);
                fallback.setType(mimeType);
                fallback.putExtra(Intent.EXTRA_TEXT, caption != null ? caption : "");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
                Toast.makeText(context, "Помилка відправки в Miogram: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Sends audio track directly to Miogram to play or save in Telegram music.
     */
    public static void shareAudio(Context context, File audioFile, String title) {
        shareMedia(context, audioFile, "audio/*", "🎵 " + (title != null ? title : "TikTok MI Sound"));
    }

    /**
     * Sends sticker/avatar to Miogram to add to stickers or saved messages.
     */
    public static void shareSticker(Context context, File imageFile) {
        shareMedia(context, imageFile, "image/*", "#sticker @fuckramochka");
    }
}
