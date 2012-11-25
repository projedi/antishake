package me.projedi.antishake;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.content.Intent;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Service calculates a transformation to minimize shaking of a view
 *
 * Uses intent "me.projedi.antishake.ACTION_SHAKE" with field "transform"
 * which is float[3] where float[0] - x translate, float[1] - y translate,
 * float[2] - rotatation.
 */
public class ShakeService extends Service {
// TODO: Use intents for setting accuracy and transfer rate
   @Override
   public IBinder onBind(Intent intent) { return mBinder; }

   @Override
   public void onCreate() {
      mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      // TODO: It uses API Level 9 sensor. Implement a fallback to pure accelerometer.
      mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
      mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
      // TODO: Make it possible to adjust the 3rd parameter
      mSensorManager.registerListener(mSensorListener, mAccelerometer, mSensorAccuracy);
      mSensorManager.registerListener(mSensorListener, mGyroscope, mSensorAccuracy);
      mTransform = new float[3];
      (new Timer()).schedule(mTimerTask, 0, mTransferRateMilliseconds);
   }

   @Override
   public void onDestroy() { mSensorManager.unregisterListener(mSensorListener); }

   private SensorManager mSensorManager;
   private Sensor mAccelerometer;
   private Sensor mGyroscope;
   private float[] mTransform;
   // TODO: come up with shorter name
   private long mTransferRateMilliseconds = 10;
   private int mSensorAccuracy = SensorManager.SENSOR_DELAY_FASTEST;

   private TimerTask mTimerTask = new TimerTask() {
      @Override
      public void run() {
         Intent shakeIntent = new Intent("me.projedi.antishake.ACTION_SHAKE");
         shakeIntent.putExtra("transform", mTransform);
         sendBroadcast(shakeIntent);
      }
   };

   private SensorEventListener mSensorListener = new SensorEventListener() {
      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) { }

      @Override
      public void onSensorChanged(SensorEvent event) {
         //TODO: Try to get some rotation data from just acceleration
         if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            mTransform[0] = -event.values[0];
            mTransform[1] = -event.values[1];
         } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mTransform[2] = event.values[2];
         }
      }
   };

   private final IBinder mBinder = new Binder();
}
