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
            "Меняет ленту и то, что закрыто по стране.",
            "Змінює стрічку і те, що закрите за країною.",
            "Changes the feed and what is gated by country.");

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

    /** The bar at the foot of the screen: one line, not a paragraph. */
    static final String RESTART_PENDING = pick(
            "Изменения применятся после перезапуска",
            "Зміни застосуються після перезапуску",
            "Changes apply after a restart");

    static final String RESTART = pick("Перезапустить", "Перезапустити", "Restart");

    // -------------------------------------------------------- the downloads

    static final String DOWNLOADS = pick("Скачивание", "Завантаження", "Downloads");

    static final String NO_WATERMARK = pick(
            "Без водяного знака", "Без водяного знака", "Without the watermark");

    static final String NO_WATERMARK_NOTE = pick(
            "Только если TikTok прислал чистый адрес для этого видео.",
            "Лише якщо TikTok надіслав чисту адресу для цього відео.",
            "Only where TikTok sent a clean address for the video.");

    // --------------------------------------------------------- the plugins

    static final String PLUGINS = pick("Плагины", "Плагіни", "Plugins");

    static final String PLUGIN_INSTALL = pick(
            "Установить плагин", "Встановити плагін", "Install a plugin");

    static final String PLUGIN_INSTALL_NOTE = pick(
            "Файл .mtp", "Файл .mtp", "An .mtp file");

    static final String PLUGIN_NONE = pick(
            "Пока ничего не установлено.",
            "Поки нічого не встановлено.",
            "Nothing installed yet.");

    static final String PLUGIN_WARNING = pick(
            "Песочницы нет. Ставьте только то, чему доверяете.",
            "Пісочниці немає. Встановлюйте лише те, чому довіряєте.",
            "No sandbox. Install only what you trust.");

    static final String PLUGIN_DOCS = pick(
            "Как писать плагины", "Як писати плагіни", "Writing plugins");

    static final String PLUGIN_DOCS_NOTE = pick(
            "Документация на GitHub", "Документація на GitHub", "The documentation on GitHub");

    static final String PLUGIN_INSTALLED = pick(
            "Плагин установлен", "Плагін встановлено", "Plugin installed");

    static final String PLUGIN_REMOVE = pick("Удалить", "Видалити", "Remove");

    static final String PLUGIN_REMOVE_ASK = pick(
            "Удалить плагин?", "Видалити плагін?", "Remove the plugin?");

    static final String CANCEL = pick("Отмена", "Скасувати", "Cancel");

    static final String PLUGIN_HOLD = pick(
            "Долгое нажатие — удалить",
            "Довге натискання — видалити",
            "Hold to remove");

    // ----------------------------------------------------------- the links

    static final String LINKS = pick("Ссылки", "Посилання", "Links");

    static final String CHANNEL = pick("Канал", "Канал", "Channel");

    static final String FORUM = pick("Форум", "Форум", "Forum");

    static final String THANKS = pick("Благодарности", "Подяки", "Thanks");

    static final String THANKS_NOTE = pick(
            "Люди, без которых мода бы не было.",
            "Люди, без яких мода б не було.",
            "The people the mod would not exist without.");

    static final String THANKS_OWNER = pick(
            "Владелец мода", "Власник мода", "The mod's owner");

    static final String THANKS_CLAUDE = pick(
            "Написал большую часть того, что в моде есть",
            "Написав більшу частину того, що в моді є",
            "Wrote most of what is in the mod");

    static final String THANKS_HELPER = pick(
            "Помог с несколькими фичами",
            "Допоміг з кількома фічами",
            "Helped with several of the features");

    static final String DONATE = pick("Поддержать", "Підтримати", "Support the project");

    static final String DONATE_NOTE = pick(
            "Каждый ваш рубль помогает держать проект на плаву и развивать открытое "
                    + "сообщество моддинга. Спасибо.",
            "Кожен ваш рубль допомагає тримати проєкт на плаву та розвивати відкриту "
                    + "спільноту модингу. Дякуємо.",
            "Every rouble keeps the project afloat and goes back into the open source "
                    + "modding community. Thank you.");

    static final String CARD = pick("Карта", "Картка", "Card");

    static final String TAP_TO_COPY = pick(
            "Нажми, чтобы скопировать", "Натисни, щоб скопіювати", "Tap to copy");

    static final String YOOMONEY = pick("ЮMoney", "ЮMoney", "YooMoney");

    static final String YOOMONEY_NOTE = pick(
            "Перевод напрямую", "Переказ напряму", "Straight to the transfer page");

    static final String NO_BROWSER = pick(
            "Нечем открыть ссылку", "Нема чим відкрити посилання", "Nothing here opens links");

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
