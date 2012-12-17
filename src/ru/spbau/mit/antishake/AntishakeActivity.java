package ru.spbau.mit.antishake;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ComponentName;

import android.widget.TextView;
import android.widget.SeekBar;

import android.util.Log;

public class AntishakeActivity extends Activity
                               implements SeekBar.OnSeekBarChangeListener {
   private boolean m_bound = false;
   private RectangleView m_rectangleView;
   //private TextView m_trTextView;
   //private TextView m_rotTextView;
   //private SeekBar m_trSeekBar;
   //private SeekBar m_rotSeekBar;
   private static final String TAG = "Antishake";
   private BroadcastReceiver m_receiver;
   private PowerManager.WakeLock wakeLock;
   
   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      m_rectangleView = (RectangleView) findViewById(R.id.rectangleView);
      //m_trTextView = (TextView) findViewById(R.id.trTextView);
      //m_rotTextView = (TextView) findViewById(R.id.rotTextView);
      //m_trSeekBar = (SeekBar) findViewById(R.id.trSeekBar);
      //m_rotSeekBar = (SeekBar) findViewById(R.id.rotSeekBar);
      //m_trSeekBar.setOnSeekBarChangeListener(this);
      //m_rotSeekBar.setOnSeekBarChangeListener(this);
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My wakelock tag");
      String text = getString(R.string.long_text);
      m_rectangleView.setText(text);
   }

   @Override
   public void onResume() {
      super.onResume();
      Intent intent = new Intent(this, ShakeService.class);
      bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
      m_bound = true;
      IntentFilter intentFilter = new IntentFilter("ru.spbau.mit.antishake.ACTION_SHAKE");
      m_receiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
            float[] transform = intent.getFloatArrayExtra("transform");

            //String str = String.format( "X: %f; Y: %f"
            //                          , transform[0], transform[1]);
            //Log.d(TAG, str);
            m_rectangleView.setTransform(transform);
         }
      };
      registerReceiver(m_receiver, intentFilter);
      wakeLock.acquire();
   }

   @Override
   public void onPause() {
      super.onPause();
      if(m_bound) {
         unbindService(m_connection);
         m_bound = false;
      }
      unregisterReceiver(m_receiver);
      wakeLock.release();
   }

   private ServiceConnection m_connection = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName className, IBinder binder) { }
      @Override
      public void onServiceDisconnected(ComponentName className) { }
   };

   @Override
   public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      /*
      float change = (float)progress / 10.0f;
      if(seekBar == m_trSeekBar) {
         m_trTextView.setText("Translate sensitivity: " + change);
         m_rectangleView.setTranslateCoefficient(change);
      } else if(seekBar == m_rotSeekBar) {
         m_rotTextView.setText("Rotate sensitivity: " + change);
         m_rectangleView.setRotateCoefficient(change);
      }
      */
   }

   @Override
   public void onStartTrackingTouch(SeekBar seekBar) { }

   @Override
   public void onStopTrackingTouch(SeekBar seekBar) { }

}
