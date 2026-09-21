package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class WifiPasswordUtil {

    public static class WifiEntry {
        public final String ssid;
        public final String password;
        public final String security;

        public WifiEntry(String ssid, String password, String security) {
            this.ssid = ssid == null ? "" : ssid;
            this.password = password == null ? "" : password;
            this.security = security == null ? "" : security;
        }
    }

    public static boolean isRooted(Context context) {
        try {
            return RootManager.getInstance(context).isRootAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    public static List<WifiEntry> loadWifiPasswords(Context context) {
        Map<String, WifiEntry> merged = new LinkedHashMap<>();
        String[] xmlPaths = new String[]{
                "/data/misc/wifi/WifiConfigStore.xml",
                "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
                "/data/wifi/WifiConfigStore.xml",
                "/data/misc/wifi/WifiConfigStore.xml.encrypted"
        };
        String[] confPaths = new String[]{
                "/data/misc/wifi/wpa_supplicant.conf",
                "/data/misc/wifi/p2p_supplicant.conf",
                "/data/misc/wifi/sockets/wpa_supplicant.conf"
        };
        for (String path : xmlPaths) {
            try {
                String content = runSuCat(context, path);
                if (content != null && content.contains("WifiConfiguration")) {
                    parseConfigStore(content, merged);
                }
            } catch (Exception ignored) {
            }
        }
        for (String path : confPaths) {
            try {
                String content = runSuCat(context, path);
                if (content != null && content.contains("network=")) {
                    parseSupplicant(content, merged);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String runSuCat(Context context, String path) {
        String su = "su";
        try {
            su = RootManager.getInstance(context).suBinary();
        } catch (Exception ignored) {
        }
        if (su == null || su.trim().isEmpty()) {
            su = "su";
        }
        Process process = null;
        try {
            String quoted = RootManager.escapeShellArg(path);
            process = Runtime.getRuntime().exec(new String[]{su, "-c", "cat " + quoted});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            long total = 0;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > 2097152) {
                    break;
                }
                builder.append(buffer, 0, read);
            }
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            String result = builder.toString();
            if (result.trim().isEmpty()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        v = v.replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'");
        return v.trim();
    }

    private static void putEntry(Map<String, WifiEntry> merged, String ssid, String password, String security) {
        if (ssid == null) {
            return;
        }
        String cleanSsid = stripQuotes(ssid);
        if (cleanSsid.isEmpty()) {
            return;
        }
        String cleanPass = stripQuotes(password);
        String sec = security == null ? "" : security;
        if (cleanPass.isEmpty()) {
            sec = "Open";
        } else if (sec.isEmpty()) {
            sec = "WPA/WPA2";
        }
        WifiEntry existing = merged.get(cleanSsid);
        if (existing == null) {
            merged.put(cleanSsid, new WifiEntry(cleanSsid, cleanPass, sec));
            return;
        }
        if (existing.password.isEmpty() && !cleanPass.isEmpty()) {
            merged.put(cleanSsid, new WifiEntry(cleanSsid, cleanPass, sec));
        }
    }

    private static void parseSupplicant(String content, Map<String, WifiEntry> merged) {
        try {
            Pattern blockPattern = Pattern.compile("network\\s*=\\s*\\{([^}]+)\\}", Pattern.DOTALL);
            Matcher blockMatcher = blockPattern.matcher(content);
            while (blockMatcher.find()) {
                String block = blockMatcher.group(1);
                String ssid = extractQuoted(block, "ssid");
                if (ssid.isEmpty()) {
                    ssid = extractUnquoted(block, "ssid");
                }
                if (ssid.isEmpty()) {
                    continue;
                }
                String psk = extractQuoted(block, "psk");
                String sae = extractQuoted(block, "sae_password");
                if (sae.isEmpty()) {
                    sae = extractQuoted(block, "password");
                }
                String wep0 = extractQuoted(block, "wep_key0");
                if (wep0.isEmpty()) {
                    wep0 = extractUnquoted(block, "wep_key0");
                }
                String password = "";
                String security = "";
                if (!psk.isEmpty() && !psk.equals("NONE")) {
                    password = psk;
                    security = "WPA/WPA2";
                } else if (!sae.isEmpty()) {
                    password = sae;
                    security = "WPA3";
                } else if (!wep0.isEmpty()) {
                    password = wep0;
                    security = "WEP";
                } else {
                    Pattern eapPattern = Pattern.compile("(?m)^\\s*password\\s*=\\s*\"([^\"]+)\"");
                    Matcher eapMatcher = eapPattern.matcher(block);
                    if (eapMatcher.find()) {
                        password = eapMatcher.group(1);
                        security = "EAP";
                    } else {
                        Pattern keyMgmtPattern = Pattern.compile("(?m)^\\s*key_mgmt\\s*=\\s*([^\\s#]+)");
                        Matcher keyMgmtMatcher = keyMgmtPattern.matcher(block);
                        if (keyMgmtMatcher.find()) {
                            String km = keyMgmtMatcher.group(1).trim();
                            if (km.contains("NONE")) {
                                password = "";
                                security = "Open";
                            }
                        }
                    }
                }
                putEntry(merged, ssid, password, security);
            }
        } catch (Exception ignored) {
        }
    }

    private static String extractQuoted(String block, String key) {
        try {
            Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(block);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractUnquoted(String block, String key) {
        try {
            Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*([^\\s#\\{\\}]+)");
            Matcher matcher = pattern.matcher(block);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static void parseConfigStore(String xml, Map<String, WifiEntry> merged) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8");
            boolean inNetwork = false;
            String ssid = null;
            String psk = null;
            String sae = null;
            String wep0 = null;
            String eapPassword = null;
            String currentName = null;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (tag != null && tag.equals("Network")) {
                        inNetwork = true;
                        ssid = null;
                        psk = null;
                        sae = null;
                        wep0 = null;
                        eapPassword = null;
                        currentName = null;
                    } else if (inNetwork && tag != null && tag.equals("string")) {
                        String name = parser.getAttributeValue(null, "name");
                        currentName = name;
                        try {
                            String text = parser.nextText();
                            if (name != null) {
                                switch (name) {
                                    case "SSID" -> ssid = text;
                                    case "PreSharedKey" -> psk = text;
                                    case "SaePassword" -> sae = text;
                                    case "WEP0_Key", "WEP0Key", "wep_key0" -> wep0 = text;
                                    case "Password", "EapPassword" -> {
                                        if (eapPassword == null || eapPassword.isEmpty()) {
                                            eapPassword = text;
                                        }
                                    }
                                }
                            }
                            currentName = null;
                        } catch (Exception ignored) {
                            currentName = null;
                        }
                    } else if (inNetwork && tag != null && tag.equals("int")) {
                        currentName = null;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inNetwork && currentName != null) {
                        String text = parser.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            switch (currentName) {
                                case "SSID" -> ssid = text;
                                case "PreSharedKey" -> psk = text;
                                case "SaePassword" -> sae = text;
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if (tag != null && tag.equals("Network")) {
                        if (ssid != null && !stripQuotes(ssid).isEmpty()) {
                            String password = "";
                            String security = "";
                            if (psk != null && !stripQuotes(psk).isEmpty() && !stripQuotes(psk).equals("NONE")) {
                                password = psk;
                                security = "WPA/WPA2";
                            } else if (sae != null && !stripQuotes(sae).isEmpty()) {
                                password = sae;
                                security = "WPA3";
                            } else if (wep0 != null && !stripQuotes(wep0).isEmpty()) {
                                password = wep0;
                                security = "WEP";
                            } else if (eapPassword != null && !stripQuotes(eapPassword).isEmpty()) {
                                password = eapPassword;
                                security = "EAP";
                            }
                            putEntry(merged, ssid, password, security);
                        }
                        inNetwork = false;
                        ssid = null;
                        psk = null;
                        sae = null;
                        wep0 = null;
                        eapPassword = null;
                        currentName = null;
                    } else if (tag != null && tag.equals("string")) {
                        currentName = null;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception ignored) {
        }
    }
}
