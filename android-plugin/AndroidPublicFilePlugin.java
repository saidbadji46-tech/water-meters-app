package com.saidbadji.meterreading;

import android.content.Context;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Saves a base64 file into the app's own storage, under an "ADE" subfolder
 * (Android/data/com.saidbadji.meterreading/files/ADE on the device's
 * internal storage). No runtime permission is required on any Android
 * version, since this is the app's private-but-visible external storage —
 * matches the original app behaviour.
 *
 * After writing, the file is re-read (size check) to make sure the save
 * genuinely succeeded before reporting success back to the JS side — so a
 * silent/partial write can no longer be reported as "done" when it wasn't.
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
            Context ctx = getContext();

            File baseDir = ctx.getExternalFilesDir(null);
            if (baseDir == null) {
                call.reject("التخزين الداخلي غير متوفر حالياً");
                return;
            }

            File dir = new File(baseDir, SUBFOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                call.reject("تعذر إنشاء المجلد " + SUBFOLDER);
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
        } catch (Exception e) {
            call.reject("فشل حفظ الملف: " + e.getMessage(), e);
        }
    }
}
