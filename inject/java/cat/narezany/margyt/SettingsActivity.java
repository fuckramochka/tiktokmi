package cat.narezany.margyt;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * MargyT's own screen, drawn in TikTok's settings language.
 *
 * Page, cards, title, grey section labels: the shapes are TikTok's, and so are
 * the colours -- not copied out of a screenshot but the ones the row measured
 * off the real settings screen and kept. Opened from the launcher with TikTok
 * never having been on screen, it falls back to plain black or white.
 *
 * Every view is built here in code. Adding a layout or a style would mean
 * adding resources, and adding resources means rewriting a 25 MB resource
 * table -- the one thing this build refuses to do.
 */
public class SettingsActivity extends Activity {
    void accountAppearanceChanged(){skin=Skin.remembered(this);rebuild();}

    private Skin skin;
    private FrameLayout root;
    private final java.util.Map<String,View> settingTargets=new java.util.HashMap<>();
    private LinearLayout column;
    private View restartBar;
    private RestartSymbol restartSymbol;
    private TextView restartWhy, restartAction;
    private GradientDrawable restartBackground;
    private boolean rebuildQueued;
    private boolean badgePlacementOpen,gradientFineOpen;
    private String disclosureKey,renderDisclosureKey;
    private boolean collapsing;
    private final java.util.Map<String,java.util.List<DisclosureBody>> disclosureBodies=new java.util.HashMap<>();
    private final java.util.Map<String,Chevron> disclosureArrows=new java.util.HashMap<>();
    private boolean revealAnimationRequested;
    private boolean revealAnimationUsed;
    private Runnable scrollRestore;
    private android.view.ViewTreeObserver.OnPreDrawListener scrollRestoreListener;
    private int requestedScrollY;
    private final Runnable delayedRebuild = new Runnable() {
        @Override
        public void run() {
            rebuildQueued = false;
            rebuild();
        }
    };

    /**
     * Whether a setting was changed in this process.
     *
     * Static, because the bar is about the process and not about the screen:
     * leaving the settings and coming back does not un-change what was changed,
     * and only a restart -- which is a new process, where this is false again --
     * does.
     */
    private static boolean pending;

    /** Where the mod, its people and its money live. */
    private static final String CHANNEL = "https://t.me/margytiktok";
    private static final String FORUM = "https://t.me/margeletforum";
    private static final String OWNER_TELEGRAM = "https://t.me/narezany";
    private static final String OWNER_TIKTOK =
            "https://tiktok.com/@narezany?_r=1&_t=ZT-99hPDJ26hji_";
    /** Where a receipt goes: the channel's own messages, not somebody's inbox. */
    private static final String CHANNEL_WRITE = "https://t.me/margytiktok?direct";
    private static final String TTCUZ = "https://t.me/ttcuzmod";
    private static final String GIT = "https://git.narez.xyz/narezany/MargyT";
    private static final String DOCS_PLUGINS =
            "https://git.narez.xyz/narezany/MargyT/src/branch/main/docs/plugins.md";
    private static final String YOOMONEY = "https://yoomoney.ru/to/4100118196133693";
    private static final String BOOSTY = "https://boosty.to/ega_link";
    private static final String FUNPAY = "https://funpay.com/lots/offer?id=80127335";
    /** Stars go as gifts to this account; the link opens it in Telegram. */
    private static final String STARS = "https://t.me/narezany";

    private boolean countriesOpen;
    private boolean accentOpen;
    private Dot accentCustomSwatch;
    private boolean textOpen;
    private boolean backgroundOpen;
    private boolean themeColoursOpen;
    private boolean fontOpen;
    private ScrollView page;
    private boolean thanksOpen;
    private int settingsGroup;
    /** The page open inside a shelf, or null for the shelf's own list of pages. */
    private String settingsPage;

    /**
     * A page of a shelf: one subject, with a name and a line that says what is in it.
     * Its settings are the sections listed, in the order they are built.
     */
    private static final class Shelf {
        final int group; final String id, icon, title, note; final String[] sections;
        Shelf(int group, String id, String icon, String title, String note, String... sections) {
            this.group = group; this.id = id; this.icon = icon; this.title = title; this.note = note; this.sections = sections;
        }
    }

    private static Shelf[] shelves;

    private Shelf[] shelves() {
        if (shelves != null) return shelves;
        shelves = new Shelf[] {
            new Shelf(1, "style", "palette", Text.PAGE_STYLE, Text.PAGE_STYLE_NOTE, Text.APPEARANCE),
            new Shelf(1, "accent", "format_color_fill", Text.ACCENT, Text.PAGE_ACCENT_NOTE, Text.ACCENT),
            new Shelf(1, "md3", "view_compact", Text.MD3_TIKTOK, Text.PAGE_MD3_NOTE, Text.MD3_TIKTOK),
            new Shelf(1, "theme", "contrast", Text.THEME, Text.PAGE_THEME_NOTE, Text.THEME),
            new Shelf(1, "font", "text_fields", Text.FONT, Text.PAGE_FONT_NOTE, Text.FONT),
            new Shelf(2, "badges", "verified", Text.BADGES, Text.PAGE_BADGES_NOTE, Text.BADGES),
            new Shelf(2, "gradient", "palette", Text.GRADIENT, Text.PAGE_GRADIENT_NOTE, Text.GRADIENT),
            new Shelf(2, "look", "account_circle", Text.PROFILE_VIEW, Text.PAGE_LOOK_NOTE, Text.PROFILE_VIEW),
            new Shelf(2, "account", "fingerprint", Text.ACCOUNT, Text.PAGE_ACCOUNT_NOTE, Text.ACCOUNT),
            new Shelf(3, "feed", "view_compact", Text.FEED, Text.PAGE_FEED_NOTE, Text.FEED, Text.DATE_FILTER, Text.TAGS),
            new Shelf(3, "video", "play_circle", Text.VIDEO, Text.PAGE_VIDEO_NOTE, Text.RAIL, Text.VIDEO, Text.FPS),
            new Shelf(3, "comments", "chat", Text.COMMENTS, Text.PAGE_COMMENTS_NOTE, Text.COMMENTS),
            new Shelf(3, "burn", "eye_open", BURN, Text.PAGE_BURN_NOTE, BURN),
            new Shelf(3, "region", "public", Text.REGION, Text.PAGE_REGION_NOTE, Text.REGION, Text.COUNTRY),
            new Shelf(4, "downloads", "download", Text.DOWNLOADS, Text.PAGE_DOWNLOADS_NOTE, Text.DOWNLOADS),
            new Shelf(4, "backup", "restore", Text.BACKUP, Text.PAGE_BACKUP_NOTE, Text.BACKUP),
            new Shelf(4, "diagnostics", "bug_report", Text.DIAGNOSTICS, Text.PAGE_DIAGNOSTICS_NOTE, Text.DIAGNOSTICS),
            new Shelf(4, "diary", "article", Text.DIARY, Text.PAGE_DIARY_NOTE, Text.DIARY),
            new Shelf(5, "update", "update", Text.UPDATE, Text.PAGE_UPDATE_NOTE, Text.CHANNEL, Text.UPDATE),
            new Shelf(5, "links", "link", Text.LINKS, Text.PAGE_LINKS_NOTE, Text.LINKS),
            new Shelf(5, "thanks", "favorite_border", Text.THANKS, Text.PAGE_THANKS_NOTE, Text.THANKS),
            new Shelf(6, "plugins", "extension", Text.PLUGINS, "", Text.PLUGINS),
            new Shelf(7, "streaks", "schedule", Text.STREAKS, Text.PAGE_STREAKS_NOTE, Text.STREAKS),
            new Shelf(7, "notifications", "notifications", Text.NOTIFICATIONS, Text.PAGE_NOTIFICATIONS_NOTE, Text.NOTIFICATIONS),
            new Shelf(8, "dep", "flare", Text.FUN_DEP, Text.PAGE_DEP_NOTE, Text.FUN_DEP),
            new Shelf(8, "rubble", "open_with", Text.FUN_RUBBLE, Text.PAGE_RUBBLE_NOTE, Text.FUN_RUBBLE),
            new Shelf(8, "screamers", "animation", Text.FUN_SCREAMERS, Text.PAGE_SCREAMERS_NOTE, Text.FUN_SCREAMERS),
            new Shelf(8, "avariyka", "notifications", Text.FUN_AVARIYKA, Text.PAGE_AVARIYKA_NOTE, Text.FUN_AVARIYKA),
            new Shelf(8, "cat", "image", Text.FUN_CAT, Text.PAGE_CAT_NOTE, Text.FUN_CAT),
        };
        return shelves;
    }

    private java.util.List<Shelf> shelvesOf(int group) {
        java.util.List<Shelf> out = new java.util.ArrayList<>();
        for (Shelf one : shelves()) {
            if (one.group != group) continue;
            if (Version.LITE) { boolean any = false; for (String section : one.sections) any |= inLite(section); if (!any) continue; }
            out.add(one);
        }
        return out;
    }

    private Shelf shelf(String id) {
        if (id == null) return null;
        for (Shelf one : shelves()) if (one.id.equals(id)) return one;
        return null;
    }

    private Shelf shelfOfSection(String section) {
        for (Shelf one : shelves()) for (String name : one.sections) if (name.equals(section)) return one;
        return null;
    }
    private boolean mineOpen;
    private java.util.List<Mine.Held> ordering;
    private boolean streakOpen;
    private boolean diaryOpen;
    /** Set once the badges have been rearranged by hand and not yet saved. */
    private boolean rearranged;
    /** The card the donation button brings the screen down to. */
    private View donateAnchor;
    private final Runnable badgeUpdates = () -> {
        if (!isFinishing() && !isDestroyed()) rebuild();
    };
    private String searchQuery = "";
    private final java.util.Map<String, View> sectionTargets =
            new java.util.HashMap<String, View>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Margy.attach(this);
        // drawn in what TikTok's own settings were last seen wearing, which is
        // the theme when one is on: this screen is opened from that one
        skin = Skin.remembered(this);
        Unsaved.look(this);
        dressTheWindow();
        // the list may still be the one cached before this screen existed, so
        // redraw when the server answers rather than waiting for a tap
        Badges.tell(badgeUpdates);
        // a test build is only for the people who paid for it, and whether
        // this account is one of them is a thing the server knows -- so the
        // refusal waits for its answer rather than firing on a cold start,
        // when the list of badges is simply not here yet
        Mine.ask(() -> {
            if (isFinishing() || isDestroyed()) return;
            rebuild();
            if (Tester.on() && !Tester.allowed()) Tester.refuse(this);
        });
        if (Tester.on() && Tester.asked() && !Tester.allowed()) Tester.refuse(this);

