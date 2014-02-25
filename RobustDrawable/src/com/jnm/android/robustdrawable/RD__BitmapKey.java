package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.widget.ImageView.ScaleType;

public abstract class RD__BitmapKey extends ConstantState implements Serializable, Cloneable {
	protected void log(String pLog) {
//		JMLog.e("RDBitmapKey] Class:"+this.getClass().getSimpleName()+" Key:"+getKey()+" Log:"+pLog);
	}
	
	protected abstract String getKey();
	protected abstract String getKeyWithoutSize();
	

	private static File getDir_IfNotExistMKDir(String pPath) {
		File ret = new File(pPath);
		if(ret.exists() == false) {
			boolean result = ret.mkdir();
			
			if(result == false) {
				ret.delete();
				ret.getAbsoluteFile().delete();
				ret = new File(pPath);
				ret.mkdir();
			}
		} else if(ret.isDirectory() == false) {
			ret.delete();
			ret = new File(pPath);
			ret.mkdir();
		}
		return ret;
	}
	private static File getDir_Cache() {
		return getDir_IfNotExistMKDir(RobustDrawable__Parent.getContext().getCacheDir().getPath()+"/RobustDrawable");
	}
	private static boolean isNotInInterval(long pNowTime, long pCheckTime, long pInterval) {
		return !(pNowTime - pInterval < pCheckTime && pCheckTime <= pNowTime);
	}
	private static long 	sCache_LastCheckDateTime = -1;

	static long sFileCache_MaxSize = 30 * 1024 * 1024;
	static long sFileCache_TrimSize = 20 * 1024 * 1024;
	private static File getFile_Cache(String pName) {
		File ret = new File(getDir_Cache()+"/"+RobustDrawable_Tool.encodeBase64(pName));
		// if(ret == null) ret = new File(getDir_Cache().getPath()+"/"+pName);
		long now = RobustDrawable_Tool.getCurrentTime();
		
		if(isNotInInterval(now, sCache_LastCheckDateTime, 1*60*1000)) {
			sCache_LastCheckDateTime = now;
			
			new Thread() {
				private void listing(File pDir) {
					for(File f : pDir.listFiles()) {
						if(f.isDirectory()) {
							listing(f);
						} else if(f.isFile()) {
							mSum += f.length();
							mFiles.add(f);
						}
					}
				}
				
				private long mSum = 0;
				private ArrayList<File> mFiles = new ArrayList<File>();
				@Override
				public void run() {
					super.run();
					try {
						listing(getDir_Cache());
						if(mSum < sFileCache_MaxSize)
							return;
						
						Collections.sort(mFiles, new Comparator<File>() {
							@Override
							public int compare(File pO1, File pO2) {
								return (int) (pO2.lastModified() - pO1.lastModified());
							}
						});
//						mFiles.sort(new IJMComparator<File>() {
//							@Override
//							public int compare(File pO1, File pO2) {
//								return (int) (pO2.lastModified() - pO1.lastModified());
//							}
//						});
						
						while(mFiles.size() > 1) {
							File f = mFiles.get(0);
							if(mSum <= sFileCache_TrimSize) {
								break;
							}
							mSum -= f.length();
							f.delete();
							mFiles.remove(0);
						}
						
					} catch (Throwable e) {
						RobustDrawable__Parent.ex(e);
					}
				}
			}
			.start();
		}
		
		return ret;
	}
	public File getCacheFile_Original() {
		return getFile_Cache(getKey());
	}
	public boolean isDeletable_CacheFile_Original() {
		return true;
	}

	public File getCacheFile_Result() {
		return getFile_Cache(toString());
	}
	
	@Override
	public RD__BitmapKey clone() throws CloneNotSupportedException {
		RD__BitmapKey c = (RD__BitmapKey) super.clone();
		
		c.mSrcRect = new Rect();
		c.mSrcRect.set(mSrcRect);
		
		c.mDstRect = new Rect();
		c.mDstRect.set(mDstRect);
		
		c.mBounds = new Rect();
		c.mBounds.set(mBounds);
		
		c.mViewRect = new Rect();
		c.mViewRect.set(mViewRect);
		
		c.mOptions = new ArrayList<RDOption>();
		c.mOptions.addAll(mOptions);
		
		c.mScaleType = mScaleType;
		
		return c;
	}
	
	private ArrayList<RDOption> 	mOptions 	= new ArrayList<RDOption>();
	private String getStringOfOptions() {
		String ret = "";
		for(RDOption o : mOptions) {
			ret += "_"+o.getClass().getSimpleName();
		}
		return ret;
	}
	
