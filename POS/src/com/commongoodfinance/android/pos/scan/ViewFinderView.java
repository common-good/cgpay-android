/* $Id: $
 */
package com.commongoodfinance.android.pos.scan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class ViewFinderView extends View {
    private static final float LINE_LEN = 50.0F;
    private static final float LINE_WIDTH = 3.0F;

    private final Paint paint;
    private RectF viewFinderRect;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public ViewFinderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        paint = createPaint();
   }

    /**
     * @param context
     * @param attrs
     */
    public ViewFinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = createPaint();
    }

    /**
     * @param context
     */
    public ViewFinderView(Context context) {
        super(context);
        paint = createPaint();
    }

    /**
     * @see android.view.View#onDraw(android.graphics.Canvas)
     */
    @Override
    public void onDraw(Canvas canvas) {
        if (null == viewFinderRect) {
            viewFinderRect = getViewFinderRect(canvas.getClipBounds());
        }

        canvas.drawLine(
            viewFinderRect.left,
            viewFinderRect.top,
            viewFinderRect.left + LINE_LEN,
            viewFinderRect.top,
            paint);
        canvas.drawLine(
            viewFinderRect.left,
            viewFinderRect.top,
            viewFinderRect.left,
            viewFinderRect.top + LINE_LEN,
            paint);

        canvas.drawLine(
            viewFinderRect.right - LINE_LEN,
            viewFinderRect.top,
            viewFinderRect.right,
            viewFinderRect.top,
            paint);
        canvas.drawLine(
            viewFinderRect.right,
            viewFinderRect.top,
            viewFinderRect.right,
            viewFinderRect.top + LINE_LEN,
            paint);

        canvas.drawLine(
            viewFinderRect.left,
            viewFinderRect.bottom - LINE_LEN,
            viewFinderRect.left,
            viewFinderRect.bottom,
            paint);
        canvas.drawLine(
            viewFinderRect.left,
            viewFinderRect.bottom,
            viewFinderRect.left + LINE_LEN,
            viewFinderRect.bottom,
            paint);

        canvas.drawLine(
            viewFinderRect.right - LINE_LEN,
            viewFinderRect.bottom,
            viewFinderRect.right,
            viewFinderRect.bottom,
            paint);
        canvas.drawLine(
            viewFinderRect.right,
            viewFinderRect.bottom - LINE_LEN,
            viewFinderRect.right,
            viewFinderRect.bottom,
            paint);
    }

    private Paint createPaint() {
        Paint pnt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pnt.setColor(Color.RED);
        pnt.setStrokeWidth(LINE_WIDTH);
        return pnt;
    }

    /**
     * @return the view finder rect
     */
    private RectF getViewFinderRect(Rect r) {
        int w = r.right - r.left;
        int h = r.bottom - r.top;
        float dw = (w - ((w * 3) / 4)) / 2;
        float dh = (h - ((h * 3) / 4)) / 2;
        return new RectF(r.left + dw, r.top + dh, r.right - dw, r.bottom - dh);
    }
}
