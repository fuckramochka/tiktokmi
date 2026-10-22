package com.ss.android.ugc.profile.platform.base.data;

/**
 * One part of a profile page as TikTok's server sends it: what kind of part
 * it is, which one, and the parts inside it. The page is a tree of these.
 */
public class ProfileComponents {
    /** The kind of part ("relation_info_base_item"); for a container, its name again. */
    public String componentId;
    /** Which one it is ("relation_info_follower", "header_avatar"): what the page is drawn by. */
    public String componentName;
    public java.util.List<? extends ProfileComponents> components;
}
