package mi.tiktokmi;

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

    static final String ROW = pick("Настройки TikTok MI", "Налаштування TikTok MI", "TikTok MI settings");

    static final String REGION = pick("Регион", "Регіон", "Region");

    static final String CHANGE_REGION = pick(
            "Менять регион", "Змінювати регіон", "Change the region");

    static final String COUNTRY = pick("Страна", "Країна", "Country");

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

    static final String ACCENT_CUSTOM = pick(
            "Свой цвет (HEX)", "Свій колір (HEX)", "Custom colour (HEX)");

    static final String ACCENT_CUSTOM_HINT = pick(
            "#FE2C55 или FFB7C5", "#FE2C55 або FFB7C5", "#FE2C55 or FFB7C5");

    static final String COLOR_INVALID = pick(
            "Неверный формат цвета (нужен #RRGGBB)", "Невірний формат кольору (потрібен #RRGGBB)",
            "Invalid colour format (#RRGGBB needed)");

    static final String APPLY = pick("Применить", "Застосувати", "Apply");

    /** The bar at the foot of the screen: one line, not a paragraph. */
    static final String RESTART_PENDING = pick(
            "Изменения применятся после перезапуска",
            "Зміни застосуються після перезапуску",
            "Changes apply after a restart");

    static final String RESTART = pick("Перезапустить", "Перезапустити", "Restart");

    // ------------------------------------------------ what TikTok ships off

    static final String HIDDEN = pick("Анти A/B", "Анти A/B", "Anti A/B");

    static final String HIDDEN_NOTE = pick(
            "Функции у TikTok уже написаны, но выдаются случайной части людей. "
                    + "Здесь они включаются всем.",
            "Функції в TikTok уже написані, але видаються випадковій частині людей. "
                    + "Тут вони вмикаються всім.",
            "TikTok has written these already and hands them to a random share of "
                    + "people. Here they are switched on for everyone.");

    static final String CLOSE = pick("Понятно", "Зрозуміло", "Got it");

    static final String SAVE_STICKER = pick(
            "Скачать стикер", "Завантажити стікер", "Download the sticker");

    static final String STICKER_FAILED = pick(
            "Не получилось сохранить стикер", "Не вдалося зберегти стікер",
            "Could not save the sticker");

    static final String SAVE_AVATARS_ON = pick(
            "Кнопка на аватарках", "Кнопка на аватарках", "The button on avatars");

    static final String SAVE_STICKERS_ON = pick(
            "Кнопка на стикерах", "Кнопка на стікерах", "The button on stickers");

    static final String BADGES_ON = pick("Значки", "Значки", "Badges");

    static final String SAVE_AVATAR = pick(
            "Сохранить аватарку", "Зберегти аватарку", "Save the avatar");

    static final String SAVED = pick("Сохранено", "Збережено", "Saved");

    static final String AVATAR_NOTHING = pick(
            "Нечего сохранять — откройте аватарку сначала",
            "Нема чого зберігати — відкрийте аватарку спершу",
            "Nothing to save yet -- open an avatar first");

    static final String AVATAR_FAILED = pick(
            "Не получилось сохранить", "Не вдалося зберегти", "Could not save it");

    static final String BADGE_OWNER = pick(
            "Владелец TikTok MI", "Власник TikTok MI",
            "The owner of TikTok MI");

    static final String VIDEO = pick("Видео", "Відео", "Video");

    static final String THEME = pick("Тема", "Тема", "Theme");

    static final String THEME_ON = pick(
            "Своя тема", "Своя тема", "A theme of your own");

    static final String THEME_MATERIAL = pick(
            "Цвета с обоев", "Кольори зі шпалер", "Colours from the wallpaper");

    static final String THEME_TEXT = pick("Текст", "Текст", "Text");

    static final String THEME_BACKGROUND = pick("Фон", "Тло", "Background");

    static final String THEME_NOTE = pick(
            "Перекрашивается только то, что TikTok и сам перекрашивает при "
                    + "смене светлой темы на тёмную. Акцент живёт отдельно.",
            "Перефарбовується лише те, що TikTok і сам перефарбовує при зміні "
                    + "світлої теми на темну. Акцент живе окремо.",
            "Only what TikTok itself repaints when you switch between light and "
                    + "dark. The accent is its own thing.");

    static final String BACKGROUND = pick(
            "Играть в фоне", "Грати у фоні", "Play in the background");

    static final String AUTOSCROLL = pick(
            "Автопрокрутка ленты", "Автопрокрутка стрічки", "Scroll the feed by itself");

    static final String SOUND = pick(
            "Звук, снятый по копирайту", "Звук, знятий за копірайтом",
            "Sound pulled for copyright");

    static final String SEEKBAR = pick(
            "Перемотка на всех видео", "Перемотка на всіх відео",
            "The scrubbing bar everywhere");

    static final String VOICE = pick(
            "Голосовые комментарии", "Голосові коментарі", "Voice comments");

    static final String THEME_STRENGTH = pick(
            "Насыщенность фона", "Насиченість тла", "How much of that background");

    static final String THEME_STRENGTH_NOTE = pick(
            "Слева — почти чёрный с оттенком выбранного цвета, справа — сам цвет.",
            "Ліворуч — майже чорний з відтінком обраного кольору, праворуч — сам колір.",
            "To the left, near black with a hint of the colour; to the right, the colour.");

    static final String ACCENT_WALLPAPER = pick(
            "Взять цвет с обоев", "Взяти колір зі шпалер", "Take the colour from the wallpaper");

    // --------------------------------------------------------------- fonts

    static final String FONT = pick("Шрифт", "Шрифт", "Typeface");

    static final String FONT_SYSTEM = pick("Системный", "Системний", "The system one");
    static final String FONT_SANS = pick("Обычный", "Звичайний", "Sans");
    static final String FONT_SANS_LIGHT = pick("Тонкий", "Тонкий", "Light");
    static final String FONT_SANS_CONDENSED = pick("Узкий", "Вузький", "Condensed");
    static final String FONT_SERIF = pick("С засечками", "Із засічками", "Serif");
    static final String FONT_MONOSPACE = pick("Моноширинный", "Моноширинний", "Monospace");
    static final String FONT_CURSIVE = pick("Рукописный", "Рукописний", "Cursive");
    static final String FONT_FILE = pick("Свой файл", "Свій файл", "A file of your own");

    static final String FONT_PICK = pick(
            "Выбрать .ttf или .otf", "Обрати .ttf або .otf", "Pick a .ttf or .otf");

    static final String FONT_FAILED = pick(
            "Не получилось прочитать шрифт", "Не вдалося прочитати шрифт",
            "That file is not a font this phone can read");

    static final String EMOJI = pick("Шрифт эмодзи", "Шрифт емодзі", "Emoji");

    static final String EMOJI_SYSTEM = pick("Системные", "Системні", "The system ones");
    static final String EMOJI_TWEMOJI = pick("Twemoji", "Twemoji", "Twemoji");
    static final String EMOJI_NOTO = pick("Noto", "Noto", "Noto");
    static final String EMOJI_BLOB = pick("Blobmoji", "Blobmoji", "Blobmoji");
    static final String EMOJI_FILE = pick("Свой файл", "Свій файл", "A file of your own");

    static final String EMOJI_NOTE = pick(
            "Все паки уже внутри мода, скачивать ничего не нужно. Нужен Android 10 "
                    + "и выше: ниже него меняются только буквы.",
            "Усі паки вже всередині мода, завантажувати нічого не треба. Потрібен "
                    + "Android 10 і вище: нижче змінюються лише літери.",
            "Every pack is already inside the mod; nothing is fetched. Android 10 "
                    + "and up: below that only the letters change.");

    static final String EMOJI_READY = pick(
            "Готово — перезапусти приложение", "Готово — перезапусти застосунок",
            "Done -- restart the app");

    // ---------------------------------------------------------- the icon

    static final String ICON = pick("Иконка", "Іконка", "The icon");

    static final String ICON_NOTE = pick(
            "Иконки с конкурса. Хочешь, чтобы твоя была здесь — участвуй.",
            "Іконки з конкурсу. Хочеш, щоб твоя була тут — бери участь.",
            "These came from a contest. Enter it if you want yours here.");

    static final String ICON_CONTEST = pick(
            "Участвовать в конкурсе", "Взяти участь у конкурсі", "Enter the contest");

    static final String ICON_DEFAULT = pick("Обычная", "Звичайна", "The usual one");

    // -------------------------------------------------------- the donation

    static final String DONATE_BANNER = pick(
            "Значок за поддержку", "Значок за підтримку", "A badge for supporting");

    static final String DONATE_BANNER_TEXT = pick(
            "Пожертвование от 250 ₽ даёт персональный значок рядом с ником — "
                    + "его видят все, у кого установлен TikTok MI.",
            "Пожертва від 250 ₽ дає персональний значок поруч із ніком — "
                    + "його бачать усі, у кого встановлено TikTok MI.",
            "A donation of 250 roubles or more comes with a personal badge beside "
                    + "your name, visible to everyone running TikTok MI.");

    static final String DONATE_BANNER_HOW = pick(
            "После перевода напишите @dkramochka в Telegram или TikTok и приложите "
                    + "свой ID аккаунта вместе с чеком или скриншотом перевода.",
            "Після переказу напишіть @dkramochka в Telegram або TikTok і додайте "
                    + "свій ID акаунта разом із чеком або скріншотом переказу.",
            "Once it has gone through, write to @dkramochka on Telegram or TikTok "
                    + "with your account ID and the receipt.");

    static final String DONATE_BANNER_BUTTON = pick(
            "Пожертвовать", "Пожертвувати", "Donate");

    static final String DONATE_BANNER_WRITE = pick(
            "Написать в Telegram", "Написати в Telegram", "Write on Telegram");

    static final String REMIND_TITLE = pick(
            "Нравится TikTok MI?", "Подобається TikTok MI?", "Enjoying TikTok MI?");

    static final String REMIND_TEXT = pick(
            "Поддержи разработку и получи свой значок рядом с ником.",
            "Підтримай розробку й отримай свій значок поруч із ніком.",
            "Support the making of it and get a badge beside your name.");

    static final String REMIND_MORE = pick("Подробнее", "Докладніше", "Tell me more");

    static final String REMIND_NEVER = pick(
            "Больше не напоминать", "Більше не нагадувати", "Never remind me");

    static final String STREAK_TEST = pick(
            "Отправить тестовое сообщение", "Надіслати тестове повідомлення",
            "Send a test message");

    static final String STREAK_TEST_NOTE = pick(
            "Всем, у кого есть серия — даже если огонёк горит. Результат в дневнике.",
            "Усім, у кого є серія — навіть якщо вогник горить. Результат у щоденнику.",
            "To everyone with a streak, lit or not. The diary says what happened.");

    static final String STREAK_TEST_GOING = pick(
            "Отправляю, смотри дневник", "Надсилаю, дивись щоденник",
            "Sending; the diary will say");

    static final String CAT_FROM = pick("Принёс", "Приніс", "Brought by");

    static final String CAT_YOURS = pick(
            "Хочешь своего кота здесь? Напиши @dkramochka в Telegram",
            "Хочеш свого кота тут? Напиши @dkramochka в Telegram",
            "Want your cat here? Write to @dkramochka on Telegram");

    static final String CAT_WAIT = pick(
            "Коты ещё едут, попробуй ещё раз", "Коти ще їдуть, спробуй ще раз",
            "The cats are still on their way; try again");

    static final String SAVE = pick("Сохранить", "Зберегти", "Save");

    // -------------------------------------------------------- the downloads

    static final String DOWNLOADS = pick("Скачивание", "Завантаження", "Downloads");

    static final String NO_WATERMARK = pick(
            "Без водяного знака", "Без водяного знака", "Without the watermark");

    static final String DOWNLOAD_ALWAYS = pick(
            "Сохранять можно всё", "Зберігати можна все", "Save anything");

    static final String FEED = pick("Лента", "Стрічка", "Feed");

    static final String HIDE_ADS = pick(
            "Убирать рекламу", "Прибирати рекламу", "Drop the advertisements");

    static final String HIDE_LIVES = pick(
            "Скрыть прямые эфиры", "Приховати прямі ефіри", "Hide live streams");

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

    // ----------------------------------------------------------- the streaks

    static final String STREAKS = pick("Серии", "Серії", "Streaks");

    static final String STREAK_AUTO = pick(
            "Продлевать серии сами", "Продовжувати серії самі", "Keep streaks alive");

    static final String BETA = pick("бета", "бета", "beta");

    static final String STREAK_NOTE = pick(
            "Отправляет выбранный стикер тем, с кем серия вот-вот погаснет. "
                    + "Не чаще раза в сутки на человека.",
            "Надсилає вибраний стікер тим, з ким серія ось-ось згасне. "
                    + "Не частіше разу на добу на людину.",
            "Sends the sticker you picked to whoever the streak is about to lapse "
                    + "with, at most once a day each.");

    static final String STREAK_STICKER = pick(
            "Чем продлевать", "Чим продовжувати", "What to send");

    static final String STREAK_NOTHING = pick(
            "Откройте стикеры в переписке, и они появятся здесь",
            "Відкрийте стікери в листуванні, і вони з'являться тут",
            "Open the stickers in a chat and they will show up here");

    static final String STREAK_NEED_STICKER = pick(
            "Сначала откройте стикеры в любом чате TikTok!",
            "Спершу відкрийте стікери у будь-якому чаті TikTok!",
            "First open stickers in any TikTok chat!");

    // ------------------------------------------------------- the updates

    static final String UPDATE = pick("Обновление", "Оновлення", "An update");

    static final String UPDATE_THERE_IS = pick(
            "Вышла версия", "Вийшла версія", "There is a version");

    static final String UPDATE_GET = pick("Скачать", "Завантажити", "Get it");

    static final String UPDATE_LATER = pick("Не сейчас", "Не зараз", "Not now");

    static final String UPDATE_NEVER = pick(
            "Больше не напоминать", "Більше не нагадувати", "Stop reminding me");

    static final String UPDATE_GETTING = pick("Скачиваю", "Завантажую", "Getting it");

    static final String UPDATE_FAILED = pick(
            "Не получилось скачать", "Не вдалося завантажити", "Could not get it");

    static final String UPDATE_NONE = pick(
            "Уже последняя версия", "Вже остання версія", "This is the latest");

    static final String UPDATE_NO_ANSWER = pick(
            "GitHub не ответил", "GitHub не відповів", "GitHub did not answer");

    static final String UPDATE_ALLOW = pick(
            "Разрешите установку из этого источника, и я поставлю",
            "Дозвольте встановлення з цього джерела, і я поставлю",
            "Allow installing from this source and it will go on");

    static final String UPDATE_CHECK = pick(
            "Проверить обновления", "Перевірити оновлення", "Check for updates");

    static final String UPDATE_CHECKING = pick(
            "Проверка обновлений...", "Перевірка оновлень...", "Checking for updates...");

    static final String UPDATE_REMIND = pick(
            "Напоминать об обновлениях", "Нагадувати про оновлення",
            "Remind me about updates");

    static final String UPDATE_INSTALL = pick(
            "Установить скачанное", "Встановити завантажене", "Install what was downloaded");

    static final String VERSIONS = pick("Версии", "Версії", "Versions");

    static final String SOURCE = pick("Исходники", "Вихідники", "The source");

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

    static final String CLEAR = pick("Очистить", "Очистити", "Clear");

    static final String DIARY = pick(
            "Что мод видел", "Що мод бачив", "What the mod saw");

    static final String CLEARED = pick("Журнал очищен", "Журнал очищено", "The diary is empty");

    static final String ACCOUNT = pick("Аккаунт", "Акаунт", "Account");

    static final String ACCOUNT_ID = pick("ID", "ID", "ID");

    static final String ACCOUNT_SEC_ID = pick(
            "ID для ссылки", "ID для посилання", "The id a link is built from");

    static final String ACCOUNT_UNKNOWN = pick(
            "Пока неизвестен", "Поки невідомий", "Not seen yet");

    // ------------------------------------------------ Amegram Ecosystem
    static final String ECOSYSTEM = pick("Экосистема Amegram", "Екосистема Amegram", "Amegram Ecosystem");
    static final String ECOSYSTEM_CONNECTED = pick("● Amegram подключен", "● Amegram підключено", "● Amegram connected");
    static final String ECOSYSTEM_NOT_INSTALLED = pick("○ Amegram не установлен", "○ Amegram не встановлено", "○ Amegram not installed");
    static final String SYNC_THEME = pick("Синхронизация темы", "Синхронізація теми", "Mirror theme & accent");
    static final String SYNC_THEME_NOTE = pick(
            "Транслирует цвета акцента и тёмную тему в Amegram без перезапуска",
            "Транслює кольори акценту та темну тему в Amegram без перезапуску",
            "Broadcasts accent color and dark theme to Amegram without restart");
    static final String DIRECT_SHARE_SAVED = pick("1-Tap в Сохранённое", "1-Tap у Збережене", "1-Tap to Saved Messages");
    static final String DIRECT_SHARE_SAVED_NOTE = pick(
            "Отправка чистого видео сразу в Избранное Telegram без лишних диалогов",
            "Надсилання чистого відео одразу в Збережене Telegram без зайвих діалогів",
            "Sends clean video directly to Saved Messages without extra dialogs");
    static final String CLIPVAULT = pick("Фоновый буфер ClipVault", "Фоновий буфер ClipVault", "Ambient ClipVault");
    static final String CLIPVAULT_NOTE = pick(
            "Делится скопированными видео и звуками с Amegram без навязчивых окон",
            "Ділиться скопійованими відео та звуками з Amegram без нав'язливих вікон",
            "Silently shares copied video links and audio with Amegram without popups");

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
