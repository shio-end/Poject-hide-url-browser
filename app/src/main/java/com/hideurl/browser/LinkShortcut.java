package com.hideurl.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

final class LinkShortcut {
    static final String OPEN = "com.hideurl.browser.OPEN_BOOKMARK";
    static final int PHOTO = 492;
    private final Activity activity;
    String pendingUrl;
    String pendingName;

    LinkShortcut(Activity activity) { this.activity = activity; }

    void menu(String url) {
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (!manager.isRequestPinShortcutSupported()) {
            message("หน้าจอหลักของเครื่องนี้ไม่รองรับการเพิ่มทางลัด"); return;
        }
        String id = shortcutId(url);
        ShortcutInfo pinned = findPinned(manager, id);
        Sheets sheet = new Sheets(activity, pinned == null ? "สร้างทางลัดบนหน้าจอหลัก" : "แก้ไขทางลัด", true);
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setTextColor(sheet.theme.ink);
        name.setSelectAllOnFocus(true);
        name.setContentDescription("ชื่อไอคอนทางลัด");
        name.setText(pinned != null && pinned.getShortLabel() != null ? pinned.getShortLabel() : defaultName(url));
        sheet.row(sheet.label("ชื่อไอคอนทางลัด", 15, true));
        sheet.row(name);
        if (pinned != null) {
            sheet.row(sheet.action("บันทึกชื่อทางลัด", () -> {
                String label = readName(name);
                if (label == null) return;
                ShortcutInfo change = new ShortcutInfo.Builder(activity, id).setShortLabel(label).build();
                if (manager.updateShortcuts(Collections.singletonList(change))) {
                    sheet.dialog.dismiss();
                    message("เปลี่ยนชื่อทางลัดแล้ว");
                } else message("เปลี่ยนชื่อทางลัดไม่ได้ กรุณาลองอีกครั้ง");
            }));
        }
        sheet.row(sheet.action("ใช้ไอคอน VEIL", () -> {
            String label = readName(name);
            if (label == null) return;
            sheet.dialog.dismiss();
            pin(url, label, Icon.createWithResource(activity, R.drawable.app_icon));
        }));
        sheet.row(sheet.action("เลือกรูปและจัดกรอบ", () -> {
            String label = readName(name);
            if (label == null) return;
            sheet.dialog.dismiss();
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            pendingUrl = url;
            pendingName = label;
            try { activity.startActivityForResult(picker, PHOTO); }
            catch (android.content.ActivityNotFoundException e) { pendingUrl = null; pendingName = null; message("เปิดตัวเลือกรูปไม่ได้"); }
        }));
        sheet.dialog.show();
    }

    void photo(Uri uri) {
        String url = pendingUrl;
        String name = pendingName;
        pendingUrl = null;
        pendingName = null;
        if (url == null || uri == null) return;
        new Thread(() -> {
            try {
                Bitmap image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.getContentResolver(), uri),
                        (decoder, info, src) -> {
                            int side = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            decoder.setTargetSampleSize(Math.max(1, (side + 2047) / 2048));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed() && !activity.isFinishing()) crop(url, name == null ? defaultName(url) : name, image);
                });
            } catch (Exception error) { activity.runOnUiThread(() -> message("อ่านรูปไม่ได้ กรุณาเลือกรูปใหม่")); }
        }, "veil-shortcut-image").start();
    }

    private void crop(String url, String name, Bitmap image) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        CropView preview = new CropView(image);
        int side = Math.round(200 * activity.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(side, side);
        size.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        layout.addView(preview, size);
        SeekBar zoom = new SeekBar(activity);
        zoom.setContentDescription("ซูมรูปทางลัด");
        zoom.setMax(300);
        layout.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int n, boolean user) {
                preview.zoom = 1 + n / 100f;
                preview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        Sheets sheet = new Sheets(activity, "จัดกรอบไอคอน", true);
        sheet.row(sheet.label("ลากรูปเพื่อจัดตำแหน่ง เลื่อนแถบเพื่อซูม", 15, false));
        sheet.row(layout);
        sheet.row(sheet.action("เพิ่มทางลัด", () -> {
            sheet.dialog.dismiss();
            Bitmap icon = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            preview.render(new Canvas(icon), 256);
            pin(url, name, Icon.createWithBitmap(icon));
        }));
        sheet.dialog.show();
    }

    private final class CropView extends View {
        final Bitmap image;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        float x, y, zoom = 1, lastX, lastY;
        CropView(Bitmap image) {
            super(activity);
            this.image = image;
            x = image.getWidth() / 2f; y = image.getHeight() / 2f;
            setContentDescription("กรอบรูปทางลัด ลากเพื่อจัดตำแหน่ง");
        }
        float span() { return Math.min(image.getWidth(), image.getHeight()) / zoom; }
        void render(Canvas canvas, int size) {
            float span = span(), half = span / 2;
            x = Math.max(half, Math.min(x, image.getWidth() - half));
            y = Math.max(half, Math.min(y, image.getHeight() - half));
            canvas.save();
            canvas.scale(size / span, size / span);
            canvas.translate(half - x, half - y);
            canvas.drawBitmap(image, null, new Rect(0, 0, image.getWidth(), image.getHeight()), paint);
            canvas.restore();
        }
        @Override protected void onDraw(Canvas canvas) { render(canvas, getWidth()); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                x += (lastX - event.getX()) * span() / getWidth();
                y += (lastY - event.getY()) * span() / getWidth();
                invalidate();
            }
            lastX = event.getX(); lastY = event.getY();
            return true;
        }
    }

    private String readName(EditText field) {
        String label = field.getText().toString().trim();
        if (!label.isEmpty()) return label;
        field.setError("กรุณาใส่ชื่อไอคอนทางลัด");
        field.requestFocus();
        return null;
    }

    private String defaultName(String url) {
        String title = activity.getSharedPreferences("browser", Activity.MODE_PRIVATE)
                .getString("title:" + url, Uri.parse(url).getHost());
        return title == null || title.trim().isEmpty() ? url : title;
    }

    private String shortcutId(String url) {
        return "url-" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
    }

    private ShortcutInfo findPinned(ShortcutManager manager, String id) {
        for (ShortcutInfo pinned : manager.getPinnedShortcuts()) {
            if (id.equals(pinned.getId())) return pinned;
        }
        return null;
    }

    private void pin(String url, String name, Icon icon) {
        String id = shortcutId(url);
        Intent launch = new Intent(activity, MainActivity.class).setAction(OPEN).setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ShortcutInfo info = new ShortcutInfo.Builder(activity, id).setShortLabel(name).setIcon(icon).setIntent(launch).build();
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (findPinned(manager, id) != null) {
            message(manager.updateShortcuts(Collections.singletonList(info)) ? "อัปเดตไอคอนทางลัดแล้ว" : "อัปเดตทางลัดไม่ได้");
            return;
        }
        if (!manager.requestPinShortcut(info, null)) message("เพิ่มทางลัดบนหน้าจอหลักไม่ได้");
    }

    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show(); }
}
