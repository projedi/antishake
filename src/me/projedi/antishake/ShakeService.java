package me.projedi.antishake;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.Timer;
import java.util.TimerTask;

import jama.*;
import jkalman.*;

// TODO: Don't recreate stuff in onCreate

// TODO: Use intents for setting params

// TODO: Use 
/**
 * Service calculates a transformation to minimize shaking of a view
 *
 * Uses intent "me.projedi.antishake.ACTION_SHAKE" with field "transform"
 * which is float[3] where transform[0] - x translation, transform[1] - y translation,
 * transform[2] - rotatation.
 */
public class ShakeService extends Service {
   @Override
   public IBinder onBind(Intent intent) {
      Log.d(TAG, "service binded");
      return mBinder;
   }

   @Override
   public void onCreate() {
      Log.d(TAG, "service created");
      mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      // TODO: It uses API Level 9 sensor. Implement a fallback to pure accelerometer.
      mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
      mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
      mSensorManager.registerListener(mSensorListener, mAccelerometer, mSensorAccuracy);
      mSensorManager.registerListener(mSensorListener, mGyroscope, mSensorAccuracy);
      mTransform = new float[] {0, 0, 0};
      mBroadcastTimer = new Timer();
      initKalmanPosition();
      initKalmanRotation();
      DisplayMetrics metrics = new DisplayMetrics();
      ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
      mMetersToPixelsX = metrics.xdpi * 39.3701;
      mMetersToPixelsY = metrics.ydpi * 39.3701;
      mBroadcastTimer.schedule(mBroadcastTask, 0, mTransferRate);
   }

   @Override
   public void onDestroy() {
      Log.d(TAG, "service destroyed");
      mSensorManager.unregisterListener(mSensorListener);
      mBroadcastTimer.cancel();
   }

   private void updatePosition(double ax, double ay, double dt) {
      double vx = ax*dt;
      double vy = ay*dt;
      double[][] trans = { {1, 0, dt, 0}
                         , {0, 1, 0,  dt}
                         , {0, 0, 1,  0}
                         , {0, 0, 0,  1} };
      mKalmanPosition.setTransition_matrix(new Matrix(trans));
      mKalmanPosition.Predict();
      Matrix newState = mKalmanPosition.Correct(new Matrix(new double[] {0, 0, vx, vy}, 4));
      mTransform[0] = (float)(-newState.get(0,0) * mMetersToPixelsX);
      mTransform[1] = (float)(-newState.get(1,0) * mMetersToPixelsY);
   }

   private void updateRotation(double vphi, double dt) {
      double phi = mKalmanRotation.getState_post().get(0,0);
      double a = Math.abs(phi);
      double[][] trans = { {1, dt}, {a, 1} };
      //double[][] trans = { {1, dt}, {0, 1} };
      mKalmanRotation.setTransition_matrix(new Matrix(trans));
      Matrix predictedState = mKalmanRotation.Predict();
      Matrix newState = mKalmanRotation.Correct(new Matrix(new double[] { 0, vphi }, 2));
      mTransform[2] = (float)(-newState.get(0,0) * mRadiansToDegrees);
   }

   private void initKalmanPosition() {
      try {
         // In order to stay in a 0-neighbourhood it measures position as (0,0)
         mKalmanPosition = new JKalman(4, 4);
      } catch(Exception e) { }
      mKalmanPosition.setMeasurement_matrix(Matrix.identity(4, 4));
      double p = 1e-3;
      double dp = 1e-3;
      double v = 1e-3;
      double[][] processNoise = { {p, 0, dp, 0}
                                , {0, p, 0,  dp}
                                , {0, 0, v,  0}
                                , {0, 0, 0,  v} };
      mKalmanPosition.setProcess_noise_cov(new Matrix(processNoise));
      double mp = 1e-1;
      double mv = 1e-3;
      double[][] measureNoise = { {mp, 0, 0, 0}
                                , {0, mp, 0, 0}
                                , {0, 0, mv, 0}
                                , {0, 0, 0, mv} };
      mKalmanPosition.setMeasurement_noise_cov(new Matrix(measureNoise));
      mKalmanPosition.setState_post(new Matrix(new double[] {0, 0, 0, 0}, 4));
   }

   private void initKalmanRotation() {
      try {
         // In order to stay in a 0-neighbourhood it measures rotation as 0
         mKalmanRotation = new JKalman(2, 2);
      } catch(Exception e) { }
      double[][] measure = { {0, 0}, {0, 1} };
      mKalmanRotation.setMeasurement_matrix((new Matrix(measure)).transpose());
      double p = 1e-3;
      double dp = 1e-3;
      double v = 1e-1;
      double[][] processNoise = { {p, dp}
                                , {0, v} };
      mKalmanRotation.setProcess_noise_cov(new Matrix(processNoise));
      double mp = 1e-3;
      double mv = 1e-5;
      double[][] measureNoise = { {mp, 0}, {0, mv} };
      mKalmanRotation.setMeasurement_noise_cov(new Matrix(measureNoise));
      mKalmanRotation.setState_post(new Matrix(new double[] { 0, 0 }, 2));
   }

   private SensorManager mSensorManager;
   private Sensor mAccelerometer;
   private Sensor mGyroscope;
   private Timer mBroadcastTimer;
   private JKalman mKalmanPosition;
   private JKalman mKalmanRotation;
   private float[] mTransform;

   private long mTransferRate = 10;
   private int mSensorAccuracy = SensorManager.SENSOR_DELAY_FASTEST;
   //private int mSensorAccuracy = SensorManager.SENSOR_DELAY_UI;
   private double mRadiansToDegrees = 180.0 / Math.PI;
   private double mMetersToPixelsX;
   private double mMetersToPixelsY;

   private TimerTask mBroadcastTask = new TimerTask() {
      @Override
      public void run() {
         Intent shakeIntent = new Intent("me.projedi.antishake.ACTION_SHAKE");
         shakeIntent.putExtra("transform", mTransform);
         sendBroadcast(shakeIntent);
      }
   };

   private SensorEventListener mSensorListener = new SensorEventListener() {
      private long timestampAcceleration = 0;
      private long timestampGyroscope = 0;

      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) { } 

      @Override
      public void onSensorChanged(SensorEvent event) {
         double dt = 0.0;
         if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            if(timestampAcceleration != 0)
               dt = (double)(event.timestamp - timestampAcceleration) / 1e9;
            timestampAcceleration = event.timestamp;
            updatePosition(event.values[0], event.values[1], dt);
         } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if(timestampGyroscope != 0)
               dt = (double)(event.timestamp - timestampGyroscope) / 1e9;
            timestampGyroscope = event.timestamp;
            updateRotation(-event.values[2], dt);
         }
      }
   };

   private final IBinder mBinder = new Binder();
   private final String TAG = "ShakeService";
}
