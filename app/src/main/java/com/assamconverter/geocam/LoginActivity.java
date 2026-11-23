package com.assamconverter.geocam;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONObject;

/** Optional login / sign up with an assamconverter.com account — saves your photos to your account. */
public class LoginActivity extends AppCompatActivity {
    private boolean signup = false, required = false;
    private LinearLayout loginBox, signupBox;
    private TextView msg, title, sub;

    @Override
    protected void onCreate(Bundle b) {
        Prefs.applyTheme(this);
        super.onCreate(b);
        required = getIntent().getBooleanExtra("required", false);
        LinearLayout col = Ui.screen(this, "Account");

        title = Ui.text(this, "Welcome back", 26, true);
        title.setPadding(0, Util.dp(this, 6), 0, 0);
        col.addView(title);
        sub = Ui.muted(this, "Log in with your assamconverter.com account to save your photos online and see them on any phone.", 14);
        sub.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 14));
        col.addView(sub);

        MaterialButtonToggleGroup tabs = new MaterialButtonToggleGroup(this);
        tabs.setSingleSelection(true);
        tabs.setSelectionRequired(true);
        MaterialButton tLogin = tab("Log in"), tSign = tab("Sign up");
        tLogin.setId(View.generateViewId()); tSign.setId(View.generateViewId());
        tabs.addView(tLogin, new LinearLayout.LayoutParams(0, Util.dp(this, 48), 1));
        tabs.addView(tSign, new LinearLayout.LayoutParams(0, Util.dp(this, 48), 1));
        tabs.check(tLogin.getId());
        col.addView(tabs, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // log in
        loginBox = new LinearLayout(this); loginBox.setOrientation(LinearLayout.VERTICAL);
        loginBox.addView(Ui.label(this, "E-mail, mobile or username"));
        EditText lId = Ui.input(this, "you@example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        loginBox.addView(lId);
        loginBox.addView(Ui.label(this, "Password"));
        EditText lPw = Ui.input(this, "Your password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginBox.addView(lPw);
        MaterialButton bLogin = Ui.button(this, "Log in", true);
        loginBox.addView(bLogin);
        TextView forgot = Ui.muted(this, "Forgot password? Reset it on assamconverter.com", 13);
        forgot.setGravity(Gravity.CENTER);
        forgot.setPadding(0, Util.dp(this, 12), 0, 0);
        forgot.setOnClickListener(v -> MainActivity.open(this, BuildConfig.BASE_URL + "/login.php"));
        loginBox.addView(forgot);
        col.addView(loginBox);

        // sign up
        signupBox = new LinearLayout(this); signupBox.setOrientation(LinearLayout.VERTICAL); signupBox.setVisibility(View.GONE);
        signupBox.addView(Ui.label(this, "Full name"));
        EditText sName = Ui.input(this, "Your name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        signupBox.addView(sName);
        signupBox.addView(Ui.label(this, "E-mail"));
        EditText sEmail = Ui.input(this, "you@example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        signupBox.addView(sEmail);
        signupBox.addView(Ui.label(this, "Mobile (optional)"));
        EditText sMob = Ui.input(this, "10-digit mobile number", InputType.TYPE_CLASS_PHONE);
        signupBox.addView(sMob);
        signupBox.addView(Ui.label(this, "Password"));
        EditText sPw = Ui.input(this, "At least 8 characters", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        signupBox.addView(sPw);
        MaterialButton bSign = Ui.button(this, "Create free account", true);
        signupBox.addView(bSign);
        TextView terms = Ui.muted(this, "One account for the app and assamconverter.com.", 13);
        terms.setGravity(Gravity.CENTER);
        terms.setPadding(0, Util.dp(this, 10), 0, 0);
        signupBox.addView(terms);
        col.addView(signupBox);

        msg = Ui.text(this, "", 14, false);
        msg.setPadding(Util.dp(this, 12), Util.dp(this, 10), Util.dp(this, 12), Util.dp(this, 10));
        msg.setVisibility(View.GONE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Util.dp(this, 14);
        col.addView(msg, mlp);

        if (!required) {
            MaterialButton guest = Ui.button(this, "← Continue without login", false);
            guest.setOnClickListener(v -> finish());
            col.addView(guest);
            TextView g = Ui.muted(this, "Without login, photos stay on this phone. Every photo still gets a verify link.", 12.5f);
            g.setGravity(Gravity.CENTER);
            g.setPadding(0, Util.dp(this, 8), 0, 0);
            col.addView(g);
        }

        tabs.addOnButtonCheckedListener((grp, id, checked) -> {
            if (!checked) return;
            signup = id == tSign.getId();
            loginBox.setVisibility(signup ? View.GONE : View.VISIBLE);
            signupBox.setVisibility(signup ? View.VISIBLE : View.GONE);
            title.setText(signup ? "Create your free account" : "Welcome back");
            sub.setText(signup ? "Takes 30 seconds. Works on assamconverter.com too." : "Log in with your assamconverter.com account to save your photos online and see them on any phone.");
            msg.setVisibility(View.GONE);
        });

        bLogin.setOnClickListener(v -> {
            String id = lId.getText().toString().trim(), pw = lPw.getText().toString();
            if (id.isEmpty()) { err("Enter your e-mail, mobile number or username."); return; }
            if (pw.isEmpty()) { err("Enter your password."); return; }
            send(bLogin, "login", obj("login", id, "password", pw));
        });
        bSign.setOnClickListener(v -> {
            String n = sName.getText().toString().trim(), e = sEmail.getText().toString().trim(), m = sMob.getText().toString().replaceAll("\\D", ""), pw = sPw.getText().toString();
            if (n.length() < 2) { err("Enter your full name."); return; }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { err("Enter a valid e-mail address."); return; }
            if (!m.isEmpty() && !m.matches("(91)?[6-9]\\d{9}")) { err("Enter a valid 10-digit mobile number, or leave it empty."); return; }
            if (pw.length() < 8) { err("Password must be at least 8 characters."); return; }
            send(bSign, "signup", obj("fullname", n, "email", e, "mobile", m, "password", pw));
        });
    }

    private MaterialButton tab(String t) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(t);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private static JSONObject obj(String... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i < kv.length; i += 2) o.put(kv[i], kv[i + 1]); } catch (Exception ignored) { }
        return o;
    }

    private void err(String s) {
        msg.setVisibility(View.VISIBLE);
        msg.setText(s);
        msg.setBackgroundColor(0x33D93A2F);
    }

    private void send(MaterialButton b, String action, JSONObject body) {
        b.setEnabled(false);
        CharSequence t = b.getText();
        b.setText("Please wait…");
        Api.call(this, action, body, r -> {
            b.setEnabled(true);
            b.setText(t);
            if (r.optBoolean("ok")) {
                Prefs p = new Prefs(this);
                p.setUser(r.optString("token"), r.optJSONObject("user"));
                int moved = r.optInt("claimed", 0);
                Toast.makeText(this, "Welcome, " + p.userName() + "!" + (moved > 0 ? " " + moved + (moved == 1 ? " photo" : " photos") + " from this phone moved to your account." : ""), Toast.LENGTH_LONG).show();
                Store.syncAll(this);
                finish();
            } else if ("use_website".equals(r.optString("error")) || "signup_closed".equals(r.optString("error"))) {
                err(r.optString("message") + " Tap here to open the sign-up page.");
                msg.setOnClickListener(v -> MainActivity.open(this, r.optString("signup_url").startsWith("http") ? r.optString("signup_url") : BuildConfig.BASE_URL + r.optString("signup_url", "/login.php")));
            } else {
                err(r.optString("message", "Something went wrong. Please try again."));
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (required) moveTaskToBack(true); else super.onBackPressed();
    }
}