	static int sDefaultResId = -1;
	private int mDefaultResId = -1;
	public RD__BitmapKey setDefaultBitmapResource(int pResId) {
		mDefaultResId = pResId;
		return this;
	}
	Bitmap getDefaultBitmap() {
		if(mDefaultResId >= 0) {
			log("getDefaultBitmap 1 "+mDefaultResId);
			
			RD_Resource r = new RD_Resource(mDefaultResId);
			for(int i=0;i<mOptions.size();i++) {
				r.addOption(mOptions.get(i));
			}
			Bitmap bm = RobustDrawable__Parent.getCache(r);
			if(bm != null && bm.isRecycled() == false) {
				return bm;
			}
			try {
				log("getDefaultBitmap 3 ");
				bm = BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), r.mResID);
				bm = applyOptions(bm);

				RobustDrawable__Parent.putCache(r, bm);
				return bm;
			} catch (Throwable e) {
				RobustDrawable__Parent.ex(e);
			}
		}
		return null;
	}
	
//	private File 	mCachedOriginalFile = null;
//	File 	getCachedOriginalFile() {
//		if(mCachedOriginalFile != null)
//			return mCachedOriginalFile;
//		return new File(mKey);
//	}
//	public abstract void downloadToOriginalFile() throws Throwable;
//	public abstract InputStream getOriginalInputStream();
//	public abstract File getOriginalFile();
	public RD__BitmapKey addOption(RDOption pOption) {
		mOptions.add(pOption);
		return this;
	}
	
	
	@Override
	public String toString() {
		String key = ""; 
		try {
			key = getKey();
		} catch (Throwable e) {
			key = "";
		}
		return this.getClass().getSimpleName()+"___"+key+"_"+getDstWidth()+"_"+getDstHeight()+getStringOfOptions();
	}
	@Override
	public int hashCode() {
		String key = ""; 
		try {
			key = getKey();
		} catch (Throwable e) {
			key = "";
		}
		return (this.getClass().getSimpleName()+"___"+key+getStringOfOptions()).hashCode();
	}
	@Override
	public boolean equals(Object pO) {
		boolean ret = false;
		if(pO instanceof RD__BitmapKey && this.getClass().isInstance(pO)) {
			RD__BitmapKey bk = ((RD__BitmapKey) pO);
			if(getKeyWithoutSize().compareTo(bk.getKeyWithoutSize()) == 0 && getStringOfOptions().compareTo(bk.getStringOfOptions()) == 0) {
				if(getDstWidth() == -1 && getDstHeight() == -1) {
					ret = true;
				} else if(getDstWidth() == bk.getDstWidth() && getDstHeight() == bk.getDstHeight()) {
					ret = true;
				}
			} else {
				ret = false;
			}
		} else {
			ret = super.equals(pO);
		}
		return ret;
	}
	
