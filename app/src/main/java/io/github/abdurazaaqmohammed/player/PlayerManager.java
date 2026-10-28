package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.github.abdurazaaqmohammed.domain.files.FileSorting;

public class PlayerManager {

    public enum RepeatMode { OFF, ONE, ALL }
    public enum PlayState { IDLE, PREPARING, PLAYING, PAUSED, STOPPED, ENDED, ERROR }

    private static PlayerManager instance;

    private final Context appContext;
    private MediaPlayer mediaPlayer;
    private PlayState state = PlayState.IDLE;
    private List<MediaItem> queue = new ArrayList<>();
    private int currentIndex = -1;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean shuffle;
    private boolean keepScreenOn = true;
    private float playbackSpeed = 1f;
    private float volumeLeft = 1f;
    private float volumeRight = 1f;
    private boolean isMuted;
    private int skipDuration = 10000;
    private Surface videoSurface;
    private boolean isVideo;
    private int lastVideoWidth;
    private int lastVideoHeight;
    private int resumePosition;
    private boolean videoBackgroundPlay;
    private int uiVisibleCount;

    private int abRepeatA = -1;
    private int abRepeatB = -1;

    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PlaybackCallback> callbacks = new ArrayList<>();
    private final Random random = new Random();
    private List<Integer> shuffleOrder;

    public interface PlaybackCallback {
        void onStateChanged(PlayState newState);
        void onProgress(int position, int duration);
        void onMediaItemChanged(MediaItem item);
        void onError(String message);
        void onBufferUpdate(int percent);
    }

    /** One selectable subtitle track (embedded or external sidecar). */
    public static class SubtitleTrack {
        /** Index for {@link PlayerManager#selectSubtitleTrack(int)}. */
        public final int playerTrackIndex;
        public final String label;
        public final boolean external;

        public SubtitleTrack(int playerTrackIndex, String label, boolean external) {
            this.playerTrackIndex = playerTrackIndex;
            this.label = label;
            this.external = external;
        }
    }

    /** Receives subtitle cues (null/empty clears) and track-list changes. */
    public interface SubtitleListener {
        void onSubtitleCue(String text);
        void onSubtitleTracksChanged();
    }

    /** Sentinel track index used for in-app rendered external cues. */
    public static final int EXTERNAL_TRACK_INDEX = -100;

    private final List<SubtitleListener> subtitleListeners = new ArrayList<>();
    private final List<SubtitleTrack> subtitleTracks = new ArrayList<>();
    private boolean subtitlesEnabled = true;
    private boolean subtitleAutoLoad = true;
    private int subtitleDelayMs;
    private int selectedSubtitleTrack = -1;
    private String manualExternalSubtitle;
    private String manualExternalForPath;
    private String activeExternalLabel;
    private List<SubtitleUtil.Cue> externalCues = new ArrayList<>();
    private String externalCuesForPath;
    private String lastExternalCue;
    private int externalParseGen;

    public void registerSubtitleListener(SubtitleListener l) {
        if (l != null && !subtitleListeners.contains(l)) subtitleListeners.add(l);
    }

    public void unregisterSubtitleListener(SubtitleListener l) {
        subtitleListeners.remove(l);
    }

