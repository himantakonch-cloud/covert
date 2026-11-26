package com.assamconverter.geocam;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/** Settings: stamp, entries, camera, appearance, account, app version. */
public class SettingsActivity extends AppCompatActivity {
    private Prefs p;

    @Override
    protected void onCreate(Bundle b) {
        Prefs.applyTheme(this);
        super.onCreate(b);
        p = new Prefs(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        LinearLayout col = Ui.screen(this, "Settings");

        LinearLayout st = Ui.card(this, "Stamp");
        choice(st, "Lat / Long format", "coord", new String[]{"both", "dec", "dms"}, new String[]{"Decimal + DMS", "Decimal", "DMS (°′″)"}, "both");
        choice(st, "Position", "position", new String[]{"bottom", "top"}, new String[]{"Bottom", "Top"}, "bottom");
        choice(st, "Size", "size", new String[]{"0.8", "1", "1.25"}, new String[]{"Small", "Medium", "Large"}, "1");
        toggle(st, "Map picture", null, "map", true);
        toggle(st, "Address & village name", "From OpenStreetMap", "addr", true);
        toggle(st, "Altitude & direction", "e.g. Altitude 112 m · Facing NE 47°", "extra", true);
        col.addView(st);

        LinearLayout en = Ui.card(this, "Entries");
        textRow(en, "Entry prefix", "e.g. SITE- → SITE-12", "prefix", InputType.TYPE_CLASS_TEXT);
        textRow(en, "Next entry number", null, "next", InputType.TYPE_CLASS_NUMBER);
        textRow(en, "Note on every photo", "Shown only when filled", "note", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        col.addView(en);

        LinearLayout ca = Ui.card(this, "Camera");
        choice(ca, "Photo quality", "pq", new String[]{"high", "std"}, new String[]{"Highest", "Standard (smaller files)"}, "high");
        choice(ca, "Video quality", "vq", new String[]{"720", "1080"}, new String[]{"720p", "1080p"}, "720");
        toggle(ca, "Record sound in videos", "Asks for the microphone", "sound", true);
        toggle(ca, "Grid lines", null, "grid", false);
        choiceInt(ca, "Self-timer", "timer", new int[]{0, 3, 10}, new String[]{"Off", "3 seconds", "10 seconds"});
        col.addView(ca);

        LinearLayout ap = Ui.card(this, "Appearance");
        choice(ap, "Theme", "theme", new String[]{"auto", "light", "dark"}, new String[]{"Same as phone", "Light", "Dark"}, "auto");
        col.addView(ap);

        LinearLayout ac = Ui.card(this, "Account");
        if (p.loggedIn()) {
            ac.addView(Ui.row(this, p.userName(), p.userEmail() + " · photos are saved to your account", null));
            MaterialButton out = Ui.button(this, "Log out", false);
            out.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("Log out? Photos stay on this phone and in your Gallery.")
                    .setPositiveButton("Log out", (d, w) -> { Api.call(this, "logout", null, null); p.logout(); build(); })
                    .setNegativeButton("Cancel", null).show());
            ac.addView(out);
        } else {
            ac.addView(Ui.row(this, "Not logged in", "Photos are saved on this phone. Every photo still gets a verify link.", null));
            MaterialButton in = Ui.button(this, "Log in / Sign up — save photos online", true);
            in.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            ac.addView(in);
        }
        ac.addView(spacer());
        col.addView(ac);

        LinearLayout app = Ui.card(this, "App");
        boolean newer = !p.latest().isEmpty() && Util.cmpVersion(BuildConfig.VERSION_NAME, p.latest()) < 0;
        MaterialButton chk = Ui.button(this, newer ? "Update to " + p.latest() : "Check for update", newer);
        chk.setOnClickListener(v -> Api.call(this, "hello", null, r -> {
            org.json.JSONObject a = r.optJSONObject("cfg") == null ? null : r.optJSONObject("cfg").optJSONObject("app");
            if (a != null) { p.put("latest", a.optString("latest")); p.put("minVersion", a.optString("min")); p.put("apkUrl", a.optString("apk_url")); p.put("updateNote", a.optString("note")); }
            if (!r.optBoolean("ok") && "network".equals(r.optString("error"))) { android.widget.Toast.makeText(this, "No internet connection", android.widget.Toast.LENGTH_SHORT).show(); return; }
            MainActivity.checkUpdate(this, true);
        }));
        app.addView(Ui.row(this, "GeoTag Camera " + BuildConfig.VERSION_NAME, newer ? "Version " + p.latest() + " is available" : "Free app by AssamConverter", null));
        app.addView(chk);
        MaterialButton log = Ui.button(this, "Entry log", false);
        log.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        app.addView(log);
        MaterialButton team = Ui.button(this, "About & Team", false);
        team.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        app.addView(team);
        MaterialButton web = Ui.button(this, "Open assamconverter.com", false);
        web.setOnClickListener(v -> MainActivity.open(this, BuildConfig.BASE_URL + "/"));
        app.addView(web);
        app.addView(spacer());
        col.addView(app);

        TextView foot = Ui.muted(this, "Maps and addresses © OpenStreetMap contributors", 12);
        foot.setGravity(android.view.Gravity.CENTER);
        col.addView(foot);
    }

    private android.view.View spacer() {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Util.dp(this, 10)));
        return v;
    }

    private void toggle(LinearLayout card, String title, String sub, String key, boolean def) {
        if (card.getChildCount() > 1) card.addView(Ui.divider(this));
        card.addView(Ui.row(this, title, sub, Ui.sw(this, p.b(key, def), (bt, on) -> p.put(key, on))));
    }

    private void choice(LinearLayout card, String title, String key, String[] vals, String[] labels, String def) {
        if (card.getChildCount() > 1) card.addView(Ui.divider(this));
        String cur = p.s(key, def);
        int idx = 0;
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(cur)) idx = i;
        TextView v = Ui.text(this, labels[idx] + "  ›", 14, true);
        v.setTextColor(Ui.c(this, R.color.accent));
        LinearLayout r = Ui.row(this, title, null, v);
        final int sel = idx;
        r.setOnClickListener(x -> new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, sel, (d, w) -> {
            p.put(key, vals[w]);
            d.dismiss();
            if ("theme".equals(key)) { Prefs.applyTheme(this); recreate(); } else build();
        }).show());
        card.addView(r);
    }

    private void choiceInt(LinearLayout card, String title, String key, int[] vals, String[] labels) {
        String[] sv = new String[vals.length];
        for (int i = 0; i < vals.length; i++) sv[i] = String.valueOf(vals[i]);
        if (card.getChildCount() > 1) card.addView(Ui.divider(this));
        int cur = p.i(key, 0), idx = 0;
        for (int i = 0; i < vals.length; i++) if (vals[i] == cur) idx = i;
        TextView v = Ui.text(this, labels[idx] + "  ›", 14, true);
        v.setTextColor(Ui.c(this, R.color.accent));
        LinearLayout r = Ui.row(this, title, null, v);
        final int sel = idx;
        r.setOnClickListener(x -> new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, sel, (d, w) -> {
            p.put(key, vals[w]); d.dismiss(); build();
        }).show());
        card.addView(r);
    }

    private void textRow(LinearLayout card, String title, String sub, String key, int type) {
        if (card.getChildCount() > 1) card.addView(Ui.divider(this));
        boolean num = type == InputType.TYPE_CLASS_NUMBER;
        String cur = num ? String.valueOf(p.next()) : p.s(key, "");
        TextView v = Ui.text(this, (cur.isEmpty() ? "Add" : cur) + "  ›", 14, true);
        v.setTextColor(Ui.c(this, R.color.accent));
        v.setMaxWidth(Util.dp(this, 150));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout r = Ui.row(this, title, sub, v);
        r.setOnClickListener(x -> {
            EditText e = Ui.input(this, title, type);
            e.setText(cur);
            new AlertDialog.Builder(this).setTitle(title).setView(Ui.dialogBox(this, e))
                    .setPositiveButton("Save", (d, w) -> {
                        String t = e.getText().toString().trim();
                        if (num) { try { p.put(key, Math.max(1, Integer.parseInt(t))); } catch (Exception ignored) { } }
                        else p.put(key, t.length() > 100 ? t.substring(0, 100) : t);
                        build();
                    }).setNegativeButton("Cancel", null).show();
        });
        card.addView(r);
    }
}
