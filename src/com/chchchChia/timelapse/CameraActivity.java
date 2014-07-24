package com.chchchChia.timelapse;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.chchchChia.timelapse.R;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.Toast;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {


	private static final String TAG = "TimeLapse_Debug";
	protected static Camera mCamera;//TODO why is this exposed?
	protected static float frameRate=2;
	private boolean isRecording = false;
	private boolean mIsBound=false;
	private Context mContext;
	//private Camera mCamera;
	private Camera.Parameters cParams;
	private TimeLapseService mBoundService;
	private Intent mIntent;
	private ImageButton awButton, rezButton, captureButton, flipCamera,  fpsButton, durButton;
	protected static FrameLayout preview;
	protected static CameraPreview mPreview;
	public static SurfaceView mSurfaceView;
    public static SurfaceHolder mSurfaceHolder;
    public static boolean mPreviewRunning;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    protected Messenger mService = null;
    private int fpsArrayValue;
    private double duration=0;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
	//TODO check if service is running, change button to stop if so
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		Log.d(TAG,"service created");
        setContentView(R.layout.activity_camera);
        mContext=this;
       // doBindService();
       // mCamera=getCameraInstance();
       // cParams=mCamera.getParameters();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs=PreferenceManager.getDefaultSharedPreferences(this);
        frameRate=prefs.getFloat("fps", 2);   
        fpsArrayValue=prefs.getInt("fps_array_value", 3);
        if (savedInstanceState!=null){
        	Log.d(TAG,"isRecording="+Boolean.toString(isRecording=savedInstanceState.getBoolean("isRecording")));
        	isRecording=savedInstanceState.getBoolean("isRecording");
        }else{
        	mIntent = new Intent(this, TimeLapseService.class).setAction("RECORD");    
	        mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);
	        mSurfaceHolder = mSurfaceView.getHolder();
	        mSurfaceHolder.addCallback(this);
	        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        initGUI();
	}

	private void initGUI(){
		// Add a listener, make it do things, repeat
		flipCamera=(ImageButton)findViewById(R.id.btnFlip);
		flipCamera.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				if(!isRecording){
				mBoundService.switchCamera();
				}
			}
			
		});
		awButton=(ImageButton)findViewById(R.id.btnAW);
		awButton.setOnClickListener(new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						if(!isRecording){
						
						final List<String> list = cParams.getSupportedWhiteBalance();
						CharSequence[] AWList=list.toArray(new CharSequence[list.size()]);
						AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
						builder.setTitle("Select Auto White")
				           .setItems(AWList, new DialogInterface.OnClickListener() {
				               public void onClick(DialogInterface dialog, int which) {
				               // The 'which' argument contains the index position
				               // of the selected item
				            	   mBoundService.setAW(list.get(which));
				            	   prefs.edit().putString("aw", list.get(which)).apply();
				            	   dialog.dismiss();
				                   // Set summary to be the user-description for the selected value
				                  // connectionPref.setSummary(sharedPreferences.getString(key, ""));
				            	   //Toast.makeText(mContext, "You Choose "+which, Toast.LENGTH_SHORT).show();
				           }
				    });
						builder.create().show();
				    	
					}
					}
				});
		
		rezButton=(ImageButton)findViewById(R.id.btnResolution);
		rezButton.setOnClickListener(new View.OnClickListener() {
					int quality=500;
					@Override
					public void onClick(View v) {
						if(!isRecording){
						final ArrayList<Integer> list=mBoundService.supportedProfiles();
						Log.d(TAG,list.toString());
						final Map<Integer, String> strMap=new TreeMap<Integer, String>(Collections.reverseOrder());
						for(Integer i:list){
							switch(i){
							
							//case 1000:strMap.put("Lowest Resolution Time Lapse",1000);
							//	break;
							//case 1001:strMap.put("Highest Resolution Time Lapse",1001);
							//	break;
							case 1002:strMap.put(1002,"176x144");
								break;
							case 1003:strMap.put(1003,"352x288");
								break;
							case 1004:strMap.put(1004,"720x480P");
								break;
							case 1005:strMap.put(1005,"1280x720P");
								break;
							case 1006:strMap.put(1006,"1920x1080P");
								break;
						
							}
						}
						
//						final List<Size> list = 
//								cParams.getSupportedVideoSizes() == null ? 
//										cParams.getSupportedVideoSizes():
//											cParams.getSupportedPreviewSizes();
						
						
						final CharSequence[] RezList=(CharSequence[]) strMap.values().toArray(new String[strMap.size()]);
						//TODO use above to set the initial value, from prefs
						AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
						builder.setTitle("Select Video Resolution")
				           .setItems(RezList, new DialogInterface.OnClickListener() {
				               public void onClick(DialogInterface dialog, int which) {
				               // The 'which' argument contains the index position
				               // of the selected item
				            	   //mBoundService.setRez(list.get(which),quality);
				            	   Integer[] temp = strMap.keySet().toArray(new Integer[strMap.size()]);
				            	   mBoundService.setProfile(temp[which]);
				            	   prefs.edit().putInt("profile", temp[which]).apply();
				            	   dialog.dismiss();
				                   // Set summary to be the user-description for the selected value
				                  // connectionPref.setSummary(sharedPreferences.getString(key, ""));

				           }
				    });
				    builder.create().show();
					}
					}
					});
		
		fpsButton=(ImageButton)findViewById(R.id.btnFPS);
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);		
		View fpsView = inflater.inflate(R.layout.fps_picker, null);
		
		fpsButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if(!isRecording){
						int value;
						final String[] fpsList={"0.125","0.25","0.333","0.5","1","1.5","2","3","5","10","30","60"};
						//if (Arrays.asList(fpsList).indexOf(Float.toString(prefs.getFloat("fps", 2)))==-1){
						//	value=
						//}
						//Log.d(TAG,"fps-pref="+Float.toString(prefs.getFloat("fps", 2)));
						final Dialog d = new Dialog(mContext);
						d.setTitle("Seconds Between Frames (sec)");
						d.setContentView(R.layout.fps_picker);
						Button btnSet=(Button)d.findViewById(R.id.btnFPSSet);
						Button btnCancel=(Button)d.findViewById(R.id.btnFPSCancel);
						final NumberPicker fpsNP=(NumberPicker) d.findViewById(R.id.fpsNP);
						fpsNP.setMinValue(0);
						fpsNP.setMaxValue(11);
						fpsNP.setDisplayedValues(fpsList);
						fpsNP.setValue(fpsArrayValue);
						fpsNP.setWrapSelectorWheel(false);
						btnSet.setOnClickListener(new View.OnClickListener() {
							
							@Override
							public void onClick(View v) {
								Log.d(TAG,"fpsarrayvalue= "+fpsArrayValue);
								mBoundService.setFPS(1/Double.parseDouble(fpsList[fpsNP.getValue()]));
								prefs.edit().putFloat("fps",(float) (1/Double.parseDouble(fpsList[fpsNP.getValue()]))).apply();
								prefs.edit().putInt("fps_array_value",fpsNP.getValue()).apply();
								fpsArrayValue=fpsNP.getValue();
								d.dismiss();
								
							}
						});
						btnCancel.setOnClickListener(new View.OnClickListener() {
							
							@Override
							public void onClick(View v) {
								d.dismiss();
								
							}
						});
						fpsNP.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {						
							@Override
							public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
								//mBoundService.setFPS(1/Double.parseDouble(fpsList[newVal]));
								//prefs.edit().putFloat("fps",(float) (1/Double.parseDouble(fpsList[newVal]))).apply();
								//prefs.edit().putInt("fps_array_value",newVal);
							}
						});
					d.show();	
					}
					}
				}); 
		durButton=(ImageButton)findViewById(R.id.btnDuration);
		durButton.setOnClickListener(new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						if(!isRecording){
						//final List<String> list = cParams.getSupportedWhiteBalance();
						final EditText input = new EditText(mContext);
						input.setRawInputType(Configuration.KEYBOARD_12KEY);
						input.setText(Double.toString(duration));
						//input.setKeyListener(DigitsKeyListener.getInstance());
						//CharSequence[] AWList=list.toArray(new CharSequence[list.size()]);
						AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
						builder.setTitle("Select Duration of Video (0=No Limit)")
						.setView(input)//TODO set MEssage to prev choosen value?
						.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
							  Editable value = input.getText();
							  
							  try{
								  mBoundService.setDuration((int)(1000*Double.parseDouble(value.toString())));
								  prefs.edit().putInt("duration", (int)(1000*Double.parseDouble(value.toString()))).apply();
								  duration=Integer.parseInt(value.toString());
							  }catch (NumberFormatException nfe){
								  AlertDialog.Builder temp = new AlertDialog.Builder(mContext);
								  temp.setTitle("Please only enter numbers!");
								  temp.show();
							  }
							  }
							});

							builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
							  public void onClick(DialogInterface dialog, int whichButton) {
							    // Canceled.
							  }
						
				    });
				    builder.create().show();
						
					}
					}
				});
		/*
		settingsButton=(ImageButton) findViewById(R.id.btnSettings);
		settingsButton.setOnClickListener(
				new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						startActivity(new Intent(mContext, PrefActivity.class));
						
					}
				}
				);*/
		
		captureButton = (ImageButton) findViewById(R.id.button_capture);
		setButtonText();
		//captureButton.setImageResource(R.drawable.stopped);
		captureButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		            if (isRecording) {
		            	mBoundService.stopRecording();
		                //Toast.makeText(mContext, "Stopped Recording", Toast.LENGTH_SHORT).show();
		                isRecording = false;
		                setButtonText();
		            } else {
		            		mIntent.putExtra("frameRate", frameRate);
		                    startService(mIntent);
		                    mBoundService.recordVideo();
		                    Toast.makeText(mContext, "Recording...", Toast.LENGTH_SHORT).show();
		                    isRecording = true;
		                    setButtonText();
		                }
		            }       
		    }
		);
	}
	

	
	private void setButtonText(){
		Log.d(TAG, "isRecording="+Boolean.toString(isRecording));
		//TODO Implement changing button
		if(this.isRecording){
			captureButton.setImageResource(R.drawable.record);
		}else{
			captureButton.setImageResource(R.drawable.stopped);
		}
	}
	private void updatePrefs(){
		
	}
	@Override
	protected void onSaveInstanceState(Bundle state) {
		state.putBoolean("isRecording", isRecording);
		 super.onSaveInstanceState(state);
	}
	
	
	@Override	
	protected void onPostCreate(Bundle savedInstanceState) {
		Log.d(TAG,"savedState made");
		super.onPostCreate(savedInstanceState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
	getMenuInflater().inflate(R.menu.menu, menu);
	return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			if(!isRecording){
			startActivity(new Intent(this, PrefActivity.class));
			return true;
			}else{
				return false;
			}
		}
	return super.onOptionsItemSelected(item);
}
	
	@Override
    protected void onPause() {
        super.onPause();
        doUnbindService();
        Log.d(TAG,"onPause!");
        if(this.isRecording){
        	
        	//mCamera.stopPreview();
        	Log.d(TAG,"paused while recording");
        }
        if (!this.isRecording){
        	stopService(mIntent);
        //TODO this needs to be modified for preference activity
        }
    }
	@Override
	protected void onDestroy(){
		super.onDestroy();
		 if (!this.isRecording){
		       //mBoundService.releaseCamera();
			 doUnbindService();
		       stopService(mIntent);
		       Log.d(TAG,"service stopped");
		       }else{
		 doUnbindService();
		       }
	}
	@Override
	protected void onResume(){
		super.onResume();
		//doBindService();
		//^^Badness, as onResume is called BEFORE surface creation/changed
		if(!this.isRecording){

		}
		if(this.isRecording){

		}
	}


	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = ((TimeLapseService.LocalBinder)service).getService();
	        isRecording=mBoundService.isRecording();
	        setButtonText();
	        cParams=mBoundService.getParams();
	       // mService = new Messenger(service);
	        mBoundService.setMessenger(new Messenger(new IncomingHandler()));
/*	        try {
                Message msg = Message.obtain(null, TimeLapseService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }*/
	        //createOptionLists();
	        Log.d(TAG,"service bound!");
	    }
		@Override
	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	        Log.d(TAG, "service disconnected!");
	    }
	};

	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(mContext, 
	            TimeLapseService.class), mConnection, Context.BIND_AUTO_CREATE);
	    
	    mIsBound = true;
	}

	void doUnbindService() {
	    if (mIsBound) {
	        // Detach our existing connection.
	        unbindService(mConnection);
	        mIsBound = false;
	    }
	}

	class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case TimeLapseService.STATUS_UPDATE:
                if (msg.arg1==TimeLapseService.RECORDING){
                	isRecording = true;
                    setButtonText();
                }else{
                	Toast.makeText(mContext, "Stopped Recording", Toast.LENGTH_SHORT).show();
	                isRecording = false;
	                setButtonText();
                }
                break;
            case TimeLapseService.TIME_UPDATE:
                //TODO implement this
                break;
            default:
                super.handleMessage(msg);
            }
        }
    }
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		Log.d(TAG,"surface Changed");
		doBindService();
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		Log.d(TAG,"surfaceDestroyed");
		
	}	
	

}
