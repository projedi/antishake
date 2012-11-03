package me.projedi.antishake;

import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;

public class RectangleView extends View {
   private static final String TAG = "Antishake";
   private Paint m_paint;
   private Paint m_paint1;
   private RectF m_rect;
   private RectF m_rect1;
   int w;
   int h;
   float dx;
   float dy;
   float alpha;
   float ax;
   float ay;

   public RectangleView(Context context, AttributeSet attrs) {
      super(context,attrs);
      m_paint = new Paint();
      m_paint.setStyle(Paint.Style.FILL);
      m_paint.setColor(0xffff0000);
      m_paint1 = new Paint();
      m_paint1.setStyle(Paint.Style.FILL);
      m_paint1.setColor(0xff00ff00);
      w = 0;
      h = 0;
      dx = 0;
      dy = 0;
      alpha = 0;
      ax = 1;
      ay = 1;
      m_rect = new RectF(0,0,0,0);
      m_rect1 = new RectF(0,0,0,0);
   }

   public void applyXCoefficient(float x) {
      ax = x;
      recalculateRect();
   }
   
   public void applyYCoefficient(float y) {
      ay = y;
      recalculateRect();
   }

   public void applyShift(float dx, float dy, float alpha) {
      this.dx = dx;
      this.dy = dy;
      this.alpha = alpha;
      recalculateRect();
   }

   @Override
   protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      this.w = w;
      this.h = h;
      recalculateRect();
   }

   private void recalculateRect() {
      //Log.d(TAG, "Recalculating rectangle");
      float left = w/4 + ax*dx;
      float top = h/4 + ay*dy;
      m_rect.set(left,top,left+w/2,top+h/2);
      m_rect1.set(w/4,h/4,3*w/4,3*h/4);
      invalidate();
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      //Log.d(TAG, "drawing");
      canvas.drawRect(m_rect1, m_paint1);
      canvas.drawRect(m_rect, m_paint);
   }
}
