package com.saidbadji.meterreading;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Saves a base64 file into the PUBLIC Documents/ADE folder so it is visible
 * to any file manager / when the phone is plugged into a PC, using
 * MediaStore -- no folder picker, no special "legacy storage" targetSdk
 * trick, and no extra runtime permission dialog on Android 10+ (which is
 * exactly what caused the Play Protect "old Android" install warning and
 * the storage-permission failures with the previous approach).
 *
 * Android 10+ (API 29+): MediaStore, no permission required.
 * Android 9 and below:  writes directly to the public Documents dir and
 *                        needs WRITE_EXTERNAL_STORAGE (added to
 *                        AndroidManifest.xml by the build workflow, with
 *                        maxSdkVersion="28").
 *
 * After writing, the file is re-opened and its actual byte count is
 * compared against what we meant to write, so a silent/partial save can
 * never be reported back to the JS side as "done" when it wasn't.
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(ctx, filename, bytes, call);
            } else {
                saveViaLegacyFile(filename, bytes, call);
            }
        } catch (IllegalArgumentException e) {
            call.reject("بيانات base64 غير صالحة: " + e.getMessage(), e);
        } catch (Exception e) {
            call.reject("فشل حفظ الملف: " + e.getMessage(), e);
        }
    }

    private void saveViaMediaStore(Context ctx, String filename, byte[] bytes, PluginCall call) throws Exception {
        Uri collection = MediaStore.Files.getContentUri("external");
        String relativePath = Environment.DIRECTORY_DOCUMENTS + File.separator + SUBFOLDER;

        Uri itemUri = findExisting(ctx, collection, filename, relativePath);

        if (itemUri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, guessMime(filename));
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            itemUri = ctx.getContentResolver().insert(collection, values);
        }

        if (itemUri == null) {
            call.reject("تعذر إنشاء الملف عبر MediaStore: " + filename);
            return;
        }

        try (OutputStream out = ctx.getContentResolver().openOutputStream(itemUri, "wt")) {
            if (out == null) {
                call.reject("تعذر فتح الملف للكتابة: " + filename);
                return;
            }
            out.write(bytes);
            out.flush();
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        ctx.getContentResolver().update(itemUri, done, null, null);

        long actualSize = -1;
        try (InputStream verify = ctx.getContentResolver().openInputStream(itemUri)) {
            if (verify != null) {
                long count = 0;
                byte[] buf = new byte[8192];
                int n;
                while ((n = verify.read(buf)) != -1) count += n;
                actualSize = count;
            }
        }

        if (actualSize != bytes.length) {
            call.reject("فشل التحقق من حفظ الملف: " + filename
                    + " (الحجم المتوقع " + bytes.length + " بايت، الفعلي " + actualSize + " بايت)");
            return;
        }

        JSObject ret = new JSObject();
        ret.put("path", "Documents/" + SUBFOLDER + "/" + filename);
        ret.put("size", actualSize);
        call.resolve(ret);
    }

    private Uri findExisting(Context ctx, Uri collection, String filename, String relativePath) {
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = { filename, relativePath + File.separator };
        try (Cursor cursor = ctx.getContentResolver().query(collection, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                return Uri.withAppendedPath(collection, String.valueOf(id));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveViaLegacyFile(String filename, byte[] bytes, PluginCall call) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SUBFOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            call.reject("تعذر إنشاء المجلد Documents/" + SUBFOLDER);
            return;
        }
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            fos.flush();
            fos.getFD().sync();
        }

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
    }

    private String guessMime(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }
}
