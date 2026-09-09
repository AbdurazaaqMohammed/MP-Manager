package io.github.abdurazaaqmohammed.tools;

import java.util.ArrayList;
import java.util.List;

public class ToolRegistry {
    public static class ToolItem {
        public final String id;
        public final String title;
        public final String subtitle;
        public final int iconRes;
        public ToolItem(String id, String title, String subtitle, int iconRes) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
        }
    }
    public static List<ToolItem> getTools(android.content.Context context) {
        List<ToolItem> tools = new ArrayList<>();
        int pkg = 0;
        try {
            pkg = context.getResources().getIdentifier("hex_keyboard_24px", "drawable", context.getPackageName());
        } catch (Exception ignored) {
        }
        tools.add(new ToolItem("calc", "Calculator", "Scientific calculator", resId(context, "hex_keyboard_24px", pkg)));
        tools.add(new ToolItem("converter", "Unit Converter", "Length weight temp data", resId(context, "baseline_swap_horiz_24", pkg)));
        tools.add(new ToolItem("ruler", "Ruler", "On-screen cm inch ruler", resId(context, "horizontal_align_left_24px", pkg)));
        tools.add(new ToolItem("protractor", "Protractor", "Measure angles by touch", resId(context, "fullscreen_24px", pkg)));
        tools.add(new ToolItem("compass", "Compass", "Magnetic heading", resId(context, "ic_locate", pkg)));
        tools.add(new ToolItem("level", "Bubble Level", "Surface level meter", resId(context, "horizontal_align_right_24px", pkg)));
        tools.add(new ToolItem("stopwatch", "Stopwatch", "Laps and precision", resId(context, "ic_history", pkg)));
        tools.add(new ToolItem("timer", "Countdown Timer", "Alarms on finish", resId(context, "stop_circle_24px", pkg)));
        tools.add(new ToolItem("flashlight", "Flashlight", "Torch and screen light", resId(context, "ic_eye_mt", pkg)));
        tools.add(new ToolItem("magnifier", "Magnifier", "Zoom text loupe", resId(context, "ic_magnifier", pkg)));
        tools.add(new ToolItem("password", "Password Generator", "Secure random passwords", resId(context, "lock_24px", pkg)));
        tools.add(new ToolItem("hash", "Hash Generator", "MD5 SHA-1 SHA-256", resId(context, "ic_hash_mt", pkg)));
        tools.add(new ToolItem("base64", "Base64 Tool", "Encode and decode", resId(context, "baseline_text_snippet_24", pkg)));
        tools.add(new ToolItem("json", "JSON Formatter", "Validate and pretty print", resId(context, "baseline_insert_drive_file_24", pkg)));
        tools.add(new ToolItem("textcounter", "Text Counter", "Words chars lines", resId(context, "wrap_text_24px", pkg)));
        tools.add(new ToolItem("datediff", "Date Calculator", "Difference and age", resId(context, "inventory_2_24px", pkg)));
        tools.add(new ToolItem("bmi", "BMI Calculator", "Body mass index", resId(context, "tag_24px", pkg)));
        tools.add(new ToolItem("discount", "Discount Calc", "Price GST percent", resId(context, "call_split_24px", pkg)));
        tools.add(new ToolItem("emi", "EMI Calculator", "Loan EMI interest", resId(context, "control_point_duplicate_24px", pkg)));
        tools.add(new ToolItem("random", "Randomizer", "Dice coin numbers", resId(context, "shuffle_24px", pkg)));
        tools.add(new ToolItem("tally", "Tally Counter", "Tap counter", resId(context, "add_24px", pkg)));
        tools.add(new ToolItem("metronome", "Metronome", "Tempo beat keeper", resId(context, "music_24px", pkg)));
        tools.add(new ToolItem("deviceinfo", "Device Info", "Screen sensors build", resId(context, "baseline_info_24", pkg)));
        tools.add(new ToolItem("netinfo", "Network Info", "Wi-Fi and mobile", resId(context, "wifi_24px", pkg)));
        tools.add(new ToolItem("worldclock", "World Clock", "Time zones live", resId(context, "clock_24px", pkg)));
        tools.add(new ToolItem("currency", "Currency Converter", "Offline rates", resId(context, "call_split_24px", pkg)));
        tools.add(new ToolItem("tip", "Tip Calculator", "Bill split", resId(context, "tag_24px", pkg)));
        tools.add(new ToolItem("gpa", "GPA Calculator", "Grades credits", resId(context, "pdf_24px", pkg)));
        tools.add(new ToolItem("pomodoro", "Pomodoro Timer", "Focus sessions", resId(context, "pause_24px", pkg)));
        tools.add(new ToolItem("hiit", "Interval Timer", "Work rest rounds", resId(context, "fast_forward_24px", pkg)));
        tools.add(new ToolItem("wheel", "Decision Wheel", "Spin picker", resId(context, "repeat_24px", pkg)));
        tools.add(new ToolItem("caseconv", "Case Converter", "Change letter case", resId(context, "uppercase_24px", pkg)));
        tools.add(new ToolItem("morse", "Morse Code", "Encode decode play", resId(context, "terminal_24px", pkg)));
        tools.add(new ToolItem("baseconv", "Base Converter", "Bin oct dec hex", resId(context, "flip_24px", pkg)));
        tools.add(new ToolItem("fuel", "Fuel Calculator", "Mileage cost", resId(context, "gauge_24px", pkg)));
        tools.add(new ToolItem("ohm", "Ohm Law Calc", "V I R P solver", resId(context, "ic_inspect", pkg)));
        tools.add(new ToolItem("resistor", "Resistor Decoder", "Color bands", resId(context, "colorize_24px", pkg)));
        tools.add(new ToolItem("notes", "Quick Notes", "Saved notes", resId(context, "edit_24px", pkg)));
        tools.add(new ToolItem("checklist", "Checklist", "Todo list", resId(context, "baseline_sort_24", pkg)));
        tools.add(new ToolItem("tone", "Tone Generator", "Frequency player", resId(context, "volume_up_24px", pkg)));
        tools.add(new ToolItem("recorder", "Voice Recorder", "Record and play", resId(context, "queue_music_24px", pkg)));
        tools.add(new ToolItem("gps", "GPS Speedometer", "Live speed", resId(context, "ic_locate", pkg)));
        tools.add(new ToolItem("storage", "Storage Info", "Disks and RAM", resId(context, "archive_24px", pkg)));
        tools.add(new ToolItem("battery", "Battery Info", "Level health temp", resId(context, "battery_24px", pkg)));
        tools.add(new ToolItem("sensors", "Sensor Tester", "Live sensor values", resId(context, "search_24px", pkg)));
        tools.add(new ToolItem("tts", "Speak Text", "Text to speech", resId(context, "wrap_text_24px", pkg)));
        tools.add(new ToolItem("bmr", "Calorie Calculator", "BMR TDEE", resId(context, "inventory_2_24px", pkg)));
        tools.add(new ToolItem("compound", "Interest Calculator", "Compound and SIP", resId(context, "control_point_duplicate_24px", pkg)));
        tools.add(new ToolItem("percent", "Percentage Calc", "Percent tools", resId(context, "call_split_24px", pkg)));
        tools.add(new ToolItem("fraction", "Fraction Calc", "Simplify fractions", resId(context, "flip_24px", pkg)));
        tools.add(new ToolItem("agecalc", "Age Calculator", "Exact age plus", resId(context, "inventory_2_24px", pkg)));
        tools.add(new ToolItem("dateadd", "Date Adder", "Date plus days", resId(context, "baseline_sort_24", pkg)));
        tools.add(new ToolItem("timecalc", "Time Calculator", "Add durations", resId(context, "ic_history", pkg)));
        tools.add(new ToolItem("savings", "Savings Goal", "Reach target", resId(context, "save_24px", pkg)));
        tools.add(new ToolItem("gst", "Tax Calculator", "Add remove tax", resId(context, "tag_24px", pkg)));
        tools.add(new ToolItem("pace", "Pace Calculator", "Run pace speed", resId(context, "fast_forward_24px", pkg)));
        tools.add(new ToolItem("cooking", "Cooking Converter", "Cups grams", resId(context, "image_24px", pkg)));
        tools.add(new ToolItem("lorem", "Lorem Generator", "Placeholder text", resId(context, "wrap_text_24px", pkg)));
        tools.add(new ToolItem("strength", "Password Strength", "Entropy score", resId(context, "lock_24px", pkg)));
        tools.add(new ToolItem("uuid", "UUID Generator", "Random IDs", resId(context, "ic_hash_mt", pkg)));
        tools.add(new ToolItem("colorconv", "Color Converter", "HEX RGB HSL", resId(context, "colorize_24px", pkg)));
        tools.add(new ToolItem("regex", "Regex Tester", "Match patterns", resId(context, "find_replace_24px", pkg)));
        tools.add(new ToolItem("urlcodec", "URL Encoder", "Encode decode", resId(context, "baseline_text_snippet_24", pkg)));
        tools.add(new ToolItem("binarytext", "Binary Translator", "Text binary", resId(context, "terminal_24px", pkg)));
        tools.add(new ToolItem("caesar", "Caesar Cipher", "Shift cipher", resId(context, "lowercase_24px", pkg)));
        tools.add(new ToolItem("cards", "Card Deck", "Draw shuffle", resId(context, "shuffle_24px", pkg)));
        tools.add(new ToolItem("oracle", "Magic 8-Ball", "Ask answers", resId(context, "ic_eye_mt", pkg)));
        tools.add(new ToolItem("prime", "Prime Tools", "Primes factors", resId(context, "hex_keyboard_24px", pkg)));
        tools.add(new ToolItem("quadratic", "Quadratic Solver", "Roots vertex", resId(context, "fullscreen_24px", pkg)));
        tools.add(new ToolItem("matrix", "Matrix 2x2", "Add mul inverse", resId(context, "ic_grid", pkg)));
        tools.add(new ToolItem("triangle", "Triangle Solver", "Sides angles", resId(context, "jump_to_element_24px", pkg)));
        tools.add(new ToolItem("geometry", "Geometry Calc", "Area volume", resId(context, "hex_undo_24px", pkg)));
        tools.add(new ToolItem("water", "Water Tracker", "Daily intake", resId(context, "drop_24px", pkg)));
        tools.add(new ToolItem("sleep", "Sleep Cycles", "Best bedtimes", resId(context, "pause_24px", pkg)));
        tools.add(new ToolItem("bodyfat", "Body Fat Est", "Navy method", resId(context, "tag_24px", pkg)));
        tools.add(new ToolItem("habit", "Habit Tracker", "Streaks", resId(context, "baseline_check_circle_24", pkg)));
        tools.add(new ToolItem("expense", "Expense Tracker", "Spend log", resId(context, "pdf_24px", pkg)));
        tools.add(new ToolItem("unitprice", "Price Compare", "Best value pack", resId(context, "baseline_share_24", pkg)));
        tools.add(new ToolItem("screentest", "Screen Tester", "Dead pixel test", resId(context, "fullscreen_24px", pkg)));
        tools.add(new ToolItem("vibration", "Vibration Studio", "Custom patterns", resId(context, "volume_off_24px", pkg)));
        tools.add(new ToolItem("strobe", "Strobe Light", "Flashing screen", resId(context, "colorize_24px", pkg)));
        tools.add(new ToolItem("altimeter", "Altimeter", "Pressure height", resId(context, "arrow_drop_up_24px", pkg)));
        tools.add(new ToolItem("nfc", "NFC Reader", "Scan tags", resId(context, "wifi_24px", pkg)));
        tools.add(new ToolItem("bluetooth", "Bluetooth Pairs", "Bonded devices", resId(context, "ic_swap", pkg)));
        tools.add(new ToolItem("apps", "App Manager", "Installed apps", resId(context, "apk_document_24px", pkg)));
        tools.add(new ToolItem("cpuinfo", "CPU Info", "Cores freq", resId(context, "terminal_24px", pkg)));
        tools.add(new ToolItem("clipboard", "Clipboard History", "Saved clips", resId(context, "baseline_content_copy_24", pkg)));
        tools.add(new ToolItem("datausage", "Data Usage", "Traffic stats", resId(context, "cloud_upload_24px", pkg)));
        tools.add(new ToolItem("volume", "Volume Panel", "All streams", resId(context, "volume_up_24px", pkg)));
        tools.add(new ToolItem("ringtone", "Ringtone Preview", "Browse sounds", resId(context, "music_24px", pkg)));
        tools.add(new ToolItem("wallpaper", "Wallpaper Maker", "Solid gradients", resId(context, "image_24px", pkg)));
        tools.add(new ToolItem("quicksettings", "Quick Settings", "System shortcuts", resId(context, "baseline_settings_24", pkg)));
        tools.add(new ToolItem("attendance", "Attendance Tracker", "Percent needed", resId(context, "baseline_check_circle_24", pkg)));
        tools.add(new ToolItem("typing", "Typing Test", "WPM accuracy", resId(context, "hex_keyboard_24px", pkg)));
        tools.add(new ToolItem("reaction", "Reaction Test", "Tap speed", resId(context, "skip_next_24px", pkg)));
        tools.add(new ToolItem("memory", "Memory Game", "Sequence recall", resId(context, "ic_grid", pkg)));
        tools.add(new ToolItem("tictactoe", "Tic-Tac-Toe", "Vs computer", resId(context, "close_24px", pkg)));
        tools.add(new ToolItem("lottery", "Lottery Picker", "Lucky numbers", resId(context, "shuffle_24px", pkg)));
        tools.add(new ToolItem("moon", "Moon Phase", "Lunar today", resId(context, "moon_24px", pkg)));
        tools.add(new ToolItem("eventcount", "Event Countdown", "Live countdown", resId(context, "clock_24px", pkg)));
        tools.add(new ToolItem("qrgen", "QR Generator", "Text URL Wi-Fi to QR", resId(context, "qr_24px", pkg)));
        tools.add(new ToolItem("qrscan", "QR Scanner", "Camera barcode scan", resId(context, "qr_scan_24px", pkg)));
        return tools;
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
