# SymbolDrawerSettings is encoded by Gson (including defaults and schemaVersion).
-keep class com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings { *; }
# Bundle/storage models and typed actions are reflected by the library codecs.
-keep class com.zerostudio.idea.widget.editor.symbolinput.v2.model.** { *; }
-keepattributes Signature,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken
