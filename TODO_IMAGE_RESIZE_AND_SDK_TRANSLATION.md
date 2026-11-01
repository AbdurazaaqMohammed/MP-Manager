# MP-Manager Next Tasks & Detailed Architecture Notes

> **Last Updated:** 2026-11-01
> **Repository:** MP-Manager (`/data/user/0/com.agy/files/.gemini/antigravity-app/scratch/MP-Manager-Test/MP-Manager-Test-main`)
> **Branch:** `main` (commit `9ba02f83` pushed to `https://github.com/midopop-ux/MP-Manager-Test.git`)

---

## 1. Problem 1: Image Resize ("المط") & Backup (.bak) Flow

### User Requirements:
1. **Restore Backup (`.bak`):**
   - وعند حفظ الصورة بعد تعديل المط (أو أي تعديل بيكسل)، يتم إعادة تسمية الملف الأصلي إلى `.bak` في نفس المسار.
   - **الاحتفاظ بالـ Exif و Last Modified للملف الأصلي:** بما أنه تمت إعادة تسميته فقط (`rename` / `move`)، فإنه يحتفظ ببياناته الأصلية والـ inode وتاريخ التعديل وExif بالكامل دون أدنى تغيير.
2. **النسخة المعدلة:**
   - تأخذ اسم الملف الأصلي بالضبط في نفس المسار.
   - نسخ بيانات الـ EXIF من النسخة الاحتياطية / الأصلية إلى الصورة الجديدة المعدلة مع ضبط الأبعاد الجديدة.
   - إبلاغ `MediaScannerConnection.scanFile` بالمسارين (الأصلي والبديل Emulated).
3. **سبب المشكلة الحالي ("بيخزن في الكاش ولما بدوس حفظ مش بيعمل حاجة"):**
   - في `ImageEditActivity.java`:
     - عند فتح الصورة عبر Gallery / Share، يستقبل `ImageEditActivity` الـ Uri (`content://...`).
     - دالة `resolveSharedImage(Uri uri)` كانت تنسخ الملف مؤقتاً إلى الكاش وتضع `fromShared = true` و `originalPath = dest.getAbsolutePath()`.
     - عند الضغط على حفظ في `doSave()`:
       ```java
       if (fromShared) {
           pm.dismiss();
           runOnUiThread(this::promptSharedSaveLocation);
           return;
       }
       ```
       فتطلب من المستخدم اختيار مجلد جديد أو تفشل صامتاً إذا لم يتمكن المستخدم من الحفظ في المكان الأصلي!
     - بينما في `SharedEntryActivity.java`: يتم عمل `SharedPathResolver.resolve(...)` لمعرفة المسار الحقيقي على القرص.
   - **الحل:**
     - نقل منطق فحص المسار الحقيقي أو تمريره من `SharedEntryActivity` / حل الـ `Uri` إلى مسار حقيقي (سواء `/data/media/0/...` أو `/storage/emulated/0/...`).
     - إذا كان المسار الحقيقي معروفاً، نجعل `fromShared = false` ونعين `originalPath = realPath`.
     - في `doSave()`:
       1. إعادة تسمية الملف الأصلي:
          ```java
          String bakPath = originalPath + ".bak";
          AccessManager.move(this, originalPath, bakPath, AccessManager.needsElevated(this, originalPath));
          ```
       2. حفظ النسخة المعدلة من `workingFile` إلى `originalPath`:
          ```java
          AccessManager.copyFile(this, workingFile.getAbsolutePath(), originalPath, AccessManager.needsElevated(this, originalPath));
          ```
       3. نسخ الـ EXIF من `bakPath` إلى `originalPath` (مع تحديث أبعاد الصورة الجديدة).
       4. عمل `scanMediaFile(originalPath)` و `scanMediaFile(bakPath)`.

---

## 2. Problem 2: Android SDK Translation in APK Info ("ترجمة إصدارات الأندرويد")

### User Requirements:
- في نافذة تفاصيل الـ APK (التي تم رفع صورتها في الشات):
  - تحويل رقم **Target SDK** (مثلاً `30`) ورقم **Min SDK** (مثلاً `21`) إلى اسم إصدار الأندرويد المقابل + رقم الـ API.
  - مثال:
    - `30` -> `Android 11 (API 30)` أو `Android 11 (30)`
    - `21` -> `Android 5.0 (API 21)` أو `Android 5.0 Lollipop (21)`

### Locations in Code:
1. **`AppDetailsDialog.java`** (حول الأسطر 305 - 318):
   ```java
   case "detail:target_sdk": {
       if (ai.targetSdk > 0) {
           TextView v = addRow(act, container, act.getString(R.string.target_sdk), formatSdkWithAndroidVersion(ai.targetSdk), null, false);
           v.setOnLongClickListener(copy);
       }
       break;
   }
   case "detail:min_sdk": {
       if (ai.minSdk >= 0) {
           TextView v = addRow(act, container, act.getString(R.string.min_sdk), formatSdkWithAndroidVersion(ai.minSdk), null, false);
           v.setOnLongClickListener(copy);
       }
       break;
   }
   ```