//	private int mViewWidth = 0;
//	private int mViewHeight = 0;
//	int getViewWidth() {
//		return mViewWidth;
//	}
//	int getViewHeight() {
//		return mViewHeight;
//	}

	void onBoundsChange(Rect pBounds) {
		mDstRect.left = pBounds.left;
		mDstRect.top = pBounds.top;
		mDstRect.right = pBounds.right;
		mDstRect.bottom = pBounds.bottom;
		
		if(mDstRect.width() > MaxBitmapSize) {
			mDstRect.bottom = mDstRect.height()*MaxBitmapSize/mDstRect.width() + mDstRect.top;
			mDstRect.right = MaxBitmapSize + mDstRect.left;
		}
		if(mDstRect.height() > MaxBitmapSize) {
			mDstRect.right = mDstRect.width()*MaxBitmapSize/mDstRect.height() + mDstRect.left;
			mDstRect.bottom = MaxBitmapSize + mDstRect.top;
		}
		
		mViewRect.left = pBounds.left;
		mViewRect.top = pBounds.top;
		mViewRect.right = pBounds.right;
		mViewRect.bottom = pBounds.bottom;
		
		mBounds.left = pBounds.left;
		mBounds.top = pBounds.top;
		mBounds.right = pBounds.right;
		mBounds.bottom = pBounds.bottom;
		
		
//		if(mSrcRect.width() > 0 && mSrcRect.height() > 0 && pBounds.width() > 0 && pBounds.height() > 0) {
//			switch (getScaleType()) {
//			case FIT_CENTER:
//				if((mSrcRect.width()*100/mSrcRect.height()) > (pBounds.width()*100/pBounds.height())) {
//					log("onBoundsChange ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+pBounds.width()+", "+pBounds.height()+") ");
//					float h = mSrcRect.height()*pBounds.width()/mSrcRect.width();
//					mViewRect.set(0, (int)((pBounds.height()-h)/2), pBounds.width(), (int)(h+(pBounds.height()-h)/2));
//				} else {
//					log("onBoundsChange ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+pBounds.width()+", "+pBounds.height()+") ");
//					float w = mSrcRect.width()*pBounds.height()/mSrcRect.height();
//					mViewRect.set((int)((pBounds.width()-w)/2), 0, (int)(w+(pBounds.width()-w)/2), pBounds.height());
//				}
//				break;
//			default:
//				mViewRect.set(0, 0, pBounds.width(), pBounds.height());
//				break;
//			}
//		}
	}
	private Rect mSrcRect = new Rect();
	Rect getSrcRect(Bitmap pBitmap) {
		if(mDstRect.width() > 0 && mDstRect.height() > 0) {
			switch (getScaleType()) {
			case CENTER_CROP:
				if((mDstRect.width()*100/mDstRect.height()) > (pBitmap.getWidth()*100/pBitmap.getHeight())) {
					float h = mDstRect.height()*pBitmap.getWidth()/mDstRect.width();
					mSrcRect.set(0, (int)((pBitmap.getHeight()-h)/2), pBitmap.getWidth(), (int)(h+(pBitmap.getHeight()-h)/2));
				} else {
					float w = mDstRect.width()*pBitmap.getHeight()/mDstRect.height();
					mSrcRect.set((int)((pBitmap.getWidth()-w)/2), 0, (int)(w+(pBitmap.getWidth()-w)/2), pBitmap.getHeight());
				}
				break;
			default:
				mSrcRect.set(0, 0, pBitmap.getWidth(), pBitmap.getHeight());
				break;
			}
		}
		return mSrcRect;
	}
	private Rect mDstRect = new Rect();
	Rect getDstRect() { return mDstRect; }
	
	private Rect mViewRect = new Rect();
	private Rect mBounds = new Rect();
	Rect getViewRect(Canvas pCanvas) {  
//		if(mSrcRect.width() > 0 && mSrcRect.height() > 0 && pCanvas.getWidth() > 0 && pCanvas.getHeight() > 0) {
//			switch (getScaleType()) {
//			case FIT_CENTER:
//				if((mSrcRect.width()*100/mSrcRect.height()) > (pCanvas.getWidth()*100/pCanvas.getHeight())) {
//					log("getViewRect ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+pCanvas.getWidth()+", "+pCanvas.getHeight()+") ");
//					float h = mSrcRect.height()*pCanvas.getWidth()/mSrcRect.width();
//					mViewRect.set(0, (int)((pCanvas.getHeight()-h)/2), pCanvas.getWidth(), (int)(h+(pCanvas.getHeight()-h)/2));
//				} else {
//					log("getViewRect ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+pCanvas.getWidth()+", "+pCanvas.getHeight()+") ");
//					float w = mSrcRect.width()*pCanvas.getHeight()/mSrcRect.height();
//					mViewRect.set((int)((pCanvas.getWidth()-w)/2), 0, (int)(w+(pCanvas.getWidth()-w)/2), pCanvas.getHeight());
//				}
//				break;
//			default:
//				mViewRect.set(0, 0, pCanvas.getWidth(), pCanvas.getHeight());
//				break;
//			}
//		}
		if(mSrcRect.width() > 0 && mSrcRect.height() > 0 && mBounds.width() > 0 && mBounds.height() > 0) {
			switch (getScaleType()) {
			case CENTER_INSIDE:
			case FIT_CENTER:
				if((mSrcRect.width()*100/mSrcRect.height()) > (mBounds.width()*100/mBounds.height())) {
					log("getViewRect ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+mBounds.width()+", "+mBounds.height()+") ");
					float h = mSrcRect.height()*mBounds.width()/mSrcRect.width();
					mViewRect.set(0, (int)((mBounds.height()-h)/2), mBounds.width(), (int)(h+(mBounds.height()-h)/2));
				} else {
					log("getViewRect ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+mBounds.width()+", "+mBounds.height()+") ");
					float w = mSrcRect.width()*mBounds.height()/mSrcRect.height();
					mViewRect.set((int)((mBounds.width()-w)/2), 0, (int)(w+(mBounds.width()-w)/2), mBounds.height());
				}
				break;
			default:
				mViewRect.set(0, 0, mBounds.width(), mBounds.height());
				break;
			}
		}
		return mViewRect; 
	}
	
	private static final int MaxBitmapSize = 680;
	public int getDstWidth() {
		if(mDstRect.width() <= 0) return -1;
		if(mDstRect.width() > MaxBitmapSize) return MaxBitmapSize;
//		return mDstRect.width();
		return mDstRect.width()*80/100;
	}
	public int getDstHeight() {
		if(mDstRect.height() <= 0) return -1;
		if(mDstRect.height() > MaxBitmapSize) return MaxBitmapSize;
//		return mDstRect.height();
		return mDstRect.height()*80/100;
	}
	
	private ScaleType mScaleType = ScaleType.CENTER_CROP;
	public ScaleType getScaleType() { return mScaleType; }
	public RD__BitmapKey setScaleType(ScaleType pScaleType) {
		mScaleType = pScaleType;
		return this;
	}

	@Override
	public Drawable newDrawable() {
		return new RobustDrawable__Parent().init(this);
	}
	
	@Override
	public Drawable newDrawable(Resources res) {
//		return new RobustDrawable__Parent(this);
		return new RobustDrawable__Parent().init(this);
	}
	
	private int mChangingConfigurations;
	@Override
	public int getChangingConfigurations() { return mChangingConfigurations; }
	void setChangingConfigurations(int pChangingConfigurations) { mChangingConfigurations = pChangingConfigurations; }

	Bitmap applyOptions(Bitmap pBitmap) throws Throwable {
		Bitmap ret = pBitmap;
		for(RDOption o : mOptions) {
			ret = o.applyOption(this, ret);
		}
		return ret;
	}

	protected void download_To_CacheFile_Original() throws Throwable {
		
	}
