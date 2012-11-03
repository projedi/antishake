package me.projedi.antishake;

import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import java.util.Timer;
import java.util.TimerTask;
import android.graphics.Path;
import android.graphics.Matrix;

public class RectangleView extends View {
   private static final String TAG = "Antishake";
   private Paint m_paintMoving;
   private Paint m_paintStatic;
   private Path m_pathStatic;
   private Path m_pathMoving;
   float[] m_transform;
   float m_translateCoefficient;
   float m_rotateCoefficient;

   private TimerTask m_timerTask = new TimerTask() {
      @Override
      public void run() {
         recalculateRect();
         postInvalidate();
      }
   };

   public RectangleView(Context context, AttributeSet attrs) {
      super(context,attrs);
      m_paintMoving = new Paint();
      m_paintMoving.setStyle(Paint.Style.FILL);
      m_paintMoving.setColor(0xffff0000);
      m_paintStatic = new Paint();
      m_paintStatic.setStyle(Paint.Style.FILL);
      m_paintStatic.setColor(0xff00ff00);
      m_transform = new float[3];
      m_translateCoefficient = 1;
      m_rotateCoefficient = 1;
      m_pathMoving = new Path();
      m_pathStatic = new Path();
      m_pathMoving.addRect(new RectF(0,0,0,0), Path.Direction.CW);
      m_pathMoving.close();
      Timer timer = new Timer();
      timer.schedule(m_timerTask,0,30);
   }

   public void setTranslateCoefficient(float a) {
      m_translateCoefficient = a;
   }

   public void setRotateCoefficient(float a) {
      m_rotateCoefficient = a;
   }

   public void setTransform(float[] transform) {
      m_transform = transform;
   }

   private void recalculateRect() {
      float w = getWidth();
      float h = getHeight();
      m_pathStatic = new Path();
      m_pathStatic.addRect(new RectF(w/4,h/4,3*w/4,3*h/4), Path.Direction.CW);
      m_pathStatic.close();
      Matrix mat = new Matrix();
      mat.setRotate(m_transform[2]*m_rotateCoefficient,w/2,h/2);
      mat.postTranslate( m_transform[0]*m_translateCoefficient
                       , m_transform[1]*m_translateCoefficient);
      m_pathStatic.transform(mat,m_pathMoving);
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      //Log.d(TAG, "drawing");
      canvas.drawPath(m_pathStatic, m_paintStatic);
      canvas.drawPath(m_pathMoving, m_paintMoving);
   }
}
