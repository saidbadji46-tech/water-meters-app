package com.saidbadji.meterreading;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.OutputStream;

@CapacitorPlugin(name = "AndroidPublicFile")
public class AndroidPublicFilePlugin extends Plugin {

    @PluginMethod
    public void savePublicFile(PluginCall call) {

        String filename = call.getString("filename");
        String data = call.getString("data");

        if (filename == null || filename.trim().isEmpty()) {
            call.reject("filename missing");
            return;
        }

        if (data == null || data.trim().isEmpty()) {
            call.reject("data missing");
            return;
        }

        try {

            byte[] bytes;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bytes = java.util.Base64.getDecoder().decode(data);
            } else {
                bytes = android.util.Base64.decode(
                        data,
                        android.util.Base64.DEFAULT
                );
            }

            ContentResolver resolver =
                    getContext().getContentResolver();

            Uri uri;

            /*
             * Android 10 وما فوق
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                ContentValues values = new ContentValues();

                values.put(
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        filename
                );

                values.put(
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        getMimeType(filename)
                );

                values.put(
                        MediaStore.Files.FileColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/ADE"
                );

                values.put(
                        MediaStore.Files.FileColumns.IS_PENDING,
                        1
                );

                uri = resolver.insert(
                        MediaStore.Files.getContentUri("external"),
                        values
                );

                if (uri == null) {
                    throw new Exception(
                            "MediaStore insert failed"
                    );
                }

                try (OutputStream output =
                             resolver.openOutputStream(uri)) {

                    if (output == null) {
                        throw new Exception(
                                "Could not open output stream"
                        );
                    }

                    output.write(bytes);
                    output.flush();
                }

                ContentValues finished = new ContentValues();

                finished.put(
                        MediaStore.Files.FileColumns.IS_PENDING,
                        0
                );

                resolver.update(
                        uri,
                        finished,
                        null,
                        null
                );

            } else {

                /*
                 * Android 9 وما تحت
                 */

                java.io.File documents =
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS
                        );

                java.io.File adeFolder =
                        new java.io.File(
                                documents,
                                "ADE"
                        );

                if (!adeFolder.exists()
                        && !adeFolder.mkdirs()) {

                    throw new Exception(
                            "Could not create Documents/ADE"
                    );
                }

                java.io.File outputFile =
                        new java.io.File(
                                adeFolder,
                                filename
                        );

                try (
                        java.io.FileOutputStream output =
                                new java.io.FileOutputStream(
                                        outputFile
                                )
                ) {

                    output.write(bytes);
                    output.flush();
                }

                uri = Uri.fromFile(outputFile);
            }

            JSObject result = new JSObject();

            result.put(
                    "uri",
                    uri.toString()
            );

            result.put(
                    "filename",
                    filename
            );

            result.put(
                    "folder",
                    "Documents/ADE"
            );

            call.resolve(result);

        } catch (Exception e) {

            call.reject(
                    "Android public save failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String getMimeType(String filename) {

        String name =
                filename.toLowerCase();

        if (name.endsWith(".zip")) {
            return "application/zip";
        }

        if (name.endsWith(".txt")) {
            return "text/plain";
        }

        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        if (name.endsWith(".json")) {
            return "application/json";
        }

        if (name.endsWith(".html")) {
            return "text/html";
        }

        return "application/octet-stream";
    }
}
