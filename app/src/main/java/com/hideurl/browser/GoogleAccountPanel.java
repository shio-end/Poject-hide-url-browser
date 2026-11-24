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
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

final class GoogleAccountPanel {
    static final int CONSENT = 490;
    private static final String READ = "https://www.googleapis.com/auth/youtube.readonly";
    private final Activity activity;
    private final SharedPreferences profile;
    private final CancellationSignal cancellation = new CancellationSignal();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Consumer<String> openPlaylist;
    private boolean busy;

    GoogleAccountPanel(Activity activity, Consumer<String> openPlaylist) {
        this.activity = activity;
        this.openPlaylist = openPlaylist;
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
        Sheets s = new Sheets(activity, "เพลย์ลิสต์ YouTube");
        s.row(s.label("VEIL ขอสิทธิ์อ่านข้อมูล YouTube เพื่อแสดงเพลย์ลิสต์ของ " + email(), 16, false));
        s.row(s.action("ดูเพลย์ลิสต์ของฉัน", () -> { s.dialog.dismiss(); authorize(); }));
        s.dialog.show();
    }

    private void authorize() {
        if (busy) return;
        busy = true;
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
        load(token, "", email());
    }

    private void load(String token, String next, String owner) {
        message("กำลังโหลดเพลย์ลิสต์…");
        worker.execute(() -> {
            HttpsURLConnection connection = null;
            try {
                Uri.Builder url = Uri.parse("https://www.googleapis.com/youtube/v3/playlists").buildUpon()
                        .appendQueryParameter("part", "snippet").appendQueryParameter("mine", "true")
                        .appendQueryParameter("maxResults", "50");
                if (!next.isEmpty()) url.appendQueryParameter("pageToken", next);
                connection = (HttpsURLConnection) new URL(url.build().toString()).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try (java.util.Scanner in = new java.util.Scanner(connection.getInputStream(), "UTF-8")) {
                    JSONObject page = new JSONObject(in.useDelimiter("\\A").next());
                    activity.runOnUiThread(() -> {
                        if (!cancellation.isCanceled() && owner.equals(email())) display(page, token, owner);
                    });
                }
            } catch (Exception e) { activity.runOnUiThread(() -> message("โหลดเพลย์ลิสต์ไม่ได้ กรุณาลองอีกครั้ง")); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void display(JSONObject page, String token, String owner) {
        Sheets s = new Sheets(activity, "เพลย์ลิสต์ YouTube ของฉัน");
        s.row(s.label("พรีวิวเพลงใช้เซสชันของเว็บไซต์ เพลย์ลิสต์ส่วนตัวอาจเปิดเล่นไม่ได้", 14, false));
        JSONArray items = page.optJSONArray("items");
        if (items == null || items.length() == 0) s.row(s.label("ยังไม่มีเพลย์ลิสต์", 16, false));
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || item.optJSONObject("snippet") == null) continue;
            String id = item.optString("id");
            s.row(s.action(item.optJSONObject("snippet").optString("title"), () -> {
                s.dialog.dismiss();
                openPlaylist.accept(id);
            }));
        }
        String next = page.optString("nextPageToken");
        if (!next.isEmpty()) s.row(s.action("หน้าถัดไป", () -> { s.dialog.dismiss(); load(token, next, owner); }));
        s.dialog.show();
    }

    private void message(String text) {
        if (!cancellation.isCanceled()) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    void close() { cancellation.cancel(); worker.shutdownNow(); }
}