    private void notifySubtitleCue(String text) {
        for (SubtitleListener l : new ArrayList<>(subtitleListeners)) {
            try {
                l.onSubtitleCue(text);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifySubtitleTracksChanged() {
        for (SubtitleListener l : new ArrayList<>(subtitleListeners)) {
            try {
                l.onSubtitleTracksChanged();
            } catch (Exception ignored) {
            }
        }
    }

    public List<SubtitleTrack> getSubtitleTracks() {
        return new ArrayList<>(subtitleTracks);
    }

    public boolean isSubtitlesEnabled() {
        return subtitlesEnabled;
    }

    public void setSubtitlesEnabled(boolean enabled) {
        subtitlesEnabled = enabled;
        saveSettings();
        if (!enabled) {
            lastExternalCue = null;
            notifySubtitleCue(null);
        } else {
            updateExternalCue(true);
        }
    }

    public boolean isSubtitleAutoLoad() {
        return subtitleAutoLoad;
    }

    public void setSubtitleAutoLoad(boolean autoLoad) {
        subtitleAutoLoad = autoLoad;
        saveSettings();
        if (autoLoad) {
            MediaItem item = getCurrentItem();
            MediaPlayer mp = mediaPlayer;
            if (item != null && item.isVideo && mp != null
                    && (state == PlayState.PLAYING || state == PlayState.PAUSED
                        || state == PlayState.PREPARING)) {
                maybeAutoLoadExternal(item);
            }
        }
    }

    public int getSubtitleDelayMs() {
        return subtitleDelayMs;
    }

    public void setSubtitleDelayMs(int delayMs) {
        subtitleDelayMs = Math.max(-5000, Math.min(5000, delayMs));
        saveSettings();
        updateExternalCue(true);
    }

    public String getActiveSubtitleLabel() {
        for (SubtitleTrack t : subtitleTracks) {
            if (t.playerTrackIndex == selectedSubtitleTrack) return t.label;
        }
        return null;
    }

    /** Selects an embedded track, or {@link #EXTERNAL_TRACK_INDEX} for sidecar. */
    public void selectSubtitleTrack(int playerTrackIndex) {
        if (playerTrackIndex == EXTERNAL_TRACK_INDEX) {
            if (hasExternalCues()) {
                selectedSubtitleTrack = EXTERNAL_TRACK_INDEX;
                subtitlesEnabled = true;
                saveSettings();
                deselectEmbeddedTracks();
                updateExternalCue(true);
                notifySubtitleTracksChanged();
            }
            return;
        }
        selectedSubtitleTrack = playerTrackIndex;
        subtitlesEnabled = true;
        saveSettings();
        MediaPlayer mp = mediaPlayer;
        if (mp != null && playerTrackIndex >= 0) {
            try {
                mp.selectTrack(playerTrackIndex);
            } catch (Exception ignored) {
            }
        }
        if (playerTrackIndex >= 0) {
            lastExternalCue = null;
        }
        updateExternalCue(true);
    }

    /** Deselects framework subtitle tracks so external cues render alone. */
    private void deselectEmbeddedTracks() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        try {
            MediaPlayer.TrackInfo[] infos = mp.getTrackInfo();
            if (infos == null) return;
            for (int i = 0; i < infos.length; i++) {
                int type = infos[i].getTrackType();
                if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
                        || type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mp.deselectTrack(i);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Uses an external subtitle file for the current video. Cues are parsed
     * in-app and rendered against the playback clock (SRT/VTT/ASS/SSA/SUB).
     * Returns false when the file can't be used. The parsed cue count is
     * reported via {@link #getExternalCueCount()}.
     */
    public boolean setExternalSubtitle(String absPath) {
        MediaItem item = getCurrentItem();
        if (item == null || !item.isVideo || absPath == null) return false;
        File f = new File(absPath);
        if (!f.isFile() || !f.canRead() || !SubtitleUtil.isSupportedExternal(f.getName())) return false;
        manualExternalSubtitle = absPath;
        manualExternalForPath = item.path;
        activeExternalLabel = f.getName();
        subtitlesEnabled = true;
        saveSettings();
        loadExternalCuesAsync(absPath, true);
        return true;
    }

    /** Number of parsed external cues for the current file (0 when none). */
    public int getExternalCueCount() {
        synchronized (this) {
            return externalCues == null ? 0 : externalCues.size();
        }
    }

    public boolean hasExternalCues() {
        return getExternalCueCount() > 0;
    }

    /** Parses external cues off the calling thread, then activates them. */
    private void loadExternalCuesAsync(final String absPath, final boolean select) {
        final int gen = ++externalParseGen;
        new Thread(() -> {
            List<SubtitleUtil.Cue> cues = new ArrayList<>();
            try {
                cues = SubtitleUtil.parse(new File(absPath));
            } catch (Exception e) {
                cues = new ArrayList<>();
            }
            final List<SubtitleUtil.Cue> parsed = cues;
            handler.post(() -> {
                if (gen != externalParseGen) return;
                synchronized (PlayerManager.this) {
                    externalCues = new ArrayList<>(parsed);
                    externalCuesForPath = absPath;
                }
                lastExternalCue = null;
                if (select && !parsed.isEmpty()) {
                    selectedSubtitleTrack = EXTERNAL_TRACK_INDEX;
                    subtitlesEnabled = true;
                    saveSettings();
                    deselectEmbeddedTracks();
                }
                MediaPlayer mp = mediaPlayer;
                if (mp != null) refreshSubtitleTracks(mp, -1, null);
                else notifySubtitleTracksChanged();
                updateExternalCue(true);
            });
        }).start();
    }

    /** Rebuilds the track list; external sidecar is always listed first. */
    private void refreshSubtitleTracks(MediaPlayer mp, int externalIndex, String externalLabel) {
        List<SubtitleTrack> tracks = new ArrayList<>();
        String extLabel = externalLabel != null ? externalLabel : activeExternalLabel;
        if (hasExternalCues() && extLabel != null) {
            tracks.add(new SubtitleTrack(EXTERNAL_TRACK_INDEX, extLabel, true));
        }
        try {
            MediaPlayer.TrackInfo[] infos = mp.getTrackInfo();
            if (infos != null) {
                for (int i = 0; i < infos.length; i++) {
                    int type = infos[i].getTrackType();
                    if (type != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
                            && type != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                        continue;
                    }
                    boolean isExt = i == externalIndex;
                    String label = null;
                    if (label == null) {
                        String lang = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                lang = infos[i].getLanguage();
                            } catch (Exception ignored) {
                            }
                        }
                        label = (lang == null || lang.isEmpty() || "und".equals(lang))
                                ? "Track " + (tracks.size() + 1) : lang;
                    }
                    tracks.add(new SubtitleTrack(i, label, isExt));
                }
            }
        } catch (Exception ignored) {
        }
        subtitleTracks.clear();
        subtitleTracks.addAll(tracks);
        boolean selectionValid = selectedSubtitleTrack == EXTERNAL_TRACK_INDEX
                ? hasExternalCues() : selectedSubtitleTrack < 0;
        for (SubtitleTrack t : tracks) {
            if (t.playerTrackIndex == selectedSubtitleTrack) {
                selectionValid = true;
                break;
            }
        }
        if (!selectionValid) selectedSubtitleTrack = -1;
        notifySubtitleTracksChanged();
    }

    private void resetSubtitlesForNewItem() {
        subtitleTracks.clear();
        selectedSubtitleTrack = -1;
        activeExternalLabel = null;
        lastExternalCue = null;
        externalParseGen++;
        synchronized (this) {
            externalCues = new ArrayList<>();
            externalCuesForPath = null;
        }
        handler.post(() -> notifySubtitleCue(null));
        notifySubtitleTracksChanged();
    }

    /** Renders the external cue for the current clock (no-op when inactive). */
    private void updateExternalCue(boolean force) {
        if (!subtitlesEnabled || selectedSubtitleTrack != EXTERNAL_TRACK_INDEX
                || !hasExternalCues()) {
            if (force && lastExternalCue != null) {
                lastExternalCue = null;
                handler.post(() -> notifySubtitleCue(null));
            } else if (force && selectedSubtitleTrack != EXTERNAL_TRACK_INDEX
                    && subtitlesEnabled) {
                // Embedded path drives cues via the timed-text listener.
            }
            return;
        }
        List<SubtitleUtil.Cue> cues;
        synchronized (this) {
            cues = new ArrayList<>(externalCues);
        }
        int pos = getCurrentPosition();
        String cue = SubtitleUtil.cueAt(cues, pos, subtitleDelayMs);
        if (cue == null ? lastExternalCue != null
                : !cue.equals(lastExternalCue)) {
            lastExternalCue = cue;
            final String out = cue;
            handler.post(() -> notifySubtitleCue(out));
        }
    }

    /**
     * Loads the manual pick (for this video) or the best same-name sidecar.
     * Called once the player is prepared so the track list is complete.
     */
    private void maybeAutoLoadExternal(MediaItem item) {
        if (item == null || !item.isVideo || item.path == null) return;
        String manual = null;
        if (manualExternalSubtitle != null && item.path.equals(manualExternalForPath)) {
            File mf = new File(manualExternalSubtitle);
            if (mf.isFile() && mf.canRead()
                    && SubtitleUtil.isSupportedExternal(mf.getName())) {
                manual = manualExternalSubtitle;
            }
        }
        if (manual != null) {
            synchronized (this) {
                if (!manual.equals(externalCuesForPath) || !hasExternalCues()) {
                    activeExternalLabel = new File(manual).getName();
                    loadExternalCuesAsync(manual, true);
                }
            }
            return;
        }
        if (!subtitleAutoLoad) return;
        synchronized (this) {
            if (hasExternalCues() && externalCuesForPath != null) return;
        }
        // Scan the video directory off the main thread.
        final String videoPath = item.path;
        final int gen = externalParseGen;
        new Thread(() -> {
            List<File> sidecars = SubtitleUtil.findSidecars(videoPath);
            handler.post(() -> {
                if (gen != externalParseGen) return;
                MediaItem cur = getCurrentItem();
                if (cur == null || !videoPath.equals(cur.path)) return;
                if (!subtitleAutoLoad || hasExternalCues()) return;
                if (manualExternalSubtitle != null
                        && videoPath.equals(manualExternalForPath)) return;
                if (sidecars.isEmpty()) return;
                File best = sidecars.get(0);
                manualExternalSubtitle = null;
                manualExternalForPath = null;
                activeExternalLabel = best.getName();
                loadExternalCuesAsync(best.getAbsolutePath(), true);
            });
        }).start();
    }

    public static synchronized PlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlayerManager(context.getApplicationContext());
        }
        return instance;
    }

    private PlayerManager(Context context) {
        this.appContext = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        loadSettings();
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public PlayState getState() { return state; }

    public List<MediaItem> getQueue() { return queue; }

    public int getCurrentIndex() { return currentIndex; }

    public MediaItem getCurrentItem() {
        if (currentIndex >= 0 && currentIndex < queue.size()) return queue.get(currentIndex);
        return null;
    }

    public RepeatMode getRepeatMode() { return repeatMode; }
    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        saveSettings();
    }

    public boolean isShuffle() { return shuffle; }

    public float getPlaybackSpeed() { return playbackSpeed; }
    public boolean isKeepScreenOn() { return keepScreenOn; }
    public void setKeepScreenOn(boolean keep) { this.keepScreenOn = keep; saveSettings(); }
    public boolean isMuted() { return isMuted; }
    public int getSkipDuration() { return skipDuration; }
    public void setSkipDuration(int ms) { this.skipDuration = ms; saveSettings(); }

    public boolean hasABRepeat() { return abRepeatA >= 0 && abRepeatB >= 0; }
    public int getABRepeatA() { return abRepeatA; }
    public int getABRepeatB() { return abRepeatB; }

    public boolean isVideoBackgroundPlay() { return videoBackgroundPlay; }
    public void setVideoBackgroundPlay(boolean enable) { this.videoBackgroundPlay = enable; saveSettings(); }

    public void incrementUIVisible() { uiVisibleCount++; }
    public void decrementUIVisible() { if (uiVisibleCount > 0) uiVisibleCount--; }
    public boolean isUIVisible() { return uiVisibleCount > 0; }

    public void registerCallback(PlaybackCallback cb) { if (!callbacks.contains(cb)) callbacks.add(cb); }
    public void unregisterCallback(PlaybackCallback cb) { callbacks.remove(cb); }

    private void notifyState(PlayState s) {
        state = s;
        for (PlaybackCallback cb : callbacks) cb.onStateChanged(s);
    }

    private void notifyProgress(int pos, int dur) {
        for (PlaybackCallback cb : callbacks) cb.onProgress(pos, dur);
    }

    private void notifyMediaItem(MediaItem item) {
        for (PlaybackCallback cb : callbacks) cb.onMediaItemChanged(item);
    }

    private void notifyError(String msg) {
        for (PlaybackCallback cb : callbacks) cb.onError(msg);
    }

    private void notifyBuffer(int pct) {
        for (PlaybackCallback cb : callbacks) cb.onBufferUpdate(pct);
    }

    public void play(List<MediaItem> items, int startIndex) {
        if (items == null || items.isEmpty()) return;
        queue = new ArrayList<>(items);
        currentIndex = startIndex;
        if (shuffle) buildShuffleOrder();
        playCurrent();
    }

    public void play(MediaItem item) {
        List<MediaItem> single = new ArrayList<>();
        single.add(item);
        play(single, 0);
    }

    public void addAndPlay(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            playCurrent();
        }
    }

    public void addToQueue(MediaItem item) {
        queue.add(item);
        if (currentIndex < 0) { currentIndex = 0; playCurrent(); }
    }

    public void addToQueue(List<MediaItem> items) {
        queue.addAll(items);
        if (currentIndex < 0 && !queue.isEmpty()) { currentIndex = 0; playCurrent(); }
    }

    public void removeFromQueue(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            if (index < currentIndex) currentIndex--;
            else if (index == currentIndex) {
                if (queue.isEmpty()) { stop(); currentIndex = -1; }
                else playCurrent();
            }
        }
    }

