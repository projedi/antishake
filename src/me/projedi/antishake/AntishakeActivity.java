package me.projedi.antishake;

import android.app.Activity;
import android.os.Bundle;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;

import android.util.Log;

public class AntishakeActivity extends Activity
                               implements SensorEventListener {
   private SensorManager m_sensorManager;
   private Sensor m_accelerometer;
   private TextView m_accelerometerLog;
   private static final String TAG = "Antishake";
   
   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      m_accelerometer = m_sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      m_sensorManager.registerListener( this, m_accelerometer
                                      , SensorManager.SENSOR_DELAY_UI);
      m_accelerometerLog = (TextView) findViewById(R.id.accelerometerLog);
   }

   @Override
   public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
      String str = String.format( "X: %f\nY: %f\nZ: %f"
                                , event.values[0], event.values[1], event.values[2]);
      m_accelerometerLog.setText(str);
   }

   @Override
   public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
