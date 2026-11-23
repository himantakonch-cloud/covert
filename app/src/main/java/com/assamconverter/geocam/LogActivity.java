package com.assamconverter.geocam;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Entry log: every photo/video with its place and coordinates — search, navigate, verify link, export. */
public class LogActivity extends AppCompatActivity {
    private Store store;
    private Prefs prefs;
    private final List<Store.Entry> all = new ArrayList<>(), shown = new ArrayList<>();
    private Adapter adapter;
    private TextView info;
    private LinearLayout guest;
    private String q = "";

    @Override
    protected void onCreate(Bundle b) {
        Prefs.applyTheme(this);
        super.onCreate(b);
        store = new Store(this);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.c(this, R.color.paper));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Util.dp(this, 8);
        bar.setPadding(p, p, p, p);
        ImageButton back = Ui.icon(this, R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView t = Ui.text(this, "Entry log", 20, true);
        t.setPadding(p, 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton sync = Ui.icon(this, R.drawable.ic_sync, "Sync");
        sync.setOnClickListener(v -> { Store.syncAll(this); android.widget.Toast.makeText(this, "Syncing…", android.widget.Toast.LENGTH_SHORT).show(); v.postDelayed(this::reload, 2500); });
        bar.addView(sync);
        ImageButton csv = Ui.icon(this, R.drawable.ic_csv, "Export CSV");
        csv.setOnClickListener(v -> exportCsv());
        bar.addView(csv);
        root.addView(bar);
        root.addView(Ui.divider(this));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        int q2 = Util.dp(this, 14);
        head.setPadding(q2, Util.dp(this, 10), q2, 0);
        guest = Ui.card(this, null);
        TextView g = Ui.text(this, "You're not logged in — photos stay on this phone and every photo has a verify link. Tap here to log in and save them online.", 13.5f, false);
        g.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 8));
        guest.addView(g);
        guest.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        head.addView(guest);
        EditText search = Ui.input(this, "Search entry no., place, note or code…", 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) { q = s.toString().trim().toLowerCase(Locale.ROOT); filter(); }
        });
        head.addView(search);
        info = Ui.muted(this, "", 12.5f);
        info.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 4));
        head.addView(info);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setClipToPadding(false);
        lv.setPadding(q2, 0, q2, Util.dp(this, 24));
        lv.addHeaderView(head, null, false);
        adapter = new Adapter();
        lv.setAdapter(adapter);
        root.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        guest.setVisibility(prefs.loggedIn() ? View.GONE : View.VISIBLE);
        reload();
    }

    private void reload() {
        all.clear();
        all.addAll(store.all());
        filter();
        // entries from other phones (same account) or this phone's online log
        Api.call(this, "entries", null, r -> {
            JSONArray a = r.optJSONArray("entries");
            if (a == null) return;
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.optJSONObject(k);
                boolean have = false;
                for (Store.Entry e : all) if (e.code.equals(o.optString("code"))) { have = true; break; }
                if (have) continue;
                Store.Entry e = new Store.Entry();
                e.code = o.optString("code"); e.kind = o.optString("kind"); e.lat = o.optDouble("lat"); e.lng = o.optDouble("lng"); e.acc = (float) o.optDouble("acc", 0);
                e.taken = (long) o.optDouble("taken"); e.offset = o.optInt("offset", 330); e.place = o.optString("place"); e.address = o.optString("address");
                e.note = o.optString("note"); e.entryNo = o.optString("entry_no"); e.dur = o.optDouble("duration", 0); e.synced = true;
                e.file = "remote";
                all.add(e);
            }
            java.util.Collections.sort(all, (x, y) -> Long.compare(y.taken, x.taken));
            filter();
        });
    }

    private void filter() {
        shown.clear();
        int waiting = 0;
        for (Store.Entry e : all) {
            if (!e.synced) waiting++;
            String hay = (e.entryNo + " " + e.place + " " + e.address + " " + e.note + " " + e.code).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) shown.add(e);
        }
        info.setText(all.isEmpty() ? "Photos and videos you take appear here with their location." : all.size() + " entries" + (waiting > 0 ? " · " + waiting + " waiting to sync" : " · all synced") + (q.isEmpty() ? "" : " · " + shown.size() + " match"));
        adapter.notifyDataSetChanged();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Store.Entry e = shown.get(pos);
            LinearLayout card = Ui.card(LogActivity.this, null);
            card.setPadding(Util.dp(LogActivity.this, 12), Util.dp(LogActivity.this, 12), Util.dp(LogActivity.this, 12), Util.dp(LogActivity.this, 10));
            LinearLayout top = new LinearLayout(LogActivity.this);
            ImageView th = new ImageView(LogActivity.this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setBackgroundColor(Color.BLACK);
            Bitmap bm = "remote".equals(e.file) ? null : store.loadThumb(e.code);
            if (bm != null) th.setImageBitmap(bm); else { th.setImageResource(R.drawable.ic_pin); th.setColorFilter(Ui.c(LogActivity.this, R.color.accent)); th.setScaleType(ImageView.ScaleType.CENTER_INSIDE); }
            th.setOnClickListener(v -> { if (!"remote".equals(e.file)) startActivity(new Intent(LogActivity.this, ViewerActivity.class).putExtra("code", e.code)); });
            top.addView(th, new LinearLayout.LayoutParams(Util.dp(LogActivity.this, 76), Util.dp(LogActivity.this, 76)));
            LinearLayout tx = new LinearLayout(LogActivity.this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(Util.dp(LogActivity.this, 12), 0, 0, 0);
            TextView h = Ui.text(LogActivity.this, "No. " + e.entryNo + (e.video() ? "  ▶ " + Math.round(e.dur) + "s" : "") + "  ·  " + (e.place.isEmpty() ? "Location" : e.place), 14.5f, true);
            tx.addView(h);
            tx.addView(Ui.muted(LogActivity.this, String.format(Locale.US, "%.6f, %.6f", e.lat, e.lng) + (e.acc > 0 ? " · ±" + Math.round(e.acc) + " m" : ""), 12.5f));
            tx.addView(Ui.muted(LogActivity.this, Util.when(e.taken, e.offset), 12.5f));
            if (!e.note.isEmpty()) tx.addView(Ui.muted(LogActivity.this, "Note: " + e.note, 12.5f));
            TextView st = Ui.muted(LogActivity.this, "Entry " + e.code + "  ·  " + ("remote".equals(e.file) ? "other phone" : e.synced ? "✓ synced" : "waiting to sync"), 12f);
            st.setTextColor(e.synced ? Ui.c(LogActivity.this, R.color.ok) : 0xFFB45309);
            tx.addView(st);
            top.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(top);

            LinearLayout acts = new LinearLayout(LogActivity.this);
            acts.setPadding(0, Util.dp(LogActivity.this, 8), 0, 0);
            acts.addView(small("Navigate", true, v -> MainActivity.open(LogActivity.this, "https://www.google.com/maps/dir/?api=1&destination=" + e.lat + "," + e.lng)));
            if (e.synced) acts.addView(small("Verify page", false, v -> MainActivity.open(LogActivity.this, BuildConfig.BASE_URL + "/geocam/v/" + e.code)));
            acts.addView(small("Delete", false, v -> new AlertDialog.Builder(LogActivity.this).setMessage("Delete this entry" + ("remote".equals(e.file) ? " from your online log?" : " from the phone and the log?"))
                    .setPositiveButton("Delete", (d, w) -> {
                        if (!"remote".equals(e.file)) store.delete(e);
                        try { Api.call(LogActivity.this, "entry_del", new JSONObject().put("code", e.code), null); } catch (Exception ignored) { }
                        all.remove(e); filter();
                    }).setNegativeButton("Cancel", null).show()));
            card.addView(acts);
            // list rows need list layout params: wrap the card
            android.widget.FrameLayout wrap = new android.widget.FrameLayout(LogActivity.this);
            wrap.setPadding(0, 0, 0, Util.dp(LogActivity.this, 10));
            card.setLayoutParams(new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            wrap.addView(card);
            wrap.setLayoutParams(new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }
    }

    private TextView small(String t, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(this, t, 13, true);
        int p = Util.dp(this, 10);
        b.setPadding(p, Util.dp(this, 7), p, Util.dp(this, 7));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Util.dp(this, 10));
        if (primary) { g.setColor(Ui.c(this, R.color.accent)); b.setTextColor(Color.WHITE); }
        else { g.setColor(Color.TRANSPARENT); g.setStroke(Util.dp(this, 1), Ui.c(this, R.color.line)); }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Util.dp(this, 6);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void exportCsv() {
        if (all.isEmpty()) { android.widget.Toast.makeText(this, "The log is empty", android.widget.Toast.LENGTH_SHORT).show(); return; }
        try {
            StringBuilder s = new StringBuilder("﻿entry_no,code,type,place,address,latitude,longitude,accuracy_m,taken,note,map_link,verify_link\n");
            for (Store.Entry e : all) {
                String[] r = {e.entryNo, e.code, e.kind, e.place, e.address, String.format(Locale.US, "%.6f", e.lat), String.format(Locale.US, "%.6f", e.lng),
                        String.valueOf(Math.round(e.acc)), Util.when(e.taken, e.offset), e.note,
                        String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", e.lat, e.lng), BuildConfig.BASE_URL + "/geocam/v/" + e.code};
                for (int k = 0; k < r.length; k++) { if (k > 0) s.append(','); s.append('"').append(r[k] == null ? "" : r[k].replace("\"", "\"\"")).append('"'); }
                s.append('\n');
            }
            File d = new File(getCacheDir(), "shared");
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
            File f = new File(d, "geotag-entry-log.csv");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(s.toString().getBytes("UTF-8")); }
            Uri u = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".files", f);
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Save or share the entry log"));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not export", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
