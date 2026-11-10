-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn android.content.res.**
-dontwarn org.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn java.lang.reflect.AnnotatedType
-keepnames class * { *; }
-keepnames interface * { *; }
-keepnames enum * { *; }
-keep class android.content.** { *; }
-keep class **.model.** { *; }
-keep class **.models.** { *; }
-dontobfuscate

# Release stack traces keep method line numbers.
# Signature/InnerClasses/EnclosingMethod are REQUIRED at runtime: Chunk
# (com.apk.axml) reads getGenericSuperclass() to find its Header type, and
# Gson reads generic field signatures. -keepnames alone strips them.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Pack tools are loaded at runtime with DexClassLoader from installed pack
# APKs and link against these host classes by name.
-keep class io.github.abdurazaaqmohammed.plugins.api.** { *; }
-keep class io.github.abdurazaaqmohammed.plugins.ipc.** { *; }

# Gson models: fields are read/written reflectively; shrinking would drop
# them and silently lose stored user data.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class io.github.abdurazaaqmohammed.MPManager.ftp.FtpProfile { *; }
-keep class io.github.abdurazaaqmohammed.features.files.network.NetworkProfile { *; }

# SMBJ uses MBassador's annotation-based event subscriptions. EL filters are an
# optional desktop-only feature; SMBJ never uses them and Android has no javax.el.
-keepclassmembers class com.hierynomus.smbj.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
-dontwarn javax.el.**
-keep class io.github.abdurazaaqmohammed.utils.DnsManager$DnsProfile { *; }
-keep class io.github.abdurazaaqmohammed.utils.RecycleBin$Meta { *; }
-keep class io.github.abdurazaaqmohammed.utils.OverlayInjectorUtil$ToastOptions { *; }
-keep class io.github.abdurazaaqmohammed.utils.OverlayInjectorUtil$DialogOptions { *; }
-keep class io.github.abdurazaaqmohammed.data.prefs.** { *; }
-keep class modder.hub.dexeditor.utils.EditorPositionManager$Position { *; }

# Compile-time-only references from vendored jars (bin.zip inner-class
# holders, apkzlib's AutoValue annotations); the classes are never used.
-dontwarn bin.zip.ZipFile$NameAndComment-IA
-dontwarn bin.zip.ZipFile$OffsetEntry-IA
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue

# --- R8 victims found by on-device smoke tests (debug PASS / release FAIL) ---
#
# 1) AXML quick-edit: Chunk() instantiates its Header subclass reflectively
#    via getConstructors() (com.apk.axml.aXMLUtils.Chunk). Plain -keepnames
#    does NOT keep constructors, so R8 deleted ChunkType, Header ctor and
#    the $H header classes; encoding then NPEs in ValueChunk/TagChunk.
-keep class com.apk.axml.aXMLUtils.ChunkType { *; }
-keep class com.apk.axml.aXMLUtils.Chunk { *; }
-keep class com.apk.axml.aXMLUtils.Chunk$** { *; }
-keep class com.apk.axml.aXMLUtils.**$H { *; }

# 2) FTP server: MINA SimpleIoProcessorPool instantiates NioProcessor
#    reflectively looking for (Executor) / (Executor, SelectorProvider) /
#    default ctors. R8 deleted the two Executor ctors, so start() throws
#    "must have a public constructor with one Executor..."
-keep class org.apache.mina.transport.socket.nio.NioProcessor {
    public <init>(java.util.concurrent.Executor);
    public <init>(java.util.concurrent.Executor, java.nio.channels.spi.SelectorProvider);
    public <init>();
}
-keep class org.apache.mina.transport.socket.nio.DatagramProcessor {
    public <init>(java.util.concurrent.Executor);
    public <init>();
}

# 3) APK signing: SignWrapper uses PKCS12KeyStoreSpi.BCPKCS12KeyStore, whose
#    constructor creates "X.509" via BCJcaJceHelper -> bundled
#    BouncyCastleProvider. The provider loads its SPIs by NAME string
#    (loadAlgorithms), so R8 deleted CertificateFactory/KeyFactory/KeyStore
#    SPIs and loading fails with "X.509 not found" (debug works).
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jcajce.util.BCJcaJceHelper { *; }
-keep class org.bouncycastle.jcajce.util.ProviderJcaJceHelper { *; }
-keep class org.bouncycastle.jcajce.util.DefaultJcaJceHelper { *; }

