package com.roobit.android.restclient;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

public class RestClientRequestTask extends AsyncTask<Object, Void, RestResult> {

	private RestClientRequest _request = null;
	private static final int MAX_RETRIES = 3;

	public interface RestClientRequestListener {
		public void requestStarted();

		public void requestCancelled();

		public void requestFinishedPreprocess(RestResult result);

		public void requestFinished(RestResult result);
	}

	RestClientRequestListener listener;
	volatile private boolean _cancelled = false;
	private int _priority = Thread.NORM_PRIORITY;
	
	public RestClientRequestTask(RestClientRequestListener listener) {
		this.listener = listener;
	}

	public void setThreadPriority(int priority) {
		_priority = priority;
	}

	@Override
	protected RestResult doInBackground(Object... args) {
		Thread.currentThread().setPriority(_priority);
		RestClient.Operation op = (RestClient.Operation) args[0];
		Uri uri = (Uri) args[1];
		Properties httpHeaders = (Properties) args[2];
		Properties parameters = (Properties) args[3];
		ByteArrayOutputStream postData = (ByteArrayOutputStream) args[4];
		int retries = 0;
		RestResult result = null;
		if(!_cancelled) {
			do {
				synchronized (this) {
					if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
						_request = new RestClientRequest_Froyo();
					} else {
						_request = new RestClientRequest_Gingerbread();
					}
				}
				result = _request.synchronousExecute(op, uri, httpHeaders, parameters, postData);
				if(result.getResponseCode() == -1 && !_cancelled) {
					continue;
				} else {
					break;
				}
			} while(retries++ < MAX_RETRIES);
			synchronized (this) {
				_request = null;
			}
			if(listener != null) {
				listener.requestFinishedPreprocess(result);
			}
		}
		return result;
	}

	public void cancelRequest() {
		synchronized (this) {
			_cancelled  = true;
			cancel(true);
			if(_request != null) {
				_request.cancel();
			}
		}
	}

	@Override
	protected void onPreExecute() {
		if(listener != null) {
			listener.requestStarted();
		}
	}

	@Override
	protected void onCancelled() {
		if(listener != null) {
			listener.requestCancelled();
		}
	}

	@Override
	protected void onPostExecute(RestResult result) {
		if(listener != null) {
			listener.requestFinished(result);
		}
	}
}
