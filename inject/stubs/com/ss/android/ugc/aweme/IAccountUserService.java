package com.ss.android.ugc.aweme;

/** TikTok's account service, as much of it as the mod listens to. */
public interface IAccountUserService {

    /** The numeric id of whoever is signed in. */
    String getCurUserId();

    /** The long opaque id, the one a profile link is built from. */
    String getCurSecUserId();
}
