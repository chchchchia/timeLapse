package com.chchchChia.timelapse;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;



import java.util.List;

import com.chchchChia.timelapse.R;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class TimeLapseService extends Service{
	public static boolean IS_RECORDING=false;
	protected static boolean sdCard = false;
	protected static final int TIME_UPDATE=1;
	protected static final int STATUS_UPDATE=2;
	protected static final int RECORDING=1;
	protected static final int STOPPED=0;
	private static final String TAG="TimeLapseService";
	protected int duration=0;
	private Context mContext;
	private Intent mIntent;
	private NotificationManager mNoteMgr;
	private Notification note;
	private WakeLock wakeLock;
	private Size videoSize;
	private int timeElapsed=0;
	private int quality=500000;
	private int mCameraID, AWid;
	private int cp=CamcorderProfile.QUALITY_TIME_LAPSE_HIGH;
	protected  Camera mCamera;
	private MediaRecorder mMediaRecorder;
	private double frameRate=2;
	protected static CameraPreview mPreview;
	private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private File videoFile;
    private Messenger mClient;

	@Override
	public void onCreate(){
		mNoteMgr=(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mContext=getApplicationContext();
		IS_RECORDING=false;
		mCamera=getCameraInstance();
		mSurfaceView = CameraActivity.mSurfaceView;
	    mSurfaceHolder = CameraActivity.mSurfaceHolder;
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(this);
        frameRate=prefs.getFloat("fps", 2);
        setAW(prefs.getString("aw", Camera.Parameters.WHITE_BALANCE_AUTO));
        //setRez(prefs.get)
        setProfile(prefs.getInt("profile", CamcorderProfile.QUALITY_TIME_LAPSE_HIGH));
        setDuration(prefs.getInt("duration", 0));
	    try {    	
	    	prepPreview();
			mCamera.setPreviewDisplay(mSurfaceHolder);
			mCamera.startPreview();
			Log.d(TAG,"preview started");
		} catch (IOException e) {
			Log.d(TAG,e.getMessage());
			e.printStackTrace();
		}
		Log.d(TAG,"service creation");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if(mCamera==null){
			mCamera=getCameraInstance();
		}
		frameRate=intent.getFloatExtra("frameRate", 2);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		    wakeLock = pm.newWakeLock(pm.PARTIAL_WAKE_LOCK, "GPS_Serv");
		    if(!wakeLock.isHeld()){
		    	wakeLock.acquire();
		    }
		startForeground(1221, showNotification("TimeLapse is Recording..."));
	    return START_STICKY;
	}

	protected void stopRecording(){
		if(IS_RECORDING){
			try {
				mCamera.reconnect();
			} catch (IOException e) {
				Log.d(TAG,e.getMessage());
				e.printStackTrace();
			}
			mMediaRecorder.stop();
			releaseMediaRecorder();
			mCamera.lock();
			IS_RECORDING=false;
			sendStatusMessageToUI();
			updateNotification();
			if (wakeLock != null && wakeLock.isHeld()) {
			      wakeLock.release();
			      wakeLock = null;
			    }	
		}
		
		//add new video to gallery
		
		ContentValues values = new ContentValues(4);
		long current = System.currentTimeMillis();
		values.put(MediaStore.Video.Media.TITLE, "Time Lapse" + videoFile.getName());
		values.put(MediaStore.Video.Media.DATE_ADDED, (int) (current / 1000));
		values.put(MediaStore.Video.Media.MIME_TYPE, "video/mpeg");
		values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());
		ContentResolver contentResolver = getContentResolver();

		Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		Uri newUri = contentResolver.insert(base, values);

		// Notify the media application on the device
		sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri)); 
	}
	

	protected void setMessenger(Messenger m){
		mClient=m;
	}
	protected void recordVideo(){
		if(prepCamcorder()){
			mMediaRecorder.start();
			IS_RECORDING=true;
			sendStatusMessageToUI();
			updateNotification();
			Log.d(TAG,"videoStart");
		}else{
			Log.d(TAG, "failed to prep camera");
			Toast.makeText(mContext, "Recording Failed!", Toast.LENGTH_SHORT).show();
			releaseMediaRecorder();
		}
	}
	
	@Override
	public void onDestroy(){
		releaseMediaRecorder();
		releaseCamera();		 
		if (wakeLock != null && wakeLock.isHeld()) {
		      wakeLock.release();
		      wakeLock = null;
		    }	
	}

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	private final IBinder mBinder = new LocalBinder();


	@SuppressLint("NewApi")
	private Notification showNotification(String text){
		mNoteMgr=(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		 Intent notificationIntent = new Intent(getApplicationContext(), CameraActivity.class);
		 PendingIntent pintent=PendingIntent.getActivity(mContext, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		
		 Notification not = new Notification.Builder(mContext)
		 	.setContentTitle(text)
		 	.setSmallIcon(R.drawable.icon)
		 	.setOngoing(true)
		 	.setContentIntent(pintent)
		 	.build();
		 return not;
	}

	private void updateNotification(){
		Notification note;
		if(IS_RECORDING){
			note=showNotification("TimeLapse is Recording...");
		}else{
			note=showNotification("TimeLapse is Stopped...");
		}

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNoteMgr.notify(1221, note);
	}
	@SuppressLint("NewApi")
	//API 9 is reqd for the for loop. Fallback is included, and Lint is told to shutup
	public Camera getCameraInstance(){
		if(mCamera!=null){
			return mCamera;
		}
	    Camera c = null;
	    int id=0;
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
	    	for (id=0;id<=Camera.getNumberOfCameras();id++){
			    try {
			    	if(id==Camera.CameraInfo.CAMERA_FACING_BACK){
			        c = Camera.open(id); // attempt to get a Camera instance
			        mCameraID=id;
			        	break;
			    	}
			    }
			    catch (Exception e){
			    	//Log.d(TAG,e.getMessage());
			    	e.printStackTrace();
			    	Toast.makeText(mContext, e.toString(), Toast.LENGTH_SHORT).show();
			    }
	    }
	    }else{
	    	try {
		        c = Camera.open(); // attempt to get a Camera instance
		    }
		    catch (Exception e){
		    	e.printStackTrace();
		    	Toast.makeText(mContext, e.toString(), Toast.LENGTH_SHORT).show();
		    }
	    }
	    return c; // returns null if camera is unavailable
	}
	protected void switchCamera(){
		if(Camera.getNumberOfCameras()<2){
			Toast.makeText(mContext, "Sorry, no other cameras to switch to.",Toast.LENGTH_SHORT).show();
			return;
		}
		if(mCameraID==Camera.CameraInfo.CAMERA_FACING_BACK){
			mCameraID=Camera.CameraInfo.CAMERA_FACING_FRONT;
		}else{
			mCameraID=Camera.CameraInfo.CAMERA_FACING_BACK;
		}
		if(mCamera!=null){
			try{
			mCamera.stopPreview();
			mCamera.release();
			mCamera=Camera.open(mCameraID);
			mCamera.setPreviewDisplay(mSurfaceHolder);
			prepPreview();
			mCamera.startPreview();
			}catch(IOException ioe){
				ioe.printStackTrace();
			}catch(RuntimeException re){
				re.printStackTrace();
				Toast.makeText(mContext, re.toString(), Toast.LENGTH_SHORT).show();;
			}
			
		}
	}
	protected void setAW(String id){
		if(mCamera!=null){
			Camera.Parameters params = mCamera.getParameters();
			params.setWhiteBalance(id);
			mCamera.setParameters(params);
		}
	}
	
	protected void setRez(Size id, int quality){
			this.videoSize=id;
			this.quality=quality;
	}
	
	protected void setProfile(int id){
		cp=id;
		//Toast.makeText(mContext, "Set profile numnber "+id, Toast.LENGTH_SHORT).show();
	}
	
	protected ArrayList<Integer> supportedProfiles(){
		ArrayList<Integer> list=new ArrayList<Integer>();
		for (int i=1000;i<1008;i++){//1000->1007 represent the time lapse profiles
			if(CamcorderProfile.hasProfile(mCameraID, i)){
				list.add(i);
			}
		}
		//Collections.sort(list);
		//Collections.reverse(list);
		Log.d(TAG,"list to str"+list.toString());
		Collections.sort(list, new Comparator<Integer>()
			    {
	        @Override
	        public int compare(Integer x, Integer y){
	        	return y-x;
	        }
		 });
		Log.d(TAG,"list to str"+list.toString());
		return list;
	}
	
	protected void setFPS(double fps){
			this.frameRate=fps;	
			Log.d(TAG, "fps set as "+fps);
	}
	
	protected void setDuration(int dur_in_ms){
		this.duration=dur_in_ms;
	}
	
	protected Camera.Parameters getParams(){
		if(mCamera!=null){
			return mCamera.getParameters();
		}else{
			return null;
		}
	}
	
	protected int getCameraID(){
		if(mCamera!=null){
		return mCameraID;
		}else{
			return 0;
		}
	}
	private void prepPreview(){
		Camera.Parameters params = mCamera.getParameters();
        mCamera.setParameters(params);
        Camera.Parameters p = mCamera.getParameters();

        final List<Size> listSize = p.getSupportedPreviewSizes();
        Log.d(TAG, listSize.toString());
        Size mPreviewSize = listSize.get(2);
        Log.v(TAG, "use: width = " + mPreviewSize.width 
                    + " height = " + mPreviewSize.height);
        p.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        p.setPreviewFormat(PixelFormat.YCbCr_420_SP);
        mCamera.setParameters(p);
	}
	protected boolean isRecording(){
		return IS_RECORDING;
	}
	@SuppressLint("NewApi")
	private boolean prepCamcorder(){
		//TODO this should read from prefs set
		//TODO prefs should be filled by individual camera's abilities
		
	    mMediaRecorder = new MediaRecorder();
	    setMediaRecorderListener();
	    Log.d(TAG,mMediaRecorder.toString());
	    // Step 1: Unlock and set camera to MediaRecorder
	    mCamera.unlock();
	    mMediaRecorder.setCamera(mCamera);
	    // Step 2: Set sources
	    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
	    // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
		    mMediaRecorder.setProfile(CamcorderProfile.get(mCameraID,cp));
		    mMediaRecorder.setCaptureRate(frameRate);
		    mMediaRecorder.setMaxDuration(duration);
		    //mMediaRecorder.setVideoEncodingBitRate(quality);
		    //TODO adjust above to be less, umm shitty, with user adjustability
	    }else{
	    	mMediaRecorder.setVideoSize(videoSize.width, videoSize.height);
	    	  mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
	    	  mMediaRecorder.setMaxDuration(duration);;
	    	  mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
	    	  mMediaRecorder.setVideoEncodingBitRate(quality);//TODO this should be 500k, or 2000k
	    	  //TODO set capture rate with api appropriate method
	    	  mMediaRecorder.setCaptureRate(frameRate);
	    }
	    // Step 4: Set output file
	    mMediaRecorder.setOutputFile(getOutputMediaFile().toString());
	    // Step 5: Set the preview output
	    mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
	    // Step 6: Prepare configured MediaRecorder
	    
	    try {
	        mMediaRecorder.prepare();
	    } catch (IllegalStateException e) {
	        Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
	        releaseMediaRecorder();
	        return false;
	    } catch (IOException e) {
	        Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
	        releaseMediaRecorder();
	        return false;
	    }
	    return true;
		
	}
	
	private void setMediaRecorderListener(){
		mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
			
			@Override
			public void onInfo(MediaRecorder mr, int what, int extra) {
				if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
					stopRecording();
				}
				
			}
		});
	}
	private File getOutputMediaFile(){
		File mediaStorageDir;
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
			Log.d(TAG,"mounted");
			
		   mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
		              Environment.DIRECTORY_DCIM), "TimeLapse");
		    if (! mediaStorageDir.exists()){
		        if (! mediaStorageDir.mkdirs()){
		            Log.d(TAG, "failed to create directory");
		            return null;
		        }
		    
		}
		}else{
			Toast.makeText(mContext, "Unable to access file system!", Toast.LENGTH_SHORT).show();
			return null;
		}
	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "VID_"+ timeStamp + ".mp4");
	        if(!mediaFile.exists()){
	        	try{
	        	mediaFile.createNewFile();
	        	Log.d(TAG,"file exists "+mediaFile.getPath());
	        	}catch (IOException ioe){
	        		ioe.printStackTrace();
	        	}
	        	}
	        videoFile=mediaFile;
	    return mediaFile;
	}	
	private void sendStatusMessageToUI() {
        if(mClient!=null){
            try {
                // Send data as an Integer
            	//Bundle b = new Bundle();
             //   b.putString("status","stopped");
            	int status=IS_RECORDING ?  RECORDING:STOPPED;
            	Message msg = Message.obtain(null, STATUS_UPDATE, status, 0);
            //	msg.setData(b);
               mClient.send(msg);
               
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
               e.printStackTrace();
            }
        }
    }
	
	//TODO, for time display
	 private void sendTimeMessageToUI() {
	        if(mClient!=null){
	            try {
	                Message msg = Message.obtain(null, TIME_UPDATE, timeElapsed, 0);
	                mClient.send(msg);
	            } catch (RemoteException e) {
	                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
	               e.printStackTrace();
	            }
	        }
	    }
	 public class LocalBinder extends Binder {
	        TimeLapseService getService() {
	            return TimeLapseService.this;
	        }
	    }

}
