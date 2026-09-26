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

    /** Whether TikTok counts this post as a live stream. */
    public boolean isLive() {
        throw new UnsupportedOperationException("stub");
    }

    /** Type of aweme post (101 is LIVE). */
    public int getAwemeType() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * The caption the author wrote. The standard model getter, beside the
     * ones above; read only where a missing method cannot take the rest of
     * the filtering down with it (see Feed.captionOf).
     */
    public String getDesc() {
        throw new UnsupportedOperationException("stub");
    }

    public String getAid() {
        throw new UnsupportedOperationException("stub");
    }

    public com.ss.android.ugc.aweme.profile.model.User getAuthor() {
        throw new UnsupportedOperationException("stub");
    }

    public Video getVideo() {
        throw new UnsupportedOperationException("stub");
    }

    public String getPartN() {
        throw new UnsupportedOperationException("stub");
    }

    public int getCollectStatus() {
        throw new UnsupportedOperationException("stub");
    }

    public String getShareUrl() {
        throw new UnsupportedOperationException("stub");
    }

    public long getCreateTime() {
        throw new UnsupportedOperationException("stub");
    }
}
