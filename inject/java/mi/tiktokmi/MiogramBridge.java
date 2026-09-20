package mi.tiktokmi;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Miogram / Amegram Ecosystem Bridge.
 *
 * Connects TikTok MI with Amegram (Telegram mod by @fuckramochka).
 * Features:
 * 1. 1-Tap direct share of clean media without watermarks directly into "Saved Messages" (dialogId=0).
 * 2. 1-Tap share into Telegram Stories (app.amegram.POST_STORY / StoryRecorder).
 * 3. TikTok Audio export with metadata (title, artist, album) into Amegram's music player.
 * 4. Sticker pack export (org.telegram.messenger.CREATE_STICKER_PACK) directly into Telegram stickers.
 * 5. High-speed 1-click Theme & Accent synchronization via ContentProvider / Broadcast.
 * 6. Cross-app VIP / Donor status check via ContentProvider.
 * 7. ClipVault shared media buffer synchronization.
 */
public final class MiogramBridge {

    private MiogramBridge() {}

    public static final String AMEGRAM_PACKAGE = "app.amegram";
    public static final String MIOGRAM_LEGACY_PACKAGE = "app.miogram";
    public static final String TG_OFFICIAL_PACKAGE = "org.telegram.messenger";

    public static final String ECOSYSTEM_AUTHORITY = "app.amegram.ecosystem";
    public static final String ACTION_THEME_CHANGED = "app.amegram.ACTION_THEME_CHANGED";
    public static final String ACTION_POST_STORY = "app.amegram.POST_STORY";
    public static final String ACTION_CLIP_VAULT = "app.amegram.ACTION_CLIP_VAULT";

