# R8 rules for the release build (v1.6).
#
# Before this file the release APK shipped four unshrunk dex files, ~32 MB of
# the ~103 MB total, most of it library code the app never calls. R8 is now on;
# what follows is only what R8 cannot work out for itself, i.e. code that is
# reached by reflection, by JNI, or by name from an asset.
#
# Rule of thumb when adding to this file: keep the NARROWEST thing that is
# actually looked up dynamically. A `-keep class foo.** { *; }` is a promise
# that every member of every class is needed, and it is usually wrong.

# ---------------------------------------------------------------- our own code
# Nothing in com.racunko is reflected over; the entry points (activities, the
# application class, the FileProvider) are kept by the manifest keep rules AGP
# generates. parser-core is plain Kotlin called directly.

# --------------------------------------------------------------------- Room
# The generated *_Impl is loaded by name from the @Database class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------- ViewModel
# AndroidViewModelFactory finds the (Application) constructor reflectively.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ------------------------------------------------------------- pdfbox-android
# PDFBox resolves fonts, CMaps and the glyph list out of assets by NAME and
# instantiates COS/font classes through several factory paths that R8 cannot
# follow. This is the one library kept whole on purpose: text extraction from a
# stranger's PDF is the app's core job and a NoSuchMethodError there would only
# ever show up on a real bill, never in a test.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**

# BouncyCastle arrives with PDFBox for encrypted PDFs. Its optional AWT/JCE
# corners are not on Android; warnings about them are expected, not a problem.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# ------------------------------------------------- Tesseract4Android (foss)
# Called from native code — the JNI layer looks these up by name and signature.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# -------------------------------------------------------------- ML Kit (gms)
# THIS is what crashed the first R8 build at Application.onCreate:
#
#   RacunkoApp.onCreate → EngineFactory.create → MlKitQrDecoder.<init>
#   → BarcodeScanning.getClient() → NullPointerException
#
# ML Kit finds its components by CLASS NAME in manifest meta-data, instantiates
# them through a no-arg constructor, and then keys them in a registry by Class
# object. R8 breaks that twice over: it drops the constructor of a class that is
# only ever created reflectively, and — the one that actually bit — it MERGES
# classes, which collapses two distinct registry keys into one so the lookup
# returns null. Keeping the vision surface whole stops both. The registrars
# themselves ship with `-keepnames`, which prevents renaming but NOT removal,
# so their constructors are spelled out here.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.common.internal.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**

# ------------------------------------------------------------------- ZXing
-dontwarn com.google.zxing.**

# ----------------------------------------------------------------- Kotlin
# Coroutines' debug agent probe, absent at runtime.
-dontwarn kotlinx.coroutines.debug.**