        ScrollView scroll = page = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root=new FrameLayout(this);
        root.setBackgroundColor(skin.page);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        Design.page(this, root, skin.page);
        rebuild();
        maybeAskForSupport();
        if(getIntent().getBooleanExtra("margyt_tutorial",false))root.postDelayed(()->Tutorial.show(this),350L);
        openPluginFile(getIntent());
    }

    /** Set by PluginOpenActivity: a .mtp opened from a file manager, to review as if it were picked here. */
    static final String OPEN_PLUGIN = "margyt_open_plugin";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPluginFile(intent);
    }

    private void openPluginFile(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(OPEN_PLUGIN, false) || intent.getData() == null) return;
        final Uri file = intent.getData();
        intent.removeExtra(OPEN_PLUGIN);
        settingsGroup = 6;
        settingsPage = "plugins";
        rebuild();
        root.post(() -> reviewPlugin(file));
    }

    void tutorialStep(int step) {
        settingsGroup=step==1 ? 1 : step==2 ? 3 : step==3 ? 2 : 0;
        if(step==1)accentOpen=true;
        searchQuery="";rebuild();
        String section=step==1 ? Text.ACCENT : step==2 ? BURN : step==3 ? Text.GRADIENT : null;
        View target=section==null ? null : sectionTargets.get(section);
        if(target!=null)page.post(()->page.smoothScrollTo(0,Math.max(0,target.getTop()-dp(20))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Streaks.isEnabled()){StreakAlarm.schedule(this,false);Streaks.round(this);}
        if(root!=null && column!=null)rebuild();
    }

    @Override protected void onPause(){
        navigating=false;
        if(column!=null){column.animate().withEndAction(null).cancel();column.setAlpha(1f);column.setTranslationX(0f);}
        super.onPause();
    }

    private void dressTheWindow() {
        try {
            getWindow().setStatusBarColor(skin.page);
            getWindow().setNavigationBarColor(skin.page);
            if (!skin.dark()) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------- the screen

    private void rebuild() {
        navigating=false;
        collapsing=false;
        column.animate().withEndAction(null).cancel(); column.setAlpha(1f); column.setTranslationX(0f);
        revealAnimationUsed = false;
        boolean animateReveal = revealAnimationRequested;
        revealAnimationRequested = false;
        revealAnimationUsed = !animateReveal;
        final int previousScroll = page == null ? 0 : page.getScrollY();
        sectionTargets.clear();settingTargets.clear();
        disclosureBodies.clear();disclosureArrows.clear();renderDisclosureKey=null;
        column.removeAllViews();
        column.addView(backArrow());
        if (settingsGroup == DONATE_PAGE) {
            column.addView(title(Text.DONATE_BANNER));
            donatePage();
            showRestartBar();
            restoreScroll(0);
            return;
        }
        if (settingsGroup == FLAGS_PAGE) {
            column.addView(title(Text.DEV_FLAGS));
            flagsPage();
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }
        // a shelf with one page opens on it straight away
        if (settingsGroup != 0 && settingsPage == null && searchQuery.length() == 0) {
            java.util.List<Shelf> pages = shelvesOf(settingsGroup);
            if (pages.size() == 1) settingsPage = pages.get(0).id;
        }
        Shelf open = shelf(settingsPage);
        if (open != null && open.group != settingsGroup) { settingsPage = null; open = null; }
        column.addView(title(settingsGroup == 0 ? (Version.LITE ? "MargyT Lite" : "MargyT") : open != null ? open.title : groupName(settingsGroup)));
        if(settingsGroup==0 || searchQuery.length()>0)column.addView(searchBox());
        if (settingsGroup != 0 && open == null && searchQuery.length() == 0) {
            column.addView(shelfList(settingsGroup));
            column.addView(wrap(actionRow("info",Local.of("Подсказки"),null,this::showSectionHelp)));
            showRestartBar();
            restoreScroll(0);
            return;
        }
        if (open != null && open.note.length() > 0) { View lead = lead(open.note); lead.setTag(LEAD, Boolean.TRUE); column.addView(lead); }
        if (searchQuery.length() > 0) {
            searchResults();
            restoreScroll(previousScroll);
            return;
        }

        if (settingsGroup == 0) {
            if (Badges.freeStillOpen() && !Mine.hasFree()) column.addView(freeBanner());
            if (!donateClosed && (!supporter() || bannerAlways())) column.addView(donateBanner());
            column.addView(groupNavigation());
            column.addView(wrap(actionRow("info",Local.of("Обучение"),null,() -> Tutorial.show(this))));
            column.addView(versions());
            showRestartBar();
            restoreScroll(0);
            return;
        }

        column.addView(section(Text.APPEARANCE));
        column.addView(wrap(designPicker()));
        LinearLayout icons = card();
        icons.addView(iconCarousel());
        icons.addView(line());
        icons.addView(actionRow("wallpaper", Text.CUSTOM_ICON, null, () -> CustomIcon.pick(this)));
        column.addView(wrap(icons));

        column.addView(section(Text.ACCENT));
        LinearLayout accent = card();
        accent.addView(phone("accent").liked(true));
        accent.addView(accentHead());
        if (accentOpen) {
            if (Accent.fromWallpaper() != 0) {
                accent.addView(line());
                accent.addView(appear(wallpaperRow()));
            }
            accent.addView(line());
            accent.addView(appear(palette()));
        }
        column.addView(wrap(accent));

        column.addView(section(Text.MD3_TIKTOK));
        LinearLayout loading = card();
        final SettingsArt.Loader loader = new SettingsArt.Loader(this, skin, Loading.isEnabled());
        loading.addView(loader);
        loading.addView(liveToggleRow("palette", Design.frosted()?Text.LOADING_INDICATOR:Text.LOADING_SHAPES, Loading.isEnabled(),
                on -> { Loading.setEnabled(on); loader.set(on); }));
        loading.addView(quiet(Design.frosted()?Text.LOADING_STYLE_NOTE:Text.LOADING_NOTE));
        column.addView(wrap(loading));

        LinearLayout cards = card();
        final SettingsPictures.Grid grid = new SettingsPictures.Grid(this, skin, Corners.isEnabled());
        cards.addView(grid);
        cards.addView(toggleRow("image", Text.ROUND_CARDS, Corners.isEnabled(), on -> {
            Corners.setEnabled(on);
            grid.set(on);
        }));
        column.addView(wrap(cards));

        LinearLayout shapes = card();
        shapes.addView(avatarShapes());
        column.addView(wrap(shapes));

        loading = card();
        final SettingsArt.Phone md3Phone = phone("md3");
        loading.addView(md3Phone);
        loading.addView(toggleRow("tune", Text.NAV_CAPSULE, NavBar.isEnabled(), on -> {
            NavBar.setEnabled(on);
            md3Phone.capsule(on).settle();
        }));
        loading.addView(line());
        loading.addView(toggleRow("tune", Design.caption(Text.MD3_FEED), Md3.isOn(Md3.KEY_FEED), on -> Md3.set(Md3.KEY_FEED, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Design.caption(Text.MD3_PROFILE), Md3.isOn(Md3.KEY_PROFILE), on -> Md3.set(Md3.KEY_PROFILE, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Design.caption(Text.MD3_INBOX), Md3.isOn(Md3.KEY_INBOX), on -> Md3.set(Md3.KEY_INBOX, on)));
        loading.addView(line());
        loading.addView(toggleRow("chat_bubble", Design.caption(Text.DESIGN_COMMENTS), CommentStyle.enabled(), CommentStyle::set));
        column.addView(wrap(loading));

        column.addView(section(Text.THEME));
        LinearLayout theme = card();
        final SettingsPictures.Black black = new SettingsPictures.Black(this, skin, Themes.amoledChosen() && Themes.isDark());
        theme.addView(black);
        theme.addView(liveToggleRow("contrast", Text.AMOLED, Themes.amoledChosen() && Themes.isDark(), on -> {
            black.set(on && Themes.isDark());
            if (on && !Themes.isDark()) {
                Panel.with(this, skin, Text.AMOLED).text(Text.AMOLED_LIGHT).primary(Local.of("Понятно"), null).show();
                rebuildAfterSwitchAnimation();
                return;
            }
            Themes.setAmoled(on);
            markChanged();
            rebuildAfterSwitchAnimation();
        }));
        theme.addView(quiet(Themes.isDark() ? Text.AMOLED_NOTE : Text.AMOLED_LIGHT));
        theme.addView(line());
        if (Themes.available()) {
            // on, and wearing the phone's own colours: one switch for both,
            // because the other way to be on is to pick a preset below
            theme.addView(toggleRow("wallpaper", Text.THEME_MATERIAL,
                    Themes.isEnabled() && Themes.isMaterial(), on -> {
                        Themes.setMaterial(on);
                        Themes.setEnabled(on);
                        markChanged();
                        rebuildAfterSwitchAnimation();
                    }));
            theme.addView(quiet(Text.THEME_NOTE));
        } else {
            theme.addView(quiet(Text.THEME_OLD));
        }
        theme.addView(line());
        theme.addView(actionRow("palette", Text.THEME_PRESETS, Text.THEME_PRESETS_NOTE,
                this::chooseThemePreset));
        theme.addView(line());
        theme.addView(themeColoursHead());
        if (themeColoursOpen) {
            theme.addView(line());
            for (String role : AppearanceColors.ROLES) {
                theme.addView(appearanceColourRow(role));
                theme.addView(line());
            }
        }
        column.addView(wrap(theme));

        column.addView(section(Text.FONT));
        LinearLayout fonts = card();
        fonts.addView(fontCarousels());
        column.addView(wrap(fonts));

        column.addView(section(Text.GRADIENT));
        column.addView(wrap(gradientCard()));

        column.addView(section(Text.BADGES));
        LinearLayout badges = card();
        final SettingsPictures.Badged badged = new SettingsPictures.Badged(this, skin, Badges.isEnabled());
        badges.addView(badged);
        badges.addView(toggleRow("favorite_border", Text.BADGES_ON, Badges.isEnabled(), on -> {
            Badges.setEnabled(on);
            badged.set(on);
        }));
        badges.addView(line());
        badges.addView(mineHead());
        if (mineOpen) badges.addView(appear(mineRows()));
        if (AdminPanel.owner()) {
            // the owner's alone: which phones may use the admin inside TikTok
            badges.addView(line());
            badges.addView(actionRow("fingerprint", Text.ADMIN_DEVICES, null, () -> AdminPanel.devices(this)));
        }
        column.addView(wrap(badges));

        column.addView(section(Text.ACCOUNT));
        LinearLayout account = card();
        account.addView(idRow(Text.ACCOUNT_ID, Account.id()));
        account.addView(line());
        account.addView(idRow(Text.ACCOUNT_SEC_ID, Account.secId()));
        column.addView(wrap(account));

        column.addView(section(Text.PROFILE_VIEW));
        LinearLayout look = card();
        final SettingsPictures.Layout layout = new SettingsPictures.Layout(this, skin, Flags.isOn(Flags.KEY_NEW_PROFILE));
        look.addView(layout);
        look.addView(toggleRow("view_compact", Text.NEW_PROFILE, Flags.isOn(Flags.KEY_NEW_PROFILE), on -> {
            Flags.set(Flags.KEY_NEW_PROFILE, on);
            layout.set(on);
        }));
        look.addView(quiet(Text.NEW_PROFILE_NOTE));
        column.addView(wrap(look));

        column.addView(section(Text.REGION));
        LinearLayout head = card();
        head.addView(switchRow());
        column.addView(wrap(head));

        column.addView(section(Text.COUNTRY));
        LinearLayout countries = card();
        countries.addView(countryHead());
        if (countriesOpen) {
            countries.addView(line());
            for (String[] country : Margy.COUNTRIES) {
                countries.addView(appear(countryRow(country)));
            }
        }
        column.addView(wrap(countries));

        column.addView(section(Text.FEED));
        LinearLayout feed = card();
        // the feed as a strip of cards: what is hidden folds out of it
        final SettingsPictures.Feed strip = new SettingsPictures.Feed(this, skin)
                .hide(SettingsPictures.Feed.AD, Feed.isEnabled())
                .hide(SettingsPictures.Feed.LIVE, Feed.hides(Feed.KEY_LIVE))
                .hide(SettingsPictures.Feed.PHOTO, Feed.hides(Feed.KEY_PHOTOS))
                .hide(SettingsPictures.Feed.STORY, Feed.hides(Feed.KEY_STORIES))
                .hide(SettingsPictures.Feed.SUGGEST, Feed.hides(Feed.KEY_SUGGESTIONS));
        feed.addView(strip);
        feed.addView(toggleRow("block", Text.HIDE_ADS, Feed.isEnabled(), on -> {
            Feed.setEnabled(on);
            strip.hide(SettingsPictures.Feed.AD, on).settle();
        }));
        feed.addView(line());
        feed.addView(toggleRow("visibility_off", Text.HIDE_LIVE, Feed.hides(Feed.KEY_LIVE), on -> {
            Feed.setHides(Feed.KEY_LIVE, on);
            strip.hide(SettingsPictures.Feed.LIVE, on).settle();
        }));
        feed.addView(line());
        feed.addView(toggleRow("image", Text.HIDE_PHOTOS, Feed.hides(Feed.KEY_PHOTOS), on -> {
            Feed.setHides(Feed.KEY_PHOTOS, on);
            strip.hide(SettingsPictures.Feed.PHOTO, on).settle();
        }));
        feed.addView(line());
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES),on->{
            Feed.setHides(Feed.KEY_STORIES,on);
            strip.hide(SettingsPictures.Feed.STORY, on).settle();
        }));
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS),on->{
            Feed.setHides(Feed.KEY_SUGGESTIONS,on);
            strip.hide(SettingsPictures.Feed.SUGGEST, on).settle();
        }));
        column.addView(wrap(feed));

        column.addView(section(Text.SIMPLE_FEED));
        LinearLayout simpler = card();
        final SettingsArt.Phone simpleArt = phone("simple").simple(SimpleFeed.isOn());
        simpler.addView(simpleArt);
        simpler.addView(liveToggleRow("view_compact", Text.SIMPLE_FEED, SimpleFeed.isOn(), on -> {
            SimpleFeed.set(this, on);
            simpleArt.simple(on).settle();
        }));
        simpler.addView(quiet(Text.SIMPLE_FEED_NOTE));
        column.addView(wrap(simpler));

        column.addView(section(Text.DATE_FILTER));
        LinearLayout dates = card();
        final SettingsPictures.Ages ages = new SettingsPictures.Ages(this, skin, FeedDates.hours());
        dates.addView(ages);
        LinearLayout datesHead = row();
        datesHead.addView(icon("schedule"));
        datesHead.addView(label(Text.DATE_FILTER), grow());
        final TextView datesNow = detail(FeedDates.describe(FeedDates.hours()));
        datesNow.setTextColor(skin.text);
        datesHead.addView(datesNow);
        dates.addView(sized(datesHead, 52));
        String[] limits = new String[FeedDates.HOURS.length];
        int limitAt = 0;
        for (int i = 0; i < limits.length; i++) {
            limits[i] = FeedDates.describe(FeedDates.HOURS[i]);
            if (FeedDates.HOURS[i] == FeedDates.hours()) limitAt = i;
        }
        dates.addView(chips(limits, limitAt, index -> {
            FeedDates.setHours(FeedDates.HOURS[index]);
            datesNow.setText(FeedDates.describe(FeedDates.HOURS[index]));
            ages.set(FeedDates.HOURS[index]);
        }));
        dates.addView(caption(Text.DATE_FILTER_NOTE));
        column.addView(wrap(dates));

        column.addView(section(Text.TAGS));
        LinearLayout tags = card();
        tags.addView(betaRow("tag", Text.TAGS, Tags.isEnabled(), Tags::setEnabled));
        java.util.List<String> blocked = Tags.all();
        if (blocked.isEmpty()) {
            tags.addView(caption(Text.TAGS_NONE));
        } else {
            for (final String tag : blocked) {
                tags.addView(line());
                tags.addView(actionRow("block", "#" + tag, null, () -> {
                    Tags.remove(tag);
                    rebuild();
                }));
            }
        }
        tags.addView(line());
        tags.addView(actionRow("tag", Text.TAGS_ADD, null, () -> Popup.write(
                this, Text.TAGS_ADD, "", Text.TAGS_ADD, said -> {
                    if (!Tags.add(said)) {
                        Toast.makeText(this, Text.TAGS_FULL, Toast.LENGTH_SHORT).show();
                    }
                    rebuild();
                })));
        tags.addView(caption(Text.TAGS_NOTE));
        column.addView(wrap(tags));

        column.addView(section(Text.RAIL));
        LinearLayout rail=card();
        // the buttons can be tapped on the picture itself, and the switches follow
        final SettingsArt.Phone railArt = phone("rail");
        rail.addView(railArt);
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        String[] railIcons={"favorite_border","chat","star","share"};
        final View[] railRows = new View[railNames.length];
        for(int i=0;i<railNames.length;i++){
            final int kind=i;
            railRows[i] = liveToggleRow(railIcons[i],railNames[i],FeedRail.shown(i),on->{
                FeedRail.setShown(kind,on);
                railArt.rail(kind,on).settle();
            });
            rail.addView(railRows[i]);
        }
        railArt.onRail(kind -> {
            boolean now = !FeedRail.shown(kind);
            FeedRail.setShown(kind, now);
            railArt.rail(kind, now).settle();
            M3Switch moved = switchIn(railRows[kind]);
            if (moved != null) moved.setChecked(now, true);
        });
        rail.addView(quiet(Text.RAIL_TAP));
        column.addView(wrap(rail));

        column.addView(section(Text.VIDEO));
        LinearLayout video = card();
        // a finger on the edge of a video: the speed while held, and swiped down to keep it
        final SettingsPictures.Speed speedArt = new SettingsPictures.Speed(this, skin, PlayerTools.speed(),
                Flags.isOn(Flags.KEY_SPEED_LOCK));
        video.addView(speedArt);
        LinearLayout speedHead = row();
        speedHead.addView(icon("speed"));
        speedHead.addView(label(Text.PLAYER_SPEED), grow());
        final TextView speedNow = detail(PlayerTools.speed() + "\u00D7");
        speedNow.setTextColor(skin.text);
        speedHead.addView(speedNow);
        video.addView(sized(speedHead, 52));
        final float[] speeds = {1f, 1.5f, 2f, 3f};
        int speedAt = 4;
        for (int i = 0; i < speeds.length; i++) if (Math.abs(PlayerTools.speed() - speeds[i]) < 0.01f) speedAt = i;
        video.addView(segmented(new String[] {"1\u00D7", "1.5\u00D7", "2\u00D7", "3\u00D7", "\u2026"}, speedAt, index -> {
            if (index == 4) { PlayerTools.choose(this, this::rebuild); return; }
            PlayerTools.setSpeed(speeds[index]);
            speedNow.setText(speeds[index] + "\u00D7");
            speedArt.set(speeds[index], Flags.isOn(Flags.KEY_SPEED_LOCK));
        }));
        video.addView(toggleRow("lock", Text.SPEED_LOCK, Flags.isOn(Flags.KEY_SPEED_LOCK), on -> {
            Flags.set(Flags.KEY_SPEED_LOCK, on);
            speedArt.set(PlayerTools.speed(), on);
        }));

        video.addView(liveToggleRow("timeline", Text.SEEKBAR, Seekbar.isEnabled(), on -> {
            Seekbar.setEnabled(on);
            railArt.seekbar(on).settle();
        }));
        video.addView(line());
        video.addView(liveToggleRow("info", Text.ALWAYS_DATE, Dates.isEnabled(), on -> {
            Dates.setEnabled(on);
            railArt.date(on).settle();
        }));
        video.addView(line());
        column.addView(wrap(video));

        LinearLayout sound = card();
        final SettingsPictures.Sound soundArt = new SettingsPictures.Sound(this, skin, Sound.isEnabled());
        sound.addView(soundArt);
        sound.addView(toggleRow("volume_up", Text.SOUND, Sound.isEnabled(), on -> {
            Sound.setEnabled(on);
            soundArt.set(on);
        }));
        column.addView(wrap(sound));

        LinearLayout bright = card();
        final SettingsPictures.Bright brightArt = new SettingsPictures.Bright(this, skin, Brightness.isEnabled());
        bright.addView(brightArt);
        bright.addView(toggleRow("hdr_off", Text.OWN_BRIGHTNESS, Brightness.isEnabled(), on -> {
            Brightness.setEnabled(on);
            brightArt.set(on);
            markChanged();
        }));
        bright.addView(caption(Text.OWN_BRIGHTNESS_NOTE));
        column.addView(wrap(bright));

        column.addView(section(Text.FPS));
        LinearLayout fps = card();
        // a ball drawn as often as the chosen rate, beside TikTok's sixty, and the rates in one row
        final SettingsPictures.Fps fpsArt = new SettingsPictures.Fps(this, skin, Rate.name());
        fps.addView(fpsArt);
        String rate = Rate.name();
        String[] rates = new String[Rate.CHOICES.length];
        int rateAt = 0;
        for (int i = 0; i < Rate.CHOICES.length; i++) {
            rates[i] = Rate.CHOICES[i].length() == 0 ? Text.FPS_AUTO : Rate.CHOICES[i];
            if (Rate.CHOICES[i].equals(rate)) rateAt = i;
        }
        fps.addView(segmented(rates, rateAt, index -> {
            Rate.choose(Rate.CHOICES[index]);
            fpsArt.set(Rate.CHOICES[index]);
            markChanged();
        }));
        fps.addView(quiet(Text.FPS_ABOUT));
        if (!Rate.reachable(this)) fps.addView(quiet(Text.FPS_UNSUPPORTED));
        column.addView(wrap(fps));

        column.addView(section(Text.COMMENTS));
        LinearLayout comments = card();
        final SettingsPictures.Copying copying = new SettingsPictures.Copying(this, skin, Comments.isCopyEnabled());
        comments.addView(copying);
        comments.addView(liveToggleRow("article", Text.COMMENT_COPY_ON, Comments.isCopyEnabled(), on -> {
            Comments.setCopyEnabled(on);
            copying.set(on);
        }));
        comments.addView(quiet(Text.COMMENT_COPY_NOTE));
        column.addView(wrap(comments));

        column.addView(section(BURN));
        LinearLayout burn = card();
        // the phone shows what the switch and the slider do, as they are moved
        final SettingsArt.Phone burnArt = phone("burn").burn(Dim.isConfigured(), Dim.strength(), NavBar.isKept());
        burn.addView(burnArt);
        burn.addView(liveToggleRow("eye_open", Text.DIM, Dim.isConfigured(), on -> {
            Dim.setEnabled(on);
            burnArt.burn(on, Dim.strength(), NavBar.isKept()).settle();
            rebuildAfterSwitchAnimation();
        }));
        if (Dim.isConfigured()) {
            burn.addView(line());
            burn.addView(slider("contrast", Text.DIM_HOW, Dim.strength(), 100, 0, "%", value -> Dim.setStrength(value), false,
                    value -> burnArt.burn(true, value, NavBar.isKept()).settle()));
            burn.addView(line());
            burn.addView(toggleRow("visibility_off", Text.KEEP_BAR, NavBar.isKept(), on -> {
                NavBar.setKept(on);
                burnArt.burn(true, Dim.strength(), on).settle();
            }));
        }
        burn.addView(quiet(Local.of("Глаз в ленте включает и выключает приглушение на лету.")));
        column.addView(wrap(burn));

        column.addView(section(Text.DOWNLOADS));
        LinearLayout downloads = card();
        downloads.addView(liveToggleRow("download", Text.DOWNLOAD_WINDOW, VideoActions.windowOn(), VideoActions::setWindow));
        downloads.addView(quiet(Text.DOWNLOAD_WINDOW_NOTE));
        downloads.addView(line());
        LinearLayout qualityHead = row();
        qualityHead.addView(icon("image"));
        qualityHead.addView(label(Text.FRAME_QUALITY), grow());
        downloads.addView(sized(qualityHead, 52));
        final int[] qualities = {480, 720, 1080};
        int qualityAt = FrameCapture.quality() == 480 ? 0 : FrameCapture.quality() == 1080 ? 2 : 1;
        downloads.addView(segmented(new String[] {"480p", "720p", "1080p"}, qualityAt, index ->
                AccountAppearance.prefs(this).edit().putInt(FrameCapture.KEY, qualities[index]).apply()));
        final SettingsPictures.Watermark mark = new SettingsPictures.Watermark(this, skin, Download.isEnabled());
        downloads.addView(mark, 0);
        downloads.addView(toggleRow("image", Text.NO_WATERMARK, Download.isEnabled(), on -> {
            Download.setEnabled(on);
            mark.set(on);
        }));
        downloads.addView(line());
        downloads.addView(toggleRow("download", Text.DOWNLOAD_ALWAYS, Download.isAlways(),
                Download::setAlways));
        downloads.addView(line());
        downloads.addView(toggleRow("place", Text.SAVE_AVATARS_ON, Avatars.isEnabled(),
                Avatars::setEnabled));
        downloads.addView(line());
        downloads.addView(toggleRow("star", Text.SAVE_STICKERS_ON, Stickers.isEnabled(),
                Stickers::setEnabled));
        column.addView(wrap(downloads));

        column.addView(section(Text.BACKUP));
        LinearLayout backup = card();
        backup.addView(actionRow("upload", Text.BACKUP_EXPORT, Text.BACKUP_EXPORT_NOTE,
                this::createBackup));
        backup.addView(line());
        backup.addView(actionRow("download", Text.BACKUP_IMPORT, Text.BACKUP_IMPORT_NOTE,
                this::pickBackup));
        backup.addView(line());
        backup.addView(actionRow("restore", Text.BACKUP_RESET, Text.BACKUP_RESET_NOTE,
                this::pickResetGroup));
        column.addView(wrap(backup));

        column.addView(section(Text.PLUGINS));
        LinearLayout plugins = card();
        plugins.addView(actionRow("add", Local.of("Добавить плагин"), null, this::showPluginAdd));
        List<Plugins.Info> installed = Plugins.list();
        List<ScriptPlugins.Program> commands=ScriptPlugins.list(this);
        if(installed.isEmpty()&&commands.isEmpty())plugins.addView(quiet(Local.of("Пока нет установленных плагинов")));
        for(ScriptPlugins.Program program:commands){
            boolean enabled=getSharedPreferences(ScriptPlugins.STORE,0).getBoolean("on:"+program.id,false);
            plugins.addView(actionRow("extension",program.name,enabled?Local.of("Включён"):Local.of("Выключен"),()->ScriptPlugins.details(this,program,this::rebuild)));
        }
        for(Plugins.Info info:installed)plugins.addView(pluginRow(info));
        column.addView(wrap(plugins));

        column.addView(section(Text.STREAKS));
        LinearLayout streaks = card();
        final SettingsArt.Chat chat = new SettingsArt.Chat(this, skin);
        final String sent = Streaks.chosen();
        chat.state(Streaks.isEnabled(), sent.length() == 0 ? null : Streaks.thumbnail(this, sent, () -> {
            if (!chat.isAttachedToWindow()) return;
            chat.state(Streaks.isEnabled(), Streaks.thumbnail(this, sent));
        }));
        streaks.addView(chat);
        streaks.addView(betaRow("repeat", Text.STREAK_AUTO, Streaks.isEnabled(), on -> {
            Streaks.setEnabled(on);
            chat.state(on, sent.length() == 0 ? null : Streaks.thumbnail(this, sent)).settle();
        }));
        streaks.addView(line());
        // What the schedule is standing on, said plainly: an inexact alarm, a
        // battery saver or a muted notification channel each stop the send, and
        // none of them is visible anywhere else in the app.
        streaks.addView(actionRow("bug_report", Text.STREAK_HEALTH, StreakAlarm.status(this), () -> {
            StreakAlarm.permissions(this);
            Toast.makeText(this, StreakAlarm.status(this), Toast.LENGTH_LONG).show();
        }));
        streaks.addView(line());
        streaks.addView(actionRow("schedule", Local.of("Время отправки"),StreakSchedule.label(),()->StreakSchedule.choose(this,this::rebuild)));
        streaks.addView(stickerHead());
        if (streakOpen) {
            streaks.addView(line());
            streaks.addView(appear(stickerChoices()));
        }
        streaks.addView(line());
        streaks.addView(actionRow("play_circle", Text.STREAK_ALL, Text.STREAK_ALL_NOTE,
                () -> Popup.ask(this, Text.STREAK_ALL, Text.STREAK_ALL_ASK,
                        Text.STREAK_ALL_SEND, () -> Streaks.sendAll(this),
                        Text.CANCEL, null, null, null)));
        column.addView(wrap(streaks));

        column.addView(section(Text.NOTIFICATIONS));
        LinearLayout notifications=card();
        // three notifications as the phone shows them: the kinds turned off are struck out
        final SettingsPictures.Notices notices = new SettingsPictures.Notices(this, skin)
                .set(0, ModNotifications.option(0)).set(1, ModNotifications.option(1)).set(2, ModNotifications.option(2));
        notifications.addView(notices);
        notifications.addView(liveToggleRow("chat",Text.NOTIFY_MESSAGES,ModNotifications.option(0),on->{ModNotifications.set(ModNotifications.MESSAGES,on);notices.set(0,on).settle();if(!on)rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_PROMOS,ModNotifications.option(1),on->{ModNotifications.set(ModNotifications.PROMOS,on);notices.set(1,on).settle();if(!on)rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_OTHER,ModNotifications.option(2),on->{ModNotifications.set(ModNotifications.OTHER,on);notices.set(2,on).settle();if(!on)rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications_off",Text.NOTIFY_NO_PROMPTS,ModNotifications.noPrompts(),on->{AccountAppearance.prefs(this).edit().putBoolean(ModNotifications.NO_PROMPTS,on).apply();ModNotifications.reload();}));
        notifications.addView(actionRow("settings",Text.NOTIFY_SETTINGS,null,()->ModNotifications.settings(this)));
        column.addView(wrap(notifications));

        column.addView(section(Text.CHANNEL));
        column.addView(channelBanner());

        column.addView(section(Text.UPDATE));
        LinearLayout updates = card();
        updates.addView(actionRow("download", Text.UPDATE_CHECK,
                Updater.newer() ? Text.UPDATE_THERE_IS + " " + Updater.latest() : null,
                () -> Updater.check(this, true)));
        updates.addView(line());
        if (Updater.storeInstalled(this)) {
            updates.addView(actionRow("extension", Text.UPDATE_OPEN_STORE, Text.UPDATE_IN_STORE, () -> Updater.openStore(this)));
            updates.addView(line());
        }
        updates.addView(actionRow("download", Text.UPDATE_DOWNLOAD_STORE,
                Updater.storeInstalled(this) ? null : Text.UPDATE_IN_STORE, () -> Updater.downloadStore(this)));
        updates.addView(line());
        updates.addView(toggleRow("info", Text.UPDATE_REMIND, Updater.remind(this),
                on -> Updater.setRemind(this, on)));
        if (Updater.waiting(this)) {
            updates.addView(line());
            updates.addView(actionRow("extension", Text.UPDATE_INSTALL, null,
                    () -> Updater.install(this)));
        }
        column.addView(wrap(updates));

        column.addView(section(Text.LINKS));
        LinearLayout links = card();
        links.addView(linkRow("group", Text.FORUM, "@margeletforum", FORUM));
        links.addView(line());
        links.addView(linkRow("extension", Text.SOURCE, "narezany/MargyT", GIT));
        column.addView(wrap(links));

        column.addView(section(Text.DIAGNOSTICS));
        LinearLayout diagnostics = card();
        diagnostics.addView(liveToggleRow("bug_report", Local.of("Отчёты о сбоях"),CrashReports.enabled(this),on->CrashReports.set(this,on)));
        diagnostics.addView(quiet(Local.of("Технический стек сбоя, без сообщений и аккаунта.")));
        diagnostics.addView(quiet(Diagnostics.report()));
        diagnostics.addView(line());
        diagnostics.addView(actionRow("article", Text.COPY_DIAGNOSTICS, null,
                () -> copy("MargyT diagnostics", DevMode.isOn()
                        ? Diagnostics.fullReport() : Diagnostics.report())));
        column.addView(wrap(diagnostics));

        column.addView(section(Text.DIARY));
        LinearLayout diary = card();
        diary.addView(diaryHead());
        if (diaryOpen) {
            diary.addView(line());
            diary.addView(appear(diaryLines()));
        }
        if (DevMode.isOn()) addDeveloperRows(diary);
        column.addView(wrap(diary));

        column.addView(section(Text.THANKS));
        LinearLayout support = card();
        support.addView(thanksHead());
        if (thanksOpen) {
            support.addView(line());
            View details = thanks();
            support.addView(appear(details));
        }
        donateAnchor = wrap(support);
        column.addView(donateAnchor);
        LinearLayout offer = card();
        offer.addView(liveToggleRow("favorite_border", Text.BANNER_ALWAYS, bannerAlways(),
                on -> AccountAppearance.prefs(this).edit().putBoolean(BANNER_ALWAYS, on).apply()));
        offer.addView(quiet(Text.BANNER_ALWAYS_NOTE));
        column.addView(wrap(offer));

        column.addView(section(Text.FUN_DEP));
        LinearLayout dep = card();
        dep.addView(chipRow("flare", Text.FUN_DEP, new String[] {Text.CHIP_TEMPORARY, Text.CHIP_COLLAB},
                this::openDep));
        dep.addView(quiet(Text.DEP_NOTE));
        dep.addView(line());
        final SettingsPictures.DepWindow depArt = new SettingsPictures.DepWindow(this, skin, DepFloat.isOn(this));
        dep.addView(depArt);
        dep.addView(liveToggleRow("open_with", Text.DEP_FLOAT, DepFloat.isOn(this), on -> {
            DepFloat.set(this, on);
            depArt.set(on);
        }));
        column.addView(wrap(dep));

        column.addView(section(Text.FUN_SCREAMERS));
        LinearLayout screamers = card();
        screamers.addView(new SettingsPictures.Scream(this, skin));
        screamers.addView(liveToggleRow("animation", Text.FUN_SCREAMERS, Screamer.isOn(this),
                on -> Screamer.set(this, on)));
        screamers.addView(quiet(Text.SCREAMERS_NOTE));
        screamers.addView(line());
        screamers.addView(liveToggleRow("volume_up", Text.SCREAMER_SOUND, Screamer.loud(this),
                on -> Screamer.setLoud(this, on)));
        screamers.addView(line());
        screamers.addView(actionRow("play_arrow", Text.SCREAMER_NOW, null, () -> Screamer.now(this)));
        column.addView(wrap(screamers));

        column.addView(section(Text.FUN_RUBBLE));
        LinearLayout rubble = card();
        rubble.addView(new SettingsPictures.Fall(this, skin));
        rubble.addView(actionRow("open_with", Text.FUN_RUBBLE, null, () -> Rubble.arm(this)));
        rubble.addView(quiet(Text.RUBBLE_NOTE));
        column.addView(wrap(rubble));

        column.addView(section(Text.FUN_AVARIYKA));
        LinearLayout avariyka = card();
        avariyka.addView(new SettingsPictures.Emergency(this, skin));
        avariyka.addView(liveToggleRow("notifications", Text.FUN_AVARIYKA, Avariyka.isOn(this), on -> Avariyka.set(this, on)));
        avariyka.addView(quiet(Text.AVARIYKA_NOTE));
        column.addView(wrap(avariyka));

        column.addView(section(Text.FUN_CAT));
        column.addView(fatCat());

        filterGroup();
        column.addView(versions());

        showRestartBar();
        restoreScroll(previousScroll);
    }

    /**
     * The calculator, for an account that has proved itself. The coins are
     * the server's and are kept by account, so an account that anybody could
     * claim to be would have its coins staked by anybody.
     */
    private void openDep() {
        if (DepGame.verified()) {
            DepCalc.show(this, skin);
            return;
        }
        Panel.with(this, skin, Text.DEP_PROVE)
                .text(Text.DEP_PROVE_WHY)
                .primary(Text.PROVE, this::proveAccount)
                .quiet(Text.CANCEL, null)
                .show();
    }

    /** The fat tilcayo, as wide as the screen will have him. */
    private View fatCat() {
        final ImageView cat = new ImageView(this);
        cat.setAdjustViewBounds(true);
        cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cat.setContentDescription(Text.FUN_CAT);
        GradientDrawable round = new GradientDrawable();
        round.setColor(skin.card);
        round.setCornerRadius(dp(28));
        cat.setBackground(round);
        cat.setClipToOutline(true);
        cat.setMinimumHeight(dp(160));
        if (fatCatPicture != null) {
            cat.setImageBitmap(fatCatPicture);
        } else {
            final Context app = getApplicationContext();
            Net.away("fat cat", () -> {
                try {
                    byte[] raw = Cats.fat(app);
                    if (raw == null) return;
                    final android.graphics.Bitmap picture =
                            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    runOnUiThread(() -> {
                        fatCatPicture = picture;
                        if (picture != null && cat.isAttachedToWindow()) cat.setImageBitmap(picture);
                    });
                } catch (Throwable error) {
                    Diary.note("fat cat: " + error);
                }
            });
        }
        LinearLayout box = new LinearLayout(this);
        box.setPadding(edge(), 0, edge(), dp(6));
        box.addView(cat, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static android.graphics.Bitmap fatCatPicture;

    /** A row that opens something, with a word or two beside its name saying what kind of thing it is. */
    private View chipRow(String picture, String title, String[] chips, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        row.addView(label(title));
        LinearLayout marks = new LinearLayout(this);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        for (String word : chips) {
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextColor(onAccent());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            GradientDrawable back = new GradientDrawable();
            back.setColor(Accent.colour());
            back.setCornerRadius(dp(12));
            chip.setBackground(back);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.rightMargin = dp(6);
            marks.addView(chip, place);
        }
        row.addView(marks, grow());
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setOnClickListener(v -> action.run());
        return sized(row, 56);
    }

    private void restoreScroll(final int scrollY) {
        Fonts.applyTree(root);
        if (page == null) return;
        requestedScrollY = Math.max(0, scrollY);
        if (scrollRestore != null) page.removeCallbacks(scrollRestore);
        if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive()) {
            page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
            scrollRestoreListener = null;
        }
        if (requestedScrollY == 0) {
            page.scrollTo(0, 0);
            scrollRestore = null;
            return;
        }
        // Restore before the next frame is drawn. A posted scroll runs after
        // the new list has briefly appeared at the top of the screen.
        scrollRestoreListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                page.getViewTreeObserver().removeOnPreDrawListener(this);
                scrollRestoreListener = null;
                page.scrollTo(0, requestedScrollY);
                return true;
            }
        };
        page.getViewTreeObserver().addOnPreDrawListener(scrollRestoreListener);
    }

    /**
     * The bar that says a restart is due, pinned to the foot of the screen.
     *
     * It sits in the root frame rather than in the column, so it stays put
     * while the page scrolls under it, and it is built again on every rebuild
     * because the accent it is painted with may be what just changed.
     */
    private void showRestartBar() {
        if (pending && restartBar == null) {
            restartBar = restartBar();
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            floating.setMargins(dp(12), 0, dp(12), dp(12) + navigationBar());
            root.addView(restartBar, floating);
            Motion.rise(restartBar);
        }
        if (pending && restartBar instanceof LinearLayout) {
            LinearLayout bar = (LinearLayout)restartBar;
            styleRestartBar(bar);
            restartSymbol.colour(Accent.colour());
            restartWhy.setTextColor(skin.text);
            restartAction.setTextColor(onAccent());
            restartBackground.setColor(Accent.colour());
        }
        // Keep an existing bar in place: rebuilding rows must not replay its entrance.
        if (!pending && restartBar != null) {
            restartBar.animate().withEndAction(null).cancel();
            root.removeView(restartBar);
            restartBar = null;
            restartSymbol=null;restartWhy=null;restartAction=null;restartBackground=null;
        }
        column.setPadding(0, statusBar(), 0,
                dp(32) + (pending ? dp(92) + navigationBar() : 0));
    }

    /** The restart bar floats: a rounded tonal container with the accent laid over it. */
    private void styleRestartBar(LinearLayout bar) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Expressive.mix(skin.card, Accent.colour(), 0.22f));
        shape.setCornerRadius(dp(28));
        bar.setBackground(shape);
        bar.setElevation(dp(6));
    }

    /** A setting changed: redraw, and from now on the bar is up. */
    /** Something that needs a restart was changed on another screen: the store, say. */
    static void restartOwed() {
        pending = true;
        Unsaved.other();
    }

    private void markChanged() {
        // not simply "yes": put everything back as it was and there is
        // nothing left for a restart to apply
        pending = Unsaved.after(this);
        showRestartBar();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        // what the settings were before this tap changed any of them
        if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            Unsaved.look(this);
        }
        return super.dispatchTouchEvent(event);
    }

    /** A live preview based on the identity TikTok has loaded for this account. */

    // ------------------------------------------------- what MargyT brings along

    /** Counted in preferences: asked on the third visit and then never again. */
    private static final String VISITS = "settings_visits";
    private static final String ASKED = "settings_asked";
    private static final int ON_VISIT = 3;

    private java.util.List<Integer> gradientColours;
    private int gradientSlot;

    /**
     * The first thing on the screen, and the only thing here that asks for
     * something. Short, because a long one is an advertisement.
     */
    /** Closed with its cross: gone until the settings are opened again. */
    private boolean donateClosed;

    /** A supporter can still ask to see the offer, to show it to a friend or to give again. */
    static final String BANNER_ALWAYS = "donate_banner_always";

    private boolean bannerAlways() {
        return AccountAppearance.prefs(this).getBoolean(BANNER_ALWAYS, false);
    }

    /** Whether this account already wears the supporter's badge, hidden or shown. */
    private static boolean supporter() {
        try {
            if (Mine.holds(Tester.SUPPORTER)) return true;
            Badges.Badge[] worn = Badges.of(Account.id());
            if (worn != null) for (Badges.Badge one : worn) if (one != null && Tester.SUPPORTER.equals(one.id)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private View donateBanner() {
        // a filled card: a title, one line of what it gives, one button; how to
        // pay and where to write are on the page the button opens
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(8), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(Design.frosted() ? 18 : 12));
        background.setColor(skin.card);
        card.setBackground(background);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView head = new TextView(this);
        head.setText(Text.DONATE_BANNER);
        head.setTextColor(skin.text);
        M3Type.line(head, 16, 0.15f);
        head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(head, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = new ImageView(this);
        close.setImageDrawable(SettingsGlyph.make("close", skin.muted()));
        close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setContentDescription(Text.CLOSE);
        Motion.press(close);
        close.setOnClickListener(v -> {
            donateClosed = true;
            View banner = card;
            ViewGroup holder = banner.getParent() instanceof ViewGroup ? (ViewGroup) banner.getParent() : null;
            View gone = holder != null && holder != column ? holder : banner;
            gone.animate().alpha(0f).setDuration(160).withEndAction(() -> rebuild()).start();
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(top);

        TextView body = new TextView(this);
        body.setText(Text.DONATE_BANNER_TEXT);
        body.setTextColor(skin.muted());
        M3Type.bodyMedium(body);
        body.setPadding(0, 0, dp(8), 0);
        card.addView(body);

        TextView go = new TextView(this);
        go.setText(Text.DONATE_BANNER_BUTTON);
        go.setTextColor(onAccent());
        M3Type.line(go, 14, 0.1f);
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(24), 0, dp(24), 0);
        go.setBackground(Expressive.tile(this, Accent.colour(), 0x33FFFFFF, dp(20), dp(20), dp(8)));
        go.setOnClickListener(v -> showDonations());
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        below.topMargin = dp(16);
        below.gravity = Gravity.END;
        below.rightMargin = dp(8);
        card.addView(go, below);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(edge(), dp(8), edge(), dp(2));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    /**
     * Ask once, on the third time this screen is opened.
     *
     * Once, and only once: refusing is remembered for good, and so is having
     * been asked. Somebody who opens the settings twenty times should be left
     * alone after the first answer.
     */
    private void maybeAskForSupport() {
        try {
            android.content.SharedPreferences prefs =
                    getSharedPreferences(Margy.PREFS, MODE_PRIVATE);
            if (prefs.getBoolean(ASKED, false)) return;

            int visits = prefs.getInt(VISITS, 0) + 1;
            prefs.edit().putInt(VISITS, visits).apply();
            if (visits < ON_VISIT) return;
            prefs.edit().putBoolean(ASKED, true).apply();

            column.post(() -> Popup.ask(this, Text.REMIND_TITLE, Text.REMIND_TEXT,
                    Text.REMIND_MORE, this::showDonations,
                    Text.REMIND_NEVER, null,
                    null, null));
        } catch (Throwable error) {
            Diary.note("settings: " + error);
        }
    }

    private void tookFree(boolean ok, String trouble) {
        if (needsProof(ok, trouble)) return;
        Screen.say(ok ? Text.FREE_BADGE_GOT : Text.MINE_TOO_OFTEN);
        rebuild();
    }

    /**
     * Whether this account has proved it is anybody's, and the way to do it.
     *
     * At the head of the badge list because nothing under it saves until this
     * is done, and because the reason is worth seeing once.
     */
    private View proveRow() {
        boolean done = Proof.proved();
        LinearLayout row = row();
        row.setPadding(dp(16), 0, dp(16), 0);
        row.addView(icon(done ? "verified_user" : "fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(done ? Text.PROVE_DONE : Text.PROVE));
        text.addView(detail(done ? Text.PROVE_DONE_NOTE : Text.PROVE_NOTE));
        row.addView(text, grow());

        if (!done) row.setOnClickListener(v -> proveAccount());
        return sized(row, 64);
    }

    /**
     * Prove the account: by itself where TikTok lets the bio be saved from
     * here, and by hand -- the card with the code -- where it does not, or
     * where doing it by itself did not work.
     */
    private void proveAccount() {
        if (!BioProof.available()) {
            proveByHand();
            return;
        }
        Screen.say(Text.PROVE_AUTO_GOING);
        BioProof.start((proved, said) -> {
            if (isFinishing() || isDestroyed()) return;
            if (proved) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
                rebuild();
                return;
            }
            Screen.say(said);
            proveByHand();
        });
    }

    /** Ask for a code, then show the card that takes the name. */
    private void proveByHand() {
        String already = Proof.waiting();
        if (already.length() > 0) {
            askToCheck(already);
            return;
        }
        Screen.say(Text.MINE_WAIT);
        Proof.want((ok, trouble) -> {
            if (!ok) {
                Screen.say(trouble);
                return;
            }
            askToCheck(Proof.waiting());
        });
    }

    /** A refusal that only means the account has not been proved yet. */
    private boolean needsProof(boolean ok, String trouble) {
        if (ok || !Mine.PROVE.equals(trouble)) return false;
        proveAccount();
        return true;
    }

    private void askToCheck(String code) {
        Popup.prove(this, code, (name, reply) -> Proof.check(name, (ok, trouble) -> {
            if (ok) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
            }
            // the card stays open on a no, with the trouble under the code
            reply.said(ok, trouble, Proof.waiting(), Proof.wantsName());
            rebuild();
        }), reply -> Proof.fresh((ok, trouble) ->
                reply.said(ok, trouble, Proof.waiting(), false)));
    }

    private LinearLayout gradientCard() {
        LinearLayout card = card();
        if (!Mine.holds(Tester.SUPPORTER)) {
            card.addView(actionRow("gradient", Text.GRADIENT, null, () -> {}));
            card.addView(caption(Text.GRADIENT_ONLY));
            return card;
        }
        if (gradientColours == null) gradientColours = Gradient.starting();
        if (gradientSlot >= gradientColours.size()) gradientSlot = 0;

        card.addView(gradientPreview());
        card.addView(gradientDots());

        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        card.addView(slider(Text.GRADIENT_HUE, hsv[0], 359, value -> setSlot(value, -1, -1)));
        card.addView(slider(Text.GRADIENT_SAT, hsv[1], 100, value -> setSlot(-1, value, -1)));
        card.addView(slider(Text.GRADIENT_VALUE, hsv[2], 100, value -> setSlot(-1, -1, value)));

        card.addView(line());
        if (gradientColours.size() < Gradient.MOST) {
            card.addView(actionRow("palette", Text.GRADIENT_ADD, null, () -> {
                gradientColours.add(gradientColours.get(gradientColours.size() - 1));
                gradientSlot = gradientColours.size() - 1;
                rebuild();
            }));
        }
        if (gradientColours.size() > Gradient.FEWEST) {
            card.addView(actionRow("block", Text.GRADIENT_DROP_ONE, null, () -> {
                gradientColours.remove(gradientSlot);
                gradientSlot = 0;
                rebuild();
            }));
        }
        card.addView(line());
        card.addView(actionRow("star", Text.GRADIENT_SAVE, null, () -> Gradient.save(
                gradientColours, (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(actionRow("visibility_off", Text.GRADIENT_OFF, null, () -> Gradient.drop(
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(caption(Text.GRADIENT_NOTE));
        return card;
    }

    private void setSlot(int hue, int saturation, int value) {
        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        if (hue >= 0) hsv[0] = hue;
        if (saturation >= 0) hsv[1] = saturation;
        if (value >= 0) hsv[2] = value;
        gradientColours.set(gradientSlot, Gradient.from(hsv[0], hsv[1], hsv[2]));
        rebuild();
    }

    private View gradientPreview() {
        int[] colours = new int[gradientColours.size()];
        for (int i = 0; i < colours.length; i++) colours[i] = gradientColours.get(i);
        View bar = new View(this);
        android.graphics.drawable.GradientDrawable paint =
                new Design.ColourSample(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        colours);
        paint.setCornerRadius(dp(10));
        bar.setBackground(paint);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(16), dp(14), dp(16), dp(6));
        holder.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        return holder;
    }

    private View gradientDots() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(16), dp(6), dp(16), dp(10));
        for (int i = 0; i < gradientColours.size(); i++) {
            final int at = i;
            Dot dot = new Dot(this, gradientColours.get(i), i == gradientSlot);
            dot.setOnClickListener(v -> {
                gradientSlot = at;
                rebuild();
            });
            line.addView(dot, new LinearLayout.LayoutParams(dp(36), dp(36), 1f));
        }
        return line;
    }

    private static String fpsName(String which) {
        if (which == null || which.length() == 0) return Text.FPS_AUTO;
        return String.format(Text.FPS_LOCKED, which + " FPS");
    }

    private static String named(Badges.Badge badge) {
        if (badge.title != null && badge.title.length() > 0) return badge.title;
        return badge.text;
    }

    /** A card number is read off the screen by a person, so it is grouped. */
    private static String spaced(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    /** Open the thanks, on the shelf they live on, and take the screen down to them. */
    /** Not one of the shelves: the page the "donate" button opens, and nothing else does. */
    private static final int DONATE_PAGE = 99;

    private void showDonations() {
        settingsGroup = DONATE_PAGE;
        searchQuery = "";
        rebuild();
    }

    /**
     * How to get the badge, as three things to do in order.
     *
     * The button used to open the list of thanks and scroll to the card number
     * at the bottom of it, and people who had just decided to pay were left
     * looking at credits, wondering what to do next. This says it: pay here,
     * copy this, write there.
     */
    private void donatePage() {
        LinearLayout what = card();
        what.addView(quiet(Text.DONATE_BANNER_TEXT));
        column.addView(wrap(what));

        column.addView(section(Text.DONATE_STEP_PAY));
        LinearLayout pay = card();
        pay.addView(linkRow("star", Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY));
        pay.addView(line());
        pay.addView(linkRow("public", Text.BOOSTY, Text.BOOSTY_NOTE, BOOSTY));
        pay.addView(line());
        pay.addView(linkRow("public", Text.FUNPAY, Text.FUNPAY_NOTE, FUNPAY));
        pay.addView(line());
        pay.addView(linkRow("star", Text.STARS, Text.STARS_NOTE, STARS));
        column.addView(wrap(pay));

        column.addView(section(Text.DONATE_STEP_ID));
        LinearLayout mine = card();
        mine.addView(idRow(Text.ACCOUNT_ID, Account.id()));
        mine.addView(quiet(Text.DONATE_ID_NOTE));
        column.addView(wrap(mine));

        column.addView(section(Text.DONATE_STEP_WRITE));
        LinearLayout write = card();
        write.addView(actionRow("chat", Text.DONATE_BANNER_WRITE, Text.DONATE_WRITE_NOTE,
                () -> open(CHANNEL_WRITE)));
        write.addView(quiet(Text.DONATE_AFTER));
        column.addView(wrap(write));
    }

    private void rebuildAfterSwitchAnimation() {
        if (rebuildQueued) column.removeCallbacks(delayedRebuild);
        else rebuildQueued = true;
        column.postDelayed(delayedRebuild, M3Switch.ANIMATION_DURATION_MS);
    }

    // ----------------------------------------------------------- the rows

    private View switchRow() {
        LinearLayout row = row();
        final View lead = icon("language", true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(Margy.isEnabled(), false);
        row.addView(lead);
        row.addView(label(Text.CHANGE_REGION), grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Margy.isEnabled());
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            Margy.setEnabled(checked);
            markChanged();
            rebuildAfterSwitchAnimation();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.performClick();
        });
        return sized(row, 52);
    }

    private View countryHead() {
        String[] current = Margy.current();
        LinearLayout row = row();
        row.addView(flag(current[Margy.ISO]));
        row.setAlpha(Margy.isEnabled() ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(current[Margy.LABEL]));
        text.addView(detail(current[Margy.CARRIER] + "  ·  " + current[Margy.MCCMNC]
                + "  ·  " + current[Margy.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        row.addView(disclosure(countriesOpen,"countriesOpen"));

        if (Margy.isEnabled()) {
            row.setOnClickListener(v -> {
                disclosureKey = "countriesOpen";
                countriesOpen = !countriesOpen;
                rebuildAfterReveal(false);
            });
        }
        return sized(row, 60);
    }

    private View countryRow(final String[] country) {
        boolean selected = country[Margy.ISO].equals(Margy.iso());
        LinearLayout row = row();
        row.addView(flag(country[Margy.ISO]));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(country[Margy.LABEL]));
        text.addView(detail(country[Margy.CARRIER] + "  ·  " + country[Margy.MCCMNC]
                + "  ·  " + country[Margy.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        if (selected) row.addView(new Check(this, Accent.colour()));

        row.setOnClickListener(v -> {
            Margy.setIso(country[Margy.ISO]);
            countriesOpen = false;
            markChanged();
            rebuild();
        });
        return sized(row, 60);
    }

    /**
     * A country's flag, spelled rather than drawn.
     *
     * The two letters of a country's code have twin characters in the regional
     * indicator block, and a pair of those is a flag -- so `ru` becomes the
     * Russian flag with no picture involved, in whichever emoji font is on.
     * Which is also why it is worth having: with an emoji pack chosen in the
     * settings above, these are that pack's flags.
     */
    private View flag(String iso) {
        TextView view = new TextView(this);
        StringBuilder out = new StringBuilder();
        String code = iso.toUpperCase(Locale.US);
        for (int i = 0; i < code.length() && i < 2; i++) {
            int letter = code.charAt(i) - 'A';
            if (letter < 0 || letter > 25) return spacer();
            out.appendCodePoint(0x1F1E6 + letter);
        }
        view.setText(out.toString());
        // always Twemoji, whatever is chosen for the app: a phone's own flag
        // emoji is often two letters in a box, and this one is always a flag
        android.graphics.Typeface flags = Fonts.twemoji(this);
        if (flags != null) view.setTypeface(flags);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams size =
                new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT);
        size.rightMargin = dp(10);
        view.setLayoutParams(size);
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(34), 1));
        return view;
    }

    private View disclosure(boolean expanded, String key) {
        renderDisclosureKey=expanded ? key : null;
        Chevron arrow=new Chevron(this,skin.muted(),expanded,key.equals(disclosureKey));
        disclosureArrows.put(key,arrow);return arrow;
    }

    private View accentHead() {
        LinearLayout row = row();
        row.addView(icon("palette"));
        row.addView(label(Text.ACCENT_COLOUR), grow());
        row.addView(new Dot(this, Accent.colour(), false));

        row.addView(disclosure(accentOpen,"accentOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "accentOpen";
            accentOpen = !accentOpen;
            rebuildAfterReveal(accentOpen);
        });
        return sized(row, 52);
    }

    private View themeColoursHead() {
        LinearLayout row = row();
        row.addView(icon("tune"));
        row.addView(label(Text.THEME_COLOURS), grow());
        row.addView(disclosure(themeColoursOpen,"themeColoursOpen"));
        row.setContentDescription(Text.THEME_COLOURS);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            disclosureKey = "themeColoursOpen";
            themeColoursOpen = !themeColoursOpen;
            rebuildAfterReveal(themeColoursOpen);
        });
        return sized(row, 52);
    }

    // ------------------------------------------------------------ own badges

    private View mineHead() {
        LinearLayout row = row();
        row.addView(icon("favorite_border"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.MINE));
        text.addView(detail(Text.MINE_NOTE));
        row.addView(text, grow());

        row.addView(disclosure(mineOpen,"mineOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "mineOpen";
            mineOpen = !mineOpen;
            if (mineOpen) {
                ordering = null;
                Mine.ask(this::rebuild);
            }
            rebuildAfterReveal(mineOpen);
        });
        return sized(row, 60);
    }

    private View mineRows() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        // the list the server last sent wins over whatever was being
        // rearranged: the first answer on a cold start is empty, and without
        // this that empty answer is what stayed on screen
        if (Mine.tookFresh() && !rearranged) ordering = null;
        if (ordering == null) ordering = Mine.held();
        rows.addView(proveRow());
        if (ordering.isEmpty()) {
            rows.addView(quiet(Mine.everAsked() ? Text.MINE_NONE : Text.MINE_WAIT));
            return rows;
        }

        for (int i = 0; i < ordering.size(); i++) {
            final int at = i;
            final Mine.Held one = ordering.get(i);
            if (i > 0) rows.addView(line());

            LinearLayout row = row();
            row.setPadding(edge(), 0, edge(), 0);

            Badges.Badge badge = Badges.byId(one.id);
            row.addView(badgeDot(badge));
            // the short name, which is what a badge is called; the long line
            // is its description and belongs in the popup, not in a list
            row.addView(label(badge == null ? one.id : named(badge)), grow());

            // up and down rather than dragging: a row that can be dragged has
            // to fight the page it is on for the same gesture
            row.addView(mover(true, at > 0, () -> {
                java.util.Collections.swap(ordering, at, at - 1);
                rearranged = true;
                rebuild();
            }));
            row.addView(mover(false, at < ordering.size() - 1, () -> {
                java.util.Collections.swap(ordering, at, at + 1);
                rearranged = true;
                rebuild();
            }));

            M3Switch toggle = new M3Switch(this);
            toggle.colours(Accent.colour(), skin.muted(), skin.card);
            toggle.setChecked(one.shown, false);
            toggle.setOnChanged(checked -> {
                one.shown = checked;
                rearranged = true;
            });
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(52), dp(32));
            size.leftMargin = dp(10);
            row.addView(toggle, size);

            rows.addView(sized(row, 60));
        }

        rows.addView(button(Text.MINE_SAVE, () -> Mine.save(ordering,
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Screen.say(ok ? Text.MINE_SAVED : Text.MINE_TOO_OFTEN);
                    if (ok) {
                        rearranged = false;
                        ordering = null;
                    }
                    rebuild();
                })));
        return rows;
    }

    /** Something to press, rather than a row that happens to do something. */
    private View button(String title, final Runnable action) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(onAccent());
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(v -> action.run());
        LinearLayout holder = new LinearLayout(this);
        if (Design.frosted()) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            view.setPadding(0, dp(15), 0, dp(15));
            // a full pill that squares off a little while it is held
            view.setBackground(Expressive.tile(this, Accent.colour(), 0x33FFFFFF, dp(28), dp(28), dp(12)));
            holder.setPadding(dp(16), dp(12), dp(16), dp(16));
            holder.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return holder;
        }
        // 40dp, fully round, label large; the margin around it still answers the finger
        M3Type.line(view, 14, 0.1f);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setBackground(Expressive.tile(this, Accent.colour(), 0x33FFFFFF, dp(20), dp(20), dp(8)));
        holder.setPadding(edge(), dp(12), edge(), dp(16));
        holder.setOnClickListener(v -> action.run());
        holder.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        return holder;
    }

    private View mover(boolean up, boolean can, final Runnable action) {
        android.widget.FrameLayout target=new android.widget.FrameLayout(this);
        Chevron arrow=new Chevron(this,can?skin.text:skin.muted(),up,false);
        android.widget.FrameLayout.LayoutParams image=new android.widget.FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);
        target.addView(arrow,image);
        target.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        target.setContentDescription(up ? Local.of("Выше") : Local.of("Ниже"));
        target.setEnabled(can);
        if (can) {Motion.press(target);target.setOnClickListener(v -> action.run());}
        return target;
    }

    private View badgeDot(Badges.Badge badge) {
        android.widget.ImageView view = new android.widget.ImageView(this);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(26), dp(26));
        size.rightMargin = dp(12);
        view.setLayoutParams(size);
        if (badge == null) return view;
        android.graphics.Bitmap picture = badge.image.length() == 0
                ? null : Badges.picture(this, badge.image);
        if (picture != null) {
            view.setImageBitmap(picture);
        } else {
            // a badge that names no picture of its own is the mod's own note,
            // which is the same thing it is drawn as beside a name
            view.setImageBitmap(Badges.note());
        }
        if (badge.colour != 0) {
            view.setColorFilter(badge.colour, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        return view;
    }

    /** The one that is free until it is not. */
    private View freeBanner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Math.max(skin.radius, dp(18)));
        background.setColor(blend(0xFF40E0D0, skin.card, 0.82f));
        card.setBackground(background);

        TextView head = new TextView(this);
        head.setText(Text.FREE_BADGE);
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView body = new TextView(this);
        body.setText(Text.FREE_BADGE_TEXT);
        body.setTextColor(skin.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        TextView take = new TextView(this);
        take.setText(Text.FREE_BADGE_TAKE);
        take.setTextColor(0xFF10221F);
        take.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        take.setTypeface(Typeface.DEFAULT_BOLD);
        take.setGravity(Gravity.CENTER);
        take.setPadding(0, dp(11), 0, dp(11));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF40E0D0);
        pill.setCornerRadius(dp(14));
        take.setBackground(pill);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = dp(14);
        take.setOnClickListener(v -> {
            if (!Mine.everAsked()) {
                Mine.ask(() -> Mine.takeFree(this::tookFree));
                return;
            }
            Mine.takeFree(this::tookFree);
        });
        card.addView(take, below);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(14), dp(20), dp(2));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private static int blend(int colour, int into, float how) {
        int red = (int) (((colour >> 16) & 0xFF) * (1 - how) + ((into >> 16) & 0xFF) * how);
        int green = (int) (((colour >> 8) & 0xFF) * (1 - how) + ((into >> 8) & 0xFF) * how);
        int blue = (int) ((colour & 0xFF) * (1 - how) + (into & 0xFF) * how);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    // -------------------------------------------------------- the colours

    private View wallpaperRow() {
        final int colour = Accent.fromWallpaper();
        LinearLayout row = row();
        row.addView(icon("wallpaper"));
        row.addView(label(Text.ACCENT_WALLPAPER), grow());
        row.addView(new Dot(this, colour, Accent.isMaterial()));
        row.setOnClickListener(v -> {
            Accent.setMaterial(true);
            markChanged();
            rebuild();
        });
        return sized(row, 52);
    }

    /** The inline body expands with the disclosure arrow, as one group. */
    private View appear(View view) {
        boolean animated=!revealAnimationUsed && Motion.enabled(this);
        if(animated)revealAnimationUsed=true;
        DisclosureBody body=new DisclosureBody(this,view,animated);
        if(renderDisclosureKey!=null) {
            java.util.List<DisclosureBody> list=disclosureBodies.get(renderDisclosureKey);
            if(list==null){list=new java.util.ArrayList<>();disclosureBodies.put(renderDisclosureKey,list);}
            list.add(body);
        }
        return body;
    }

    private void rebuildAfterReveal(boolean expanded) {
        if(collapsing)return;
        java.util.List<DisclosureBody> bodies=disclosureBodies.get(disclosureKey);
        if(!expanded && bodies!=null && !bodies.isEmpty() && Motion.enabled(this)) {
            collapsing=true;
            Chevron arrow=disclosureArrows.get(disclosureKey);
            if(arrow!=null)arrow.animate().rotation(0f).setDuration(180L).start();
            final int[] remaining={bodies.size()};
            for(DisclosureBody body:new java.util.ArrayList<>(bodies))body.collapse(()->{
                if(--remaining[0]==0){collapsing=false;revealAnimationRequested=false;disclosureKey=null;rebuild();}
            });
            return;
        }
        revealAnimationRequested=expanded;rebuild();disclosureKey=null;
    }







    private static final int CREATE_BACKUP = 0x4D42; // "MB"
    private static final int PICK_BACKUP = 0x4D49;   // "MI"

    private void createBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "margyt-settings.json");
            startActivityForResult(intent, CREATE_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void pickBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, PICK_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void reviewBackup(final Uri source) {
        Screen.progress(Text.BACKUP_READING, 0);
        Net.away("backup", () -> {
            final Backup.Review review = Backup.review(this, source);
            if (review.fits()) Screen.progressDone(); else Screen.progressGone();
            runOnUiThread(() -> {
                if (!review.fits()) {
                    Screen.say(Text.BACKUP_FAILED);
                    return;
                }
                new ModDialog.Builder(this, skin)
                        .setTitle(Text.BACKUP_IMPORT)
                        .setMessage(Text.backupPreview(review.count()))
                        .setNegativeButton(Text.CANCEL, null)
                        .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                            if (Backup.apply(this, review)) {
                                markChanged();
                                rebuild();
                                Screen.say(Text.BACKUP_DONE);
                            } else Screen.say(Text.BACKUP_FAILED);
                        })
                        .show();
            });
        });
    }

    private void pickResetGroup() {
        final String[] groups = {
                Text.RESET_APPEARANCE, Text.RESET_PLAYER, Text.RESET_FEED, Text.RESET_ALL
        };
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.BACKUP_RESET)
                    .setMessage(Text.BACKUP_RESET_ASK)
                    .setItems(groups, (dialog, chosen) -> askToReset(chosen + 1))
                    .setNegativeButton(Text.CANCEL, null)
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void askToReset(final int group) {
        new ModDialog.Builder(this, skin)
                .setTitle(Text.BACKUP_RESET)
                .setMessage(Text.BACKUP_RESET_ASK)
                .setNegativeButton(Text.CANCEL, null)
                .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                    Backup.reset(this, group);
                    markChanged();
                    rebuild();
                })
                .show();
    }

    /** A value between nothing and something, set by dragging. */
    private interface Chosen {
        void at(int value);
    }

    private View slider(String title, int now, int most, final Chosen chosen) {
        return slider(title, now, most, 0, "", chosen);
    }

    /** Ползунок с живым числом: показывает (положение + shift) и единицу измерения. */
    private View slider(String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider("tune", title, now, most, shift, unit, chosen);
    }

    /** The same, with its own icon in front so no row of the list is left bare. */
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider(picture,title,now,most,shift,unit,chosen,false);
    }
    private View liveSlider(String picture,String title,int now,int most,int shift,String unit,Chosen chosen){
        return slider(picture,title,now,most,shift,unit,chosen,true);
    }
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen, final boolean live) {
        return slider(picture, title, now, most, shift, unit, chosen, live, null);
    }

    /** `moving` hears every position while the finger is on it: for a picture that follows. */
    private View slider(String picture, String title, int now, int most, final int shift, final String unit,
                        final Chosen chosen, final boolean live, final Chosen moving) {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(20), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(picture));
        head.addView(label(title), new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView number = new TextView(this);
        number.setTextColor(accentInk());
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        number.setText((Math.min(now, most) + shift) + unit);
        head.addView(number);
        rows.addView(head);

        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(most);
        bar.setProgress(Math.min(now, most));
        Expressive.slider(bar, Accent.colour(), Expressive.mix(skin.card, skin.text, 0.2f));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seek, int value, boolean human) {
                number.setText((value + shift) + unit);
                if(live && human)chosen.at(value);
                if(moving != null && human)moving.at(value);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seek) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seek) {
                if(!live)chosen.at(seek.getProgress());
            }
        });
        LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barSize.leftMargin = dp(56);
        rows.addView(bar, barSize);
        return rows;
    }


    // ---------------------------------------------------------- the font

    private static final int PICK_FONT = 0x4D46;  // "MF"
    private static final int PICK_EMOJI = 0x4D45;  // "ME"
    private static final int PICK_PROFILE_BADGE = 0x5450; // "TP"




    private GradientDrawable colourPreview(int colour) {
        GradientDrawable preview = new Design.ColourSample();
        preview.setColor(colour);
        preview.setCornerRadius(dp(12));
        preview.setStroke(dp(1), skin.muted());
        return preview;
    }

    private int contrastingText(int colour) {
        float luminance = (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
        return luminance > 0.55f ? Color.BLACK : Color.WHITE;
    }




    private View fontHead() {
        LinearLayout row = row();
        row.addView(icon("text_fields"));
        row.addView(label(Text.FONT), grow());

        TextView now = detail(fontName(Fonts.name()));
        now.setTextColor(skin.text);
        row.addView(now);

        row.addView(disclosure(fontOpen,"fontOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "fontOpen";
            fontOpen = !fontOpen;
            rebuildAfterReveal(fontOpen);
        });
        return sized(row, 52);
    }

    private static String fontName(String which) {
        if (Fonts.SANS.equals(which)) return Text.FONT_SANS;
        if (Fonts.SANS_LIGHT.equals(which)) return Text.FONT_SANS_LIGHT;
        if (Fonts.SANS_CONDENSED.equals(which)) return Text.FONT_SANS_CONDENSED;
        if (Fonts.SERIF.equals(which)) return Text.FONT_SERIF;
        if (Fonts.MONOSPACE.equals(which)) return Text.FONT_MONOSPACE;
        if (Fonts.CURSIVE.equals(which)) return Text.FONT_CURSIVE;
        if (Fonts.FILE.equals(which)) return Text.FONT_FILE;
        return Text.FONT_SYSTEM;
    }

    private View fontChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        String now = Fonts.name();
        for (int i = 0; i < Fonts.PRESETS.length; i++) {
            final String which = Fonts.PRESETS[i];
            if (i > 0) rows.addView(line());
            rows.addView(pickRow(fontName(which), which.equals(now),
                    Fonts.SYSTEM.equals(which) ? null : which, () -> {
                        Fonts.choose(which);
                        markChanged();
                        rebuild();
                    }));
        }
        rows.addView(line());
        rows.addView(actionRow("download", Text.FONT_PICK,
                Fonts.FILE.equals(now) ? Text.FONT_FILE : null, this::pickFont));

        rows.addView(line());
        rows.addView(section(Text.EMOJI));
        String emoji = Fonts.emoji();
        List<EmojiPackManager.Pack> packs = EmojiPackManager.packs(this);
        boolean firstPack = true;
        for (EmojiPackManager.Pack pack : packs) {
            final String which = pack.id;
            if (Fonts.EMOJI_FILE.equals(which)) continue;
            if (!firstPack) rows.addView(line());
            firstPack = false;
            rows.addView(pickRow(emojiName(pack), which.equals(emoji), null, () -> {
                Fonts.chooseEmoji(this, which);
                markChanged();
                rebuild();
            }));
        }
        rows.addView(line());
        rows.addView(actionRow("download", Text.EMOJI_FILE,
                Fonts.EMOJI_FILE.equals(emoji) ? Text.EMOJI_FILE : null, this::pickEmoji));
        TextView sample = new TextView(this);
        sample.setText(Text.EMOJI_PREVIEW);
        sample.setTextColor(skin.text);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        sample.setGravity(Gravity.CENTER);
        sample.setPadding(dp(16), dp(12), dp(16), dp(12));
        android.graphics.Typeface emojiPreview = Fonts.preview(this, emoji);
        if (emojiPreview != null) sample.setTypeface(emojiPreview);
        rows.addView(sample, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        rows.addView(quiet(Text.EMOJI_NOTE));
        return rows;
    }

    /** The fonts and the emoji packs, each a tile written in itself. */
    private View fontCarousels() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = row();
        head.addView(icon("text_fields"));
        head.addView(label(Text.FONT), grow());
        TextView now = detail(fontName(Fonts.name()));
        now.setTextColor(skin.text);
        head.addView(now);
        box.addView(sized(head, 52));
        LinearLayout letters = new LinearLayout(this);
        View chosen = null;
        String font = Fonts.name();
        for (final String which : Fonts.PRESETS) {
            Typeface face = Fonts.SYSTEM.equals(which) ? Typeface.DEFAULT : Typeface.create(which, Typeface.NORMAL);
            View tile = typeTile(face, "\u0410\u0430", fontName(which), which.equals(font), () -> {
                Fonts.choose(which);
                markChanged();
                rebuild();
            });
            if (which.equals(font)) chosen = tile;
            letters.addView(tile);
        }
        android.widget.HorizontalScrollView one = carousel(letters);
        box.addView(one);
        centre(one, chosen);
        box.addView(actionRow("download", Text.FONT_PICK, Fonts.FILE.equals(font) ? Text.FONT_FILE : null, this::pickFont));
        box.addView(line());

        LinearLayout emojiHead = row();
        emojiHead.addView(icon("star"));
        emojiHead.addView(label(Text.EMOJI), grow());
        box.addView(sized(emojiHead, 52));
        LinearLayout faces = new LinearLayout(this);
        View chosenPack = null;
        String emoji = Fonts.emoji();
        for (EmojiPackManager.Pack pack : EmojiPackManager.packs(this)) {
            final String which = pack.id;
            if (Fonts.EMOJI_FILE.equals(which)) continue;
            View tile = typeTile(Fonts.preview(this, which), "\uD83D\uDE00\uD83D\uDC31", emojiName(pack), which.equals(emoji), () -> {
                Fonts.chooseEmoji(this, which);
                markChanged();
                rebuild();
            });
            if (which.equals(emoji)) chosenPack = tile;
            faces.addView(tile);
        }
        android.widget.HorizontalScrollView two = carousel(faces);
        box.addView(two);
        centre(two, chosenPack);
        box.addView(actionRow("download", Text.EMOJI_FILE, Fonts.EMOJI_FILE.equals(emoji) ? Text.EMOJI_FILE : null, this::pickEmoji));
        box.addView(quiet(Text.EMOJI_NOTE));
        return box;
    }

    /**
     * A sample written in its own font. Fonts passes over a view whose class
     * is called an icon, as it does TikTok's icon fonts: otherwise the chosen
     * font was put on every sample, and they all looked the same.
     */
    private static final class SampleIcon extends TextView {
        SampleIcon(Context context) { super(context); }
    }

    private View typeTile(Typeface face, String sample, String name, boolean on, final Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(4), dp(2), dp(4), dp(2));
        TextView big = new SampleIcon(this);
        big.setText(sample);
        big.setTextColor(skin.text);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        if (face != null) big.setTypeface(face);
        big.setGravity(Gravity.CENTER);
        GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(20));
        back.setColor(on ? accentTonal() : Expressive.mix(skin.card, skin.text, 0.06f));
        if (on) back.setStroke(dp(2), Accent.colour());
        big.setBackground(back);
        tile.addView(big, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView label = new TextView(this);
        label.setText(name);
        M3Type.line(label, 12, 0.4f);
        label.setTextColor(on ? skin.text : skin.muted());
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(0, dp(6), 0, 0);
        tile.addView(label, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));
        tile.setOnClickListener(v -> action.run());
        Motion.press(tile);
        return tile;
    }

    private String emojiName(EmojiPackManager.Pack pack) {
        if (Fonts.SYSTEM.equals(pack.id)) return Text.EMOJI_SYSTEM;
        return pack.name;
    }

    private void pickEmoji() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_EMOJI);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * A row that is one of several answers to one question.
     *
     * `preview` is a typeface name to write the row in, so a font can be read
     * before it is chosen; null leaves the row in the app's own.
     */
    private View pickRow(String title, boolean chosen, String preview, final Runnable action) {
        LinearLayout row = row();
        row.setPadding(edge(), 0, edge(), 0);

        TextView name = label(title);
        if (preview != null) {
            try {
                name.setTypeface(android.graphics.Typeface.create(
                        preview, android.graphics.Typeface.NORMAL));
            } catch (Throwable ignored) {
            }
        }
        row.addView(name, grow());
        if (chosen) row.addView(new Check(this, Accent.colour()));
        row.setOnClickListener(v -> action.run());
        return sized(row, 52);
    }

    private void pickFont() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FONT);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the icon

    // ------------------------------------------------------------ choosing

    /** Told which of several was picked. */
    private interface Picked { void picked(int index); }

    /**
     * Material 3 segmented buttons: one outlined row, the chosen part filled
     * and ticked. For a choice of a few, in place of a list or a window.
     */
    private View segmented(final String[] labels, int chosen, final Picked picked) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(edge(), dp(6), edge(), dp(14));
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] parts = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView part = new TextView(this);
            part.setGravity(Gravity.CENTER);
            part.setSingleLine(true);
            part.setEllipsize(android.text.TextUtils.TruncateAt.END);
            M3Type.line(part, 14, 0.1f);
            part.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            part.setPadding(dp(6), 0, dp(6), 0);
            parts[i] = part;
            part.setOnClickListener(v -> {
                paintSegments(parts, labels, index);
                picked.picked(index);
            });
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if (i > 0) place.leftMargin = -dp(1);
            row.addView(part, place);
        }
        paintSegments(parts, labels, chosen);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        return box;
    }

    private void paintSegments(TextView[] parts, String[] labels, int chosen) {
        for (int i = 0; i < parts.length; i++) {
            boolean on = i == chosen, first = i == 0, last = i == parts.length - 1;
            GradientDrawable back = new GradientDrawable();
            float r = dp(20);
            back.setCornerRadii(new float[] {first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0,
                    last ? r : 0, last ? r : 0, first ? r : 0, first ? r : 0});
            back.setColor(on ? accentTonal() : 0);
            back.setStroke(dp(1), skin.muted());
            parts[i].setBackground(back);
            parts[i].setTextColor(on ? skin.text : skin.muted());
            parts[i].setText(on ? "\u2713 " + labels[i] : labels[i]);
        }
    }

    /** Filter chips in a row that scrolls: for a choice of more than fit across. */
    private View chips(final String[] labels, int chosen, final Picked picked) {
        LinearLayout strip = new LinearLayout(this);
        final java.util.List<TextView> all = new java.util.ArrayList<TextView>();
        View chosenChip = null;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            M3Type.line(chip, 14, 0.1f);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            all.add(chip);
            if (i == chosen) chosenChip = chip;
            chip.setOnClickListener(v -> {
                paintChips(all, index);
                picked.picked(index);
            });
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            place.rightMargin = dp(8);
            strip.addView(chip, place);
        }
        paintChips(all, chosen);
        android.widget.HorizontalScrollView scroll = carousel(strip);
        strip.setPadding(edge(), dp(4), edge(), dp(14));
        centre(scroll, chosenChip);
        return scroll;
    }

    private void paintChips(java.util.List<TextView> all, int chosen) {
        for (int i = 0; i < all.size(); i++) {
            boolean on = i == chosen;
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(8));
            back.setColor(on ? accentTonal() : 0);
            if (!on) back.setStroke(dp(1), skin.muted());
            all.get(i).setBackground(back);
            all.get(i).setTextColor(on ? skin.text : skin.muted());
        }
    }

    /**
     * TikTok's feed on a phone for one card. The bar, the seek bar, the date
     * and the buttons are drawn as they are set; the screen saver and the
     * simple feed only on their own cards, or every phone came out faint.
     */
    private SettingsArt.Phone phone(String card) {
        SettingsArt.Phone art = new SettingsArt.Phone(this, skin, card)
                .burn(false, 0, true).simple(false)
                .capsule(NavBar.isEnabled()).seekbar(Seekbar.isEnabled()).date(Dates.isEnabled());
        for (int i = 0; i < 4; i++) art.rail(i, FeedRail.shown(i));
        return art;
    }

    /** The switch inside a row, to move it from somewhere else on the page. */
    private static M3Switch switchIn(View row) {
        if (row instanceof M3Switch) return (M3Switch) row;
        if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int i = 0; i < group.getChildCount(); i++) {
                M3Switch found = switchIn(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ carousels

    /** A row that scrolls sideways, for choosing one of many by its picture. */
    private android.widget.HorizontalScrollView carousel(LinearLayout strip) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(10), dp(2), dp(10), dp(14));
        scroll.addView(strip);
        return scroll;
    }

    /** Brought to the middle once laid out, so the chosen one is in sight. */
    private void centre(final android.widget.HorizontalScrollView scroll, final View chosen) {
        if (chosen == null) return;
        scroll.post(() -> scroll.scrollTo(Math.max(0,
                chosen.getLeft() - (scroll.getWidth() - chosen.getWidth()) / 2), 0));
    }

    /** The launcher icons as pictures to scroll through, the chosen one ringed. */
    private View iconCarousel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = row();
        head.addView(icon("image"));
        head.addView(label(Text.ICON), grow());
        final TextView named = detail(Launcher.nameOf(Launcher.chosen()));
        named.setTextColor(skin.text);
        head.addView(named);
        box.addView(sized(head, 52));

        LinearLayout strip = new LinearLayout(this);
        final java.util.List<View> frames = new java.util.ArrayList<View>();
        View chosen = null;
        for (String which : Launcher.shown()) {
            View tile = iconTile(which, frames, named);
            if (which.equals(Launcher.chosen())) chosen = tile;
            strip.addView(tile);
        }
        // the icons from before 2.1.0, only for whoever had the mod then
        if (Launcher.legacyOffered()) {
            TextView legacy = new TextView(this);
            legacy.setText(Text.ICON_LEGACY);
            legacy.setTextColor(accentInk());
            M3Type.titleSmall(legacy);
            legacy.setGravity(Gravity.CENTER);
            legacy.setPadding(dp(14), 0, dp(6), dp(22));
            strip.addView(legacy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            for (String which : Launcher.LEGACY) {
                View tile = iconTile(which, frames, named);
                if (which.equals(Launcher.chosen())) chosen = tile;
                strip.addView(tile);
            }
        }
        android.widget.HorizontalScrollView scroll = carousel(strip);
        box.addView(scroll);
        centre(scroll, chosen);
        return box;
    }

    private View iconTile(final String which, final java.util.List<View> frames, final TextView named) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(4), dp(2), dp(4), dp(2));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setPadding(dp(4), dp(4), dp(4), dp(4));
        frame.setTag(which);
        ImageView picture = new ImageView(this);
        Bitmap shot = Launcher.preview(which);
        if (shot != null) picture.setImageBitmap(shot);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        picture.setClipToOutline(true);
        frame.addView(picture, new android.widget.FrameLayout.LayoutParams(dp(60), dp(60)));
        // wrapped, or the column stretched it to the label's width and the ring came out oblong
        tile.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frames.add(frame);
        TextView name = new TextView(this);
        name.setText(Launcher.nameOf(which));
        M3Type.line(name, 12, 0.4f);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(4), 0, 0);
        name.setTag("name");
        tile.addView(name, new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        ring(frames, Launcher.chosen());
        tile.setOnClickListener(v -> {
            Launcher.choose(this, which);
            ring(frames, which);
            named.setText(Launcher.nameOf(which));
        });
        Motion.press(tile);
        return tile;
    }

    /** The chosen one ringed in the accent and named in full colour; the rest quiet. */
    private void ring(java.util.List<View> frames, String chosen) {
        for (View frame : frames) {
            boolean on = chosen.equals(frame.getTag());
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(20));
            ring.setColor(0);
            if (on) ring.setStroke(dp(3), Accent.colour());
            frame.setBackground(ring);
            View tile = (View) frame.getParent();
            if (tile instanceof ViewGroup) {
                View name = ((ViewGroup) tile).findViewWithTag("name");
                if (name instanceof TextView) ((TextView) name).setTextColor(on ? skin.text : skin.muted());
            }
        }
    }

    /** narezany's profile with the avatar in the chosen shape, and the shapes to choose from under it. */
    private View avatarShapes() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final SettingsArt.Profile art = new SettingsArt.Profile(this, skin);
        box.addView(art);
        LinearLayout head = row();
        head.addView(icon("account_circle"));
        head.addView(label(Text.AVATAR_SHAPE), grow());
        final TextView named = detail(AvatarShape.nameOf(AvatarShape.chosen()));
        named.setTextColor(skin.text);
        head.addView(named);
        box.addView(sized(head, 52));

        LinearLayout strip = new LinearLayout(this);
        final java.util.List<View> chips = new java.util.ArrayList<View>();
        View chosen = null;
        for (final String shape : AvatarShape.ALL) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setPadding(dp(10), dp(10), dp(10), dp(8));
            chip.setTag(shape);
            ImageView picture = new ImageView(this);
            picture.setTag("picture");
            chip.addView(picture, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView name = new TextView(this);
            name.setText(AvatarShape.nameOf(shape));
            M3Type.line(name, 12, 0.4f);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setTag("name");
            name.setPadding(0, dp(6), 0, 0);
            // a width of its own: wrapped to the word, letter spacing cut the last letter off
            chip.addView(name, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
            chips.add(chip);
            if (shape.equals(AvatarShape.chosen())) chosen = chip;
            chip.setOnClickListener(v -> {
                if (shape.equals(AvatarShape.chosen())) return;
                AvatarShape.choose(this, shape);
                art.shape(shape);
                named.setText(AvatarShape.nameOf(shape));
                shapeChips(chips);
                // a picture already cut keeps its shape until it is cut again
                markChanged();
            });
            Motion.press(chip);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.rightMargin = dp(4);
            strip.addView(chip, place);
        }
        shapeChips(chips);
        android.widget.HorizontalScrollView scroll = carousel(strip);
        box.addView(scroll);
        centre(scroll, chosen);
        return box;
    }

    private void shapeChips(java.util.List<View> chips) {
        String now = AvatarShape.chosen();
        for (View chip : chips) {
            boolean on = now.equals(chip.getTag());
            GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(18));
            back.setColor(on ? accentTonal() : 0);
            chip.setBackground(back);
            View picture = chip.findViewWithTag("picture");
            if (picture instanceof ImageView) ((ImageView) picture).setImageBitmap(
                    AvatarShape.picture((String) chip.getTag(), dp(36), on ? Accent.colour() : skin.muted()));
            View name = chip.findViewWithTag("name");
            if (name instanceof TextView) ((TextView) name).setTextColor(on ? skin.text : skin.muted());
        }
    }


    /**
     * What a text colour and a background colour may be.
     *
     * Two ramps rather than one palette: what a background wants is a set of
     * near-blacks and near-whites, and what text wants is the other end. The
     * accent's own dots are bright colours and would be no use for either.
     */
    private static final int[] DARKS = {
            0xFF000000, 0xFF0B0B0F, 0xFF121212, 0xFF161823, 0xFF1B1B1B,
            0xFF0D1B2A, 0xFF12232E, 0xFF1A1423, 0xFF14261C, 0xFF241A1A,
    };

    private static final int[] LIGHTS = {
            0xFFFFFFFF, 0xFFF6F6F6, 0xFFEDEDED, 0xFFE8E4DA, 0xFFDCDCDC,
            0xFFCFD8DC, 0xFFB0B8C4, 0xFF8A8A8A, 0xFF5A5A5A, 0xFF2E2E2E,
    };


    private interface ColourPicked { void set(int colour); }

    private View appearanceColourRow(final String role) {
        final int selected = AppearanceColors.ACCENT.equals(role)
                ? Accent.colour() : AppearanceColors.get(role);
        LinearLayout row = row();
        row.setPadding(edge(), 0, edge(), 0);
        row.addView(label(AppearanceColors.label(role)), grow());
        row.addView(new Dot(this, selected == 0 ? skin.muted() : selected, false));
        TextView value = detail(selected == 0 ? "TikTok" : String.format(Locale.US,
                "#%06X", selected & 0xFFFFFF));
        value.setTextColor(skin.muted());
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(10);
        row.addView(value, valueParams);
        row.setOnClickListener(v -> askColour(AppearanceColors.label(role),
                selected == 0 ? Accent.TIKTOK : selected, colour -> {
                    AppearanceColors.set(role, colour);
                    markChanged();
                    rebuild();
                }));
        return sized(row, 52);
    }

    private void askColour(String title, int initial, ColourPicked picked) {
        Panel panel = Panel.with(this,skin,title);
        EditText input = panel.field("#RRGGBB");
        input.setText(String.format(Locale.US, "#%06X", initial & 0xFFFFFF));
        panel.primaryKeepOpen(getString(android.R.string.ok), () -> {
                    try {
                        int colour = android.graphics.Color.parseColor(
                                input.getText().toString().trim());
                        picked.set(colour);
                        panel.close();
                    } catch (IllegalArgumentException error) {
                        input.setError(Local.of("Укажите цвет в формате #RRGGBB"));
                    }
                });
        panel.quiet(Text.CANCEL,null); panel.show();
    }

    /** Keep theme presets together in one menu row instead of three buttons. */
    private void chooseThemePreset() {
        final String[] choices = {Text.THEME_PRESET_DARK, Text.THEME_PRESET_BLUE,
                Text.THEME_RESET};
        new ModDialog.Builder(this, skin)
                .setTitle(Text.THEME_PRESETS)
                .setItems(choices, (dialog, index) -> {
                    
                    if (index == 0) Themes.preset(0);
                    else if (index == 1) Themes.preset(1);
                    else Themes.reset();
                    markChanged();
                    rebuild();
                })
                .setNegativeButton(Text.CANCEL, null)
                .show();
    }

    private void chooseAccentColour() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AccentColourPicker picker = new AccentColourPicker(this, Accent.colour());
        final View sample = new View(this);
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(dp(56), dp(28));
        sampleParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(sample, sampleParams);
        content.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(238)));
        picker.onColourChanged(colour -> sample.setBackgroundColor(colour));
        sample.setBackgroundColor(Accent.colour());
        android.widget.HorizontalScrollView extras=new android.widget.HorizontalScrollView(this);extras.setHorizontalScrollBarEnabled(false);LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(0,dp(8),0,dp(8));
        final java.util.List<Dot> extraDots=new java.util.ArrayList<>();for(int colour:new int[]{0xFFFFA726,0xFF44D65B,0xFFAA45EE,0xFFF343C9,0xFF405FE8,0xFF35B8E5,0xFFFA4654}){Dot dot=new Dot(this,colour,colour==Accent.colour());extraDots.add(dot);dot.setContentDescription(String.format(Locale.ROOT,"#%06X",colour&0xffffff));Motion.touch(dot);dot.setOnClickListener(v->{picker.setColour(colour);for(Dot other:extraDots)other.choose(other==dot,true);});LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(44),dp(44));size.rightMargin=dp(8);strip.addView(dot,size);}extras.addView(strip);content.addView(extras);
        new ModDialog.Builder(this, skin).setTitle(Text.ACCENT_PICKER)
                .setView(content).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Accent.setCustom(picker.colour());
                    if(accentCustomSwatch!=null)accentCustomSwatch.reveal(picker.colour());
                    markChanged();
                    if(rebuildQueued)column.removeCallbacks(delayedRebuild);rebuildQueued=true;column.postDelayed(delayedRebuild,260L);
                }).show();
    }

    private View palette() {
        android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(dp(16),dp(12),dp(16),dp(16));
        int[] colours=Accent.palette();final java.util.List<Dot> swatches=new java.util.ArrayList<>();
        for(int colour:colours){Dot dot=new Dot(this,colour,!Accent.isMaterial()&&colour==Accent.colour());swatches.add(dot);Motion.touch(dot);
            dot.setContentDescription(String.format(Locale.US,"#%06X",colour&0xFFFFFF));
            dot.setOnClickListener(v->{Accent.set(colour);for(Dot swatch:swatches)swatch.choose(swatch.fill.getColor()==colour,true);if(accentCustomSwatch!=null)accentCustomSwatch.choose(false,true);markChanged();rebuildAfterSwitchAnimation();});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(48),dp(48));size.rightMargin=dp(8);strip.addView(dot,size);
        }
        int custom=Accent.custom();
        Dot picker=new Dot(this,custom==0?Accent.colour():custom,custom!=0 && !Accent.isMaterial() && custom==Accent.colour());
        if(custom==0)picker.fill.setShader(new android.graphics.SweepGradient(dp(24),dp(24),new int[]{0xFFE53935,0xFFFFB300,0xFF43A047,0xFF00ACC1,0xFF1E88E5,0xFF8E24AA,0xFFE53935},null));
        accentCustomSwatch=picker;Motion.touch(picker);picker.setContentDescription(Text.ACCENT_PICKER);picker.setOnClickListener(v->chooseAccentColour());
        strip.addView(picker,new LinearLayout.LayoutParams(dp(48),dp(48)));scroll.addView(strip);return scroll;
    }

    /**
     * A Material icon, tinted to whatever this screen turned out to be.
     *
     * Decoded once per name and kept: the settings screen is rebuilt on every
     * tap, and decoding a dozen pngs each time would be felt.
     */
    private View icon(String name) {
        return icon(name, false, 40);
    }

    /** The shapes the first icons of the main list get; the rest are picked by name. */
    private static final java.util.Map<String, Integer> SHAPES = new java.util.HashMap<String, Integer>();
    static {
        SHAPES.put("palette", Expressive.COOKIE9);
        SHAPES.put("group", Expressive.CLOVER4);
        SHAPES.put("play_circle", Expressive.SUNNY);
        SHAPES.put("tune", Expressive.FLOWER6);
        SHAPES.put("info", Expressive.PENTAGON);
        SHAPES.put("extension", Expressive.COOKIE7);
        SHAPES.put("chat", Expressive.BURST12);
    }

    private static int shapeFor(String name) {
        Integer fixed = SHAPES.get(name);
        return fixed != null ? fixed : (name.hashCode() & 0x7FFFFFFF) % Expressive.KINDS;
    }

    /**
     * An icon inside an expressive shape.
     *
     * A toggle's shape is quiet when it is off and fills with the accent, turning
     * by a lobe, when it is on; anything else is a tonal shape that stays put.
     */
    private View icon(String name, boolean toggle, int sizeDp) {
        android.graphics.drawable.Drawable vector = name.equals("search")
                ? new Expressive.Magnifier(0xFFFFFFFF) : SettingsGlyph.make(name, 0xFFFFFFFF);
        if (vector == null) {
            Bitmap picture = pictureFor(name);
            if (picture != null) vector = new android.graphics.drawable.BitmapDrawable(getResources(), picture);
        }
        int accent = Accent.colour();
        int tonal = accentTonal();
        int ink = accentInk();
        Expressive.Badge badge = toggle
                ? new Expressive.Badge(this, shapeFor(name), vector, sizeDp,
                        Expressive.mix(skin.card, skin.text, 0.14f), accent, skin.muted(), onAccent())
                : new Expressive.Badge(this, shapeFor(name), vector, sizeDp, tonal, tonal, ink, ink);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        size.rightMargin = dp(Design.frosted() ? 16 : 12);
        badge.setLayoutParams(size);
        return badge;
    }

    private Bitmap pictureFor(String name) {
        Bitmap bitmap = ICONS.get(name);
        if (bitmap == null) {
            try {
                String data = Icons.PNG.get(name);
                if (data != null) {
                    byte[] png = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                    bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (bitmap != null) ICONS.put(name, bitmap);
                }
            } catch (Throwable ignored) {
            }
        }
        return bitmap;
    }

    private static final java.util.Map<String, Bitmap> ICONS =
            new java.util.HashMap<String, Bitmap>();

    /** What is set by a setting: one line and a switch, wherever it lives. */
    private interface Setting {
        void set(boolean on);
    }

    private View toggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture,title,on,setting,true);
    }

    private View liveToggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture,title,on,setting,false);
    }

    private View toggleRow(String picture, String title, boolean on, final Setting setting,
                           final boolean requiresRestart) {
        LinearLayout row = row();
        final View lead = icon(picture, true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(on, false);
        row.addView(lead);
        row.addView(label(title), grow());

        settingTargets.put(title,row);
        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setContentDescription(title);
        toggle.setChecked(on);
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            setting.set(checked);
            if (requiresRestart) markChanged();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.performClick();
        });
        return sized(row, 48);
    }

    /**
     * An identifier, and a tap to copy it.
     *
     * The long one does not fit on a phone, so what is shown is the ends of it
     * and what is copied is all of it.
     */
    /**
     * A switch with a word beside it saying not to trust it yet.
     *
     * The only thing in the mod that acts on its own and the only one that
     * sends anything, so it says so on the row rather than in a note nobody
     * reads.
     */
    private View betaRow(String picture, String title, boolean on, final Setting setting) {
        LinearLayout row = row();
        final View lead = icon(picture, true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(on, false);
        row.addView(lead);
        row.addView(label(title), grow());

        TextView beta = new TextView(this);
        beta.setText(Text.BETA);
        beta.setTextColor(onAccent());
        beta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        beta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        beta.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(Accent.colour());
        chip.setCornerRadius(dp(12));
        beta.setBackground(chip);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        place.leftMargin = dp(8);
        row.addView(beta, place);

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(on);
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            setting.set(checked);
            markChanged();
        });
        LinearLayout.LayoutParams togglePlace = new LinearLayout.LayoutParams(dp(52), dp(32));
        togglePlace.leftMargin = dp(10);
        row.addView(toggle, togglePlace);
        return sized(row, 52);
    }

    private View stickerHead() {
        LinearLayout row = row();
        row.addView(icon("star"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.STREAK_STICKER));
        int many = Streaks.offered().size();
        text.addView(detail(many == 0 ? Text.STREAK_NOTHING : String.valueOf(many)));
        row.addView(text, grow());

        row.addView(disclosure(streakOpen,"streakOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "streakOpen";
            streakOpen = !streakOpen;
            rebuildAfterReveal(streakOpen);
        });
        return sized(row, 60);
    }

    /** The stickers the app has drawn so far, as something to point at. */
    private View stickerChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(16), dp(14));

        FavouriteStickers.load(()->{if(!isFinishing())rebuild();});
        java.util.List<String> ids = Streaks.offered();
        if (ids.isEmpty()) {
            rows.addView(quiet(Text.STREAK_NOTHING));
            return rows;
        }

        String chosen = Streaks.chosen();
        LinearLayout line = null;
        for (int i = 0; i < ids.size() && i < 120; i++) {
            if (i % 5 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, dp(5), 0, dp(5));
                rows.addView(line);
            }
            final String id = ids.get(i);
            View one = stickerTile(id, id.equals(chosen));
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(52), dp(52), 1f);
            line.addView(one, size);
        }
        return rows;
    }

    private View stickerTile(final String id, boolean chosen) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (chosen) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(0x00000000);
            ring.setStroke(dp(2), Accent.colour());
            ring.setCornerRadius(dp(10));
            view.setBackground(ring);
        }
        final java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<ImageView>(view);
        Bitmap picture = Streaks.thumbnail(this, id,()->{
            ImageView tile=target.get();if(tile!=null && tile.isAttachedToWindow()){
                Bitmap loaded=Streaks.thumbnail(tile.getContext(),id);if(loaded!=null)tile.setImageBitmap(loaded);
            }
        });
        if (picture != null) view.setImageBitmap(picture);
        view.setOnClickListener(v -> {
            Streaks.choose(id);
            rebuild();
        });
        return view;
    }

    /** A row that does something at once, rather than setting anything. */
    private View actionRow(String picture, String title, String detail, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        boolean value = detail != null && detail.length() <= 14;

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        if (detail != null && !value) text.addView(detail(detail));
        row.addView(text, grow());

        if (value) {
            TextView shown = detail(detail);
            shown.setGravity(Gravity.CENTER_VERTICAL);
            shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);shown.setPadding(dp(8), 0, dp(6), 0);
            row.addView(shown);
        }
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));

        row.setOnClickListener(v -> action.run());
        return sized(row, detail == null || value ? 48 : 60);
    }

    /**
     * What this is and what it was built from, at the very bottom.
     *
     * Small and grey on purpose: nobody needs it until something has gone
     * wrong, and then it is the first thing anybody will ask for.
     */
    private View versions() {
        TextView view = new TextView(this);
        // the author and the licence live here as well: the licence asks that
        // a fork keep this line and the card that asks for support, rather
        // than moving the name into print nobody reads
        view.setText((Version.LITE ? "MargyT Lite " : "MargyT ") + Version.MOD
                + (Version.LITE ? " beta  ·  TikTok Lite " : "  ·  TikTok ") + Version.TIKTOK
                + "\n" + Text.MADE_BY);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(24), dp(20), dp(8));
        return view;
    }

    private View idRow(String title, final String value) {
        LinearLayout row = row();
        row.addView(icon("fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));

        TextView shown = detail(value == null ? Text.ACCOUNT_UNKNOWN : shorten(value));
        if (value != null) shown.setTextColor(skin.text);
        text.addView(shown);
        row.addView(text, grow());

        if (value != null) {
            row.addView(away());
            row.setOnClickListener(v -> copy(title, value));
        }
        return sized(row, 60);
    }

    private static String shorten(String value) {
        if (value.length() <= 26) return value;
        return value.substring(0, 14) + "…" + value.substring(value.length() - 8);
    }

    // ----------------------------------------------------------- the plugins

    private void showPluginAdd(){
        Panel panel=Panel.with(this,skin,Local.of("Добавить плагин"));
        panel.primary(Local.of("Создать из кнопок"),()->ScriptPlugins.edit(this,ScriptPlugins.SAMPLE,this::rebuild));
        panel.quiet(Local.of("Вставить JSON"),()->ScriptPlugins.edit(this,"",this::rebuild));
        panel.quiet(Local.of("Установить файл .mtp"),this::pickPlugin);
        panel.quiet(Local.of("Магазин"),()->startActivity(new Intent(this,StoreActivity.class)));
        panel.quiet(Local.of("Документация"),()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DOCS_PLUGINS))));
        panel.quiet(Text.CLOSE,null);panel.show();
    }

    private static final int PICK_PLUGIN = 0x4D50;  // "MP"

    /**
     * What plugins put into the settings: one block per switched-on plugin,
     * under its own name, in the same rows the mod's own settings use.
     */


    private View pluginSettingRow(final Plugins.Row item,
                                  final android.content.SharedPreferences store) {
        switch (item.kind) {
            case Plugins.Row.SECTION:
                return caption(item.title);
            case Plugins.Row.TOGGLE:
                return toggleRow("extension", item.title,
                        store.getBoolean(item.key, item.def != 0),
                        on -> {store.edit().putBoolean(item.key, on).apply();Plugins.settingsChanged(item.plugin,item.key);});
            case Plugins.Row.CHOICE: {
                final String[] options = item.options;
                if (options == null || options.length == 0) return null;
                int now = Math.max(0, Math.min(options.length - 1,
                        store.getInt(item.key, item.def)));
                return actionRow("tune", item.title, options[now], () ->
                        new ModDialog.Builder(this, skin)
                                .setTitle(item.title)
                                .setSingleChoiceItems(options,
                                        Math.max(0, Math.min(options.length - 1,
                                                store.getInt(item.key, item.def))),
                                        (dialog, which) -> {
                                            store.edit().putInt(item.key, which).apply();
                                            Plugins.settingsChanged(item.plugin,item.key);
                                            markChanged();
                                            dialog.dismiss();
                                            rebuild();
                                        })
                                .show());
            }
            case Plugins.Row.SLIDER: {
                int now = Math.max(item.min, Math.min(item.max,
                        store.getInt(item.key, item.def)));
                return slider(item.title, now - item.min, item.max - item.min, item.min, "",
                        value -> {
                            store.edit().putInt(item.key, value + item.min).apply();
                            Plugins.settingsChanged(item.plugin,item.key);
                            markChanged();
                        });
            }
            default:
                return actionRow("extension", item.title, item.detail, () -> {
                    try {
                        if (item.action != null) item.action.tapped();
                    } catch (Throwable error) {
                        Diary.note("plugin " + item.plugin + ": " + error);
                    }
                });
        }
    }

    private View installRow() {
        LinearLayout row = row();
        row.addView(icon("extension"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.PLUGIN_INSTALL));
        text.addView(detail(Text.PLUGIN_INSTALL_NOTE));
        row.addView(text, grow());

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Accent.colour());
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        plus.setPadding(dp(12), 0, 0, 0);
        row.addView(plus);

        row.setOnClickListener(v -> pickPlugin());
        return sized(row, 60);
    }

    /**
     * One installed plugin: what it says about itself, and a switch.
     *
     * Not `sized()` like the other rows -- a description is as tall as it is,
     * and a plugin whose author wrote two sentences should not have the second
     * one clipped.
     */
    private View pluginRow(final Plugins.Info info) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        Bitmap icon = info.icon();
        if (icon != null) {
            ImageView view = new ImageView(this);
            view.setImageBitmap(icon);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(view, size);
        } else {
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(new Dot(this, Accent.colour(), false), size);
        }

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(info.name + "  " + info.version));

        String by = info.author;
        if (!info.description.isEmpty()) by = by + "  ·  " + info.description;
        text.addView(detail(by));
        if (info.trouble != null) text.addView(detail(info.trouble));
        long stalled = Lag.cost(info.id);
        if (stalled >= 1000L) {
            TextView slow = detail(Local.of("Тормозит приложение: подвисаний за этот запуск") + " " + (stalled + 500L) / 1000L + " " + Local.of("с"));
            slow.setTextColor(0xFFE57373);
            text.addView(slow);
        }
        if(info.safety!=null&&!info.safety.isEmpty())text.addView(detail(Local.of("Удерживайте для действий")));
        row.addView(text, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Plugins.isEnabled(info.id));
        // a plugin that failed to start can still be switched off, and tried again
        toggle.setEnabled(true);
        toggle.setOnChanged(checked -> {
            activatePlugin(info,checked);
        });
        row.addView(toggle);

        row.setOnClickListener(v -> openPluginSettings(info));
        Motion.press(row);
        row.setOnLongClickListener(v -> {
            Panel actions=Panel.with(this,skin,info.name).compact();
            actions.primary(Local.of("Редактировать"),()->PluginEditorActivity.binary(this,info.id));
            actions.quiet(Local.of("Удалить с устройства"),()->askToRemove(info));
            if(AdminAccess.current())actions.quiet(Local.of("Управление магазином"),()->PluginAdmin.show(this));
            actions.quiet(Text.CLOSE,null);actions.show();
            return true;
        });
        return row;
    }

    private void activatePlugin(final Plugins.Info info,boolean enabled){
        // A plugin's hooks are in TikTok from the moment it starts, and stay there after it is told to
        // stop: on or off, it only takes hold with a restart. The plugins said so themselves, in a line
        // of text; this offers the same button every other such setting does.
        if(!enabled){Net.away("plugin stop",()->{Plugins.setEnabled(info.id,false);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});return;}
        // on is on: what the plugin reaches for was shown when it was installed, and is one tap away
        Net.away("plugin start",()->{Plugins.setEnabled(info.id,true);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});
    }

    private void openPluginSettings(final Plugins.Info info) {
        if(Plugins.isEnabled(info.id) && Plugins.openSettings(info.id,this))return;
        Panel panel=Panel.with(this,skin,info.name);
        if(info.safety!=null&&!info.safety.isEmpty())panel.text(info.safety);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.content.SharedPreferences store=getSharedPreferences("margyt_plugin_"+info.id,MODE_PRIVATE);
        int count=0;
        if(Plugins.isEnabled(info.id)) for(Plugins.Row item:Plugins.rows()) {
            if(!info.id.equals(item.plugin))continue;
            try { View control=pluginSettingRow(item,store); if(control!=null){body.addView(control);count++;} }
            catch(Throwable error){Diary.note("plugin settings "+info.id+": "+error);}
        }
        if(count==0){
            if(!Plugins.isEnabled(info.id))panel.text(Local.of("Включите плагин, чтобы открыть его настройки."));
            else if(Plugins.isRunning(info.id))panel.text(Local.of("Плагин не предоставил настройки. Версия ")+info.version);
            else{
                String why=Plugins.problem(info.id);
                panel.text(why!=null?Local.of("Плагин включён, но не запущен: ")+why:Local.of("Плагин включён, но ещё не запущен в этом сеансе."));
                panel.primary(Local.of("Запустить заново"),()->Net.away("plugin revive",()->{
                    final boolean ok=Plugins.revive(info.id);
                    runOnUiThread(()->{if(isFinishing())return;rebuild();if(ok)openPluginSettings(info);});
                }));
            }
        }else panel.view(body);
        panel.quiet(Local.of("Назад"),null).show();
    }

    private void pickPlugin() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // .mtp is nobody's registered type, so the picker is shown everything
            intent.setType("*/*");
            startActivityForResult(intent, PICK_PLUGIN);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        Uri source = data.getData();
        if (source == null) return;

        if (request == CREATE_BACKUP) {
            Screen.progress(Text.BACKUP_WRITING, 0);
            Net.away("backup", () -> {
                boolean done = Backup.write(this, source);
                if (done) Screen.progressDone(); else Screen.progressGone();
                runOnUiThread(() -> Screen.say(done ? Text.BACKUP_DONE : Text.BACKUP_FAILED));
            });
            return;
        }
        if (request == PICK_BACKUP) {
            reviewBackup(source);
            return;
        }

        if (request == PICK_FONT || request == PICK_EMOJI) {
            if (Fonts.take(this, source, request == PICK_EMOJI)) markChanged();
            else Toast.makeText(this, Text.FONT_FAILED, Toast.LENGTH_LONG).show();
            rebuild();
            return;
        }
        if (request == CustomIcon.PICK) {
            CustomIcon.offer(this, source);
            return;
        }
        if (request != PICK_PLUGIN) return;
        reviewPlugin(source);
    }
    private void reviewPlugin(final Uri source){
        Net.away("plugin inspection",()->{
            try {final PluginAudit.Report report=Plugins.inspect(this,source);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    Panel panel=Panel.with(this,skin,Text.PLUGIN_REVIEW).text(report.summary());
                    panel.primary(Text.PLUGIN_INSTALL,()->Net.away("plugin install",()->{
                        try {Plugins.install(this,source,report.digest);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});Screen.say(Text.PLUGIN_INSTALLED);}
                        catch(Throwable error){Screen.say(String.valueOf(error.getMessage()));}
                    }));
                    panel.quiet(Local.of("Назад"),null).show();
                });
            }catch(Throwable error){runOnUiThread(()->{if(!isFinishing())Panel.with(this,skin,Local.of("Плагин не принят")).text(String.valueOf(error.getMessage())).quiet(Local.of("Назад"),null).show();});}
        });
    }

    private void askToRemove(final Plugins.Info info) {
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.PLUGIN_REMOVE_ASK)
                    .setMessage(info.name + "  " + info.version)
                    .setNegativeButton(Text.CANCEL, null)
                    .setPositiveButton(Text.PLUGIN_REMOVE, (dialog, which) -> {
                        Plugins.uninstall(this, info.id);
                        markChanged();
                        rebuild();
                    })
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------- the links

    private View linkRow(String picture, String title, String handle, final String url) {
        LinearLayout row = row();
        row.addView(icon(picture));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        text.addView(detail(handle));
        row.addView(text, grow());
        row.addView(away());

        row.setOnClickListener(v -> open(url));
        return sized(row, 60);
    }

    /** The channel card: the whole card is a button to the mod's Telegram channel. */
    private View channelBanner() {
        ChannelBanner banner = new ChannelBanner(this);
        banner.setOnClickListener(v -> open(ChannelBanner.URL));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(edge(), dp(2), edge(), dp(10));
        box.addView(banner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View thanksHead() {
        LinearLayout row = row();
        row.addView(icon("favorite_border"));
        row.addView(label(Text.THANKS), grow());

        row.addView(disclosure(thanksOpen,"thanksOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "thanksOpen";
            thanksOpen = !thanksOpen;
            rebuildAfterReveal(thanksOpen);
        });
        return sized(row, 52);
    }

    /** Who made this, and the two ways to pay for it. */
    private View thanks() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(6), 0, dp(10));

        rows.addView(quiet(Text.THANKS_NOTE));
        rows.addView(owner());
        rows.addView(person("Claude Opus 5", Text.THANKS_CLAUDE, null));
        rows.addView(person("someone's mf", Text.THANKS_TTCUZ, TTCUZ));

        rows.addView(line());

        TextView heading = new TextView(this);
        heading.setText(Text.DONATE);
        heading.setTextColor(skin.text);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(16), dp(14), dp(16), dp(2));
        rows.addView(heading);

        rows.addView(quiet(Text.DONATE_NOTE));

        rows.addView(linkRow("star", Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY));
        rows.addView(linkRow("public", Text.BOOSTY, Text.BOOSTY_NOTE, BOOSTY));
        rows.addView(linkRow("public", Text.FUNPAY, Text.FUNPAY_NOTE, FUNPAY));
        rows.addView(linkRow("star", Text.STARS, Text.STARS_NOTE, STARS));
        return rows;
    }

    /** The one row with two places to go, so it asks which. */
    private View owner() {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label("@narezany"));
        text.addView(detail(Text.THANKS_OWNER));
        row.addView(text, grow());
        row.addView(away());

        row.setOnClickListener(v -> {
            try {
                new ModDialog.Builder(this, skin)
                        .setTitle("@narezany")
                        .setItems(new CharSequence[]{"Telegram", "TikTok"}, (dialog, which) ->
                                open(which == 0 ? OWNER_TELEGRAM : OWNER_TIKTOK))
                        .setNegativeButton(Text.CANCEL, null)
                        .show();
            } catch (Throwable error) {
                open(OWNER_TELEGRAM);
            }
        });
        return sized(row, 60);
    }

    private View person(String name, String what, final String url) {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(name));
        text.addView(detail(what));
        row.addView(text, grow());

        if (url != null) {
            row.addView(away());
            row.setOnClickListener(v -> open(url));
        }
        return sized(row, 60);
    }

    /** The mark on a row that leaves the app. */
    private View away() {
        Chevron arrow=new Chevron(this,skin.muted(),false,false);
        arrow.setRotation(-90f);
        return arrow;
    }

    /** A paragraph that is there to be read once and then ignored. */
    private TextView quiet(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        if (Design.frosted()) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        else M3Type.bodySmall(view);
        view.setPadding(dp(16), dp(2), dp(16), dp(8));
        return view;
    }

    /** A card number is read off the screen by a person, so it is grouped. */


    private void open(String url) {
        // a tiktok.com link belongs to the app this is running inside, so it is
        // offered there first: without this the browser opens, recognises the
        // link and hands it straight back, which is two screens for nothing
        url = inApp(url);
        if (url.contains("tiktok.com") && openWith(url, getPackageName())) return;
        if (openWith(url, null)) return;
        Toast.makeText(this, Text.NO_BROWSER, Toast.LENGTH_SHORT).show();
    }

    /**
     * The spelling of a link the app answers to.
     *
     * TikTok claims `www.tiktok.com` and a dozen others in its manifest, and
     * does not claim the bare domain -- so a link written without the `www`
     * resolves to nothing in the app, falls through to the browser, and the
     * browser hands it straight back. One prefix is the whole difference.
     */
    private static String inApp(String url) {
        if (url.startsWith("https://tiktok.com/")) {
            return "https://www.tiktok.com/" + url.substring("https://tiktok.com/".length());
        }
        return url;
    }

    private boolean openWith(String url, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (packageName != null) intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copy(String what, String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(what, text));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    /** Five taps in a row on the heading switch the developer tools on. */
    private void developerTap() {
        if (!DevMode.tapped()) return;
        if (DevMode.isOn()) {
            Toast.makeText(this, Text.DEV_ALREADY, Toast.LENGTH_SHORT).show();
            return;
        }
        DevMode.set(true);
        Diary.note("developer mode on");
        Toast.makeText(this, Text.DEV_ON, Toast.LENGTH_SHORT).show();
        rebuild();
    }

    /** Under the diary: the full report to copy, TikTok's flags, and the way back out. */
    private void addDeveloperRows(LinearLayout into) {
        into.addView(line());
        into.addView(actionRow("tune", Text.DEV_FLAGS, Text.DEV_FLAGS_NOTE, this::showFlags));
        into.addView(line());
        into.addView(actionRow("article", Text.DEV_FULL_REPORT, Text.DEV_FULL_NOTE,
                () -> copy("MargyT full report", Diagnostics.fullReport())));
        into.addView(line());
        into.addView(toggleRow("tune", Text.DEV_SWITCH, true, on -> {
            if (!on) {
                DevMode.set(false);
                rebuildAfterSwitchAnimation();
            }
        }));
    }

    /** Every shape as a picture of itself, four to a row. */
    private void chooseAvatarShape() {
        final Panel panel = Panel.with(this, skin, Text.AVATAR_SHAPE);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        final String now = AvatarShape.chosen();
        LinearLayout line = null;
        for (int i = 0; i < AvatarShape.ALL.length; i++) {
            final String shape = AvatarShape.ALL[i];
            if (i % 4 == 0) {
                line = new LinearLayout(this);
                grid.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout tile = Panel.tile(this, skin, AvatarShape.picture(shape, dp(36), 0xFFFFFFFF),
                    shape.equals(now) ? Accent.colour() : skin.text, AvatarShape.nameOf(shape),
                    shape.equals(now));
            tile.setOnClickListener(v -> {
                panel.close();
                if (shape.equals(now)) return;
                AvatarShape.choose(this, shape);
                // a picture already cut keeps its shape until it is cut again
                markChanged();
                rebuild();
            });
            Motion.press(tile);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            place.setMargins(dp(3), dp(3), dp(3), dp(3));
            line.addView(tile, place);
        }
        // the last row is not full: keep its tiles the width of the others
        for (int i = AvatarShape.ALL.length; i % 4 != 0; i++) {
            line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ((LinearLayout.LayoutParams) line.getChildAt(line.getChildCount() - 1).getLayoutParams())
                    .setMargins(dp(3), dp(3), dp(3), dp(3));
        }
        panel.view(grid);
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    // ------------------------------------------------------ TikTok's flags

    private static final int FLAGS_PAGE = 98;
    /** How many rows a search shows. There are twenty thousand flags and a row is a view. */
    private static final int FLAG_ROWS = 60;

    private String flagQuery = "";
    private java.util.List<Flags.Known> flagList;
    private boolean flagsLoading;

    private void showFlags() {
        settingsGroup = FLAGS_PAGE;
        searchQuery = "";
        flagQuery = "";
        page.scrollTo(0, 0);
        rebuild();
    }

    /**
     * Every flag TikTok declares, to look up and to answer differently.
     *
     * Nothing is listed until something is typed, bar what has already been
     * changed: the list is twenty thousand names long, and the one somebody
     * came for is found by a piece of its name or not at all.
     */
    private void flagsPage() {
        LinearLayout warning = card();
        warning.addView(quiet(Text.FLAGS_WARNING));
        column.addView(wrap(warning));

        final java.util.Map<String, Object> mine = Flags.overrides();
        if (FlagGuard.heldBack() && !mine.isEmpty()) {
            LinearLayout held = card();
            held.addView(quiet(Text.FLAGS_HELD));
            held.addView(actionRow("restore", Text.FLAGS_AGAIN, null, () -> {
                FlagGuard.again();
                Flags.forget();
                restartOwed();
                rebuild();
            }));
            column.addView(wrap(held));
        }

        if (flagList == null) {
            LinearLayout wait = card();
            wait.addView(quiet(Text.FLAGS_LOADING));
            column.addView(wrap(wait));
            if (flagsLoading) return;
            flagsLoading = true;
            // the list is built by a class initialiser the size of a small
            // book, and that is not something to wait for with a finger down
            new Thread(() -> {
                final java.util.List<Flags.Known> all = Flags.catalogue();
                runOnUiThread(() -> {
                    flagList = all;
                    flagsLoading = false;
                    if (settingsGroup == FLAGS_PAGE && !isFinishing()) rebuild();
                });
            }, "margyt-flags").start();
            return;
        }

        column.addView(flagSearch());

        java.util.List<Flags.Known> shown = new java.util.ArrayList<Flags.Known>();
        int found = 0;
        for (Flags.Known flag : flagList) {
            boolean wanted = flagQuery.length() == 0 ? mine.containsKey(flag.key)
                    : flag.key.toLowerCase(Locale.ROOT).contains(flagQuery);
            if (!wanted) continue;
            found++;
            if (shown.size() < FLAG_ROWS) shown.add(flag);
        }

        column.addView(section(flagQuery.length() == 0 ? Text.FLAGS_CHANGED
                : String.format(Locale.ROOT, Text.FLAGS_FOUND, found)));
        LinearLayout list = card();
        if (flagList.isEmpty()) list.addView(quiet(Text.FLAGS_NO_LIST));
        else if (shown.isEmpty()) list.addView(quiet(flagQuery.length() == 0
                ? String.format(Locale.ROOT, Text.FLAGS_NONE_CHANGED, flagList.size())
                : Text.NOTHING_FOUND));
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) list.addView(line());
            list.addView(flagRow(shown.get(i), mine.get(shown.get(i).key)));
        }
        if (found > shown.size()) {
            list.addView(quiet(String.format(Locale.ROOT, Text.FLAGS_MORE, shown.size())));
        }
        column.addView(wrap(list));

        if (!mine.isEmpty()) {
            column.addView(wrap(actionRow("restore", Text.FLAGS_RESET_ALL, null, () -> {
                Flags.clearOverrides();
                restartOwed();
                rebuild();
            })));
        }
    }

    private View flagSearch() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(flagQuery);
        input.setHint(Text.FLAGS_SEARCH);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final Runnable look = () -> {
            flagQuery = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            page.scrollTo(0, 0);
            rebuild();
        };
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            look.run();
            return true;
        });
        row.addView(input, grow());

        TextView go = label(flagQuery.length() == 0 ? Text.SEARCH : Text.SEARCH_CLEAR);
        go.setTextColor(accentInk());
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(Expressive.tile(this, accentTonal(), (Accent.colour() & 0xFFFFFF) | 0x33000000,
                dp(20), dp(20), dp(10)));
        go.setOnClickListener(v -> {
            if (flagQuery.length() > 0) input.setText("");
            look.run();
        });
        row.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(edge(), dp(4), edge(), dp(8));
        box.addView(sized(row, 56));
        return box;
    }

    /** A flag: its name, its type, what TikTok says and, if it differs, what is said here. */
    private View flagRow(final Flags.Known flag, final Object mine) {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(flag.key);
        name.setTypeface(Typeface.MONOSPACE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        words.addView(name);

        final Object own = flag.settable() ? Flags.own(flag) : null;
        final Object theirs = own != null ? own : flag.fallback;
        String says = flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs);
        if (mine != null) says += "  ·  " + Text.FLAGS_HERE + ": " + flagValue(mine);
        TextView about = detail(says);
        about.setPadding(0, dp(4), 0, 0);
        if (mine != null) about.setTextColor(accentInk());
        words.addView(about);
        row.addView(words, grow());
        row.setOnClickListener(v -> editFlag(flag, theirs, mine));
        return sized(row, 56);
    }

    private static String flagValue(Object value) {
        if (value == null) return "—";
        String text = value instanceof Object[] ? java.util.Arrays.toString((Object[]) value)
                : String.valueOf(value);
        if (value instanceof String) text = '"' + text + '"';
        return text.length() > 48 ? text.substring(0, 48) + "…" : text;
    }

    private void editFlag(final Flags.Known flag, Object theirs, Object mine) {
        if (!flag.settable()) {
            Toast.makeText(this, Text.FLAGS_FIXED, Toast.LENGTH_SHORT).show();
            return;
        }
        final Panel panel = Panel.with(this, skin, flag.key);
        if (flag.type.equals("BOOLEAN")) {
            panel.option(Text.FLAGS_AS_TIKTOK + " (" + flagValue(theirs) + ")", mine == null, false,
                    () -> answerFlag(flag, null));
            panel.option(Text.FLAGS_ON, Boolean.TRUE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.TRUE));
            panel.option(Text.FLAGS_OFF, Boolean.FALSE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.FALSE));
            panel.quiet(Text.CLOSE, null);
            panel.show();
            return;
        }
        panel.text(flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs));
        final EditText input = panel.field(String.valueOf(theirs));
        Object now = mine != null ? mine : theirs;
        if (now != null) input.setText(String.valueOf(now));
        if (!flag.type.equals("STRING")) {
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    | (flag.type.equals("FLOAT") || flag.type.equals("DOUBLE")
                            ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        }
        panel.primaryKeepOpen(Text.APPLY, () -> {
            Object value = Flags.parse(flag, input.getText().toString());
            if (value == null) {
                Screen.say(Text.FLAGS_BAD);
                return;
            }
            panel.close();
            answerFlag(flag, value);
        });
        if (mine != null) panel.quiet(Text.FLAGS_RESET, () -> answerFlag(flag, null));
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    private void answerFlag(Flags.Known flag, Object value) {
        Flags.override(flag, value);
        Diary.note("flag " + flag.key + " = " + (value == null ? "TikTok's own" : value));
        // nearly every flag is read once and remembered, so it takes a restart
        restartOwed();
        rebuild();
    }

    /** The whole report, monospaced, so columns line up and it reads like a log. */
    private View fullReportView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        TextView view = new TextView(this);
        view.setText(Diagnostics.fullReport());
        view.setTextColor(skin.muted());
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        view.setTextIsSelectable(true);
        box.addView(view);
        return box;
    }

    private View diaryHead() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // The title gets the whole row; the two actions sit on a line of their own,
        // otherwise the long words squeeze the title down to one letter per line.
        LinearLayout row = row();
        row.addView(icon("article"));
        TextView title = label(Text.DIARY_TITLE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, grow());
        row.addView(disclosure(diaryOpen,"diaryOpen"));
        row.setOnClickListener(v -> {
            disclosureKey = "diaryOpen";
            diaryOpen = !diaryOpen;
            rebuildAfterReveal(diaryOpen);
        });
        box.addView(sized(row, 52));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), 0, dp(16), dp(8));

        TextView copy = diaryAction(Text.COPY);
        copy.setOnClickListener(v -> copyDiary());
        actions.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = diaryAction(Text.CLEAR);
        clear.setOnClickListener(v -> {
            Diary.clear();
            Toast.makeText(this, Text.CLEARED, Toast.LENGTH_SHORT).show();
            rebuild();
        });
        LinearLayout.LayoutParams clearWhere = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearWhere.leftMargin = dp(8);
        actions.addView(clear, clearWhere);
        box.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private TextView diaryAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(Accent.colour());
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(true);
        action.setEllipsize(android.text.TextUtils.TruncateAt.END);
        action.setPadding(dp(12), dp(10), dp(12), dp(10));
        action.setMinHeight(dp(44));
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(22));
        pill.setColor((Accent.colour() & 0xFFFFFF) | 0x1F000000);
        action.setBackground(pill);
        Motion.press(action);
        return action;
    }

    private View diaryLines() {
        if (DevMode.isOn()) return fullReportView();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        for (String entry : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText(entry);
            view.setTextColor(skin.muted());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        return box;
    }

    /**
     * Start TikTok over, so everything is drawn again in the new colour.
     *
     * The launcher's own intent, then out: what comes back is a fresh process
     * with nothing of the old one's colours cached in it.
     */
    private void restartTikTok() {
        try {
            android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (intent == null) return;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            Runtime.getRuntime().exit(0);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDiary() {
        try {
            StringBuilder out = new StringBuilder("MargyT\n");
            if (DevMode.isOn()) out.append(Diagnostics.fullReport());
            else for (String line : Diary.lines()) out.append(line).append('\n');
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("MargyT", out.toString()));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the parts

    private View backArrow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        boolean m3 = !Design.frosted();
        if (m3) {
            bar.setPadding(dp(4), 0, dp(16), 0);
            bar.setMinimumHeight(dp(64));
        } else {
            bar.setPadding(dp(12), dp(8), dp(16), dp(4));
        }
        // a round tonal button that turns into a rounded square under the finger
        View button = Expressive.roundButton(this, SettingsGlyph.make("arrow_forward", skin.text),
                skin.card, (Accent.colour() & 0xFFFFFF) | 0x33000000, skin.text, m3 ? 40 : 48);
        ((ViewGroup) button).getChildAt(0).setScaleX(-1f);
        if (m3) {
            // the app bar's navigation icon: the words of where it goes are an iOS habit
            ((LinearLayout.LayoutParams) button.getLayoutParams()).setMargins(dp(4), dp(4), dp(4), dp(4));
            bar.addView(button);
            bar.setContentDescription(settingsGroup == 0 ? "TikTok" : Local.of("Настройки"));
            bar.setOnClickListener(v -> goBack());
            return bar;
        }
        bar.addView(button);
        TextView name = new TextView(this);
        name.setText(settingsGroup == 0 ? "TikTok" : Local.of("Настройки"));
        name.setTextColor(skin.muted());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(dp(12), 0, 0, 0);
        bar.addView(name);
        bar.setContentDescription(settingsGroup == 0 ? "TikTok" : Local.of("Настройки"));
        bar.setOnClickListener(v -> goBack());
        return bar;
    }

    private View title(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        if (Design.frosted()) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, Design.fluent()?(settingsGroup==0?32:28):settingsGroup == 0 ? 44 : 34);
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            view.setPadding(dp(28), dp(8), dp(24), dp(16));
        } else {
            if (settingsGroup == 0) M3Type.displaySmall(view);
            else M3Type.headlineMedium(view);
            view.setPadding(dp(32), dp(12), dp(24), dp(20));
        }
        // five taps in a row and a cat turns up; one by accident does nothing
        view.setOnClickListener(v -> {
            Cats.tapped(view);
            developerTap();
        });
        return view;
    }

    /**
     * Search is an explicit action rather than a TextWatcher rebuilding the
     * entire settings tree for every keystroke. The keyboard stays stable,
     * and the results are real shortcuts into matching setting groups.
     */
    private View searchBox() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(Design.frosted() ? 20 : 16), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        ImageView glass = new ImageView(this);
        glass.setImageDrawable(new Expressive.Magnifier(skin.muted()));
        LinearLayout.LayoutParams glassSize = new LinearLayout.LayoutParams(dp(24), dp(24));
        glassSize.rightMargin = dp(Design.frosted() ? 12 : 16);
        row.addView(glass, glassSize);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchQuery);
        input.setSelectAllOnFocus(false);
        input.setHint(Text.SEARCH_HINT);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        if (Design.frosted()) input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        else M3Type.line(input, 16, 0.5f);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(input.getText().toString());
                return true;
            }
            return false;
        });
        row.addView(input, grow());

        TextView go = label(searchQuery.length() == 0 ? Text.SEARCH : Text.SEARCH_CLEAR);
        go.setTextColor(accentInk());
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (!Design.frosted()) M3Type.line(go, 14, 0.1f);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(Expressive.tile(this, accentTonal(), (Accent.colour() & 0xFFFFFF) | 0x33000000,
                dp(20), dp(20), dp(Design.frosted() ? 10 : 8)));
        go.setOnClickListener(v -> {
            if (searchQuery.length() > 0) runSearch("");
            else runSearch(input.getText().toString());
        });
        row.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(edge(), dp(4), edge(), dp(8));
        box.addView(sized(row, 56));
        return box;
    }

    private void runSearch(String words) {
        searchQuery = words == null ? "" : words.trim().toLowerCase(Locale.ROOT);
        rebuild();
    }


    private void chooseSeekStep(){
        new ModDialog.Builder(this).setItems(new String[]{"5","10","15","30","60",Text.CUSTOM_SPEED},(dialog,index)->{
            if(index<5){AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,new int[]{5,10,15,30,60}[index]).apply();rebuild();return;}
            Panel panel=Panel.with(this,skin,Text.SEEK_STEP);android.widget.EditText input=panel.field("1–60");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(Integer.toString(PlayerTools.step()));
            panel.primaryKeepOpen(Text.APPLY,()->{try{int value=Integer.parseInt(input.getText().toString());if(value<1 || value>60){Screen.say("1–60");return;}AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,value).apply();panel.close();rebuild();}catch(Throwable ignored){Screen.say("1–60");}});panel.quiet(Text.CLOSE,null);panel.show();
        }).show();
    }
    private void showHiddenItems() {
        Panel panel=Panel.with(this,skin,Text.HIDDEN_ITEMS);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        String[] keys={Feed.KEY,Feed.KEY_LIVE,Feed.KEY_PHOTOS,Feed.KEY_STORIES,Feed.KEY_SUGGESTIONS};
        String[] labels={Text.HIDE_ADS,Text.HIDE_LIVE,Text.HIDE_PHOTOS,Text.HIDE_STORIES,Text.HIDE_SUGGESTIONS};
        int count=0;
        for(int i=0;i<keys.length;i++){
            final String key=keys[i];
            if(!(key.equals(Feed.KEY)?Feed.isEnabled():Feed.hides(key)))continue;
            count++;
            rows.addView(actionRow("visibility_off",labels[i],Text.HIDDEN_RESTORE,()->{
                if(key.equals(Feed.KEY))Feed.setEnabled(false);else Feed.setHides(key,false);
                panel.close();rebuild();Screen.say(Text.HIDDEN_NEXT_PAGE);
            }));
        }
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)if(!FeedRail.shown(i)){
            final int kind=i;count++;
            rows.addView(actionRow("visibility_off",railNames[i],Text.HIDDEN_RESTORE,()->{
                FeedRail.setShown(kind,true);panel.close();rebuild();
            }));
        }
        for(java.util.Map.Entry<Integer,String> card:FeedBlacklist.cards().entrySet()){
            final int type=card.getKey();count++;
            rows.addView(actionRow("group",card.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.removeCard(type);panel.close();rebuild();}));
        }
        for(java.util.Map.Entry<String,String> author:FeedBlacklist.items().entrySet()){
            final String uid=author.getKey();count++;
            rows.addView(actionRow("group",author.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.remove(uid);panel.close();rebuild();}));
        }
        if(count==0)rows.addView(caption(Text.HIDDEN_EMPTY));
        else {
            rows.addView(caption(Text.HIDDEN_NEXT_PAGE));
            panel.primary(Text.HIDDEN_RESTORE_ALL,()->{
                Feed.setEnabled(false);Feed.setHides(Feed.KEY_LIVE,false);Feed.setHides(Feed.KEY_PHOTOS,false);
                Feed.setHides(Feed.KEY_STORIES,false);Feed.setHides(Feed.KEY_SUGGESTIONS,false);FeedBlacklist.clear();
                for(int i=0;i<4;i++)FeedRail.setShown(i,true);
                rebuild();
            });
        }
        panel.view(rows);panel.quiet(Text.CLOSE,null);panel.show();
    }

    private void searchResults() {
        column.addView(section(Text.SEARCH_RESULTS));
        LinearLayout results = card();
        int found = 0;
        found+=searchControl(results,Text.FEED,Text.HIDE_ADS,Feed.isEnabled());
        found+=searchControl(results,Text.FEED,Text.HIDE_LIVE,Feed.hides(Feed.KEY_LIVE));
        found+=searchControl(results,Text.FEED,Text.HIDE_PHOTOS,Feed.hides(Feed.KEY_PHOTOS));
        found+=searchControl(results,Text.FEED,Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES));
        found+=searchControl(results,Text.FEED,Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS));
        found+=searchControl(results,Text.FEED,Text.SIMPLE_FEED,SimpleFeed.isOn());
        found+=searchHit(results,Text.NOTIFICATIONS,Text.NOTIFY_MESSAGES+" "+Text.NOTIFY_PROMOS+" "+Text.NOTIFY_OTHER+" "+Text.NOTIFY_NO_PROMPTS+" "+Text.NOTIFY_SETTINGS,()->{});
        found+=searchHit(results,Text.VIDEO,Text.PLAYER_SPEED,()->{});
        found+=searchHit(results,Text.DOWNLOADS,Text.DL_QUALITY+" "+Text.FRAME_QUALITY,()->{});
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)found+=searchControl(results,Text.RAIL,railNames[i],FeedRail.shown(i));
        if(matchesSearch(Text.HIDDEN_ITEMS)){
            results.addView(actionRow("visibility_off",Text.HIDDEN_ITEMS,Text.HIDDEN_ITEMS_NOTE,this::showHiddenItems));found++;
        }
        found+=searchControl(results,Text.DOWNLOADS,Text.NO_WATERMARK,Download.isEnabled());
        found+=searchControl(results,Text.VIDEO,Text.ALWAYS_DATE,Dates.isEnabled());
        found+=searchControl(results,BURN,Text.DIM,Dim.isConfigured());
        found+=searchControl(results,Text.COMMENTS,Text.COMMENT_COPY_ON,Comments.isCopyEnabled());
        found+=searchControl(results,Text.VIDEO,Text.OWN_BRIGHTNESS,Brightness.isEnabled());
        found+=searchControl(results,Text.BADGES,Text.BADGES_ON,Badges.isEnabled());
        found+=searchControl(results,Text.PROFILE_VIEW,Text.NEW_PROFILE,Flags.isOn(Flags.KEY_NEW_PROFILE));
        found+=searchControl(results,Text.MD3_TIKTOK,Text.NAV_CAPSULE,NavBar.isEnabled());
        found+=searchControl(results,Text.MD3_TIKTOK,Text.MD3_FEED,Md3.isOn(Md3.KEY_FEED));
        found+=searchControl(results,Text.MD3_TIKTOK,Text.MD3_PROFILE,Md3.isOn(Md3.KEY_PROFILE));
        found+=searchControl(results,Text.MD3_TIKTOK,Text.MD3_INBOX,Md3.isOn(Md3.KEY_INBOX));
        found+=searchControl(results,Text.MD3_TIKTOK,Text.DESIGN_COMMENTS,CommentStyle.enabled());
        found+=searchHit(results,Text.MD3_TIKTOK,Text.AVATAR_SHAPE,()->{});
        found+=searchHit(results,Text.FPS,Text.FPS,()->{});
        found+=searchHit(results,Text.TAGS,Text.TAGS+" "+Text.TAGS_ADD,()->{});
        found+=searchHit(results,Text.GRADIENT,Text.GRADIENT,()->{});
        found+=searchControl(results,Text.THEME,Text.AMOLED,Themes.isAmoled());
        if(found==0){
        found += searchHit(results, Text.REGION, Text.SEARCH_REGION,
                () -> { countriesOpen = true; });
        found += searchHit(results, Text.ACCENT, Text.SEARCH_ACCENT,
                () -> { accentOpen = true; });
        found += searchHit(results, Text.THEME, Text.SEARCH_THEME,
                () -> { themeColoursOpen = true; });
        found += searchHit(results, Text.FEED, Text.SEARCH_FEED, () -> { });
        found += searchHit(results, Text.VIDEO, Text.SEARCH_VIDEO, () -> { });
        found += searchHit(results, Text.COMMENTS, Text.SEARCH_COMMENTS, () -> { });
        found += searchHit(results, Text.DOWNLOADS, Text.SEARCH_DOWNLOADS, () -> { });
        found += searchHit(results, Text.BACKUP, Text.SEARCH_BACKUP, () -> { });
        found += searchHit(results, Text.FONT, Text.SEARCH_FONT,
                () -> { fontOpen = true; });
        found += searchHit(results, Text.BADGES, Text.SEARCH_BADGES, () -> { mineOpen = true; });
        }
        if (found == 0) results.addView(caption(Text.SEARCH_NONE));
        column.addView(wrap(results));
    }

    private int searchControl(LinearLayout into,String section,String title,boolean on){
        if(!matchesSearch(title))return 0;if(into.getChildCount()>0)into.addView(line());
        into.addView(actionRow("search",title,on?Local.of("Включено"):Local.of("Выключено"),()->{searchQuery="";settingsGroup=groupOf(section);Shelf to=shelfOfSection(section);settingsPage=to==null?null:to.id;rebuild();page.post(()->{View target=settingTargets.get(title);if(target==null)return;int offset=0;View cursor=target;while(cursor!=column){offset+=cursor.getTop();if(!(cursor.getParent() instanceof View))break;cursor=(View)cursor.getParent();}page.smoothScrollTo(0,Math.max(0,offset-dp(16)));Motion.change(target);});}));return 1;
    }
    private int searchHit(LinearLayout into, String title, String words, final Runnable open) {
        if (!matchesSearch(title + " " + words)) return 0;
        if (into.getChildCount() > 0) into.addView(line());
        into.addView(actionRow("search", title, currentValue(title), () -> {
            open.run();
            searchQuery = "";
            settingsGroup = groupOf(title);
            Shelf to = shelfOfSection(title);
            settingsPage = to == null ? null : to.id;
            page.scrollTo(0, 0);
            rebuild();
            final View target = sectionTargets.get(title);
            if (target != null) page.post(() -> page.smoothScrollTo(0, target.getTop()));
        }));
        return 1;
    }

    private String currentValue(String title){
        if(title.equals(Text.ACCENT))return String.format(Locale.US,"#%06X",Accent.colour()&0xFFFFFF);
        if(title.equals(Text.THEME))return Themes.isEnabled()?Local.of("Включена"):Local.of("Выключена");
        if(title.equals(Text.FEED))return Feed.isEnabled()?Local.of("Реклама скрыта"):Local.of("Реклама разрешена");
        if(title.equals(Text.DOWNLOADS))return Download.isEnabled()?Local.of("Без водяного знака"):Local.of("Обычное сохранение");
        if(title.equals(Text.PROFILE_STYLE))return Account.profileHandle()==null?Local.of("Откройте свой профиль"):"@"+Account.profileHandle();
        return Local.of("Открыть настройки");
    }
    private boolean matchesSearch(String words) {
        return words.toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private View section(String text) {
        renderDisclosureKey=null;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(accentInk());
        if (Design.frosted()) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            view.setLetterSpacing(0.04f);
            view.setPadding(dp(28), dp(24), dp(16), dp(8));
        } else {
            M3Type.titleSmall(view);
            view.setPadding(dp(32), dp(20), dp(16), dp(8));
        }
        sectionTargets.put(text, view);
        return view;
    }

    private String groupName(int group) {
        switch (group) {
            case 1: return Local.of("Оформление");
            case 2: return Local.of("Профиль");
            case 3: return Local.of("Лента и видео");
            case 4: return Local.of("Инструменты");
            case 5: return Local.of("О проекте");
            case 6: return Local.of("Плагины");
            case 7: return Local.of("Серии и уведомления");
            case 8: return Text.FUN;
            default: return "MargyT";
        }
    }

    private void showSectionHelp() {
        String[] notes = {"", Local.of("Акцент меняет цвет кнопок и вкладок. Тема и шрифты отдельно."),
                Local.of("Значки и градиент на ник. Сначала надо один раз подтвердить аккаунт."),
                Local.of("Удержание «Отправить» открывает скачивание и перемотку, удержание края видео ускоряет его."),
                Local.of("Скачивание, копии настроек, диагностика и журнал."),
                Local.of("Здесь находятся версия мода, обновления, журнал и участники."), Local.of("Магазин и настройки плагинов. Ставь только те, которым доверяешь."),
                Local.of("Автопродление серий и уведомления мода."),
                Text.FUN_HELP};
        Panel.with(this,skin,Local.of("Подсказки")).text(notes[Math.max(0,Math.min(notes.length-1,settingsGroup))])
                .quiet(Local.of("Обучение"),() -> Tutorial.show(this)).primary(Local.of("Понятно"),null).show();
    }

    private View groupNavigation() {
        LinearLayout list = card();
        String[] icons = {"palette", "group", "play_circle", "tune", "info", "extension", "chat", "flare"};
        String[] notes = {Local.of("Акцент, тема и шрифты"),
                Local.of("Значки, градиент и аккаунт"),
                Local.of("Просмотр, кнопки, защита экрана и регион"),
                Local.of("Скачивание, копии и диагностика"),
                Local.of("Версия, обновления и участники"), Local.of("Магазин и установленные"),
                Local.of("Серии и уведомления"), Text.FUN_NOTE};
        // Shelf order on screen: what people touch daily first, the project itself last.
        // Lite has the shelves that have something on them for it
        int[] shelf = Version.LITE ? new int[] {1, 3, 2, 4, 8, 5} : new int[] {1, 2, 3, 7, 4, 6, 8, 5};
        for (int n = 0; n < shelf.length; n++) {
            final int i = shelf[n];
            final int chosen = i;
            if (n > 0) list.addView(line());
            LinearLayout entry = row();
            entry.setPadding(dp(16),dp(14),dp(20),dp(14));
            entry.addView(icon(icons[i-1],false,Design.fluent()?32:52));
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
            TextView head = label(groupName(i)); head.setTextSize(Design.fluent()?16:19);
            head.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            words.addView(head);words.addView(detail(notes[i-1]));entry.setContentDescription(groupName(i)+". "+notes[i-1]);
            entry.addView(words,grow()); entry.addView(away());
            entry.setMinimumHeight(dp(80));Motion.touch(entry);
            entry.setOnClickListener(v -> {
                if(navigating)return;navigating=true;
                Motion.navigate(column,1f,()->{settingsGroup=chosen;settingsPage=null;page.scrollTo(0,0);rebuild();navigating=false;});
            });
            list.addView(entry);
        }
        return wrap(list);
    }

    /**
     * Which shelf a section lives on.
     * 1 look, 2 profile, 3 feed and video, 4 tools, 5 about, 6 plugins, 7 friends and chats,
     * 8 the things that are for nothing.
     */
    private int groupOf(String section) {
        Shelf one = shelfOfSection(section);
        return one == null ? 4 : one.group;
    }

    /** The pages of a shelf, each with what it is for. */
    private View shelfList(int group) {
        LinearLayout list = card();
        java.util.List<Shelf> pages = shelvesOf(group);
        for (int n = 0; n < pages.size(); n++) {
            final Shelf one = pages.get(n);
            if (n > 0) list.addView(line());
            LinearLayout entry = row();
            entry.setPadding(dp(16), dp(12), dp(20), dp(12));
            entry.addView(icon(one.icon, false, Design.fluent() ? 32 : 44));
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
            TextView head = label(one.title); head.setTextSize(Design.fluent() ? 15 : 17);
            head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            words.addView(head);
            if (one.note.length() > 0) words.addView(detail(one.note));
            entry.setContentDescription(one.title + ". " + one.note);
            entry.addView(words, grow()); entry.addView(away());
            entry.setMinimumHeight(dp(72)); Motion.touch(entry);
            entry.setOnClickListener(v -> {
                if (navigating) return; navigating = true;
                Motion.navigate(column, 1f, () -> { settingsPage = one.id; page.scrollTo(0, 0); rebuild(); navigating = false; });
            });
            list.addView(entry);
        }
        return wrap(list);
    }

    /** A line under a page's title saying what the page is for. */
    private View lead(String words) {
        TextView view = new TextView(this);
        view.setText(words);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setLineSpacing(0, 1.1f);
        view.setPadding(dp(24), 0, dp(24), dp(10));
        return view;
    }

    private void filterGroup() {
        Shelf open = shelf(settingsPage);
        boolean shown = false, offered = true;
        for (int i = 3; i < column.getChildCount(); i++) {
            View item = column.getChildAt(i);
            if (item instanceof TextView && sectionTargets.containsValue(item)) {
                String section = ((TextView) item).getText().toString();
                shown = false;
                if (open != null) for (String name : open.sections) shown |= name.equals(section);
                offered = !Version.LITE || inLite(section);
                // a page of one section already says its name in the title
                if (shown && open.sections.length == 1) { item.setVisibility(View.GONE); continue; }
            } else if (item.getTag(LEAD) != null) continue;
            item.setVisibility(shown && offered ? View.VISIBLE : View.GONE);
        }
    }
    private static final int LEAD = 0x7e0c0340;

    /**
     * The sections that do something in TikTok Lite. The rest hook classes
     * Lite does not have, and a switch that does nothing is worse than none.
     */
    private static boolean inLite(String section) {
        return section.equals(Text.REGION) || section.equals(Text.COUNTRY)
                || section.equals(Text.ACCENT) || section.equals(Text.THEME)
                || section.equals(Text.DOWNLOADS) || section.equals(Text.BADGES) || section.equals(Text.ACCOUNT)
                || section.equals(Text.FUN_SCREAMERS) || section.equals(Text.FUN_CAT)
                || section.equals(Text.DIAGNOSTICS) || section.equals(Text.DIARY)
                || section.equals(Text.THANKS) || section.equals(Text.LINKS) || section.equals(Text.CHANNEL);
    }

    private static final String BURN = Local.of("Защита экрана");

    private boolean navigating;
    private void goBack() {
        if(navigating)return;
        if (searchQuery.length() > 0) { searchQuery = ""; rebuild(); }
        else if (settingsPage != null && shelvesOf(settingsGroup).size() > 1) {
            navigating=true;Motion.navigate(column,-1f,()->{settingsPage=null;rebuild();navigating=false;});
        }
        else if (settingsGroup != 0) {
            // the flags are reached from the tools, so that is where back leads
            final int to = settingsGroup == FLAGS_PAGE ? 4 : 0;
            navigating=true;Motion.navigate(column,-1f,()->{settingsGroup=to;settingsPage=to==4?"diary":null;rebuild();navigating=false;});
        }
        else finish();
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onDestroy() {
        Badges.stopTelling(badgeUpdates);
        if (column != null) { column.removeCallbacks(delayedRebuild); column.animate().withEndAction(null).cancel(); }
        if (page != null) {
            if (scrollRestore != null) page.removeCallbacks(scrollRestore);
            if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive())
                page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
        }
        super.onDestroy();
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(16), dp(12), dp(16), dp(4));
        return view;
    }

    /**
     * A segmented list in Material 3 Expressive: every row is a tile of its own,
     * with large outer corners, small inner ones and a thin gap between them.
     */
    /** The three styles side by side, each a small settings card drawn in it. */
    private View designPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = row();
        head.addView(icon("palette"));
        head.addView(label(Text.DESIGN), grow());
        box.addView(sized(head, 52));
        LinearLayout three = new LinearLayout(this);
        three.setPadding(dp(12), 0, dp(12), dp(14));
        String[][] modes = {{"md3", "Material 3"}, {"glass", "Liquid Glass"}, {"fluent", "Fluent"}};
        for (final String[] mode : modes) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            boolean on = mode[0].equals(Design.mode());
            tile.addView(new SettingsArt.Style(this, skin, mode[0], on));
            TextView name = new TextView(this);
            name.setText(mode[1]);
            M3Type.line(name, 12, 0.4f);
            name.setTextColor(on ? skin.text : skin.muted());
            name.setTypeface(Typeface.create(on ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(6), 0, 0);
            tile.addView(name);
            tile.setPadding(dp(3), 0, dp(3), 0);
            tile.setOnClickListener(v -> { if (!mode[0].equals(Design.mode())) applyDesign(mode[0]); });
            Motion.press(tile);
            three.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(three);
        box.addView(quiet(Text.DESIGN_NOTE));
        return box;
    }

    private void chooseDesign() {
        Panel panel=Panel.with(this,skin,Text.DESIGN);
        panel.text(Text.DESIGN_NOTE).section(null);
        panel.option("Material 3","md3".equals(Design.mode()),true,()->applyDesign("md3"));
        panel.option("Liquid Glass",Design.glass(),true,()->applyDesign("glass"));
        panel.option("Fluent",Design.fluent(),true,()->applyDesign("fluent"));
        panel.show();
    }

    private void applyDesign(String mode) {
        Design.choose(this,mode);
        dressTheWindow();
        Design.page(this,root,skin.page);
        rebuild();
        root.post(()->Design.own(root));
    }

    private LinearLayout card() {
        return new Expressive.Group(this, skin.card, (Accent.colour() & 0xFFFFFF) | 0x33000000);
    }

    /** A lone row is a one-tile list, so it looks like every other group. */
    private View wrap(View card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(edge(), 0, edge(), dp(6));
        if (!(card instanceof Expressive.Group)) {
            LinearLayout single = card();
            single.addView(card);
            card = single;
        }
        box.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** The side margin of the page: 16 in Material 3, as a compact window has it. */
    private int edge() {
        return dp(Design.frosted() ? 12 : 16);
    }

    /** The accent as text on the page: lighter on dark, darker on light. */
    private int accentInk() {
        return skin.dark() ? Expressive.mix(Accent.colour(), 0xFFFFFFFF, 0.45f)
                : Expressive.mix(Accent.colour(), 0xFF000000, 0.35f);
    }

    /** The accent laid thin over the card: the tonal container of M3. */
    private int accentTonal() {
        return Expressive.mix(skin.card, Accent.colour(), 0.24f);
    }

    private View line() {
        View spacer=new View(this);spacer.setVisibility(View.GONE);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,0));return spacer;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (Design.frosted()) row.setPadding(dp(16), dp(4), dp(20), dp(4));
        else row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setMinimumHeight(dp(56));
        Motion.press(row);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        if (Design.frosted()) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        else M3Type.line(view, 16, 0.5f);
        view.setIncludeFontPadding(false);view.setPadding(0,0,dp(12),0);
        return view;
    }

    private TextView detail(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        if (Design.frosted()) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        } else {
            M3Type.bodyMedium(view);
            view.setPadding(0, dp(2), 0, 0);
        }
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View sized(View view, int height) {
        if (height > 1 && !Design.frosted()) height = height <= 56 ? 56 : height <= 72 ? 72 : 88;
        if (height > 1) view.setMinimumHeight(dp(Math.max(56, height)));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height <= 1 ? dp(height) : ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /**
     * The bar itself: one line of why, and the button that does it.
     *
     * Painted in the card colour with a hairline above, so it reads as resting
     * on the page rather than floating over it, and padded underneath by
     * whatever the navigation bar takes -- otherwise the button sits under the
     * gesture pill.
     */
    private View restartBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        styleRestartBar(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(20), dp(10), dp(12), dp(10));

        RestartSymbol symbol = new RestartSymbol(this, Accent.colour());
        LinearLayout.LayoutParams symbolSize = new LinearLayout.LayoutParams(dp(24),dp(24));
        symbolSize.rightMargin = dp(12);
        content.addView(symbol,symbolSize);

        TextView why = new TextView(this);
        why.setText(Text.RESTART_PENDING);
        why.setTextColor(skin.text);
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.addView(why, grow());

        TextView button = new TextView(this);
        button.setText(Text.RESTART);
        button.setTextColor(onAccent());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16),0,dp(16),0);button.setMinWidth(dp(132));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(24));
        button.setMinHeight(dp(48));
        button.setFocusable(true);
        button.setBackground(pill);
        button.setOnClickListener(v -> restartTikTok());
        LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(48));actionSize.leftMargin=dp(12);content.addView(button,actionSize);
        why.setIncludeFontPadding(false);why.setMaxLines(2);why.setGravity(Gravity.CENTER_VERTICAL);
        restartSymbol=symbol;restartWhy=why;restartAction=button;restartBackground=pill;
        Fonts.applyTree(content);Motion.press(button);
        bar.addView(content);
        return bar;
    }

    /**
     * What to write on the accent: the palette holds a mint and a near-white
     * as well as the pink, and white letters on either of those are unreadable.
     */
    private int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        int brightness = (red * 299 + green * 587 + blue * 114) / 1000;
        return brightness > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    /**
     * How much of the bottom belongs to the system, keyboard excluded.
     *
     * The system window inset counts the keyboard as well, so a rebuild while
     * one was up -- adding a hashtag, say -- padded the restart bar by the
     * height of the keyboard and turned it into half a screen.
     */
    private int navigationBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets == null) return 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
            }
            return insets.getStableInsetBottom();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int statusBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            int top = 0;
            if (insets != null) {
                top = android.os.Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()).top
                        : insets.getStableInsetTop();
            }
            if (top > 0) return top;
        } catch (Throwable ignored) {
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----------------------------------------------------- the small shapes

    private interface ColourChanged { void changed(int colour); }

    /** A full hue and saturation/value picker, with no colour-code entry. */
    private static final class AccentColourPicker extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = new float[3];
        private final int[] rainbow = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, 0xFFFF00FF, Color.RED};
        private ColourChanged changed;

        AccentColourPicker(Context context, int initial) {
            super(context);
            Color.colorToHSV(initial, hsv);
            setContentDescription(Text.ACCENT_PICKER);
        }

        void onColourChanged(ColourChanged listener) { changed = listener; }

        int colour() { return Color.HSVToColor(hsv); }
        void setColour(int colour){Color.colorToHSV(colour,hsv);invalidate();if(changed!=null)changed.changed(colour);}

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(10);
            float left = pad, right = getWidth() - pad, top = pad;
            float bottom = getHeight() - dp(42);
            int pureHue = Color.HSVToColor(new float[] {hsv[0], 1f, 1f});
            paint.setShader(new LinearGradient(left, top, right, top,
                    Color.WHITE, pureHue, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(new LinearGradient(left, top, left, bottom,
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(null);

            float markX = left + hsv[1] * (right - left);
            float markY = top + (1f - hsv[2]) * (bottom - top);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(markX, markY, dp(8), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(markX, markY, dp(6), paint);

            float hueTop = bottom + dp(12), hueBottom = getHeight() - dp(8);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, hueTop, right, hueTop, rainbow,
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, hueTop, right, hueBottom, dp(5), dp(5), paint);
            paint.setShader(null);
            float hueX = left + hsv[0] / 360f * (right - left);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(hueX, (hueTop + hueBottom) / 2f, dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN
                    && event.getAction() != android.view.MotionEvent.ACTION_MOVE
                    && event.getAction() != android.view.MotionEvent.ACTION_UP) return true;
            float pad = dp(10), left = pad, right = Math.max(left + 1, getWidth() - pad);
            float top = pad, bottom = getHeight() - dp(42);
            if (event.getY() <= bottom + dp(5)) {
                hsv[1] = Math.max(0f, Math.min(1f, (event.getX() - left) / (right - left)));
                hsv[2] = 1f - Math.max(0f, Math.min(1f,
                        (event.getY() - top) / Math.max(1f, bottom - top)));
            } else {
                hsv[0] = Math.max(0f, Math.min(1f,
                        (event.getX() - left) / (right - left))) * 360f;
            }
            invalidate();
            if (changed != null) changed.changed(colour());
            return true;
        }
    }

    /** A tick, drawn rather than typed: the glyph fonts have is never the one. */
    private static final class Check extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context context, int colour) {
            super(context);
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.round(22 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float unit = getWidth() / 22f;
            paint.setStrokeWidth(unit * 2.2f);
            float y = getHeight() / 2f;
            canvas.drawLine(unit * 4, y + unit, unit * 9, y + unit * 5.5f, paint);
            canvas.drawLine(unit * 9, y + unit * 5.5f, unit * 18, y - unit * 5f, paint);
        }
    }

    /** Circle at rest; a rounded square and outline indicate the selected colour. */
    private static final class Dot extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final android.graphics.Path outline = new android.graphics.Path();
        private boolean chosen;
        private float selection;
        private android.animation.ValueAnimator animation;

        Dot(Context context, int colour, boolean chosen) {
            super(context);
            this.chosen=chosen; selection=chosen?1f:0f;
            fill.setColor(colour); ring.setColor(colour); ring.setStyle(Paint.Style.STROKE);
            setSelected(chosen); setFocusable(true);
        }
        private final Paint customInk=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float customProgress;
        private android.animation.ValueAnimator colourAnimation;
        void reveal(int colour){
            choose(true,true);ring.setColor(colour);
            if(!Motion.enabled(getContext())){fill.setShader(null);fill.setColor(colour);invalidate();return;}
            if(colourAnimation!=null)colourAnimation.cancel();customInk.setColor(colour);customProgress=0;
            colourAnimation=android.animation.ValueAnimator.ofFloat(0,1);colourAnimation.setDuration(220L);
            colourAnimation.addUpdateListener(a->{customProgress=(Float)a.getAnimatedValue();invalidate();});
            colourAnimation.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){fill.setShader(null);fill.setColor(colour);customProgress=0;colourAnimation=null;invalidate();}});
            colourAnimation.start();
        }
        void choose(boolean selected, boolean animate) {
            chosen=selected; setSelected(selected);
            if (animation!=null) {animation.cancel();animation=null;}
            if (!animate || !Motion.enabled(getContext())) {selection=chosen?1f:0f;invalidate();return;}
            animation=android.animation.ValueAnimator.ofFloat(selection,chosen?1f:0f);
            animation.setDuration(180L);
            animation.addUpdateListener(a -> {selection=(Float)a.getAnimatedValue();invalidate();});
            animation.start();
        }
        @Override protected void onMeasure(int width, int height) {
            int size=Math.round(26*getResources().getDisplayMetrics().density);
            setMeasuredDimension(resolveSize(size,width),resolveSize(size,height));
        }
        @Override protected void onDraw(Canvas canvas) {
            float unit=getResources().getDisplayMetrics().density;
            float x=getWidth()/2f,y=getHeight()/2f;
            float radius=Math.max(1f,Math.min(x,y)-6*unit);
            // chosen: the circle grows a little, scallops into a cookie and turns one lobe
            Expressive.shape(outline,Expressive.COOKIE9,x,y,radius+3*unit*selection,selection,selection*360f/Expressive.lobes(Expressive.COOKIE9));
            canvas.drawPath(outline,fill);
            if(customProgress>0){customInk.setAlpha(Math.round(255*customProgress));canvas.drawPath(outline,customInk);}
            if (selection>0f) {
                int shade=fill.getColor();
                boolean light=(Color.red(shade)*299+Color.green(shade)*587+Color.blue(shade)*114)/1000>150;
                ring.setStyle(Paint.Style.STROKE);ring.setStrokeCap(Paint.Cap.ROUND);ring.setStrokeWidth(2.4f*unit);
                ring.setColor(light ? 0xFF111111 : 0xFFFFFFFF);ring.setAlpha(Math.round(255*Math.min(1f,selection)));
                canvas.drawLine(x-5*unit,y+0.5f*unit,x-1.5f*unit,y+4*unit,ring);
                canvas.drawLine(x-1.5f*unit,y+4*unit,x+5.5f*unit,y-3.5f*unit,ring);
            }
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (hasOnClickListeners()) {info.setCheckable(true);info.setChecked(chosen);info.setClassName("android.widget.RadioButton");}
        }
        @Override protected void onDetachedFromWindow() {
            if (animation!=null) {animation.cancel();animation=null;}
            if(colourAnimation!=null){colourAnimation.cancel();colourAnimation=null;}
            selection=chosen?1f:0f;super.onDetachedFromWindow();
        }
    }
}
