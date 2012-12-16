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
      //mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
      mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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
      prevRes = new double[2];
      prevMeasure = new double[2];
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
      //double x = mKalmanPosition.getState_post().get(0,0);
      //double y = mKalmanPosition.getState_post().get(1,0);
      Log.d(TAG, "Accelerometer ax = " + ax + "; ay = " + ay);
      Log.d(TAG, "Accelerometer vx = " + vx + "; vy = " + vy);
      //double x0 = 0.001;
      //double y0 = x0;
      //double cx = Math.abs(x) > x0 ? 1 : Math.abs(x) / x0;
      //double cy = Math.abs(y) > y0 ? 1 : Math.abs(y) / y0;
      double cx = -1;
      double cy = -1;
      double[][] trans = { {1,  0,  dt, 0}
                         , {0,  1,  0,  dt}
                         , {cx, 0,  0,  0}
                         , {0,  cy, 0,  0} };
      mKalmanPosition.setTransition_matrix(new Matrix(trans));
      mKalmanPosition.Predict();
      Matrix newState = mKalmanPosition.Correct(new Matrix(new double[] {0, 0, vx, vy}, 4));
      vx = newState.get(2,0);
      vy = newState.get(3,0);
      double x = newState.get(0,0);
      double y = newState.get(1,0);
      Log.d(TAG, "Kalman x = " + x + "; y = " + y);
      Log.d(TAG, "Kalman vx = " + vx + "; vy = " + vy);
      //x *= 0.3;
      //y *= 0.3;
      // I get almost 0.4 when in rest and about 1-3 when shaking
      double sigma = 1;
      double v0 = 2;
      //ax = ax < 0.3 || ax > 3 ? 0 : ax;
      //ay = ay < 0.3 || ay > 3 ? 0 : ay;
      vx *= Math.exp(-(Math.abs(vx)-v0)*(Math.abs(vx)-v0)/2/sigma/sigma);
      vy *= Math.exp(-(Math.abs(vy)-v0)*(Math.abs(vy)-v0)/2/sigma/sigma);
      Log.d(TAG, "Yet another x = " + x + "; y = " + y);
      Log.d(TAG, "Yet another vx = " + vx + "; vy = " + vy);
      newState.set(2,0,vx);
      newState.set(3,0,vy);
      newState.set(0,0,x);
      newState.set(1,0,y);
      mKalmanPosition.setState_post(newState);
      mTransform[0] = (float)(-newState.get(0,0) * mMetersToPixelsX);
      mTransform[1] = (float)(-newState.get(1,0) * mMetersToPixelsY);
   }

   private double [] prevRes;
   private double [] prevMeasure;
   private double[] bandpassFilter(double ax, double ay, double dt) {
      double cutoffLP = 1.0;
      double cutoffHP = 10.0;
      double RCLP = 1. / 2 / Math.PI / cutoffLP;
      double RCHP = 1. / 2 / Math.PI / cutoffHP;
      double alphaLP = dt / (RCLP + dt);
      double alphaHP = RCHP / (RCHP + dt);
      double[] res = new double[2];
      res[0] = ax * alphaLP + (1-alphaLP) * prevRes[0];
      double m = res[0];
      res[0] = prevRes[0] * alphaHP + alphaHP * (res[0] - prevMeasure[0]);
      prevMeasure[0] = m;
      prevRes[0] = res[0];
      res[1] = ay * alphaLP + (1-alphaLP) * prevRes[1];
      m = res[1];
      res[1] = prevRes[1] * alphaHP + alphaHP * (res[1] - prevMeasure[1]);
      prevMeasure[1] = m;
      prevRes[1] = res[1];
      return res;
   }

   private void updatePositionPureFilter(double ax, double ay, double dt) {
      Log.d(TAG, "Before: " + ax + " " + ay);
      double[] res = bandpassFilter(ax,ay,dt);
      Log.d(TAG, "After: " + res[0] + " " + res[1]);
      double x = res[0] * 2.2;
      double y = res[1] * 2.2;
      mTransform[0] = (float)(x * 10);
      mTransform[1] = (float)(y * 10);
   }

   private void updateRotation(double vphi, double dt) {
      double sigma = 0.5;
      double vphi0 = 1.0;
      vphi *= Math.exp(-(Math.abs(vphi)-vphi0)*(Math.abs(vphi)-vphi0)/2/sigma/sigma);
      //Log.d(TAG, "vphi = " + vphi);
      double phi = mKalmanRotation.getState_post().get(0,0);
      double phi0 = 0.1;
      double a = Math.abs(phi) > phi0 ? 1 : Math.abs(phi) / phi0;
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
      double[][] measure = { {0, 0, 0, 0}
                           , {0, 0, 0, 0}
                           , {0, 0, 1, 0}
                           , {0, 0, 0, 1} };
      //mKalmanPosition.setMeasurement_matrix(Matrix.identity(4, 4));
      mKalmanPosition.setMeasurement_matrix((new Matrix(measure)).transpose());
      double p = 1e-3;
      double dp = 1e-3;
      double v = 1e-1;
      double[][] processNoise = { {p, 0, dp, 0}
                                , {0, p, 0,  dp}
                                , {0, 0, v,  0}
                                , {0, 0, 0,  v} };
      mKalmanPosition.setProcess_noise_cov(new Matrix(processNoise));
      double mp = 1e-1;
      double mv = 1e-4;
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
         //if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
         if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if(timestampAcceleration != 0)
               dt = (double)(event.timestamp - timestampAcceleration) / 1e9;
            timestampAcceleration = event.timestamp;
            //updatePosition(event.values[0], event.values[1], dt);
            updatePositionPureFilter(event.values[0], event.values[1], dt);
         } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if(timestampGyroscope != 0)
               dt = (double)(event.timestamp - timestampGyroscope) / 1e9;
            timestampGyroscope = event.timestamp;
            updateRotation(-event.values[2], dt);
         }
      }
   };

   private final IBinder mBinder = new Binder();
   //private final String TAG = "ShakeService";
   private final String TAG = "Antishake";
}
