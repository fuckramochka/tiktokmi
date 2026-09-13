package com.ss.android.ugc.aweme.feed.model;

/** A post in the feed: a video, its author, and what may be done with it. */
public class Aweme {

    /** Whether TikTok counts this post as an advertisement. */
    public boolean isAd() {
        throw new UnsupportedOperationException("stub");
    }

    /** The per-post ban on saving it. */
    public boolean isPreventDownload() {
        throw new UnsupportedOperationException("stub");
    }
}
