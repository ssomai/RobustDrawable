package com.jnm.android.robustdrawable;

import android.os.Handler;
import android.util.Base64;

class RobustDrawable_Tool {
	protected void log(String pLog) {
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
	}

	private static Handler sHandler = null;
	public static void post(Runnable pRunnable) {
		if(sHandler == null) {
			sHandler = new Handler();
		}
		sHandler.post(pRunnable);
	}

}
