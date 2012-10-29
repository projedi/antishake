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

public class ShakeService extends Service
                          implements SensorEventListener {
   private SensorManager m_sensorManager;
   private Sensor m_accelerometer;

   @Override
   public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
      //TODO: Send notification
      float[] matrix = { event.values[0], event.values[1], event.values[2] };
      Intent i = new Intent("android.intent.action.MAIN").putExtra("transMatrix", matrix);
      this.sendBroadcast(i);
   }

   @Override
   public void onAccuracyChanged(Sensor sensor, int accuracy) { }

   @Override
   public void onCreate() {
      m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      m_accelerometer = m_sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      //TODO: Play with 3rd parameter
      m_sensorManager.registerListener( this, m_accelerometer
                                      , SensorManager.SENSOR_DELAY_UI);
   }

   private final IBinder m_binder = new Binder();

   @Override
   public IBinder onBind(Intent intent) {
      return m_binder;
   }
}
