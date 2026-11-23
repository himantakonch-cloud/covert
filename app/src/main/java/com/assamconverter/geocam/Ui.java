package com.assamconverter.geocam;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Builds the simple screens (settings, login, log) in code so they all look alike. */
final class Ui {
    private Ui() { }

    static int c(Context x, int res) { return ContextCompat.getColor(x, res); }

    /** Screen with a top bar (back + title) and a scrolling column. Returns the column. */
    static LinearLayout screen(Activity a, String title, View... actions) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(a, R.color.paper));
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Util.dp(a, 8);
        bar.setPadding(p, p, p, p);
        ImageButton back = icon(a, R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> a.finish());
        bar.addView(back);
        TextView t = text(a, title, 20, true);
        t.setPadding(Util.dp(a, 8), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (View v : actions) bar.addView(v);
        root.addView(bar);
        View line = new View(a);
        line.setBackgroundColor(c(a, R.color.line));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int q = Util.dp(a, 14);
        col.setPadding(q, q, q, Util.dp(a, 28));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        a.setContentView(root);
        return col;
    }

    static ImageButton icon(Context a, int res, String desc) {
        ImageButton b = new ImageButton(a);
        b.setImageResource(res);
        b.setColorFilter(c(a, R.color.ink));
        b.setBackground(null);
        b.setContentDescription(desc);
        int p = Util.dp(a, 10);
        b.setPadding(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(Util.dp(a, 46), Util.dp(a, 46)));
        return b;
    }

    static TextView text(Context a, String s, float sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c(a, R.color.ink));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView muted(Context a, String s, float sp) {
        TextView t = text(a, s, sp, false);
        t.setTextColor(c(a, R.color.muted));
        return t;
    }

    /** A white rounded card (dark grey in dark mode). */
    static LinearLayout card(Context a, String heading) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.card_bg);
        int p = Util.dp(a, 14);
        l.setPadding(p, Util.dp(a, 6), p, Util.dp(a, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Util.dp(a, 12);
        l.setLayoutParams(lp);
        if (heading != null) {
            TextView h = muted(a, heading.toUpperCase(), 12);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setLetterSpacing(0.08f);
            h.setPadding(0, Util.dp(a, 8), 0, Util.dp(a, 4));
            l.addView(h);
        }
        return l;
    }

    /** Row: title (+ small text) on the left, any control on the right. */
    static LinearLayout row(Context a, String title, String sub, View right) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Util.dp(a, 54));
        int p = Util.dp(a, 6);
        r.setPadding(0, p, 0, p);
        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(a, title, 15, false));
        if (sub != null && !sub.isEmpty()) tx.addView(muted(a, sub, 12.5f));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right != null) r.addView(right);
        return r;
    }

    static MaterialSwitch sw(Context a, boolean on, android.widget.CompoundButton.OnCheckedChangeListener l) {
        MaterialSwitch s = new MaterialSwitch(a);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    static MaterialButton button(Context a, String text, boolean primary) {
        MaterialButton b = new MaterialButton(a, null, primary ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(Util.dp(a, 52));
        b.setCornerRadius(Util.dp(a, 14));
        if (primary) b.setBackgroundTintList(ColorStateList.valueOf(c(a, R.color.accent)));
        else b.setTextColor(c(a, R.color.ink));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Util.dp(a, 10);
        b.setLayoutParams(lp);
        return b;
    }

    static EditText input(Context a, String hint, int type) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type == 0 ? InputType.TYPE_CLASS_TEXT : type);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(c(a, R.color.ink));
        e.setHintTextColor(c(a, R.color.muted));
        GradientDrawable g = new GradientDrawable();
        g.setColor(c(a, R.color.card));
        g.setStroke(Util.dp(a, 1.5f), c(a, R.color.line));
        g.setCornerRadius(Util.dp(a, 12));
        e.setBackground(g);
        int p = Util.dp(a, 14);
        e.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Util.dp(a, 6);
        e.setLayoutParams(lp);
        return e;
    }

    static TextView label(Context a, String s) {
        TextView t = muted(a, s, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Util.dp(a, 12), 0, 0);
        return t;
    }

    static View divider(Context a) {
        View v = new View(a);
        v.setBackgroundColor(c(a, R.color.line));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** Text input inside a dialog. */
    static FrameLayout dialogBox(Context a, View v) {
        FrameLayout f = new FrameLayout(a);
        int p = Util.dp(a, 20);
        f.setPadding(p, Util.dp(a, 8), p, 0);
        f.addView(v);
        return f;
    }
}
