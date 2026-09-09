package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.TrafficStats;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import io.github.abdurazaaqmohammed.utils.QrUtil;

public class ToolRunnerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private SensorEventListener activeListener;
    private float[] accelValues = null;
    private float[] magnetValues = null;
    private float compassBearing = 0f;
    private CompassView compassView;
    private TextView compassText;
    private LevelView levelView;
    private TextView levelText;
    private boolean stopwatchRunning = false;
    private long stopwatchBase = 0L;
    private long stopwatchAccum = 0L;
    private TextView stopwatchText;
    private Runnable stopwatchTick = null;
    private ArrayAdapter<String> lapAdapter;
    private ArrayList<String> laps = new ArrayList<>();
    private int lapCount = 0;
    private CountDownTimer countDownTimer = null;
    private long timerRemaining = 0L;
    private long timerTotal = 0L;
    private boolean timerRunning = false;
    private TextView timerText;
    private CameraManager cameraManager;
    private String torchCameraId = null;
    private boolean torchOn = false;
    private boolean pendingTorchRetry = false;
    private boolean screenLightOn = false;
    private View screenLightView;
    private boolean sosRunning = false;
    private Runnable sosTick = null;
    private int sosStep = 0;
    private ToneGenerator toneGenerator = null;
    private boolean metronomeRunning = false;
    private Runnable metronomeTick = null;
    private int metronomeBpm = 120;
    private int metronomeBeat = 0;
    private View metronomeFlash;
    private int tallyCount = 0;
    private TextView tallyText;
    private int rulerMode = 0;
    private float rulerCal = 1.0f;
    private RulerView rulerView;
    private TextView rulerInfo;
    private ProtractorView protractorView;
    private TextView protractorText;
    private Vibrator vibrator;
    private TextToSpeech ttsEngine;
    private AudioTrack toneTrack;
    private Thread toneThread;
    private boolean tonePlaying = false;
    private MediaRecorder voiceRecorder;
    private MediaPlayer voicePlayer;
    private boolean recordingNow = false;
    private boolean pendingAudioRetry = false;
    private Runnable pendingAudioAction = null;
    private LocationManager locationManager;
    private LocationListener gpsListener;
    private boolean gpsRunning = false;
    private boolean pendingGpsRetry = false;
    private double gpsMax = 0;
    private double gpsSum = 0;
    private int gpsCount = 0;
    private TextView gpsText;
    private CountDownTimer pomoTimer = null;
    private boolean pomoRunning = false;
    private long pomoLeft = 0L;
    private int pomoPhase = 0;
    private int pomoCycle = 1;
    private TextView pomoText;
    private CountDownTimer hiitTimer = null;
    private boolean hiitRunning = false;
    private TextView sensorLiveText;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPending;
    private TextView nfcText;
    private boolean pendingBtRetry = false;
    private boolean pendingQrScan = false;
    private TextView qrScanOutput = null;
    private android.widget.ImageView qrGenView = null;
    private Bitmap qrGenBitmap = null;
    private int reactionState = 0;
    private long reactionShownAt = 0L;
    private Runnable reactionPending = null;
    private View reactionPad;
    private TextView reactionText;
    private List<Integer> memSeq = new ArrayList<>();
    private int memPos = 0;
    private boolean memAccept = false;
    private int memScore = 0;
    private TextView memText;
    private List<Button> memButtons = new ArrayList<>();
    private String[] tttBoard = new String[9];
    private boolean tttOver = false;
    private int tttWins = 0;
    private int tttLosses = 0;
    private int tttDraws = 0;
    private TextView tttText;
    private List<Button> tttButtons = new ArrayList<>();
    private android.content.ClipboardManager.OnPrimaryClipChangedListener clipListener = null;
    private boolean strobeOn = false;
    private Runnable strobeTick = null;
    private int strobeHz = 4;
    private View strobeView;
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences toolPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean toolDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(toolPrefs.getInt("theme", toolDark ? io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark : io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        String earlyId = getIntent().getStringExtra("tool_id");
        if (earlyId != null && earlyId.equals("ruler")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        String toolId = getIntent().getStringExtra("tool_id");
        String toolTitle = getIntent().getStringExtra("tool_title");
        if (toolTitle == null || toolTitle.isEmpty()) {
            ToolRegistry.ToolItem found = ToolRegistry.findById(this, toolId);
            toolTitle = found == null ? "Tool" : found.title;
        }
        if (toolId == null) {
            toolId = "calc";
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(toolTitle);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        String id = toolId;
        if (id.equals("calc")) {
            buildCalculator(box);
        } else if (id.equals("converter")) {
            buildConverter(box);
        } else if (id.equals("ruler")) {
            buildRuler(box);
        } else if (id.equals("protractor")) {
            buildProtractor(box);
        } else if (id.equals("compass")) {
            buildCompass(box);
        } else if (id.equals("level")) {
            buildLevel(box);
        } else if (id.equals("stopwatch")) {
            buildStopwatch(box);
        } else if (id.equals("timer")) {
            buildTimer(box);
        } else if (id.equals("flashlight")) {
            buildFlashlight(box);
        } else if (id.equals("magnifier")) {
            buildMagnifier(box);
        } else if (id.equals("password")) {
            buildPassword(box);
        } else if (id.equals("hash")) {
            buildHash(box);
        } else if (id.equals("base64")) {
            buildBase64(box);
        } else if (id.equals("json")) {
            buildJson(box);
        } else if (id.equals("textcounter")) {
            buildTextCounter(box);
        } else if (id.equals("datediff")) {
            buildDateDiff(box);
        } else if (id.equals("bmi")) {
            buildBmi(box);
        } else if (id.equals("discount")) {
            buildDiscount(box);
        } else if (id.equals("emi")) {
            buildEmi(box);
        } else if (id.equals("random")) {
            buildRandom(box);
        } else if (id.equals("tally")) {
            buildTally(box);
        } else if (id.equals("metronome")) {
            buildMetronome(box);
        } else if (id.equals("deviceinfo")) {
            buildDeviceInfo(box);
        } else if (id.equals("netinfo")) {
            buildNetInfo(box);
        } else if (id.equals("worldclock")) {
            buildWorldClock(box);
        } else if (id.equals("currency")) {
            buildCurrency(box);
        } else if (id.equals("tip")) {
            buildTip(box);
        } else if (id.equals("gpa")) {
            buildGpa(box);
        } else if (id.equals("pomodoro")) {
            buildPomodoro(box);
        } else if (id.equals("hiit")) {
            buildHiit(box);
        } else if (id.equals("wheel")) {
            buildWheel(box);
        } else if (id.equals("caseconv")) {
            buildCaseConv(box);
        } else if (id.equals("morse")) {
            buildMorse(box);
        } else if (id.equals("baseconv")) {
            buildBaseConv(box);
        } else if (id.equals("fuel")) {
            buildFuel(box);
        } else if (id.equals("ohm")) {
            buildOhm(box);
        } else if (id.equals("resistor")) {
            buildResistor(box);
        } else if (id.equals("notes")) {
            buildNotes(box);
        } else if (id.equals("checklist")) {
            buildChecklist(box);
        } else if (id.equals("tone")) {
            buildTone(box);
        } else if (id.equals("recorder")) {
            buildRecorder(box);
        } else if (id.equals("gps")) {
            buildGps(box);
        } else if (id.equals("storage")) {
            buildStorage(box);
        } else if (id.equals("battery")) {
            buildBattery(box);
        } else if (id.equals("sensors")) {
            buildSensors(box);
        } else if (id.equals("tts")) {
            buildTts(box);
        } else if (id.equals("bmr")) {
            buildBmr(box);
        } else if (id.equals("compound")) {
            buildCompound(box);
        } else if (id.equals("percent")) {
            buildPercent(box);
        } else if (id.equals("fraction")) {
            buildFraction(box);
        } else if (id.equals("agecalc")) {
            buildAgeCalc(box);
        } else if (id.equals("dateadd")) {
            buildDateAdd(box);
        } else if (id.equals("timecalc")) {
            buildTimeCalc(box);
        } else if (id.equals("savings")) {
            buildSavings(box);
        } else if (id.equals("gst")) {
            buildGst(box);
        } else if (id.equals("pace")) {
            buildPace(box);
        } else if (id.equals("cooking")) {
            buildCooking(box);
        } else if (id.equals("lorem")) {
            buildLorem(box);
        } else if (id.equals("strength")) {
            buildStrength(box);
        } else if (id.equals("uuid")) {
            buildUuid(box);
        } else if (id.equals("colorconv")) {
            buildColorConv(box);
        } else if (id.equals("regex")) {
            buildRegex(box);
        } else if (id.equals("urlcodec")) {
            buildUrlCodec(box);
        } else if (id.equals("binarytext")) {
            buildBinaryText(box);
        } else if (id.equals("caesar")) {
            buildCaesar(box);
        } else if (id.equals("cards")) {
            buildCards(box);
        } else if (id.equals("oracle")) {
            buildOracle(box);
        } else if (id.equals("prime")) {
            buildPrime(box);
        } else if (id.equals("quadratic")) {
            buildQuadratic(box);
        } else if (id.equals("matrix")) {
            buildMatrix(box);
        } else if (id.equals("triangle")) {
            buildTriangle(box);
        } else if (id.equals("geometry")) {
            buildGeometry(box);
        } else if (id.equals("water")) {
            buildWater(box);
        } else if (id.equals("sleep")) {
            buildSleep(box);
        } else if (id.equals("bodyfat")) {
            buildBodyFat(box);
        } else if (id.equals("habit")) {
            buildHabit(box);
        } else if (id.equals("expense")) {
            buildExpense(box);
        } else if (id.equals("unitprice")) {
            buildUnitPrice(box);
        } else if (id.equals("screentest")) {
            buildScreenTest(box);
        } else if (id.equals("vibration")) {
            buildVibration(box);
        } else if (id.equals("strobe")) {
            buildStrobe(box);
        } else if (id.equals("altimeter")) {
            buildAltimeter(box);
        } else if (id.equals("nfc")) {
            buildNfc(box);
        } else if (id.equals("bluetooth")) {
            buildBluetooth(box);
        } else if (id.equals("apps")) {
            buildApps(box);
        } else if (id.equals("cpuinfo")) {
            buildCpuInfo(box);
        } else if (id.equals("clipboard")) {
            buildClipboard(box);
        } else if (id.equals("datausage")) {
            buildDataUsage(box);
        } else if (id.equals("volume")) {
            buildVolume(box);
        } else if (id.equals("ringtone")) {
            buildRingtone(box);
        } else if (id.equals("wallpaper")) {
            buildWallpaper(box);
        } else if (id.equals("quicksettings")) {
            buildQuickSettings(box);
        } else if (id.equals("attendance")) {
            buildAttendance(box);
        } else if (id.equals("typing")) {
            buildTyping(box);
        } else if (id.equals("reaction")) {
            buildReaction(box);
        } else if (id.equals("memory")) {
            buildMemory(box);
        } else if (id.equals("tictactoe")) {
            buildTicTacToe(box);
        } else if (id.equals("lottery")) {
            buildLottery(box);
        } else if (id.equals("moon")) {
            buildMoon(box);
        } else if (id.equals("eventcount")) {
            buildEventCount(box);
        } else if (id.equals("qrgen")) {
            buildQrGen(box);
        } else if (id.equals("qrscan")) {
            buildQrScan(box);
        } else {
            TextView t = new TextView(this);
            t.setText("Unknown tool");
            box.addView(t);
        }
    }
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && activeListener != null) {
            try {
                sensorManager.unregisterListener(activeListener);
            } catch (Exception ignored) {
            }
        }
        try {
            if (locationManager != null && gpsListener != null) {
                locationManager.removeUpdates(gpsListener);
            }
        } catch (Exception ignored) {
        }
        try {
            if (nfcAdapter != null) {
                nfcAdapter.disableForegroundDispatch(this);
            }
        } catch (Exception ignored) {
        }
        stopwatchPauseSilent();
    }
    protected void onResume() {
        super.onResume();
        String toolId = getIntent().getStringExtra("tool_id");
        if (toolId == null) {
            return;
        }
        if (toolId.equals("compass")) {
            startCompassSensors();
        } else if (toolId.equals("level")) {
            startLevelSensors();
        } else if (toolId.equals("gps") && gpsRunning) {
            startGpsUpdates();
        } else if (toolId.equals("nfc")) {
            enableNfcDispatch();
        }
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String toolId = getIntent().getStringExtra("tool_id");
        if (toolId != null && toolId.equals("nfc")) {
            parseNfcIntent(intent);
        }
    }
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (sensorManager != null && activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        activeListener = null;
        try {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
        } catch (Exception ignored) {
        }
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) {
        }
        metronomeRunning = false;
        sosRunning = false;
        try {
            if (torchOn && cameraManager != null && torchCameraId != null && Build.VERSION.SDK_INT >= 23) {
                cameraManager.setTorchMode(torchCameraId, false);
            }
        } catch (Exception ignored) {
        }
        torchOn = false;
        try {
            if (toneGenerator != null) {
                toneGenerator.release();
            }
        } catch (Exception ignored) {
        }
        toneGenerator = null;
        try {
            if (pomoTimer != null) {
                pomoTimer.cancel();
            }
        } catch (Exception ignored) {
        }
        try {
            if (hiitTimer != null) {
                hiitTimer.cancel();
            }
        } catch (Exception ignored) {
        }
        stopTone();
        try {
            if (voiceRecorder != null) {
                voiceRecorder.release();
            }
        } catch (Exception ignored) {
        }
        voiceRecorder = null;
        try {
            if (voicePlayer != null) {
                voicePlayer.release();
            }
        } catch (Exception ignored) {
        }
        voicePlayer = null;
        try {
            if (locationManager != null && gpsListener != null) {
                locationManager.removeUpdates(gpsListener);
            }
        } catch (Exception ignored) {
        }
        try {
            if (ttsEngine != null) {
                ttsEngine.stop();
                ttsEngine.shutdown();
            }
        } catch (Exception ignored) {
        }
        ttsEngine = null;
        try {
            if (clipListener != null) {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.removePrimaryClipChangedListener(clipListener);
            }
        } catch (Exception ignored) {
        }
        clipListener = null;
        strobeOn = false;
    }
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 9001 && pendingTorchRetry) {
            pendingTorchRetry = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setTorch(true);
            } else {
                toast("Camera permission denied");
            }
        } else if (requestCode == 9002) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingAudioAction != null) {
                Runnable action = pendingAudioAction;
                pendingAudioAction = null;
                pendingAudioRetry = false;
                action.run();
            } else if (!granted) {
                pendingAudioAction = null;
                pendingAudioRetry = false;
                toast("Microphone permission denied");
            }
        } else if (requestCode == 9003 && pendingGpsRetry) {
            pendingGpsRetry = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGpsUpdates();
            } else {
                toast("Location permission denied");
            }
        } else if (requestCode == 9005 && pendingQrScan) {
            pendingQrScan = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScan();
            } else {
                toast("Camera permission denied");
            }
        } else if (requestCode == 9004 && pendingBtRetry) {
            pendingBtRetry = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshBtList();
            } else {
                toast("Bluetooth permission denied");
            }
        }
    }
    private void enableNfcDispatch() {
        try {
            if (nfcAdapter == null) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            }
            if (nfcAdapter == null) {
                return;
            }
            Intent intent = new Intent(this, getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            nfcPending = PendingIntent.getActivity(this, 0, intent, flags);
            IntentFilter[] filters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
            nfcAdapter.enableForegroundDispatch(this, nfcPending, filters, null);
        } catch (Exception ignored) {
        }
    }
    private void parseNfcIntent(Intent intent) {
        try {
            String action = intent.getAction();
            if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) && !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) && !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                return;
            }
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag == null || nfcText == null) {
                return;
            }
            StringBuilder b = new StringBuilder();
            byte[] id = tag.getId();
            StringBuilder hex = new StringBuilder();
            for (byte bb : id) {
                String h = Integer.toHexString(0xFF & bb);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h.toUpperCase(Locale.US));
            }
            b.append("ID ").append(hex.toString()).append("\n");
            String[] techs = tag.getTechList();
            b.append("Tech: ");
            for (int i = 0; i < techs.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                String t = techs[i];
                int dot = t.lastIndexOf('.');
                b.append(dot >= 0 ? t.substring(dot + 1) : t);
            }
            NdefMessage[] msgs = null;
            try {
                android.os.Parcelable[] raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if (raw != null) {
                    msgs = new NdefMessage[raw.length];
                    for (int i = 0; i < raw.length; i++) {
                        msgs[i] = (NdefMessage) raw[i];
                    }
                }
            } catch (Exception ignored) {
            }
            if (msgs != null) {
                for (NdefMessage msg : msgs) {
                    for (NdefRecord rec : msg.getRecords()) {
                        String payload = decodeNdefText(rec);
                        if (payload != null) {
                            b.append("\n").append(payload);
                        }
                    }
                }
            }
            nfcText.setText(b.toString());
        } catch (Exception e) {
            if (nfcText != null) {
                nfcText.setText("Read failed");
            }
        }
    }
    private String decodeNdefText(NdefRecord rec) {
        try {
            if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && java.util.Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = rec.getPayload();
                boolean utf16 = (payload[0] & 0x80) != 0;
                int langLen = payload[0] & 0x3F;
                return new String(payload, 1 + langLen, payload.length - 1 - langLen, utf16 ? "UTF-16" : "UTF-8");
            } else if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && java.util.Arrays.equals(rec.getType(), NdefRecord.RTD_URI)) {
                byte[] payload = rec.getPayload();
                return new String(payload, 1, payload.length - 1, "UTF-8");
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    private TextView btText;
    private ArrayAdapter<String> btAdapter;
    private List<String> btNames = new ArrayList<>();
    private void refreshBtList() {
        if (btAdapter == null) {
            return;
        }
        btNames.clear();
        try {
            if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                pendingBtRetry = true;
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 9004);
                if (btText != null) {
                    btText.setText("Bluetooth permission needed");
                }
                return;
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                if (btText != null) {
                    btText.setText("No Bluetooth hardware");
                }
                btAdapter.notifyDataSetChanged();
                return;
            }
            if (!adapter.isEnabled()) {
                if (btText != null) {
                    btText.setText("Bluetooth is off, turn it on and refresh");
                }
            } else {
                java.util.Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded == null || bonded.isEmpty()) {
                    if (btText != null) {
                        btText.setText("No paired devices");
                    }
                } else {
                    if (btText != null) {
                        btText.setText(bonded.size() + " paired");
                    }
                    for (BluetoothDevice d : bonded) {
                        String name = d.getName();
                        btNames.add((name == null ? "Unknown" : name) + "\n" + d.getAddress());
                    }
                }
            }
        } catch (Exception e) {
            if (btText != null) {
                btText.setText("Bluetooth unavailable");
            }
        }
        btAdapter.notifyDataSetChanged();
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (scanResult != null) {
                if (scanResult.getContents() != null && qrScanOutput != null) {
                    qrScanOutput.setText(scanResult.getContents());
                    toast("Scanned");
                }
            }
        } catch (Exception ignored) {
        }
    }
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
    private void copyText(String label, String value) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(label, value);
            cm.setPrimaryClip(clip);
            toast("Copied");
        } catch (Exception e) {
            toast("Copy failed");
        }
    }
    private TextView addTitle(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(8));
        box.addView(t, p);
        return t;
    }
    private TextView addLabel(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setAlpha(0.8f);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(8), 0, dp(4));
        box.addView(t, p);
        return t;
    }
    private EditText makeInput(LinearLayout box, String hint, int inputType) {
        com.google.android.material.textfield.TextInputLayout layout =
                io.github.abdurazaaqmohammed.ui.UiFields.box(this, hint);
        EditText e = io.github.abdurazaaqmohammed.ui.UiFields.field(layout, inputType);
        e.setSingleLine(false);
        e.setMinLines(1);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(8));
        box.addView(layout, p);
        return e;
    }
    private MaterialButton makeButton(LinearLayout box, String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        box.addView(b, p);
        return b;
    }
    private TextView makeOutput(LinearLayout box) {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.parseColor("#14000000")));
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(8), 0, dp(4));
        box.addView(t, p);
        return t;
    }
    private LinearLayout makeRow(LinearLayout box) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }
    private MaterialButton makeRowButton(LinearLayout row, String text, float weight) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setMinHeight(dp(56));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int m = dp(4);
        p.setMargins(m, m, m, m);
        row.addView(b, p);
        return b;
    }
    private void vibrateTick() {
        try {
            if (vibrator == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(20);
            }
        } catch (Exception ignored) {
        }
    }
    private void buildCalculator(LinearLayout box) {
        addTitle(box, "Calculator");
        final EditText display = makeInput(box, "0", InputType.TYPE_CLASS_TEXT);
        display.setTextSize(24);
        display.setTypeface(android.graphics.Typeface.MONOSPACE);
        final TextView result = makeOutput(box);
        result.setText("= 0");
        String[][] rows = new String[][]{
                {"C", "(", ")", "DEL"},
                {"7", "8", "9", "div"},
                {"4", "5", "6", "mul"},
                {"1", "2", "3", "sub"},
                {"0", ".", "%", "add"},
                {"sin", "cos", "tan", "eq"},
                {"log", "ln", "sqrt", "pow"},
                {"pi", "e", "^", "ans"}
        };
        final String[] lastAns = new String[]{"0"};
        for (String[] r : rows) {
            LinearLayout row = makeRow(box);
            for (String key : r) {
                String label = key;
                if (key.equals("div")) {
                    label = "÷";
                } else if (key.equals("mul")) {
                    label = "×";
                } else if (key.equals("sub")) {
                    label = "-";
                } else if (key.equals("add")) {
                    label = "+";
                } else if (key.equals("eq")) {
                    label = "=";
                } else if (key.equals("pow")) {
                    label = "x^y";
                } else if (key.equals("DEL")) {
                    label = "⌫";
                }
                MaterialButton b = makeRowButton(row, label, 1f);
                final String k = key;
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String cur = display.getText().toString();
                        if (k.equals("C")) {
                            display.setText("");
                            result.setText("= 0");
                        } else if (k.equals("DEL")) {
                            if (cur.length() > 0) {
                                display.setText(cur.substring(0, cur.length() - 1));
                            }
                        } else if (k.equals("eq")) {
                            String expr = display.getText().toString();
                            try {
                                double val = evalExpression(expr);
                                String out = formatNumber(val);
                                result.setText("= " + out);
                                lastAns[0] = out;
                            } catch (Exception e) {
                                result.setText("Error");
                            }
                        } else if (k.equals("div")) {
                            display.append("÷");
                        } else if (k.equals("mul")) {
                            display.append("×");
                        } else if (k.equals("sub")) {
                            display.append("-");
                        } else if (k.equals("add")) {
                            display.append("+");
                        } else if (k.equals("ans")) {
                            display.append(lastAns[0]);
                        } else if (k.equals("pow")) {
                            display.append("^");
                        } else if (k.equals("sqrt")) {
                            display.append("sqrt(");
                        } else {
                            display.append(k);
                        }
                        String expr2 = display.getText().toString();
                        if (!expr2.isEmpty()) {
                            try {
                                double val2 = evalExpression(expr2);
                                result.setText("= " + formatNumber(val2));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                });
            }
        }
        MaterialButton copyBtn = makeButton(box, "Copy result");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("calc", result.getText().toString());
            }
        });
    }
    private String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "Error";
        }
        DecimalFormat f = new DecimalFormat("0.##########");
        return f.format(v);
    }
    private double evalExpression(String expr) throws Exception {
        if (expr == null || expr.trim().isEmpty()) {
            return 0;
        }
        String s = expr.replace("×", "*").replace("÷", "-DIV-");
        s = s.replace("-DIV-", "/");
        s = s.replace(" ", "");
        ExprParser parser = new ExprParser(s);
        double v = parser.parse();
        return v;
    }
    private static class ExprParser {
        private final String str;
        private int pos = 0;
        ExprParser(String s) {
            str = s;
        }
        double parse() throws Exception {
            double v = parseAddSub();
            if (pos < str.length()) {
                throw new Exception("Unexpected");
            }
            return v;
        }
        private double parseAddSub() throws Exception {
            double v = parseMulDiv();
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c == '+') {
                    pos++;
                    v += parseMulDiv();
                } else if (c == '-') {
                    pos++;
                    v -= parseMulDiv();
                } else {
                    break;
                }
            }
            return v;
        }
        private double parseMulDiv() throws Exception {
            double v = parsePower();
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c == '*') {
                    pos++;
                    v *= parsePower();
                } else if (c == '/') {
                    pos++;
                    double d = parsePower();
                    v = v / d;
                } else if (c == '%') {
                    pos++;
                    double d = parsePower();
                    v = v % d;
                } else {
                    break;
                }
            }
            return v;
        }
        private double parsePower() throws Exception {
            double v = parseUnary();
            if (pos < str.length() && str.charAt(pos) == '^') {
                pos++;
                double e = parseUnary();
                v = Math.pow(v, e);
            }
            return v;
        }
        private double parseUnary() throws Exception {
            if (pos < str.length() && str.charAt(pos) == '+') {
                pos++;
                return parseUnary();
            }
            if (pos < str.length() && str.charAt(pos) == '-') {
                pos++;
                return -parseUnary();
            }
            return parsePrimary();
        }
        private double parsePrimary() throws Exception {
            if (pos < str.length() && str.charAt(pos) == '(') {
                pos++;
                double v = parseAddSub();
                if (pos >= str.length() || str.charAt(pos) != ')') {
                    throw new Exception("Bracket");
                }
                pos++;
                return v;
            }
            if (matchWord("sin")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.sin(Math.toRadians(v));
            }
            if (matchWord("cos")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.cos(Math.toRadians(v));
            }
            if (matchWord("tan")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.tan(Math.toRadians(v));
            }
            if (matchWord("sqrt")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.sqrt(v);
            }
            if (matchWord("log")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.log10(v);
            }
            if (matchWord("ln")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.log(v);
            }
            if (matchWord("pi")) {
                return Math.PI;
            }
            if (pos < str.length() && (str.charAt(pos) == 'e' || str.charAt(pos) == 'E')) {
                pos++;
                return Math.E;
            }
            int start = pos;
            boolean dotSeen = false;
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new Exception("Number");
            }
            return Double.parseDouble(str.substring(start, pos));
        }
        private boolean matchWord(String w) {
            if (str.startsWith(w, pos)) {
                int after = pos + w.length();
                if (after < str.length() && Character.isLetter(str.charAt(after))) {
                    return false;
                }
                pos += w.length();
                return true;
            }
            return false;
        }
        private void expect(char c) throws Exception {
            if (pos >= str.length() || str.charAt(pos) != c) {
                throw new Exception("Expected");
            }
            pos++;
        }
    }
    private void buildConverter(LinearLayout box) {
        addTitle(box, "Unit Converter");
        final String[] categories = new String[]{"Length", "Weight", "Temperature", "Data", "Speed", "Time", "Area", "Volume"};
        Spinner catSpinner = new Spinner(this);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSpinner.setAdapter(catAdapter);
        box.addView(catSpinner);
        final Spinner fromSpinner = new Spinner(this);
        box.addView(fromSpinner);
        final Spinner toSpinner = new Spinner(this);
        box.addView(toSpinner);
        final EditText input = makeInput(box, "Value", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final TextView output = makeOutput(box);
        output.setText("Result");
        final List<String[]> unitState = new ArrayList<>();
        Runnable refreshUnits = new Runnable() {
            public void run() {
            }
        };
        catSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String[] units = unitsForCategory(categories[position]);
                ArrayAdapter<String> a1 = new ArrayAdapter<>(ToolRunnerActivity.this, android.R.layout.simple_spinner_item, units);
                a1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                fromSpinner.setAdapter(a1);
                ArrayAdapter<String> a2 = new ArrayAdapter<>(ToolRunnerActivity.this, android.R.layout.simple_spinner_item, units);
                a2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                toSpinner.setAdapter(a2);
                if (units.length > 1) {
                    toSpinner.setSelection(1);
                }
                convertUnits(categories[position], fromSpinner, toSpinner, input, output);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String cat = (String) catSpinner.getSelectedItem();
                if (cat == null) {
                    cat = categories[0];
                }
                convertUnits(cat, fromSpinner, toSpinner, input, output);
            }
            public void afterTextChanged(Editable s) {
            }
        };
        input.addTextChangedListener(watcher);
        android.widget.AdapterView.OnItemSelectedListener convertListener = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String cat = (String) catSpinner.getSelectedItem();
                if (cat == null) {
                    cat = categories[0];
                }
                convertUnits(cat, fromSpinner, toSpinner, input, output);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        fromSpinner.setOnItemSelectedListener(convertListener);
        toSpinner.setOnItemSelectedListener(convertListener);
        MaterialButton swapBtn = makeButton(box, "Swap units");
        swapBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int f = fromSpinner.getSelectedItemPosition();
                int t = toSpinner.getSelectedItemPosition();
                fromSpinner.setSelection(t);
                toSpinner.setSelection(f);
            }
        });
    }
    private String[] unitsForCategory(String cat) {
        if (cat.equals("Length")) {
            return new String[]{"mm", "cm", "m", "km", "inch", "ft", "yd", "mile"};
        } else if (cat.equals("Weight")) {
            return new String[]{"mg", "g", "kg", "ton", "oz", "lb"};
        } else if (cat.equals("Temperature")) {
            return new String[]{"C", "F", "K"};
        } else if (cat.equals("Data")) {
            return new String[]{"B", "KB", "MB", "GB", "TB", "Kb", "Mb", "Gb"};
        } else if (cat.equals("Speed")) {
            return new String[]{"m/s", "km/h", "mph", "knot", "ft/s"};
        } else if (cat.equals("Time")) {
            return new String[]{"ms", "s", "min", "h", "day", "week"};
        } else if (cat.equals("Area")) {
            return new String[]{"mm2", "cm2", "m2", "ha", "km2", "ft2", "acre"};
        } else {
            return new String[]{"mL", "L", "m3", "tsp", "tbsp", "cup", "floz", "gal"};
        }
    }
    private void convertUnits(String cat, Spinner from, Spinner to, EditText input, TextView output) {
        try {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) {
                output.setText("Result");
                return;
            }
            double v = Double.parseDouble(s);
            String f = from.getSelectedItem() == null ? "" : from.getSelectedItem().toString();
            String t = to.getSelectedItem() == null ? "" : to.getSelectedItem().toString();
            double r;
            if (cat.equals("Temperature")) {
                r = convertTemp(v, f, t);
            } else {
                double base = toBase(v, f, cat);
                r = fromBase(base, t, cat);
            }
            DecimalFormat df = new DecimalFormat("0.######");
            output.setText(df.format(v) + " " + f + " = " + df.format(r) + " " + t);
        } catch (Exception e) {
            output.setText("Invalid input");
        }
    }
    private double toBase(double v, String unit, String cat) {
        if (cat.equals("Length")) {
            if (unit.equals("mm")) return v / 1000.0;
            if (unit.equals("cm")) return v / 100.0;
            if (unit.equals("m")) return v;
            if (unit.equals("km")) return v * 1000.0;
            if (unit.equals("inch")) return v * 0.0254;
            if (unit.equals("ft")) return v * 0.3048;
            if (unit.equals("yd")) return v * 0.9144;
            if (unit.equals("mile")) return v * 1609.344;
        } else if (cat.equals("Weight")) {
            if (unit.equals("mg")) return v / 1000000.0;
            if (unit.equals("g")) return v / 1000.0;
            if (unit.equals("kg")) return v;
            if (unit.equals("ton")) return v * 1000.0;
            if (unit.equals("oz")) return v * 0.028349523125;
            if (unit.equals("lb")) return v * 0.45359237;
        } else if (cat.equals("Data")) {
            if (unit.equals("B")) return v;
            if (unit.equals("KB")) return v * 1024.0;
            if (unit.equals("MB")) return v * 1048576.0;
            if (unit.equals("GB")) return v * 1073741824.0;
            if (unit.equals("TB")) return v * 1099511627776.0;
            if (unit.equals("Kb")) return v * 128.0;
            if (unit.equals("Mb")) return v * 131072.0;
            if (unit.equals("Gb")) return v * 134217728.0;
        } else if (cat.equals("Speed")) {
            if (unit.equals("m/s")) return v;
            if (unit.equals("km/h")) return v / 3.6;
            if (unit.equals("mph")) return v * 0.44704;
            if (unit.equals("knot")) return v * 0.514444;
            if (unit.equals("ft/s")) return v * 0.3048;
        } else if (cat.equals("Time")) {
            if (unit.equals("ms")) return v / 1000.0;
            if (unit.equals("s")) return v;
            if (unit.equals("min")) return v * 60.0;
            if (unit.equals("h")) return v * 3600.0;
            if (unit.equals("day")) return v * 86400.0;
            if (unit.equals("week")) return v * 604800.0;
        } else if (cat.equals("Area")) {
            if (unit.equals("mm2")) return v / 1000000.0;
            if (unit.equals("cm2")) return v / 10000.0;
            if (unit.equals("m2")) return v;
            if (unit.equals("ha")) return v * 10000.0;
            if (unit.equals("km2")) return v * 1000000.0;
            if (unit.equals("ft2")) return v * 0.09290304;
            if (unit.equals("acre")) return v * 4046.8564224;
        } else if (cat.equals("Volume")) {
            if (unit.equals("mL")) return v / 1000.0;
            if (unit.equals("L")) return v;
            if (unit.equals("m3")) return v * 1000.0;
            if (unit.equals("tsp")) return v * 0.00492892159375;
            if (unit.equals("tbsp")) return v * 0.01478676478125;
            if (unit.equals("cup")) return v * 0.2365882365;
            if (unit.equals("floz")) return v * 0.0295735295625;
            if (unit.equals("gal")) return v * 3.785411784;
        }
        return v;
    }
    private double fromBase(double base, String unit, String cat) {
        if (cat.equals("Length")) {
            if (unit.equals("mm")) return base * 1000.0;
            if (unit.equals("cm")) return base * 100.0;
            if (unit.equals("m")) return base;
            if (unit.equals("km")) return base / 1000.0;
            if (unit.equals("inch")) return base / 0.0254;
            if (unit.equals("ft")) return base / 0.3048;
            if (unit.equals("yd")) return base / 0.9144;
            if (unit.equals("mile")) return base / 1609.344;
        } else if (cat.equals("Weight")) {
            if (unit.equals("mg")) return base * 1000000.0;
            if (unit.equals("g")) return base * 1000.0;
            if (unit.equals("kg")) return base;
            if (unit.equals("ton")) return base / 1000.0;
            if (unit.equals("oz")) return base / 0.028349523125;
            if (unit.equals("lb")) return base / 0.45359237;
        } else if (cat.equals("Data")) {
            if (unit.equals("B")) return base;
            if (unit.equals("KB")) return base / 1024.0;
            if (unit.equals("MB")) return base / 1048576.0;
            if (unit.equals("GB")) return base / 1073741824.0;
            if (unit.equals("TB")) return base / 1099511627776.0;
            if (unit.equals("Kb")) return base / 128.0;
            if (unit.equals("Mb")) return base / 131072.0;
            if (unit.equals("Gb")) return base / 134217728.0;
        } else if (cat.equals("Speed")) {
            if (unit.equals("m/s")) return base;
            if (unit.equals("km/h")) return base * 3.6;
            if (unit.equals("mph")) return base / 0.44704;
            if (unit.equals("knot")) return base / 0.514444;
            if (unit.equals("ft/s")) return base / 0.3048;
        } else if (cat.equals("Time")) {
            if (unit.equals("ms")) return base * 1000.0;
            if (unit.equals("s")) return base;
            if (unit.equals("min")) return base / 60.0;
            if (unit.equals("h")) return base / 3600.0;
            if (unit.equals("day")) return base / 86400.0;
            if (unit.equals("week")) return base / 604800.0;
        } else if (cat.equals("Area")) {
            if (unit.equals("mm2")) return base * 1000000.0;
            if (unit.equals("cm2")) return base * 10000.0;
            if (unit.equals("m2")) return base;
            if (unit.equals("ha")) return base / 10000.0;
            if (unit.equals("km2")) return base / 1000000.0;
            if (unit.equals("ft2")) return base / 0.09290304;
            if (unit.equals("acre")) return base / 4046.8564224;
        } else if (cat.equals("Volume")) {
            if (unit.equals("mL")) return base * 1000.0;
            if (unit.equals("L")) return base;
            if (unit.equals("m3")) return base / 1000.0;
            if (unit.equals("tsp")) return base / 0.00492892159375;
            if (unit.equals("tbsp")) return base / 0.01478676478125;
            if (unit.equals("cup")) return base / 0.2365882365;
            if (unit.equals("floz")) return base / 0.0295735295625;
            if (unit.equals("gal")) return base / 3.785411784;
        }
        return base;
    }
    private double convertTemp(double v, String f, String t) {
        double c;
        if (f.equals("C")) {
            c = v;
        } else if (f.equals("F")) {
            c = (v - 32.0) * 5.0 / 9.0;
        } else {
            c = v - 273.15;
        }
        if (t.equals("C")) {
            return c;
        } else if (t.equals("F")) {
            return c * 9.0 / 5.0 + 32.0;
        } else {
            return c + 273.15;
        }
    }
    private void buildRuler(LinearLayout box) {
        addTitle(box, "Ruler");
        addLabel(box, "Place object along the top edge. Toggle units and calibrate with the slider.");
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton cmBtn = new RadioButton(this);
        cmBtn.setId(View.generateViewId());
        cmBtn.setText("cm");
        RadioButton inchBtn = new RadioButton(this);
        inchBtn.setId(View.generateViewId());
        inchBtn.setText("inch");
        group.addView(cmBtn);
        group.addView(inchBtn);
        group.check(rulerMode == 1 ? inchBtn.getId() : cmBtn.getId());
        box.addView(group);
        rulerView = new RulerView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        rp.setMargins(0, dp(8), 0, dp(8));
        box.addView(rulerView, rp);
        rulerInfo = makeOutput(box);
        addLabel(box, "Calibration");
        SeekBar calBar = new SeekBar(this);
        calBar.setMax(40);
        calBar.setProgress(20);
        box.addView(calBar);
        try {
            float saved = getSharedPreferences("tools", MODE_PRIVATE).getFloat("ruler_cal", 1.0f);
            rulerCal = saved;
            calBar.setProgress(Math.round((saved - 0.8f) * 100.0f));
        } catch (Exception ignored) {
        }
        updateRulerInfo();
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                rulerMode = (checkedId == inchBtn.getId()) ? 1 : 0;
                rulerView.setMode(rulerMode);
                rulerView.setCal(rulerCal);
                updateRulerInfo();
            }
        });
        calBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                rulerCal = 0.8f + (progress / 100.0f);
                rulerView.setCal(rulerCal);
                updateRulerInfo();
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putFloat("ruler_cal", rulerCal).apply();
                } catch (Exception ignored) {
                }
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rulerView.setMode(rulerMode);
        rulerView.setCal(rulerCal);
    }
    private void updateRulerInfo() {
        if (rulerInfo == null || rulerView == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthPx = (float) rulerView.getMeasuredWidth();
        if (widthPx <= 0) {
            widthPx = (float) dm.widthPixels - dp(32);
        }
        float xdpi = dm.xdpi <= 0 ? 320f : dm.xdpi;
        float inches = widthPx / xdpi * rulerCal;
        if (rulerMode == 0) {
            rulerInfo.setText("Screen width: " + new DecimalFormat("0.0").format(inches * 2.54) + " cm");
        } else {
            rulerInfo.setText("Screen width: " + new DecimalFormat("0.00").format(inches) + " inch");
        }
        rulerView.post(new Runnable() {
            public void run() {
                updateRulerInfoText();
            }
        });
    }
    private void updateRulerInfoText() {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float widthPx = (float) rulerView.getWidth();
            if (widthPx <= 0) {
                return;
            }
            float xdpi = dm.xdpi <= 0 ? 320f : dm.xdpi;
            float inches = widthPx / xdpi * rulerCal;
            if (rulerMode == 0) {
                rulerInfo.setText("Screen width: " + new DecimalFormat("0.0").format(inches * 2.54) + " cm");
            } else {
                rulerInfo.setText("Screen width: " + new DecimalFormat("0.00").format(inches) + " inch");
            }
        } catch (Exception ignored) {
        }
    }
    private static class RulerView extends View {
        private int mode = 0;
        private float cal = 1.0f;
        private int bgColor = Color.WHITE;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public RulerView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            int ink = Color.parseColor("#202124");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ink);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            paint.setColor(primary);
            paint.setStrokeWidth(4f);
            textPaint.setColor(ink);
            textPaint.setTextSize(32f);
        }
        void setMode(int m) {
            mode = m;
            invalidate();
        }
        void setCal(float c) {
            cal = c;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float xdpi = getResources().getDisplayMetrics().xdpi;
            if (xdpi <= 0) {
                xdpi = 320f;
            }
            float pxPerUnit;
            int maxUnits;
            if (mode == 0) {
                pxPerUnit = xdpi / 2.54f * cal;
                maxUnits = (int) (getWidth() / pxPerUnit) + 1;
                for (int cm = 0; cm <= maxUnits; cm++) {
                    float x = cm * pxPerUnit;
                    canvas.drawLine(x, 0, x, 90, paint);
                    canvas.drawText(String.valueOf(cm), x + 6, 120, textPaint);
                    for (int mm = 1; mm < 10; mm++) {
                        float xm = x + mm * pxPerUnit / 10f;
                        if (xm > getWidth()) {
                            break;
                        }
                        float h = mm == 5 ? 70 : 45;
                        canvas.drawLine(xm, 0, xm, h, paint);
                    }
                }
            } else {
                pxPerUnit = xdpi * cal;
                maxUnits = (int) (getWidth() / pxPerUnit) + 1;
                for (int inch = 0; inch <= maxUnits; inch++) {
                    float x = inch * pxPerUnit;
                    canvas.drawLine(x, 0, x, 90, paint);
                    canvas.drawText(String.valueOf(inch), x + 6, 120, textPaint);
                    for (int q = 1; q < 16; q++) {
                        float xq = x + q * pxPerUnit / 16f;
                        if (xq > getWidth()) {
                            break;
                        }
                        float h = q % 8 == 0 ? 70 : (q % 4 == 0 ? 60 : 45);
                        canvas.drawLine(xq, 0, xq, h, paint);
                    }
                }
            }
            canvas.drawLine(0, getHeight() - 10, getWidth(), getHeight() - 10, paint);
        }
    }
    private void buildProtractor(LinearLayout box) {
        addTitle(box, "Protractor");
        addLabel(box, "Touch the dial to measure an angle from 0 to 180 degrees.");
        protractorView = new ProtractorView(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        box.addView(protractorView, pp);
        protractorText = makeOutput(box);
        protractorText.setText("Angle: 0 deg");
        protractorView.setListener(new ProtractorView.AngleListener() {
            public void onAngle(float deg) {
                protractorText.setText("Angle: " + new DecimalFormat("0.0").format(deg) + " deg");
            }
        });
        MaterialButton resetBtn = makeButton(box, "Reset");
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                protractorView.setAngle(0f);
                protractorText.setText("Angle: 0 deg");
            }
        });
    }
    private static class ProtractorView extends View {
        interface AngleListener {
            void onAngle(float deg);
        }
        private float angle = 0f;
        private AngleListener listener;
        private int bgColor = Color.WHITE;
        private Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ProtractorView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            int tick = Color.parseColor("#5F6368");
            int ink = Color.parseColor("#202124");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                tick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, tick);
                ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ink);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            arcPaint.setColor(primary);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(6f);
            tickPaint.setColor(tick);
            tickPaint.setStrokeWidth(3f);
            needlePaint.setColor(Color.parseColor("#D93025"));
            needlePaint.setStrokeWidth(8f);
            textPaint.setColor(ink);
            textPaint.setTextSize(30f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        void setListener(AngleListener l) {
            listener = l;
        }
        void setAngle(float a) {
            angle = Math.max(0f, Math.min(180f, a));
            invalidate();
        }
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float cx = getWidth() / 2f;
                float cy = getHeight() - 40f;
                float dx = event.getX() - cx;
                float dy = cy - event.getY();
                double deg = Math.toDegrees(Math.atan2(dy, dx));
                if (deg < 0) {
                    deg = 0;
                }
                if (deg > 180) {
                    deg = 180;
                }
                angle = (float) deg;
                invalidate();
                if (listener != null) {
                    listener.onAngle(angle);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float cx = getWidth() / 2f;
            float cy = getHeight() - 40f;
            float radius = Math.min(getWidth() / 2f - 30f, getHeight() - 80f);
            canvas.drawLine(40, cy, getWidth() - 40, cy, tickPaint);
            for (int d = 0; d <= 180; d += 5) {
                double rad = Math.toRadians(d);
                float inner = radius - (d % 30 == 0 ? 60 : (d % 10 == 0 ? 45 : 28));
                float x1 = cx + (float) (Math.cos(rad) * inner);
                float y1 = cy - (float) (Math.sin(rad) * inner);
                float x2 = cx + (float) (Math.cos(rad) * radius);
                float y2 = cy - (float) (Math.sin(rad) * radius);
                canvas.drawLine(x1, y1, x2, y2, tickPaint);
                if (d % 30 == 0) {
                    float tx = cx + (float) (Math.cos(rad) * (radius - 90));
                    float ty = cy - (float) (Math.sin(rad) * (radius - 90)) + 10;
                    canvas.drawText(String.valueOf(d), tx, ty, textPaint);
                }
            }
            double ar = Math.toRadians(angle);
            float nx = cx + (float) (Math.cos(ar) * radius);
            float ny = cy - (float) (Math.sin(ar) * radius);
            canvas.drawLine(cx, cy, nx, ny, needlePaint);
            canvas.drawCircle(cx, cy, 12, needlePaint);
        }
    }
    private void buildCompass(LinearLayout box) {
        addTitle(box, "Compass");
        compassView = new CompassView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(260), dp(260));
        cp.gravity = Gravity.CENTER;
        cp.setMargins(0, dp(8), 0, dp(8));
        box.addView(compassView, cp);
        compassText = makeOutput(box);
        compassText.setText("Waiting for sensors");
        compassText.setGravity(Gravity.CENTER);
        MaterialButton calBtn = makeButton(box, "Restart sensors");
        calBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startCompassSensors();
            }
        });
        startCompassSensors();
    }
    private void startCompassSensors() {
        if (sensorManager == null) {
            if (compassText != null) {
                compassText.setText("No sensors on this device");
            }
            return;
        }
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accel == null || magnet == null) {
            if (compassText != null) {
                compassText.setText("Compass sensor not available");
            }
            return;
        }
        try {
            if (activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        accelValues = null;
        magnetValues = null;
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    accelValues = event.values.clone();
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    magnetValues = event.values.clone();
                }
                if (accelValues != null && magnetValues != null) {
                    float[] r = new float[9];
                    float[] orient = new float[3];
                    if (SensorManager.getRotationMatrix(r, null, accelValues, magnetValues)) {
                        SensorManager.getOrientation(r, orient);
                        float az = (float) Math.toDegrees(orient[0]);
                        if (az < 0) {
                            az += 360f;
                        }
                        compassBearing = az;
                        if (compassView != null) {
                            compassView.setBearing(az);
                        }
                        if (compassText != null) {
                            compassText.setText(Math.round(az) + " deg  " + cardinalFor(az));
                        }
                    }
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(activeListener, accel, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(activeListener, magnet, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            if (compassText != null) {
                compassText.setText("Sensor error");
            }
        }
    }
    private String cardinalFor(float az) {
        String[] names = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = Math.round(az / 45f) % 8;
        return names[idx];
    }
    private static class CompassView extends View {
        private float bearing = 0f;
        private int bgColor = Color.WHITE;
        private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public CompassView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            int tick = Color.parseColor("#5F6368");
            int ink = Color.parseColor("#202124");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                tick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, tick);
                ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ink);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            circlePaint.setColor(primary);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(8f);
            tickPaint.setColor(tick);
            tickPaint.setStrokeWidth(4f);
            needlePaint.setColor(Color.parseColor("#D93025"));
            needlePaint.setStrokeWidth(10f);
            textPaint.setColor(ink);
            textPaint.setTextSize(44f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        void setBearing(float b) {
            bearing = b;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - 20f;
            canvas.drawColor(bgColor);
            canvas.drawCircle(cx, cy, radius, circlePaint);
            canvas.save();
            canvas.rotate(-bearing, cx, cy);
            String[] labels = new String[]{"N", "E", "S", "W"};
            for (int i = 0; i < 360; i += 15) {
                double rad = Math.toRadians(i);
                float len = i % 90 == 0 ? 50 : 28;
                float x1 = cx + (float) (Math.sin(rad) * (radius - len));
                float y1 = cy - (float) (Math.cos(rad) * (radius - len));
                float x2 = cx + (float) (Math.sin(rad) * radius);
                float y2 = cy - (float) (Math.cos(rad) * radius);
                canvas.drawLine(x1, y1, x2, y2, tickPaint);
            }
            for (int i = 0; i < 4; i++) {
                double rad = Math.toRadians(i * 90);
                float tx = cx + (float) (Math.sin(rad) * (radius - 90));
                float ty = cy - (float) (Math.cos(rad) * (radius - 90)) + 16;
                canvas.drawText(labels[i], tx, ty, textPaint);
            }
            canvas.restore();
            canvas.drawLine(cx, cy, cx, cy - radius + 60, needlePaint);
            canvas.drawCircle(cx, cy, 14, needlePaint);
        }
    }
    private void buildLevel(LinearLayout box) {
        addTitle(box, "Bubble Level");
        levelView = new LevelView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        box.addView(levelView, lp);
        levelText = makeOutput(box);
        levelText.setText("Waiting for sensors");
        levelText.setGravity(Gravity.CENTER);
        startLevelSensors();
    }
    private void startLevelSensors() {
        if (sensorManager == null) {
            if (levelText != null) {
                levelText.setText("No sensors on this device");
            }
            return;
        }
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel == null) {
            if (levelText != null) {
                levelText.setText("Accelerometer not available");
            }
            return;
        }
        try {
            if (activeListener != null && compassView == null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        if (compassView != null && getIntent().getStringExtra("tool_id") != null && getIntent().getStringExtra("tool_id").equals("compass")) {
            return;
        }
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                double pitch = Math.toDegrees(Math.atan2(-x, Math.sqrt(y * y + z * z)));
                double roll = Math.toDegrees(Math.atan2(y, z));
                if (levelView != null) {
                    levelView.setTilt((float) pitch, (float) roll);
                }
                if (levelText != null) {
                    boolean flat = Math.abs(pitch) < 1.5 && Math.abs(roll) < 1.5;
                    levelText.setText("Pitch " + new DecimalFormat("0.0").format(pitch) + "  Roll " + new DecimalFormat("0.0").format(roll) + (flat ? "  LEVEL" : ""));
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(activeListener, accel, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            if (levelText != null) {
                levelText.setText("Sensor error");
            }
        }
    }
    private static class LevelView extends View {
        private float pitch = 0f;
        private float roll = 0f;
        private int bgColor = Color.WHITE;
        private Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public LevelView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            ringPaint.setColor(primary);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(6f);
            bubblePaint.setColor(Color.parseColor("#D93025"));
        }
        void setTilt(float p, float r) {
            pitch = p;
            roll = r;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(cx, cy) - 24f;
            canvas.drawCircle(cx, cy, maxR, ringPaint);
            canvas.drawCircle(cx, cy, maxR / 3f, ringPaint);
            canvas.drawCircle(cx, cy, 10, ringPaint);
            float bx = cx + Math.max(-1f, Math.min(1f, pitch / 20f)) * maxR;
            float by = cy + Math.max(-1f, Math.min(1f, roll / 20f)) * maxR;
            float dx = bx - cx;
            float dy = by - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > maxR - 30) {
                bx = cx + dx / dist * (maxR - 30);
                by = cy + dy / dist * (maxR - 30);
            }
            canvas.drawCircle(bx, by, 30, bubblePaint);
        }
    }
    private void buildStopwatch(LinearLayout box) {
        addTitle(box, "Stopwatch");
        stopwatchText = makeOutput(box);
        stopwatchText.setTextSize(32);
        stopwatchText.setGravity(Gravity.CENTER);
        stopwatchText.setText("00:00.00");
        LinearLayout row = makeRow(box);
        MaterialButton startBtn = makeRowButton(row, "Start", 1f);
        MaterialButton lapBtn = makeRowButton(row, "Lap", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        laps.clear();
        lapCount = 0;
        lapAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, laps);
        android.widget.ListView lapList = new android.widget.ListView(this);
        lapList.setAdapter(lapAdapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        box.addView(lapList, lp);
        stopwatchTick = new Runnable() {
            public void run() {
                if (stopwatchRunning) {
                    stopwatchText.setText(formatStopwatch(elapsedStopwatch()));
                    handler.postDelayed(this, 30);
                }
            }
        };
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (stopwatchRunning) {
                    stopwatchPauseSilent();
                    ((Button) v).setText("Start");
                } else {
                    stopwatchBase = SystemClock.elapsedRealtime();
                    stopwatchRunning = true;
                    ((Button) v).setText("Pause");
                    handler.post(stopwatchTick);
                }
            }
        });
        lapBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (stopwatchRunning) {
                    lapCount++;
                    laps.add(0, "Lap " + lapCount + "  " + formatStopwatch(elapsedStopwatch()));
                    lapAdapter.notifyDataSetChanged();
                    vibrateTick();
                }
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopwatchRunning = false;
                stopwatchAccum = 0L;
                stopwatchText.setText("00:00.00");
                laps.clear();
                lapAdapter.notifyDataSetChanged();
                lapCount = 0;
                startBtn.setText("Start");
            }
        });
    }
    private long elapsedStopwatch() {
        if (stopwatchRunning) {
            return stopwatchAccum + (SystemClock.elapsedRealtime() - stopwatchBase);
        }
        return stopwatchAccum;
    }
    private void stopwatchPauseSilent() {
        if (stopwatchRunning) {
            stopwatchAccum = elapsedStopwatch();
            stopwatchRunning = false;
        }
    }
    private String formatStopwatch(long ms) {
        long m = ms / 60000;
        long s = (ms % 60000) / 1000;
        long cs = (ms % 1000) / 10;
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs);
    }
    private void buildTimer(LinearLayout box) {
        addTitle(box, "Countdown Timer");
        LinearLayout row = makeRow(box);
        final EditText hInput = makeRowInput(row, "hh", InputType.TYPE_CLASS_NUMBER, 1f, null);
        final EditText mInput = makeRowInput(row, "mm", InputType.TYPE_CLASS_NUMBER, 1f, null);
        final EditText sInput = makeRowInput(row, "ss", InputType.TYPE_CLASS_NUMBER, 1f, null);
        timerText = makeOutput(box);
        timerText.setTextSize(32);
        timerText.setGravity(Gravity.CENTER);
        timerText.setText("00:00");
        LinearLayout row2 = makeRow(box);
        MaterialButton startBtn = makeRowButton(row2, "Start", 1f);
        MaterialButton pauseBtn = makeRowButton(row2, "Pause", 1f);
        MaterialButton resetBtn = makeRowButton(row2, "Reset", 1f);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (timerRunning) {
                    return;
                }
                long total = timerRemaining;
                if (total <= 0) {
                    long h = parseLongSafe(hInput.getText().toString());
                    long m = parseLongSafe(mInput.getText().toString());
                    long s = parseLongSafe(sInput.getText().toString());
                    total = (h * 3600 + m * 60 + s) * 1000;
                }
                if (total <= 0) {
                    toast("Enter a duration");
                    return;
                }
                timerTotal = total;
                timerRunning = true;
                try {
                    if (countDownTimer != null) {
                        countDownTimer.cancel();
                    }
                } catch (Exception ignored) {
                }
                countDownTimer = new CountDownTimer(total, 200) {
                    public void onTick(long left) {
                        timerRemaining = left;
                        timerText.setText(formatTimer(left));
                    }
                    public void onFinish() {
                        timerRunning = false;
                        timerRemaining = 0;
                        timerText.setText("Done");
                        toast("Time is up");
                        vibrateTick();
                        beep();
                    }
                };
                countDownTimer.start();
            }
        });
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (timerRunning && countDownTimer != null) {
                    countDownTimer.cancel();
                    timerRunning = false;
                }
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (countDownTimer != null) {
                    try {
                        countDownTimer.cancel();
                    } catch (Exception ignored) {
                    }
                }
                timerRunning = false;
                timerRemaining = 0;
                timerText.setText("00:00");
            }
        });
    }
    private long parseLongSafe(String s) {
        try {
            s = s.trim();
            if (s.isEmpty()) {
                return 0;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
    private String formatTimer(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
    private void beep() {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600);
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        tg.release();
                    } catch (Exception ignored) {
                    }
                }
            }, 800);
        } catch (Exception ignored) {
        }
    }
    private void buildFlashlight(LinearLayout box) {
        addTitle(box, "Flashlight");
        addLabel(box, "Torch uses the camera flash. Screen light fills the display white.");
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    for (String id : cameraManager.getCameraIdList()) {
                        torchCameraId = id;
                        try {
                            Boolean flash = cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                            if (flash != null && flash) {
                                torchCameraId = id;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        final MaterialButton torchBtn = makeButton(box, "Torch: OFF");
        torchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setTorch(!torchOn);
                torchBtn.setText(torchOn ? "Torch: ON" : "Torch: OFF");
            }
        });
        final MaterialButton screenBtn = makeButton(box, "Screen light: OFF");
        screenLightView = new View(this);
        screenLightView.setBackgroundColor(Color.WHITE);
        screenLightView.setVisibility(View.GONE);
        box.addView(screenLightView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        screenBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                screenLightOn = !screenLightOn;
                screenLightView.setVisibility(screenLightOn ? View.VISIBLE : View.GONE);
                screenBtn.setText(screenLightOn ? "Screen light: ON" : "Screen light: OFF");
                if (screenLightOn) {
                    toast("Maximize brightness for best effect");
                }
            }
        });
        final MaterialButton sosBtn = makeButton(box, "SOS blink: OFF");
        sosBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sosRunning = !sosRunning;
                sosBtn.setText(sosRunning ? "SOS blink: ON" : "SOS blink: OFF");
                if (sosRunning) {
                    sosStep = 0;
                    if (sosTick == null) {
                        sosTick = new Runnable() {
                            public void run() {
                                if (!sosRunning) {
                                    return;
                                }
                                boolean on = (sosStep % 2 == 0);
                                setTorchSilent(on);
                                torchBtn.setText(torchOn ? "Torch: ON" : "Torch: OFF");
                                sosStep++;
                                handler.postDelayed(this, sosStep % 4 == 0 ? 600 : 250);
                            }
                        };
                    }
                    handler.post(sosTick);
                } else {
                    setTorchSilent(false);
                    torchBtn.setText("Torch: OFF");
                }
            }
        });
    }
    private void setTorch(boolean on) {
        if (Build.VERSION.SDK_INT < 23) {
            toast("Torch needs Android 6+");
            return;
        }
        if (cameraManager == null || torchCameraId == null) {
            toast("Flash not available");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchRetry = true;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 9001);
            toast("Camera permission needed");
            return;
        }
        setTorchSilent(on);
    }
    private void setTorchSilent(boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && cameraManager != null && torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, on);
                torchOn = on;
            }
        } catch (Exception e) {
            toast("Torch failed");
        }
    }
    private void buildMagnifier(LinearLayout box) {
        addTitle(box, "Magnifier");
        addLabel(box, "Type or paste text, then zoom it with the slider.");
        final EditText input = makeInput(box, "Text to magnify", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText("Hold the phone close and read comfortably.");
        final TextView zoom = new TextView(this);
        zoom.setText("Hold the phone close and read comfortably.");
        zoom.setTextSize(32);
        zoom.setPadding(dp(12), dp(12), dp(12), dp(12));
        zoom.setBackgroundColor(Color.parseColor("#FFFFFF"));
        zoom.setTextColor(Color.parseColor("#000000"));
        box.addView(zoom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addLabel(box, "Text size");
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(108);
        sizeBar.setProgress(20);
        box.addView(sizeBar);
        final CheckBox invertBox = new CheckBox(this);
        invertBox.setText("High contrast (black on yellow)");
        box.addView(invertBox);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                zoom.setText(s.toString());
            }
            public void afterTextChanged(Editable s) {
            }
        });
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                zoom.setTextSize(12 + progress);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        invertBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                if (checked) {
                    zoom.setBackgroundColor(Color.parseColor("#000000"));
                    zoom.setTextColor(Color.parseColor("#FFFF00"));
                } else {
                    zoom.setBackgroundColor(Color.parseColor("#FFFFFF"));
                    zoom.setTextColor(Color.parseColor("#000000"));
                }
            }
        });
    }
    private void buildPassword(LinearLayout box) {
        addTitle(box, "Password Generator");
        final TextView lengthLabel = addLabel(box, "Length: 16");
        SeekBar lengthBar = new SeekBar(this);
        lengthBar.setMax(60);
        lengthBar.setProgress(12);
        box.addView(lengthBar);
        final CheckBox upperBox = new CheckBox(this);
        upperBox.setText("A-Z");
        upperBox.setChecked(true);
        box.addView(upperBox);
        final CheckBox lowerBox = new CheckBox(this);
        lowerBox.setText("a-z");
        lowerBox.setChecked(true);
        box.addView(lowerBox);
        final CheckBox digitBox = new CheckBox(this);
        digitBox.setText("0-9");
        digitBox.setChecked(true);
        box.addView(digitBox);
        final CheckBox symbolBox = new CheckBox(this);
        symbolBox.setText("Symbols");
        symbolBox.setChecked(true);
        box.addView(symbolBox);
        final TextView output = makeOutput(box);
        output.setText("Press Generate");
        lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int len = 4 + progress;
                lengthLabel.setText("Length: " + len);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        MaterialButton genBtn = makeButton(box, "Generate");
        genBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int len = 4 + lengthBar.getProgress();
                StringBuilder pool = new StringBuilder();
                if (upperBox.isChecked()) {
                    pool.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                }
                if (lowerBox.isChecked()) {
                    pool.append("abcdefghijklmnopqrstuvwxyz");
                }
                if (digitBox.isChecked()) {
                    pool.append("0123456789");
                }
                if (symbolBox.isChecked()) {
                    pool.append("!@#$%^&*()-_=+[]{};:,.?");
                }
                if (pool.length() == 0) {
                    toast("Pick at least one set");
                    return;
                }
                try {
                    SecureRandom random = new SecureRandom();
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < len; i++) {
                        out.append(pool.charAt(random.nextInt(pool.length())));
                    }
                    output.setText(out.toString());
                } catch (Exception e) {
                    output.setText("Error");
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("password", output.getText().toString());
            }
        });
    }
    private void buildHash(LinearLayout box) {
        addTitle(box, "Hash Generator");
        final EditText input = makeInput(box, "Text to hash", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final TextView output = makeOutput(box);
        output.setText("Result appears here");
        MaterialButton goBtn = makeButton(box, "Compute MD5 SHA-1 SHA-256 SHA-512");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = input.getText().toString();
                try {
                    StringBuilder b = new StringBuilder();
                    b.append("MD5: ").append(hashString(s, "MD5")).append("\n\n");
                    b.append("SHA-1: ").append(hashString(s, "SHA-1")).append("\n\n");
                    b.append("SHA-256: ").append(hashString(s, "SHA-256")).append("\n\n");
                    b.append("SHA-512: ").append(hashString(s, "SHA-512"));
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Error: " + e.getMessage());
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("hash", output.getText().toString());
            }
        });
    }
    private String hashString(String s, String algo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algo);
        byte[] bytes = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xFF & b);
            if (h.length() == 1) {
                hex.append('0');
            }
            hex.append(h);
        }
        return hex.toString();
    }
    private void buildBase64(LinearLayout box) {
        addTitle(box, "Base64 Tool");
        final EditText input = makeInput(box, "Input", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final TextView output = makeOutput(box);
        output.setText("Result appears here");
        LinearLayout row = makeRow(box);
        MaterialButton encBtn = makeRowButton(row, "Encode", 1f);
        MaterialButton decBtn = makeRowButton(row, "Decode", 1f);
        encBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String s = input.getText().toString();
                    output.setText(Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                } catch (Exception e) {
                    output.setText("Error");
                }
            }
        });
        decBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String s = input.getText().toString().trim();
                    output.setText(new String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8));
                } catch (Exception e) {
                    output.setText("Invalid Base64");
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy result");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("base64", output.getText().toString());
            }
        });
    }
    private void buildJson(LinearLayout box) {
        addTitle(box, "JSON Formatter");
        final EditText input = makeInput(box, "{\"key\":\"value\"}", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        final TextView output = makeOutput(box);
        output.setText("Result appears here");
        LinearLayout row = makeRow(box);
        MaterialButton fmtBtn = makeRowButton(row, "Format", 1f);
        MaterialButton minBtn = makeRowButton(row, "Minify", 1f);
        MaterialButton validBtn = makeRowButton(row, "Validate", 1f);
        fmtBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String s = input.getText().toString().trim();
                    if (s.startsWith("[")) {
                        org.json.JSONArray arr = new org.json.JSONArray(s);
                        output.setText(arr.toString(2));
                    } else {
                        JSONObject obj = new JSONObject(s);
                        output.setText(obj.toString(2));
                    }
                } catch (Exception e) {
                    output.setText("Invalid JSON");
                }
            }
        });
        minBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String s = input.getText().toString().trim();
                    if (s.startsWith("[")) {
                        org.json.JSONArray arr = new org.json.JSONArray(s);
                        output.setText(arr.toString());
                    } else {
                        JSONObject obj = new JSONObject(s);
                        output.setText(obj.toString());
                    }
                } catch (Exception e) {
                    output.setText("Invalid JSON");
                }
            }
        });
        validBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String s = input.getText().toString().trim();
                    if (s.startsWith("[")) {
                        new org.json.JSONArray(s);
                    } else {
                        new JSONObject(s);
                    }
                    output.setText("Valid JSON");
                } catch (Exception e) {
                    output.setText("Invalid JSON");
                }
            }
        });
    }
    private void buildTextCounter(LinearLayout box) {
        addTitle(box, "Text Counter");
        final EditText input = makeInput(box, "Type or paste text", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(5);
        final TextView output = makeOutput(box);
        output.setText("Chars: 0  Words: 0  Lines: 0");
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String t = s.toString();
                int chars = t.length();
                int lines = t.isEmpty() ? 0 : t.split("\n", -1).length;
                String trimmed = t.trim();
                int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
                int sentences = 0;
                for (int i = 0; i < t.length(); i++) {
                    char c = t.charAt(i);
                    if (c == '.' || c == '!' || c == '?') {
                        sentences++;
                    }
                }
                output.setText("Chars: " + chars + "  Words: " + words + "  Lines: " + lines + "  Sentences: " + sentences);
            }
            public void afterTextChanged(Editable s) {
            }
        });
    }
    private void buildDateDiff(LinearLayout box) {
        addTitle(box, "Date Calculator");
        addLabel(box, "Use yyyy-MM-dd, for example 2024-01-31.");
        final EditText d1 = makeInput(box, "Start date", InputType.TYPE_CLASS_DATETIME);
        final EditText d2 = makeInput(box, "End date", InputType.TYPE_CLASS_DATETIME);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        d1.setText(fmt.format(new Date()));
        d2.setText(fmt.format(new Date()));
        final TextView output = makeOutput(box);
        MaterialButton todayBtn = makeButton(box, "Set both to today");
        todayBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                d1.setText(today);
                d2.setText(today);
            }
        });
        MaterialButton calcBtn = makeButton(box, "Calculate difference");
        calcBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    f.setLenient(false);
                    Date a = f.parse(d1.getText().toString().trim());
                    Date b = f.parse(d2.getText().toString().trim());
                    long diff = Math.abs(b.getTime() - a.getTime());
                    long days = diff / 86400000L;
                    long weeks = days / 7;
                    long months = days / 30;
                    output.setText(days + " days  (" + weeks + " weeks, about " + months + " months)");
                } catch (Exception e) {
                    output.setText("Use yyyy-MM-dd");
                }
            }
        });
        MaterialButton ageBtn = makeButton(box, "Age from start date to today");
        ageBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    f.setLenient(false);
                    Date birth = f.parse(d1.getText().toString().trim());
                    Calendar c1 = Calendar.getInstance();
                    c1.setTime(birth);
                    Calendar c2 = Calendar.getInstance();
                    int years = c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR);
                    int months = c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH);
                    int days = c2.get(Calendar.DAY_OF_MONTH) - c1.get(Calendar.DAY_OF_MONTH);
                    if (days < 0) {
                        months--;
                        days += 30;
                    }
                    if (months < 0) {
                        years--;
                        months += 12;
                    }
                    output.setText(years + " years, " + months + " months, " + days + " days");
                } catch (Exception e) {
                    output.setText("Use yyyy-MM-dd");
                }
            }
        });
    }
    private void buildBmi(LinearLayout box) {
        addTitle(box, "BMI Calculator");
        final EditText heightInput = makeInput(box, "Height in cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText weightInput = makeInput(box, "Weight in kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        output.setText("Enter height and weight");
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double h = Double.parseDouble(heightInput.getText().toString()) / 100.0;
                    double w = Double.parseDouble(weightInput.getText().toString());
                    double bmi = w / (h * h);
                    String cat;
                    if (bmi < 18.5) {
                        cat = "Underweight";
                    } else if (bmi < 25) {
                        cat = "Normal";
                    } else if (bmi < 30) {
                        cat = "Overweight";
                    } else {
                        cat = "Obese";
                    }
                    output.setText("BMI " + new DecimalFormat("0.0").format(bmi) + "  " + cat);
                } catch (Exception e) {
                    output.setText("Invalid input");
                }
            }
        });
    }
    private void buildDiscount(LinearLayout box) {
        addTitle(box, "Discount Calculator");
        final EditText priceInput = makeInput(box, "Original price", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText discInput = makeInput(box, "Discount percent", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText taxInput = makeInput(box, "Tax percent (optional)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double price = Double.parseDouble(priceInput.getText().toString());
                    double disc = discInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(discInput.getText().toString());
                    double tax = taxInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(taxInput.getText().toString());
                    double saved = price * disc / 100.0;
                    double afterDisc = price - saved;
                    double taxAmt = afterDisc * tax / 100.0;
                    double total = afterDisc + taxAmt;
                    DecimalFormat df = new DecimalFormat("0.00");
                    output.setText("You save " + df.format(saved) + ", pay " + df.format(total));
                } catch (Exception e) {
                    output.setText("Invalid input");
                }
            }
        });
    }
    private void buildEmi(LinearLayout box) {
        addTitle(box, "EMI Calculator");
        final EditText pInput = makeInput(box, "Loan amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText rInput = makeInput(box, "Annual interest percent", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText nInput = makeInput(box, "Months", InputType.TYPE_CLASS_NUMBER);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double p = Double.parseDouble(pInput.getText().toString());
                    double annual = Double.parseDouble(rInput.getText().toString());
                    int n = Integer.parseInt(nInput.getText().toString().trim());
                    double r = annual / 1200.0;
                    double emi;
                    if (r == 0) {
                        emi = p / n;
                    } else {
                        double pow = Math.pow(1 + r, n);
                        emi = p * r * pow / (pow - 1);
                    }
                    double total = emi * n;
                    DecimalFormat df = new DecimalFormat("0.00");
                    output.setText("EMI " + df.format(emi) + "  Total " + df.format(total) + "  Interest " + df.format(total - p));
                } catch (Exception e) {
                    output.setText("Invalid input");
                }
            }
        });
    }
    private void buildRandom(LinearLayout box) {
        addTitle(box, "Randomizer");
        final EditText minInput = makeInput(box, "Min", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText maxInput = makeInput(box, "Max", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        minInput.setText("1");
        maxInput.setText("100");
        final TextView output = makeOutput(box);
        output.setTextSize(40);
        output.setGravity(Gravity.CENTER);
        output.setText("-");
        final Random random = new Random();
        LinearLayout row = makeRow(box);
        MaterialButton numBtn = makeRowButton(row, "Number", 1f);
        MaterialButton diceBtn = makeRowButton(row, "Dice", 1f);
        MaterialButton coinBtn = makeRowButton(row, "Coin", 1f);
        numBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    int min = Integer.parseInt(minInput.getText().toString().trim());
                    int max = Integer.parseInt(maxInput.getText().toString().trim());
                    if (min > max) {
                        int t = min;
                        min = max;
                        max = t;
                    }
                    output.setText(String.valueOf(min + random.nextInt(max - min + 1)));
                    vibrateTick();
                } catch (Exception e) {
                    output.setText("?");
                }
            }
        });
        diceBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int d = 1 + random.nextInt(6);
                String[] faces = new String[]{"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
                output.setText(faces[d - 1] + "  " + d);
                vibrateTick();
            }
        });
        coinBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(random.nextBoolean() ? "Heads" : "Tails");
                vibrateTick();
            }
        });
    }
    private void buildTally(LinearLayout box) {
        addTitle(box, "Tally Counter");
        tallyText = makeOutput(box);
        tallyText.setTextSize(56);
        tallyText.setGravity(Gravity.CENTER);
        tallyCount = 0;
        tallyText.setText("0");
        LinearLayout row = makeRow(box);
        MaterialButton addBtn = makeRowButton(row, "+1", 1f);
        MaterialButton subBtn = makeRowButton(row, "-1", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tallyCount++;
                tallyText.setText(String.valueOf(tallyCount));
                vibrateTick();
            }
        });
        subBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tallyCount--;
                tallyText.setText(String.valueOf(tallyCount));
                vibrateTick();
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tallyCount = 0;
                tallyText.setText("0");
            }
        });
    }
    private ToneGenerator getTone() {
        if (toneGenerator == null) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            } catch (Exception ignored) {
            }
        }
        return toneGenerator;
    }
    private void buildMetronome(LinearLayout box) {
        addTitle(box, "Metronome");
        final TextView bpmLabel = addLabel(box, "Tempo: 120 BPM");
        SeekBar bpmBar = new SeekBar(this);
        bpmBar.setMax(210);
        bpmBar.setProgress(90);
        box.addView(bpmBar);
        metronomeFlash = new View(this);
        metronomeFlash.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1B73E8")));
        box.addView(metronomeFlash, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        final TextView beatText = makeOutput(box);
        beatText.setGravity(Gravity.CENTER);
        beatText.setText("Stopped");
        bpmBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                metronomeBpm = 30 + progress;
                bpmLabel.setText("Tempo: " + metronomeBpm + " BPM");
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        final MaterialButton toggleBtn = makeButton(box, "Start");
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (metronomeRunning) {
                    metronomeRunning = false;
                    toggleBtn.setText("Start");
                    beatText.setText("Stopped");
                    return;
                }
                metronomeRunning = true;
                metronomeBeat = 0;
                toggleBtn.setText("Stop");
                if (metronomeTick == null) {
                    metronomeTick = new Runnable() {
                        public void run() {
                            if (!metronomeRunning) {
                                return;
                            }
                            metronomeBeat++;
                            int beat = ((metronomeBeat - 1) % 4) + 1;
                            beatText.setText("Beat " + beat + " of 4");
                            try {
                                ToneGenerator tg = getTone();
                                if (tg != null) {
                                    tg.startTone(beat == 1 ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_BEEP2, 90);
                                }
                            } catch (Exception ignored) {
                            }
                            try {
                                int idle = MaterialColors.getColor(ToolRunnerActivity.this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1B73E8"));
                                metronomeFlash.setBackgroundColor(beat == 1 ? Color.parseColor("#D93025") : idle);
                            } catch (Exception ignored) {
                            }
                            long interval = 60000L / Math.max(30, metronomeBpm);
                            handler.postDelayed(this, interval);
                        }
                    };
                }
                handler.post(metronomeTick);
            }
        });
    }
    private void buildDeviceInfo(LinearLayout box) {
        addTitle(box, "Device Info");
        TextView output = makeOutput(box);
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            StringBuilder b = new StringBuilder();
            b.append("Model: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            b.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            b.append("Board: ").append(Build.BOARD).append("  Brand: ").append(Build.BRAND).append("\n");
            b.append("Device: ").append(Build.DEVICE).append("  Product: ").append(Build.PRODUCT).append("\n");
            b.append("Screen: ").append(dm.widthPixels).append(" x ").append(dm.heightPixels).append("  density ").append(dm.densityDpi).append("\n");
            b.append("xdpi ").append(new DecimalFormat("0.0").format(dm.xdpi)).append("  ydpi ").append(new DecimalFormat("0.0").format(dm.ydpi)).append("\n");
            Runtime rt = Runtime.getRuntime();
            b.append("Heap: ").append(rt.totalMemory() / 1048576L).append(" MB  free ").append(rt.freeMemory() / 1048576L).append(" MB\n");
            if (sensorManager != null) {
                List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                b.append("Sensors: ").append(sensors.size()).append("\n");
                int shown = 0;
                for (Sensor s : sensors) {
                    if (shown >= 20) {
                        break;
                    }
                    b.append("- ").append(s.getName()).append("\n");
                    shown++;
                }
            }
            output.setText(b.toString());
        } catch (Exception e) {
            output.setText("Unavailable");
        }
        MaterialButton copyBtn = makeButton(box, "Copy info");
        final TextView outRef = output;
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("device", outRef.getText().toString());
            }
        });
    }
    private void buildNetInfo(LinearLayout box) {
        addTitle(box, "Network Info");
        TextView output = makeOutput(box);
        try {
            StringBuilder b = new StringBuilder();
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    NetworkInfo active = cm.getActiveNetworkInfo();
                    if (active != null) {
                        b.append("Active: ").append(active.getTypeName()).append(" connected=").append(active.isConnected()).append("\n");
                    } else {
                        b.append("Active: none\n");
                    }
                } catch (Exception e) {
                    b.append("Active: unknown\n");
                }
            }
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    b.append("Wi-Fi enabled: ").append(wm.isWifiEnabled()).append("\n");
                    WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        String ssid = info.getSSID() == null ? "-" : info.getSSID().replace("\"", "");
                        b.append("SSID: ").append(ssid).append("\n");
                        b.append("BSSID: ").append(info.getBSSID()).append("\n");
                        b.append("Link speed: ").append(info.getLinkSpeed()).append(" Mbps\n");
                        b.append("RSSI: ").append(info.getRssi()).append(" dBm\n");
                        int ip = info.getIpAddress();
                        b.append("IP: ").append(ipToString(ip)).append("\n");
                    }
                    DhcpInfo dhcp = wm.getDhcpInfo();
                    if (dhcp != null) {
                        b.append("Gateway: ").append(ipToString(dhcp.gateway)).append("\n");
                        b.append("DNS1: ").append(ipToString(dhcp.dns1)).append("\n");
                        b.append("DNS2: ").append(ipToString(dhcp.dns2)).append("\n");
                    }
                }
            } catch (Exception e) {
                b.append("Wi-Fi: unavailable\n");
            }
            output.setText(b.toString());
        } catch (Exception e) {
            output.setText("Unavailable");
        }
        MaterialButton refreshBtn = makeButton(box, "Refresh");
        final TextView outRef = output;
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                box.removeAllViews();
                onDestroyCleanupForRefresh();
                buildNetInfo(box);
                addTitle(box, "Refreshed");
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy info");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("network", outRef.getText().toString());
            }
        });
    }
    private void onDestroyCleanupForRefresh() {
    }
    private String ipToString(int ip) {
        return (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255);
    }
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return new DecimalFormat("0.0").format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return new DecimalFormat("0.0").format(mb) + " MB";
        }
        double gb = mb / 1024.0;
        if (gb < 1024) {
            return new DecimalFormat("0.00").format(gb) + " GB";
        }
        return new DecimalFormat("0.00").format(gb / 1024.0) + " TB";
    }
    private void buildWorldClock(LinearLayout box) {
        addTitle(box, "World Clock");
        final String[] zones = new String[]{"America/New_York", "America/Chicago", "America/Los_Angeles", "Europe/London", "Europe/Berlin", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland"};
        final TextView clocks = makeOutput(box);
        final Runnable ticker = new Runnable() {
            public void run() {
                try {
                    StringBuilder b = new StringBuilder();
                    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
                    SimpleDateFormat d = new SimpleDateFormat("EEE dd MMM", Locale.US);
                    Date now = new Date();
                    for (String z : zones) {
                        java.util.TimeZone tz = java.util.TimeZone.getTimeZone(z);
                        f.setTimeZone(tz);
                        d.setTimeZone(tz);
                        String shortName = z.substring(z.indexOf(47) + 1).replace("_", " ");
                        b.append(shortName).append("  ").append(f.format(now)).append("  ").append(d.format(now)).append("\n");
                    }
                    clocks.setText(b.toString().trim());
                } catch (Exception ignored) {
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ticker);
        addTitle(box, "Zone Converter");
        final EditText timeInput = makeInput(box, "HH:mm, e.g. 14:30", InputType.TYPE_CLASS_DATETIME);
        timeInput.setText("12:00");
        final Spinner fromZone = new Spinner(this);
        final Spinner toZone = new Spinner(this);
        ArrayAdapter<String> zoneAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, zones);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromZone.setAdapter(zoneAdapter);
        toZone.setAdapter(zoneAdapter);
        toZone.setSelection(5);
        box.addView(fromZone);
        box.addView(toZone);
        final TextView convOut = makeOutput(box);
        MaterialButton convBtn = makeButton(box, "Convert");
        convBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String[] parts = timeInput.getText().toString().trim().split(":");
                    int hh = Integer.parseInt(parts[0].trim());
                    int mm = Integer.parseInt(parts[1].trim());
                    Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone(fromZone.getSelectedItem().toString()));
                    c.set(Calendar.HOUR_OF_DAY, hh);
                    c.set(Calendar.MINUTE, mm);
                    c.set(Calendar.SECOND, 0);
                    SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                    f.setTimeZone(java.util.TimeZone.getTimeZone(toZone.getSelectedItem().toString()));
                    convOut.setText(f.format(c.getTime()) + " in " + toZone.getSelectedItem().toString());
                } catch (Exception e) {
                    convOut.setText("Use HH:mm");
                }
            }
        });
    }
    private void buildCurrency(LinearLayout box) {
        addTitle(box, "Currency Converter");
        addLabel(box, "Indicative offline rates, base USD.");
        final String[] codes = new String[]{"USD", "EUR", "GBP", "JPY", "INR", "CNY", "AED", "SAR", "PKR", "BDT", "CAD", "AUD"};
        final double[] perUsd = new double[]{1.0, 0.92, 0.79, 149.5, 83.2, 7.24, 3.67, 3.75, 278.0, 117.0, 1.36, 1.52};
        final Spinner fromCur = new Spinner(this);
        final Spinner toCur = new Spinner(this);
        ArrayAdapter<String> curAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, codes);
        curAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromCur.setAdapter(curAdapter);
        toCur.setAdapter(curAdapter);
        toCur.setSelection(1);
        box.addView(fromCur);
        box.addView(toCur);
        final EditText amount = makeInput(box, "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText("100");
        final TextView output = makeOutput(box);
        Runnable compute = new Runnable() {
            public void run() {
            }
        };
        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                convertCurrency(codes, perUsd, fromCur, toCur, amount, output);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        fromCur.setOnItemSelectedListener(listener);
        toCur.setOnItemSelectedListener(listener);
        amount.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                convertCurrency(codes, perUsd, fromCur, toCur, amount, output);
            }
            public void afterTextChanged(Editable s) {
            }
        });
        MaterialButton swapBtn = makeButton(box, "Swap");
        swapBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int f = fromCur.getSelectedItemPosition();
                int t = toCur.getSelectedItemPosition();
                fromCur.setSelection(t);
                toCur.setSelection(f);
            }
        });
        convertCurrency(codes, perUsd, fromCur, toCur, amount, output);
    }
    private void convertCurrency(String[] codes, double[] perUsd, Spinner from, Spinner to, EditText amount, TextView output) {
        try {
            double v = Double.parseDouble(amount.getText().toString().trim());
            int fi = from.getSelectedItemPosition();
            int ti = to.getSelectedItemPosition();
            double usd = v / perUsd[fi];
            double result = usd * perUsd[ti];
            DecimalFormat df = new DecimalFormat("0.##");
            output.setText(df.format(v) + " " + codes[fi] + " = " + df.format(result) + " " + codes[ti]);
        } catch (Exception e) {
            output.setText("Enter amount");
        }
    }
    private void buildTip(LinearLayout box) {
        addTitle(box, "Tip Calculator");
        final EditText billInput = makeInput(box, "Bill amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView tipLabel = addLabel(box, "Tip: 15%");
        SeekBar tipBar = new SeekBar(this);
        tipBar.setMax(40);
        tipBar.setProgress(15);
        box.addView(tipBar);
        final TextView peopleLabel = addLabel(box, "People: 1");
        SeekBar peopleBar = new SeekBar(this);
        peopleBar.setMax(19);
        peopleBar.setProgress(0);
        box.addView(peopleBar);
        final TextView output = makeOutput(box);
        final int[] tipPct = new int[]{15};
        final int[] people = new int[]{1};
        Runnable compute = new Runnable() {
            public void run() {
                try {
                    double bill = Double.parseDouble(billInput.getText().toString());
                    double tip = bill * tipPct[0] / 100.0;
                    double total = bill + tip;
                    DecimalFormat df = new DecimalFormat("0.00");
                    output.setText("Tip " + df.format(tip) + "  Total " + df.format(total) + "  Each " + df.format(total / people[0]));
                } catch (Exception e) {
                    output.setText("Enter bill amount");
                }
            }
        };
        tipBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tipPct[0] = progress;
                tipLabel.setText("Tip: " + progress + "%");
                compute.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        peopleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                people[0] = 1 + progress;
                peopleLabel.setText("People: " + people[0]);
                compute.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        billInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
    }
    private void buildGpa(LinearLayout box) {
        addTitle(box, "GPA Calculator");
        final String[] grades = new String[]{"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F"};
        final double[] points = new double[]{4.0, 4.0, 3.7, 3.3, 3.0, 2.7, 2.3, 2.0, 1.7, 1.0, 0.0};
        final LinearLayout rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(rowsBox);
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    double totalPoints = 0;
                    double totalCredits = 0;
                    for (int i = 0; i < rowsBox.getChildCount(); i++) {
                        LinearLayout row = (LinearLayout) rowsBox.getChildAt(i);
                        Spinner g = (Spinner) row.getChildAt(0);
                        EditText c = (EditText) row.getChildAt(1);
                        String cs = c.getText().toString().trim();
                        if (cs.isEmpty()) {
                            continue;
                        }
                        double credits = Double.parseDouble(cs);
                        totalPoints += points[g.getSelectedItemPosition()] * credits;
                        totalCredits += credits;
                    }
                    if (totalCredits <= 0) {
                        output.setText("Add courses with credits");
                        return;
                    }
                    output.setText("GPA " + new DecimalFormat("0.00").format(totalPoints / totalCredits) + "  Credits " + new DecimalFormat("0.#").format(totalCredits));
                } catch (Exception e) {
                    output.setText("Check credits");
                }
            }
        };
        final android.widget.AdapterView.OnItemSelectedListener gradeListener = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                compute.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        Runnable addRow = new Runnable() {
            public void run() {
            }
        };
        final Runnable addCourseRow = new Runnable() {
            public void run() {
                LinearLayout row = new LinearLayout(ToolRunnerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                Spinner g = new Spinner(ToolRunnerActivity.this);
                ArrayAdapter<String> ga = new ArrayAdapter<>(ToolRunnerActivity.this, android.R.layout.simple_spinner_item, grades);
                ga.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                g.setAdapter(ga);
                g.setSelection(1);
                g.setOnItemSelectedListener(gradeListener);
                row.addView(g, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                final EditText c = makeRowInput(row, "Credits", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 1f, "3");
                c.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        compute.run();
                    }
                    public void afterTextChanged(Editable s) {
                    }
                });
                MaterialButton del = new MaterialButton(ToolRunnerActivity.this);
                del.setText("X");
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        rowsBox.removeView(row);
                        compute.run();
                    }
                });
                row.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
                rowsBox.addView(row);
                compute.run();
            }
        };
        addCourseRow.run();
        addCourseRow.run();
        addCourseRow.run();
        MaterialButton addBtn = makeButton(box, "Add course");
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addCourseRow.run();
            }
        });
    }
    private void buildPomodoro(LinearLayout box) {
        addTitle(box, "Pomodoro Timer");
        final EditText focusInput = makeInput(box, "Focus minutes", InputType.TYPE_CLASS_NUMBER);
        focusInput.setText("25");
        final EditText breakInput = makeInput(box, "Break minutes", InputType.TYPE_CLASS_NUMBER);
        breakInput.setText("5");
        pomoText = makeOutput(box);
        pomoText.setTextSize(32);
        pomoText.setGravity(Gravity.CENTER);
        pomoText.setText("Ready");
        pomoPhase = 0;
        pomoCycle = 1;
        LinearLayout row = makeRow(box);
        MaterialButton startBtn = makeRowButton(row, "Start", 1f);
        MaterialButton skipBtn = makeRowButton(row, "Skip", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pomoRunning) {
                    if (pomoTimer != null) {
                        pomoTimer.cancel();
                    }
                    pomoRunning = false;
                    ((Button) v).setText("Start");
                    return;
                }
                long focusMs = parseLongSafe(focusInput.getText().toString()) * 60000L;
                long breakMs = parseLongSafe(breakInput.getText().toString()) * 60000L;
                if (focusMs <= 0 || breakMs <= 0) {
                    toast("Enter minutes");
                    return;
                }
                pomoRunning = true;
                ((Button) v).setText("Pause");
                startPomoPhase(focusMs, breakMs);
            }
        });
        skipBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pomoTimer != null) {
                    pomoTimer.cancel();
                }
                long focusMs = parseLongSafe(focusInput.getText().toString()) * 60000L;
                long breakMs = parseLongSafe(breakInput.getText().toString()) * 60000L;
                advancePomoPhase(focusMs, breakMs);
                if (pomoRunning) {
                    startPomoPhase(focusMs, breakMs);
                }
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pomoTimer != null) {
                    pomoTimer.cancel();
                }
                pomoRunning = false;
                pomoPhase = 0;
                pomoCycle = 1;
                pomoText.setText("Ready");
                startBtn.setText("Start");
            }
        });
    }
    private void startPomoPhase(final long focusMs, final long breakMs) {
        final long duration = pomoPhase == 0 ? focusMs : breakMs;
        if (pomoTimer != null) {
            try {
                pomoTimer.cancel();
            } catch (Exception ignored) {
            }
        }
        pomoTimer = new CountDownTimer(duration, 500) {
            public void onTick(long left) {
                pomoLeft = left;
                String label = pomoPhase == 0 ? "Focus " + pomoCycle : "Break";
                pomoText.setText(label + "\n" + formatTimer(left));
            }
            public void onFinish() {
                beep();
                vibrateTick();
                advancePomoPhase(focusMs, breakMs);
                if (pomoRunning) {
                    startPomoPhase(focusMs, breakMs);
                }
            }
        };
        pomoTimer.start();
    }
    private void advancePomoPhase(long focusMs, long breakMs) {
        if (pomoPhase == 0) {
            pomoPhase = 1;
        } else {
            pomoPhase = 0;
            pomoCycle++;
        }
    }
    private void buildHiit(LinearLayout box) {
        addTitle(box, "Interval Timer");
        final EditText workInput = makeInput(box, "Work seconds", InputType.TYPE_CLASS_NUMBER);
        workInput.setText("30");
        final EditText restInput = makeInput(box, "Rest seconds", InputType.TYPE_CLASS_NUMBER);
        restInput.setText("10");
        final EditText roundsInput = makeInput(box, "Rounds", InputType.TYPE_CLASS_NUMBER);
        roundsInput.setText("8");
        final TextView output = makeOutput(box);
        output.setTextSize(28);
        output.setGravity(Gravity.CENTER);
        output.setText("Ready");
        LinearLayout row = makeRow(box);
        MaterialButton startBtn = makeRowButton(row, "Start", 1f);
        MaterialButton stopBtn = makeRowButton(row, "Stop", 1f);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (hiitRunning) {
                    return;
                }
                long work = parseLongSafe(workInput.getText().toString()) * 1000L;
                long rest = parseLongSafe(restInput.getText().toString()) * 1000L;
                int rounds = (int) parseLongSafe(roundsInput.getText().toString());
                if (work <= 0 || rest <= 0 || rounds <= 0) {
                    toast("Enter work, rest and rounds");
                    return;
                }
                hiitRunning = true;
                runHiitRound(output, work, rest, rounds, 1, true);
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hiitRunning = false;
                if (hiitTimer != null) {
                    try {
                        hiitTimer.cancel();
                    } catch (Exception ignored) {
                    }
                }
                output.setText("Stopped");
            }
        });
    }
    private void runHiitRound(final TextView output, final long work, final long rest, final int rounds, final int current, final boolean isWork) {
        if (!hiitRunning || current > rounds) {
            hiitRunning = false;
            output.setText("Done");
            beep();
            return;
        }
        final long duration = isWork ? work : rest;
        final String label = (isWork ? "WORK " : "REST ") + current + " of " + rounds;
        if (hiitTimer != null) {
            try {
                hiitTimer.cancel();
            } catch (Exception ignored) {
            }
        }
        hiitTimer = new CountDownTimer(duration, 250) {
            public void onTick(long left) {
                output.setText(label + "\n" + (left / 1000 + 1) + "s");
            }
            public void onFinish() {
                beep();
                if (isWork) {
                    runHiitRound(output, work, rest, rounds, current, false);
                } else {
                    runHiitRound(output, work, rest, rounds, current + 1, true);
                }
            }
        };
        hiitTimer.start();
    }
    private void buildWheel(LinearLayout box) {
        addTitle(box, "Decision Wheel");
        final EditText optionsInput = makeInput(box, "Options separated by commas", InputType.TYPE_CLASS_TEXT);
        optionsInput.setText("Pizza, Burger, Sushi, Tacos");
        final WheelView wheelView = new WheelView(this);
        box.addView(wheelView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        final TextView result = makeOutput(box);
        result.setGravity(Gravity.CENTER);
        result.setText("Tap Spin");
        optionsInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                wheelView.setOptions(parseWheelOptions(s.toString()));
            }
            public void afterTextChanged(Editable s) {
            }
        });
        wheelView.setOptions(parseWheelOptions(optionsInput.getText().toString()));
        wheelView.setListener(new WheelView.WheelListener() {
            public void onResult(String name) {
                result.setText(name);
                vibrateTick();
            }
        });
        MaterialButton spinBtn = makeButton(box, "Spin");
        spinBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                wheelView.setOptions(parseWheelOptions(optionsInput.getText().toString()));
                wheelView.spin();
            }
        });
    }
    private List<String> parseWheelOptions(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            out.add("A");
            out.add("B");
        }
        return out;
    }
    private static class WheelView extends View {
        interface WheelListener {
            void onResult(String name);
        }
        private List<String> options = new ArrayList<>();
        private float rotation = 0f;
        private boolean spinning = false;
        private WheelListener listener;
        private Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] palette = new int[]{Color.parseColor("#1B73E8"), Color.parseColor("#0D652D"), Color.parseColor("#B06000"), Color.parseColor("#A50E0E"), Color.parseColor("#681DA8"), Color.parseColor("#00696B")};
        private int bgWheel = Color.WHITE;
        public WheelView(Context context) {
            super(context);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(34f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            pointerPaint.setColor(Color.parseColor("#D93025"));
            try {
                bgWheel = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
        }
        void setOptions(List<String> opts) {
            options = new ArrayList<>(opts);
            invalidate();
        }
        void setListener(WheelListener l) {
            listener = l;
        }
        void spin() {
            if (spinning || options.size() < 2) {
                return;
            }
            spinning = true;
            final float start = rotation;
            final float target = start + 1080f + (float) (Math.random() * 360f);
            final long begin = SystemClock.elapsedRealtime();
            final long duration = 3200L;
            final android.os.Handler animHandler = new android.os.Handler(Looper.getMainLooper());
            Runnable frame = new Runnable() {
                public void run() {
                    float t = Math.min(1f, (SystemClock.elapsedRealtime() - begin) / (float) duration);
                    float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                    rotation = start + (target - start) * eased;
                    invalidate();
                    if (t < 1f && spinning) {
                        animHandler.postDelayed(this, 16);
                    } else {
                        spinning = false;
                        if (listener != null) {
                            listener.onResult(currentWinner());
                        }
                        invalidate();
                    }
                }
            };
            animHandler.post(frame);
        }
        private String currentWinner() {
            if (options.isEmpty()) {
                return "";
            }
            float normalized = ((rotation % 360) + 360) % 360;
            float pointerAngle = (270f - normalized + 360f) % 360f;
            float sweep = 360f / options.size();
            int idx = (int) (pointerAngle / sweep) % options.size();
            return options.get(idx);
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgWheel);
            if (options.isEmpty()) {
                return;
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - 16f;
            android.graphics.RectF oval = new android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            float sweep = 360f / options.size();
            canvas.save();
            canvas.rotate(rotation, cx, cy);
            for (int i = 0; i < options.size(); i++) {
                slicePaint.setColor(palette[i % palette.length]);
                canvas.drawArc(oval, i * sweep - 90, sweep, true, slicePaint);
            }
            for (int i = 0; i < options.size(); i++) {
                canvas.save();
                canvas.rotate(i * sweep + sweep / 2f, cx, cy);
                canvas.drawText(options.get(i), cx, cy - radius + 60, textPaint);
                canvas.restore();
            }
            canvas.restore();
            float[] pointer = new float[]{cx - 24, 8, cx + 24, 8, cx, 64};
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 6, pointer, 0, null, 0, null, 0, null, 0, 0, pointerPaint);
        }
    }
    private void buildCaseConv(LinearLayout box) {
        addTitle(box, "Case Converter");
        final EditText input = makeInput(box, "Text", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        final TextView output = makeOutput(box);
        output.setText("Result");
        LinearLayout row1 = makeRow(box);
        MaterialButton upperBtn = makeRowButton(row1, "UPPER", 1f);
        MaterialButton lowerBtn = makeRowButton(row1, "lower", 1f);
        LinearLayout row2 = makeRow(box);
        MaterialButton titleBtn = makeRowButton(row2, "Title", 1f);
        MaterialButton sentenceBtn = makeRowButton(row2, "Sentence", 1f);
        LinearLayout row3 = makeRow(box);
        MaterialButton altBtn = makeRowButton(row3, "aLtErNaTe", 1f);
        MaterialButton reverseBtn = makeRowButton(row3, "Reverse", 1f);
        upperBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(input.getText().toString().toUpperCase(Locale.US));
            }
        });
        lowerBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(input.getText().toString().toLowerCase(Locale.US));
            }
        });
        titleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(toTitleCase(input.getText().toString()));
            }
        });
        sentenceBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(toSentenceCase(input.getText().toString()));
            }
        });
        altBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = input.getText().toString();
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    b.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
                }
                output.setText(b.toString());
            }
        });
        reverseBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(new StringBuilder(input.getText().toString()).reverse().toString());
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy result");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("case", output.getText().toString());
            }
        });
    }
    private String toTitleCase(String s) {
        StringBuilder b = new StringBuilder();
        boolean nextUp = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                nextUp = true;
                b.append(c);
            } else if (nextUp) {
                b.append(Character.toUpperCase(c));
                nextUp = false;
            } else {
                b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }
    private String toSentenceCase(String s) {
        String lower = s.toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder(lower);
        boolean nextUp = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (nextUp && Character.isLetter(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
                nextUp = false;
            }
            if (c == '.' || c == '!' || c == '?') {
                nextUp = true;
            }
        }
        return b.toString();
    }
    private java.util.Map<String, String> morseEncodeMap() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("A", ".-");
        m.put("B", "-...");
        m.put("C", "-.-.");
        m.put("D", "-..");
        m.put("E", ".");
        m.put("F", "..-.");
        m.put("G", "--.");
        m.put("H", "....");
        m.put("I", "..");
        m.put("J", ".---");
        m.put("K", "-.-");
        m.put("L", ".-..");
        m.put("M", "--");
        m.put("N", "-.");
        m.put("O", "---");
        m.put("P", ".--.");
        m.put("Q", "--.-");
        m.put("R", ".-.");
        m.put("S", "...");
        m.put("T", "-");
        m.put("U", "..-");
        m.put("V", "...-");
        m.put("W", ".--");
        m.put("X", "-..-");
        m.put("Y", "-.--");
        m.put("Z", "--..");
        m.put("0", "-----");
        m.put("1", ".----");
        m.put("2", "..---");
        m.put("3", "...--");
        m.put("4", "....-");
        m.put("5", ".....");
        m.put("6", "-....");
        m.put("7", "--...");
        m.put("8", "---..");
        m.put("9", "----.");
        return m;
    }
    private void buildMorse(LinearLayout box) {
        addTitle(box, "Morse Code");
        final java.util.Map<String, String> enc = morseEncodeMap();
        final java.util.Map<String, String> dec = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : enc.entrySet()) {
            dec.put(e.getValue(), e.getKey());
        }
        final EditText input = makeInput(box, "Text or morse", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final TextView output = makeOutput(box);
        output.setText("Result");
        LinearLayout row = makeRow(box);
        MaterialButton encBtn = makeRowButton(row, "Encode", 1f);
        MaterialButton decBtn = makeRowButton(row, "Decode", 1f);
        encBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = input.getText().toString().toUpperCase(Locale.US);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == ' ') {
                        b.append("  ");
                    } else {
                        String code = enc.get(String.valueOf(c));
                        if (code != null) {
                            if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                                b.append(' ');
                            }
                            b.append(code);
                        }
                    }
                }
                output.setText(b.toString().trim());
            }
        });
        decBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = input.getText().toString().trim();
                StringBuilder b = new StringBuilder();
                for (String word : s.split("   ")) {
                    for (String code : word.trim().split(" ")) {
                        String letter = dec.get(code.trim());
                        if (letter != null) {
                            b.append(letter);
                        }
                    }
                    b.append(' ');
                }
                output.setText(b.toString().trim());
            }
        });
        LinearLayout row2 = makeRow(box);
        MaterialButton playBtn = makeRowButton(row2, "Play", 1f);
        MaterialButton copyBtn = makeRowButton(row2, "Copy", 1f);
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String code = output.getText().toString();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                            for (int i = 0; i < code.length(); i++) {
                                char c = code.charAt(i);
                                if (c == '.') {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                                    Thread.sleep(200);
                                } else if (c == '-') {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 360);
                                    Thread.sleep(440);
                                } else {
                                    Thread.sleep(240);
                                }
                            }
                            tg.release();
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            }
        });
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("morse", output.getText().toString());
            }
        });
    }
    private void buildBaseConv(LinearLayout box) {
        addTitle(box, "Base Converter");
        final EditText input = makeInput(box, "Number", InputType.TYPE_CLASS_TEXT);
        input.setText("255");
        final String[] bases = new String[]{"Binary (2)", "Octal (8)", "Decimal (10)", "Hex (16)"};
        final int[] radix = new int[]{2, 8, 10, 16};
        final Spinner fromBase = new Spinner(this);
        ArrayAdapter<String> baseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bases);
        baseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromBase.setAdapter(baseAdapter);
        fromBase.setSelection(2);
        box.addView(fromBase);
        final TextView output = makeOutput(box);
        Runnable compute = new Runnable() {
            public void run() {
                try {
                    String s = input.getText().toString().trim().replace("0x", "").replace("0X", "");
                    long v = Long.parseLong(s, radix[fromBase.getSelectedItemPosition()]);
                    StringBuilder b = new StringBuilder();
                    b.append("Bin: ").append(Long.toBinaryString(v)).append("\n");
                    b.append("Oct: ").append(Long.toOctalString(v)).append("\n");
                    b.append("Dec: ").append(v).append("\n");
                    b.append("Hex: ").append(Long.toHexString(v).toUpperCase(Locale.US)).append("\n");
                    b.append("Bits: ").append(v == 0 ? 1 : (64 - Long.numberOfLeadingZeros(v)));
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Invalid for selected base");
                }
            }
        };
        final Runnable computeRef = compute;
        fromBase.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                computeRef.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                computeRef.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
    }
    private void buildFuel(LinearLayout box) {
        addTitle(box, "Fuel Calculator");
        final EditText distInput = makeInput(box, "Distance in km", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText fuelInput = makeInput(box, "Fuel used in liters", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText priceInput = makeInput(box, "Price per liter (optional)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double dist = Double.parseDouble(distInput.getText().toString());
                    double fuel = Double.parseDouble(fuelInput.getText().toString());
                    double per100 = fuel / dist * 100.0;
                    double kml = dist / fuel;
                    double mpg = kml * 2.35215;
                    DecimalFormat df = new DecimalFormat("0.00");
                    StringBuilder b = new StringBuilder();
                    b.append("Consumption ").append(df.format(per100)).append(" L/100km\n");
                    b.append("Economy ").append(df.format(kml)).append(" km/L  (").append(df.format(mpg)).append(" mpg)\n");
                    String ps = priceInput.getText().toString().trim();
                    if (!ps.isEmpty()) {
                        double price = Double.parseDouble(ps);
                        b.append("Trip cost ").append(df.format(fuel * price)).append("  (").append(df.format(fuel * price / dist)).append(" per km)");
                    }
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Enter distance and fuel");
                }
            }
        });
    }
    private void buildOhm(LinearLayout box) {
        addTitle(box, "Ohm Law Solver");
        addLabel(box, "Fill any two values, leave the rest empty.");
        final EditText vInput = makeInput(box, "Voltage V", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText iInput = makeInput(box, "Current A", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText rInput = makeInput(box, "Resistance Ohm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText pInput = makeInput(box, "Power W", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Solve");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Double V = parseDoubleOrNull(vInput.getText().toString());
                    Double I = parseDoubleOrNull(iInput.getText().toString());
                    Double R = parseDoubleOrNull(rInput.getText().toString());
                    Double P = parseDoubleOrNull(pInput.getText().toString());
                    for (int k = 0; k < 6; k++) {
                        if (V == null && I != null && R != null) {
                            V = I * R;
                        }
                        if (V == null && P != null && I != null && I != 0) {
                            V = P / I;
                        }
                        if (V == null && P != null && R != null && R > 0) {
                            V = Math.sqrt(P * R);
                        }
                        if (I == null && V != null && R != null && R != 0) {
                            I = V / R;
                        }
                        if (I == null && P != null && V != null && V != 0) {
                            I = P / V;
                        }
                        if (I == null && P != null && R != null && R > 0) {
                            I = Math.sqrt(P / R);
                        }
                        if (R == null && V != null && I != null && I != 0) {
                            R = V / I;
                        }
                        if (R == null && V != null && P != null && P != 0) {
                            R = V * V / P;
                        }
                        if (R == null && P != null && I != null && I != 0) {
                            R = P / (I * I);
                        }
                        if (P == null && V != null && I != null) {
                            P = V * I;
                        }
                        if (P == null && V != null && R != null && R != 0) {
                            P = V * V / R;
                        }
                        if (P == null && I != null && R != null) {
                            P = I * I * R;
                        }
                    }
                    DecimalFormat df = new DecimalFormat("0.####");
                    output.setText("V=" + fmtNull(V, df) + "  I=" + fmtNull(I, df) + "  R=" + fmtNull(R, df) + "  P=" + fmtNull(P, df));
                } catch (Exception e) {
                    output.setText("Enter at least two values");
                }
            }
        });
    }
    private Double parseDoubleOrNull(String s) {
        try {
            s = s.trim();
            if (s.isEmpty()) {
                return null;
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }
    private String fmtNull(Double v, DecimalFormat df) {
        return v == null ? "-" : df.format(v);
    }
    private void buildResistor(LinearLayout box) {
        addTitle(box, "Resistor Decoder");
        final String[] colors = new String[]{"Black", "Brown", "Red", "Orange", "Yellow", "Green", "Blue", "Violet", "Gray", "White", "Gold", "Silver"};
        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton fourBtn = new RadioButton(this);
        fourBtn.setId(View.generateViewId());
        fourBtn.setText("4-band");
        RadioButton fiveBtn = new RadioButton(this);
        fiveBtn.setId(View.generateViewId());
        fiveBtn.setText("5-band");
        modeGroup.addView(fourBtn);
        modeGroup.addView(fiveBtn);
        modeGroup.check(fourBtn.getId());
        box.addView(modeGroup);
        final LinearLayout bandsBox = new LinearLayout(this);
        bandsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(bandsBox);
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
            }
        };
        final Runnable buildBands = new Runnable() {
            public void run() {
            }
        };
        final int[] bandCount = new int[]{4};
        final Runnable computeValue = new Runnable() {
            public void run() {
                try {
                    int n = bandsBox.getChildCount();
                    List<Integer> digits = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        Spinner s = (Spinner) bandsBox.getChildAt(i);
                        digits.add(s.getSelectedItemPosition());
                    }
                    int[] digitVal = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2};
                    double[] tolMap = new double[]{20, 1, 2, 20, 20, 0.5, 0.25, 0.1, 0.05, 20, 5, 10};
                    double value;
                    double tol;
                    if (bandCount[0] == 4) {
                        int d1 = digitVal[digits.get(0)];
                        int d2 = digitVal[digits.get(1)];
                        int mult = digitVal[digits.get(2)];
                        tol = tolMap[digits.get(3)];
                        value = (d1 * 10 + d2) * Math.pow(10, mult);
                    } else {
                        int d1 = digitVal[digits.get(0)];
                        int d2 = digitVal[digits.get(1)];
                        int d3 = digitVal[digits.get(2)];
                        int mult = digitVal[digits.get(3)];
                        tol = tolMap[digits.get(4)];
                        value = (d1 * 100 + d2 * 10 + d3) * Math.pow(10, mult);
                    }
                    output.setText(formatOhms(value) + "  Tol " + tol + "%");
                } catch (Exception e) {
                    output.setText("Pick band colors");
                }
            }
        };
        final android.widget.AdapterView.OnItemSelectedListener bandListener = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                computeValue.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        final Runnable rebuild = new Runnable() {
            public void run() {
                bandsBox.removeAllViews();
                int count = bandCount[0] == 4 ? 4 : 5;
                String[] labels = bandCount[0] == 4 ? new String[]{"Digit 1", "Digit 2", "Multiplier", "Tolerance"} : new String[]{"Digit 1", "Digit 2", "Digit 3", "Multiplier", "Tolerance"};
                int[] defaults = bandCount[0] == 4 ? new int[]{2, 7, 3, 10} : new int[]{2, 7, 3, 3, 10};
                for (int i = 0; i < count; i++) {
                    addLabel(bandsBox, labels[i]);
                    Spinner s = new Spinner(ToolRunnerActivity.this);
                    ArrayAdapter<String> ca = new ArrayAdapter<>(ToolRunnerActivity.this, android.R.layout.simple_spinner_item, colors);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    s.setAdapter(ca);
                    s.setSelection(defaults[i]);
                    s.setOnItemSelectedListener(bandListener);
                    bandsBox.addView(s);
                }
                computeValue.run();
            }
        };
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                bandCount[0] = (checkedId == fiveBtn.getId()) ? 5 : 4;
                rebuild.run();
            }
        });
        rebuild.run();
    }
    private String formatOhms(double v) {
        DecimalFormat df = new DecimalFormat("0.##");
        if (v >= 1000000) {
            return df.format(v / 1000000.0) + " MOhm";
        } else if (v >= 1000) {
            return df.format(v / 1000.0) + " kOhm";
        }
        return df.format(v) + " Ohm";
    }
    private void buildNotes(LinearLayout box) {
        addTitle(box, "Quick Notes");
        final Gson gson = new Gson();
        final String prefsKey = "quick_notes_json";
        final List<String> notes = new ArrayList<>();
        try {
            String saved = getSharedPreferences("tools", MODE_PRIVATE).getString(prefsKey, "[]");
            List<String> loaded = gson.fromJson(saved, new TypeToken<List<String>>() {
            }.getType());
            if (loaded != null) {
                notes.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
        final EditText input = makeInput(box, "Write a note", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        final android.widget.ListView listView = new android.widget.ListView(this);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, notes);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        final Runnable persist = new Runnable() {
            public void run() {
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString(prefsKey, gson.toJson(notes)).apply();
                } catch (Exception ignored) {
                }
            }
        };
        MaterialButton addBtn = makeButton(box, "Save note");
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.isEmpty()) {
                    toast("Write something first");
                    return;
                }
                notes.add(0, t);
                input.setText("");
                adapter.notifyDataSetChanged();
                persist.run();
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                notes.remove(position);
                adapter.notifyDataSetChanged();
                persist.run();
                return true;
            }
        });
        addLabel(box, "Long-press a note to delete it.");
    }
    private static class CheckItem {
        String title;
        boolean done;
        CheckItem(String t, boolean d) {
            title = t;
            done = d;
        }
    }
    private void buildChecklist(LinearLayout box) {
        addTitle(box, "Checklist");
        final Gson gson = new Gson();
        final String prefsKey = "checklist_json";
        final List<CheckItem> items = new ArrayList<>();
        try {
            String saved = getSharedPreferences("tools", MODE_PRIVATE).getString(prefsKey, "[]");
            List<CheckItem> loaded = gson.fromJson(saved, new TypeToken<List<CheckItem>>() {
            }.getType());
            if (loaded != null) {
                items.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
        final EditText input = makeInput(box, "New item", InputType.TYPE_CLASS_TEXT);
        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(listBox);
        final Runnable persist = new Runnable() {
            public void run() {
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString(prefsKey, gson.toJson(items)).apply();
                } catch (Exception ignored) {
                }
            }
        };
        final Runnable refresh = new Runnable() {
            public void run() {
            }
        };
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            public void run() {
                listBox.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    final int idx = i;
                    CheckItem item = items.get(i);
                    LinearLayout row = new LinearLayout(ToolRunnerActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    CheckBox cb = new CheckBox(ToolRunnerActivity.this);
                    cb.setText(item.title);
                    cb.setChecked(item.done);
                    cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                            items.get(idx).done = checked;
                            persist.run();
                        }
                    });
                    row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    MaterialButton del = new MaterialButton(ToolRunnerActivity.this);
                    del.setText("X");
                    del.setOnClickListener(v -> {
                        items.remove(idx);
                        persist.run();
                        render[0].run();
                    });
                    row.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    listBox.addView(row);
                }
            }
        };
        render[0].run();
        MaterialButton addBtn = makeButton(box, "Add item");
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.isEmpty()) {
                    return;
                }
                items.add(new CheckItem(t, false));
                input.setText("");
                persist.run();
                render[0].run();
            }
        });
        MaterialButton clearBtn = makeButton(box, "Clear completed");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    if (items.get(i).done) {
                        items.remove(i);
                    }
                }
                persist.run();
                render[0].run();
            }
        });
    }
    private void stopTone() {
        tonePlaying = false;
        try {
            if (toneThread != null) {
                toneThread.interrupt();
            }
        } catch (Exception ignored) {
        }
        toneThread = null;
        try {
            if (toneTrack != null) {
                toneTrack.stop();
                toneTrack.release();
            }
        } catch (Exception ignored) {
        }
        toneTrack = null;
    }
    private void buildTone(LinearLayout box) {
        addTitle(box, "Tone Generator");
        final TextView freqLabel = addLabel(box, "Frequency: 440 Hz");
        SeekBar freqBar = new SeekBar(this);
        freqBar.setMax(3950);
        freqBar.setProgress(390);
        box.addView(freqBar);
        final int[] freq = new int[]{440};
        freqBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                freq[0] = 50 + progress;
                freqLabel.setText("Frequency: " + freq[0] + " Hz");
                if (tonePlaying) {
                    stopTone();
                    startTone(freq[0]);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout row = makeRow(box);
        MaterialButton playBtn = makeRowButton(row, "Play", 1f);
        MaterialButton stopBtn = makeRowButton(row, "Stop", 1f);
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopTone();
                startTone(freq[0]);
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopTone();
            }
        });
    }
    private void startTone(final int freqHz) {
        try {
            final int sampleRate = 44100;
            int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) {
                minBuf = sampleRate;
            }
            final AudioTrack track;
            if (Build.VERSION.SDK_INT >= 21) {
                track = new AudioTrack.Builder().setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(minBuf, sampleRate)).build();
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, sampleRate), AudioTrack.MODE_STREAM);
            }
            toneTrack = track;
            tonePlaying = true;
            track.play();
            toneThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        double phase = 0;
                        double step = 2.0 * Math.PI * freqHz / sampleRate;
                        short[] buf = new short[2048];
                        while (tonePlaying && !Thread.currentThread().isInterrupted()) {
                            for (int i = 0; i < buf.length; i++) {
                                buf[i] = (short) (Math.sin(phase) * 16000);
                                phase += step;
                                if (phase > 2.0 * Math.PI * 4096) {
                                    phase -= 2.0 * Math.PI * 4096;
                                }
                            }
                            track.write(buf, 0, buf.length);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            toneThread.start();
        } catch (Exception e) {
            toast("Tone failed");
        }
    }
    private void buildRecorder(LinearLayout box) {
        addTitle(box, "Voice Recorder");
        final TextView status = makeOutput(box);
        status.setText("Ready");
        final MaterialButton recBtn = makeButton(box, "Start recording");
        final MaterialButton playBtn = makeButton(box, "Play last");
        final android.widget.ListView listView = new android.widget.ListView(this);
        final List<java.io.File> files = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        final Runnable refreshList = new Runnable() {
            public void run() {
                files.clear();
                names.clear();
                try {
                    java.io.File dir = new java.io.File(getCacheDir(), "recordings");
                    java.io.File[] all = dir.listFiles();
                    if (all != null) {
                        java.util.Arrays.sort(all, new java.util.Comparator<java.io.File>() {
                            public int compare(java.io.File a, java.io.File b) {
                                return Long.compare(b.lastModified(), a.lastModified());
                            }
                        });
                        for (java.io.File f : all) {
                            files.add(f);
                            names.add(f.getName() + "  (" + formatBytes(f.length()) + ")");
                        }
                    }
                } catch (Exception ignored) {
                }
                adapter.notifyDataSetChanged();
            }
        };
        refreshList.run();
        recBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (recordingNow) {
                    try {
                        voiceRecorder.stop();
                    } catch (Exception ignored) {
                    }
                    try {
                        voiceRecorder.release();
                    } catch (Exception ignored) {
                    }
                    voiceRecorder = null;
                    recordingNow = false;
                    recBtn.setText("Start recording");
                    status.setText("Saved");
                    refreshList.run();
                    return;
                }
                if (ActivityCompat.checkSelfPermission(ToolRunnerActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingAudioRetry = true;
                    final Runnable self = new Runnable() {
                        public void run() {
                            recBtn.performClick();
                        }
                    };
                    pendingAudioAction = self;
                    ActivityCompat.requestPermissions(ToolRunnerActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 9002);
                    return;
                }
                try {
                    java.io.File dir = new java.io.File(getCacheDir(), "recordings");
                    dir.mkdirs();
                    java.io.File out = new java.io.File(dir, "rec_" + System.currentTimeMillis() + ".3gp");
                    voiceRecorder = new MediaRecorder();
                    voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                    voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                    voiceRecorder.setOutputFile(out.getAbsolutePath());
                    voiceRecorder.prepare();
                    voiceRecorder.start();
                    recordingNow = true;
                    recBtn.setText("Stop recording");
                    status.setText("Recording " + out.getName());
                } catch (Exception e) {
                    status.setText("Record failed");
                }
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (files.isEmpty()) {
                    toast("No recordings yet");
                    return;
                }
                playRecording(files.get(0), status);
            }
        });
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                playRecording(files.get(position), status);
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                try {
                    files.get(position).delete();
                } catch (Exception ignored) {
                }
                refreshList.run();
                return true;
            }
        });
        addLabel(box, "Tap a recording to play it, long-press to delete.");
    }
    private void playRecording(java.io.File f, TextView status) {
        try {
            if (voicePlayer != null) {
                try {
                    voicePlayer.release();
                } catch (Exception ignored) {
                }
            }
            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(f.getAbsolutePath());
            voicePlayer.prepare();
            voicePlayer.start();
            status.setText("Playing " + f.getName());
            voicePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    status.setText("Ready");
                }
            });
        } catch (Exception e) {
            status.setText("Play failed");
        }
    }
    private void startGpsUpdates() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        if (locationManager == null) {
            toast("Location unavailable");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingGpsRetry = true;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 9003);
            return;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                toast("Enable GPS first");
            }
        } catch (Exception ignored) {
        }
        if (gpsListener == null) {
            gpsListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    if (!gpsRunning) {
                        return;
                    }
                    try {
                        float ms = location.hasSpeed() ? location.getSpeed() : 0f;
                        double kmh = ms * 3.6;
                        if (kmh > gpsMax) {
                            gpsMax = kmh;
                        }
                        gpsSum += kmh;
                        gpsCount++;
                        DecimalFormat df = new DecimalFormat("0.0");
                        if (gpsText != null) {
                            gpsText.setText(df.format(kmh) + " km/h\nMax " + df.format(gpsMax) + "  Avg " + df.format(gpsCount == 0 ? 0 : gpsSum / gpsCount));
                        }
                    } catch (Exception ignored) {
                    }
                }
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
                public void onProviderEnabled(String provider) {
                }
                public void onProviderDisabled(String provider) {
                }
            };
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, gpsListener);
        } catch (Exception e) {
            toast("GPS failed");
        }
    }
    private void buildGps(LinearLayout box) {
        addTitle(box, "GPS Speedometer");
        gpsText = makeOutput(box);
        gpsText.setTextSize(36);
        gpsText.setGravity(Gravity.CENTER);
        gpsText.setText("0.0 km/h");
        LinearLayout row = makeRow(box);
        MaterialButton startBtn = makeRowButton(row, "Start", 1f);
        MaterialButton stopBtn = makeRowButton(row, "Stop", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gpsRunning = true;
                startGpsUpdates();
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gpsRunning = false;
                try {
                    if (locationManager != null && gpsListener != null) {
                        locationManager.removeUpdates(gpsListener);
                    }
                } catch (Exception ignored) {
                }
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gpsMax = 0;
                gpsSum = 0;
                gpsCount = 0;
                gpsText.setText("0.0 km/h");
            }
        });
    }
    private void buildStorage(LinearLayout box) {
        addTitle(box, "Storage Info");
        final TextView output = makeOutput(box);
        final Runnable refresh = new Runnable() {
            public void run() {
                try {
                    StringBuilder b = new StringBuilder();
                    StatFs internal = new StatFs(Environment.getDataDirectory().getAbsolutePath());
                    long blockSize = internal.getBlockSizeLong();
                    long total = internal.getBlockCountLong() * blockSize;
                    long free = internal.getAvailableBlocksLong() * blockSize;
                    b.append("Internal total ").append(formatBytes(total)).append("\n");
                    b.append("Internal free ").append(formatBytes(free)).append("\n");
                    try {
                        java.io.File ext = Environment.getExternalStorageDirectory();
                        if (ext != null && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                            StatFs sd = new StatFs(ext.getAbsolutePath());
                            long bs = sd.getBlockSizeLong();
                            b.append("Shared total ").append(formatBytes(sd.getBlockCountLong() * bs)).append("\n");
                            b.append("Shared free ").append(formatBytes(sd.getAvailableBlocksLong() * bs)).append("\n");
                        }
                    } catch (Exception ignored) {
                    }
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    b.append("RAM total ").append(formatBytes(mi.totalMem)).append("\n");
                    b.append("RAM free ").append(formatBytes(mi.availMem)).append("\n");
                    b.append(mi.lowMemory ? "RAM low" : "RAM ok");
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Unavailable");
                }
            }
        };
        refresh.run();
        MaterialButton refreshBtn = makeButton(box, "Refresh");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh.run();
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("storage", output.getText().toString());
            }
        });
    }
    private void buildBattery(LinearLayout box) {
        addTitle(box, "Battery Info");
        final TextView output = makeOutput(box);
        final Runnable refresh = new Runnable() {
            public void run() {
                try {
                    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    StringBuilder b = new StringBuilder();
                    if (battery == null) {
                        output.setText("Unavailable");
                        return;
                    }
                    int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int pct = scale <= 0 ? level : Math.round(level * 100f / scale);
                    b.append("Level ").append(pct).append("%\n");
                    int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    String statusStr = "Unknown";
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                        statusStr = "Charging";
                    } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                        statusStr = "Full";
                    } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                        statusStr = "Discharging";
                    } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                        statusStr = "Not charging";
                    }
                    b.append("Status ").append(statusStr).append("\n");
                    int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    String plugStr = "Unplugged";
                    if (plugged == BatteryManager.BATTERY_PLUGGED_AC) {
                        plugStr = "AC";
                    } else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                        plugStr = "USB";
                    } else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                        plugStr = "Wireless";
                    }
                    b.append("Power ").append(plugStr).append("\n");
                    int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                    String healthStr = "Unknown";
                    if (health == BatteryManager.BATTERY_HEALTH_GOOD) {
                        healthStr = "Good";
                    } else if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) {
                        healthStr = "Overheat";
                    } else if (health == BatteryManager.BATTERY_HEALTH_COLD) {
                        healthStr = "Cold";
                    } else if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) {
                        healthStr = "Over voltage";
                    } else if (health == BatteryManager.BATTERY_HEALTH_DEAD) {
                        healthStr = "Dead";
                    }
                    b.append("Health ").append(healthStr).append("\n");
                    b.append("Temp ").append(new DecimalFormat("0.0").format(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)).append(" C\n");
                    b.append("Voltage ").append(battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)).append(" mV\n");
                    b.append("Tech ").append(battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Unavailable");
                }
            }
        };
        refresh.run();
        MaterialButton refreshBtn = makeButton(box, "Refresh");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh.run();
            }
        });
    }
    private void buildSensors(LinearLayout box) {
        addTitle(box, "Sensor Tester");
        sensorLiveText = makeOutput(box);
        sensorLiveText.setText("Starting...");
        if (sensorManager == null) {
            sensorLiveText.setText("No sensors on this device");
            return;
        }
        final float[][] latest = new float[5][];
        final String[] names = new String[]{"Accel", "Gyro", "Magnet", "Light", "Proximity"};
        try {
            if (activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        if (compassView != null || levelView != null) {
            return;
        }
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                int type = event.sensor.getType();
                if (type == Sensor.TYPE_ACCELEROMETER) {
                    latest[0] = event.values.clone();
                } else if (type == Sensor.TYPE_GYROSCOPE) {
                    latest[1] = event.values.clone();
                } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                    latest[2] = event.values.clone();
                } else if (type == Sensor.TYPE_LIGHT) {
                    latest[3] = event.values.clone();
                } else if (type == Sensor.TYPE_PROXIMITY) {
                    latest[4] = event.values.clone();
                } else {
                    return;
                }
                if (sensorLiveText != null) {
                    StringBuilder b = new StringBuilder();
                    DecimalFormat df = new DecimalFormat("0.00");
                    for (int i = 0; i < 5; i++) {
                        b.append(names[i]).append(": ");
                        if (latest[i] == null) {
                            b.append("-");
                        } else {
                            for (int j = 0; j < latest[i].length; j++) {
                                if (j > 0) {
                                    b.append(", ");
                                }
                                b.append(df.format(latest[i][j]));
                            }
                        }
                        if (i < 4) {
                            b.append("\n");
                        }
                    }
                    sensorLiveText.setText(b.toString());
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        int[] types = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY};
        int found = 0;
        for (int t : types) {
            try {
                Sensor s = sensorManager.getDefaultSensor(t);
                if (s != null) {
                    sensorManager.registerListener(activeListener, s, SensorManager.SENSOR_DELAY_UI);
                    found++;
                }
            } catch (Exception ignored) {
            }
        }
        if (found == 0) {
            sensorLiveText.setText("No common sensors found");
        }
    }
    private void buildTts(LinearLayout box) {
        addTitle(box, "Speak Text");
        final EditText input = makeInput(box, "Text to speak", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setText("Hello from Tools Kit");
        final TextView pitchLabel = addLabel(box, "Pitch: 1.0");
        SeekBar pitchBar = new SeekBar(this);
        pitchBar.setMax(150);
        pitchBar.setProgress(50);
        box.addView(pitchBar);
        final TextView rateLabel = addLabel(box, "Speed: 1.0");
        SeekBar rateBar = new SeekBar(this);
        rateBar.setMax(150);
        rateBar.setProgress(50);
        box.addView(rateBar);
        final float[] pitch = new float[]{1.0f};
        final float[] rate = new float[]{1.0f};
        final TextView status = makeOutput(box);
        status.setText("Engine starting...");
        try {
            ttsEngine = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                public void onInit(int code) {
                    try {
                        if (code == TextToSpeech.SUCCESS) {
                            ttsEngine.setLanguage(Locale.US);
                            status.setText("Ready");
                        } else {
                            status.setText("Engine failed");
                        }
                    } catch (Exception e) {
                        status.setText("Engine failed");
                    }
                }
            });
        } catch (Exception e) {
            status.setText("Engine failed");
        }
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                pitch[0] = 0.5f + progress / 100f;
                pitchLabel.setText("Pitch: " + new DecimalFormat("0.0").format(pitch[0]));
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rateBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                rate[0] = 0.5f + progress / 100f;
                rateLabel.setText("Speed: " + new DecimalFormat("0.0").format(rate[0]));
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout row = makeRow(box);
        MaterialButton speakBtn = makeRowButton(row, "Speak", 1f);
        MaterialButton stopBtn = makeRowButton(row, "Stop", 1f);
        speakBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.isEmpty()) {
                    toast("Enter text first");
                    return;
                }
                if (ttsEngine == null) {
                    toast("Engine not ready");
                    return;
                }
                try {
                    ttsEngine.setPitch(pitch[0]);
                    ttsEngine.setSpeechRate(rate[0]);
                    if (Build.VERSION.SDK_INT >= 21) {
                        ttsEngine.speak(t, TextToSpeech.QUEUE_FLUSH, null, "tools-tts");
                    } else {
                        ttsEngine.speak(t, TextToSpeech.QUEUE_FLUSH, null);
                    }
                } catch (Exception e) {
                    toast("Speak failed");
                }
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if (ttsEngine != null) {
                        ttsEngine.stop();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }
    private void buildBmr(LinearLayout box) {
        addTitle(box, "Calorie Calculator");
        RadioGroup genderGroup = new RadioGroup(this);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton maleBtn = new RadioButton(this);
        maleBtn.setId(View.generateViewId());
        maleBtn.setText("Male");
        RadioButton femaleBtn = new RadioButton(this);
        femaleBtn.setId(View.generateViewId());
        femaleBtn.setText("Female");
        genderGroup.addView(maleBtn);
        genderGroup.addView(femaleBtn);
        genderGroup.check(maleBtn.getId());
        box.addView(genderGroup);
        final EditText ageInput = makeInput(box, "Age in years", InputType.TYPE_CLASS_NUMBER);
        final EditText heightInput = makeInput(box, "Height in cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText weightInput = makeInput(box, "Weight in kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final String[] activities = new String[]{"Sedentary", "Light", "Moderate", "Active", "Extra active"};
        final double[] factors = new double[]{1.2, 1.375, 1.55, 1.725, 1.9};
        final Spinner actSpinner = new Spinner(this);
        ArrayAdapter<String> actAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, activities);
        actAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actSpinner.setAdapter(actAdapter);
        actSpinner.setSelection(2);
        box.addView(actSpinner);
        final TextView output = makeOutput(box);
        final int maleId = maleBtn.getId();
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    int age = Integer.parseInt(ageInput.getText().toString().trim());
                    double h = Double.parseDouble(heightInput.getText().toString());
                    double w = Double.parseDouble(weightInput.getText().toString());
                    boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                    double bmr = male ? (10 * w + 6.25 * h - 5 * age + 5) : (10 * w + 6.25 * h - 5 * age - 161);
                    double tdee = bmr * factors[actSpinner.getSelectedItemPosition()];
                    DecimalFormat df = new DecimalFormat("0");
                    output.setText("BMR " + df.format(bmr) + " kcal  TDEE " + df.format(tdee) + " kcal");
                } catch (Exception e) {
                    output.setText("Enter age, height and weight");
                }
            }
        });
    }
    private void buildCompound(LinearLayout box) {
        addTitle(box, "Interest Calculator");
        final EditText pInput = makeInput(box, "Initial amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pInput.setText("10000");
        final EditText rInput = makeInput(box, "Annual percent", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rInput.setText("8");
        final EditText yInput = makeInput(box, "Years", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        yInput.setText("5");
        final String[] freqs = new String[]{"Yearly", "Half-yearly", "Quarterly", "Monthly"};
        final int[] perYear = new int[]{1, 2, 4, 12};
        final Spinner freqSpinner = new Spinner(this);
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, freqs);
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        freqSpinner.setAdapter(freqAdapter);
        freqSpinner.setSelection(3);
        box.addView(freqSpinner);
        final EditText sipInput = makeInput(box, "Monthly deposit, 0 for none", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sipInput.setText("0");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double p = Double.parseDouble(pInput.getText().toString());
                    double annual = Double.parseDouble(rInput.getText().toString()) / 100.0;
                    double years = Double.parseDouble(yInput.getText().toString());
                    int n = perYear[freqSpinner.getSelectedItemPosition()];
                    double r = annual / n;
                    double periods = n * years;
                    double lump = p * Math.pow(1 + r, periods);
                    double monthly = Double.parseDouble(sipInput.getText().toString());
                    double sipFv = 0;
                    if (monthly > 0) {
                        double mr = annual / 12.0;
                        int months = (int) Math.round(years * 12);
                        if (mr == 0) {
                            sipFv = monthly * months;
                        } else {
                            sipFv = monthly * (Math.pow(1 + mr, months) - 1) / mr * (1 + mr);
                        }
                    }
                    DecimalFormat df = new DecimalFormat("0.00");
                    output.setText("Lump sum grows to " + df.format(lump) + "\nDeposits grow to " + df.format(sipFv) + "\nTotal " + df.format(lump + sipFv));
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private void buildPercent(LinearLayout box) {
        addTitle(box, "Percentage Calculator");
        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton m1 = new RadioButton(this);
        m1.setId(View.generateViewId());
        m1.setText("X percent of Y");
        RadioButton m2 = new RadioButton(this);
        m2.setId(View.generateViewId());
        m2.setText("X is what percent of Y");
        RadioButton m3 = new RadioButton(this);
        m3.setId(View.generateViewId());
        m3.setText("Percent change from X to Y");
        modeGroup.addView(m1);
        modeGroup.addView(m2);
        modeGroup.addView(m3);
        modeGroup.check(m1.getId());
        box.addView(modeGroup);
        final EditText xInput = makeInput(box, "X", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText yInput = makeInput(box, "Y", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final TextView output = makeOutput(box);
        final int id1 = m1.getId();
        final int id2 = m2.getId();
        Runnable compute = new Runnable() {
            public void run() {
                try {
                    double x = Double.parseDouble(xInput.getText().toString());
                    double y = Double.parseDouble(yInput.getText().toString());
                    int mode = modeGroup.getCheckedRadioButtonId();
                    DecimalFormat df = new DecimalFormat("0.##");
                    if (mode == id1) {
                        output.setText(df.format(x * y / 100.0));
                    } else if (mode == id2) {
                        output.setText(df.format(x / y * 100.0) + "%");
                    } else {
                        output.setText(df.format((y - x) / x * 100.0) + "%");
                    }
                } catch (Exception e) {
                    output.setText("Enter X and Y");
                }
            }
        };
        final Runnable computeRef = compute;
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                computeRef.run();
            }
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                computeRef.run();
            }
            public void afterTextChanged(Editable s) {
            }
        };
        xInput.addTextChangedListener(watcher);
        yInput.addTextChangedListener(watcher);
        compute.run();
    }
    private long gcdLong(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }
    private void buildFraction(LinearLayout box) {
        addTitle(box, "Fraction Calculator");
        LinearLayout row1 = makeRow(box);
        final EditText aInput = makeRowInput(row1, "a", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, "1");
        TextView slash1 = new TextView(this);
        slash1.setText("—");
        slash1.setGravity(Gravity.CENTER);
        slash1.setTextSize(20);
        row1.addView(slash1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));
        final EditText bInput = makeRowInput(row1, "b", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, "2");
        final Spinner opSpinner = new Spinner(this);
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"+", "-", "x", "div"});
        opAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        opSpinner.setAdapter(opAdapter);
        box.addView(opSpinner);
        LinearLayout row2 = makeRow(box);
        final EditText cInput = makeRowInput(row2, "c", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, "1");
        TextView slash2 = new TextView(this);
        slash2.setText("—");
        slash2.setGravity(Gravity.CENTER);
        slash2.setTextSize(20);
        row2.addView(slash2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));
        final EditText dInput = makeRowInput(row2, "d", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, "3");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    long a = Long.parseLong(aInput.getText().toString().trim());
                    long b = Long.parseLong(bInput.getText().toString().trim());
                    long c = Long.parseLong(cInput.getText().toString().trim());
                    long d = Long.parseLong(dInput.getText().toString().trim());
                    if (b == 0 || d == 0) {
                        output.setText("Denominator cannot be 0");
                        return;
                    }
                    long num;
                    long den;
                    int op = opSpinner.getSelectedItemPosition();
                    if (op == 0) {
                        num = a * d + c * b;
                        den = b * d;
                    } else if (op == 1) {
                        num = a * d - c * b;
                        den = b * d;
                    } else if (op == 2) {
                        num = a * c;
                        den = b * d;
                    } else {
                        if (c == 0) {
                            output.setText("Cannot divide by zero");
                            return;
                        }
                        num = a * d;
                        den = b * c;
                    }
                    if (den < 0) {
                        num = -num;
                        den = -den;
                    }
                    long g = gcdLong(num, den);
                    num /= g;
                    den /= g;
                    DecimalFormat df = new DecimalFormat("0.####");
                    output.setText(num + " / " + den + "  =  " + df.format((double) num / den));
                } catch (Exception e) {
                    output.setText("Enter four integers");
                }
            }
        });
    }
    private void buildAgeCalc(LinearLayout box) {
        addTitle(box, "Age Calculator");
        addLabel(box, "Use yyyy-MM-dd.");
        final EditText birthInput = makeInput(box, "Birth date", InputType.TYPE_CLASS_DATETIME);
        birthInput.setText("2000-01-01");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    f.setLenient(false);
                    Date birth = f.parse(birthInput.getText().toString().trim());
                    Calendar c1 = Calendar.getInstance();
                    c1.setTime(birth);
                    Calendar c2 = Calendar.getInstance();
                    if (c1.after(c2)) {
                        output.setText("Birth date is in the future");
                        return;
                    }
                    int years = c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR);
                    int months = c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH);
                    int days = c2.get(Calendar.DAY_OF_MONTH) - c1.get(Calendar.DAY_OF_MONTH);
                    if (days < 0) {
                        months--;
                        days += 30;
                    }
                    if (months < 0) {
                        years--;
                        months += 12;
                    }
                    long totalDays = (c2.getTimeInMillis() - c1.getTimeInMillis()) / 86400000L;
                    SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.US);
                    Calendar next = Calendar.getInstance();
                    next.setTime(birth);
                    next.set(Calendar.YEAR, c2.get(Calendar.YEAR));
                    if (!next.after(c2)) {
                        next.add(Calendar.YEAR, 1);
                    }
                    long toNext = (next.getTimeInMillis() - c2.getTimeInMillis()) / 86400000L;
                    output.setText(years + " years, " + months + " months, " + days + " days\nTotal " + totalDays + " days  (" + totalDays / 7 + " weeks)\nBorn on a " + dayFmt.format(birth) + "\nNext birthday in " + toNext + " days");
                } catch (Exception e) {
                    output.setText("Use yyyy-MM-dd");
                }
            }
        });
    }
    private void buildDateAdd(LinearLayout box) {
        addTitle(box, "Date Adder");
        final EditText dateInput = makeInput(box, "Start yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        dateInput.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        final EditText daysInput = makeInput(box, "Days to add (negative subtracts)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        daysInput.setText("30");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    f.setLenient(false);
                    Date start = f.parse(dateInput.getText().toString().trim());
                    int n = Integer.parseInt(daysInput.getText().toString().trim());
                    Calendar c = Calendar.getInstance();
                    c.setTime(start);
                    c.add(Calendar.DAY_OF_MONTH, n);
                    SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.US);
                    output.setText(f.format(c.getTime()) + "  (" + dayFmt.format(c.getTime()) + ")");
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private long parseDurationToSeconds(String s) throws Exception {
        String[] parts = s.trim().split(":");
        if (parts.length == 1) {
            return Long.parseLong(parts[0].trim());
        } else if (parts.length == 2) {
            return Long.parseLong(parts[0].trim()) * 60 + Long.parseLong(parts[1].trim());
        } else if (parts.length == 3) {
            return Long.parseLong(parts[0].trim()) * 3600 + Long.parseLong(parts[1].trim()) * 60 + Long.parseLong(parts[2].trim());
        }
        throw new Exception("bad");
    }
    private String formatDuration(long total) {
        boolean neg = total < 0;
        total = Math.abs(total);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return (neg ? "-" : "") + String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
    private void buildTimeCalc(LinearLayout box) {
        addTitle(box, "Time Calculator");
        addLabel(box, "Durations as ss, mm:ss or hh:mm:ss.");
        final EditText t1 = makeInput(box, "Duration 1", InputType.TYPE_CLASS_DATETIME);
        t1.setText("01:30:00");
        final EditText t2 = makeInput(box, "Duration 2", InputType.TYPE_CLASS_DATETIME);
        t2.setText("00:45:00");
        final TextView output = makeOutput(box);
        LinearLayout row = makeRow(box);
        MaterialButton addBtn = makeRowButton(row, "Add", 1f);
        MaterialButton subBtn = makeRowButton(row, "Subtract", 1f);
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    long r = parseDurationToSeconds(t1.getText().toString()) + parseDurationToSeconds(t2.getText().toString());
                    output.setText(formatDuration(r) + "  (" + r + "s, " + new DecimalFormat("0.##").format(r / 60.0) + " min)");
                } catch (Exception e) {
                    output.setText("Check format");
                }
            }
        });
        subBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    long r = parseDurationToSeconds(t1.getText().toString()) - parseDurationToSeconds(t2.getText().toString());
                    output.setText(formatDuration(r) + "  (" + r + "s)");
                } catch (Exception e) {
                    output.setText("Check format");
                }
            }
        });
    }
    private void buildSavings(LinearLayout box) {
        addTitle(box, "Savings Goal");
        final EditText targetInput = makeInput(box, "Target amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText savedInput = makeInput(box, "Already saved", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText monthlyInput = makeInput(box, "Monthly deposit", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText rateInput = makeInput(box, "Annual percent, 0 for none", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rateInput.setText("0");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Plan");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double target = Double.parseDouble(targetInput.getText().toString());
                    double balance = savedInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(savedInput.getText().toString());
                    double monthly = Double.parseDouble(monthlyInput.getText().toString());
                    double annual = rateInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(rateInput.getText().toString());
                    if (monthly <= 0) {
                        output.setText("Monthly deposit must be positive");
                        return;
                    }
                    double mr = annual / 1200.0;
                    int months = 0;
                    while (balance < target && months < 1200) {
                        balance += monthly;
                        balance *= (1 + mr);
                        months++;
                    }
                    if (months >= 1200) {
                        output.setText("Goal unreachable in 100 years");
                        return;
                    }
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.MONTH, months);
                    SimpleDateFormat f = new SimpleDateFormat("MMM yyyy", Locale.US);
                    output.setText(months + " months  (around " + f.format(c.getTime()) + ")\nProjected " + new DecimalFormat("0.00").format(balance));
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private void buildGst(LinearLayout box) {
        addTitle(box, "Tax Calculator");
        final EditText amountInput = makeInput(box, "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText rateInput = makeInput(box, "Tax percent", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rateInput.setText("18");
        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton addBtn2 = new RadioButton(this);
        addBtn2.setId(View.generateViewId());
        addBtn2.setText("Add tax");
        RadioButton remBtn = new RadioButton(this);
        remBtn.setId(View.generateViewId());
        remBtn.setText("Remove tax");
        modeGroup.addView(addBtn2);
        modeGroup.addView(remBtn);
        modeGroup.check(addBtn2.getId());
        box.addView(modeGroup);
        final TextView output = makeOutput(box);
        final int addId = addBtn2.getId();
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double amount = Double.parseDouble(amountInput.getText().toString());
                    double rate = Double.parseDouble(rateInput.getText().toString());
                    DecimalFormat df = new DecimalFormat("0.00");
                    if (modeGroup.getCheckedRadioButtonId() == addId) {
                        double tax = amount * rate / 100.0;
                        output.setText("Tax " + df.format(tax) + "  Total " + df.format(amount + tax));
                    } else {
                        double net = amount / (1 + rate / 100.0);
                        output.setText("Net " + df.format(net) + "  Tax " + df.format(amount - net));
                    }
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private void buildPace(LinearLayout box) {
        addTitle(box, "Pace Calculator");
        final EditText distInput = makeInput(box, "Distance in km", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distInput.setText("5");
        LinearLayout row = makeRow(box);
        final EditText hInput = makeRowInput(row, "hh", InputType.TYPE_CLASS_NUMBER, 1f, "0");
        final EditText mInput = makeRowInput(row, "mm", InputType.TYPE_CLASS_NUMBER, 1f, "25");
        final EditText sInput = makeRowInput(row, "ss", InputType.TYPE_CLASS_NUMBER, 1f, "0");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double dist = Double.parseDouble(distInput.getText().toString());
                    long secs = parseLongSafe(hInput.getText().toString()) * 3600 + parseLongSafe(mInput.getText().toString()) * 60 + parseLongSafe(sInput.getText().toString());
                    if (dist <= 0 || secs <= 0) {
                        output.setText("Enter distance and time");
                        return;
                    }
                    double secPerKm = secs / dist;
                    long pm = (long) (secPerKm / 60);
                    long ps = Math.round(secPerKm % 60);
                    double kmh = dist / (secs / 3600.0);
                    StringBuilder b = new StringBuilder();
                    b.append("Pace ").append(pm).append(":").append(String.format(Locale.US, "%02d", ps)).append(" per km\n");
                    b.append("Speed ").append(new DecimalFormat("0.0").format(kmh)).append(" km/h\n");
                    b.append("10K in ").append(formatDuration(Math.round(secPerKm * 10))).append("  Marathon in ").append(formatDuration(Math.round(secPerKm * 42.195)));
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private void buildCooking(LinearLayout box) {
        addTitle(box, "Cooking Converter");
        final String[] ingredients = new String[]{"Water", "Milk", "Flour", "Sugar", "Butter", "Rice", "Oats", "Oil"};
        final double[] gramsPerCup = new double[]{236.0, 240.0, 120.0, 200.0, 227.0, 185.0, 90.0, 218.0};
        final Spinner ingSpinner = new Spinner(this);
        ArrayAdapter<String> ingAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ingredients);
        ingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ingSpinner.setAdapter(ingAdapter);
        box.addView(ingSpinner);
        final EditText cupsInput = makeInput(box, "Cups", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cupsInput.setText("1");
        final EditText gramsInput = makeInput(box, "Grams", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    double gpc = gramsPerCup[ingSpinner.getSelectedItemPosition()];
                    String cs = cupsInput.getText().toString().trim();
                    if (!cs.isEmpty()) {
                        double grams = Double.parseDouble(cs) * gpc;
                        output.setText(new DecimalFormat("0.#").format(grams) + " g  (" + new DecimalFormat("0.#").format(grams / 28.3495) + " oz)");
                    }
                } catch (Exception e) {
                    output.setText("Enter cups or grams");
                }
            }
        };
        ingSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                compute.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        cupsInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        MaterialButton reverseBtn = makeButton(box, "Grams to cups");
        reverseBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double gpc = gramsPerCup[ingSpinner.getSelectedItemPosition()];
                    double grams = Double.parseDouble(gramsInput.getText().toString());
                    cupsInput.setText(new DecimalFormat("0.##").format(grams / gpc));
                    compute.run();
                } catch (Exception e) {
                    output.setText("Enter grams");
                }
            }
        });
        compute.run();
    }
    private void buildLorem(LinearLayout box) {
        addTitle(box, "Lorem Generator");
        final TextView countLabel = addLabel(box, "Paragraphs: 3");
        SeekBar countBar = new SeekBar(this);
        countBar.setMax(9);
        countBar.setProgress(2);
        box.addView(countBar);
        final TextView output = makeOutput(box);
        final String[] words = new String[]{"lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi", "aliquip", "commodo", "consequat", "duis", "aute", "irure", "fugiat", "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "proident", "sunt", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "est", "laborum"};
        final Random loremRandom = new Random();
        final Runnable generate = new Runnable() {
            public void run() {
                int paras = 1 + countBar.getProgress();
                countLabel.setText("Paragraphs: " + paras);
                StringBuilder b = new StringBuilder();
                for (int p = 0; p < paras; p++) {
                    int sentences = 3 + loremRandom.nextInt(3);
                    for (int s = 0; s < sentences; s++) {
                        int len = 5 + loremRandom.nextInt(8);
                        for (int w = 0; w < len; w++) {
                            String word = words[loremRandom.nextInt(words.length)];
                            if (w == 0) {
                                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                            }
                            b.append(word);
                            b.append(w == len - 1 ? ". " : " ");
                        }
                    }
                    if (p < paras - 1) {
                        b.append("\n\n");
                    }
                }
                output.setText(b.toString().trim());
            }
        };
        countBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                generate.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        generate.run();
        LinearLayout row = makeRow(box);
        MaterialButton regenBtn = makeRowButton(row, "New", 1f);
        MaterialButton copyBtn = makeRowButton(row, "Copy", 1f);
        regenBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                generate.run();
            }
        });
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("lorem", output.getText().toString());
            }
        });
    }
    private void buildStrength(LinearLayout box) {
        addTitle(box, "Password Strength");
        final EditText input = makeInput(box, "Password to test", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final TextView output = makeOutput(box);
        output.setText("Type a password");
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String p = s.toString();
                if (p.isEmpty()) {
                    output.setText("Type a password");
                    return;
                }
                boolean lower = false;
                boolean upper = false;
                boolean digit = false;
                boolean symbol = false;
                for (int i = 0; i < p.length(); i++) {
                    char c = p.charAt(i);
                    if (c >= 'a' && c <= 'z') {
                        lower = true;
                    } else if (c >= 'A' && c <= 'Z') {
                        upper = true;
                    } else if (c >= '0' && c <= '9') {
                        digit = true;
                    } else {
                        symbol = true;
                    }
                }
                int pool = (lower ? 26 : 0) + (upper ? 26 : 0) + (digit ? 10 : 0) + (symbol ? 33 : 0);
                double entropy = p.length() * (Math.log(pool) / Math.log(2));
                String label;
                if (entropy < 28) {
                    label = "Very weak";
                } else if (entropy < 36) {
                    label = "Weak";
                } else if (entropy < 60) {
                    label = "Fair";
                } else if (entropy < 80) {
                    label = "Strong";
                } else {
                    label = "Excellent";
                }
                double guesses = Math.pow(2, entropy - 1);
                String time = guessesToTime(guesses);
                StringBuilder tips = new StringBuilder();
                if (p.length() < 12) {
                    tips.append("Use 12 or more characters. ");
                }
                if (!symbol) {
                    tips.append("Add symbols. ");
                }
                if (!digit) {
                    tips.append("Add digits. ");
                }
                if (!upper || !lower) {
                    tips.append("Mix upper and lower case.");
                }
                output.setText(label + "  (" + new DecimalFormat("0").format(entropy) + " bits)\nCrack estimate " + time + "\n" + tips.toString().trim());
            }
            public void afterTextChanged(Editable s) {
            }
        });
    }
    private String guessesToTime(double guesses) {
        double perSecond = 10000000000.0;
        double seconds = guesses / perSecond;
        if (seconds < 1) {
            return "under a second";
        } else if (seconds < 60) {
            return new DecimalFormat("0").format(seconds) + " seconds";
        } else if (seconds < 3600) {
            return new DecimalFormat("0").format(seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            return new DecimalFormat("0").format(seconds / 3600) + " hours";
        } else if (seconds < 31536000) {
            return new DecimalFormat("0").format(seconds / 86400) + " days";
        } else if (seconds < 3153600000L) {
            return new DecimalFormat("0").format(seconds / 31536000) + " years";
        }
        return "centuries";
    }
    private void buildUuid(LinearLayout box) {
        addTitle(box, "UUID Generator");
        final TextView countLabel = addLabel(box, "Count: 5");
        SeekBar countBar = new SeekBar(this);
        countBar.setMax(19);
        countBar.setProgress(4);
        box.addView(countBar);
        final TextView output = makeOutput(box);
        final Runnable generate = new Runnable() {
            public void run() {
                int n = 1 + countBar.getProgress();
                countLabel.setText("Count: " + n);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    b.append(java.util.UUID.randomUUID().toString());
                    if (i < n - 1) {
                        b.append("\n");
                    }
                }
                output.setText(b.toString());
            }
        };
        countBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                generate.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        generate.run();
        LinearLayout row = makeRow(box);
        MaterialButton regenBtn = makeRowButton(row, "New", 1f);
        MaterialButton copyBtn = makeRowButton(row, "Copy", 1f);
        regenBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                generate.run();
            }
        });
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("uuid", output.getText().toString());
            }
        });
    }
    private void buildColorConv(LinearLayout box) {
        addTitle(box, "Color Converter");
        final EditText hexInput = makeInput(box, "HEX, e.g. #1B73E8", InputType.TYPE_CLASS_TEXT);
        hexInput.setText("#1B73E8");
        final View swatch = new View(this);
        box.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    String h = hexInput.getText().toString().trim().replace("#", "");
                    if (h.length() == 3) {
                        h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                    }
                    int color = Color.parseColor("#" + h);
                    int r = Color.red(color);
                    int g = Color.green(color);
                    int b = Color.blue(color);
                    float[] hsv = new float[3];
                    Color.RGBToHSV(r, g, b, hsv);
                    swatch.setBackgroundColor(color);
                    StringBuilder sb = new StringBuilder();
                    sb.append("RGB ").append(r).append(", ").append(g).append(", ").append(b).append("\n");
                    sb.append("HSL ").append(Math.round(hsv[0])).append(", ").append(Math.round(hsv[1] * 100)).append("%, ").append(Math.round(hsv[2] * 100)).append("%\n");
                    sb.append("HEX #").append(String.format(Locale.US, "%02X%02X%02X", r, g, b));
                    output.setText(sb.toString());
                } catch (Exception e) {
                    output.setText("Enter a valid HEX color");
                }
            }
        };
        hexInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        LinearLayout row = makeRow(box);
        MaterialButton randomBtn = makeRowButton(row, "Random", 1f);
        MaterialButton copyBtn = makeRowButton(row, "Copy", 1f);
        randomBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Random r = new Random();
                hexInput.setText(String.format(Locale.US, "#%02X%02X%02X", r.nextInt(256), r.nextInt(256), r.nextInt(256)));
            }
        });
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("color", output.getText().toString());
            }
        });
    }
    private void buildRegex(LinearLayout box) {
        addTitle(box, "Regex Tester");
        final EditText patternInput = makeInput(box, "Pattern, e.g. [a-z]+@[a-z]+", InputType.TYPE_CLASS_TEXT);
        patternInput.setText("[a-z]+@[a-z]+");
        final CheckBox caseBox = new CheckBox(this);
        caseBox.setText("Ignore case");
        box.addView(caseBox);
        final CheckBox multiBox = new CheckBox(this);
        multiBox.setText("Multiline");
        box.addView(multiBox);
        final EditText testInput = makeInput(box, "Test text", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMinLines(3);
        testInput.setText("mail me at joe@example or ann@test");
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    int flags = 0;
                    if (caseBox.isChecked()) {
                        flags |= java.util.regex.Pattern.CASE_INSENSITIVE;
                    }
                    if (multiBox.isChecked()) {
                        flags |= java.util.regex.Pattern.MULTILINE;
                    }
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(patternInput.getText().toString(), flags);
                    java.util.regex.Matcher m = p.matcher(testInput.getText().toString());
                    int count = 0;
                    StringBuilder b = new StringBuilder();
                    while (m.find() && count < 10) {
                        count++;
                        b.append(count).append(". ").append(m.group()).append("\n");
                    }
                    int total = count;
                    while (m.find()) {
                        total++;
                    }
                    if (total == 0) {
                        output.setText("No matches");
                    } else {
                        output.setText(total + (total == 1 ? " match" : " matches") + "\n" + b.toString().trim());
                    }
                } catch (Exception e) {
                    output.setText("Invalid pattern");
                }
            }
        };
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        };
        patternInput.addTextChangedListener(watcher);
        testInput.addTextChangedListener(watcher);
        caseBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                compute.run();
            }
        });
        multiBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                compute.run();
            }
        });
        compute.run();
    }
    private void buildUrlCodec(LinearLayout box) {
        addTitle(box, "URL Encoder");
        final EditText input = makeInput(box, "Input", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        final TextView output = makeOutput(box);
        output.setText("Result");
        LinearLayout row = makeRow(box);
        MaterialButton encBtn = makeRowButton(row, "Encode", 1f);
        MaterialButton decBtn = makeRowButton(row, "Decode", 1f);
        encBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    output.setText(java.net.URLEncoder.encode(input.getText().toString(), "UTF-8"));
                } catch (Exception e) {
                    output.setText("Error");
                }
            }
        });
        decBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    output.setText(java.net.URLDecoder.decode(input.getText().toString(), "UTF-8"));
                } catch (Exception e) {
                    output.setText("Invalid encoding");
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy result");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("url", output.getText().toString());
            }
        });
    }
    private void buildBinaryText(LinearLayout box) {
        addTitle(box, "Binary Translator");
        final EditText input = makeInput(box, "Text or binary", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        final TextView output = makeOutput(box);
        output.setText("Result");
        LinearLayout row = makeRow(box);
        MaterialButton encBtn = makeRowButton(row, "To binary", 1f);
        MaterialButton decBtn = makeRowButton(row, "To text", 1f);
        encBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    byte[] bytes = input.getText().toString().getBytes("UTF-8");
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < bytes.length; i++) {
                        if (i > 0) {
                            b.append(' ');
                        }
                        String bin = Integer.toBinaryString(bytes[i] & 255);
                        while (bin.length() < 8) {
                            bin = "0" + bin;
                        }
                        b.append(bin);
                    }
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Error");
                }
            }
        });
        decBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String[] parts = input.getText().toString().trim().split("\\s+");
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    for (String part : parts) {
                        bos.write(Integer.parseInt(part, 2));
                    }
                    output.setText(new String(bos.toByteArray(), "UTF-8"));
                } catch (Exception e) {
                    output.setText("Use 8-bit groups separated by spaces");
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy result");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("binary", output.getText().toString());
            }
        });
    }
    private void buildCaesar(LinearLayout box) {
        addTitle(box, "Caesar Cipher");
        final EditText input = makeInput(box, "Text", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        final TextView shiftLabel = addLabel(box, "Shift: 3");
        SeekBar shiftBar = new SeekBar(this);
        shiftBar.setMax(25);
        shiftBar.setProgress(3);
        box.addView(shiftBar);
        final int[] shift = new int[]{3};
        shiftBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                shift[0] = progress;
                shiftLabel.setText("Shift: " + progress);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        final TextView output = makeOutput(box);
        output.setText("Result");
        LinearLayout row = makeRow(box);
        MaterialButton encBtn = makeRowButton(row, "Encrypt", 1f);
        MaterialButton decBtn = makeRowButton(row, "Decrypt", 1f);
        MaterialButton bruteBtn = makeRowButton(row, "All shifts", 1f);
        encBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(caesarShift(input.getText().toString(), shift[0]));
            }
        });
        decBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                output.setText(caesarShift(input.getText().toString(), 26 - (shift[0] % 26)));
            }
        });
        bruteBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i < 26; i++) {
                    b.append(i).append(": ").append(caesarShift(input.getText().toString(), i)).append("\n");
                }
                output.setText(b.toString().trim());
            }
        });
    }
    private String caesarShift(String s, int shift) {
        shift = ((shift % 26) + 26) % 26;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                b.append((char) ('a' + (c - 'a' + shift) % 26));
            } else if (c >= 'A' && c <= 'Z') {
                b.append((char) ('A' + (c - 'A' + shift) % 26));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
    private void buildCards(LinearLayout box) {
        addTitle(box, "Card Deck");
        final List<String> deck = new ArrayList<>();
        final TextView current = makeOutput(box);
        current.setTextSize(48);
        current.setGravity(Gravity.CENTER);
        current.setText("-");
        final TextView count = addLabel(box, "52 cards left");
        final Runnable reset = new Runnable() {
            public void run() {
                deck.clear();
                String[] suits = new String[]{"Spades", "Hearts", "Diamonds", "Clubs"};
                String[] ranks = new String[]{"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
                for (String suit : suits) {
                    for (String rank : ranks) {
                        deck.add(rank + " of " + suit);
                    }
                }
                count.setText("52 cards left");
                current.setText("-");
            }
        };
        reset.run();
        LinearLayout row = makeRow(box);
        MaterialButton drawBtn = makeRowButton(row, "Draw", 1f);
        MaterialButton shuffleBtn = makeRowButton(row, "Shuffle", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        drawBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (deck.isEmpty()) {
                    toast("Deck empty, reset first");
                    return;
                }
                String card = deck.remove(deck.size() - 1);
                current.setText(card);
                count.setText(deck.size() + " cards left");
                vibrateTick();
            }
        });
        shuffleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                java.util.Collections.shuffle(deck);
                toast("Shuffled");
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reset.run();
            }
        });
    }
    private void buildOracle(LinearLayout box) {
        addTitle(box, "Magic 8-Ball");
        final EditText question = makeInput(box, "Ask a yes-no question", InputType.TYPE_CLASS_TEXT);
        final TextView answer = makeOutput(box);
        answer.setTextSize(24);
        answer.setGravity(Gravity.CENTER);
        answer.setText("Ask and tap");
        final String[] answers = new String[]{"It is certain", "Without a doubt", "Yes definitely", "Most likely", "Outlook good", "Signs point to yes", "Reply hazy, try again", "Ask again later", "Cannot predict now", "Concentrate and ask again", "Do not count on it", "My reply is no", "Outlook not so good", "Very doubtful", "No chance", "Yes", "No", "Maybe", "Definitely not", "Go for it"};
        MaterialButton askBtn = makeButton(box, "Shake the ball");
        askBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (question.getText().toString().trim().isEmpty()) {
                    toast("Ask something first");
                    return;
                }
                answer.setText(answers[new Random().nextInt(answers.length)]);
                vibrateTick();
            }
        });
    }
    private void buildPrime(LinearLayout box) {
        addTitle(box, "Prime Tools");
        final EditText input = makeInput(box, "Number up to 1000000000", InputType.TYPE_CLASS_NUMBER);
        input.setText("97");
        final TextView output = makeOutput(box);
        MaterialButton checkBtn = makeButton(box, "Check prime and factorize");
        checkBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    long n = Long.parseLong(input.getText().toString().trim());
                    if (n < 0 || n > 1000000000L) {
                        output.setText("Enter 0 to 1000000000");
                        return;
                    }
                    StringBuilder b = new StringBuilder();
                    b.append(n).append(n == 1 ? " is not prime\n" : (isPrimeLong(n) ? " is prime\n" : " is not prime\n"));
                    if (n > 1) {
                        b.append("Factors: ").append(factorizeLong(n)).append("\n");
                        long next = n + 1;
                        while (!isPrimeLong(next)) {
                            next++;
                        }
                        b.append("Next prime: ").append(next);
                    }
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Enter an integer");
                }
            }
        });
        MaterialButton listBtn = makeButton(box, "List primes up to N (max 10000)");
        listBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    int n = Integer.parseInt(input.getText().toString().trim());
                    if (n < 2 || n > 10000) {
                        output.setText("Enter 2 to 10000");
                        return;
                    }
                    boolean[] sieve = new boolean[n + 1];
                    java.util.Arrays.fill(sieve, true);
                    sieve[0] = false;
                    if (n >= 1) {
                        sieve[1] = false;
                    }
                    for (int i = 2; i * i <= n; i++) {
                        if (sieve[i]) {
                            for (int j = i * i; j <= n; j += i) {
                                sieve[j] = false;
                            }
                        }
                    }
                    StringBuilder b = new StringBuilder();
                    int count = 0;
                    for (int i = 2; i <= n; i++) {
                        if (sieve[i]) {
                            if (count > 0) {
                                b.append(", ");
                            }
                            b.append(i);
                            count++;
                        }
                    }
                    output.setText(count + " primes\n" + b.toString());
                } catch (Exception e) {
                    output.setText("Enter an integer");
                }
            }
        });
    }
    private boolean isPrimeLong(long n) {
        if (n < 2) {
            return false;
        }
        if (n == 2 || n == 3) {
            return true;
        }
        if (n % 2 == 0) {
            return false;
        }
        for (long i = 3; i * i <= n; i += 2) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
    private String factorizeLong(long n) {
        StringBuilder b = new StringBuilder();
        long rest = n;
        boolean first = true;
        for (long p = 2; p * p <= rest; p += (p == 2 ? 1 : 2)) {
            int exp = 0;
            while (rest % p == 0) {
                rest /= p;
                exp++;
            }
            if (exp > 0) {
                if (!first) {
                    b.append(" x ");
                }
                b.append(p);
                if (exp > 1) {
                    b.append("^").append(exp);
                }
                first = false;
            }
        }
        if (rest > 1) {
            if (!first) {
                b.append(" x ");
            }
            b.append(rest);
        }
        return b.toString();
    }
    private void buildQuadratic(LinearLayout box) {
        addTitle(box, "Quadratic Solver");
        addLabel(box, "Solves a x squared plus b x plus c equals 0.");
        final EditText aInput = makeInput(box, "a", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        aInput.setText("1");
        final EditText bInput = makeInput(box, "b", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        bInput.setText("-3");
        final EditText cInput = makeInput(box, "c", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        cInput.setText("2");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Solve");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double a = Double.parseDouble(aInput.getText().toString());
                    double b = Double.parseDouble(bInput.getText().toString());
                    double c = Double.parseDouble(cInput.getText().toString());
                    DecimalFormat df = new DecimalFormat("0.####");
                    if (a == 0) {
                        if (b == 0) {
                            output.setText("Not an equation");
                        } else {
                            output.setText("Linear root x = " + df.format(-c / b));
                        }
                        return;
                    }
                    double disc = b * b - 4 * a * c;
                    double vx = -b / (2 * a);
                    double vy = a * vx * vx + b * vx + c;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Discriminant ").append(df.format(disc)).append("\n");
                    if (disc > 0) {
                        sb.append("x1 = ").append(df.format((-b + Math.sqrt(disc)) / (2 * a))).append("\n");
                        sb.append("x2 = ").append(df.format((-b - Math.sqrt(disc)) / (2 * a))).append("\n");
                    } else if (disc == 0) {
                        sb.append("x = ").append(df.format(-b / (2 * a))).append("\n");
                    } else {
                        double re = -b / (2 * a);
                        double im = Math.sqrt(-disc) / (2 * a);
                        sb.append("x1 = ").append(df.format(re)).append(" + ").append(df.format(im)).append("i\n");
                        sb.append("x2 = ").append(df.format(re)).append(" - ").append(df.format(im)).append("i\n");
                    }
                    sb.append("Vertex (").append(df.format(vx)).append(", ").append(df.format(vy)).append(")");
                    output.setText(sb.toString());
                } catch (Exception e) {
                    output.setText("Enter a, b and c");
                }
            }
        });
    }
    private void buildMatrix(LinearLayout box) {
        addTitle(box, "Matrix 2x2");
        addLabel(box, "Matrix A");
        LinearLayout aRow1 = makeRow(box);
        final EditText a11 = makeNumCell(aRow1, "1");
        final EditText a12 = makeNumCell(aRow1, "2");
        LinearLayout aRow2 = makeRow(box);
        final EditText a21 = makeNumCell(aRow2, "3");
        final EditText a22 = makeNumCell(aRow2, "4");
        addLabel(box, "Matrix B");
        LinearLayout bRow1 = makeRow(box);
        final EditText b11 = makeNumCell(bRow1, "5");
        final EditText b12 = makeNumCell(bRow1, "6");
        LinearLayout bRow2 = makeRow(box);
        final EditText b21 = makeNumCell(bRow2, "7");
        final EditText b22 = makeNumCell(bRow2, "8");
        final Spinner opSpinner = new Spinner(this);
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"A + B", "A - B", "A x B", "det(A)", "inverse(A)", "transpose(A)"});
        opAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        opSpinner.setAdapter(opAdapter);
        box.addView(opSpinner);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Compute");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double x11 = Double.parseDouble(a11.getText().toString());
                    double x12 = Double.parseDouble(a12.getText().toString());
                    double x21 = Double.parseDouble(a21.getText().toString());
                    double x22 = Double.parseDouble(a22.getText().toString());
                    double y11 = Double.parseDouble(b11.getText().toString());
                    double y12 = Double.parseDouble(b12.getText().toString());
                    double y21 = Double.parseDouble(b21.getText().toString());
                    double y22 = Double.parseDouble(b22.getText().toString());
                    DecimalFormat df = new DecimalFormat("0.####");
                    int op = opSpinner.getSelectedItemPosition();
                    String result;
                    if (op == 0) {
                        result = mat2(df, x11 + y11, x12 + y12, x21 + y21, x22 + y22);
                    } else if (op == 1) {
                        result = mat2(df, x11 - y11, x12 - y12, x21 - y21, x22 - y22);
                    } else if (op == 2) {
                        result = mat2(df, x11 * y11 + x12 * y21, x11 * y12 + x12 * y22, x21 * y11 + x22 * y21, x21 * y12 + x22 * y22);
                    } else if (op == 3) {
                        result = "det = " + df.format(x11 * x22 - x12 * x21);
                    } else if (op == 4) {
                        double det = x11 * x22 - x12 * x21;
                        if (det == 0) {
                            result = "Singular, no inverse";
                        } else {
                            result = mat2(df, x22 / det, -x12 / det, -x21 / det, x11 / det);
                        }
                    } else {
                        result = mat2(df, x11, x21, x12, x22);
                    }
                    output.setText(result);
                } catch (Exception e) {
                    output.setText("Fill all cells");
                }
            }
        });
    }
    private EditText makeRowInput(LinearLayout row, String hint, int inputType, float weight, String def) {
        com.google.android.material.textfield.TextInputLayout layout =
                io.github.abdurazaaqmohammed.ui.UiFields.box(this, hint);
        EditText e = io.github.abdurazaaqmohammed.ui.UiFields.field(layout, inputType);
        e.setGravity(Gravity.CENTER);
        if (def != null) e.setText(def);
        row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return e;
    }
    private EditText makeNumCell(LinearLayout row, String def) {
        com.google.android.material.textfield.TextInputLayout layout =
                io.github.abdurazaaqmohammed.ui.UiFields.box(this, null);
        EditText e = io.github.abdurazaaqmohammed.ui.UiFields.field(layout,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(def);
        e.setGravity(Gravity.CENTER);
        row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return e;
    }
    private String mat2(DecimalFormat df, double a, double b, double c, double d) {
        return "| " + df.format(a) + "  " + df.format(b) + " |\n| " + df.format(c) + "  " + df.format(d) + " |";
    }
    private void buildTriangle(LinearLayout box) {
        addTitle(box, "Triangle Solver");
        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rightBtn = new RadioButton(this);
        rightBtn.setId(View.generateViewId());
        rightBtn.setText("Right legs");
        RadioButton sssBtn = new RadioButton(this);
        sssBtn.setId(View.generateViewId());
        sssBtn.setText("3 sides");
        modeGroup.addView(rightBtn);
        modeGroup.addView(sssBtn);
        modeGroup.check(rightBtn.getId());
        box.addView(modeGroup);
        final EditText s1 = makeInput(box, "Side a", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s1.setText("3");
        final EditText s2 = makeInput(box, "Side b", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s2.setText("4");
        final EditText s3 = makeInput(box, "Side c (3-sides mode only)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s3.setText("5");
        final TextView output = makeOutput(box);
        final int rightId = rightBtn.getId();
        MaterialButton goBtn = makeButton(box, "Solve");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    DecimalFormat df = new DecimalFormat("0.##");
                    if (modeGroup.getCheckedRadioButtonId() == rightId) {
                        double a = Double.parseDouble(s1.getText().toString());
                        double b = Double.parseDouble(s2.getText().toString());
                        double hyp = Math.sqrt(a * a + b * b);
                        double angA = Math.toDegrees(Math.atan2(a, b));
                        output.setText("Hypotenuse " + df.format(hyp) + "\nAngles " + df.format(angA) + " and " + df.format(90 - angA) + " deg\nArea " + df.format(a * b / 2) + "  Perimeter " + df.format(a + b + hyp));
                    } else {
                        double a = Double.parseDouble(s1.getText().toString());
                        double b = Double.parseDouble(s2.getText().toString());
                        double c = Double.parseDouble(s3.getText().toString());
                        if (a + b <= c || a + c <= b || b + c <= a) {
                            output.setText("Not a valid triangle");
                            return;
                        }
                        double s = (a + b + c) / 2;
                        double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
                        double angA = Math.toDegrees(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
                        double angB = Math.toDegrees(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
                        output.setText("Area " + df.format(area) + "  Perimeter " + df.format(a + b + c) + "\nAngles " + df.format(angA) + ", " + df.format(angB) + ", " + df.format(180 - angA - angB) + " deg");
                    }
                } catch (Exception e) {
                    output.setText("Check sides");
                }
            }
        });
    }
    private void buildGeometry(LinearLayout box) {
        addTitle(box, "Geometry Calculator");
        final Spinner shapeSpinner = new Spinner(this);
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Circle (r)", "Rectangle (w,h)", "Triangle (b,h)", "Cylinder (r,h)", "Sphere (r)"});
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shapeSpinner.setAdapter(shapeAdapter);
        box.addView(shapeSpinner);
        final EditText v1 = makeInput(box, "r or width or base", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v1.setText("5");
        final EditText v2 = makeInput(box, "h (rect, triangle, cylinder)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v2.setText("10");
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double a = Double.parseDouble(v1.getText().toString());
                    String vs = v2.getText().toString().trim();
                    double b = vs.isEmpty() ? 0 : Double.parseDouble(vs);
                    DecimalFormat df = new DecimalFormat("0.##");
                    int shape = shapeSpinner.getSelectedItemPosition();
                    StringBuilder sb = new StringBuilder();
                    if (shape == 0) {
                        sb.append("Area ").append(df.format(Math.PI * a * a)).append("\nCircumference ").append(df.format(2 * Math.PI * a));
                    } else if (shape == 1) {
                        sb.append("Area ").append(df.format(a * b)).append("\nPerimeter ").append(df.format(2 * (a + b)));
                    } else if (shape == 2) {
                        sb.append("Area ").append(df.format(a * b / 2));
                    } else if (shape == 3) {
                        sb.append("Volume ").append(df.format(Math.PI * a * a * b)).append("\nSurface ").append(df.format(2 * Math.PI * a * (a + b)));
                    } else {
                        sb.append("Volume ").append(df.format(4.0 / 3.0 * Math.PI * a * a * a)).append("\nSurface ").append(df.format(4 * Math.PI * a * a));
                    }
                    output.setText(sb.toString());
                } catch (Exception e) {
                    output.setText("Check inputs");
                }
            }
        });
    }
    private void buildWater(LinearLayout box) {
        addTitle(box, "Water Tracker");
        final EditText weightInput = makeInput(box, "Weight in kg for target", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightInput.setText("70");
        final TextView targetText = makeOutput(box);
        final TextView todayText = new TextView(this);
        todayText.setTextSize(40);
        todayText.setGravity(Gravity.CENTER);
        todayText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLACK));
        box.addView(todayText);
        final String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        final int[] drunk = new int[]{0};
        try {
            drunk[0] = getSharedPreferences("tools", MODE_PRIVATE).getInt("water_" + todayKey, 0);
        } catch (Exception ignored) {
        }
        final Runnable render = new Runnable() {
            public void run() {
                try {
                    double w = Double.parseDouble(weightInput.getText().toString());
                    int target = (int) Math.round(w * 35);
                    targetText.setText("Target " + target + " ml");
                    todayText.setText(drunk[0] + " ml");
                } catch (Exception e) {
                    targetText.setText("Enter weight");
                }
            }
        };
        render.run();
        weightInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout row = makeRow(box);
        MaterialButton add250 = makeRowButton(row, "+250", 1f);
        MaterialButton add500 = makeRowButton(row, "+500", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset", 1f);
        final Runnable persist = new Runnable() {
            public void run() {
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putInt("water_" + todayKey, drunk[0]).apply();
                } catch (Exception ignored) {
                }
            }
        };
        add250.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                drunk[0] += 250;
                todayText.setText(drunk[0] + " ml");
                persist.run();
                vibrateTick();
            }
        });
        add500.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                drunk[0] += 500;
                todayText.setText(drunk[0] + " ml");
                persist.run();
                vibrateTick();
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                drunk[0] = 0;
                todayText.setText("0 ml");
                persist.run();
            }
        });
    }
    private void buildSleep(LinearLayout box) {
        addTitle(box, "Sleep Cycles");
        addLabel(box, "Each cycle is 90 minutes. Wake at the end of a cycle.");
        final EditText wakeInput = makeInput(box, "Wake time HH:mm", InputType.TYPE_CLASS_DATETIME);
        wakeInput.setText("07:00");
        final TextView output = makeOutput(box);
        MaterialButton bedBtn = makeButton(box, "Best bedtimes");
        bedBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String[] parts = wakeInput.getText().toString().trim().split(":");
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
                    c.set(Calendar.MINUTE, Integer.parseInt(parts[1].trim()));
                    c.set(Calendar.SECOND, 0);
                    SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                    StringBuilder b = new StringBuilder();
                    for (int i = 6; i >= 3; i--) {
                        Calendar t = (Calendar) c.clone();
                        t.add(Calendar.MINUTE, -i * 90 - 15);
                        b.append(i).append(" cycles: ").append(f.format(t.getTime())).append("\n");
                    }
                    output.setText(b.toString().trim());
                } catch (Exception e) {
                    output.setText("Use HH:mm");
                }
            }
        });
        MaterialButton nowBtn = makeButton(box, "Sleeping now, when to wake");
        nowBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Calendar now = Calendar.getInstance();
                SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                StringBuilder b = new StringBuilder();
                for (int i = 3; i <= 6; i++) {
                    Calendar t = (Calendar) now.clone();
                    t.add(Calendar.MINUTE, i * 90 + 15);
                    b.append(i).append(" cycles: ").append(f.format(t.getTime())).append("\n");
                }
                output.setText(b.toString().trim());
            }
        });
    }
    private void buildBodyFat(LinearLayout box) {
        addTitle(box, "Body Fat Estimator");
        addLabel(box, "US Navy method, measurements in cm.");
        RadioGroup genderGroup = new RadioGroup(this);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton maleBtn = new RadioButton(this);
        maleBtn.setId(View.generateViewId());
        maleBtn.setText("Male");
        RadioButton femaleBtn = new RadioButton(this);
        femaleBtn.setId(View.generateViewId());
        femaleBtn.setText("Female");
        genderGroup.addView(maleBtn);
        genderGroup.addView(femaleBtn);
        genderGroup.check(maleBtn.getId());
        box.addView(genderGroup);
        final EditText waistInput = makeInput(box, "Waist cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText neckInput = makeInput(box, "Neck cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText heightInput = makeInput(box, "Height cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText hipInput = makeInput(box, "Hip cm (female only)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        final int maleId = maleBtn.getId();
        MaterialButton goBtn = makeButton(box, "Calculate");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double waist = Double.parseDouble(waistInput.getText().toString());
                    double neck = Double.parseDouble(neckInput.getText().toString());
                    double height = Double.parseDouble(heightInput.getText().toString());
                    boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                    double bf;
                    if (male) {
                        bf = 495 / (1.0324 - 0.19077 * Math.log10(waist - neck) + 0.15456 * Math.log10(height)) - 450;
                    } else {
                        double hip = Double.parseDouble(hipInput.getText().toString());
                        bf = 495 / (1.29579 - 0.35004 * Math.log10(waist + hip - neck) + 0.22100 * Math.log10(height)) - 450;
                    }
                    String cat;
                    double low = male ? 18 : 25;
                    if (bf < (male ? 6 : 14)) {
                        cat = "Essential";
                    } else if (bf < low) {
                        cat = "Athletic";
                    } else if (bf < (male ? 25 : 32)) {
                        cat = "Fit";
                    } else {
                        cat = "High";
                    }
                    output.setText(new DecimalFormat("0.0").format(bf) + "%  " + cat);
                } catch (Exception e) {
                    output.setText("Check measurements");
                }
            }
        });
    }
    private static class HabitItem {
        String title;
        int streak;
        String lastDone;
        HabitItem(String t, int s, String l) {
            title = t;
            streak = s;
            lastDone = l;
        }
    }
    private void buildHabit(LinearLayout box) {
        addTitle(box, "Habit Tracker");
        final Gson gson = new Gson();
        final String prefsKey = "habits_json";
        final List<HabitItem> items = new ArrayList<>();
        try {
            String saved = getSharedPreferences("tools", MODE_PRIVATE).getString(prefsKey, "[]");
            List<HabitItem> loaded = gson.fromJson(saved, new TypeToken<List<HabitItem>>() {
            }.getType());
            if (loaded != null) {
                items.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
        final EditText input = makeInput(box, "New habit", InputType.TYPE_CLASS_TEXT);
        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(listBox);
        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        final Runnable persist = new Runnable() {
            public void run() {
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString(prefsKey, gson.toJson(items)).apply();
                } catch (Exception ignored) {
                }
            }
        };
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            public void run() {
                listBox.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    final int idx = i;
                    HabitItem item = items.get(i);
                    LinearLayout row = new LinearLayout(ToolRunnerActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView label = new TextView(ToolRunnerActivity.this);
                    label.setText(item.title + "\n" + item.streak + " day streak");
                    label.setTextSize(15);
                    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    MaterialButton doneBtn = new MaterialButton(ToolRunnerActivity.this);
                    doneBtn.setText(today.equals(item.lastDone) ? "Done" : "Check");
                    doneBtn.setEnabled(!today.equals(item.lastDone));
                    doneBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            HabitItem it = items.get(idx);
                            try {
                                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                Date last = f.parse(it.lastDone);
                                long gap = (f.parse(today).getTime() - last.getTime()) / 86400000L;
                                it.streak = gap == 1 ? it.streak + 1 : 1;
                            } catch (Exception e) {
                                it.streak = 1;
                            }
                            it.lastDone = today;
                            persist.run();
                            render[0].run();
                            vibrateTick();
                        }
                    });
                    row.addView(doneBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    MaterialButton del = new MaterialButton(ToolRunnerActivity.this);
                    del.setText("X");
                    del.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            items.remove(idx);
                            persist.run();
                            render[0].run();
                        }
                    });
                    row.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    listBox.addView(row);
                }
            }
        };
        render[0].run();
        MaterialButton addBtn = makeButton(box, "Add habit");
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.isEmpty()) {
                    return;
                }
                items.add(new HabitItem(t, 0, ""));
                input.setText("");
                persist.run();
                render[0].run();
            }
        });
    }
    private static class ExpenseItem {
        String label;
        double amount;
        ExpenseItem(String l, double a) {
            label = l;
            amount = a;
        }
    }
    private void buildExpense(LinearLayout box) {
        addTitle(box, "Expense Tracker");
        final Gson gson = new Gson();
        final String prefsKey = "expenses_json";
        final List<ExpenseItem> items = new ArrayList<>();
        try {
            String saved = getSharedPreferences("tools", MODE_PRIVATE).getString(prefsKey, "[]");
            List<ExpenseItem> loaded = gson.fromJson(saved, new TypeToken<List<ExpenseItem>>() {
            }.getType());
            if (loaded != null) {
                items.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
        final EditText labelInput = makeInput(box, "What for", InputType.TYPE_CLASS_TEXT);
        final EditText amountInput = makeInput(box, "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView totalText = makeOutput(box);
        final android.widget.ListView listView = new android.widget.ListView(this);
        final List<String> names = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        final Runnable refresh = new Runnable() {
            public void run() {
                names.clear();
                double total = 0;
                DecimalFormat df = new DecimalFormat("0.00");
                for (int i = items.size() - 1; i >= 0; i--) {
                    ExpenseItem e = items.get(i);
                    names.add(e.label + "  " + df.format(e.amount));
                    total += e.amount;
                }
                totalText.setText("Total " + df.format(total) + "  (" + items.size() + " items)");
                adapter.notifyDataSetChanged();
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString(prefsKey, gson.toJson(items)).apply();
                } catch (Exception ignored) {
                }
            }
        };
        refresh.run();
        MaterialButton addBtn = makeButton(box, "Add expense");
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String label = labelInput.getText().toString().trim();
                    double amount = Double.parseDouble(amountInput.getText().toString());
                    if (label.isEmpty()) {
                        label = "Expense";
                    }
                    items.add(new ExpenseItem(label, amount));
                    labelInput.setText("");
                    amountInput.setText("");
                    refresh.run();
                } catch (Exception e) {
                    toast("Enter an amount");
                }
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                items.remove(items.size() - 1 - position);
                refresh.run();
                return true;
            }
        });
        MaterialButton clearBtn = makeButton(box, "Clear all");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                items.clear();
                refresh.run();
            }
        });
    }
    private void buildUnitPrice(LinearLayout box) {
        addTitle(box, "Price Compare");
        final EditText priceA = makeInput(box, "Pack A price", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText qtyA = makeInput(box, "Pack A quantity", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText priceB = makeInput(box, "Pack B price", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText qtyB = makeInput(box, "Pack B quantity", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView output = makeOutput(box);
        MaterialButton goBtn = makeButton(box, "Compare");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    double pa = Double.parseDouble(priceA.getText().toString());
                    double qa = Double.parseDouble(qtyA.getText().toString());
                    double pb = Double.parseDouble(priceB.getText().toString());
                    double qb = Double.parseDouble(qtyB.getText().toString());
                    double ua = pa / qa;
                    double ub = pb / qb;
                    DecimalFormat df = new DecimalFormat("0.0000");
                    StringBuilder b = new StringBuilder();
                    b.append("A ").append(df.format(ua)).append(" per unit\nB ").append(df.format(ub)).append(" per unit\n");
                    if (ua < ub) {
                        b.append("A is cheaper by ").append(new DecimalFormat("0.0").format((ub - ua) / ub * 100)).append("%");
                    } else if (ub < ua) {
                        b.append("B is cheaper by ").append(new DecimalFormat("0.0").format((ua - ub) / ua * 100)).append("%");
                    } else {
                        b.append("Same value");
                    }
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Fill all four fields");
                }
            }
        });
    }
    private void buildScreenTest(LinearLayout box) {
        addTitle(box, "Screen Tester");
        addLabel(box, "Fill the screen with a solid color to spot dead pixels. Tap the color to exit.");
        final View fillView = new View(this);
        fillView.setVisibility(View.GONE);
        box.addView(fillView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
        LinearLayout row1 = makeRow(box);
        MaterialButton redBtn = makeRowButton(row1, "Red", 1f);
        MaterialButton greenBtn = makeRowButton(row1, "Green", 1f);
        MaterialButton blueBtn = makeRowButton(row1, "Blue", 1f);
        LinearLayout row2 = makeRow(box);
        MaterialButton whiteBtn = makeRowButton(row2, "White", 1f);
        MaterialButton blackBtn = makeRowButton(row2, "Black", 1f);
        MaterialButton grayBtn = makeRowButton(row2, "Gray", 1f);
        View.OnClickListener showColor = new View.OnClickListener() {
            public void onClick(View v) {
                fillView.setBackgroundColor(((ColorDrawableLike) v.getTag()).color);
                fillView.setVisibility(View.VISIBLE);
            }
        };
        redBtn.setTag(new ColorDrawableLike(Color.RED));
        greenBtn.setTag(new ColorDrawableLike(Color.GREEN));
        blueBtn.setTag(new ColorDrawableLike(Color.BLUE));
        whiteBtn.setTag(new ColorDrawableLike(Color.WHITE));
        blackBtn.setTag(new ColorDrawableLike(Color.BLACK));
        grayBtn.setTag(new ColorDrawableLike(Color.GRAY));
        redBtn.setOnClickListener(showColor);
        greenBtn.setOnClickListener(showColor);
        blueBtn.setOnClickListener(showColor);
        whiteBtn.setOnClickListener(showColor);
        blackBtn.setOnClickListener(showColor);
        grayBtn.setOnClickListener(showColor);
        fillView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fillView.setVisibility(View.GONE);
            }
        });
    }
    private static class ColorDrawableLike {
        final int color;
        ColorDrawableLike(int c) {
            color = c;
        }
    }
    private void vibratePattern(long[] pattern) {
        try {
            if (vibrator == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }
    private void buildVibration(LinearLayout box) {
        addTitle(box, "Vibration Studio");
        final EditText customInput = makeInput(box, "Custom pattern ms, e.g. 0,200,100,400", InputType.TYPE_CLASS_TEXT);
        customInput.setText("0,200,100,400");
        LinearLayout row1 = makeRow(box);
        MaterialButton shortBtn = makeRowButton(row1, "Short", 1f);
        MaterialButton longBtn = makeRowButton(row1, "Long", 1f);
        MaterialButton sosBtn = makeRowButton(row1, "SOS", 1f);
        LinearLayout row2 = makeRow(box);
        MaterialButton heartbeatBtn = makeRowButton(row2, "Heartbeat", 1f);
        MaterialButton customBtn = makeRowButton(row2, "Custom", 1f);
        MaterialButton stopBtn = makeRowButton(row2, "Stop", 1f);
        shortBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibratePattern(new long[]{0, 150});
            }
        });
        longBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibratePattern(new long[]{0, 600});
            }
        });
        sosBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibratePattern(new long[]{0, 150, 150, 150, 150, 150, 300, 400, 200, 400, 200, 400, 300, 150, 150, 150, 150, 150});
            }
        });
        heartbeatBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibratePattern(new long[]{0, 120, 120, 180, 400});
            }
        });
        customBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String[] parts = customInput.getText().toString().trim().split(",");
                    long[] pattern = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        pattern[i] = Math.max(0, Long.parseLong(parts[i].trim()));
                    }
                    vibratePattern(pattern);
                } catch (Exception e) {
                    toast("Use numbers separated by commas");
                }
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if (vibrator != null) {
                        vibrator.cancel();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }
    private void buildStrobe(LinearLayout box) {
        addTitle(box, "Strobe Light");
        addLabel(box, "Flashing light. Look away if sensitive.");
        final TextView hzLabel = addLabel(box, "Flashes per second: 4");
        SeekBar hzBar = new SeekBar(this);
        hzBar.setMax(9);
        hzBar.setProgress(3);
        box.addView(hzBar);
        strobeView = new View(this);
        strobeView.setBackgroundColor(Color.WHITE);
        box.addView(strobeView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
        hzBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                strobeHz = 1 + progress;
                hzLabel.setText("Flashes per second: " + strobeHz);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        final MaterialButton toggleBtn = makeButton(box, "Start");
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (strobeOn) {
                    strobeOn = false;
                    toggleBtn.setText("Start");
                    return;
                }
                strobeOn = true;
                toggleBtn.setText("Stop");
                if (strobeTick == null) {
                    strobeTick = new Runnable() {
                        public void run() {
                            if (!strobeOn || strobeView == null) {
                                return;
                            }
                            boolean white = strobeView.getTag() == null || "b".equals(strobeView.getTag());
                            strobeView.setBackgroundColor(white ? Color.BLACK : Color.WHITE);
                            strobeView.setTag(white ? "b" : "w");
                            handler.postDelayed(this, 1000L / Math.max(1, strobeHz * 2));
                        }
                    };
                }
                handler.post(strobeTick);
            }
        });
    }
    private void buildAltimeter(LinearLayout box) {
        addTitle(box, "Altimeter");
        final TextView output = makeOutput(box);
        output.setTextSize(28);
        output.setGravity(Gravity.CENTER);
        output.setText("Starting...");
        if (sensorManager == null) {
            output.setText("No sensors on this device");
            return;
        }
        Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressure == null) {
            output.setText("No barometer on this device");
            return;
        }
        try {
            if (activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        if (compassView != null || levelView != null) {
            return;
        }
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                float hpa = event.values[0];
                double altitude = 44330.0 * (1.0 - Math.pow(hpa / 1013.25, 0.1903));
                DecimalFormat df = new DecimalFormat("0.0");
                if (output != null) {
                    output.setText(df.format(altitude) + " m\n" + df.format(hpa) + " hPa");
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(activeListener, pressure, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            output.setText("Sensor error");
        }
    }
    private void buildNfc(LinearLayout box) {
        addTitle(box, "NFC Reader");
        nfcText = makeOutput(box);
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            if (adapter == null) {
                nfcText.setText("No NFC hardware on this device");
                return;
            }
            if (!adapter.isEnabled()) {
                nfcText.setText("Turn on NFC, then hold a tag to the phone");
            } else {
                nfcText.setText("Hold a tag to the phone");
            }
        } catch (Exception e) {
            nfcText.setText("NFC unavailable");
            return;
        }
        enableNfcDispatch();
        MaterialButton copyBtn = makeButton(box, "Copy tag info");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("nfc", nfcText.getText().toString());
            }
        });
    }
    private void buildBluetooth(LinearLayout box) {
        addTitle(box, "Paired Bluetooth");
        btText = makeOutput(box);
        final android.widget.ListView listView = new android.widget.ListView(this);
        btNames.clear();
        btAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, btNames);
        listView.setAdapter(btAdapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        MaterialButton refreshBtn = makeButton(box, "Refresh");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refreshBtList();
            }
        });
        MaterialButton openBtn = makeButton(box, "Open Bluetooth settings");
        openBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception e) {
                    toast("Cannot open settings");
                }
            }
        });
        refreshBtList();
    }
    private void buildApps(LinearLayout box) {
        addTitle(box, "Installed Apps");
        final EditText search = makeInput(box, "Search apps", InputType.TYPE_CLASS_TEXT);
        final android.widget.ListView listView = new android.widget.ListView(this);
        final List<android.content.pm.ApplicationInfo> all = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final List<android.content.pm.ApplicationInfo> visible = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        final android.content.pm.PackageManager pm = getPackageManager();
        final Runnable reload = new Runnable() {
            public void run() {
                all.clear();
                try {
                    all.addAll(pm.getInstalledApplications(0));
                } catch (Exception ignored) {
                }
                java.util.Collections.sort(all, new java.util.Comparator<android.content.pm.ApplicationInfo>() {
                    public int compare(android.content.pm.ApplicationInfo a, android.content.pm.ApplicationInfo b) {
                        return String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b)));
                    }
                });
                filterApps(pm, all, visible, names, search.getText().toString());
                adapter.notifyDataSetChanged();
            }
        };
        reload.run();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(pm, all, visible, names, s.toString());
                adapter.notifyDataSetChanged();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                try {
                    android.content.pm.ApplicationInfo app = visible.get(position);
                    Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                    if (launch != null) {
                        startActivity(launch);
                    } else {
                        Intent info = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + app.packageName));
                        startActivity(info);
                    }
                } catch (Exception e) {
                    toast("Cannot open");
                }
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                try {
                    android.content.pm.ApplicationInfo app = visible.get(position);
                    Intent del = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + app.packageName));
                    startActivity(del);
                } catch (Exception e) {
                    toast("Cannot uninstall");
                }
                return true;
            }
        });
        addLabel(box, "Tap to open, long-press to uninstall.");
    }
    private void filterApps(android.content.pm.PackageManager pm, List<android.content.pm.ApplicationInfo> all, List<android.content.pm.ApplicationInfo> visible, List<String> names, String query) {
        visible.clear();
        names.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (android.content.pm.ApplicationInfo app : all) {
            String label = String.valueOf(pm.getApplicationLabel(app));
            if (q.isEmpty() || label.toLowerCase(Locale.US).contains(q) || app.packageName.toLowerCase(Locale.US).contains(q)) {
                visible.add(app);
                names.add(label + "\n" + app.packageName);
            }
        }
    }
    private String readProcFile(String path) {
        StringBuilder b = new StringBuilder();
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(path));
            String line;
            while ((line = reader.readLine()) != null) {
                if (b.length() > 0) {
                    b.append("\n");
                }
                b.append(line);
                if (b.length() > 4000) {
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
        return b.toString();
    }
    private void buildCpuInfo(LinearLayout box) {
        addTitle(box, "CPU Info");
        final TextView output = makeOutput(box);
        try {
            StringBuilder b = new StringBuilder();
            b.append("Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
            String[] abis = Build.SUPPORTED_ABIS;
            b.append("ABI: ");
            for (int i = 0; i < abis.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(abis[i]);
            }
            b.append("\nHardware: ").append(Build.HARDWARE).append("  Board: ").append(Build.BOARD).append("\n");
            try {
                String freq = readProcFile("sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").trim();
                if (!freq.isEmpty()) {
                    b.append("CPU0 now: ").append(Long.parseLong(freq) / 1000).append(" MHz\n");
                }
            } catch (Exception ignored) {
            }
            String cpuinfo = readProcFile("proc/cpuinfo");
            int shown = 0;
            for (String line : cpuinfo.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Processor") || t.startsWith("Hardware") || t.startsWith("model name")) {
                    b.append(t).append("\n");
                    shown++;
                    if (shown >= 6) {
                        break;
                    }
                }
            }
            output.setText(b.toString().trim());
        } catch (Exception e) {
            output.setText("Unavailable");
        }
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("cpu", output.getText().toString());
            }
        });
    }
    private void buildClipboard(LinearLayout box) {
        addTitle(box, "Clipboard History");
        final Gson gson = new Gson();
        final String prefsKey = "clips_json";
        final List<String> clips = new ArrayList<>();
        try {
            String saved = getSharedPreferences("tools", MODE_PRIVATE).getString(prefsKey, "[]");
            List<String> loaded = gson.fromJson(saved, new TypeToken<List<String>>() {
            }.getType());
            if (loaded != null) {
                clips.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, clips);
        final android.widget.ListView listView = new android.widget.ListView(this);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        final Runnable persist = new Runnable() {
            public void run() {
                List<String> trimmed = clips.size() > 50 ? clips.subList(0, 50) : clips;
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString(prefsKey, gson.toJson(trimmed)).apply();
                } catch (Exception ignored) {
                }
            }
        };
        final Runnable saveCurrent = new Runnable() {
            public void run() {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence text = cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0 ? null : cm.getPrimaryClip().getItemAt(0).coerceToText(ToolRunnerActivity.this);
                    if (text != null) {
                        String s = text.toString();
                        if (!s.isEmpty() && (clips.isEmpty() || !clips.get(0).equals(s))) {
                            clips.add(0, s.length() > 200 ? s.substring(0, 200) : s);
                            adapter.notifyDataSetChanged();
                            persist.run();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        };
        saveCurrent.run();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipListener = new android.content.ClipboardManager.OnPrimaryClipChangedListener() {
                public void onPrimaryClipChanged() {
                    saveCurrent.run();
                }
            };
            cm.addPrimaryClipChangedListener(clipListener);
        } catch (Exception ignored) {
        }
        MaterialButton saveBtn = makeButton(box, "Save current clip");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveCurrent.run();
            }
        });
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                copyText("clip", clips.get(position));
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                clips.remove(position);
                adapter.notifyDataSetChanged();
                persist.run();
                return true;
            }
        });
        MaterialButton clearBtn = makeButton(box, "Clear history");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clips.clear();
                adapter.notifyDataSetChanged();
                persist.run();
            }
        });
    }
    private void buildDataUsage(LinearLayout box) {
        addTitle(box, "Data Usage");
        addLabel(box, "Counters reset on reboot.");
        final TextView output = makeOutput(box);
        final Runnable refresh = new Runnable() {
            public void run() {
                try {
                    StringBuilder b = new StringBuilder();
                    b.append("Mobile down ").append(formatBytes(TrafficStats.getMobileRxBytes())).append("\n");
                    b.append("Mobile up ").append(formatBytes(TrafficStats.getMobileTxBytes())).append("\n");
                    b.append("Total down ").append(formatBytes(TrafficStats.getTotalRxBytes())).append("\n");
                    b.append("Total up ").append(formatBytes(TrafficStats.getTotalTxBytes())).append("\n");
                    int uid = android.os.Process.myUid();
                    b.append("This app down ").append(formatBytes(TrafficStats.getUidRxBytes(uid))).append("\n");
                    b.append("This app up ").append(formatBytes(TrafficStats.getUidTxBytes(uid)));
                    output.setText(b.toString());
                } catch (Exception e) {
                    output.setText("Unavailable");
                }
            }
        };
        refresh.run();
        MaterialButton refreshBtn = makeButton(box, "Refresh");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh.run();
            }
        });
    }
    private void buildVolume(LinearLayout box) {
        addTitle(box, "Volume Panel");
        final android.media.AudioManager audio = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            TextView t = makeOutput(box);
            t.setText("Audio service unavailable");
            return;
        }
        int[] streams = new int[]{AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};
        String[] names = new String[]{"Music", "Alarm", "Ring", "Notify"};
        for (int i = 0; i < streams.length; i++) {
            final int stream = streams[i];
            addLabel(box, names[i]);
            SeekBar bar = new SeekBar(this);
            try {
                bar.setMax(audio.getStreamMaxVolume(stream));
                bar.setProgress(audio.getStreamVolume(stream));
            } catch (Exception ignored) {
            }
            final int[] last = new int[]{bar.getProgress()};
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    if (fromUser) {
                        try {
                            audio.setStreamVolume(stream, progress, 0);
                        } catch (Exception ignored) {
                        }
                    }
                    last[0] = progress;
                }
                public void onStartTrackingTouch(SeekBar s) {
                }
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            box.addView(bar);
        }
        MaterialButton muteBtn = makeButton(box, "Mute music stream");
        muteBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                    toast("Music muted, use sliders to restore");
                } catch (Exception e) {
                    toast("Failed");
                }
            }
        });
    }
    private void buildRingtone(LinearLayout box) {
        addTitle(box, "Ringtone Preview");
        final String[] types = new String[]{"Ringtones", "Alarms", "Notifications"};
        final int[] typeVals = new int[]{RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION};
        final Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        box.addView(typeSpinner);
        final android.widget.ListView listView = new android.widget.ListView(this);
        final List<String> names = new ArrayList<>();
        final List<android.net.Uri> uris = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        final Ringtone[] current = new Ringtone[]{null};
        final Runnable load = new Runnable() {
            public void run() {
                names.clear();
                uris.clear();
                try {
                    RingtoneManager manager = new RingtoneManager(ToolRunnerActivity.this);
                    manager.setType(typeVals[typeSpinner.getSelectedItemPosition()]);
                    android.database.Cursor cursor = manager.getCursor();
                    while (cursor.moveToNext()) {
                        names.add(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX));
                        uris.add(manager.getRingtoneUri(cursor.getPosition()));
                    }
                    try {
                        cursor.close();
                    } catch (Exception ignored) {
                    }
                } catch (Exception ignored) {
                }
                adapter.notifyDataSetChanged();
            }
        };
        load.run();
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                load.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (current[0] != null) {
                        current[0].stop();
                    }
                    current[0] = RingtoneManager.getRingtone(ToolRunnerActivity.this, uris.get(position));
                    current[0].play();
                } catch (Exception e) {
                    toast("Play failed");
                }
            }
        });
        MaterialButton stopBtn = makeButton(box, "Stop preview");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if (current[0] != null) {
                        current[0].stop();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }
    private void buildWallpaper(LinearLayout box) {
        addTitle(box, "Wallpaper Maker");
        final int[] picks = new int[]{Color.parseColor("#1B73E8"), Color.parseColor("#0D652D"), Color.parseColor("#A50E0E"), Color.parseColor("#681DA8"), Color.parseColor("#000000"), Color.parseColor("#FFFFFF")};
        final int[] first = new int[]{picks[0]};
        final int[] second = new int[]{picks[3]};
        final boolean[] gradient = new boolean[]{true};
        LinearLayout row1 = makeRow(box);
        for (int c : picks) {
            Button sw = new Button(this);
            sw.setBackgroundColor(c);
            final int color = c;
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    first[0] = color;
                }
            });
            row1.addView(sw, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        addLabel(box, "Tap a swatch for the first color.");
        LinearLayout row2 = makeRow(box);
        for (int c : picks) {
            Button sw = new Button(this);
            sw.setBackgroundColor(c);
            final int color = c;
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    second[0] = color;
                }
            });
            row2.addView(sw, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        addLabel(box, "Tap a swatch for the second color.");
        final CheckBox gradientBox = new CheckBox(this);
        gradientBox.setText("Gradient blend");
        gradientBox.setChecked(true);
        box.addView(gradientBox);
        final android.widget.ImageView preview = new android.widget.ImageView(this);
        box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
        final Runnable render = new Runnable() {
            public void run() {
                try {
                    int w = 540;
                    int h = 960;
                    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    if (gradientBox.isChecked()) {
                        for (int y = 0; y < h; y++) {
                            float t = y / (float) h;
                            int r = Math.round(Color.red(first[0]) * (1 - t) + Color.red(second[0]) * t);
                            int g = Math.round(Color.green(first[0]) * (1 - t) + Color.green(second[0]) * t);
                            int b = Math.round(Color.blue(first[0]) * (1 - t) + Color.blue(second[0]) * t);
                            int color = Color.rgb(r, g, b);
                            for (int x = 0; x < w; x++) {
                                bmp.setPixel(x, y, color);
                            }
                        }
                    } else {
                        bmp.eraseColor(first[0]);
                    }
                    preview.setImageBitmap(bmp);
                    preview.setTag(bmp);
                } catch (Exception e) {
                    toast("Render failed");
                }
            }
        };
        gradientBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                gradient[0] = checked;
                render.run();
            }
        });
        render.run();
        MaterialButton applyBtn = makeButton(box, "Set as wallpaper");
        applyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Object tag = preview.getTag();
                    if (!(tag instanceof android.graphics.Bitmap)) {
                        return;
                    }
                    WallpaperManager wm = WallpaperManager.getInstance(ToolRunnerActivity.this);
                    wm.setBitmap((android.graphics.Bitmap) tag);
                    toast("Wallpaper set");
                } catch (Exception e) {
                    toast("Failed, check wallpaper permission");
                }
            }
        });
    }
    private void buildQuickSettings(LinearLayout box) {
        addTitle(box, "System Shortcuts");
        String[][] entries = new String[][]{
                {"Wi-Fi settings", android.provider.Settings.ACTION_WIFI_SETTINGS},
                {"Bluetooth settings", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS},
                {"Display settings", android.provider.Settings.ACTION_DISPLAY_SETTINGS},
                {"Sound settings", android.provider.Settings.ACTION_SOUND_SETTINGS},
                {"Date and time", android.provider.Settings.ACTION_DATE_SETTINGS},
                {"Storage", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
                {"Location", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS},
                {"App info (this app)", android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS},
                {"All apps", android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS}
        };
        for (String[] entry : entries) {
            final String action = entry[1];
            final String label = entry[0];
            MaterialButton b = makeButton(box, label);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(action);
                        if (label.startsWith("App info")) {
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        }
                        startActivity(intent);
                    } catch (Exception e) {
                        toast("Cannot open");
                    }
                }
            });
        }
    }
    private void buildAttendance(LinearLayout box) {
        addTitle(box, "Attendance Tracker");
        final EditText presentInput = makeInput(box, "Classes attended", InputType.TYPE_CLASS_NUMBER);
        final EditText totalInput = makeInput(box, "Total classes", InputType.TYPE_CLASS_NUMBER);
        final TextView reqLabel = addLabel(box, "Required: 75%");
        SeekBar reqBar = new SeekBar(this);
        reqBar.setMax(40);
        reqBar.setProgress(15);
        box.addView(reqBar);
        final int[] required = new int[]{75};
        final TextView output = makeOutput(box);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    int present = Integer.parseInt(presentInput.getText().toString());
                    int total = Integer.parseInt(totalInput.getText().toString());
                    if (total <= 0 || present < 0 || present > total) {
                        output.setText("Check numbers");
                        return;
                    }
                    double pct = present * 100.0 / total;
                    DecimalFormat df = new DecimalFormat("0.0");
                    StringBuilder b = new StringBuilder();
                    b.append("Now ").append(df.format(pct)).append("%\n");
                    if (pct >= required[0]) {
                        int bunk = 0;
                        while ((present * 100.0 / (total + bunk + 1)) >= required[0]) {
                            bunk++;
                        }
                        b.append("Safe. You can skip ").append(bunk).append(" classes.");
                    } else {
                        int need = 0;
                        while (((present + need) * 100.0 / (total + need)) < required[0]) {
                            need++;
                            if (need > 1000) {
                                break;
                            }
                        }
                        b.append("Short. Attend next ").append(need).append(" classes.");
                    }
                    output.setText(b.toString());
                    try {
                        getSharedPreferences("tools", MODE_PRIVATE).edit().putInt("att_present", present).putInt("att_total", total).apply();
                    } catch (Exception ignored) {
                    }
                } catch (Exception e) {
                    output.setText("Enter numbers");
                }
            }
        };
        try {
            presentInput.setText(String.valueOf(getSharedPreferences("tools", MODE_PRIVATE).getInt("att_present", 0)));
            totalInput.setText(String.valueOf(getSharedPreferences("tools", MODE_PRIVATE).getInt("att_total", 0)));
        } catch (Exception ignored) {
        }
        reqBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                required[0] = 60 + progress;
                reqLabel.setText("Required: " + required[0] + "%");
                compute.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        };
        presentInput.addTextChangedListener(watcher);
        totalInput.addTextChangedListener(watcher);
        compute.run();
    }
    private void buildTyping(LinearLayout box) {
        addTitle(box, "Typing Test");
        final String target = "The quick brown fox jumps over the lazy dog near the quiet river bank at dawn";
        TextView targetView = makeOutput(box);
        targetView.setText(target);
        final EditText input = makeInput(box, "Type here", InputType.TYPE_CLASS_TEXT);
        final TextView stats = makeOutput(box);
        stats.setText("Start typing to begin");
        final long[] startAt = new long[]{0};
        final boolean[] active = new boolean[]{false};
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String typed = s.toString();
                if (!active[0] && typed.length() > 0) {
                    active[0] = true;
                    startAt[0] = SystemClock.elapsedRealtime();
                }
                if (!active[0]) {
                    return;
                }
                int correct = 0;
                int n = Math.min(typed.length(), target.length());
                for (int i = 0; i < n; i++) {
                    if (typed.charAt(i) == target.charAt(i)) {
                        correct++;
                    }
                }
                double minutes = Math.max(1, SystemClock.elapsedRealtime() - startAt[0]) / 60000.0;
                double wpm = (typed.length() / 5.0) / minutes;
                double acc = typed.length() == 0 ? 100 : correct * 100.0 / typed.length();
                DecimalFormat df = new DecimalFormat("0");
                stats.setText(df.format(wpm) + " WPM  " + df.format(acc) + "% accuracy");
                if (typed.equals(target)) {
                    active[0] = false;
                    stats.setText("Done. " + df.format(wpm) + " WPM  " + df.format(acc) + "% accuracy");
                    vibrateTick();
                }
            }
            public void afterTextChanged(Editable s) {
            }
        });
        MaterialButton resetBtn = makeButton(box, "Reset");
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                input.setText("");
                active[0] = false;
                stats.setText("Start typing to begin");
            }
        });
    }
    private void buildReaction(LinearLayout box) {
        addTitle(box, "Reaction Test");
        addLabel(box, "Tap Start, wait for green, then tap fast. Tapping red fails.");
        reactionPad = new View(this);
        reactionPad.setBackgroundColor(Color.parseColor("#D93025"));
        box.addView(reactionPad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
        reactionText = makeOutput(box);
        reactionText.setGravity(Gravity.CENTER);
        reactionText.setText("Press Start");
        reactionState = 0;
        long best = 0;
        try {
            best = getSharedPreferences("tools", MODE_PRIVATE).getLong("reaction_best", 0);
        } catch (Exception ignored) {
        }
        final long[] bestRef = new long[]{best};
        if (best > 0) {
            reactionText.setText("Press Start\nBest " + best + " ms");
        }
        LinearLayout row = makeRow(box);
        MaterialButton startBtn = makeRowButton(row, "Start", 1f);
        MaterialButton resetBtn = makeRowButton(row, "Reset best", 1f);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reactionState = 1;
                reactionPad.setBackgroundColor(Color.parseColor("#D93025"));
                reactionText.setText("Wait for green...");
                if (reactionPending != null) {
                    handler.removeCallbacks(reactionPending);
                }
                reactionPending = new Runnable() {
                    public void run() {
                        if (reactionState != 1) {
                            return;
                        }
                        reactionState = 2;
                        reactionShownAt = SystemClock.elapsedRealtime();
                        reactionPad.setBackgroundColor(Color.parseColor("#0D652D"));
                        reactionText.setText("TAP NOW");
                    }
                };
                handler.postDelayed(reactionPending, 1500 + new Random().nextInt(2500));
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bestRef[0] = 0;
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putLong("reaction_best", 0).apply();
                } catch (Exception ignored) {
                }
                reactionText.setText("Press Start");
            }
        });
        reactionPad.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (reactionState == 1) {
                    reactionState = 0;
                    if (reactionPending != null) {
                        handler.removeCallbacks(reactionPending);
                    }
                    reactionText.setText("Too early, press Start again");
                } else if (reactionState == 2) {
                    long ms = SystemClock.elapsedRealtime() - reactionShownAt;
                    reactionState = 0;
                    reactionPad.setBackgroundColor(Color.parseColor("#D93025"));
                    StringBuilder b = new StringBuilder();
                    b.append(ms).append(" ms");
                    if (bestRef[0] == 0 || ms < bestRef[0]) {
                        bestRef[0] = ms;
                        b.append("  New best");
                        try {
                            getSharedPreferences("tools", MODE_PRIVATE).edit().putLong("reaction_best", ms).apply();
                        } catch (Exception ignored) {
                        }
                    } else {
                        b.append("  Best ").append(bestRef[0]).append(" ms");
                    }
                    reactionText.setText(b.toString());
                    vibrateTick();
                }
            }
        });
    }
    private void buildMemory(LinearLayout box) {
        addTitle(box, "Memory Game");
        addLabel(box, "Watch the flashing tiles, then repeat the sequence.");
        memText = makeOutput(box);
        memText.setGravity(Gravity.CENTER);
        memText.setText("Score 0");
        memScore = 0;
        memSeq.clear();
        memButtons.clear();
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            Button tile = new Button(this);
            tile.setText(String.valueOf(i + 1));
            tile.setMinHeight(dp(64));
            android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
            p.width = 0;
            p.columnSpec = android.widget.GridLayout.spec(i % 3, 1f);
            p.height = dp(64);
            int m = dp(4);
            p.setMargins(m, m, m, m);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onMemoryTap(idx);
                }
            });
            memButtons.add(tile);
            grid.addView(tile, p);
        }
        MaterialButton startBtn = makeButton(box, "Start new game");
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                memScore = 0;
                memSeq.clear();
                memText.setText("Score 0");
                extendMemorySequence();
            }
        });
    }
    private void extendMemorySequence() {
        memSeq.add(new Random().nextInt(9));
        memPos = 0;
        memAccept = false;
        memText.setText("Watch... (" + memSeq.size() + " steps)");
        for (int i = 0; i < memSeq.size(); i++) {
            final int step = i;
            handler.postDelayed(new Runnable() {
                public void run() {
                    flashMemoryTile(memSeq.get(step));
                    if (step == memSeq.size() - 1) {
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                memAccept = true;
                                memText.setText("Your turn (" + memSeq.size() + " steps)");
                            }
                        }, 500);
                    }
                }
            }, 600L * (i + 1));
        }
    }
    private void flashMemoryTile(int idx) {
        try {
            final Button tile = memButtons.get(idx);
            tile.setBackgroundColor(Color.parseColor("#1B73E8"));
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        tile.setBackgroundResource(android.R.drawable.btn_default);
                    } catch (Exception ignored) {
                    }
                }
            }, 350);
            vibrateTick();
        } catch (Exception ignored) {
        }
    }
    private void onMemoryTap(int idx) {
        if (!memAccept || memSeq.isEmpty()) {
            return;
        }
        if (memSeq.get(memPos) == idx) {
            memPos++;
            flashMemoryTile(idx);
            if (memPos >= memSeq.size()) {
                memAccept = false;
                memScore++;
                memText.setText("Score " + memScore);
                handler.postDelayed(new Runnable() {
                    public void run() {
                        extendMemorySequence();
                    }
                }, 800);
            }
        } else {
            memAccept = false;
            memText.setText("Game over. Score " + memScore + ". Start again.");
            memSeq.clear();
        }
    }
    private void buildTicTacToe(LinearLayout box) {
        addTitle(box, "Tic-Tac-Toe");
        addLabel(box, "You play X, computer plays O.");
        tttText = makeOutput(box);
        tttText.setGravity(Gravity.CENTER);
        tttButtons.clear();
        for (int i = 0; i < 9; i++) {
            tttBoard[i] = "";
        }
        tttOver = false;
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            Button cell = new Button(this);
            cell.setText("");
            cell.setTextSize(32);
            cell.setMinHeight(dp(72));
            android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
            p.width = 0;
            p.columnSpec = android.widget.GridLayout.spec(i % 3, 1f);
            p.height = dp(72);
            int m = dp(4);
            p.setMargins(m, m, m, m);
            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onTttTap(idx);
                }
            });
            tttButtons.add(cell);
            grid.addView(cell, p);
        }
        updateTttText();
        MaterialButton resetBtn = makeButton(box, "New game");
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < 9; i++) {
                    tttBoard[i] = "";
                    tttButtons.get(i).setText("");
                }
                tttOver = false;
                updateTttText();
            }
        });
    }
    private void onTttTap(int idx) {
        if (tttOver || !tttBoard[idx].isEmpty()) {
            return;
        }
        tttBoard[idx] = "X";
        tttButtons.get(idx).setText("X");
        if (tttWinner("X")) {
            tttOver = true;
            tttWins++;
            updateTttText();
            return;
        }
        if (tttFull()) {
            tttOver = true;
            tttDraws++;
            updateTttText();
            return;
        }
        int move = tttBestMove();
        tttBoard[move] = "O";
        tttButtons.get(move).setText("O");
        if (tttWinner("O")) {
            tttOver = true;
            tttLosses++;
        } else if (tttFull()) {
            tttOver = true;
            tttDraws++;
        }
        updateTttText();
    }
    private boolean tttWinner(String p) {
        int[][] lines = new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        for (int[] line : lines) {
            if (tttBoard[line[0]].equals(p) && tttBoard[line[1]].equals(p) && tttBoard[line[2]].equals(p)) {
                return true;
            }
        }
        return false;
    }
    private boolean tttFull() {
        for (String s : tttBoard) {
            if (s.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    private int tttBestMove() {
        for (int i = 0; i < 9; i++) {
            if (tttBoard[i].isEmpty()) {
                tttBoard[i] = "O";
                boolean win = tttWinner("O");
                tttBoard[i] = "";
                if (win) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (tttBoard[i].isEmpty()) {
                tttBoard[i] = "X";
                boolean win = tttWinner("X");
                tttBoard[i] = "";
                if (win) {
                    return i;
                }
            }
        }
        if (tttBoard[4].isEmpty()) {
            return 4;
        }
        int[] corners = new int[]{0, 2, 6, 8};
        List<Integer> free = new ArrayList<>();
        for (int c : corners) {
            if (tttBoard[c].isEmpty()) {
                free.add(c);
            }
        }
        if (!free.isEmpty()) {
            return free.get(new Random().nextInt(free.size()));
        }
        for (int i = 0; i < 9; i++) {
            if (tttBoard[i].isEmpty()) {
                return i;
            }
        }
        return 0;
    }
    private void updateTttText() {
        if (tttText == null) {
            return;
        }
        String state = tttOver ? (tttWinner("X") ? "You win" : (tttWinner("O") ? "Computer wins" : "Draw")) : "Your move";
        tttText.setText(state + "\nYou " + tttWins + "  CPU " + tttLosses + "  Draw " + tttDraws);
    }
    private void buildLottery(LinearLayout box) {
        addTitle(box, "Lottery Picker");
        final EditText countInput = makeInput(box, "Numbers to pick", InputType.TYPE_CLASS_NUMBER);
        countInput.setText("6");
        final EditText maxInput = makeInput(box, "Max number", InputType.TYPE_CLASS_NUMBER);
        maxInput.setText("49");
        final TextView output = makeOutput(box);
        output.setTextSize(24);
        output.setGravity(Gravity.CENTER);
        output.setText("-");
        MaterialButton goBtn = makeButton(box, "Pick numbers");
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    int count = Integer.parseInt(countInput.getText().toString().trim());
                    int max = Integer.parseInt(maxInput.getText().toString().trim());
                    if (count <= 0 || max <= 0 || count > max || count > 20) {
                        output.setText("Count must be 1 to 20 and not above max");
                        return;
                    }
                    List<Integer> pool = new ArrayList<>();
                    for (int i = 1; i <= max; i++) {
                        pool.add(i);
                    }
                    java.util.Collections.shuffle(pool);
                    List<Integer> picked = pool.subList(0, count);
                    java.util.Collections.sort(picked);
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < picked.size(); i++) {
                        if (i > 0) {
                            b.append("  ");
                        }
                        b.append(picked.get(i));
                    }
                    output.setText(b.toString());
                    vibrateTick();
                } catch (Exception e) {
                    output.setText("Enter count and max");
                }
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("lottery", output.getText().toString());
            }
        });
    }
    private void buildMoon(LinearLayout box) {
        addTitle(box, "Moon Phase");
        final EditText dateInput = makeInput(box, "Date yyyy-MM-dd, empty for today", InputType.TYPE_CLASS_DATETIME);
        final TextView output = makeOutput(box);
        output.setTextSize(20);
        output.setGravity(Gravity.CENTER);
        final Runnable compute = new Runnable() {
            public void run() {
                try {
                    Date date = new Date();
                    String raw = dateInput.getText().toString().trim();
                    if (!raw.isEmpty()) {
                        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        f.setLenient(false);
                        date = f.parse(raw);
                    }
                    double known = Date.UTC(100, 0, 6, 18, 14, 0);
                    double days = (date.getTime() - known) / 86400000.0;
                    double age = ((days % 29.530588853) + 29.530588853) % 29.530588853;
                    int phase = (int) Math.floor((age / 29.530588853) * 8 + 0.5) % 8;
                    String[] names = new String[]{"New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous", "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent"};
                    String[] icons = new String[]{"●", "◐", "◑", "◒", "○", "◓", "◑", "◐"};
                    double illum = (1 - Math.cos(2 * Math.PI * age / 29.530588853)) / 2 * 100;
                    output.setText(icons[phase] + "\n" + names[phase] + "\nMoon age " + new DecimalFormat("0.0").format(age) + " days  (" + new DecimalFormat("0").format(illum) + "% lit)");
                } catch (Exception e) {
                    output.setText("Use yyyy-MM-dd");
                }
            }
        };
        dateInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        MaterialButton copyBtn = makeButton(box, "Copy");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("moon", output.getText().toString());
            }
        });
    }
    private void buildEventCount(LinearLayout box) {
        addTitle(box, "Event Countdown");
        final EditText titleInput = makeInput(box, "Event name", InputType.TYPE_CLASS_TEXT);
        final EditText dateInput = makeInput(box, "Date yyyy-MM-dd HH:mm", InputType.TYPE_CLASS_DATETIME);
        try {
            String savedTitle = getSharedPreferences("tools", MODE_PRIVATE).getString("event_title", "");
            String savedDate = getSharedPreferences("tools", MODE_PRIVATE).getString("event_date", "");
            titleInput.setText(savedTitle);
            dateInput.setText(savedDate);
        } catch (Exception ignored) {
        }
        final TextView output = makeOutput(box);
        output.setTextSize(24);
        output.setGravity(Gravity.CENTER);
        final Runnable ticker = new Runnable() {
            public void run() {
                try {
                    String raw = dateInput.getText().toString().trim();
                    if (raw.isEmpty()) {
                        output.setText("Enter event date");
                        return;
                    }
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    f.setLenient(false);
                    Date target = f.parse(raw);
                    long diff = target.getTime() - System.currentTimeMillis();
                    String name = titleInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Event";
                    }
                    if (diff <= 0) {
                        output.setText(name + "\nHappening now or passed");
                        return;
                    }
                    long days = diff / 86400000L;
                    long hours = (diff % 86400000L) / 3600000L;
                    long mins = (diff % 3600000L) / 60000L;
                    long secs = (diff % 60000L) / 1000L;
                    output.setText(name + "\n" + days + "d " + String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs));
                } catch (Exception e) {
                    output.setText("Use yyyy-MM-dd HH:mm");
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ticker);
        MaterialButton saveBtn = makeButton(box, "Save event");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    getSharedPreferences("tools", MODE_PRIVATE).edit().putString("event_title", titleInput.getText().toString().trim()).putString("event_date", dateInput.getText().toString().trim()).apply();
                    toast("Saved");
                } catch (Exception e) {
                    toast("Save failed");
                }
            }
        });
    }
    private void buildQrGen(LinearLayout box) {
        addTitle(box, "QR Generator");
        final EditText input = makeInput(box, "Text, URL or WIFI config", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        qrGenView = new android.widget.ImageView(this);
        qrGenView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
        vp.gravity = Gravity.CENTER;
        vp.setMargins(0, dp(8), 0, dp(8));
        box.addView(qrGenView, vp);
        qrGenView.setVisibility(View.GONE);
        LinearLayout row = makeRow(box);
        MaterialButton genBtn = makeRowButton(row, "Generate", 1f);
        MaterialButton saveBtn = makeRowButton(row, "Save", 1f);
        MaterialButton shareBtn = makeRowButton(row, "Share", 1f);
        genBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    toast("Enter text first");
                    return;
                }
                try {
                    qrGenBitmap = QrUtil.generate(text, 1024);
                    qrGenView.setImageBitmap(qrGenBitmap);
                    qrGenView.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    toast("QR failed: " + e.getMessage());
                }
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (qrGenBitmap == null) {
                    toast("Generate first");
                    return;
                }
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            QrUtil.saveToGallery(ToolRunnerActivity.this, qrGenBitmap, "qr_" + System.currentTimeMillis());
                            handler.post(new Runnable() {
                                public void run() {
                                    toast("QR image saved");
                                }
                            });
                        } catch (final Exception e) {
                            handler.post(new Runnable() {
                                public void run() {
                                    toast("Save failed: " + e.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        });
        shareBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    toast("Enter text first");
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(share, "Share"));
            }
        });
        MaterialButton copyBtn = makeButton(box, "Copy text");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText("qr", input.getText().toString());
            }
        });
        addLabel(box, "Wi-Fi shortcut: WIFI:T:WPA;S:MyNet;P:pass123;;");
    }

    private void buildQrScan(LinearLayout box) {
        addTitle(box, "QR Scanner");
        MaterialButton scanBtn = makeButton(box, "Scan with camera");
        scanBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(ToolRunnerActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingQrScan = true;
                    ActivityCompat.requestPermissions(ToolRunnerActivity.this, new String[]{android.Manifest.permission.CAMERA}, 9005);
                    return;
                }
                startQrScan();
            }
        });
        qrScanOutput = makeOutput(box);
        qrScanOutput.setText("No scan yet");
        LinearLayout row = makeRow(box);
        MaterialButton copyBtn = makeRowButton(row, "Copy", 1f);
        MaterialButton shareBtn = makeRowButton(row, "Share", 1f);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (qrScanOutput != null) copyText("qr", qrScanOutput.getText().toString());
            }
        });
        shareBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (qrScanOutput == null) return;
                String text = qrScanOutput.getText().toString();
                if (text.isEmpty()) {
                    toast("Nothing to share");
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(share, "Share scan"));
            }
        });
    }

    private void startQrScan() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan a QR code");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch (Exception e) {
            toast("Scanner failed: " + e.getMessage());
        }
    }

    private static class StackHelper {
        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();
    }
}
