package io.github.abdurazaaqmohammed.tools;

import java.util.ArrayList;
import java.util.List;

public class ToolRegistry {
    public static final String CAT_NETWORK = "Wi-Fi & Network";
    public static final String CAT_STORAGE = "Storage & Apps";
    public static final String CAT_DEVICE = "Device & Hardware";
    public static final String CAT_MATH = "Math & Finance";
    public static final String CAT_TIME = "Time & Productivity";
    public static final String CAT_TEXT = "Text & Security";
    public static final String CAT_MEDIA = "Media & Sound";
    public static final String CAT_RAND = "Random";

    public record ToolItem(String id, String title, String subtitle, int iconRes, String category) {
            public ToolItem(String id, String title, String subtitle, int iconRes, String category) {
                this.id = id;
                this.title = title;
                this.subtitle = subtitle;
                this.iconRes = iconRes;
                this.category = category == null ? CAT_DEVICE : category;
            }
        }
    public static List<ToolItem> getTools(android.content.Context context) {
        List<ToolItem> tools = new ArrayList<>();
        int pkg = 0;
        try {
            pkg = context.getResources().getIdentifier("hex_keyboard_24px", "drawable", context.getPackageName());
        } catch (Exception ignored) {
        }
        tools.add(new ToolItem("wifimanager", "Wi-Fi Manager", "DNS profiles passwords usage", resId(context, "wifi_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("connectivity", "Connectivity Hub", "Network data Bluetooth NFC", resId(context, "wifi_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("qrgen", "QR Generator", "Text URL Wi-Fi to QR", resId(context, "qr_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("qrscan", "QR Scanner", "Camera barcode scan", resId(context, "qr_scan_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("nfc", "NFC Reader", "Scan tags", resId(context, "wifi_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("bluetooth", "Bluetooth Pairs", "Bonded devices", resId(context, "ic_swap", pkg), CAT_NETWORK));
        tools.add(new ToolItem("storagemanager", "Storage Manager", "Largest files clear cache", resId(context, "archive_24px", pkg), CAT_STORAGE));
        tools.add(new ToolItem("quicksettings", "Quick Settings", "System shortcuts", resId(context, "baseline_settings_24", pkg), CAT_STORAGE));
        tools.add(new ToolItem("devicehub", "Device Hub", "Hardware battery CPU sensors storage", resId(context, "baseline_info_24", pkg), CAT_DEVICE));
        tools.add(new ToolItem("compass", "Compass", "Magnetic heading", resId(context, "ic_locate", pkg), CAT_DEVICE));
        tools.add(new ToolItem("level", "Bubble Level", "Surface level meter", resId(context, "horizontal_align_right_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("gps", "GPS Speedometer", "Live speed", resId(context, "ic_locate", pkg), CAT_DEVICE));
        tools.add(new ToolItem("ruler", "Ruler", "On-screen cm inch ruler", resId(context, "horizontal_align_left_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("protractor", "Protractor", "Measure angles by touch", resId(context, "fullscreen_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("magnifier", "Magnifier", "Zoom text loupe", resId(context, "ic_magnifier", pkg), CAT_DEVICE));
        tools.add(new ToolItem("flashlight", "Flashlight", "Torch and fullscreen light", resId(context, "ic_eye_mt", pkg), CAT_DEVICE));
        tools.add(new ToolItem("vibration", "Vibration Studio", "Custom patterns", resId(context, "volume_off_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("strobe", "Strobe Light", "Fullscreen flashing", resId(context, "colorize_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("screentest", "Screen Tester", "Fullscreen dead-pixel test", resId(context, "fullscreen_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("volume", "Volume Panel", "All streams", resId(context, "volume_up_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("ringtone", "Ringtone Preview", "Browse sounds", resId(context, "music_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("wallpaper", "Wallpaper Maker", "Color wheel gradients", resId(context, "image_24px", pkg), CAT_DEVICE));
        tools.add(new ToolItem("calc", "Calculator", "Scientific calculator", resId(context, "hex_keyboard_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("converter", "Unit Converter", "Length weight temp data", resId(context, "baseline_swap_horiz_24", pkg), CAT_MATH));
        tools.add(new ToolItem("baseconv", "Base Converter", "Bin oct dec hex", resId(context, "flip_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("prime", "Prime Tools", "Primes factors", resId(context, "hex_keyboard_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("quadratic", "Quadratic Solver", "Roots vertex", resId(context, "fullscreen_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("matrix", "Matrix 2x2", "Add mul inverse", resId(context, "ic_grid", pkg), CAT_MATH));
        tools.add(new ToolItem("triangle", "Triangle Solver", "Sides angles", resId(context, "jump_to_element_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("geometry", "Geometry Calc", "Area volume", resId(context, "hex_undo_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("fraction", "Fraction Calc", "Simplify fractions", resId(context, "flip_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("pricelab", "Price & Tax Lab", "Discount GST tip percent compare", resId(context, "call_split_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("financelab", "Finance Lab", "EMI interest savings", resId(context, "control_point_duplicate_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("currency", "Currency Converter", "Offline rates", resId(context, "call_split_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("cooking", "Cooking Converter", "Cups grams", resId(context, "image_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("fuel", "Fuel Calculator", "Mileage cost", resId(context, "gauge_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("pace", "Pace Calculator", "Run pace speed", resId(context, "fast_forward_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("ohm", "Ohm Law Calc", "V I R P solver", resId(context, "ic_inspect", pkg), CAT_MATH));
        tools.add(new ToolItem("resistor", "Resistor Decoder", "Color bands", resId(context, "colorize_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("gpa", "GPA Calculator", "Grades credits", resId(context, "pdf_24px", pkg), CAT_MATH));
        tools.add(new ToolItem("timerlab", "Timer Suite", "Stopwatch timer pomodoro intervals", resId(context, "ic_history", pkg), CAT_TIME));
        tools.add(new ToolItem("worldclock", "World Clock", "Time zones live", resId(context, "clock_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("datelab", "Date Toolkit", "Diff age add countdown", resId(context, "inventory_2_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("notes", "Quick Notes", "Saved notes", resId(context, "edit_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("checklist", "Checklist", "Todo list", resId(context, "baseline_sort_24", pkg), CAT_TIME));
        tools.add(new ToolItem("habit", "Habit Tracker", "Streaks", resId(context, "baseline_check_circle_24", pkg), CAT_TIME));
        tools.add(new ToolItem("expense", "Expense Tracker", "Spend log", resId(context, "pdf_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("attendance", "Attendance Tracker", "Percent needed", resId(context, "baseline_check_circle_24", pkg), CAT_TIME));
        tools.add(new ToolItem("tally", "Tally Counter", "Tap counter", resId(context, "add_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("typing", "Typing Test", "WPM accuracy", resId(context, "hex_keyboard_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("healthlab", "Health Hub", "BMI calories fat water sleep", resId(context, "tag_24px", pkg), CAT_TIME));
        tools.add(new ToolItem("textlab", "Text Studio", "Counter case lorem JSON regex", resId(context, "wrap_text_24px", pkg), CAT_TEXT));
        tools.add(new ToolItem("codelab", "Encoder Lab", "Hash Base64 URL binary ciphers", resId(context, "ic_hash_mt", pkg), CAT_TEXT));
        tools.add(new ToolItem("colorconv", "Color Converter", "HEX RGB HSL", resId(context, "colorize_24px", pkg), CAT_TEXT));
        tools.add(new ToolItem("tone", "Tone Generator", "Frequency player", resId(context, "volume_up_24px", pkg), CAT_MEDIA));
        tools.add(new ToolItem("recorder", "Voice Recorder", "Record and play", resId(context, "queue_music_24px", pkg), CAT_MEDIA));
        tools.add(new ToolItem("metronome", "Metronome", "Tempo beat keeper", resId(context, "music_24px", pkg), CAT_MEDIA));
        tools.add(new ToolItem("tts", "Speak Text", "Text to speech", resId(context, "wrap_text_24px", pkg), CAT_MEDIA));
        tools.add(new ToolItem("random", "Randomizer", "Dice coin numbers", resId(context, "shuffle_24px", pkg), CAT_RAND));
        tools.add(new ToolItem("pubgenlab", "Generator Studio", "Passwords UUIDs random", resId(context, "lock_24px", pkg), CAT_RAND));
        return tools;
    }
    public static String[] categoriesInOrder() {
        return new String[]{CAT_NETWORK, CAT_STORAGE, CAT_DEVICE, CAT_MATH, CAT_TIME, CAT_TEXT, CAT_MEDIA, CAT_RAND};
    }
    private static int resId(android.content.Context context, String name, int fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id != 0) {
                return id;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
    public static ToolItem findById(android.content.Context context, String id) {
        if (id == null) {
            return null;
        }
        for (ToolItem item : getTools(context)) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }
}