    public void clearQueue() {
        stop();
        queue.clear();
        currentIndex = -1;
        shuffleOrder = null;
    }

    public synchronized void setQueue(List<MediaItem> newQueue, int newIndex) {
        if (newQueue == null || newQueue.isEmpty()) return;
        this.queue = new ArrayList<>(newQueue);
        this.currentIndex = Math.max(0, Math.min(newIndex, queue.size() - 1));
        if (shuffle) buildShuffleOrder();
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == RepeatMode.ONE) { seekTo(0); return; }
        int next = getNextIndex();
        if (next >= 0) { currentIndex = next; playCurrent(); }
        else { stop(); notifyState(PlayState.ENDED); }
    }

    public void previous() {
        if (queue.isEmpty()) return;
        int pos = getCurrentPosition();
        if (pos > 3000) { seekTo(0); return; }
        int prev = getPrevIndex();
        if (prev >= 0) { currentIndex = prev; playCurrent(); }
    }

    public void setShuffle(boolean s) {
        shuffle = s;
        if (s) buildShuffleOrder();
        else shuffleOrder = null;
        saveSettings();
    }

    private void buildShuffleOrder() {
        shuffleOrder = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder, random);
    }

    private static boolean isMediaName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac") || n.endsWith(".ogg")
                || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wma") || n.endsWith(".opus")
                || n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov")
                || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".ts") || n.endsWith(".flv")
                || n.endsWith(".wmv") || n.endsWith(".m4v");
    }

    private List<File> sortedMediaSiblings(File currentFile) {
        if (currentFile == null) return null;
        File dir = currentFile.getParentFile();
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        List<File> out = new ArrayList<>();
        for (File f : files) {
            try {
                if (f != null && f.isFile() && isMediaName(f.getName())) out.add(f);
            } catch (Exception ignored) {}
        }
        try {
            io.github.abdurazaaqmohammed.data.prefs.ListPrefs lp = new io.github.abdurazaaqmohammed.data.prefs.ListPrefs(appContext);
            String dirPath = dir.getAbsolutePath();
            File[] arr = out.toArray(new File[0]);
            FileSorting.sortFiles(arr, lp.sortBy(dirPath), lp.sortReverse(dirPath));
            out = new ArrayList<>(java.util.Arrays.asList(arr));
        } catch (Exception ignored) {
            try {
                Collections.sort(out, (a, b) -> FileSorting.compareNaturalIgnoreCase(a.getName(), b.getName()));
            } catch (Exception ignoredInner) {}
        }
        return out;
    }

    private boolean expandQueueFromFolder() {
        if (currentIndex < 0 || currentIndex >= queue.size()) return false;
        MediaItem current = queue.get(currentIndex);
        if (current == null || current.path == null) return false;
        File currentFile;
        try { currentFile = new File(current.path); } catch (Exception ignored) { return false; }
        List<File> siblings = sortedMediaSiblings(currentFile);
        if (siblings == null || siblings.isEmpty()) return false;
        String curName = currentFile.getName();
        String curAbs = currentFile.getAbsolutePath();
        int posInSiblings = -1;
        for (int i = 0; i < siblings.size(); i++) {
            File s = siblings.get(i);
            try {
                if (s.getAbsolutePath().equals(curAbs) || s.getName().equalsIgnoreCase(curName)) { posInSiblings = i; break; }
            } catch (Exception ignored) {}
        }
        if (posInSiblings < 0) return false;
        boolean changed = false;
        if (queue.size() == 1) {
            List<MediaItem> full = new ArrayList<>();
            for (File s : siblings) full.add(buildMediaItemFast(s.getAbsolutePath()));
            queue = full;
            currentIndex = posInSiblings;
            if (shuffle) buildShuffleOrder();
            changed = true;
        } else if (currentIndex + 1 >= queue.size() && posInSiblings + 1 < siblings.size()) {
            for (int i = posInSiblings + 1; i < siblings.size(); i++) {
                boolean already = false;
                String ap = siblings.get(i).getAbsolutePath();
                for (MediaItem m : queue) {
                    if (m != null && ap.equals(m.path)) { already = true; break; }
                }
                if (!already) { queue.add(buildMediaItemFast(ap)); changed = true; }
            }
        }
        return changed;
    }

    private int getNextIndex() {
        if (shuffle && shuffleOrder != null) {
            int idx = shuffleOrder.indexOf(currentIndex);
            if (idx >= 0 && idx + 1 < shuffleOrder.size()) return shuffleOrder.get(idx + 1);
            if (repeatMode == RepeatMode.ALL) {
                expandQueueFromFolder();
                buildShuffleOrder();
                if (!shuffleOrder.isEmpty()) return shuffleOrder.get(0);
                return 0;
            }
            expandQueueFromFolder();
            int idx2 = shuffleOrder.indexOf(currentIndex);
            if (idx2 >= 0 && idx2 + 1 < shuffleOrder.size()) return shuffleOrder.get(idx2 + 1);
            return -1;
        }
        if (currentIndex + 1 < queue.size()) return currentIndex + 1;
        if (repeatMode == RepeatMode.ALL && !queue.isEmpty()) {
            expandQueueFromFolder();
            return 0;
        }
        expandQueueFromFolder();
        if (currentIndex + 1 < queue.size()) return currentIndex + 1;
        return -1;
    }

    private int getPrevIndex() {
        if (shuffle && shuffleOrder != null) {
            int idx = shuffleOrder.indexOf(currentIndex);
            if (idx > 0) return shuffleOrder.get(idx - 1);
            return currentIndex;
        }
        if (currentIndex > 0) return currentIndex - 1;
        if (currentIndex == 0 && currentIndex < queue.size()) {
            MediaItem current = queue.get(currentIndex);
            if (current != null && current.path != null) {
                try {
                    File currentFile = new File(current.path);
                    List<File> siblings = sortedMediaSiblings(currentFile);
                    if (siblings != null && !siblings.isEmpty()) {
                        String curAbs = currentFile.getAbsolutePath();
                        int pos = -1;
                        for (int i = 0; i < siblings.size(); i++) {
                            if (siblings.get(i).getAbsolutePath().equals(curAbs)) { pos = i; break; }
                        }
                        if (pos > 0) {
                            File prev = siblings.get(pos - 1);
                            queue.add(0, buildMediaItemFast(prev.getAbsolutePath()));
                            return 0;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return currentIndex;
    }

    public void playPause() {
        if (state == PlayState.PLAYING) pause();
        else if (state == PlayState.PAUSED || state == PlayState.IDLE || state == PlayState.ENDED) play();
    }

    public void play() {
        if (mediaPlayer == null && currentIndex >= 0) { playCurrent(); return; }
        if (mediaPlayer != null && (state == PlayState.PAUSED || state == PlayState.IDLE || state == PlayState.ENDED)) {
            requestAudioFocus();
            mediaPlayer.start();
            notifyState(PlayState.PLAYING);
            startProgressUpdater();
            saveResumePosition();
        }
    }

    public void pause() {
        if (mediaPlayer != null && state == PlayState.PLAYING) {
            mediaPlayer.pause();
            notifyState(PlayState.PAUSED);
            stopProgressUpdater();
            saveResumePosition();
        }
    }

    public void stop() {
        stopProgressUpdater();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        abandonAudioFocus();
        isVideo = false;
        notifyState(PlayState.STOPPED);
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null) {
            msec = Math.max(0, msec);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer.seekTo(msec, MediaPlayer.SEEK_CLOSEST);
                } else {
                    mediaPlayer.seekTo(msec);
                }
            } catch (Exception ignored) {}
        }
    }

    public void skipForward() { seekTo(getCurrentPosition() + skipDuration); }
    public void skipBackward() { seekTo(getCurrentPosition() - skipDuration); }

    public int getCurrentPosition() {
        if (mediaPlayer != null) try { return mediaPlayer.getCurrentPosition(); } catch (Exception ignored) {}
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) try { return mediaPlayer.getDuration(); } catch (Exception ignored) {}
        return 0;
    }

    public void setVolume(float left, float right) {
        volumeLeft = left;
        volumeRight = right;
        isMuted = false;
        if (mediaPlayer != null) mediaPlayer.setVolume(left, right);
    }

    public void setMuted(boolean mute) {
        isMuted = mute;
        if (mediaPlayer != null) mediaPlayer.setVolume(mute ? 0 : volumeLeft, mute ? 0 : volumeRight);
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception ignored) {}
        }
    }

    public void setSurface(Surface surface) {
        this.videoSurface = surface;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setSurface(surface);
            } catch (Exception ignored) {}
        }
    }

    public boolean isPlayingVideo() { return isVideo; }

    public void setABRepeat(int a, int b) {
        abRepeatA = Math.min(a, b);
        abRepeatB = Math.max(a, b);
    }

    public void clearABRepeat() { abRepeatA = -1; abRepeatB = -1; }

    private void playCurrent() {
        if (currentIndex < 0 || currentIndex >= queue.size()) return;
        MediaItem item = queue.get(currentIndex);
        isVideo = item.isVideo;

        stopProgressUpdater();
        abandonAudioFocus();

        // Create new player BEFORE releasing the old one
        MediaPlayer newPlayer = new MediaPlayer();
        MediaPlayer oldPlayer = mediaPlayer;
        mediaPlayer = newPlayer;

        if (oldPlayer != null) {
            try { oldPlayer.stop(); oldPlayer.reset(); oldPlayer.release(); } catch (Exception ignored) {}
        }
        notifyState(PlayState.STOPPED);

        newPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(item.isVideo ? AudioAttributes.CONTENT_TYPE_MOVIE : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        if (videoSurface != null && isVideo) newPlayer.setSurface(videoSurface);

        resetSubtitlesForNewItem();
        newPlayer.setOnTimedTextListener((mp, text) -> {
            if (mp != mediaPlayer) return;
            // External cues are rendered in-app; ignore framework text then.
            if (selectedSubtitleTrack == EXTERNAL_TRACK_INDEX && hasExternalCues()) return;
            final String cue = (text == null || text.getText() == null) ? null
                    : SubtitleUtil.cleanCue(text.getText());
            handler.post(() -> {
                if (selectedSubtitleTrack == EXTERNAL_TRACK_INDEX && hasExternalCues()) return;
                if (subtitlesEnabled) notifySubtitleCue(cue == null || cue.isEmpty() ? null : cue);
            });
        });

        newPlayer.setOnPreparedListener(mp -> {
            if (mp != mediaPlayer) return;
            if (resumePosition > 0 && resumePosition < mp.getDuration()) mp.seekTo(resumePosition);
            resumePosition = 0;
            if (isVideo) {
                refreshSubtitleTracks(mp, -1, null);
                maybeAutoLoadExternal(item);
            }
            requestAudioFocus();
            mp.setVolume(isMuted ? 0 : volumeLeft, isMuted ? 0 : volumeRight);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1f) {
                try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(playbackSpeed)); } catch (Exception ignored) {}
            }
            mp.start();
            notifyState(PlayState.PLAYING);
            startProgressUpdater();
            try { appContext.startService(new Intent(appContext, MusicService.class)); } catch (Exception ignored) {}
        });
        newPlayer.setOnCompletionListener(mp -> {
            if (mp != mediaPlayer) return;
            stopProgressUpdater();
            if (abRepeatA >= 0 && abRepeatB >= 0) {
                seekTo(abRepeatA);
                return;
            }
            if (repeatMode == RepeatMode.ONE) { seekTo(0); return; }
            int next = getNextIndex();
            if (next >= 0) { currentIndex = next; playCurrent(); }
            else {
                saveResumePosition();
                notifyState(PlayState.ENDED);
            }
        });
        newPlayer.setOnErrorListener((mp, what, extra) -> {
            if (mp != mediaPlayer) return true;
            //notifyError("Media error: " + what + " / " + extra);
            notifyState(PlayState.ERROR);
            return true;
        });
        newPlayer.setOnInfoListener((mp, what, extra) -> {
            if (mp != mediaPlayer) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) notifyBuffer(0);
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) notifyBuffer(100);
            return false;
        });
        newPlayer.setOnVideoSizeChangedListener((mp, w, h) -> {
            if (mp != mediaPlayer) return;
            lastVideoWidth = w;
            lastVideoHeight = h;
            if (videoSizeChangedListener != null) videoSizeChangedListener.onVideoSizeChanged(w, h);
        });

        try {
            notifyState(PlayState.PREPARING);
            notifyMediaItem(item);
            newPlayer.setDataSource(appContext, item.uri);
            newPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            if (newPlayer == mediaPlayer) {
                notifyError("Cannot play file: " + e.getMessage());
                notifyState(PlayState.ERROR);
            }
        }
    }

    /** Lists embedded tracks; external sidecars load via maybeAutoLoadExternal. */
    private void setupSubtitles(MediaPlayer mp, MediaItem item) {
        refreshSubtitleTracks(mp, -1, null);
        maybeAutoLoadExternal(item);
    }

    private void requestAudioFocus() {
        if (hasAudioFocus) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            pause();
                            hasAudioFocus = false;
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pause();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            if (mediaPlayer != null) mediaPlayer.setVolume(0.3f, 0.3f);
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            if (mediaPlayer != null) mediaPlayer.setVolume(isMuted ? 0 : volumeLeft, isMuted ? 0 : volumeRight);
                        }
                    })
                    .build();
            int res = audioManager.requestAudioFocus(audioFocusRequest);
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } else {
            int res = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }
    }

    private void abandonAudioFocus() {
        if (!hasAudioFocus) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
        hasAudioFocus = false;
    }

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && (state == PlayState.PLAYING || state == PlayState.PAUSED)) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    notifyProgress(pos, dur);
                    updateExternalCue(false);
                    if (abRepeatA >= 0 && abRepeatB >= 0 && pos >= abRepeatB) {
                        seekTo(abRepeatA);
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 250);
            }
        }
    };

    private void startProgressUpdater() { handler.removeCallbacks(progressUpdater); handler.post(progressUpdater); }
    private void stopProgressUpdater() { handler.removeCallbacks(progressUpdater); }

    private void saveResumePosition() {
        MediaItem item = getCurrentItem();
        if (item != null && state == PlayState.PAUSED) {
            int pos = getCurrentPosition();
            PreferenceManager.getDefaultSharedPreferences(appContext)
                    .edit().putInt("resume_pos_" + item.path, pos).apply();
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        try { repeatMode = RepeatMode.values()[prefs.getInt("player_repeat", 0)]; } catch (Exception e) { repeatMode = RepeatMode.OFF; }
        shuffle = prefs.getBoolean("player_shuffle", false);
        keepScreenOn = prefs.getBoolean("player_keep_screen_on", true);
        skipDuration = prefs.getInt("player_skip_duration", 10000);
        playbackSpeed = prefs.getFloat("player_speed", 1f);
        videoBackgroundPlay = prefs.getBoolean("player_video_background", false);
        subtitlesEnabled = prefs.getBoolean("player_sub_enabled", true);
        subtitleAutoLoad = prefs.getBoolean("player_sub_autoload", true);
        subtitleDelayMs = prefs.getInt("player_sub_delay", 0);
    }

    private void saveSettings() {
        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .putInt("player_repeat", repeatMode.ordinal())
                .putBoolean("player_shuffle", shuffle)
                .putBoolean("player_keep_screen_on", keepScreenOn)
                .putInt("player_skip_duration", skipDuration)
                .putFloat("player_speed", playbackSpeed)
                .putBoolean("player_video_background", videoBackgroundPlay)
                .putBoolean("player_sub_enabled", subtitlesEnabled)
                .putBoolean("player_sub_autoload", subtitleAutoLoad)
                .putInt("player_sub_delay", subtitleDelayMs)
                .apply();
    }

    private VideoSizeChangedListener videoSizeChangedListener;
    public void setVideoSizeChangedListener(VideoSizeChangedListener l) { this.videoSizeChangedListener = l; }
    public interface VideoSizeChangedListener { void onVideoSizeChanged(int width, int height); }
    public int getLastVideoWidth() { return lastVideoWidth; }
    public int getLastVideoHeight() { return lastVideoHeight; }

    public static MediaItem buildMediaItemFast(String filePath) {
        Uri uri = Uri.fromFile(new File(filePath));
        String name = filePath.substring(filePath.lastIndexOf('/') + 1);
        String title = filePath.lastIndexOf('\\') >= 0 ? filePath.substring(filePath.lastIndexOf('\\') + 1) : name;
        String lower = filePath.toLowerCase(Locale.ROOT);
        boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".3gp")
                || lower.endsWith(".ts") || lower.endsWith(".flv") || lower.endsWith(".wmv")
                || lower.endsWith(".m4v");
        return new MediaItem(uri, filePath, title, null, null, 0, isVideo);
    }

    public static MediaItem buildMediaItem(Context context, String filePath) {
        Uri uri = Uri.fromFile(new File(filePath));
        String title = null, artist = null, album = null;
        long duration = 0;
        String lower = filePath.toLowerCase(Locale.ROOT);
        boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".3gp")
                || lower.endsWith(".ts") || lower.endsWith(".flv") || lower.endsWith(".wmv")
                || lower.endsWith(".m4v");
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (t != null) title = t;
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (a != null) artist = a;
            String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (al != null) album = al;
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) try { duration = Long.parseLong(d); } catch (NumberFormatException ignored) {}
            String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mime != null) isVideo = mime.startsWith("video/");
            mmr.release();
        } catch (Exception ignored) { try { mmr.release(); } catch (Exception ignored2) {} }
        if (title == null || title.isEmpty()) {
            String name = filePath.substring(filePath.lastIndexOf('/') + 1);
            title = filePath.lastIndexOf('\\') >= 0 ? filePath.substring(filePath.lastIndexOf('\\') + 1) : name;
        }
        return new MediaItem(uri, filePath, title, artist, album, duration, isVideo);
    }
}