2. **`ApkInfoDialogs.java`** (حول الأسطر 1420 - 1423):
   ```java
   int target = packageInfo.applicationInfo.targetSdkVersion;
   apkTargetSdk.setText(target <= 0 ? context.getString(R.string.unknown_sdk) : formatSdkWithAndroidVersion(target));
   int min = ApkInfoUtil.getMinSdk(packageInfo);
   apkMinSdk.setText(min < 0 ? context.getString(R.string.unknown_sdk) : formatSdkWithAndroidVersion(min));
   ```
3. **`ApkCompareUtil.java`**:
   - أيضاً يمكن استخدام الدالة المساعدة هناك للمقارنة بين نسختي APK.

### SDK to Android Version Mapping Table:
```java
public static String getAndroidVersionName(int sdk) {
    return switch (sdk) {
        case 1 -> "Android 1.0 (1)";
        case 2 -> "Android 1.1 (2)";
        case 3 -> "Android 1.5 Cupcake (3)";
        case 4 -> "Android 1.6 Donut (4)";
        case 5 -> "Android 2.0 Eclair (5)";
        case 6 -> "Android 2.0.1 Eclair (6)";
        case 7 -> "Android 2.1 Eclair (7)";
        case 8 -> "Android 2.2 Froyo (8)";
        case 9 -> "Android 2.3 Gingerbread (9)";
        case 10 -> "Android 2.3.3 Gingerbread (10)";
        case 11 -> "Android 3.0 Honeycomb (11)";
        case 12 -> "Android 3.1 Honeycomb (12)";
        case 13 -> "Android 3.2 Honeycomb (13)";
        case 14 -> "Android 4.0 Ice Cream Sandwich (14)";
        case 15 -> "Android 4.0.3 Ice Cream Sandwich (15)";
        case 16 -> "Android 4.1 Jelly Bean (16)";
        case 17 -> "Android 4.2 Jelly Bean (17)";
        case 18 -> "Android 4.3 Jelly Bean (18)";
        case 19 -> "Android 4.4 KitKat (19)";
        case 20 -> "Android 4.4W KitKat Wear (20)";
        case 21 -> "Android 5.0 Lollipop (21)";
        case 22 -> "Android 5.1 Lollipop (22)";
        case 23 -> "Android 6.0 Marshmallow (23)";
        case 24 -> "Android 7.0 Nougat (24)";
        case 25 -> "Android 7.1 Nougat (25)";
        case 26 -> "Android 8.0 Oreo (26)";
        case 27 -> "Android 8.1 Oreo (27)";
        case 28 -> "Android 9 Pie (28)";
        case 29 -> "Android 10 (29)";
        case 30 -> "Android 11 (30)";
        case 31 -> "Android 12 (31)";
        case 32 -> "Android 12L (32)";
        case 33 -> "Android 13 (33)";
        case 34 -> "Android 14 (34)";
        case 35 -> "Android 15 (35)";
        case 36 -> "Android 16 (36)";
        default -> sdk > 36 ? "Android " + (sdk - 19) + " (" + sdk + ")" : String.valueOf(sdk);
    };
}
```

---

## 3. Files to Edit:
1. `app/src/main/java/io/github/abdurazaaqmohammed/player/ImageEditActivity.java`:
   - حل مسار الـ Shared إلى المسار الحقيقي لمنع الوقوع في فخ الكاش `fromShared`.
   - تعديل دالة `doSave()` لتنفيذ إعادة التسمية إلى `.bak`، ثم حفظ النسخة الممطوطة باسم الملف الأصلي ونسخ الـ EXIF وعمل scan.
2. `app/src/main/java/io/github/abdurazaaqmohammed/utils/ApkInfoUtil.java`:
   - إضافة دالة `getAndroidVersionName(int sdk)`.
3. `app/src/main/java/io/github/abdurazaaqmohammed/ApkExtractor/AppDetailsDialog.java`:
   - استخدام `ApkInfoUtil.getAndroidVersionName` في عرض `target_sdk` و `min_sdk`.
4. `app/src/main/java/io/github/abdurazaaqmohammed/features/apk/ApkInfoDialogs.java`:
   - استخدام `ApkInfoUtil.getAndroidVersionName` في عرض `target_sdk` و `min_sdk`.

---
*تم إنشاء هذا الملف ليكون مرجعاً تفصيلياً جاهزاً للتنفيذ فوراً في الخطوة القادمة.*
