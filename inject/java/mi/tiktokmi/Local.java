package mi.tiktokmi;

import java.util.HashMap;
import java.util.Map;

/**
 * The words of the interface that were written in Russian where they are used.
 *
 * Most of what the mod says is in {@link Text}, in three languages. A good
 * deal of what arrived with 2.0 was not: it was Russian, typed straight into
 * the screen that shows it, so a phone set to English or Ukrainian got a
 * settings screen in two languages at once. Those words are looked up here by
 * the Russian they were written in, which is what the code still holds --
 * `Local.of("Назад")` -- and which is what comes back when the phone is
 * Russian, or when nobody has translated a line yet.
 */
final class Local {

    private Local() {}

    /** Russian -> Ukrainian, English. */
    private static final Map<String, String[]> BOOK = new HashMap<String, String[]>();

    static String of(String russian) {
        int language = Text.language3();
        if (language == 0 || russian == null) return russian;
        String[] said = BOOK.get(russian);
        return said == null ? russian : said[language - 1];
    }

    private static void add(String russian, String ukrainian, String english) {
        BOOK.put(russian, new String[] {ukrainian, english});
    }

    static {
        add("Записывать подвисания", "Записувати підвисання", "Record stutters");
        add("Нужно, только если присылаешь отчёт о лагах. Пока включено, немного тратит батарею.", "Потрібно, лише якщо надсилаєш звіт про лаги. Поки ввімкнено, трохи витрачає батарею.", "Only needed when you send a lag report. While on, it costs a little battery.");
        add("Как буквы появляются", "Як літери з'являються", "How letters appear");
        add("мягко", "м'яко", "soft");
        add("резко", "різко", "sharp");
        add("Спонсор-блок", "Спонсор-блок", "Sponsor block");
        add("Куски, где автор что-то рекламирует, пропускаются сами, а вернуться можно кнопкой «Вернуть». Отметить рекламу: зажми «Поделиться» и выбери «Отметить рекламу». Нужен подтверждённый аккаунт.", "Шматки, де автор щось рекламує, пропускаються самі, а повернутися можна кнопкою «Повернути». Позначити рекламу: затисни «Поділитися» й вибери «Позначити рекламу». Потрібен підтверджений акаунт.", "Parts where the author advertises something are skipped, and “Back” takes you back. To mark one, hold Share and choose “Mark an advert”. A confirmed account is needed.");
        add("Выключи, если хочешь только видеть рекламу на шкале.", "Вимкни, якщо хочеш лише бачити рекламу на шкалі.", "Turn it off to only see adverts on the bar.");
        add("Одну и ту же рекламу вставляют во много роликов. Мод узнаёт её по звуку, даже если в этом ролике её никто не отмечал. Для этого он ещё раз скачивает лёгкую версию ролика.", "Одну й ту саму рекламу вставляють у багато роликів. Мод упізнає її за звуком, навіть якщо в цьому ролику її ніхто не позначав. Для цього він ще раз завантажує легку версію ролика.", "The same advert is put into many videos. The mod knows it by its sound even where nobody marked it. For that it fetches a light copy of the video once more.");
        add("По звуку и на мобильном интернете", "За звуком і на мобільному інтернеті", "By sound on mobile data too");
        add("Без этого по звуку ищется только на Wi-Fi. Лёгкая версия ролика весит обычно от 1 до 3 МБ.", "Без цього за звуком шукається лише на Wi-Fi. Легка версія ролика важить зазвичай від 1 до 3 МБ.", "Without it, sound is listened to on Wi-Fi only. A light copy of a video is usually 1 to 3 MB.");
        add("Искать рекламу по звуку", "Шукати рекламу за звуком", "Find adverts by their sound");
        add("Одну и ту же рекламу вставляют во много роликов. Мод узнаёт её по звуку, даже если в этом ролике её никто не отмечал. Работает только по Wi-Fi: мод ещё раз скачивает лёгкую версию ролика.", "Одну й ту саму рекламу вставляють у багато роликів. Мод упізнає її за звуком, навіть якщо в цьому ролику її ніхто не позначав. Працює лише через Wi-Fi: мод ще раз завантажує легку версію ролика.", "The same advert is put into many videos. The mod knows it by its sound even where nobody marked it. Only on Wi-Fi: the mod fetches a light copy of the video once more.");
        add("Потяни красные края к началу и концу рекламы и нажми ▶, чтобы послушать кусок.", "Потягни червоні краї до початку й кінця реклами й натисни ▶, щоб послухати шматок.", "Drag the red edges to where the advert starts and ends, and press ▶ to hear that part.");
        add("Играет", "Грає", "Playing");
        add("Секунду, видео ещё загружается", "Секунду, відео ще завантажується", "One moment, the video is still loading");
        add("Потяни красные края к началу и концу рекламы.", "Потягни червоні краї до початку й кінця реклами.", "Drag the red edges to where the advert starts and ends.");
        add("Кадры не загрузились", "Кадри не завантажилися", "The frames did not load");
        add("Загружаю кадры…", "Завантажую кадри…", "Loading frames…");
        add("Начало", "Початок", "Start");
        add("Конец", "Кінець", "End");
        add("Реклама", "Реклама", "Advert");
        add("Отметить рекламу", "Позначити рекламу", "Mark an advert");
        add("Где автор рекламирует что-то в видео", "Де автор щось рекламує у відео", "Where the author advertises something");
        add("Реклама в видео", "Реклама у відео", "Adverts in videos");
        add("Пропуск рекламы, которую читают авторы", "Пропуск реклами, яку читають автори", "Skipping adverts authors read out");
        add("Пропускать рекламу", "Пропускати рекламу", "Skip adverts");
        add("Куски, где автор рекламирует что-то, пропускаются сами, а вернуться можно кнопкой «Вернуть». Отметить рекламу: зажми «Поделиться» и выбери «Отметить рекламу». Нужен подтверждённый аккаунт.", "Шматки, де автор щось рекламує, пропускаються самі, а повернутися можна кнопкою «Повернути». Позначити рекламу: затисни «Поділитися» й вибери «Позначити рекламу». Потрібен підтверджений акаунт.", "Parts where the author advertises something are skipped, and “Back” takes you back. To mark one, hold Share and choose “Mark an advert”. A confirmed account is needed.");
        add("Показывать рекламу красным на шкале", "Показувати рекламу червоним на шкалі", "Show adverts red on the bar");
        add("Реклама пропущена", "Рекламу пропущено", "Advert skipped");
        add("Вернуть", "Повернути", "Back");
        add("Включи это видео и попробуй ещё раз", "Увімкни це відео й спробуй ще раз", "Play this video and try again");
        add("Нужен подтверждённый аккаунт", "Потрібен підтверджений акаунт", "A confirmed account is needed");
        add("Отмечать рекламу могут только подтверждённые аккаунты. Подтвердить можно в настройках мода, в разделе «Значки».", "Позначати рекламу можуть лише підтверджені акаунти. Підтвердити можна в налаштуваннях мода, у розділі «Значки».", "Only confirmed accounts can mark adverts. Confirm yours in the mod’s settings, under Badges.");
        add("Где реклама", "Де реклама", "Where the advert is");
        add("Двигай ползунки к началу и концу рекламы. Видео перематывается за ними.", "Рухай повзунки до початку й кінця реклами. Відео перемотується за ними.", "Move the handles to where the advert starts and ends. The video follows them.");
        add("Это не реклама", "Це не реклама", "Not an advert");
        add("Отправить", "Надіслати", "Send");
        add("Спасибо, учтём", "Дякуємо, врахуємо", "Thanks, noted");
        add("Готово, реклама будет пропускаться", "Готово, реклама пропускатиметься", "Done, the advert will be skipped");
        add("Спасибо! Реклама начнёт пропускаться, когда её отметят ещё несколько человек", "Дякуємо! Реклама почне пропускатися, коли її позначать ще кілька людей", "Thanks! It will be skipped once a few more people mark it");
        add("Сначала подтверди аккаунт в разделе «Значки»", "Спершу підтверди акаунт у розділі «Значки»", "Confirm your account under Badges first");
        add("Слишком много отметок, попробуй позже", "Забагато позначок, спробуй пізніше", "Too many marks, try later");
        add("Не получилось отправить, попробуй позже", "Не вдалося надіслати, спробуй пізніше", "Could not send, try later");
        add("с", "з", "from");
        add("до", "до", "to");
        add("пропущено", "пропущено", "skipped");
        add("в сети", "в мережі", "online");
        add("был(а) давно", "був(-ла) давно", "seen a while ago");
        add("ты тут?", "ти тут?", "you there?");
        add("Прочитано", "Прочитано", "Seen");
        add("Отправлено", "Надіслано", "Sent");
        add("Основные", "Основні", "Primary");
        add("Друзья", "Друзі", "Friends");
        add("Учёба", "Навчання", "School");
        add("привет, это я", "привіт, це я", "hi, it’s me");
        add("Репосты", "Репости", "Reposts");
        add("Понятно", "Зрозуміло", "Got it");
        add("Прячь, двигай и перекрашивай что угодно", "Ховай, рухай і перефарбовуй що завгодно", "Hide, move and recolour anything");
        add("Буквы пружинят, пока печатаешь", "Літери пружинять, поки друкуєш", "Letters bounce as you type");
        add("Удалить все репосты разом", "Видалити всі репости разом", "Remove all reposts at once");
        add("Нажми на что угодно в TikTok, чтобы спрятать, сдвинуть или перекрасить", "Натисни на що завгодно в TikTok, щоб сховати, зсунути чи перефарбувати", "Tap anything in TikTok to hide, move or recolour it");
        add("Работает в комментариях и сообщениях.", "Працює в коментарях і повідомленнях.", "Works in comments and messages.");
        add("Все репосты пропадут из профиля", "Усі репости зникнуть із профілю", "Every repost leaves your profile");
        add("Чтобы добавить чат в папку, зажми его и выбери «Добавить в папку».", "Щоб додати чат до папки, затисни його й вибери «Додати до папки».", "To put a chat in a folder, hold it and choose “Add to folder”.");
        add("Никто не увидит, что ты в сети, читаешь или печатаешь, смотрел историю или профиль. «В сети» спрячется после перезапуска TikTok.", "Ніхто не побачить, що ти в мережі, читаєш чи друкуєш, дивився історію чи профіль. «В мережі» сховається після перезапуску TikTok.", "Nobody will see that you are online, reading or typing, or that you looked at their story or profile. Being online hides after TikTok restarts.");
        add("Все", "Усі", "All");
        add("ПОЗИЦИЯ", "ПОЗИЦІЯ", "POSITION");
        add("центр", "центр", "centre");
        add("сдвиг", "зсув", "offset");
        add("шаг", "крок", "step");
        add("Длинные комментарии", "Довгі коментарі", "Long comments");
        add("Призрак", "Привид", "Ghost");
        add("Режим призрака", "Режим привида", "Ghost mode");
        add("TikTok не узнает, что ты в сети, прочитал чат, печатаешь или смотрел историю и профиль. Чтобы скрыть «в сети», после включения перезапусти приложение.",
                "TikTok не дізнається, що ти в мережі, прочитав чат, друкуєш чи дивився історію та профіль. Щоб приховати «в мережі», після ввімкнення перезапусти застосунок.",
                "TikTok will not learn that you are online, have read a chat, are typing, or have seen a story or a profile. To hide being online, restart the app after switching it on.");
        add("Не показывать «в сети»", "Не показувати «в мережі»", "Do not show as online");
        add("Не отправлять «прочитано»", "Не надсилати «прочитано»", "Do not send read receipts");
        add("Не отправлять «печатает»", "Не надсилати «друкує»", "Do not send typing");
        add("Не отмечать просмотр историй", "Не позначати перегляд історій", "Do not mark stories as seen");
        add("Не отмечать посещение профилей", "Не позначати відвідування профілів", "Do not mark profile visits");
        add("Входящие: что скрыть", "Вхідні: що приховати", "Inbox: what to hide");
        add("Скрыть полосу историй целиком", "Приховати смугу історій повністю", "Hide the whole stories strip");
        add("Скрыть «Создать» (свою историю)", "Приховати «Створити» (свою історію)", "Hide “Create” (your own story)");
        add("Скрыть истории друзей", "Приховати історії друзів", "Hide friends' stories");
        add("Скрыть эфиры", "Приховати ефіри", "Hide LIVE");
        add("Скрыть виджет и суперподписку в полосе", "Приховати віджет і суперпідписку в смузі", "Hide the widget and super subscription in the strip");
        add("Скрыть заметки над аватарками", "Приховати нотатки над аватарками", "Hide notes over avatars");
        add("Скрыть «Ваши вероятные знакомые»", "Приховати «Ваші ймовірні знайомі»", "Hide “People you may know”");
        add("Изменённые", "Змінені", "Changed");
        add("Изменённых элементов нет", "Змінених елементів немає", "Nothing has been changed");
        add("Вернуть как было", "Повернути як було", "Put back as it was");
        add("скрыт", "приховано", "hidden");
        add("сдвинут", "зсунуто", "moved");
        add("размер", "розмір", "size");
        add("цвет", "колір", "colour");
        add("вложенный / родитель", "вкладений / батьківський", "inner / parent");
        add("Анимация набора", "Анімація набору", "Typing animation");
        add("Буквы появляются плавно, пока вы печатаете, в комментариях и сообщениях.", "Літери з'являються плавно, поки ви друкуєте, у коментарях і повідомленнях.", "Letters appear smoothly as you type, in comments and messages.");
        add("Анимация набора текста", "Анімація набору тексту", "Typing animation");
        add("Размытие при появлении", "Розмиття при появі", "Blur on appearing");
        add("Подъём снизу", "Підйом знизу", "Rise from below");
        add("Увеличение (пружина)", "Збільшення (пружина)", "Grow (spring)");
        add("Лёгкий поворот", "Легкий поворот", "Slight tilt");
        add("Анимация удаления", "Анімація видалення", "Deleting animation");
        add("Плавный курсор", "Плавний курсор", "Gliding cursor");
        add("Кривая", "Крива", "Curve");
        add("мягкая", "м'яка", "soft");
        add("пружина", "пружина", "spring");
        add("резкая", "різка", "sharp");
        add("выскок", "виска", "pop-out");
        add("Буквы выезжают и пружинят, удалённые улетают, курсор плывёт. В комментариях и сообщениях.", "Літери виїжджають і пружинять, видалені відлітають, курсор пливе. У коментарях і повідомленнях.", "Letters spring in, deleted ones fly away, the cursor glides. In comments and messages.");
        add("Скорость", "Швидкість", "Speed");
        add("быстро", "швидко", "fast");
        add("плавно", "плавно", "smooth");
        add("медленно", "повільно", "slow");
        add("СЕТКА", "СІТКА", "GRID");
        add("Выкл", "Вимк", "Off");
        add("Центр и линии от углов", "Центр і лінії від кутів", "Centre and lines from corners");
        add("Сдвиг идёт по линиям сетки", "Зсув іде по лініях сітки", "Moves go along the grid lines");
        add("Скрыть, сдвинуть, увеличить или перекрасить любой элемент TikTok", "Приховати, зсунути, збільшити чи перефарбувати будь-який елемент TikTok", "Hide, move, enlarge or recolour any part of TikTok");
        add("Буквы выпрыгивают, удалённые улетают, курсор плывёт", "Літери вистрибують, видалені відлітають, курсор пливе", "Letters pop in, deleted ones fly away, the cursor glides");
        add("Мои репосты", "Мої репости", "My reposts");
        add("Удалить все свои репосты разом", "Видалити всі свої репости разом", "Remove all your reposts at once");
        add("Удалить все мои репосты", "Видалити всі мої репости", "Remove all my reposts");
        add("Отменит каждый ваш репост, по одному", "Скасує кожен ваш репост, по одному", "Takes back every repost of yours, one by one");
        add("Личные сообщения", "Особисті повідомлення", "Direct messages");
        add("Папки чатов и полоса историй", "Папки чатів і смуга історій", "Chat folders and the stories strip");
        add("Что скрыть во «Входящих»", "Що приховати у «Вхідних»", "What to hide in the inbox");
        add("Без «в сети», «прочитано» и «печатает»", "Без «в мережі», «прочитано» і «друкує»", "No online, read or typing");
        add("Чаты, серии, уведомления", "Чати, серії, сповіщення", "Chats, streaks, notifications");
        add("Без ограничения TikTok на длину комментария.", "Без обмеження TikTok на довжину коментаря.", "Without TikTok's limit on a comment's length.");
        add("Редактор интерфейса", "Редактор інтерфейсу", "Interface editor");
        add("Открыть редактор", "Відкрити редактор", "Open the editor");
        add("Нажимайте на любой элемент TikTok: скрыть, сдвинуть, изменить размер и цвет", "Натискайте на будь-який елемент TikTok: приховати, зсунути, змінити розмір і колір", "Tap anything in TikTok: hide it, move it, resize or recolour it");
        add("Откройте нужный экран TikTok", "Відкрийте потрібний екран TikTok", "Open the TikTok screen you want");
        add("Сбросить все правки", "Скинути всі правки", "Reset every change");
        add("Папки в личных сообщениях", "Папки в особистих повідомленнях", "Folders in direct messages");
        add("Добавить чат в папку: долгое нажатие на чат, «Добавить в папку».", "Додати чат до папки: довге натискання на чат, «Додати до папки».", "To put a chat in a folder: long-press it, then “Add to folder”.");
        add("Автор LotusTok: призрак, папки, редактор интерфейса, анимация набора и другое", "Автор LotusTok: привид, папки, редактор інтерфейсу, анімація набору та інше", "Made LotusTok: the ghost, folders, the interface editor, the typing animation and more");
        add("Плагин для TikTok Mi: из него призрак и длинные комментарии", "Плагін для TikTok Mi: з нього привид і довгі коментарі", "A TikTok Mi plugin: the ghost and long comments come from it");
        add("Код плагина не удалось разобрать: ",
                "Код плагіна не вдалося розібрати: ",
                "The plugin's code could not be read: ");
        add("Задачи  ·  Свернуть",
                "Завдання  ·  Згорнути",
                "Tasks  ·  Collapse");
        add("Нет сетевых задач",
                "Немає мережевих завдань",
                "No network tasks");
        add("Загрузка",
                "Завантаження",
                "Downloading");
        add("Отменить",
                "Скасувати",
                "Cancel");
        add("Повторить",
                "Повторити",
                "Retry");
        add("Загрузка завершена",
                "Завантаження завершено",
                "Download finished");
        add("Лучшее",
                "Найкраще",
                "Best");
        add(" кбит/с",
                " кбіт/с",
                " kbit/s");
        add("Фото ",
                "Фото ",
                "Photo ");
        add("Отмена…",
                "Скасування…",
                "Cancelling…");
        add("Готово",
                "Готово",
                "Done");
        add("Без аккаунта",
                "Без акаунта",
                "No account");
        add("Аккаунт ",
                "Акаунт ",
                "Account ");
        add("Мод",
                "Мод",
                "Mod");
        add("Этот аккаунт",
                "Цей акаунт",
                "This account");
        add("Завершено",
                "Завершено",
                "Finished");
        add("Отменено",
                "Скасовано",
                "Cancelled");
        add("Ошибка",
                "Помилка",
                "Failed");
        add("Сохранять комментарии",
                "Зберігати коментарі",
                "Save comments");
        add("Сохранённые комментарии",
                "Збережені коментарі",
                "Saved comments");
        add("Своё значение",
                "Власне значення",
                "Custom value");
        add(" видео",
                " відео",
                " videos");
        add("Количество видео",
                "Кількість відео",
                "Number of videos");
        add("Отмена",
                "Скасувати",
                "Cancel");
        add("Выбрать",
                "Вибрати",
                "Choose");
        add("Введите число от 1 до 10000",
                "Введіть число від 1 до 10000",
                "Enter a number from 1 to 10000");
        add("Не удалось выбрать количество",
                "Не вдалося вибрати кількість",
                "Could not set the number");
        add("500 видео",
                "500 відео",
                "500 videos");
        add("1000 видео",
                "1000 відео",
                "1000 videos");
        add("Количество видео: 1–10000",
                "Кількість відео: 1–10000",
                "Number of videos: 1–10000");
        add("Не удалось сохранить комментарии. Повторите загрузку при подключении к интернету.",
                "Не вдалося зберегти коментарі. Повторіть завантаження, коли буде інтернет.",
                "Could not save the comments. Download again when you are online.");
        add("Количество комментариев: 1–1000",
                "Кількість коментарів: 1–1000",
                "Number of comments: 1–1000");
        add("Больше комментариев — дольше загрузка и больше места. Популярные в приоритете. Лайки сохраняются на момент загрузки. Применяется к следующим офлайн-загрузкам.",
                "Більше коментарів — довше завантаження і більше місця. Популярні мають пріоритет. Вподобання зберігаються на момент завантаження. Діє для наступних офлайн-завантажень.",
                "More comments take longer to download and more space. Popular ones come first. Likes are saved as they were when downloaded. Applies to the next offline downloads.");
        add("Комментарии офлайн",
                "Коментарі офлайн",
                "Offline comments");
        add("Сохранить",
                "Зберегти",
                "Save");
        add("Введите число от 1 до 1000",
                "Введіть число від 1 до 1000",
                "Enter a number from 1 to 1000");
        add("ОК",
                "ОК",
                "OK");
        add("Видео ",
                "Відео ",
                "Video ");
        add("Сохранённых комментариев пока нет. Включите сохранение перед загрузкой офлайн-видео.",
                "Збережених коментарів поки немає. Увімкніть збереження перед завантаженням офлайн-відео.",
                "No saved comments yet. Turn saving on before downloading videos for offline.");
        add("Закрыть",
                "Закрити",
                "Close");
        add("Очередь занята. Повторите чуть позже.",
                "Черга зайнята. Повторіть трохи пізніше.",
                "The queue is busy. Try again a little later.");
        add("Комментарии ещё загружаются.",
                "Коментарі ще завантажуються.",
                "The comments are still downloading.");
        add("Для этого видео комментарии не сохранены. Включите сохранение перед его офлайн-загрузкой.",
                "Для цього відео коментарі не збережено. Увімкніть збереження перед його офлайн-завантаженням.",
                "No comments were saved for this video. Turn saving on before downloading it for offline.");
        add("Сохранено: ",
                "Збережено: ",
                "Saved: ");
        add(" · лайки на момент загрузки",
                " · вподобання на момент завантаження",
                " · likes as of the download");
        add("У видео нет доступных комментариев.",
                "У відео немає доступних коментарів.",
                "This video has no comments available.");
        add("Поделиться ZIP",
                "Поділитися ZIP",
                "Share as ZIP");
        add("Не удалось прочитать архив комментариев.",
                "Не вдалося прочитати архів коментарів.",
                "Could not read the comment archive.");
        add("Архив комментариев",
                "Архів коментарів",
                "Comment archive");
        add("Не удалось открыть приложение для передачи ZIP.",
                "Не вдалося відкрити застосунок для передачі ZIP.",
                "Could not open an app to share the ZIP.");
        add("Превышен размер файла плагина",
                "Перевищено розмір файлу плагіна",
                "The plugin file is too large");
        add("Загрузка дополнительного исполняемого кода",
                "Завантаження додаткового виконуваного коду",
                "Loads more executable code");
        add("Рефлексия: часть действий нельзя установить статически",
                "Рефлексія: частину дій не можна встановити статично",
                "Reflection: some of what it does cannot be told from the code");
        add("Доступ к сети",
                "Доступ до мережі",
                "Network access");
        add("WebView и возможное выполнение JavaScript",
                "WebView і можливе виконання JavaScript",
                "A WebView, and possibly running JavaScript");
        add("Доступ к личным данным устройства",
                "Доступ до особистих даних пристрою",
                "Access to personal data on the device");
        add("Доступ к буферу обмена",
                "Доступ до буфера обміну",
                "Clipboard access");
        add("Прямой доступ к файлам приложения",
                "Прямий доступ до файлів застосунку",
                "Direct access to the app's files");
        add("Запуск системных команд",
                "Запуск системних команд",
                "Runs system commands");
        add("Загрузка нативных библиотек",
                "Завантаження нативних бібліотек",
                "Loads native libraries");
        add("Поиск классов по имени: возможна скрытая загрузка API",
                "Пошук класів за назвою: можливе приховане завантаження API",
                "Looks classes up by name: may reach APIs out of sight");
        add("Сеть через API мода",
                "Мережа через API мода",
                "Network through the mod's API");
        add("Доступ к контексту TikTok",
                "Доступ до контексту TikTok",
                "Access to TikTok's context");
        add("Загрузка классов по имени",
                "Завантаження класів за назвою",
                "Loads classes by name");
        add("Некорректная таблица DEX",
                "Некоректна таблиця DEX",
                "Malformed DEX table");
        add("Нативные методы вне проверки Java-кода",
                "Нативні методи поза перевіркою Java-коду",
                "Native methods, which this check cannot read");
        add("Некорректный DEX",
                "Некоректний DEX",
                "Malformed DEX");
        add("Неподдерживаемая версия DEX",
                "Непідтримувана версія DEX",
                "Unsupported DEX version");
        add("Некорректный заголовок DEX",
                "Некоректний заголовок DEX",
                "Malformed DEX header");
        add("Повреждён DEX: SHA-1",
                "Пошкоджено DEX: SHA-1",
                "Corrupt DEX: SHA-1");
        add("Повреждён DEX: checksum",
                "Пошкоджено DEX: checksum",
                "Corrupt DEX: checksum");
        add("Слишком большое значение DEX",
                "Завелике значення DEX",
                "DEX value too large");
        add("Таблица за границами DEX",
                "Таблиця за межами DEX",
                "Table outside the DEX");
        add("Слишком сложный DEX для проверки",
                "Занадто складний DEX для перевірки",
                "DEX too complex to check");
        add("Переполнение DEX",
                "Переповнення DEX",
                "DEX overflow");
        add("Некорректный ULEB128",
                "Некоректний ULEB128",
                "Malformed ULEB128");
        add("Индекс строки DEX",
                "Індекс рядка DEX",
                "DEX string index");
        add("Слишком большие строки DEX",
                "Завеликі рядки DEX",
                "DEX strings too large");
        add("Индекс типа DEX",
                "Індекс типу DEX",
                "DEX type index");
        add("Нет ссылки на файл плагина",
                "Немає посилання на файл плагіна",
                "No link to the plugin file");
        add("Слишком длинный адрес каталога",
                "Задовга адреса каталогу",
                "The catalog address is too long");
        add("Каталог и файлы должны использовать HTTPS без логина в адресе",
                "Каталог і файли мають використовувати HTTPS без логіна в адресі",
                "The catalog and its files must use HTTPS with no login in the address");
        add("Нужен аккаунт создателя",
                "Потрібен акаунт автора",
                "The author's account is needed");
        add("Плагин не установлен",
                "Плагін не встановлено",
                "The plugin is not installed");
        add("Пока пусто — нажмите +",
                "Поки порожньо — натисніть +",
                "Nothing yet — tap +");
        add("Добавить в магазин",
                "Додати в магазин",
                "Add to the store");
        add("Данные плагина",
                "Дані плагіна",
                "Plugin details");
        add("Новый плагин",
                "Новий плагін",
                "New plugin");
        add("Код сохраняется. После изменения плагин нужно включить заново.",
                "Код зберігається. Після зміни плагін треба увімкнути знову.",
                "The code is kept. After a change the plugin has to be switched on again.");
        add("Название",
                "Назва",
                "Name");
        add("Версия",
                "Версія",
                "Version");
        add("Автор",
                "Автор",
                "Author");
        add("ID: латиница, например my.plugin",
                "ID: латиниця, наприклад my.plugin",
                "ID: Latin letters, like my.plugin");
        add("Описание, до 240 символов",
                "Опис, до 240 символів",
                "Description, up to 240 characters");
        add("Версия TikTok (необязательно)",
                "Версія TikTok (необов'язково)",
                "TikTok version (optional)");
        add("Кнопки",
                "Кнопки",
                "Buttons");
        add("Сообщение, копирование текста или окно. До 16.",
                "Повідомлення, копіювання тексту або вікно. До 16.",
                "A message, copying text, or a window. Up to 16.");
        add("Не больше 16 кнопок",
                "Не більше 16 кнопок",
                "No more than 16 buttons");
        add("Имена",
                "Імена",
                "Names");
        add("Другое имя у аккаунта. UID — число из ссылки на профиль.",
                "Інше ім'я в акаунта. UID — число з посилання на профіль.",
                "Another name for an account. The UID is the number in the profile link.");
        add("Не больше 16 имён",
                "Не більше 16 імен",
                "No more than 16 names");
        add("Цвета",
                "Кольори",
                "Colours");
        add("Один цвет заменяется другим по всему приложению.",
                "Один колір замінюється іншим у всьому застосунку.",
                "One colour is replaced by another across the app.");
        add("Не больше 16 цветов",
                "Не більше 16 кольорів",
                "No more than 16 colours");
        add("Показать JSON",
                "Показати JSON",
                "Show JSON");
        add("Исходник",
                "Вихідний код",
                "Source");
        add("Применить",
                "Застосувати",
                "Apply");
        add("Публикация в магазине доступна только аккаунту создателя.",
                "Публікація в магазині доступна лише акаунту автора.",
                "Only the author's account can publish to the store.");
        add("Опубликовать в магазине",
                "Опублікувати в магазині",
                "Publish to the store");
        add("В магазин",
                "У магазин",
                "To the store");
        add("Иконка плагина",
                "Іконка плагіна",
                "Plugin icon");
        add("Иконка",
                "Іконка",
                "Icon");
        add("Выбрать другую",
                "Вибрати іншу",
                "Choose another");
        add("Убрать",
                "Прибрати",
                "Remove");
        add("На устройстве нет выбора файлов",
                "На пристрої немає вибору файлів",
                "This device has no file picker");
        add("Название кнопки",
                "Назва кнопки",
                "Button label");
        add("Сообщение",
                "Повідомлення",
                "Message");
        add("Копировать",
                "Копіювати",
                "Copy");
        add("Окно",
                "Вікно",
                "Window");
        add("Текст",
                "Текст",
                "Text");
        add("UID аккаунта",
                "UID акаунта",
                "Account UID");
        add("Показывать как",
                "Показувати як",
                "Show as");
        add("Заполните название, версию и автора",
                "Заповніть назву, версію й автора",
                "Fill in the name, version and author");
        add("Для магазина размер .mtp должен быть не больше 1 МБ",
                "Для магазину розмір .mtp має бути не більше 1 МБ",
                "For the store an .mtp must be 1 MB or less");
        add("Опубликовано в магазине",
                "Опубліковано в магазині",
                "Published to the store");
        add("Сохранено",
                "Збережено",
                "Saved");
        add("Не сохранено",
                "Не збережено",
                "Not saved");
        add("Проверьте поля",
                "Перевірте поля",
                "Check the fields");
        add("Не удалось прочитать иконку",
                "Не вдалося прочитати іконку",
                "Could not read the icon");
        add("Иконка слишком большая",
                "Іконка завелика",
                "The icon is too large");
        add("Файл больше ",
                "Файл більший за ",
                "The file is over ");
        add(" МБ",
                " МБ",
                " MB");
        add(" КБ",
                " КБ",
                " KB");
        add(" — магазин такой не примет",
                " — магазин такий не прийме",
                " — the store will not take it");
        add("В файле лишнее: ",
                "У файлі зайве: ",
                "The file has something extra: ");
        add(". Нужны только manifest.json, classes.dex и icon.png",
                ". Потрібні лише manifest.json, classes.dex та icon.png",
                ". Only manifest.json, classes.dex and icon.png belong in it");
        add("Файл повреждён",
                "Файл пошкоджено",
                "The file is damaged");
        add("Это не плагин: нет manifest.json или classes.dex",
                "Це не плагін: немає manifest.json або classes.dex",
                "Not a plugin: no manifest.json or classes.dex");
        add("В manifest.json неверный ID",
                "У manifest.json неправильний ID",
                "The ID in manifest.json is wrong");
        add("Ничего особенного не найдено.",
                "Нічого особливого не знайдено.",
                "Nothing out of the ordinary.");
        add("Не удалось подготовить плагин",
                "Не вдалося підготувати плагін",
                "Could not prepare the plugin");
        add("Плагин содержит лишние, вложенные или повторяющиеся файлы",
                "Плагін містить зайві, вкладені або повторювані файли",
                "The plugin has extra, nested or repeated files");
        add("Недопустимый путь плагина",
                "Неприпустимий шлях плагіна",
                "A path in the plugin is not allowed");
        add("Плагин превышает ограничение размера",
                "Плагін перевищує обмеження розміру",
                "The plugin is over the size limit");
        add("В плагине нет manifest.json или classes.dex",
                "У плагіні немає manifest.json або classes.dex",
                "The plugin has no manifest.json or classes.dex");
        add("Плагин не найден",
                "Плагін не знайдено",
                "Plugin not found");
        add("Некорректный manifest.json",
                "Некоректний manifest.json",
                "Malformed manifest.json");
        add("Плагин изменился после проверки",
                "Плагін змінився після перевірки",
                "The plugin changed after it was looked at");
        add("Не удалось сохранить предыдущий плагин",
                "Не вдалося зберегти попередній плагін",
                "Could not keep the previous plugin");
        add("Невозможно прочитать плагин",
                "Неможливо прочитати плагін",
                "Cannot read the plugin");
        add("Предпросмотр",
                "Попередній перегляд",
                "Preview");
        add("Неизвестное поле команды",
                "Невідоме поле команди",
                "Unknown field in a command");
        add("Некорректное поле: ",
                "Некоректне поле: ",
                "Malformed field: ");
        add("Управляющие символы запрещены",
                "Керівні символи заборонено",
                "Control characters are not allowed");
        add("Слишком вложенный код",
                "Занадто вкладений код",
                "The code is nested too deep");
        add("Плагин больше 64 КБ",
                "Плагін більший за 64 КБ",
                "The plugin is over 64 KB");
        add("Неизвестный формат",
                "Невідомий формат",
                "Unknown format");
        add("Иконка должна быть PNG до 32 КБ",
                "Іконка має бути PNG до 32 КБ",
                "The icon must be a PNG of 32 KB or less");
        add("Некорректный ID",
                "Некоректний ID",
                "Malformed ID");
        add("Ожидается список: ",
                "Очікується список: ",
                "A list is expected: ");
        add("Не больше 16 команд в списке",
                "Не більше 16 команд у списку",
                "No more than 16 commands in a list");
        add("Команда не разрешена: ",
                "Команду не дозволено: ",
                "Command not allowed: ");
        add("Нужен ID аккаунта",
                "Потрібен ID акаунта",
                "An account ID is needed");
        add("Цвет: #RRGGBB",
                "Колір: #RRGGBB",
                "Colour: #RRGGBB");
        add("Не больше 16 плагинов",
                "Не більше 16 плагінів",
                "No more than 16 plugins");
        add("Выключить",
                "Вимкнути",
                "Switch off");
        add("Включить",
                "Увімкнути",
                "Switch on");
        add("Редактировать",
                "Редагувати",
                "Edit");
        add("Ещё",
                "Ще",
                "More");
        add("Отправить на проверку",
                "Надіслати на перевірку",
                "Send for review");
        add("Копировать исходник",
                "Копіювати вихідний код",
                "Copy the source");
        add("Удалить",
                "Видалити",
                "Delete");
        add("Скопировано",
                "Скопійовано",
                "Copied");
        add("Задачи",
                "Завдання",
                "Tasks");
        add("Сохранено в ",
                "Збережено в ",
                "Saved to ");
        add("Подсказки",
                "Підказки",
                "Hints");
        add("Обучение",
                "Навчання",
                "Tutorial");
        add("Глаз в ленте включает и выключает приглушение на лету.",
                "Око в стрічці вмикає й вимикає приглушення на льоту.",
                "The eye in the feed turns dimming on and off.");
        add("Тормозит приложение: подвисаний за этот запуск",
                "Гальмує застосунок: підвисань за цей запуск",
                "Slows the app down: stalled this run for");
        add("с", "с", "s");
        add("Добавить плагин",
                "Додати плагін",
                "Add a plugin");
        add("Пока нет установленных плагинов",
                "Поки немає встановлених плагінів",
                "No plugins installed yet");
        add("Включён",
                "Увімкнено",
                "On");
        add("Выключен",
                "Вимкнено",
                "Off");
        add("Время отправки",
                "Час надсилання",
                "Time to send");
        add("Отчёты о сбоях",
                "Звіти про збої",
                "Crash reports");
        add("Технический стек сбоя, без сообщений и аккаунта.",
                "Технічний стек збою, без повідомлень і акаунта.",
                "A technical crash trace, without messages or account.");
        add("Выше",
                "Вище",
                "Up");
        add("Ниже",
                "Нижче",
                "Down");
        add("Укажите цвет в формате #RRGGBB",
                "Вкажіть колір у форматі #RRGGBB",
                "Give the colour as #RRGGBB");
        add("Создать из кнопок",
                "Створити з кнопок",
                "Build from buttons");
        add("Вставить JSON",
                "Вставити JSON",
                "Paste JSON");
        add("Установить файл .mtp",
                "Встановити файл .mtp",
                "Install an .mtp file");
        add("Магазин",
                "Магазин",
                "Store");
        add("Документация",
                "Документація",
                "Documentation");
        add("Удерживайте для действий",
                "Утримуйте для дій",
                "Hold for actions");
        add("Удалить с устройства",
                "Видалити з пристрою",
                "Delete from the device");
        add("Управление магазином",
                "Керування магазином",
                "Manage the store");
        add("Включите плагин, чтобы открыть его настройки.",
                "Увімкніть плагін, щоб відкрити його налаштування.",
                "Switch the plugin on to open its settings.");
        add("Плагин не предоставил настройки. Версия ",
                "Плагін не надав налаштувань. Версія ",
                "The plugin has no settings. Version ");
        add("Плагин включён, но не запущен: ",
                "Плагін увімкнено, але не запущено: ",
                "The plugin is on but not running: ");
        add("Плагин включён, но ещё не запущен в этом сеансе.",
                "Плагін увімкнено, але ще не запущено в цьому сеансі.",
                "The plugin is on but has not started in this session yet.");
        add("Запустить заново",
                "Запустити знову",
                "Start again");
        add("Назад",
                "Назад",
                "Back");
        add("Плагин не принят",
                "Плагін не прийнято",
                "The plugin was not accepted");
        add("Настройки",
                "Налаштування",
                "Settings");
        add("Включено",
                "Увімкнено",
                "On");
        add("Выключено",
                "Вимкнено",
                "Off");
        add("Включена",
                "Увімкнена",
                "On");
        add("Выключена",
                "Вимкнена",
                "Off");
        add("Реклама скрыта",
                "Рекламу приховано",
                "Ads hidden");
        add("Реклама разрешена",
                "Рекламу дозволено",
                "Ads allowed");
        add("Без водяного знака",
                "Без водяного знака",
                "No watermark");
        add("Обычное сохранение",
                "Звичайне збереження",
                "Ordinary saving");
        add("Откройте свой профиль",
                "Відкрийте свій профіль",
                "Open your profile");
        add("Открыть настройки",
                "Відкрити налаштування",
                "Open settings");
        add("Оформление",
                "Оформлення",
                "Appearance");
        add("Профиль",
                "Профіль",
                "Profile");
        add("Лента и видео",
                "Стрічка та відео",
                "Feed and video");
        add("Инструменты",
                "Інструменти",
                "Tools");
        add("О проекте",
                "Про проєкт",
                "About");
        add("Плагины",
                "Плагіни",
                "Plugins");
        add("Серии и уведомления",
                "Серії та сповіщення",
                "Streaks and notifications");
        add("Акцент меняет цвет кнопок и вкладок. Тема и шрифты отдельно.",
                "Акцент змінює колір кнопок і вкладок. Тема й шрифти окремо.",
                "The accent recolours buttons and tabs. Theme and fonts are separate.");
        add("Значки и градиент на ник. Сначала надо один раз подтвердить аккаунт.",
                "Значки й градієнт на нік. Спершу треба один раз підтвердити акаунт.",
                "Badges and a gradient name. Confirm the account once first.");
        add("Удержание «Отправить» открывает скачивание и перемотку, удержание края видео ускоряет его.",
                "Утримання «Надіслати» відкриває завантаження й перемотування, утримання краю відео пришвидшує його.",
                "Hold Share for downloading and seeking; hold the edge of a video to speed it up.");
        add("Скачивание, копии настроек, диагностика и журнал.",
                "Завантаження, копії налаштувань, діагностика й журнал.",
                "Downloads, settings backups, diagnostics and the diary.");
        add("Здесь находятся версия мода, обновления, журнал и участники.",
                "Тут версія мода, оновлення, журнал і учасники.",
                "The mod's version, updates, the diary and the people behind it.");
        add("Магазин и настройки плагинов. Ставь только те, которым доверяешь.",
                "Магазин і налаштування плагінів. Став лише ті, яким довіряєш.",
                "Plugin store and settings. Install only what you trust.");
        add("Автопродление серий и уведомления мода.",
                "Автопродовження серій і сповіщення мода.",
                "Streaks kept going on their own, and the mod's notifications.");
        add("Понятно",
                "Зрозуміло",
                "Got it");
        add("Акцент, тема и шрифты",
                "Акцент, тема та шрифти",
                "Accent, theme and fonts");
        add("Значки, градиент и аккаунт",
                "Значки, градієнт і акаунт",
                "Badges, gradient and account");
        add("Просмотр, кнопки, защита экрана и регион",
                "Перегляд, кнопки, захист екрана та регіон",
                "Viewing, buttons, screen protection and region");
        add("Скачивание, копии и диагностика",
                "Завантаження, копії та діагностика",
                "Downloads, backups and diagnostics");
        add("Версия, обновления и участники",
                "Версія, оновлення та учасники",
                "Version, updates and people");
        add("Магазин и установленные",
                "Магазин і встановлені",
                "The store and what is installed");
        add("Серии и уведомления",
                "Серії та сповіщення",
                "Streaks and notifications");
        add("Защита экрана",
                "Захист екрана",
                "Screen protection");
        add("Потяните вниз, чтобы закрыть",
                "Потягніть униз, щоб закрити",
                "Pull down to close");
        add("Каталог ещё не подключён",
                "Каталог ще не підключено",
                "No catalog connected yet");
        add("Подключить каталог",
                "Підключити каталог",
                "Connect a catalog");
        add("Каталог · изменить адрес",
                "Каталог · змінити адресу",
                "Catalog · change the address");
        add("Обновление…",
                "Оновлення…",
                "Refreshing…");
        add("Обновить",
                "Оновити",
                "Refresh");
        add("В каталоге пока пусто",
                "У каталозі поки порожньо",
                "The catalog is empty for now");
        add("Скачанные плагины остаются выключенными, пока вы их не включите.",
                "Завантажені плагіни лишаються вимкненими, доки ви їх не ввімкнете.",
                "Downloaded plugins stay off until you switch them on.");
        add("Магазин плагинов",
                "Магазин плагінів",
                "Plugin store");
        add("SHA-256 файла не совпадает с каталогом",
                "SHA-256 файлу не збігається з каталогом",
                "The file's SHA-256 does not match the catalog");
        add("ID плагина отличается от каталога",
                "ID плагіна відрізняється від каталогу",
                "The plugin's ID is not the one in the catalog");
        add("Каталог плагинов",
                "Каталог плагінів",
                "Plugin catalog");
        add("Каталог TikTok Mi подключён по умолчанию. Можно указать другой HTTPS JSON-каталог; пустой адрес отключает магазин.",
                "Каталог TikTok Mi підключено за замовчуванням. Можна вказати інший HTTPS JSON-каталог; порожня адреса вимикає магазин.",
                "TikTok Mi's catalog is connected by default. You can give another HTTPS JSON catalog; an empty address switches the store off.");
        add("Серии",
                "Серії",
                "Streaks");
        add("Серия ждёт продления",
                "Серія чекає на продовження",
                "A streak is waiting");
        add("Откройте TikTok: автопродление сработает сразу",
                "Відкрийте TikTok: автопродовження спрацює одразу",
                "Open TikTok: it will be kept alive straight away");
        add("Автопродление выключено",
                "Автопродовження вимкнено",
                "Keeping streaks alive is off");
        add("точный будильник не разрешён (Android 12+: «Будильники и напоминания»)",
                "точний будильник не дозволено (Android 12+: «Будильники та нагадування»)",
                "exact alarms are not allowed (Android 12+: Alarms & reminders)");
        add("экономия батареи не отключена для приложения",
                "економію батареї не вимкнено для застосунку",
                "battery saving is still on for the app");
        add("уведомления приложения выключены",
                "сповіщення застосунку вимкнено",
                "the app's notifications are off");
        add("время не выбрано",
                "час не вибрано",
                "no time chosen");
        add("Готово: отправка в ",
                "Готово: надсилання о ",
                "Ready: sending at ");
        add("Проверьте: ",
                "Перевірте: ",
                "Check: ");
        add("Состояние неизвестно",
                "Стан невідомий",
                "State unknown");
        add("Когда серия ждёт продления",
                "Коли серія чекає на продовження",
                "When a streak is waiting");
        add("В %02d:%02d",
                "О %02d:%02d",
                "At %02d:%02d");
        add("Время автопродления",
                "Час автопродовження",
                "Time to keep streaks alive");
        add("В выбранное время устройства — одна отправка в день друзьям с действующим огоньком. Для точного запуска в фоне разрешите будильники и работу без ограничений батареи.",
                "У вибраний час пристрою — одне надсилання на день друзям із чинним вогником. Для точного запуску у фоні дозвольте будильники й роботу без обмежень батареї.",
                "At the chosen time on this device, one send a day to friends with a live flame. For it to run on time in the background, allow alarms and unrestricted battery use.");
        add("Без расписания",
                "Без розкладу",
                "No schedule");
        add("Обновление TikTok Mi",
                "Оновлення TikTok Mi",
                "TikTok Mi update");
        add(" с",
                " с",
                " s");
        add("Графити",
                "Графіті",
                "Graffiti");
        add("Блестящий",
                "Блискучий",
                "Shiny");
        add("Косплей на ТикТок",
                "Косплей на ТікТок",
                "TikTok cosplay");
        add("Глитч",
                "Глітч",
                "Glitch");
        add("Точечная",
                "Крапкова",
                "Dotted");
        add("Терминал",
                "Термінал",
                "Terminal");
        add("Гламурная",
                "Гламурна",
                "Glamour");
        add("Толстый тилкайо",
                "Товстий тілкайо",
                "Fat tilcayo");
    }
}
