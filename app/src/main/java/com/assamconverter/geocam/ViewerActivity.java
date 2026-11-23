package com.assamconverter.geocam;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen photo / video viewer, like the phone's gallery: swipe, share (only the file), map, delete. */
public class ViewerActivity extends AppCompatActivity {
    private Store store;
    private List<Store.Entry> list;
    private int i = 0;
    private ImageView img;
    private VideoView vid;
    private TextView count, caption;
    private ImageButton prev, next;
    private final ExecutorService load = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int loadId = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        vid = new VideoView(this);
        vid.setVisibility(View.GONE);
        root.addView(vid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        MediaController mc = new MediaController(this);
        mc.setAnchorView(vid);
        vid.setMediaController(mc);

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0x88000000);
        int p = Util.dp(this, 6);
        top.setPadding(p, p, p, p);
        ImageButton back = white(R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        count = new TextView(this);
        count.setTextColor(Color.WHITE); count.setTextSize(16); count.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton log = white(R.drawable.ic_list, "Entry log");
        log.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        top.addView(log);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        prev = white(R.drawable.ic_back, "Previous");
        prev.setBackgroundResource(R.drawable.round_dark);
        prev.setOnClickListener(v -> go(-1));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Util.dp(this, 46), Util.dp(this, 46), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        pl.leftMargin = Util.dp(this, 8);
        root.addView(prev, pl);
        next = white(R.drawable.ic_back, "Next");
        next.setRotation(180);
        next.setBackgroundResource(R.drawable.round_dark);
        next.setOnClickListener(v -> go(1));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(Util.dp(this, 46), Util.dp(this, 46), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        nl.rightMargin = Util.dp(this, 8);
        root.addView(next, nl);

        // bottom bar
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(0x88000000);
        caption = new TextView(this);
        caption.setTextColor(0xFFDDDDDD); caption.setTextSize(13); caption.setGravity(Gravity.CENTER);
        caption.setPadding(p * 2, p, p * 2, 0);
        bottom.addView(caption);
        LinearLayout acts = new LinearLayout(this);
        acts.setPadding(0, p, 0, p * 2);
        acts.addView(action(R.drawable.ic_share, "Share", v -> share()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        acts.addView(action(R.drawable.ic_nav, "Map", v -> navigate()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        acts.addView(action(R.drawable.ic_delete, "Delete", v -> delete()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottom.addView(acts);
        root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent e, float vx, float vy) {
                if (a == null || e == null) return false;
                float dx = e.getX() - a.getX();
                if (Math.abs(dx) > Util.dp(ViewerActivity.this, 60) && Math.abs(dx) > Math.abs(e.getY() - a.getY())) { go(dx < 0 ? 1 : -1); return true; }
                return false;
            }
        });
        img.setOnTouchListener((v, ev) -> gd.onTouchEvent(ev));
        setContentView(root);

        list = store.all();
        String code = getIntent().getStringExtra("code");
        if (code != null) for (int k = 0; k < list.size(); k++) if (list.get(k).code.equals(code)) i = k;
        if (list.isEmpty()) {
            Toast.makeText(this, "No photos yet — tap the white button to take one", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        show();
    }

    private ImageButton white(int res, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(Color.WHITE);
        b.setBackground(null);
        b.setContentDescription(desc);
        int p = Util.dp(this, 10);
        b.setPadding(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(Util.dp(this, 46), Util.dp(this, 46)));
        return b;
    }

    private View action(int res, String label, View.OnClickListener l) {
        LinearLayout a = new LinearLayout(this);
        a.setOrientation(LinearLayout.VERTICAL);
        a.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        a.addView(iv, new LinearLayout.LayoutParams(Util.dp(this, 26), Util.dp(this, 26)));
        TextView t = new TextView(this);
        t.setText(label); t.setTextColor(Color.WHITE); t.setTextSize(12.5f);
        t.setPadding(0, Util.dp(this, 4), 0, 0);
        a.addView(t);
        a.setMinimumHeight(Util.dp(this, 56));
        a.setOnClickListener(l);
        a.setContentDescription(label);
        return a;
    }

    private Uri uriOf(Store.Entry e) {
        if (!e.file.isEmpty() && new File(e.file).exists()) return FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".files", new File(e.file));
        return e.uri.isEmpty() ? null : Uri.parse(e.uri);
    }

    private void show() {
        if (list.isEmpty()) { finish(); return; }
        if (i >= list.size()) i = list.size() - 1;
        Store.Entry e = list.get(i);
        count.setText((i + 1) + " / " + list.size());
        caption.setText("No. " + e.entryNo + (e.place.isEmpty() ? "" : "  ·  " + e.place));
        prev.setVisibility(i > 0 ? View.VISIBLE : View.GONE);
        next.setVisibility(i < list.size() - 1 ? View.VISIBLE : View.GONE);
        vid.stopPlayback();
        Uri u = uriOf(e);
        if (e.video()) {
            img.setImageBitmap(store.loadThumb(e.code));
            if (u != null) {
                vid.setVisibility(View.VISIBLE);
                vid.setVideoURI(u);
                vid.setOnPreparedListener(mp -> { img.setImageDrawable(null); vid.start(); });
            }
        } else {
            vid.setVisibility(View.GONE);
            img.setImageBitmap(store.loadThumb(e.code));
            final int id = ++loadId;
            final int maxSide = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
            load.execute(() -> {
                Bitmap bmp = decode(u, maxSide);
                ui.post(() -> { if (id == loadId && bmp != null) img.setImageBitmap(bmp); else if (id == loadId && bmp == null) caption.setText("This photo was deleted from the Gallery"); });
            });
        }
    }

    private Bitmap decode(Uri u, int maxSide) {
        if (u == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            try (InputStream in = getContentResolver().openInputStream(u)) { return BitmapFactory.decodeStream(in, null, d); }
        } catch (Exception e) { return null; }
    }

    private void go(int d) {
        int n = i + d;
        if (n < 0 || n >= list.size()) return;
        i = n;
        show();
    }

    /** Shares only the photo / video — no location text, no links. */
    private void share() {
        Store.Entry e = list.get(i);
        Uri u = uriOf(e);
        if (u == null) return;
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType(e.mime);
        s.putExtra(Intent.EXTRA_STREAM, u);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Share"));
    }

    private void navigate() {
        Store.Entry e = list.get(i);
        MainActivity.open(this, "https://www.google.com/maps/dir/?api=1&destination=" + e.lat + "," + e.lng);
    }

    private void delete() {
        Store.Entry e = list.get(i);
        new AlertDialog.Builder(this).setMessage("Delete this " + (e.video() ? "video" : "photo") + " from the phone and the log?")
                .setPositiveButton("Delete", (d, w) -> {
                    vid.stopPlayback();
                    store.delete(e);
                    try { Api.call(this, "entry_del", new JSONObject().put("code", e.code), null); } catch (Exception ignored) { }
                    list.remove(i);
                    if (list.isEmpty()) finish(); else show();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // came back from the log (maybe something was deleted there)
        if (list != null) {
            List<Store.Entry> fresh = store.all();
            if (fresh.size() != list.size()) { list = fresh; if (list.isEmpty()) { finish(); return; } show(); }
        }
    }

    @Override
    protected void onDestroy() {
        load.shutdown();
        super.onDestroy();
    }
}
