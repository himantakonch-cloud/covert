package com.assamconverter.geocam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.effects.Frame;
import androidx.camera.effects.OverlayEffect;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The camera: live GPS stamp, photo and video. This is the first screen of the app. */
public class MainActivity extends AppCompatActivity implements StampView.Source, SensorEventListener {

    private Prefs prefs;
    private Store store;
    private Places places;
    private PreviewView previewView;
    private StampView stampView;
    private TextView gps, note, rec, count, bTimer, zoomVal, mPhoto, mVideo;
    private ImageButton shutter, bGrid, bTorch, bFlip;
    private ImageView thumb;
    private LinearLayout perm, zoomRow;
    private SeekBar zoom;
    private View flash;

    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private OverlayEffect overlay;
    private boolean videoMode = false, videoStampOk = true, torchOn = false;
    private volatile boolean busy = false;
    private Recording recording;

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread overlayThread;
    private Handler overlayHandler;

    /* location & place */
    private LocationManager lm;
    private Location pos;
    private double addrLat = 999, addrLng = 999, mapLat = 999, mapLng = 999;
    private volatile Places.Addr addr;
    private volatile Bitmap mapBmp;
    private float heading = -1;
    private SensorManager sm;

    /* video stamp (drawn into every video frame) */
    private final Object stampLock = new Object();
    private Bitmap videoStamp;
    private String videoCode = "";
    private long recStart;

    private ActivityResultLauncher<String[]> askPerms;
    private ActivityResultLauncher<String> askMic;
    private Runnable afterMic;

