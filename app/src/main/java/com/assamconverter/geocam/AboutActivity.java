package com.assamconverter.geocam;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/** About GeoTag Camera and the people who made it. */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        Prefs.applyTheme(this);
        super.onCreate(b);
        LinearLayout col = Ui.screen(this, "About & Team");

        // app header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(0, Util.dp(this, 12), 0, Util.dp(this, 18));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(Util.dp(this, 84), Util.dp(this, 84)));
        TextView name = Ui.text(this, "GeoTag Camera", 24, true);
        name.setPadding(0, Util.dp(this, 12), 0, 0);
        head.addView(name);
        head.addView(Ui.muted(this, "Version " + BuildConfig.VERSION_NAME + " · by AssamConverter", 13.5f));
        TextView tag = Ui.text(this, "Photos and videos with a trusted GPS stamp — place, map, coordinates, date & time, and a verify link for every entry.", 14.5f, false);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(Util.dp(this, 8), Util.dp(this, 12), Util.dp(this, 8), 0);
        tag.setLineSpacing(0, 1.2f);
        head.addView(tag);
        col.addView(head);

        // team
        LinearLayout team = Ui.card(this, "Our team");
        team.addView(person("Himanta Jyoti Konch", "Founder", "HK", 0xFFE0561F, 0xFF8A2C08));
        team.addView(Ui.divider(this));
        team.addView(person("Durlav Gogoi", "UI/UX Designer", "DG", 0xFF2563EB, 0xFF1E3A8A));
        team.addView(spacer(6));
        col.addView(team);

        // about
        LinearLayout about = Ui.card(this, "About");
        TextView t = Ui.text(this, "GeoTag Camera is a free app from assamconverter.com. Every photo carries its real GPS location, the place name and the exact time, so field work, site visits and reports can be trusted. Photos stay on your phone; with an account, your entry log is also saved online.", 14, false);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 10));
        about.addView(t);
        col.addView(about);

        MaterialButton web = Ui.button(this, "Visit assamconverter.com", true);
        web.setOnClickListener(v -> MainActivity.open(this, BuildConfig.BASE_URL + "/"));
        col.addView(web);

        TextView foot = Ui.muted(this, "Made with ❤ in Assam\nMaps and addresses © OpenStreetMap contributors", 12.5f);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Util.dp(this, 20), 0, 0);
        foot.setLineSpacing(0, 1.3f);
        col.addView(foot);
    }

    private LinearLayout person(String name, String role, String initials, int c1, int c2) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Util.dp(this, 12);
        r.setPadding(0, p, 0, p);

        TextView av = new TextView(this);
        av.setText(initials);
        av.setTextColor(Color.WHITE);
        av.setTextSize(18);
        av.setTypeface(Typeface.DEFAULT_BOLD);
        av.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        av.setBackground(g);
        r.addView(av, new LinearLayout.LayoutParams(Util.dp(this, 56), Util.dp(this, 56)));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Util.dp(this, 14), 0, 0, 0);
        tx.addView(Ui.text(this, name, 17, true));
        TextView badge = Ui.text(this, role, 12.5f, true);
        badge.setTextColor(c1);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((c1 & 0x00FFFFFF) | 0x22000000);
        bg.setCornerRadius(Util.dp(this, 99));
        badge.setBackground(bg);
        badge.setPadding(Util.dp(this, 10), Util.dp(this, 3), Util.dp(this, 10), Util.dp(this, 3));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Util.dp(this, 5);
        tx.addView(badge, blp);
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }

    private android.view.View spacer(int dp) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Util.dp(this, dp)));
        return v;
    }
}