//	abstract Bitmap loadBitmap() throws Throwable;
	
	public RobustDrawable__Parent create() {
//		return new RobustDrawable__Parent(this);
		return new RobustDrawable__Parent().init(this);
	}
	
	private int mAttemptCount = 0;
	private static final int MaxAttemptCount = 3;
	public void increaseAttemptCount() {
		mAttemptCount++;
	}
	public boolean isAttemptable() {
		if(mAttemptCount > MaxAttemptCount)
			return false;
		return true;
	}

	public boolean isPrompt() {
		for(int i=0;i<mOptions.size();i++) {
			if(mOptions.get(i).isHardOption())
				return false;
		}
		
		if(getCacheFile_Original().exists() == false) {
			return false;
		}
				
		if(getCacheFile_Result().exists() == false) {
			return false;
		}
			
		if(getCacheFile_Original().lastModified() >= getCacheFile_Result().lastModified()) {
			return false;
		}
		
		long len = getCacheFile_Result().length();
		if(len < 10 * 1024 || 200 * 1024 < len) {
			return false;
		}
		
		return true;
	}
	protected Bitmap loadResultBitmap() throws Throwable {
		if(getDstWidth() > 10 && getDstHeight() > 10) {
			log("loadBitmap 1 ");
			return RDBitmapLoader.createBitmap(getCacheFile_Result(), getDstWidth(), getDstHeight(), this);
		}
		
		boolean retry 	= false;
		Bitmap bm 		= null;
		Options opts 	= new Options();
		
		do {
			try {
				log("loadBitmap2 retry:"+retry);
				
				bm = BitmapFactory.decodeFile(getCacheFile_Result().getPath(), opts);
				
				retry = false;
			} catch (OutOfMemoryError e) {
				if(bm != null) {
					if(bm.isRecycled() == false) 
						bm.recycle();
					bm = null;
				}
				if(opts.inSampleSize <= 1) {
					opts.inSampleSize = 2;
				} else {
					opts.inSampleSize *= 2;
				}
				
				retry = true;
			} catch (Throwable e) {
				if(bm != null) {
					if(bm.isRecycled() == false) 
						bm.recycle();
					bm = null;
				}
				
				retry = false;
				throw e;
			}
		} while(retry);
		
		return bm;
	}
	
	public static interface OnDownloadedToCacheFile_Orignal {
		void onDownloaded(RD__BitmapKey pKey);
	}
	OnDownloadedToCacheFile_Orignal mOnDownloadedListener = null;
	public RD__BitmapKey setOnDownloadedToCacheFile_Original(OnDownloadedToCacheFile_Orignal pListener) {
		mOnDownloadedListener = pListener;
		return this;		
	}
	

	public void onDownloaded_To_CacheFile_Original() {
		if(mOnDownloadedListener != null) {
			if(getCacheFile_Original().exists()) {
				mOnDownloadedListener.onDownloaded(this);
			}
		}
	}

}
