package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageView.ScaleType;

public abstract class RD__BitmapKey implements Serializable, Cloneable {
	protected void log(String pLog) {
		RDTool.log("RDBitmapKey] Class:"+this.getClass().getSimpleName()+" Key:"+getKey()+" Log:"+pLog);
	}
	
	protected abstract String getKey();
	protected abstract String getKeyWithoutSize();
	
	protected boolean mIsDefaultBitmapKey = false;
	
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

	private static File sDir_Cache = null;
	private synchronized static File getDir_Cache() {
		if(sDir_Cache == null) {
			try {
				sDir_Cache = RobustDrawable__Parent.getContext().getCacheDir();
			} catch (Throwable e) {
				sDir_Cache = null; 
				RobustDrawable__Parent.ex(e);
			}
			if(sDir_Cache != null) {
				if(sDir_Cache.exists() == false) {
					sDir_Cache.getParentFile().mkdirs();
					getDir_IfNotExistMKDir(sDir_Cache.getPath());
					
					if(sDir_Cache.exists() == false) {
						sDir_Cache = null;
					}
				}
			}
		}
		if(sDir_Cache == null) {
			try {
				sDir_Cache = RobustDrawable__Parent.getContext().getDir("Cache2", Context.MODE_PRIVATE);
			} catch (Throwable e) {
				sDir_Cache = null; 
				RobustDrawable__Parent.ex(e);
			}
			if(sDir_Cache != null) {
				if(sDir_Cache.exists() == false) {
					sDir_Cache.getParentFile().mkdirs();
					getDir_IfNotExistMKDir(sDir_Cache.getPath());
					
					if(sDir_Cache.exists() == false) {
						sDir_Cache = null;
					}
				}
			}
		}
		if(sDir_Cache == null) {
			try {
				sDir_Cache = RobustDrawable__Parent.getContext().getFilesDir();
			} catch (Throwable e) {
				sDir_Cache = null; 
				RobustDrawable__Parent.ex(e);
			}
			if(sDir_Cache != null) {
				if(sDir_Cache.exists() == false) {
					sDir_Cache.getParentFile().mkdirs();
					getDir_IfNotExistMKDir(sDir_Cache.getPath());
					
					if(sDir_Cache.exists() == false) {
						sDir_Cache = null;
					}
				}
			}
		}
		if(sDir_Cache != null) {
			sDir_Cache.getParentFile().mkdirs();
			getDir_IfNotExistMKDir(sDir_Cache.getPath());
		}
		return sDir_Cache;
	}
	
	private static File sDir_Cache_RobustDrawable = null;
	protected synchronized static File getDir_Cache_RobustDrawable() {
		if(sDir_Cache_RobustDrawable == null) {
			try {
				sDir_Cache_RobustDrawable = new File(getDir_Cache().getPath()+"/RobustDrawable");
			} catch (Throwable e) {
				sDir_Cache_RobustDrawable = null; 
				RobustDrawable__Parent.ex(e);
			}
		}
		
		if(sDir_Cache_RobustDrawable != null) {
			sDir_Cache_RobustDrawable.getParentFile().mkdirs();
			getDir_IfNotExistMKDir(sDir_Cache_RobustDrawable.getPath());
		}
		
		return sDir_Cache_RobustDrawable;
	}
	private static long 	sCache_LastCheckDateTime = -1;

