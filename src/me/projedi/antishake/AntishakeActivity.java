package me.projedi.antishake;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ComponentName;

import android.widget.TextView;

import android.util.Log;

public class AntishakeActivity extends Activity {
   private boolean m_bound = false;
   private TextView m_accelerometerLog;
   private static final String TAG = "Antishake";
   private BroadcastReceiver m_receiver;
   
   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      m_accelerometerLog = (TextView) findViewById(R.id.accelerometerLog);
   }

   @Override
   public void onStart() {
      super.onStart();
      Intent intent = new Intent(this, ShakeService.class);
      bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
   }

   @Override
   public void onResume() {
      super.onResume();
      IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");
      m_receiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
            float[] matrix = intent.getFloatArrayExtra("transMatrix");
            String str = String.format( "X: %f\nY: %f\nZ: %f"
                                      , matrix[0], matrix[1], matrix[2]);
            m_accelerometerLog.setText(str);
         }
      };
      this.registerReceiver(m_receiver, intentFilter);
   }

   @Override
   public void onPause() {
      super.onPause();
      this.unregisterReceiver(this.m_receiver);
   }

   private ServiceConnection m_connection = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName className, IBinder binder) {
         m_bound = true;
      }

      @Override
      public void onServiceDisconnected(ComponentName className) {
         m_bound = false;
      }
   };

}
