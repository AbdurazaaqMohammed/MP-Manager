package io.github.abdurazaaqmohammed.player;

import android.net.Uri;

public class MediaItem {
    public final Uri uri;
    public final String path;
    public final String title;
    public final String artist;
    public final String album;
    public final long duration;
    public final boolean isVideo;

    public MediaItem(Uri uri, String path, String title, String artist, String album, long duration, boolean isVideo) {
        this.uri = uri;
        this.path = path;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.isVideo = isVideo;
    }
}
