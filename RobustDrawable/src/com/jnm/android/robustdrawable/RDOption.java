package com.jnm.android.robustdrawable;

import android.graphics.Bitmap;


public abstract class RDOption { // implements Comparable<RDOption> {
//	Bitmap applyOption(Bitmap pBitmap) {
//		switch (this) {
//		case Blur: 
//			return applyBlur(pBitmap);
//		case WhiteCircle: 
//			return applyWhiteCircle(pBitmap);
//		case WhiteRoundedRect:
//			return applyWhiteRoundedRect(pBitmap);
//		}
//	}
	
	protected void log(String pLog) {
		if(RobustDrawable__Parent.isShowLog()) {
			RDTool.log("RDOption] Class:"+this.getClass().getSimpleName()+" Log:"+pLog);
		}
	}
	
	public abstract Bitmap applyOption(RD__BitmapKey pRDBitmapKey, Bitmap pBitmap) throws Throwable;

	public boolean isHardOption() {
		return false;
	}
	
//	@Override
//	public int compareTo(RDOption pAnother) {
//		return getClass().getSimpleName().compareTo(pAnother.getClass().getSimpleName());
//	}
}
