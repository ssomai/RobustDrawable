package com.jnm.android.robustdrawable;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.WindowManager;

class RDTool {
	protected static void log(String pLog) {
//		JMLog.e("RobustDrawable_Tool] "+pLog);
	}
	
	static String encodeBase64(String pString) {
		String ret = "";
		try {
			ret = Base64.encodeToString(pString.getBytes("UTF-8"), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
		} catch (Throwable e) {
			RobustDrawable__Parent.ex(e);
			ret = pString;
		}
		return ret;
	}
	
	public static long getCurrentTime() {
		return System.currentTimeMillis(); 
//		return JMDate.getCurrentTime();
	}
	
	private static Handler sHandler = null;
	public static void post(Runnable pRunnable) {
		if(sHandler == null) {
//			sHandler = new Handler();
			sHandler = new Handler(RobustDrawable__Parent.getContext().getMainLooper());
		}
		
		sHandler.post(pRunnable);
	}
	
	public static boolean isNotInInterval(long pCheckTime, int pInterval) {
		return isNotInInterval(getCurrentTime(), pCheckTime, pInterval);
	}
	public static boolean isNotInInterval(long pNowTime, long pCheckTime, long pInterval) {
		return !(pNowTime - pInterval < pCheckTime && pCheckTime <= pNowTime);
	}
	
	public static boolean isMainThread() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	
	private static int sDeviceWidth = -1;
	private static int sDeviceHeight = -1;
	public static int getDisplayWidth() {
		if(sDeviceWidth > 0)
			return sDeviceWidth;
		
//		Display display = ((WindowManager)JMProject_AndroidApp.getApplication().getAppContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//		boolean isLandscape = JMProject_AndroidApp.getApplication().getAppContext().getResources().getConfiguration().orientation != 1;
		Display display = ((WindowManager)RobustDrawable__Parent.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		boolean isLandscape = RobustDrawable__Parent.getContext().getResources().getConfiguration().orientation != 1;
		
		if(isLandscape)
			sDeviceWidth = display.getHeight();
		else 
			sDeviceWidth = display.getWidth();
		return sDeviceWidth;
	}
	public static int getDisplayHeight(){
		if(sDeviceHeight > 0)
			return sDeviceHeight;
		
		Display display = ((WindowManager)RobustDrawable__Parent.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		boolean isLandscape = RobustDrawable__Parent.getContext().getResources().getConfiguration().orientation != 1;
		
//		Point size = new Point();
//		display.getSize(size);
//		int width = size.x;
//		int height = size.y;
		
		if(isLandscape)
			sDeviceHeight = display.getWidth();
		else 
			sDeviceHeight = display.getHeight();
		
		return sDeviceHeight;
	}

	
}
