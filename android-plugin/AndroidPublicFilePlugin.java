package com.saidbadji.meterreading;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Writes files directly into a top-level "ADE" folder in internal storage
 * -- e.g. /storage/emulated/0/ADE -- with no folder picker and no dialog
 * beyond the normal one-time storage permission prompt.
 *
 * This relies on "legacy external storage" mode
 * (android:requestLegacyExternalStorage="true" in AndroidManifest.xml,
 * together with targetSdkVersion 29), which tells Android to skip the
 * modern scoped-storage rules for this app. That combination is only
 * honoured up to Android 10 (API 29) with WRITE_EXTERNAL_STORAGE granted;
 * the build workflow sets both of those up. This mirrors how many
 * side-loaded, single-purpose Android apps (not distributed on the Play
 * Store) still write straight into internal storage today.
 *
 * After writing, the file is re-read (size check) so a silent/partial
 * write can never be reported back to the JS side as "done" when it
 * wasn't.
 */
@CapacitorPlugin(name = "AndroidPublicFile")
public class AndroidPublicFilePlugin extends Plugin {

    private static final String SUBFOLDER = "ADE";

    @PluginMethod
    public void savePublicFile(PluginCall call) {
        String filename = call.getString("filename");
        String data = call.getString("data");

        if (filename == null || filename.isEmpty()) {
            call.reject("الاسم (filename) مطلوب");
            return;
        }
        if (data == null || data.isEmpty()) {
            call.reject("البيانات (data) مطلوبة");
            return;
        }

        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);

            File dir = new File(Environment.getExternalStorageDirectory(), SUBFOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                call.reject("تعذر إنشاء المجلد " + SUBFOLDER
                        + " -- تأكد من منح إذن التخزين للتطبيق من إعدادات الهاتف");
                return;
            }

            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync();
            }

            // Real verification: re-check the file on disk instead of just
            // trusting that write() didn't throw.
            if (!file.exists() || file.length() != bytes.length) {
                call.reject("فشل التحقق من حفظ الملف: " + filename
                        + " (الحجم المتوقع " + bytes.length + " بايت، الفعلي "
                        + (file.exists() ? file.length() : 0) + " بايت)");
                return;
            }

            JSObject ret = new JSObject();
            ret.put("path", file.getAbsolutePath());
            ret.put("size", file.length());
            call.resolve(ret);
        } catch (IllegalArgumentException e) {
            call.reject("بيانات base64 غير صالحة: " + e.getMessage(), e);
        } catch (SecurityException e) {
            call.reject("لا يوجد إذن للكتابة في التخزين -- يرجى منح إذن التخزين للتطبيق من إعدادات الهاتف", e);
        } catch (Exception e) {
            call.reject("فشل حفظ الملف: " + e.getMessage(), e);
        }
    }
}