    /**
     * Finds the preferred installed package: Amegram -> Miogram (legacy) -> Telegram (official).
     */
    public static String getPreferredPackage(Context context) {
        if (context == null) return AMEGRAM_PACKAGE;
        android.content.pm.PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(AMEGRAM_PACKAGE, 0);
            return AMEGRAM_PACKAGE;
        } catch (Throwable ignored) {
            try {
                pm.getPackageInfo(MIOGRAM_LEGACY_PACKAGE, 0);
                return MIOGRAM_LEGACY_PACKAGE;
            } catch (Throwable ignored2) {
                try {
                    pm.getPackageInfo(TG_OFFICIAL_PACKAGE, 0);
                    return TG_OFFICIAL_PACKAGE;
                } catch (Throwable ignored3) {
                    return AMEGRAM_PACKAGE; // Fallback default
                }
            }
        }
    }

    /**
     * Checks whether Amegram (or Telegram) is installed on the device.
     */
    public static boolean isAmegramInstalled(Context context) {
        if (context == null) return false;
        android.content.pm.PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(AMEGRAM_PACKAGE, 0);
            return true;
        } catch (Throwable ignored) {
            try {
                pm.getPackageInfo(MIOGRAM_LEGACY_PACKAGE, 0);
                return true;
            } catch (Throwable ignored2) {
                try {
                    pm.getPackageInfo(TG_OFFICIAL_PACKAGE, 0);
                    return true;
                } catch (Throwable ignored3) {
                    return false;
                }
            }
        }
    }

    public static boolean isMiogramInstalled(Context context) {
        return isAmegramInstalled(context);
    }

    /**
     * Sends a downloaded video or media file to Amegram / Telegram chat with standard dialog.
     */
    public static void shareMedia(Context context, File file, String mimeType, String caption) {
        shareMediaInternal(context, file, mimeType, caption, false, -1);
    }

    /**
     * 1-Tap Share: Sends clean video directly to Amegram "Saved Messages" without dialog.
     */
    public static void shareToSavedMessages(Context context, File file, String mimeType, String caption) {
        shareMediaInternal(context, file, mimeType, caption, true, 0L);
    }

    /**
     * 1-Tap Stories: Sends clean video directly to Amegram Telegram Stories editor.
     */
    public static void shareToStories(Context context, File file, String caption) {
        if (context == null || file == null || !file.exists()) {
            Toast.makeText(context, "Файл ще завантажується...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", file);

            String targetPkg = getPreferredPackage(context);
            Intent storyIntent = new Intent(ACTION_POST_STORY);
            storyIntent.setPackage(targetPkg);
            storyIntent.setDataAndType(uri, "video/mp4");
            storyIntent.putExtra(Intent.EXTRA_STREAM, uri);
            if (caption != null && !caption.isEmpty()) {
                storyIntent.putExtra(Intent.EXTRA_TEXT, caption);
                storyIntent.putExtra("caption", caption);
            }
            storyIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(storyIntent);
        } catch (Throwable error) {
            // Fallback: standard share with caption indicating story
            shareMedia(context, file, "video/mp4", caption != null ? caption : "Story from TikTok MI");
        }
    }

    private static void shareMediaInternal(Context context, File file, String mimeType, String caption,
                                          boolean directSend, long dialogId) {
        if (context == null || file == null || !file.exists()) {
            Toast.makeText(context, "Файл ще завантажується...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType != null ? mimeType : "*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            if (caption != null && !caption.isEmpty()) {
                intent.putExtra(Intent.EXTRA_TEXT, caption);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            String targetPkg = getPreferredPackage(context);
            intent.setPackage(targetPkg);

            if (directSend) {
                // Telegram interprets dialogId = 0 as Saved Messages in direct share
                intent.putExtra("dialogId", dialogId);
                intent.putExtra("direct_send", true);
                context.startActivity(intent);
            } else {
                context.startActivity(Intent.createChooser(intent, "Надіслати в Amegram"));
            }
        } catch (Throwable error) {
            // Fallback to standard chooser
            try {
                Intent fallback = new Intent(Intent.ACTION_SEND);
                fallback.setType(mimeType != null ? mimeType : "*/*");
                fallback.putExtra(Intent.EXTRA_TEXT, caption != null ? caption : "");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
                Toast.makeText(context, "Помилка відправки в Amegram: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Sends audio track directly to Amegram to play or save with ID3 metadata.
     */
    public static void shareAudio(Context context, File audioFile, String title, String artist, String album) {
        if (context == null || audioFile == null || !audioFile.exists()) {
            Toast.makeText(context, "Аудіо ще завантажується...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", audioFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TITLE, title != null ? title : "TikTok MI Sound");
            intent.putExtra("TITLE", title != null ? title : "TikTok MI Sound");
            if (artist != null) intent.putExtra("ARTIST", artist);
            if (album != null) intent.putExtra("ALBUM", album);
            intent.putExtra(Intent.EXTRA_TEXT, "🎵 " + (title != null ? title : "TikTok MI Sound") + (artist != null ? " - " + artist : ""));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.setPackage(getPreferredPackage(context));
            context.startActivity(Intent.createChooser(intent, "Слухати в Amegram"));
        } catch (Throwable error) {
            shareMedia(context, audioFile, "audio/*", "🎵 " + (title != null ? title : "TikTok MI Sound"));
        }
    }

    public static void shareAudio(Context context, File audioFile, String title) {
        shareAudio(context, audioFile, title, null, "TikTok MI Sounds");
    }

    /**
     * Sends sticker to Amegram to add to stickers or saved messages.
     */
    public static void shareSticker(Context context, File imageFile) {
        shareMedia(context, imageFile, "image/*", "#sticker @fuckramochka");
    }

    /**
     * 1-Tap Sticker Pack Importer: Native Telegram sticker import.
     */
    public static void shareStickerPack(Context context, List<File> stickerFiles, List<String> emojis) {
        if (context == null || stickerFiles == null || stickerFiles.isEmpty()) return;

        try {
            ArrayList<Uri> uris = new ArrayList<Uri>(stickerFiles.size());
            for (File file : stickerFiles) {
                if (file != null && file.exists()) {
                    uris.add(androidx.core.content.FileProvider.getUriForFile(
                            context, context.getPackageName() + ".fileprovider", file));
                }
            }
            if (uris.isEmpty()) return;

            Intent intent = new Intent("org.telegram.messenger.CREATE_STICKER_PACK");
            intent.setPackage(getPreferredPackage(context));
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            if (emojis != null && !emojis.isEmpty()) {
                intent.putStringArrayListExtra("STICKER_EMOJIS", new ArrayList<String>(emojis));
            }
            intent.putExtra("IMPORTER", "TikTok MI");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
        } catch (Throwable error) {
            // Fallback to single sticker share
            if (!stickerFiles.isEmpty()) {
                shareSticker(context, stickerFiles.get(0));
            }
        }
    }

    /**
     * Opens a Telegram chat, user, or link directly inside Amegram without browser redirect.
     */
    public static boolean openTelegram(Context context, String urlOrUsername) {
        if (context == null || urlOrUsername == null || urlOrUsername.isEmpty()) return false;
        try {
            String clean = urlOrUsername.trim();
            if (clean.startsWith("@")) {
                clean = "https://t.me/" + clean.substring(1);
            } else if (!clean.startsWith("http://") && !clean.startsWith("https://") && !clean.startsWith("tg://")) {
                clean = "https://t.me/" + clean;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clean));
            intent.setPackage(getPreferredPackage(context));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable error) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(urlOrUsername));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /**
     * Synchronizes current theme & accent with Amegram in 1 click.
     */
    public static void syncThemeToAmegram(Context context, int accentColor, boolean isDark, boolean isAmoled) {
        if (context == null) return;
        try {
            // 1. Try high-speed ContentProvider call (<2ms)
            Uri providerUri = Uri.parse("content://" + ECOSYSTEM_AUTHORITY + "/theme");
            Bundle bundle = new Bundle();
            bundle.putInt("accent", accentColor);
            bundle.putBoolean("dark", isDark);
            bundle.putBoolean("amoled", isAmoled);
            context.getContentResolver().call(providerUri, "setTheme", null, bundle);
        } catch (Throwable ignored) {
        }

        try {
            // 2. Broadcast for running Amegram instances
            Intent broadcast = new Intent(ACTION_THEME_CHANGED);
            broadcast.setPackage(getPreferredPackage(context));
            broadcast.putExtra("accent", accentColor);
            broadcast.putExtra("dark", isDark);
            broadcast.putExtra("amoled", isAmoled);
            context.sendBroadcast(broadcast);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Checks if current user is an Amegram VIP / Donor / Architect via ContentProvider.
     */
    public static boolean isAmegramDonor(Context context) {
        if (context == null) return false;
        try {
            Uri providerUri = Uri.parse("content://" + ECOSYSTEM_AUTHORITY + "/badge");
            Bundle res = context.getContentResolver().call(providerUri, "getBadgeStatus", null, null);
            if (res != null) {
                return res.getBoolean("isDonor", false) || res.getBoolean("isFounder", false);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Pushes copied TikTok media to Amegram ClipVault buffer for 1-tap pasting in chats.
     */
    public static void notifyClipVault(Context context, String mediaUrl, String title, String author, boolean isVideo) {
        if (context == null || mediaUrl == null || mediaUrl.isEmpty()) return;
        try {
            Uri providerUri = Uri.parse("content://" + ECOSYSTEM_AUTHORITY + "/clipvault");
            Bundle bundle = new Bundle();
            bundle.putString("url", mediaUrl);
            bundle.putString("title", title);
            bundle.putString("author", author);
            bundle.putBoolean("isVideo", isVideo);
            context.getContentResolver().call(providerUri, "pushClipVault", null, bundle);

            Intent broadcast = new Intent(ACTION_CLIP_VAULT);
            broadcast.setPackage(getPreferredPackage(context));
            broadcast.putExtras(bundle);
            context.sendBroadcast(broadcast);
        } catch (Throwable ignored) {
        }
    }

    private static volatile boolean syncingFromAmegram = false;

    /**
     * Handles incoming theme broadcast from Amegram.
     */
    public static void handleIncomingTheme(int accentColor, boolean isDark, boolean isAmoled) {
        if (!isThemeSyncEnabled() || accentColor == 0) return;
        if (accentColor == Accent.colour()) return;

        try {
            syncingFromAmegram = true;
            Accent.set(accentColor);
        } finally {
            syncingFromAmegram = false;
        }
    }

    public static boolean isSyncingFromAmegram() {
        return syncingFromAmegram;
    }

    /**
     * Initializes the ecosystem bridge and registers dynamic receivers.
     */
    public static void start(Context context) {
        if (context == null) return;
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter("mi.tiktokmi.ACTION_THEME_CHANGED");
            context.registerReceiver(new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    int accent = intent.getIntExtra("accent", 0);
                    boolean dark = intent.getBooleanExtra("dark", true);
                    boolean amoled = intent.getBooleanExtra("amoled", false);
                    handleIncomingTheme(accent, dark, amoled);
                }
            }, filter);
        } catch (Throwable ignored) {
        }
    }

    public static final String KEY_SYNC_THEME = "amegram_sync_theme";
    public static final String KEY_DIRECT_SAVED = "amegram_direct_saved";
    public static final String KEY_CLIPVAULT = "amegram_clipvault";

    public static boolean isThemeSyncEnabled() {
        try {
            Context context = Margy.context();
            if (context == null) return true;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SYNC_THEME, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setThemeSyncEnabled(boolean enabled) {
        try {
            Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SYNC_THEME, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isDirectSavedEnabled() {
        try {
            Context context = Margy.context();
            if (context == null) return true;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_DIRECT_SAVED, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setDirectSavedEnabled(boolean enabled) {
        try {
            Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DIRECT_SAVED, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isClipVaultEnabled() {
        try {
            Context context = Margy.context();
            if (context == null) return true;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_CLIPVAULT, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setClipVaultEnabled(boolean enabled) {
        try {
            Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_CLIPVAULT, enabled).apply();
        } catch (Throwable ignored) {
        }
    }
}
