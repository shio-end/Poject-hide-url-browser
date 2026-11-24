package com.hideurl.browser;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.CancellationSignal;
import android.widget.Toast;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.libraries.identity.googleid.*;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import org.json.*;

final class GoogleAccountPanel {
    static final int CONSENT = 490;
    private static final String READ = "https://www.googleapis.com/auth/youtube.readonly";
    private final Activity activity;
    private final SharedPreferences profile;
    private final CancellationSignal cancellation = new CancellationSignal();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final java.util.function.BiConsumer<String,String> play;
    private final SharedPreferences library;
    private String syncList, syncTitle;
    private boolean busy;

    GoogleAccountPanel(Activity activity, java.util.function.BiConsumer<String,String> play) {
        this.activity = activity;
        this.play = play;
        library = activity.getSharedPreferences("youtube_library", Activity.MODE_PRIVATE);
        long cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30);
        SharedPreferences.Editor expired = library.edit();
        for (java.util.Map.Entry<String, ?> entry : library.getAll().entrySet()) {
            if (entry.getKey().startsWith("updated:") && (Long) entry.getValue() <= cutoff) {
                expired.remove(entry.getKey()).remove(entry.getKey().substring(8));
            }
        }
        expired.apply();
        profile = activity.getSharedPreferences("veil_account", Activity.MODE_PRIVATE);
    }

    String email() { return profile.getString("email", ""); }

    void welcome(boolean existing) {
        if (profile.getBoolean("introduced", false)) return;
        profile.edit().putBoolean("introduced", true).apply();
        if (!existing) show();
    }

    void show() { show(false); }

    private void show(boolean fromYoutube) {
        Sheets s = new Sheets(activity, "บัญชี VEIL");
        if (email().isEmpty()) {
            if (!activity.getString(R.string.google_web_client_id).isEmpty()) {
                s.row(s.action("ดำเนินการต่อด้วย Google", () -> { s.dialog.dismiss(); signIn(fromYoutube); }));
            } else {
                s.row(s.label("รุ่นนี้ยังไม่ได้ตั้งค่าการเชื่อมต่อ Google", 15, false));
            }
            s.row(s.action("ใช้ต่อแบบผู้เยี่ยมชม", s.dialog::dismiss));
        } else {
            s.row(s.label(email(), 16, true));
            s.row(s.action("จัดการสิทธิ์ที่ให้ VEIL ในบัญชี Google", () ->
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/connections")))));
            s.row(s.action("ออกจากบัญชีบนเครื่องนี้", () -> {
                s.dialog.dismiss();
                library.edit().clear().apply();
                profile.edit().remove("email").apply();
                CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(),
                        cancellation, activity.getMainExecutor(), new CredentialManagerCallback<Void, ClearCredentialException>() {
                            public void onResult(Void ignored) {}
                            public void onError(ClearCredentialException error) { message("ออกจากบัญชี VEIL แล้ว"); }
                        });
            }));
        }
        s.dialog.show();
    }

    private void signIn(boolean fromYoutube) {
        if (busy) return;
        busy = true;
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.google_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation,
                activity.getMainExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            public void onResult(GetCredentialResponse response) {
                busy = false;
                if (cancellation.isCanceled()) return;
                try {
                    Credential c = response.getCredential();
                    if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                        message("Google ส่งข้อมูลบัญชีที่แอปอ่านไม่ได้");
                        return;
                    }
                    String email = GoogleIdTokenCredential.createFrom(c.getData()).getId();
                    profile.edit().putString("email", email).apply();
                    message("เข้าสู่ VEIL แล้ว");
                    if (fromYoutube) connectYoutube();
                } catch (Exception e) { message("อ่านบัญชี Google ไม่สำเร็จ"); }
            }
            public void onError(GetCredentialException error) {
                busy = false;
                if (!(error instanceof GetCredentialCancellationException)) message("เข้าสู่ Google ไม่สำเร็จ ลองอีกครั้งจากการตั้งค่า");
            }
        });
    }

    void connectYoutube() {
        if (email().isEmpty()) { show(true); return; }
        browse("", "เพลย์ลิสต์ของฉัน");
    }

    private String key(String list) { return email() + "/" + list; }

    private void browse(String list, String title) {
        try {
            String saved = library.getString(key(list), null);
            if (saved != null) {
                display(new JSONArray(saved), list, title, 0);
                return;
            }
        } catch (JSONException ignored) { library.edit().remove(key(list)).apply(); }
        Sheets sheet = new Sheets(activity, title);
        sheet.row(sheet.label("ซิงก์รายการจากบัญชี " + email() + " มาเก็บบนเครื่อง", 16, false));
        sheet.row(sheet.action("ซิงก์จาก YouTube", () -> { sheet.dialog.dismiss(); authorize(list, title); }));
        sheet.dialog.show();
    }

    private void authorize(String list, String title) {
        if (busy) return;
        busy = true;
        syncList = list;
        syncTitle = title;
        AuthorizationRequest req = AuthorizationRequest.builder().setAccount(new Account(email(), "com.google"))
                .setRequestedScopes(Collections.singletonList(new Scope(READ))).build();
        Identity.getAuthorizationClient(activity).authorize(req).addOnSuccessListener(activity, result -> {
            busy = false;
            try {
                if (result.hasResolution()) activity.startIntentSenderForResult(
                        result.getPendingIntent().getIntentSender(), CONSENT, null, 0, 0, 0);
                else granted(result);
            } catch (android.content.IntentSender.SendIntentException e) { message("เปิดหน้าขอสิทธิ์ไม่ได้"); }
        }).addOnFailureListener(activity, error -> { busy = false; message("ขอสิทธิ์ YouTube ไม่สำเร็จ"); });
    }

    void result(int code, Intent data) {
        if (code != Activity.RESULT_OK || data == null || email().isEmpty()) return;
        try { granted(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)); }
        catch (com.google.android.gms.common.api.ApiException e) { message("เชื่อมบัญชี YouTube ไม่สำเร็จ"); }
    }

    private void granted(AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || !result.getGrantedScopes().contains(READ)) { message("ยังไม่ได้รับสิทธิ์อ่าน YouTube"); return; }
        String owner = email();
        String list = syncList == null ? "" : syncList;
        String title = syncTitle == null ? "เพลย์ลิสต์ของฉัน" : syncTitle;
        busy = true;
        message("กำลังซิงก์ " + title);
        worker.execute(() -> {
            try {
                JSONArray rows = fetch(token, list);
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (cancellation.isCanceled() || !owner.equals(email())) return;
                    String cacheKey = key(list);
                    library.edit().putString(cacheKey, rows.toString())
                            .putLong("updated:" + cacheKey, System.currentTimeMillis()).apply();
                    display(rows, list, title, 0);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    busy = false;
                    message("ซิงก์ไม่สำเร็จ ข้อมูลที่ซิงก์ไว้ก่อนหน้ายังเปิดดูได้");
                });
            }
        });
    }

    private JSONArray fetch(String token, String list) throws Exception {
        JSONArray rows = new JSONArray();
        String next = "";
        do {
            if (cancellation.isCanceled()) throw new java.io.InterruptedIOException();
            Uri.Builder url = Uri.parse("https://www.googleapis.com/youtube/v3/").buildUpon()
                    .appendPath(list.isEmpty() ? "playlists" : "playlistItems")
                    .appendQueryParameter("part", "snippet").appendQueryParameter("maxResults", "50")
                    .appendQueryParameter(list.isEmpty() ? "mine" : "playlistId", list.isEmpty() ? "true" : list)
                    .appendQueryParameter("fields", list.isEmpty() ? "nextPageToken,items(id,snippet/title)"
                            : "nextPageToken,items(snippet(title,resourceId/videoId))");
            if (!next.isEmpty()) url.appendQueryParameter("pageToken", next);
            HttpsURLConnection conn = (HttpsURLConnection) new URL(url.build().toString()).openConnection();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                try (java.util.Scanner in = new java.util.Scanner(conn.getInputStream(), "UTF-8")) {
                    JSONObject page = new JSONObject(in.useDelimiter("\\A").next());
                    JSONArray items = page.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject snippet = item.getJSONObject("snippet");
                        String id = list.isEmpty() ? item.getString("id")
                                : snippet.getJSONObject("resourceId").optString("videoId");
                        if (!id.isEmpty()) rows.put(new JSONObject().put("id", id).put("title", snippet.getString("title")));
                    }
                    next = page.optString("nextPageToken");
                }
            } finally { conn.disconnect(); }
        } while (!next.isEmpty());
        return rows;
    }

    private void display(JSONArray rows, String list, String title, int offset) {
        Sheets sheet = new Sheets(activity, title);
        sheet.row(sheet.action("ซิงก์อีกครั้ง", () -> { sheet.dialog.dismiss(); authorize(list, title); }));
        if (!list.isEmpty()) {
            sheet.row(sheet.action("เปิดทั้งเพลย์ลิสต์", () -> { sheet.dialog.dismiss(); play.accept(list, ""); }));
            sheet.row(sheet.action("กลับไปคลังเพลง", () -> { sheet.dialog.dismiss(); connectYoutube(); }));
        }
        if (rows.length() == 0) sheet.row(sheet.label("ยังไม่มีรายการ", 16, false));
        for (int i = offset; i < Math.min(offset + 50, rows.length()); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id"), name = row.optString("title");
            sheet.row(sheet.action(name, () -> {
                sheet.dialog.dismiss();
                if (list.isEmpty()) browse(id, name);
                else play.accept(list, id);
            }));
        }
        if (offset > 0) sheet.row(sheet.action("หน้าก่อนหน้า", () -> {
            sheet.dialog.dismiss(); display(rows, list, title, offset - 50);
        }));
        if (offset + 50 < rows.length()) sheet.row(sheet.action("หน้าถัดไป", () -> {
            sheet.dialog.dismiss(); display(rows, list, title, offset + 50);
        }));
        sheet.dialog.show();
    }

    private void message(String text) {
        if (!cancellation.isCanceled()) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    void close() { cancellation.cancel(); worker.shutdownNow(); }
}
