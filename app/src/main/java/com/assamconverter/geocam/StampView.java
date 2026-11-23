package com.assamconverter.geocam;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/** Shows the live stamp (and grid) on top of the camera preview, exactly where it will be on the photo. */
public class StampView extends View {
    interface Source { Stamp.Info info(); Stamp.Opts opts(); boolean grid(); }

    private Source src;
    private float aspect = 3f / 4f;   // width / height of what the camera records (portrait)
    private boolean hideStamp = false;

    public StampView(Context c) { super(c); }
    public StampView(Context c, AttributeSet a) { super(c, a); }

    void setSource(Source s) { src = s; invalidate(); }
    void setAspect(float wOverH) { aspect = wOverH; invalidate(); }
    void setHideStamp(boolean h) { hideStamp = h; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        if (src == null) return;
        float vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
        // the preview is shown "fit centre": find the picture area inside the view
        float w = vw, h = vw / aspect;
        if (h > vh) { h = vh; w = vh * aspect; }
        float x = (vw - w) / 2, y = (vh - h) / 2;
        c.save();
        c.translate(x, y);
        if (src.grid()) Stamp.grid(c, w, h);
        Stamp.Info in = src.info();
        if (in != null && !hideStamp) Stamp.draw(c, w, h, in, src.opts());
        c.restore();
    }
}
