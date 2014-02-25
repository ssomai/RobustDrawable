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

import com.jnm.android.robustdrawable.RDBitmapLoader.RDBitmapLoaderJob;

public class RobustDrawable__Parent extends Drawable {
	/******************** RobustDrawable *******************/
	private static void logS(String pLog) {
//		JMLog.e("RobustDrawable] "+pLog);
	}
	private void log(String pLog) {
//		JMLog.e("RobustDrawable:"+this.hashCode()+"] Key:"+mKey+" "+pLog);
	}
	
	private static Context sContext = null;
	static Context getContext() {
		if(sContext == null)
			throw new NullPointerException("Please Init with RobustDrawable.Initializotor");
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
	}
	public static interface OnRobustDrawableExceptionListener {
		void onException(Throwable e);
	}
	public static Initializator Initializer(Context pApplicationContext) {
		sContext = pApplicationContext;
		sBitmapCache = new RDLruCache<RD__BitmapKey, Bitmap>(getMemoryClass()) {
			@Override
			protected void entryRemoved(boolean pEvicted, RD__BitmapKey pKey, Bitmap pOldValue, Bitmap pNewValue) {
				super.entryRemoved(pEvicted, pKey, pOldValue, pNewValue);
				logS("entryRemoved: "+pKey.toString()+", MaxSize:"+sBitmapCache.maxSize()+", Size:"+sBitmapCache.size());
				if(pOldValue.isRecycled() == false) {
					pOldValue.recycle();
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
					if(	Build.MODEL.toUpperCase().contains("SHV-E160") || 
						Build.MODEL.toUpperCase().contains("SHV-E250") ) {
						sMemoryClass = sMemoryClass / 2;
					} else {
						sMemoryClass = sMemoryClass / 4;
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
	static void removeCache(RD__BitmapKey pKey) {
		sBitmapCache.remove(pKey);
	}
	
	private RD__BitmapKey 	mKey;
	public RD__BitmapKey getKey() { return mKey; }
	private Paint 			mPaint;
	private long 			mShowAnimation_StartTime_msec = -1;
	
	public RobustDrawable__Parent() {
		super();
	}
	public RobustDrawable__Parent init(RD__BitmapKey pKey) {
		mKey = pKey;
		
//		mPaint = new Paint();
		
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setFilterBitmap(true);
		
//		setCallback(this);
		
		return this;
	}
	
	
	private RD__BitmapKey mKey_LastDrawn = null;
	@Override
	public void draw(Canvas canvas) {
		canvas.drawColor(Color.TRANSPARENT);
		
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
					RDBitmapLoaderJob job = new RDBitmapLoaderJob();
					job.mKey = mKey;
					job.processJob(true);
					log("isPrompatable "+job.mBitmap);
					if(job.mBitmap != null) {
						bm = job.mBitmap;
						putCache(mKey, bm);
					}
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
		
		if(bm == null) {
			drawDefaultBitmap(canvas);
			request();
		} else {
			if(mShowAnimation_StartTime_msec > 0) {
				int rtime = (int)(RobustDrawable_Tool.getCurrentTime() - mShowAnimation_StartTime_msec);
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
				invalidateSelf();
			} else {
				mPaint.setAlpha(mAlpha);
				drawBitmap(canvas, bm);
			}
		}
	}
	private void drawBitmap(Canvas canvas, Bitmap bm) {
		if(bm != null && bm.isRecycled() == false) {
//			log("drawBitmap ("+bm.getWidth()+","+bm.getHeight()+") ("+canvas.getWidth()+", "+canvas.getHeight()+") ");
//			log("drawBitmap SrcRect:"+mKey.getSrcRect(bm)+" ViewRect:"+mKey.getViewRect(canvas));
			canvas.drawBitmap(bm, mKey.getSrcRect(bm), mKey.getViewRect(canvas), mPaint);
		} else {
			request();
		}
	}
	private void drawDefaultBitmap(Canvas canvas) {
		Bitmap bm = mKey.getDefaultBitmap();
//		log("drawDefaultBitmap "+bm);
		if(bm != null && bm.isRecycled() == false) {
//			log("drawDefaultBitmap draw ("+bm.getWidth()+", "+bm.getHeight()+") "+", mKey.getSrcRect(bm):"+mKey.getSrcRect(bm)+", mKey.getDstRect():"+mKey.getDstRect()+", ("+canvas.getWidth()+","+canvas.getHeight()+")");
			canvas.drawBitmap(bm, mKey.getSrcRect(bm), mKey.getViewRect(canvas), mPaint);
		}
	}
	private void request() {
//		log("request "+mKey);
		if(mKey.isAttemptable() == false)
			return;
		
		RDBitmapLoader.start(mKey, new RDBitmapLoader.OnRDBitmapLoadListener() {
			@Override
			public void onBitmapLoaded(RDBitmapLoaderJob pRequester) {
//				log("onBitmapLoaded "+pRequester.mBitmap+" "+sBitmapCache.size());
				if(pRequester.mBitmap == null) {
					mKey.increaseAttemptCount();
					if(pRequester.mThrowable != null) {
						if(pRequester.mThrowable instanceof FileNotFoundException) {
							return;
						}
						else if(pRequester.mThrowable instanceof OutOfMemoryError) {
//							log("onCreated OutOfMemoryError size:"+sBitmapCache.size());
							//sBitmapCache.trimToSize(1);
							recycleAll();
							
							request();
							
//							Tool_App.postDelayed(new Runnable() {
//								@Override
//								public void run() {
//									request();
//								}
//							}, 20);
						}
					}
				} else {
					Bitmap cur = getCache(pRequester.mKey);
					if(cur == null) {
//						log("onCreated put:"+pRequester.mKey);
						putCache(pRequester.mKey, pRequester.mBitmap);
						
						if(mKey_LastDrawn == null || mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false) {
							if(mKey.isPrompt() == false) {
								mShowAnimation_StartTime_msec = RobustDrawable_Tool.getCurrentTime();
							}
						}
					} else {
						if(cur.isRecycled()) {
							removeCache(pRequester.mKey);
							
							putCache(pRequester.mKey, pRequester.mBitmap);
							
							if(mKey_LastDrawn == null || mKey_LastDrawn.getCacheFile_Original().equals(pRequester.mKey.getCacheFile_Original()) == false) {
								if(mKey.isPrompt() == false) {
									mShowAnimation_StartTime_msec = RobustDrawable_Tool.getCurrentTime();
								}
							}
						}
					}
					
//					log("onCreate setBitmap:"+pRequester.mKey+", ("+pRequester.mBitmap.getWidth()+","+pRequester.mBitmap.getHeight()+") "+", "+sBitmapCache.toString()+", Recycled:"+pRequester.mBitmap.isRecycled());
				}
				
				RobustDrawable_Tool.post(new Runnable() {
					@Override
					public void run() {
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
		log("onBoundsChange: "+bounds);
		mKey.onBoundsChange(copyBounds());
	}
	@Override
	public int getIntrinsicWidth() {
		return -1;
	}
	@Override
	public int getIntrinsicHeight() {
		return -1;
	}
	
	@Deprecated
	@Override
	public int getOpacity() {
		if(mPaint.getAlpha() < 255)
			return PixelFormat.TRANSLUCENT;
		else 
			return PixelFormat.OPAQUE;
	}
	
	@Override
	public final ConstantState getConstantState() {
		mKey.setChangingConfigurations(super.getChangingConfigurations());
		return mKey;
	}
	
	private int mAlpha = 255;
	
//	private static Bitmap sDefaultBitmap 			= null;
//	static Bitmap getDefaultbitmap() {
//		if(sDefaultBitmap == null || sDefaultBitmap.isRecycled()) {
//			sDefaultBitmap = BitmapFactory.decodeResource(Tool_App.getApplication().getAppContext().getResources(), R.drawable.aa_image);
//		}
//		return sDefaultBitmap;
//	}
//	private static Bitmap sDefaultBitmap_Blured 	= null;
//	static Bitmap getDefaultbitmapBlured() {
//		if(sDefaultBitmap_Blured == null) {
//			sDefaultBitmap_Blured = BitmapFactory.decodeResource(Tool_App.getApplication().getAppContext().getResources(), R.drawable.aa_image_blured);
//		}
//		return sDefaultBitmap_Blured;
//	}
	@Deprecated
	@Override
	public void setAlpha(int alpha) {
		mAlpha = alpha;
		mPaint.setAlpha(alpha);
	}
	
	@Deprecated
	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}
	
	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
		super.inflate(r, parser, attrs);
		
		log("inflate ");
	}
	public static void recycleAll() {
		logS("recycleAll");
		new Thread() {
			@Override
			public void run() {
				sBitmapCache.trimToSize(1);
			}
		}
		.start();
	}
	
	private static OnRobustDrawableExceptionListener	sOnRobustDrawableExceptionListener = null;
	public static void ex(Throwable pE) {
		if(sOnRobustDrawableExceptionListener != null) {
			sOnRobustDrawableExceptionListener.onException(pE);
		}
	}
//	@Override
//	public void invalidateDrawable(Drawable pWho) {
//		log("invalidateDrawable ");
//	}
//	@Override
//	public void scheduleDrawable(Drawable pWho, Runnable pWhat, long pWhen) {
//		log("scheduleDrawable ");
//	}
//	@Override
//	public void unscheduleDrawable(Drawable pWho, Runnable pWhat) {
//		log("unscheduleDrawable ");
//	}
}

