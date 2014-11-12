package com.jnm.android.robustdrawable;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView.ScaleType;


public class RobustDrawable__Parent<T extends RobustDrawable__Parent<?>> extends Drawable {
	/******************** RobustDrawable *******************/
//	public static boolean isShowLog() { return Tool_App.isMainThread(); }
	public static boolean isShowLog() { return false; }
	
	static void logS(String pLog) {
		if(isShowLog()) {
			RDTool.log("RD] "+pLog);
		}
	}
	private void log(String pLog) {
		if(isShowLog()) {
			RDTool.log("RobustDrawable:"+this.hashCode()+"] Key:"+mKey+" "+pLog);
		}
	}
	
	private static Context sContext = null;
	public static IRobustDrawableGetDefaultBitmapKey sIRobustDrawableGetDefaultBitmapKey;
	
	static Context getContext() {
		if(sContext == null) {
			throw new NullPointerException("Please Init with RobustDrawable.Initializotor");
		}
		return sContext;
	}
	public static class Initializator {
		public Initializator setFileCache_MaxSize(long pSizeInByte) {
			RD__BitmapKey.sFileCache_MaxSize = pSizeInByte;
			return this;
		}
		public Initializator setFileCache_TrimSize(long pSizeInByte) {
			RD__BitmapKey.sFileCache_TrimSize = pSizeInByte;
			return this;
		}
		public Initializator setOnRobustDrawableExceptionListener(OnRobustDrawableExceptionListener pListener) {
			RobustDrawable__Parent.sOnRobustDrawableExceptionListener = pListener;
			return this;
		}
		public Initializator setRobustDrawableGetDefaultBitmapKey(IRobustDrawableGetDefaultBitmapKey pListener) {
			RobustDrawable__Parent.sIRobustDrawableGetDefaultBitmapKey = pListener;
			return this;
		}
	}
	public static interface OnRobustDrawableExceptionListener {
		void onException(Throwable e, String pMessage);
	}
	public static interface IRobustDrawableGetDefaultBitmapKey {
		RD__BitmapKey getDefaultBitmapKey(RD__BitmapKey pBitmapKey);
	}
	public static Initializator Initializer(Context pApplicationContext) {
		sContext = pApplicationContext;
		sBitmapCache = new RDLruCache<RD__BitmapKey, Bitmap>(getMemoryClass()) {
			@Override
			protected void entryRemoved(boolean pEvicted, RD__BitmapKey pKey, Bitmap pOldValue, Bitmap pNewValue) {
				super.entryRemoved(pEvicted, pKey, pOldValue, pNewValue);
				logS("entryRemoved: "+pKey.toString()+" BitmapCache MaxSize:"+sBitmapCache.maxSize()+" BitmapCache Size:"+sBitmapCache.size()+" Size:"+sizeOf(pKey, pOldValue));
				if(pOldValue.isRecycled() == false) {
					synchronized (pOldValue) {
						pOldValue.recycle();	
					}
				}
			}
			protected int sizeOf(RD__BitmapKey key, Bitmap value) {
				int ret = 0;
				if(value != null) {
					ret = (value.getRowBytes() * value.getHeight());
				}
				return ret;
			}
		};

		return new Initializator();
	}
	
	private static int	sMemoryClass = -1;
	static int getMemoryClass() {
		if(sMemoryClass <= 0) {
			Context ac = sContext;
			if(ac != null) {
				ActivityManager am = ((ActivityManager)ac.getSystemService(Context.ACTIVITY_SERVICE));
				if(am != null) {
					logS("MemoryClass: "+am.getMemoryClass());
					sMemoryClass = am.getMemoryClass()*1024*1024/8;
					if(Build.MODEL.toUpperCase().startsWith("SHV")) { 
						sMemoryClass = sMemoryClass * 2;
//						sMemoryClass = sMemoryClass / 1;
					} else if(Build.MODEL.toUpperCase().startsWith("IM-A850")) { 
						sMemoryClass = sMemoryClass / 2;
					} else if(Build.MODEL.toUpperCase().startsWith("LG")) { 
						sMemoryClass = sMemoryClass / 1;
					} else {
						sMemoryClass = sMemoryClass / 1;
					}
				}
			}
		}
		return sMemoryClass;
	}
	
	private static RDLruCache<RD__BitmapKey, Bitmap> sBitmapCache = null; 
	public static Bitmap getCache(RD__BitmapKey pKey) {
		return sBitmapCache.get(pKey);
	}
	static void putCache(RD__BitmapKey pKey, Bitmap pBitmap) {
		sBitmapCache.put(pKey, pBitmap);
	}
	public static void removeCache(RD__BitmapKey pKey) {
		sBitmapCache.remove(pKey);
	}
	