    @Override
    protected void onCreate(Bundle saved) {
        Prefs.applyTheme(this);
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        store = new Store(this);
        places = new Places(this);

        previewView = findViewById(R.id.preview);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        stampView = findViewById(R.id.stamp);
        stampView.setSource(this);
        gps = findViewById(R.id.gps); note = findViewById(R.id.note); rec = findViewById(R.id.rec); count = findViewById(R.id.count);
        bTimer = findViewById(R.id.bTimer); zoomVal = findViewById(R.id.zoomVal); mPhoto = findViewById(R.id.mPhoto); mVideo = findViewById(R.id.mVideo);
        shutter = findViewById(R.id.shutter); bGrid = findViewById(R.id.bGrid); bTorch = findViewById(R.id.bTorch); bFlip = findViewById(R.id.bFlip);
        thumb = findViewById(R.id.thumb); perm = findViewById(R.id.perm); zoomRow = findViewById(R.id.zoomRow); zoom = findViewById(R.id.zoom); flash = findViewById(R.id.flash);

        askPerms = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> afterPermissions());
        askMic = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> { Runnable r = afterMic; afterMic = null; if (r != null) r.run(); });

        shutter.setOnClickListener(v -> onShutter());
        mPhoto.setOnClickListener(v -> setMode(false));
        mVideo.setOnClickListener(v -> setMode(true));
        bFlip.setOnClickListener(v -> { if (recording != null) return; prefs.put("backCam", !prefs.back()); bindCamera(); });
        bGrid.setOnClickListener(v -> { prefs.put("grid", !prefs.grid()); updButtons(); });
        bTimer.setOnClickListener(v -> {
            int t = prefs.timer() == 0 ? 3 : prefs.timer() == 3 ? 10 : 0;
            prefs.put("timer", t); updButtons();
            toast(t == 0 ? "Self-timer off" : "Self-timer " + t + " seconds");
        });
        bTorch.setOnClickListener(v -> { if (camera == null) return; torchOn = !torchOn; camera.getCameraControl().enableTorch(torchOn); updButtons(); });
        findViewById(R.id.bSet).setOnClickListener(v -> { if (recording == null) startActivity(new Intent(this, SettingsActivity.class)); });
        thumb.setOnClickListener(v -> { if (recording == null) startActivity(new Intent(this, ViewerActivity.class)); });
        note.setOnClickListener(v -> editNote());
        findViewById(R.id.permBtn).setOnClickListener(v -> {
            if (shouldAskAgain()) askPerms.launch(PERMS);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        });
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && camera != null) camera.getCameraControl().setLinearZoom(p / 100f); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        overlayThread = new HandlerThread("stamp");
        overlayThread.start();
        overlayHandler = new Handler(overlayThread.getLooper());
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        setModeUi(false);
        hello();
        if (hasAll()) afterPermissions(); else askPerms.launch(PERMS);
    }

    /* ───────────── permissions ───────────── */
    private static final String[] PERMS = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    private boolean has(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }
    private boolean hasAll() { return has(Manifest.permission.CAMERA) && (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)); }
    private boolean shouldAskAgain() {
        for (String p : PERMS) if (!has(p) && !shouldShowRequestPermissionRationale(p) && prefs.b("asked_" + p, false)) return false;
        return true;
    }

    private void afterPermissions() {
        for (String p : PERMS) prefs.put("asked_" + p, true);
        if (!has(Manifest.permission.CAMERA)) { showPerm("Allow the camera", "GeoTag Camera needs the camera to take photos and videos."); return; }
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) { showPerm("Allow your location", "The stamp needs your GPS location. Allow Location for GeoTag Camera."); return; }
        perm.setVisibility(View.GONE);
        if (provider == null) startCamera(); else if (camera == null) bindCamera();
        startLocation();
    }

    private void showPerm(String t, String s) {
        perm.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.permTitle)).setText(t);
        boolean again = shouldAskAgain();
        ((TextView) findViewById(R.id.permText)).setText(again ? s : s + "\n\nTap the button → Permissions → allow it, then come back.");
        ((TextView) findViewById(R.id.permBtn)).setText(again ? "Allow" : "Open settings");
    }

    /* ───────────── camera ───────────── */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try { provider = f.get(); bindCamera(); }
            catch (Exception e) { toast("Camera could not start"); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (provider == null || !hasAll()) return;
        provider.unbindAll();
        camera = null; torchOn = false;
        CameraSelector sel = prefs.back() ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        try { if (!provider.hasCamera(sel)) sel = prefs.back() ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA; } catch (Exception ignored) { }
        ResolutionSelector rs = new ResolutionSelector.Builder().setAspectRatioStrategy(videoMode
                ? AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY : AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build();
        Preview preview = new Preview.Builder().setResolutionSelector(rs).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        stampView.setAspect(videoMode ? 9f / 16f : 3f / 4f);
        try {
            if (!videoMode) {
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(prefs.hiQuality() ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(rs).build();
                camera = provider.bindToLifecycle(this, sel, preview, imageCapture);
            } else {
                Recorder recorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(prefs.video1080() ? Quality.FHD : Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build();
                videoCapture = VideoCapture.withOutput(recorder);
                try {
                    makeOverlay();
                    UseCaseGroup g = new UseCaseGroup.Builder().addUseCase(preview).addUseCase(videoCapture).addEffect(overlay).build();
                    camera = provider.bindToLifecycle(this, sel, g);
                    videoStampOk = true;
                } catch (Exception e) {
                    // this phone can't draw on videos → record without the burned-in stamp
                    provider.unbindAll();
                    camera = provider.bindToLifecycle(this, sel, preview, videoCapture);
                    videoStampOk = false;
                }
            }
        } catch (Exception e) {
            toast("Camera could not start: " + e.getMessage());
            return;
        }
        setupZoomAndTorch();
    }

    private void setupZoomAndTorch() {
        if (camera == null) return;
        bTorch.setVisibility(camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
        camera.getCameraInfo().getZoomState().observe(this, (ZoomState z) -> {
            boolean can = z.getMaxZoomRatio() > z.getMinZoomRatio() + 0.1f;
            zoomRow.setVisibility(can ? View.VISIBLE : View.GONE);
            zoom.setProgress(Math.round(z.getLinearZoom() * 100));
            zoomVal.setText(String.format(Locale.US, "%.1f×", z.getZoomRatio()));
        });
        updButtons();
    }

    /** Draws the stamp into every video frame (in the video's own orientation). */
    private void makeOverlay() {
        if (overlay != null) overlay.close();
        overlay = new OverlayEffect(CameraEffect.VIDEO_CAPTURE, 0, overlayHandler, t -> { });
        overlay.setOnDrawListener((Frame frame) -> {
            Canvas c = frame.getOverlayCanvas();
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Bitmap st;
            synchronized (stampLock) { st = videoStamp; }
            if (st == null || recording == null || st.isRecycled()) return true;
            Rect cr = frame.getCropRect();
            int rot = frame.getRotationDegrees();
            boolean side = rot == 90 || rot == 270;
            float W = side ? cr.height() : cr.width(), H = side ? cr.width() : cr.height();
            c.save();
            if (rot == 90) { c.translate(cr.left, cr.bottom); c.rotate(-90); }
            else if (rot == 270) { c.translate(cr.right, cr.top); c.rotate(90); }
            else if (rot == 180) { c.translate(cr.right, cr.bottom); c.rotate(180); }
            else c.translate(cr.left, cr.top);
            if (frame.isMirroring()) { c.translate(W, 0); c.scale(-1, 1); }
            c.drawBitmap(st, null, new RectF(0, 0, W, H), null);
            c.restore();
            return true;
        });
    }

    /** Re-draw the video stamp once a second while recording (time and position change). */
    private final Runnable stampTick = new Runnable() {
        @Override public void run() {
            if (recording == null) return;
            renderVideoStamp();
            long s = (System.currentTimeMillis() - recStart) / 1000;
            int max = Math.max(10, prefs.maxVideo());
            rec.setText(String.format(Locale.US, "● %02d:%02d / %02d:%02d", s / 60, s % 60, max / 60, max % 60));
            if (s >= max) { stopRecording(); return; }
            ui.postDelayed(this, 1000);
        }
    };

    private void renderVideoStamp() {
        if (pos == null) return;
        // the recorded picture in portrait (e.g. 720×1280)
        int w = prefs.video1080() ? 1080 : 720, h = prefs.video1080() ? 1920 : 1280;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Stamp.draw(new Canvas(b), w, h, info(videoCode, System.currentTimeMillis()), opts());
        final Bitmap old;
        synchronized (stampLock) { old = videoStamp; videoStamp = b; }
        if (old != null) ui.postDelayed(() -> { synchronized (stampLock) { if (old != videoStamp) old.recycle(); } }, 3000);
    }

    /* ───────────── modes, buttons ───────────── */
    private void setModeUi(boolean video) {
        videoMode = video;
        mPhoto.setTextColor(video ? 0xFFAAA196 : 0xFF000000);
        mVideo.setTextColor(video ? 0xFF000000 : 0xFFAAA196);
        mPhoto.setBackgroundResource(video ? 0 : R.drawable.pill);
        mVideo.setBackgroundResource(video ? R.drawable.pill : 0);
        if (video) mVideo.getBackground().mutate().setTint(0xFFFFB020); else mPhoto.getBackground().mutate().setTint(0xFFFFB020);
        shutter.setBackgroundResource(video ? R.drawable.shutter_video : R.drawable.shutter);
        shutter.setContentDescription(video ? "Start video" : "Take photo");
    }

    private void setMode(boolean video) {
        if (recording != null || video == videoMode) return;
        setModeUi(video);
        bindCamera();
    }

    private void updButtons() {
        bTimer.setText(prefs.timer() == 0 ? "⏱ Off" : "⏱ " + prefs.timer() + "s");
        bTimer.getBackground().mutate().setTint(prefs.timer() == 0 ? 0x99000000 : 0xFFFFB020);
        bTimer.setTextColor(prefs.timer() == 0 ? Color.WHITE : Color.BLACK);
        bGrid.setBackgroundResource(prefs.grid() ? R.drawable.round_on : R.drawable.round_dark);
        bGrid.setColorFilter(prefs.grid() ? Color.BLACK : Color.WHITE);
        bTorch.setBackgroundResource(torchOn ? R.drawable.round_on : R.drawable.round_dark);
        bTorch.setColorFilter(torchOn ? Color.BLACK : Color.WHITE);
        note.setText(prefs.note().isEmpty() ? "✎ Add note" : "✎ " + prefs.note());
        stampView.invalidate();
    }

    private void editNote() {
        EditText e = new EditText(this);
        e.setText(prefs.note());
        e.setHint("e.g. Ward 4 road work — before");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(true);
        FrameLayout box = new FrameLayout(this);
        int p = Util.dp(this, 20);
        box.setPadding(p, Util.dp(this, 8), p, 0);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle("Note on the photos").setView(box)
                .setPositiveButton("Done", (d, w) -> { prefs.put("note", e.getText().toString().trim()); updButtons(); })
                .setNeutralButton("Clear", (d, w) -> { prefs.put("note", ""); updButtons(); })
                .setNegativeButton("Cancel", null).show();
    }

    /* ───────────── what goes on the stamp ───────────── */
    @Override public Stamp.Opts opts() {
        Stamp.Opts o = new Stamp.Opts();
        o.coord = prefs.coord(); o.top = prefs.top(); o.size = prefs.size(); o.showMap = prefs.map();
        return o;
    }
    @Override public boolean grid() { return prefs.grid(); }
    @Override public Stamp.Info info() { return pos == null ? null : info("••••••••", System.currentTimeMillis()); }

    private Stamp.Info info(String code, long time) {
        Stamp.Info i = new Stamp.Info();
        i.lat = pos.getLatitude(); i.lng = pos.getLongitude(); i.acc = pos.hasAccuracy() ? pos.getAccuracy() : 0;
        Places.Addr a = addr;
        if (prefs.addr() && a != null) { i.line1 = a.line1; i.line2 = a.line2; }
        i.plus = Util.plusCode(i.lat, i.lng);
        i.time = time; i.offset = Util.offsetMin();
        i.note = prefs.note(); i.entryNo = prefs.entryNo(); i.code = code;
        i.map = prefs.map() ? mapBmp : null;
        if (prefs.extra()) {
            List<String> x = new ArrayList<>();
            if (pos.hasAltitude()) x.add("Altitude " + Math.round(pos.getAltitude()) + " m");
            if (heading >= 0) x.add("Facing " + Util.dir(heading) + " " + Math.round(heading) + "°");
            i.extra = android.text.TextUtils.join("  ·  ", x);
        }
        return i;
    }

    /* ───────────── GPS + compass ───────────── */
    private final LocationListener locL = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location l) { onLocation(l); }
        @Override public void onProviderEnabled(@NonNull String p) { }
        @Override public void onProviderDisabled(@NonNull String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
    };
    private boolean locStarted = false;

    @SuppressLint("MissingPermission")
    private void startLocation() {
        if (lm == null || locStarted) return;
        locStarted = true;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.getAllProviders().contains(p)) continue;
                Location last = lm.getLastKnownLocation(p);
                if (last != null && System.currentTimeMillis() - last.getTime() < 120000) onLocation(last);
                lm.requestLocationUpdates(p, 1000, 0, locL, Looper.getMainLooper());
            }
        } catch (Exception ignored) { }
        boolean on = false;
        try { on = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) { }
        if (!on && pos == null) {
            setGps(0xFFFF5A4F, "Location is off — tap to turn on");
            gps.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        }
    }

    private void onLocation(Location l) {
        // keep the better fix: newer, or prefer GPS over network for a short while
        if (pos != null && l.getTime() < pos.getTime() - 10000) return;
        if (pos != null && LocationManager.NETWORK_PROVIDER.equals(l.getProvider()) && LocationManager.GPS_PROVIDER.equals(pos.getProvider())
                && System.currentTimeMillis() - pos.getTime() < 15000) return;
        pos = l;
        int a = Math.round(l.getAccuracy());
        setGps(a <= 30 ? 0xFF3CCF8E : a <= 100 ? 0xFFFFB020 : 0xFFFF5A4F, "GPS ±" + a + " m");
        gps.setOnClickListener(null);
        final double lat = l.getLatitude(), lng = l.getLongitude();
        if (prefs.addr() && Util.distM(lat, lng, addrLat, addrLng) > 35) {
            addrLat = lat; addrLng = lng;
            Api.NET.execute(() -> { Places.Addr x = places.lookup(lat, lng); ui.post(() -> { addr = x; stampView.invalidate(); }); });
        }
        if (prefs.map() && Util.distM(lat, lng, mapLat, mapLng) > 25) {
            mapLat = lat; mapLng = lng;
            Api.NET.execute(() -> { Bitmap m = Places.map(lat, lng, 512); if (m != null) ui.post(() -> { mapBmp = m; stampView.invalidate(); }); });
        }
        stampView.invalidate();
    }

    private void setGps(int dot, String text) {
        android.text.SpannableString s = new android.text.SpannableString("●  " + text);
        s.setSpan(new android.text.style.ForegroundColorSpan(dot), 0, 1, 0);
        gps.setText(s);
    }

    private final float[] rotM = new float[9], remap = new float[9], ori = new float[3];
    @Override public void onSensorChanged(SensorEvent e) {
        SensorManager.getRotationMatrixFromVector(rotM, e.values);
        // phone held upright, looking through the camera
        SensorManager.remapCoordinateSystem(rotM, SensorManager.AXIS_X, SensorManager.AXIS_Z, remap);
        SensorManager.getOrientation(remap, ori);
        float h = (float) ((Math.toDegrees(ori[0]) + 360) % 360);
        if (heading < 0 || Math.abs(h - heading) > 2) heading = h;
    }
    @Override public void onAccuracyChanged(Sensor s, int a) { }

    /* ───────────── shutter ───────────── */
    private void onShutter() {
        if (perm.getVisibility() == View.VISIBLE) return;
        if (recording != null) { stopRecording(); return; }
        if (pos == null) { toast("Waiting for GPS… go outside or near a window"); return; }
        Runnable go = videoMode ? this::startRecording : this::takePhoto;
        int t = prefs.timer();
        if (t == 0) { go.run(); return; }
        if (count.getVisibility() == View.VISIBLE) return;
        count.setVisibility(View.VISIBLE);
        final int[] left = {t};
        count.setText(String.valueOf(left[0]));
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                left[0]--;
                if (left[0] <= 0) { count.setVisibility(View.GONE); go.run(); }
                else { count.setText(String.valueOf(left[0])); ui.postDelayed(this, 1000); }
            }
        }, 1000);
    }

    private void flashScreen() {
        flash.setAlpha(0.85f);
        flash.animate().alpha(0f).setDuration(260).start();
    }

    private void takePhoto() {
        if (busy || imageCapture == null || pos == null) return;
        busy = true;
        flashScreen();
        final Location p0 = new Location(pos);
        final long time = System.currentTimeMillis();
        final String code = Util.newCode(), entryNo = prefs.entryNo();
        final Stamp.Info inf = info(code, time);
        final Stamp.Opts op = opts();
        final float head = heading;
        final boolean front = !prefs.back();
        imageCapture.takePicture(work, new ImageCapture.OnImageCapturedCallback() {
            @Override public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    Bitmap raw = image.toBitmap();
                    int rot = image.getImageInfo().getRotationDegrees();
                    image.close();
                    Bitmap bmp = raw;
                    if (rot != 0 || front) {
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        if (front) m.postScale(-1, 1);
                        bmp = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
                        if (bmp != raw) raw.recycle();
                    }
                    if (!prefs.hiQuality() && Math.max(bmp.getWidth(), bmp.getHeight()) > 2560) {
                        float s = 2560f / Math.max(bmp.getWidth(), bmp.getHeight());
                        Bitmap small = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * s), Math.round(bmp.getHeight() * s), true);
                        if (small != bmp) bmp.recycle();
                        bmp = small;
                    }
                    if (!bmp.isMutable()) { Bitmap mu = bmp.copy(Bitmap.Config.ARGB_8888, true); bmp.recycle(); bmp = mu; }
                    Stamp.draw(new Canvas(bmp), bmp.getWidth(), bmp.getHeight(), inf, op);
                    Store.Entry e = new Store.Entry();
                    e.code = code; e.kind = "photo"; e.mime = "image/jpeg"; e.entryNo = entryNo;
                    File f = new File(store.mediaDir(), e.fileName());
                    try (FileOutputStream o = new FileOutputStream(f)) { bmp.compress(Bitmap.CompressFormat.JPEG, prefs.hiQuality() ? 94 : 86, o); }
                    writeExif(f, p0, time, code, head);
                    e.w = bmp.getWidth(); e.h = bmp.getHeight();
                    store.makeThumb(code, bmp);
                    bmp.recycle();
                    finishEntry(e, f, p0, time, inf, head, 0);
                } catch (Throwable t) {
                    ui.post(() -> toast("Could not save the photo: " + t.getMessage()));
                } finally {
                    busy = false;
                }
            }
            @Override public void onError(@NonNull ImageCaptureException ex) { busy = false; ui.post(() -> toast("Could not take the photo")); }
        });
    }

    private void writeExif(File f, Location p, long time, String code, float head) {
        try {
            ExifInterface x = new ExifInterface(f.getPath());
            x.setLatLong(p.getLatitude(), p.getLongitude());
            if (p.hasAltitude()) x.setAltitude(p.getAltitude());
            if (p.hasAccuracy()) x.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, Math.round(p.getAccuracy() * 10) + "/10");
            String t = Util.exifTime(time, Util.offsetMin());
            int off = Util.offsetMin(), o = Math.abs(off);
            String offs = String.format(Locale.US, "%s%02d:%02d", off >= 0 ? "+" : "-", o / 60, o % 60);
            x.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, t);
            x.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, t);
            x.setAttribute(ExifInterface.TAG_DATETIME, t);
            x.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offs);
            x.setAttribute(ExifInterface.TAG_OFFSET_TIME, offs);
            x.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoTag Camera " + BuildConfig.VERSION_NAME + " - assamconverter.com");
            x.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Entry " + code);
            x.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
            x.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            x.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            if (head >= 0) { x.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M"); x.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, Math.round(head * 100) + "/100"); }
            x.saveAttributes();
        } catch (Exception ignored) { }
    }

    /** Hash, put in the Gallery, add to the log, sync. Runs in the background. */
    private void finishEntry(Store.Entry e, File f, Location p, long time, Stamp.Info inf, float head, double dur) {
        e.lat = p.getLatitude(); e.lng = p.getLongitude(); e.acc = p.hasAccuracy() ? p.getAccuracy() : 0;
        e.alt = p.hasAltitude() ? p.getAltitude() : Double.NaN; e.heading = head; e.taken = time; e.offset = Util.offsetMin();
        e.place = inf.line1; e.address = inf.line2; e.note = prefs.note(); e.dur = dur;
        try { e.sha256 = Util.sha256(new FileInputStream(f)); } catch (Exception ignored) { }
        e.uri = store.toGallery(f, e.fileName(), e.mime);
        if (Build.VERSION.SDK_INT >= 29 && !e.uri.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            e.file = "";
        } else e.file = f.getPath();
        store.put(e);
        prefs.put("next", prefs.next() + 1);
        Store.syncAll(this);
        final boolean ok = !e.uri.isEmpty();
        ui.post(() -> {
            updThumb();
            updButtons();
            toast((e.video() ? "Video" : "Photo") + (ok ? " saved to Gallery · No. " : " saved · No. ") + e.entryNo);
        });
    }

    /* ───────────── video ───────────── */
    private void startRecording() {
        if (videoCapture == null) return;
        if (prefs.sound() && !has(Manifest.permission.RECORD_AUDIO) && !prefs.b("micAsked", false)) {
            prefs.put("micAsked", true);
            afterMic = this::startRecording;
            askMic.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        if (!videoStampOk && !prefs.b("noStampWarned", false)) {
            prefs.put("noStampWarned", true);
            toast("This phone can't add the stamp inside videos — the location is still saved in the log");
        }
        videoCode = Util.newCode();
        final String code = videoCode, entryNo = prefs.entryNo();
        final Location p0 = new Location(pos);
        final long t0 = System.currentTimeMillis();
        final Stamp.Info inf0 = info(code, t0);
        final float head = heading;
        final Store.Entry e = new Store.Entry();
        e.code = code; e.kind = "video"; e.mime = "video/mp4"; e.entryNo = entryNo;
        final File f = new File(store.mediaDir(), e.fileName());
        recStart = t0;
        renderVideoStamp();
        PendingRecording pr = videoCapture.getOutput().prepareRecording(this, new FileOutputOptions.Builder(f).build());
        if (prefs.sound() && has(Manifest.permission.RECORD_AUDIO)) {
            try { pr = pr.withAudioEnabled(); } catch (SecurityException ignored) { }
        }
        recording = pr.start(ContextCompat.getMainExecutor(this), ev -> {
            if (!(ev instanceof VideoRecordEvent.Finalize)) return;
            VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) ev;
            recording = null;
            ui.removeCallbacks(stampTick);
            rec.setVisibility(View.GONE);
            shutter.setBackgroundResource(R.drawable.shutter_video);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            stampView.setHideStamp(false);
            if (fin.hasError() && (!f.exists() || f.length() == 0)) { toast("Video could not be saved"); return; }
            final double dur = (System.currentTimeMillis() - t0) / 1000.0;
            work.execute(() -> {
                try {
                    MediaMetadataRetriever mr = new MediaMetadataRetriever();
                    mr.setDataSource(f.getPath());
                    Bitmap fr = mr.getFrameAtTime(500000);
                    mr.release();
                    if (fr != null) { store.makeThumb(code, fr); e.w = fr.getWidth(); e.h = fr.getHeight(); fr.recycle(); }
                } catch (Exception ignored) { }
                finishEntry(e, f, p0, t0, inf0, head, dur);
            });
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stampView.setHideStamp(videoStampOk);
        rec.setVisibility(View.VISIBLE);
        shutter.setBackgroundResource(R.drawable.shutter_rec);
        ui.post(stampTick);
    }

    private void stopRecording() {
        if (recording != null) recording.stop();
    }

    /* ───────────── website: version check, protection ───────────── */
    private void hello() {
        Api.call(this, "hello", null, r -> {
            if (isFinishing()) return;
            if ("blocked_source".equals(r.optString("error"))) {
                new AlertDialog.Builder(this).setCancelable(false).setTitle("Unofficial copy").setMessage(r.optString("message"))
                        .setPositiveButton("Get the official app", (d, w) -> { open(this, prefs.apkUrl()); finish(); }).show();
                return;
            }
            if (!r.optBoolean("ok")) return;
            JSONObject cfg = r.optJSONObject("cfg");
            if (cfg != null) {
                prefs.put("requireLogin", cfg.optBoolean("require_login"));
                prefs.put("maxVideo", cfg.optInt("max_video_sec", 180));
                JSONObject a = cfg.optJSONObject("app");
                if (a != null) {
                    prefs.put("latest", a.optString("latest")); prefs.put("minVersion", a.optString("min"));
                    prefs.put("apkUrl", a.optString("apk_url")); prefs.put("updateNote", a.optString("note"));
                }
            }
            if (r.optJSONObject("user") == null && prefs.loggedIn()) prefs.logout();
            checkUpdate(this, false);
            if (prefs.requireLogin() && !prefs.loggedIn()) startActivity(new Intent(this, LoginActivity.class).putExtra("required", true));
        });
    }

    /** Shows "Update available" (or "Please update") when the website has a newer APK. */
    static boolean checkUpdate(AppCompatActivity a, boolean userAsked) {
        Prefs p = new Prefs(a);
        String me = BuildConfig.VERSION_NAME;
        if (!p.minVersion().isEmpty() && Util.cmpVersion(me, p.minVersion()) < 0) {
            new AlertDialog.Builder(a).setCancelable(false).setTitle("Please update GeoTag Camera")
                    .setMessage((p.updateNote().isEmpty() ? "A new version is available." : p.updateNote()) + "\n\nYour version: " + me + "\nNew version: " + p.latest())
                    .setPositiveButton("Download update", (d, w) -> { open(a, p.apkUrl()); a.finish(); }).show();
            return true;
        }
        boolean newer = !p.latest().isEmpty() && Util.cmpVersion(me, p.latest()) < 0;
        long last;
        try { last = Long.parseLong(p.s("updAsked", "0")); } catch (Exception e) { last = 0; }
        if (newer && (userAsked || System.currentTimeMillis() - last > 86400000L)) {
            p.put("updAsked", String.valueOf(System.currentTimeMillis()));
            new AlertDialog.Builder(a).setTitle("Update available")
                    .setMessage((p.updateNote().isEmpty() ? "A new version of GeoTag Camera is available." : p.updateNote()) + "\n\nVersion " + p.latest() + " (you have " + me + ")")
                    .setPositiveButton("Download", (d, w) -> open(a, p.apkUrl())).setNegativeButton("Later", null).show();
        } else if (userAsked && !newer) {
            Toast.makeText(a, "You have the latest version (" + me + ")", Toast.LENGTH_SHORT).show();
        }
        return newer;
    }

    static void open(android.content.Context c, String url) {
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(c, "No browser found", Toast.LENGTH_SHORT).show(); }
    }

    /* ───────────── misc ───────────── */
    private void updThumb() {
        work.execute(() -> {
            List<Store.Entry> l = store.all();
            Bitmap b = l.isEmpty() ? null : store.loadThumb(l.get(0).code);
            ui.post(() -> { if (b != null) thumb.setImageBitmap(b); else thumb.setImageDrawable(null); });
        });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private final Runnable clockTick = new Runnable() { @Override public void run() { stampView.invalidate(); ui.postDelayed(this, 15000); } };

    @Override protected void onResume() {
        super.onResume();
        updButtons();
        updThumb();
        if (hasAll() && perm.getVisibility() == View.VISIBLE) afterPermissions();
        Sensor rv = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
        if (hasAll() && provider != null && camera == null) bindCamera();
        ui.removeCallbacks(clockTick);
        ui.postDelayed(clockTick, 15000);
        Store.syncAll(this);
    }

    @Override protected void onPause() {
        if (recording != null) stopRecording();
        if (sm != null) sm.unregisterListener(this);
        ui.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        try { if (lm != null) lm.removeUpdates(locL); } catch (Exception ignored) { }
        if (overlay != null) overlay.close();
        if (overlayThread != null) overlayThread.quitSafely();
        work.shutdown();
        super.onDestroy();
    }
}
