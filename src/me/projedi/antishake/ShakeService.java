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
      if (event.sensor.getType() != Sensor.TYPE_LINEAR_ACCELERATION) return;
      //TODO: Try to get rotation from acceleration values
      //It assumes that screen norm is directed at user.
      float[] transform = { -event.values[0], -event.values[1], 0.0f };
      Intent i = new Intent("android.intent.action.MAIN").putExtra("transform", transform);
      this.sendBroadcast(i);
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