	private RD__BitmapKey 	mKey;
	public RD__BitmapKey 	getKey() { return mKey; }
	private Paint 			mPaint;
	private long 			mShowAnimation_StartTime_msec = -1;
	
	public RobustDrawable__Parent() {
		super();
	}
	public RobustDrawable__Parent init(RD__BitmapKey pKey) {
		mKey = pKey;
		
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setFilterBitmap(true);
		
		log("init ");
		
		return this;
	}
	
	
	private RD__BitmapKey mKey_LastDrawn = null;
	@Override
	public void draw(Canvas canvas) {
		canvas.drawColor(Color.TRANSPARENT);
		if(mKey == null || mKey.getKey() == null) {
			drawDefaultBitmap(canvas);
			return;
		}
		
		Bitmap bm = getCache(mKey);
		if(bm != null) {
			if(bm.isRecycled()) {
				removeCache(mKey);
				bm = null;
			}
		}
		
		if(bm == null) {
			try {
				if(mKey.isAttemptable() && mKey.isPrompt()) {
					RDBitmapLoader.RDBitmapLoaderJob job = new RDBitmapLoader.RDBitmapLoaderJob();
					job.mKey = mKey.clone();
					job.processJob(true);
//					log("isPrompatable "+job.mBitmap);
					if(job.mBitmap != null) {
						bm = job.mBitmap;
						putCache(mKey, bm);
					}
				}
			} catch (Throwable e) {
				RobustDrawable__Parent.ex(e);
				if(bm != null) {
					if(bm.isRecycled() == false) {
						synchronized (bm) {
							bm.recycle();	
						}
					}
					bm = null;
				}
			}
		}
		
		if(bm == null) {
			drawDefaultBitmap(canvas);
			request();
		} else {
			if(mShowAnimation_StartTime_msec > 0) {
				int rtime = (int)(RDTool.getCurrentTime() - mShowAnimation_StartTime_msec);
				if(0 <= rtime && rtime < 500) {
					mPaint.setAlpha(mAlpha);
					drawDefaultBitmap(canvas);
					mPaint.setAlpha(mAlpha * rtime / 500);
				} else {
					mShowAnimation_StartTime_msec = -1;
					mPaint.setAlpha(mAlpha);
				}

				mKey_LastDrawn = mKey;
				drawBitmap(canvas, bm);
				mIsDrawingDefaultBitmap = false;

				invalidateSelf();
			} else {
				mPaint.setAlpha(mAlpha);
				drawBitmap(canvas, bm);
			}
		}
		
//		Debug.stopMethodTracing();
	}
	private void drawBitmap(Canvas canvas, Bitmap bm) {
		if(bm != null) {
			synchronized (bm) {
				if(bm.isRecycled() == false) {
//					log("drawBitmap BitmapSize:("+bm.getWidth()+","+bm.getHeight()+") CanvasSize:("+canvas.getWidth()+", "+canvas.getHeight()+") ");
//					log("drawBitmap SrcRect:"+mKey.getSrcRect(bm)+" ViewRect:"+mKey.getViewRect(canvas));
					
					canvas.drawBitmap(bm, mKey.getSrcRect(bm), mKey.getViewRect(canvas), mPaint);	
				}
			}
		} else {
			request();
		}
	}
	private boolean mIsDrawingDefaultBitmap = false;
	private void drawDefaultBitmap(Canvas canvas) {
		mIsDrawingDefaultBitmap = true;
		if(mKey == null) {
			return;
		}
		
		Bitmap bm = mKey.getDefaultBitmap();
//		log("drawDefaultBitmap "+bm);
		if(bm != null && bm.isRecycled() == false) {
//			log("drawDefaultBitmap draw ("+bm.getWidth()+", "+bm.getHeight()+") "+", mKey.getSrcRect(bm):"+mKey.getSrcRect(bm)+", mKey.getDstRect():"+mKey.getDstRect()+", ("+canvas.getWidth()+","+canvas.getHeight()+")");
//			JMLog.e("drawDefaultBitmap mKey:"+mKey+", bm:("+bm.getWidth()+","+bm.getHeight()+") SrcRect:"+mKey.getSrcRect(bm)+", ViewRect:"+mKey.getViewRect(canvas));
			
			canvas.drawBitmap(bm, mKey.getSrcRect(bm), mKey.getViewRect(canvas), mPaint);
		}
	}
	private void request() {
		log("request "+mKey);
		if(mKey.isAttemptable() == false) {
			return;
		}
		
		RDBitmapLoader.start(mKey, new RDBitmapLoader.OnRDBitmapLoadListener() {
			@Override
			public void onBitmapLoaded(RDBitmapLoader.RDBitmapLoaderJob pRequester) {
				log("onBitmapLoaded "+pRequester.mBitmap+" "+sBitmapCache.size());
				if(pRequester.mBitmap == null) {
					mKey.increaseAttemptCount();
					if(pRequester.mThrowable != null) {
						if(pRequester.mThrowable instanceof FileNotFoundException) {
							return;
						}
						else if(pRequester.mThrowable instanceof OutOfMemoryError) {
							log("onCreated OutOfMemoryError size:"+sBitmapCache.size());
							recycleAll(false, false);
							
							request();
						}
					}
				} else {
					Bitmap cur = getCache(pRequester.mKey);
					if(cur == null) {
						log("onCreated 1 put Key:"+pRequester.mKey+" mKey_LastDrawn:"+mKey_LastDrawn); //+" "+(mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false));
						putCache(pRequester.mKey, pRequester.mBitmap);
						
						if(mKey_LastDrawn == null || mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false) {
							log("onCreated 1 put mIsDrawingDefaultBitmap:"+mIsDrawingDefaultBitmap);
//							if(mKey.isPrompt() == false) {
							if(mIsDrawingDefaultBitmap) {
								mShowAnimation_StartTime_msec = RDTool.getCurrentTime();
							}
//							}
						}
					} else {
						log("onCreated 2 put Key:"+pRequester.mKey+" mKey_LastDrawn:"+mKey_LastDrawn); //+" "+(mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false));
						if(cur.isRecycled()) {
							removeCache(pRequester.mKey);
							
							putCache(pRequester.mKey, pRequester.mBitmap);
							
							if(mKey_LastDrawn == null || mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false) {
								log("onCreated 2 put mIsDrawingDefaultBitmap:"+mIsDrawingDefaultBitmap);
//								if(mKey.isPrompt() == false) {
								if(mIsDrawingDefaultBitmap) {
									mShowAnimation_StartTime_msec = RDTool.getCurrentTime();
								}
//								}
							}
						}
					}
					
					log("onCreate setBitmap:"+pRequester.mKey+", ("+pRequester.mBitmap.getWidth()+","+pRequester.mBitmap.getHeight()+") "+", "+sBitmapCache.toString()+", Recycled:"+pRequester.mBitmap.isRecycled());
				}
				
				RDTool.post(new Runnable() {
					@Override
					public void run() {
						log("invalidateSelf ");
						invalidateSelf();
					}
				});
			}
		});
	}
	
	
	/******************** Properties ********************/
	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		if(mKey == null) {
			return;
		}
		
