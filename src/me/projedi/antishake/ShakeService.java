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
      prevCRes = new double[2];
      prevMeasure = new double[2];
   }

   @Override
   public void onDestroy() {
      Log.d(TAG, "service destroyed");
      mSensorManager.unregisterListener(mSensorListener);
      mBroadcastTimer.cancel();
   }

   private double[] prevRes;
   private double[] prevMeasure;
   private double[] bandpassFilter(double ax, double ay, double dt) {
      double cutoffLP = 3.0;
      double cutoffHP = 20.0;
      double RCLP = 1. / 2 / Math.PI / cutoffLP;
      double RCHP = 1. / 2 / Math.PI / cutoffHP;
      double alphaLP = dt / (RCLP + dt);
      double alphaHP = RCHP / (RCHP + dt);
      double[] res = new double[2];
      res[0] = alphaHP * prevRes[0] + alphaHP * (ax - prevMeasure[0]);
      prevMeasure[0] = ax;
      res[0] = alphaLP * res[0] + (1 - alphaLP) * prevRes[0];
      prevRes[0] = res[0];
      res[1] = alphaHP * prevRes[1] + alphaHP * (ay - prevMeasure[1]);
      prevMeasure[1] = ay;
      res[1] = alphaLP * res[1] + (1 - alphaLP) * prevRes[1];
      prevRes[1] = res[1];
      /*
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
      */
      return res;
   }

   private double[] prevCRes;
   private double[] lowpassCoordinate(double x, double y, double dt) {
      double[] res = new double[2];
      double cutoff = 1;
      double RC = 1. / 2 / Math.PI / cutoff;
      double alpha = dt / (RC + dt);
      res[0] = alpha * x + (1 - alpha) * prevCRes[0];
      prevCRes[0] = res[0];
      res[1] = alpha * y + (1 - alpha) * prevCRes[1];
      prevCRes[1] = res[1];
      return res;
   }

   private void updatePosition(double ax, double ay, double dt) {
      //Log.d(TAG, "Before: " + ax + " " + ay);
      double[] res = bandpassFilter(ax,ay,dt);
      double vx = res[0]*dt;
      double vy = res[1]*dt;
      double[][] trans = { {1, 0, dt, 0}
                         , {0, 1, 0,  dt}
                         , {1, 0, 0,  0}
                         , {0, 1, 0,  0} };
      mKalmanPosition.setTransition_matrix(new Matrix(trans));
      Matrix oldState = mKalmanPosition.Predict();
      Matrix newState = mKalmanPosition.Correct(new Matrix(new double[] {0, 0, vx, vy}, 4));
      res = lowpassCoordinate(newState.get(0,0),newState.get(1,0),10*dt);
      //res[0] = newState.get(0,0);
      //res[1] = newState.get(1,0);
      mTransform[0] = (float)(-res[0] * mMetersToPixelsX);
      mTransform[1] = (float)(-res[1] * mMetersToPixelsY);
      //double sX = newState.get(0,0) * mMetersToPixelsX;
      //double sY = newState.get(1,0) * mMetersToPixelsY;
      //double x = (oldState.get(0,0) + vx * dt) * mMetersToPixelsX;
      //double y = (oldState.get(1,0) + vy * dt) * mMetersToPixelsY;
      //Log.d(TAG, "Measured state: " + x + " " + y);
      //Log.d(TAG, "Stable state: " + sX + " " + sY);
      //mTransform[0] = (float)(-x - sX);
      //mTransform[1] = (float)(-y - sY);
      //mTransform[0] = (float)(-newState.get(0,0) * mMetersToPixelsX);
      //mTransform[1] = (float)(-newState.get(1,0) * mMetersToPixelsY);
      //Log.d(TAG, "After: " + res[0] + " " + res[1]);
      //double x = res[0] * 25;
      //double y = res[1] * 25;
      //mTransform[0] = (float)x;
      //mTransform[1] = (float)y;
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
      double v = 1e-3;
      double[][] processNoise = { {p, 0, dp, 0}
                                , {0, p, 0,  dp}
                                , {0, 0, v,  0}
                                , {0, 0, 0,  v} };
      mKalmanPosition.setProcess_noise_cov(new Matrix(processNoise));
      double mp = 1e-3;
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
   //private final String TAG = "ShakeService";
   private final String TAG = "Antishake";
}
