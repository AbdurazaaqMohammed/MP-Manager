package io.github.abdurazaaqmohammed.packs.media;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RecStrings {

    private static final Map<String, Map<String, String>> LANGS = new LinkedHashMap<>();

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("rec_audio", "Audio");
        en.put("rec_video", "Video");
        en.put("rec_screen", "Screen");
        en.put("rec_ready", "Ready");
        en.put("rec_settings", "Settings");
        en.put("rec_source", "Source");
        en.put("rec_src_mic", "Microphone");
        en.put("rec_src_camcorder", "Camcorder");
        en.put("rec_src_recognition", "Voice recognition");
        en.put("rec_src_communication", "Voice call");
        en.put("rec_format", "Format");
        en.put("rec_fmt_aac", "High quality (AAC)");
        en.put("rec_fmt_amr", "Small size (AMR)");
        en.put("rec_sample_rate", "Sample rate");
        en.put("rec_bitrate", "Bitrate");
        en.put("rec_channels", "Channels");
        en.put("rec_mono", "Mono");
        en.put("rec_stereo", "Stereo");
        en.put("rec_max_length", "Max length (min)");
        en.put("rec_off", "Off");
        en.put("rec_on", "On");
        en.put("rec_record", "Record");
        en.put("rec_stop", "Stop");
        en.put("rec_pause", "Pause");
        en.put("rec_resume", "Resume");
        en.put("rec_paused", "Paused");
        en.put("rec_recording", "Recording");
        en.put("rec_now_playing", "Now playing");
        en.put("rec_nothing_loaded", "Nothing loaded");
        en.put("rec_play", "Play");
        en.put("rec_play_failed", "Play failed");
        en.put("rec_playing", "Playing");
        en.put("rec_no_recordings", "No recordings yet");
        en.put("rec_record_failed", "Record failed");
        en.put("rec_saved", "Saved");
        en.put("rec_rename", "Rename");
        en.put("rec_save", "Save");
        en.put("rec_cancel", "Cancel");
        en.put("rec_share", "Share");
        en.put("rec_share_failed", "Share failed");
        en.put("rec_open", "Open");
        en.put("rec_open_with", "Open with");
        en.put("rec_no_app", "No app found");
        en.put("rec_delete", "Delete");
        en.put("rec_tap_hint", "Tap a recording to play it, long-press for rename, share, open or delete.");
        en.put("rec_mic_needed", "Microphone permission needed, then tap Record");
        en.put("rec_pause_old_android", "Pause needs Android 7+");
        en.put("rec_pause_failed", "Pause failed");
        en.put("rec_camera", "Camera");
        en.put("rec_cam_front", "Front");
        en.put("rec_cam_back", "Back");
        en.put("rec_cam_other", "Camera");
        en.put("rec_no_camera", "No camera");
        en.put("rec_quality", "Quality");
        en.put("rec_q_high", "High");
        en.put("rec_q_low", "Low");
        en.put("rec_audio_track", "Audio track");
        en.put("rec_flash", "Light");
        en.put("rec_files", "Videos");
        en.put("rec_video_hint", "Tap a video to play it, long-press for more actions.");
        en.put("rec_switch_camera", "Switch camera");
        en.put("rec_busy", "Stop recording first");
        en.put("rec_cam_needed", "Camera permission needed");
        en.put("rec_preview_failed", "Preview failed");
        en.put("rec_resolution", "Resolution");
        en.put("rec_frame_rate", "Frame rate");
        en.put("rec_video_bitrate", "Video bitrate");
        en.put("rec_countdown", "Countdown");
        en.put("rec_show_touches", "Show touches");
        en.put("rec_starting", "Starting");
        en.put("rec_denied", "Capture denied");
        en.put("rec_failed_start", "Could not start capture");
        en.put("rec_screen_hint", "Records everything on your screen. Tap a clip to play it, long-press for more actions.");
        LANGS.put("en", en);
    }

    private RecStrings() {
    }

    public static void add(String lang, Map<String, String> table) {
        if (lang == null || table == null) return;
        LANGS.put(lang, new LinkedHashMap<>(table));
    }

    public static String get(String lang, String key) {
        try {
            if (lang != null && !lang.isEmpty()) {
                Map<String, String> table = LANGS.get(lang);
                if (table != null) {
                    String v = table.get(key);
                    if (v != null && !v.isEmpty()) return v;
                }
                int dash = lang.indexOf('-');
                if (dash > 0) {
                    table = LANGS.get(lang.substring(0, dash));
                    if (table != null) {
                        String v = table.get(key);
                        if (v != null && !v.isEmpty()) return v;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Map<String, String> en = LANGS.get("en");
            if (en != null) {
                String v = en.get(key);
                if (v != null) return v;
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
