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

import java.util.Timer;
import java.util.TimerTask;

import jama.*;
import jkalman.*;

// TODO: Bandpass filter on sensors. Then Kalman to calculate desired position
// then get transformation to that position.

// TODO: Don't recreate stuff in onCreate

/**
 * Service calculates a transformation to minimize shaking of a view
 *
 * Uses intent "me.projedi.antishake.ACTION_SHAKE" with field "transform"
 * which is float[3] where float[0] - x translate, float[1] - y translate,
 * float[2] - rotatation.
 */
public class ShakeService extends Service {
// TODO: Use intents for setting params
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
      //mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
      mSensorManager.registerListener(mSensorListener, mAccelerometer, mSensorAccuracy);
      mSensorManager.registerListener(mSensorListener, mGyroscope, mSensorAccuracy);
      mTransform = new float[6];
      mTransform[0] = 0;
      mTransform[1] = 0;
      mTransform[2] = 0;
      mTransform[3] = 0;
      mTransform[4] = 0;
      mTransform[5] = 0;
      mMeasurement = new float[3];
      mMeasurement[0] = 0;
      mMeasurement[1] = 0;
      mMeasurement[2] = 0;
      /*
      mMeasurementPrev = new float[3];
      mMeasurementPrev[0] = 0;
      mMeasurementPrev[1] = 0;
      mMeasurementPrev[2] = 0;
      mFilteredMeasurement = new float[3];
      mFilteredMeasurement[0] = 0;
      mFilteredMeasurement[1] = 0;
      mFilteredMeasurement[2] = 0;
      lowpassCoef = 1;
      highpassCoef = 1;
      */
      /*
      mPredict = new float[3];
      mPredict[0] = 1;
      mPredict[1] = 1;
      mPredict[2] = 1;
      mCorrect = new float[3];
      mCorrect[0] = 1;
      mCorrect[1] = 1;
      mCorrect[2] = 1;
      */
      mTimer = new Timer();
      mTimer.schedule(mTimerTask, 0, mTransferRateMilliseconds);
      try {
         mKalmanFilter = new JKalman(6, 6);
         //mKalmanFilter = new JKalman(6, 3);
      } catch(Exception e) { }
      double[][] trans = { {1, 0, 0, 1, 0, 0}
                         , {0, 1, 0, 0, 1, 0}
                         , {0, 0, 1, 0, 0, 1}
                         , {0, 0, 0, 1, 0, 0}
                         , {0, 0, 0, 0, 1, 0}
                         , {0, 0, 0, 0, 0, 1}
                         };
      mKalmanFilter.setTransition_matrix(new Matrix(trans));

      //double[][] measure = { {0, 0, 0}
                           //, {0, 0, 0}
                           //, {0, 0, 0}
                           //, {1, 0, 0}
                           //, {0, 1, 0}
                           //, {0, 0, 1}
                           //};
      //mKalmanFilter.setMeasurement_matrix(new Matrix(measure).transpose());

      double trNoise = 1e-1;
      double rotNoise = 1e-1;
      double vTrNoise = 1e-3;
      double vRotNoise = 1e-3;
      double dTrNoise = 1e-1;
      double dRotNoise = 1e-1;
      double[][] procNoise = { {trNoise, 0, 0, dTrNoise, 0, 0}
                             , {0, trNoise, 0, 0, dTrNoise, 0}
                             , {0, 0, rotNoise, 0, 0, dRotNoise}
                             , {0, 0, 0, vTrNoise, 0, 0}
                             , {0, 0, 0, 0, vTrNoise, 0}
                             , {0, 0, 0, 0, 0, vRotNoise}
                             };
      mKalmanFilter.setProcess_noise_cov(new Matrix(procNoise));
      double meTrNoise = 1e3;
      double meRotNoise = 1e3;
      //double meTrNoise = 0;
      //double meRotNoise = 0;
      double meVTrNoise = 1e-5;
      double meVRotNoise = 1e-5;
      double[][] measNoise = { {meTrNoise, 0, 0, 0, 0, 0}
                             , {0, meTrNoise, 0, 0, 0, 0}
                             , {0, 0, meRotNoise, 0, 0, 0}
                             , {0, 0, 0, meVTrNoise, 0, 0}
                             , {0, 0, 0, 0, meVTrNoise, 0}
                             , {0, 0, 0, 0, 0, meVRotNoise}
                             };
      //double[][] measNoise = { {meVTrNoise, 0, 0}
                             //, {0, meVTrNoise, 0}
                             //, {0, 0, meVRotNoise}
                             //};
      mKalmanFilter.setMeasurement_noise_cov(new Matrix(measNoise));
   }

   @Override
   public void onDestroy() {
      Log.d(TAG, "service destroyed");
      mSensorManager.unregisterListener(mSensorListener);
      mTimer.cancel();
   }

   private void applyKalman() {
      double[] measuredState = new double[6];
      for(int i = 0; i != 3; ++i) measuredState[i] = 0;
      for(int i = 3; i != 6; ++i) { measuredState[i] = mMeasurement[i-3]; }
      //double[] measuredState = new double[3];
      //for(int i = 0; i != 3; ++i) { measuredState[i] = mMeasurement[i]; }
      mKalmanFilter.Predict();
      Matrix correctedState = mKalmanFilter.Correct(new Matrix(measuredState, 6));
      //Matrix correctedState = mKalmanFilter.Correct(new Matrix(measuredState, 3));
      for(int i = 0; i != 3; ++i) {
         mTransform[i] = (float)correctedState.getArray()[i][0];
      }
   }