		log("onBoundsChange: "+bounds+", "+copyBounds());
		mKey.onBoundsChange(copyBounds());
		
		request();
	}
	@Override
	public int getIntrinsicWidth() {
		if(mKey == null) {
			return -1;
		}
		return mKey.getIntrinsicWidth();
	}
	@Override
	public int getIntrinsicHeight() {
		if(mKey == null) {
			return -1;
		}
		return mKey.getIntrinsicHeight();
	}
	
	@Override
	public int getOpacity() {
		if(mPaint.getAlpha() < 255) {
			return PixelFormat.TRANSLUCENT;
		} else {
			return PixelFormat.OPAQUE;
		}
	}
	
//	@Override
//	public final ConstantState getConstantState() {
//		mKey.setChangingConfigurations(super.getChangingConfigurations());
//		return mKey;
//	}
	
	private int mAlpha = 255;
	
	@Override
	public void setAlpha(int alpha) {
		mAlpha = alpha;
		mPaint.setAlpha(alpha);
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}
	
	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
		super.inflate(r, parser, attrs);
		
		log("inflate ");
	}
    private static long sLastRecycleAllTime = 0;
	public static void recycleAll(boolean pForce, boolean pRightNow) {
		logS("recycleAll Force:"+pForce+" RightNow:"+pRightNow);
		if(pForce || RDTool.isNotInInterval(sLastRecycleAllTime, 60 * 1000)) {
			sLastRecycleAllTime = RDTool.getCurrentTime();
			if(pRightNow == false) {
				new Thread() {
					@Override
					public void run() {
						sBitmapCache.evictAll();
					}
				}
				.start();
			} else {
				sBitmapCache.evictAll();
			}
		}
	}
	
	private static OnRobustDrawableExceptionListener	sOnRobustDrawableExceptionListener = null;
	public static void ex(Throwable pE) {
		ex(pE, null);
	}
	public static void ex(Throwable pE, String pMessage) {
		if(sOnRobustDrawableExceptionListener != null) {
			sOnRobustDrawableExceptionListener.onException(pE, pMessage);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public T addOption(RDOption pOption) {
		getKey().addOption(pOption);
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public T setDefaultBitmapResource(int pResID) {
		getKey().setDefaultBitmapResource(pResID);
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
    public T setScaleType(ScaleType pScaleType) {
		getKey().setScaleType(pScaleType);
		return (T) this;
	}
}

