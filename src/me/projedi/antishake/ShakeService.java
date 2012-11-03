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

public class ShakeService extends Service
                          implements SensorEventListener {
   private SensorManager m_sensorManager;
   private Sensor m_accelerometer;
   private Sensor m_gyroscope;
   float[] m_transform;

   private TimerTask m_timerTask = new TimerTask() {
      @Override
      public void run() {
         Intent i = new Intent("android.intent.action.MAIN");
         i.putExtra("transform", m_transform);
         sendBroadcast(i);
      }
   };

   @Override
   public void onSensorChanged(SensorEvent event) {
      //TODO: Try to get some rotation data from just acceleration
      if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
         m_transform[0] = -event.values[0];
         m_transform[1] = -event.values[1];
      } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
         m_transform[2] = event.values[2];
      }
   }

   @Override
   public void onAccuracyChanged(Sensor sensor, int accuracy) { }

   @Override
   public void onCreate() {
      m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      //TODO: It uses API Level 9 sensor. Implement a fallback to pure accelerometer.
      m_accelerometer = m_sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
      //TODO: Make it possible to adjust the 3rd parameter
      m_sensorManager.registerListener( this, m_accelerometer
                                      , SensorManager.SENSOR_DELAY_UI);
      m_gyroscope = m_sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
      m_sensorManager.registerListener( this, m_gyroscope
                                      , SensorManager.SENSOR_DELAY_UI);
      m_transform = new float[3];
      Timer timer = new Timer();
      timer.schedule(m_timerTask,0,30);
   }

   @Override
   public void onDestroy() {
      m_sensorManager.unregisterListener(this);

   }

   private final IBinder m_binder = new Binder();

   @Override
   public IBinder onBind(Intent intent) {
      return m_binder;
   }
}
