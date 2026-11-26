package com.hideurl.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebView;
import android.widget.Toast;
import java.util.LinkedHashSet;
import java.util.Locale;

final class WebUpload {
    static final int PICK = 493;
    private final Activity activity;
    private ValueCallback<Uri[]> callback;
    private WebView owner;
    private boolean multiple;
    private boolean picking;

    WebUpload(Activity activity) { this.activity = activity; }

    boolean choose(WebView view, ValueCallback<Uri[]> result, FileChooserParams params) {
        int mode = params.getMode();
        if (picking || (mode != FileChooserParams.MODE_OPEN && mode != FileChooserParams.MODE_OPEN_MULTIPLE)) {
            result.onReceiveValue(null);
            return true;
        }
        multiple = mode == FileChooserParams.MODE_OPEN_MULTIPLE;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String accept : params.getAcceptTypes()) {
            if (accept == null) continue;
            for (String value : accept.split(",")) {
                String type = value.trim().toLowerCase(Locale.ROOT);
                if (type.startsWith(".")) type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1));
                if (type != null && type.contains("/")) types.add(type);
            }
        }
        picker.setType(types.size() == 1 ? types.iterator().next() : "*/*");
        if (types.size() > 1 && !types.contains("*/*")) picker.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        picking = true;
        owner = view;
        callback = result;
        try { activity.startActivityForResult(picker, PICK); }
        catch (ActivityNotFoundException ex) {
            finish(Activity.RESULT_CANCELED, null);
            Toast.makeText(activity, "เปิดตัวเลือกไฟล์ไม่ได้", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    void finish(int code, Intent data) {
        picking = false;
        ValueCallback<Uri[]> result = callback;
        callback = null;
        owner = null;
        if (result == null) return;
        LinkedHashSet<Uri> files = new LinkedHashSet<>();
        if (code == Activity.RESULT_OK && data != null) {
            collect(files, data.getData());
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount() && (multiple || files.isEmpty()); i++) {
                    collect(files, clip.getItemAt(i).getUri());
                }
            }
        }
        result.onReceiveValue(files.isEmpty() ? null : files.toArray(new Uri[0]));
    }

    private void collect(LinkedHashSet<Uri> files, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return;
        String authority = uri.getAuthority();
        if (authority == null || authority.contains("@") || authority.equals(activity.getPackageName() + ".update")) return;
        files.add(uri);
    }

    void detach(WebView view) {
        if (owner == view && callback != null) {
            ValueCallback<Uri[]> result = callback;
            callback = null;
            owner = null;
            result.onReceiveValue(null);
        }
    }
}