	static long sFileCache_MaxSize = 30 * 1024 * 1024;
	static long sFileCache_TrimSize = 20 * 1024 * 1024;
	private synchronized static File getFile_Cache(String pName) {
		File ret = null;
		
		if(ret == null) {
			ret = new File(getDir_Cache_RobustDrawable().getPath()+"/"+pName.replaceAll("[,'?:.]", "_"));
		}
			
		if(RDTool.isMainThread() == false && ret != null) {
			ret.getParentFile().mkdirs();
		}
			
		if(RDTool.isMainThread() == false && RDTool.isNotInInterval(sCache_LastCheckDateTime, 3*60*60*1000)) {
			sCache_LastCheckDateTime = RDTool.getCurrentTime();
			
			new Thread() {
				class JMFile {
					File mFile = null;
					long mLastModified = 0;
					public long lastModified() {
						return mLastModified;
					}
					public long length() {
						return mFile.length();
					}
					public void delete() {
						if(mFile != null) {
							if(mFile.exists()) {
								mFile.delete();
							}
						}
					}
				}
				private void listing(File pDir) {
					for(File f : pDir.listFiles()) {
						if(f.isDirectory()) {
							listing(f);
						} else if(f.isFile()) {
							mSum += f.length();
							JMFile nf = new JMFile();
							nf.mFile = f;
							nf.mLastModified = f.lastModified();
							mFiles.add(nf);
						}
					}
				}
				
				private long mSum = 0;
				private ArrayList<JMFile> mFiles = new ArrayList<JMFile>();
				@Override
				public void run() {
					super.run();
					try {
						listing(getDir_Cache_RobustDrawable());
						if(mSum < sFileCache_MaxSize)
							return;
						
						Collections.sort(mFiles, new Comparator<JMFile>() {
							@Override
							public int compare(JMFile pO1, JMFile pO2) {
								if(pO1.lastModified() - pO2.lastModified() < 0) {
									return -1;
								} else if(pO1.lastModified() - pO2.lastModified() > 0) {
									return 1;
								} else {
									return 0;
								}
							}
						});
						
						while(mFiles.size() > 1) {
							JMFile f = mFiles.get(0);
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
	protected boolean isHardOption() {
		for(RDOption opt : mOptions) {
			if(opt.isHardOption()) {
				return true;
			}
		}
		return false;
	}
	
	protected String getStringOfOptions() {
		String ret = "";
			for(int i=0;i<mOptions.size();i++) {
				ret += "_"+mOptions.get(i).getClass().getSimpleName();
			}
		return ret;
	}
	
	private RD__BitmapKey mDefaultBitmapKey = null;
	public RD__BitmapKey getDefaultBitmapKey() {
		if(mIsDefaultBitmapKey == false) {
			if(mDefaultBitmapKey == null) {
				if(onBounded() == false || getDstWidth() <= 0 || getDstHeight() <= 0) {
				} else {
					if(RobustDrawable__Parent.sIRobustDrawableGetDefaultBitmapKey != null) {
						mDefaultBitmapKey = RobustDrawable__Parent.sIRobustDrawableGetDefaultBitmapKey.getDefaultBitmapKey(this);
						if(mDefaultBitmapKey != null) {
							mDefaultBitmapKey.mOptions.clear();
							for(int i=0;i<mOptions.size();i++) {
								mDefaultBitmapKey.mOptions.add(mOptions.get(i));
							}
							mDefaultBitmapKey.onBoundsChange(mBounds);
						}
					}
				}
			}
		}
		return mDefaultBitmapKey;
	}
	public RD__BitmapKey setDefaultBitmapResource(int pResId) {
		setDefaultBitmapKey(new RD_Resource(pResId, true).setScaleType(ScaleType.CENTER_INSIDE));
		return this;
	}
	public RD__BitmapKey setDefaultBitmapKey(RD__BitmapKey pBitmapKey) {
		if(pBitmapKey != null) {
			mDefaultBitmapKey = pBitmapKey;
			mDefaultBitmapKey.mOptions.clear();
			for(int i=0;i<mOptions.size();i++) {
				mDefaultBitmapKey.mOptions.add(mOptions.get(i));
			}
			mDefaultBitmapKey.onBoundsChange(mBounds);
		}
		return this;
	}
	Bitmap getDefaultBitmap() {
		Bitmap bm = null;
		RD__BitmapKey lDefaultBitmapKey = getDefaultBitmapKey();
		if(lDefaultBitmapKey != null) {
			bm = RobustDrawable__Parent.getCache(lDefaultBitmapKey);
			if(bm != null) {
				if(bm.isRecycled() == false) {
					return bm;
				} else {
					bm = null;
				}
			}
			
			if(lDefaultBitmapKey.isPrompt()) {
				try {
					RDBitmapLoader.RDBitmapLoaderJob job = new RDBitmapLoader.RDBitmapLoaderJob();
					job.mKey = lDefaultBitmapKey.clone();
					job.processJob(true);
					bm = job.mBitmap;
					if(bm != null && bm.isRecycled() == false) {
						RobustDrawable__Parent.putCache(lDefaultBitmapKey, bm);
					}
				} catch (Throwable e) {
					RobustDrawable__Parent.ex(e);
					if(bm != null) {
						if(bm.isRecycled() == false) {
							bm.recycle();
						}
						bm = null;
					}
				}
			}
			
			if(bm != null) {
				if(bm.isRecycled() == false) {
					return bm;
				} else {
					bm = null;
				}
			}
			
			RDBitmapLoader.start(lDefaultBitmapKey, new RDBitmapLoader.OnRDBitmapLoadListener() {
				@Override
				public void onBitmapLoaded(RDBitmapLoader.RDBitmapLoaderJob pRequester) {
					if(pRequester.mBitmap == null) {
					} else {
						Bitmap cur = RobustDrawable__Parent.getCache(pRequester.mKey);
						if(cur == null) {
							RobustDrawable__Parent.putCache(pRequester.mKey, pRequester.mBitmap);
						} else {
							if(cur.isRecycled()) {
								RobustDrawable__Parent.removeCache(pRequester.mKey);
								
								RobustDrawable__Parent.putCache(pRequester.mKey, pRequester.mBitmap);
							}
						}
					}
				}
			});
		}
		
		return bm;
	}
	
	public RD__BitmapKey addOption(RDOption pOption) {
		mOptions.add(pOption);
		RD__BitmapKey lDefaultBitmapKey = getDefaultBitmapKey();
		if(lDefaultBitmapKey != null)  {
			lDefaultBitmapKey.addOption(pOption);
		}
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
		if(pO instanceof RD__BitmapKey) {
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

	private boolean mOnBounded = false;
	protected boolean onBounded() {
		return mOnBounded;
	}
	public void setBound(int pWidth, int pHeight) {
		onBoundsChange(new Rect(0, 0, pWidth, pHeight));
	}
	void onBoundsChange(Rect pBounds) {
		if(mDefaultBitmapKey != null) {
			mDefaultBitmapKey.onBoundsChange(pBounds);
		}
			
		mOnBounded = true;
		
		mDstRect.left = pBounds.left;
		mDstRect.top = pBounds.top;
		mDstRect.right = pBounds.right;
		mDstRect.bottom = pBounds.bottom;
		
		log("onBoundsChange 11 mDstRect:"+mDstRect);
		if(mDstRect.width() > MaxBitmapSize_Width) {
			mDstRect.bottom = mDstRect.height()*MaxBitmapSize_Width/mDstRect.width() + mDstRect.top;
			mDstRect.right = MaxBitmapSize_Width + mDstRect.left;
			log("onBoundsChange 12 mDstRect:"+mDstRect);
		}
		if(mDstRect.height() > MaxBitmapSize_Height) {
			mDstRect.right = mDstRect.width()*MaxBitmapSize_Height/mDstRect.height() + mDstRect.left;
			mDstRect.bottom = MaxBitmapSize_Height + mDstRect.top;
			log("onBoundsChange 13 mDstRect:"+mDstRect);
		}
		log("onBoundsChange 20 mDstRect:"+mDstRect);
		
		mViewRect.left = pBounds.left;
		mViewRect.top = pBounds.top;
		mViewRect.right = pBounds.right;
		mViewRect.bottom = pBounds.bottom;
		
		mBounds.left = pBounds.left;
		mBounds.top = pBounds.top;
		mBounds.right = pBounds.right;
		mBounds.bottom = pBounds.bottom;
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
		if(mSrcRect.width() > 0 && mSrcRect.height() > 0 && mBounds.width() > 0 && mBounds.height() > 0) {
			switch (getScaleType()) {
			case CENTER_INSIDE:
			case FIT_CENTER:
				if((mSrcRect.width()*100/mSrcRect.height()) > (mBounds.width()*100/mBounds.height())) {
					log("getViewRect 1 ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+mBounds.width()+", "+mBounds.height()+") ");
					float h = mSrcRect.height()*mBounds.width()/mSrcRect.width();
					mViewRect.set(0, (int)((mBounds.height()-h)/2), mBounds.width(), (int)(h+(mBounds.height()-h)/2));
				} else {
					log("getViewRect 2 ScaleType:"+getScaleType()+" SrcRect:"+mSrcRect+" DstRect:"+mDstRect+" ViewRect:"+mViewRect+" Canvas:("+mBounds.width()+", "+mBounds.height()+") ");
					float w = mSrcRect.width()*mBounds.height()/mSrcRect.height();
					mViewRect.set((int)((mBounds.width()-w)/2), 0, (int)(w+(mBounds.width()-w)/2), mBounds.height());
					log("getViewRect 2 Result "+mViewRect+" w:"+w);
				}
				break;
			default:
				mViewRect.set(0, 0, mBounds.width(), mBounds.height());
				break;
			}
		}
		
		return mViewRect; 
	}
	
	private static final int MaxBitmapSize_Width = RDTool.getDisplayWidth();
	private static final int MaxBitmapSize_Height = RDTool.getDisplayHeight();
	public int getDstWidth() {
		if(mDstRect.width() <= 0) return -1;
		if(mDstRect.width() > MaxBitmapSize_Width) {
			return MaxBitmapSize_Width;
		}
		return mDstRect.width()*80/100;
	}
	public int getDstHeight() {
		if(mDstRect.height() <= 0) return -1;
		if(mDstRect.height() > MaxBitmapSize_Height) { 
			return MaxBitmapSize_Height;
		}
		return mDstRect.height()*80/100;
	}
	
	private ScaleType mScaleType = ScaleType.CENTER_CROP;
	public ScaleType getScaleType() { return mScaleType; }
	public RD__BitmapKey setScaleType(ScaleType pScaleType) {
		mScaleType = pScaleType;
		return this;
	}

//	@Override
//	public Drawable newDrawable() {
//		return new RobustDrawable__Parent().init(this);
//	}
//	
//	@Override
//	public Drawable newDrawable(Resources res) {
//		return new RobustDrawable__Parent().init(this);
//	}
//	
//	private int mChangingConfigurations;
//	@Override
//	public int getChangingConfigurations() { return mChangingConfigurations; }
//	void setChangingConfigurations(int pChangingConfigurations) { mChangingConfigurations = pChangingConfigurations; }

	Bitmap applyOptions(Bitmap pBitmap) throws Throwable {
		Bitmap ret = pBitmap;
		for(int i=0;i<mOptions.size();i++) {
			RDOption o = mOptions.get(i);
			ret = o.applyOption(this, ret);
		}
		return ret;
	}

	protected abstract void download_To_CacheFile_Original() throws Throwable;
	
	public RobustDrawable__Parent create() {
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
		return false;
	}
	protected final Bitmap loadResultBitmap() throws Throwable {
		if(getDstWidth() < 10 || getDstHeight() < 10) {
			return null;
		}
		
		return RDBitmapLoader.createBitmap(getCacheFile_Result(), getDstWidth(), getDstHeight(), this);
	}
	
	public static interface OnDownloadedToCacheFile_Orignal {
		void onDownloaded(RD__BitmapKey pKey);
	}
	OnDownloadedToCacheFile_Orignal mOnDownloadedListener = null;
	public RD__BitmapKey setOnDownloadedToCacheFile_Original(OnDownloadedToCacheFile_Orignal pListener) {
		mOnDownloadedListener = pListener;
		return this;		
	}
	

	protected void onDownloaded_To_CacheFile_Original() {
		if(mOnDownloadedListener != null) {
			if(getCacheFile_Original().exists()) {
				mOnDownloadedListener.onDownloaded(this);
			}
		}
	}

	protected int getIntrinsicWidth() {
		return -1;
	}

	protected int getIntrinsicHeight() {
		return -1;
	}

	void deleteCacheFiles_BecauseThrowed() {
		getCacheFile_Original().delete();
		getCacheFile_Result().delete();
	}

	private static HashMap<String, ReentrantLock> sFileSemas = new HashMap<String, ReentrantLock>();
	private ArrayList<String> getTargetFileSema() {
		ArrayList<String> lFileSema_Keys = new ArrayList<String>();
		if(getCacheFile_Original() != null) {
			lFileSema_Keys.add(getCacheFile_Original().getPath());
		}
		if(getCacheFile_Result() != null) {
			lFileSema_Keys.add(getCacheFile_Result().getPath());
		}
		return lFileSema_Keys;
	}
	void lock_Acquire_CacheFiles() {
		if(RDTool.isMainThread()) 
			return;
		ArrayList<String> lFileSema_Keys = getTargetFileSema();
		
		for(int i=0;i<lFileSema_Keys.size();i++) {
			String fs_key = lFileSema_Keys.get(i);
			ReentrantLock fs_sem = null;
			
			synchronized (sFileSemas) {
				fs_sem = sFileSemas.get(fs_key);
				if(fs_sem == null) {
					fs_sem = new ReentrantLock();
					sFileSemas.put(fs_key, fs_sem);
				}
			}
			fs_sem.lock();
		}
	}
	void lock_Release_CacheFiles() {
		if(RDTool.isMainThread()) 
			return;
		ArrayList<String> lFileSema_Keys = getTargetFileSema();
		for(int i=0;i<lFileSema_Keys.size();i++) {
			String fs_key = lFileSema_Keys.get(i);
			ReentrantLock fs_sem = null;
			
			synchronized (sFileSemas) {
				fs_sem = sFileSemas.get(fs_key);
				if(fs_sem != null) {
					try {
						fs_sem.unlock();
					} catch (Throwable e) {
					}
					if(fs_sem.hasQueuedThreads() == false) {
						sFileSemas.remove(fs_key);
					}
				}
			}
		}
	}
	void doDownload_To_CacheFile_Original() throws Throwable {
		try {
			lock_Acquire_CacheFiles();
			download_To_CacheFile_Original();
			onDownloaded_To_CacheFile_Original();
		} catch (Throwable e) {
			throw e;
		} finally {
			lock_Release_CacheFiles();
		}
	}


}
