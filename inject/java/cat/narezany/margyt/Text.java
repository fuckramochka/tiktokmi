package cat.narezany.margyt;

import java.util.Locale;

/**
 * The words the mod puts on screen, in the phone's language where it has them.
 *
 * Three languages and a handful of lines: enough that the mod does not look
 * bolted onto a Russian phone in English, and small enough to keep in one file
 * rather than in string resources -- which this build cannot add.
 */
final class Text {

    private Text() {}

    private static final boolean RU = "ru".equals(language()) || "be".equals(language());
    private static final boolean UK = "uk".equals(language());

    static final String ROW = pick("Настройки MargyT", "Налаштування MargyT", "MargyT settings");

    static final String REGION = pick("Регион", "Регіон", "Region");

    static final String CHANGE_REGION = pick(
            "Менять регион", "Змінювати регіон", "Change the region");

    static final String COUNTRY = pick("Страна", "Країна", "Country");

    static final String ABOUT = pick(
            "Регион меняет ленту и почти всё, что закрыто по стране. Он не меняет "
                    + "регион аккаунта — тот сервер задаёт при регистрации. IP — "
                    + "отдельная история, для него нужен VPN.",
            "Регіон змінює стрічку і майже все, що закрите за країною. Він не змінює "
                    + "регіон акаунта — той сервер задає під час реєстрації. IP — "
                    + "окрема історія, для нього потрібен VPN.",
            "Region reaches the feed and most of what is gated by country. It does "
                    + "not reach the store region, which the server fixes when the "
                    + "account is created. Your IP is a separate matter and wants a VPN.");

    static final String ACCENT = pick("Цвет TikTok", "Колір TikTok", "TikTok's colour");

    static final String ACCENT_COLOUR = pick("Акцент", "Акцент", "Accent");

    static final String ACCENT_NOTE = pick(
            "Меняет розовый, которым TikTok рисует лайки, кнопки и вкладки. "
                    + "Часть значков нарисована картинками — их цвет задаётся при сборке "
                    + "и здесь не меняется.",
            "Змінює рожевий, яким TikTok малює лайки, кнопки та вкладки. "
                    + "Частина значків намальована картинками — їхній колір задається "
                    + "під час збірки і тут не змінюється.",
            "Changes the pink TikTok draws likes, buttons and tabs with. Some icons "
                    + "are pictures rather than code; their colour is settled at build "
                    + "time and does not follow.");

    static final String RESTART = pick(
            "Перезапустить TikTok", "Перезапустити TikTok", "Restart TikTok");

    static final String RESTART_NOTE = pick(
            "Цвет меняется у того, что рисуется заново. Уже нарисованные экраны "
                    + "останутся прежними до перезапуска.",
            "Колір змінюється у того, що малюється наново. Уже намальовані екрани "
                    + "лишаться колишніми до перезапуску.",
            "The colour changes wherever something is drawn again. Screens already "
                    + "drawn keep the old one until the app is restarted.");

    static final String DIARY_TITLE = pick("Журнал мода", "Журнал мода", "The mod's diary");

    static final String COPY = pick("Скопировать", "Скопіювати", "Copy");

    static final String COPIED = pick("Скопировано", "Скопійовано", "Copied");

    static final String CLEAR = pick("Очистить журнал", "Очистити журнал", "Clear the diary");

    static final String DIARY = pick(
            "Что мод видел  (нажми, чтобы очистить)",
            "Що мод бачив  (натисни, щоб очистити)",
            "What the mod saw  (tap to clear)");

    private static String language() {
        try {
            return Locale.getDefault().getLanguage();
        } catch (Throwable ignored) {
            return "en";
        }
    }

    private static String pick(String russian, String ukrainian, String english) {
        if (RU) return russian;
        if (UK) return ukrainian;
        return english;
    }
}
