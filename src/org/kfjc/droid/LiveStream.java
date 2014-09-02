package org.kfjc.droid;

import org.kfjc.droid.LiveStreamService.LiveStreamBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;

public class LiveStream extends Activity {

	private LiveStreamService streamService;
	private Intent playIntent;
	private boolean streamServiceBound = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_live_stream);
	}

	private ServiceConnection musicConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LiveStreamBinder binder = (LiveStreamBinder) service;
			streamService = binder.getService();
			streamServiceBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			streamServiceBound = false;
		}
	};
	
	@Override
	protected void onStart() {
	  super.onStart();
	  if (playIntent == null) {
	    playIntent = new Intent(this, LiveStreamService.class);
	    bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
	    startService(playIntent);
	  }
	}
	
	@Override
	public void onDestroy() {
		stopService(playIntent);
		streamService = null;
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.live_stream, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