/*
   private void bandpassFilter(float dt) {
      // Lowpass: yi = yi-1 + al(xi - yi-1)
      // Highpass: yi = ah(yi-1 + xi - xi-1)
      // => Bandpass yi = ah(yi-1 + al(xi - yi-1) + xi - xi-1)
      // yi = ah(1 - al)yi-1 + ah(al + 1)xi - ah xi-1
      float lpcoef = dt / (lowpassCoef + dt);
      float hpcoef = highpassCoef / (lowpassCoef + dt);
      for(int i = 0; i != 3; ++i) {
         mFilteredMeasurement[i] *= hpcoef * (1 - lpcoef);
         mFilteredMeasurement[i] += hpcoef * (1 + lpcoef) * mMeasurement[i];
         mFilteredMeasurement[i] -= hpcoef * mMeasurementPrev[i];
         mMeasurement[i] = mFilteredMeasurement[i];
      }
   }
   */

   private SensorManager mSensorManager;
   private Sensor mAccelerometer;
   private Sensor mGyroscope;
   private Timer mTimer;
   private JKalman mKalmanFilter;
   private float[] mTransform;
   private float[] mMeasurement;
   //private float[] mMeasurementPrev;
   //private float[] mFilteredMeasurement;
   //private float lowpassCoef;
   //private float highpassCoef;
   //private float[] mPredict;
   //private float[] mCorrect;
   // TODO: come up with shorter name
   private long mTransferRateMilliseconds = 10;
   private int mSensorAccuracy = SensorManager.SENSOR_DELAY_FASTEST;
   //private int mSensorAccuracy = SensorManager.SENSOR_DELAY_UI;

   private TimerTask mTimerTask = new TimerTask() {
      @Override
      public void run() {
         Intent shakeIntent = new Intent("me.projedi.antishake.ACTION_SHAKE");
         float[] transform = new float[3];
         transform[0] = mTransform[0];
         transform[1] = mTransform[1];
         transform[2] = mTransform[2];
         shakeIntent.putExtra("transform", transform);
         sendBroadcast(shakeIntent);
      }
   };

   private SensorEventListener mSensorListener = new SensorEventListener() {
      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) {
         String sensorName = "";
         if(sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
            sensorName = "accel";
         else if(sensor.getType() == Sensor.TYPE_GYROSCOPE)
            sensorName = "gyro";
         Log.d(TAG, "accuracy of " + sensorName + " changed to " + accuracy);
      }

      @Override
      public void onSensorChanged(SensorEvent event) {
         boolean isChanged = false;
         //TODO: Try to get some rotation data from just acceleration
         if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            //mMeasurementPrev[0] = mMeasurement[0];
            //mMeasurementPrev[1] = mMeasurement[1];
            mMeasurement[0] = -event.values[0];
            mMeasurement[1] = -event.values[1];
            isChanged = true;
            //Log.d(TAG, "got x=" + mMeasurement[0] + ", y=" + mMeasurement[1]);
         } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            //mMeasurementPrev[2] = mMeasurement[2];
            mMeasurement[2] = event.values[2];
            isChanged = true;
            //if(Math.abs(mMeasurement[2]) > 0.009)
            //   Log.d(TAG, "got phi=" + mMeasurement[2]);
         }
         if(isChanged) {
            //bandpassFilter(10);
            //predict();
            //correct();
            applyKalman();
         }

      }
   };

   private final IBinder mBinder = new Binder();
   private final String TAG = "ShakeService";
}