# 4) FTPServer/MINA are loaded via META-INF/services + SLF4J.
#    Keep the service-listed impls + listeners; the rest is directly
#    referenced and stays reachable without a blanket keep.
-keep class org.apache.ftpserver.listener.nio.NioListener { *; }
-keep class org.apache.ftpserver.listener.nio.DatagramListener { *; }
-keep class org.apache.mina.transport.socket.nio.NioSocketAcceptor { *; }
-keep class org.apache.mina.transport.socket.nio.NioSocketConnector { *; }
-keep class org.apache.mina.core.service.SimpleIoProcessorPool { *; }
-keep class org.slf4j.spi.Slf4jServiceProvider { *; }
-keep class org.slf4j.impl.StaticLoggerBinder { *; }
-keep class org.slf4j.impl.StaticMarkerFactory { *; }

# 5) Gson: keep reflective field access. The model keeps above need
#    Signature + annotations (see -keepattributes above). Gson's own
#    adapters are reachable via new Gson(); only the service-loaded
#    JsonSerializer/JsonDeserializer impls need explicit keeps.
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# 6) Vendored JDK x509 used by apksig / keystore paths: keep whole tree.
-keep class android.sun.security.x509.** { *; }
-keep class android.sun.security.provider.JavaKeyStoreProvider { *; }

# 7) apksig does its own v1/v2/v3/v4 verify+sign with inner classes
#    (V1SchemeVerifier$Signers etc.) loaded reflectively; R8 deleted
#    them, so v1 verify silently returns false and isVerified() fails
#    even though v2/v3 succeed.
-keep class com.android.apksig.** { *; }

# 8) LSPatch: JCommander instantiates validators/converters reflectively
#    (NoValidator/NoValueValidator/BooleanConverter/...) and LSPatch's
#    @Parameter fields. R8 kept the classes but stripped their no-arg
#    ctors/methods -> "Can't instantiate validator" / "Could not invoke
#    null (boolean got String)". Keep the whole engine + CLI parser.
-keep class com.beust.jcommander.** { *; }
-keep class com.android.tools.smali.util.jcommander.** { *; }
-keep class org.lsposed.patch.** { *; }

# 9) Signing with the default debug.keystore (desktop JKS) on SDK 24:
#    MainActivity registers JavaKeyStoreProvider, whose KeyStore.JKS impl
#    (JavaKeyStore$JKS) + PKCS12 (PKCS12KeyStore) are loaded by NAME string
#    via Provider.getService, so R8 deleted the impl classes even though
#    the provider class itself was kept -> KeyStore.getInstance("JKS")
#    fails "JKS not found" and signing falls through to BKS -> "Wrong
#    version of key store" -> IOException. Keep the impls whole.
-keep class android.sun.security.provider.JavaKeyStore { *; }
-keep class android.sun.security.provider.JavaKeyStore$** { *; }
-keep class android.sun.security.provider.JavaKeyStoreProvider { *; }
-keep class android.sun.security.pkcs12.PKCS12KeyStore { *; }
-keep class android.sun.security.pkcs12.PKCS12KeyStore$** { *; }
-keep class android.sun.security.pkcs.EncryptedPrivateKeyInfo { *; }
-keep class android.sun.security.x509.** { *; }

# 10) Clone APK: bin.zip (ApkCloner's ZipFile) registers its ZipExtraField
#     impls by Class.newInstance() in a static map; R8 kept the classes
#     but stripped their ctors/methods -> ExceptionInInitializerError
#     "AsiExtraField is not a concrete class". mt.modder.hub.arsc and
#     com.apk.axml (APKParser/ResourceTableParser/string-pool) are the
#     same reflection-adjacent parser tree used by processApk().
-keep class bin.zip.** { *; }
-keep class mt.modder.hub.arsc.** { *; }
-keep class mt.modder.hub.apkCloner.** { *; }
-keep class com.apk.axml.APKParser { *; }
-keep class com.apk.axml.ResourceTableParser { *; }
-keep class com.apk.axml.aXMLUtils.** { *; }
-keep class com.apk.axml.serializableItems.** { *; }

# 11) Il2CppDumper DocumentsProvider payload: mpmanager.dump.* is compiled
#     into MP-Manager's own dex ONLY so DocumentsProviderInjector can lift
#     it out. The app's own manifest declares no provider, and no live code
#     references it, so R8 deleted it -> "MPManagerDocumentsProvider is
#     missing from MP-Manager's own dex" on the Il2Cpp+docs-provider path.
-keep class mpmanager.dump.** { *; }
