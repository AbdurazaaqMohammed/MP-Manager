import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maintainer tool for packs.json catalog signing (RSA-2048 / SHA256withRSA).
 *
 * <p>Create the keystore once with keytool (keep it private, never commit):
 * <pre>
 *   keytool -genkeypair -keystore pack-signing.jks -alias pack-signing \
 *     -keyalg RSA -keysize 2048 -validity 3650
 * </pre>
 * Then run with a JDK (single-file launch, no build needed):
 * <pre>
 *   java tools/SignCatalog.java --pubkey &lt;keystore&gt; &lt;alias&gt; &lt;password&gt;
 *   java tools/SignCatalog.java --sign &lt;keystore&gt; &lt;alias&gt; &lt;password&gt; &lt;packs.json&gt;
 *   java tools/SignCatalog.java --verify &lt;packs.json&gt; &lt;packs.json.sig&gt; &lt;base64-x509-pubkey&gt;
 *   java tools/SignCatalog.java --checksums &lt;packs.json&gt; [id=apk-path ...]
 *   java tools/SignCatalog.java --fill &lt;keystore&gt; &lt;alias&gt; &lt;password&gt; &lt;packs.json&gt; [id=apk-path ...]
 * </pre>
 * --pubkey prints the X.509 public key (base64) to pin in PackCatalog;
 * --sign writes packs.json.sig (base64 signature) next to packs.json;
 * --verify checks a signature with the pinned key (release QA);
 * --checksums downloads each apkUrl (or hashes the local APK given as
 * id=path), records its SHA-256 in the pack's sha256 field, and rewrites
 * packs.json in place (no keystore needed) so app installs can be
 * checksum-verified;
 * --fill is --checksums then --sign in one step (release flow).
 */
public class SignCatalog {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "";
        if ("--verify".equals(mode) && args.length == 4) {
            verify(args[1], args[2], args[3]);
            return;
        }
        if ("--checksums".equals(mode) && args.length >= 2) {
            updateChecksums(args[1], localFiles(args, 2));
            return;
        }
        if (("--pubkey".equals(mode) || "--sign".equals(mode) || "--fill".equals(mode))
                && args.length >= 4) {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream in = new FileInputStream(args[1])) {
                ks.load(in, args[3].toCharArray());
            }
            if ("--pubkey".equals(mode)) {
                PublicKey pub = ks.getCertificate(args[2]).getPublicKey();
                System.out.println(Base64.getEncoder().encodeToString(pub.getEncoded()));
                return;
            }
            if (args.length < 5) {
                usage();
                return;
            }
            if ("--fill".equals(mode)) updateChecksums(args[4], localFiles(args, 5));
            sign(ks, args[2], args[3], args[4]);
            return;
        }
        usage();
    }

    /** Parses trailing id=path pairs into a map of pack id to local APK. */
    private static java.util.Map<String, String> localFiles(String[] args, int from) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = from; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) map.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            else System.out.println("ignoring malformed pair: " + args[i]);
        }
        return map;
    }

    private static void verify(String catalogPath, String sigPath, String pubKeyB64) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(catalogPath));
        String sigB64 = new String(Files.readAllBytes(Paths.get(sigPath)), "UTF-8").trim();
        byte[] keyBytes = Base64.getDecoder().decode(pubKeyB64);
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        PublicKey key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(key);
        sig.update(data);
        byte[] raw = Base64.getDecoder().decode(sigB64);
        boolean ok = sig.verify(raw);
        System.out.println(ok ? "VALID" : "INVALID");
        if (!ok) System.exit(1);
    }

    private static void sign(KeyStore ks, String alias, String password, String catalogPath)
            throws Exception {
        PrivateKey key = (PrivateKey) ks.getKey(alias, password.toCharArray());
        byte[] data = Files.readAllBytes(Paths.get(catalogPath));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(data);
        String out = Base64.getEncoder().encodeToString(sig.sign());
        Files.write(Paths.get(catalogPath + ".sig"), out.getBytes("UTF-8"));
        System.out.println("signed: " + catalogPath + ".sig");
    }

    private static void usage() {
        System.out.println("usage: --pubkey|--sign|--fill <keystore> <alias> <password> <packs.json> [id=apk-path ...]");
        System.out.println("       --checksums <packs.json> [id=apk-path ...]");
        System.out.println("       --verify <packs.json> <packs.json.sig> <base64-x509-pubkey>");
        System.out.println("  id=path hashes a local APK instead of downloading apkUrl");
        System.out.println("  --fill = --checksums then --sign (release flow)");
    }

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"apkUrl\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SHA_PATTERN = Pattern.compile("\"sha256\"\\s*:\\s*\"([^\"]*)\"");

    /** One pack object inside the packs array: id, url, current sha256 and its span. */
    private static final class Pack {
        final String id, url, sha256;
        final int bodyStart, bodyEnd;
        Pack(String id, String url, String sha256, int bodyStart, int bodyEnd) {
            this.id = id; this.url = url; this.sha256 = sha256;
            this.bodyStart = bodyStart; this.bodyEnd = bodyEnd;
        }
    }

    /**
     * Downloads each pack APK named by its apkUrl and rewrites the matching
     * sha256 field in packs.json so installs can be checksum-verified. Only
     * the sha256 values are touched, so surrounding formatting is preserved.
     */
    private static void updateChecksums(String catalogPath,
            java.util.Map<String, String> localFiles) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(catalogPath)), StandardCharsets.UTF_8);
        List<Pack> packs = parsePacks(text);
        if (packs.isEmpty()) throw new IllegalStateException("no packs found in " + catalogPath);
        List<String> hashes = new ArrayList<>();
        for (Pack p : packs) {
            String local = localFiles.get(p.id);
            byte[] apk;
            String source;
            if (local != null) {
                apk = Files.readAllBytes(Paths.get(local));
                source = "local " + local;
            } else if (!p.url.isEmpty()) {
                apk = download(p.url);
                if (apk == null) throw new IOException("failed to download " + p.url);
                source = p.url;
            } else {
                System.out.println("skip " + p.id + ": no apkUrl and no local file");
                hashes.add(null);
                continue;
            }
            String hash = sha256Hex(apk);
            if (!p.sha256.isEmpty() && !p.sha256.equalsIgnoreCase(hash)) {
                System.out.println(p.id + ": " + hash + " (was " + p.sha256 + ", " + apk.length + " bytes, " + source + ")");
            } else {
                System.out.println(p.id + ": " + hash + " (" + apk.length + " bytes, " + source + ")");
            }
            hashes.add(hash);
        }
        StringBuilder out = new StringBuilder(text);
        for (int i = packs.size() - 1; i >= 0; i--) {
            Pack p = packs.get(i);
            String hash = hashes.get(i);
            if (hash == null) continue;
            String body = out.substring(p.bodyStart, p.bodyEnd);
            body = body.replaceFirst("(\"sha256\"\\s*:\\s*)\"[^\"]*\"",
                    "$1" + Matcher.quoteReplacement("\"" + hash + "\""));
            out.replace(p.bodyStart, p.bodyEnd, body);
        }
        String result = out.toString();
        if (!text.equals(result)) {
            Files.write(Paths.get(catalogPath), result.getBytes(StandardCharsets.UTF_8));
            System.out.println("checksums updated: " + packs.size() + " -> " + catalogPath);
        } else {
            System.out.println("checksums unchanged: " + catalogPath);
        }
    }

    /** Finds the top-level objects of the "packs" array (string-aware). */
    private static List<Pack> parsePacks(String text) {
        int arr = text.indexOf("\"packs\"");
        int open = text.indexOf('[', arr);
        if (arr < 0 || open < 0) return new ArrayList<>();
        List<Pack> packs = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i = skipString(text, i);
                continue;
            }
            if (c == '{') {
                if (depth == 0 && start < 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    Pack p = extract(text, start, i + 1);
                    if (p != null) packs.add(p);
                    start = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return packs;
    }

    private static int skipString(String text, int quote) {
        for (int i = quote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return text.length();
    }

    private static Pack extract(String text, int start, int end) {
        String body = text.substring(start, end);
        Matcher idM = ID_PATTERN.matcher(body);
        Matcher urlM = URL_PATTERN.matcher(body);
        Matcher shaM = SHA_PATTERN.matcher(body);
        if (!idM.find() || !urlM.find() || !shaM.find()) return null;
        return new Pack(idM.group(1), urlM.group(1), shaM.group(1), start, end);
    }

    private static byte[] download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.out.println("HTTP " + conn.getResponseCode() + " for " + url);
                return null;
            }
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            System.out.println("download error for " + url + ": " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
