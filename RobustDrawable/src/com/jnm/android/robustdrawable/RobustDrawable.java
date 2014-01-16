package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v4.util.LruCache;
import android.util.AttributeSet;
import android.view.Gravity;

import com.jnm.lib.core.JMLog;
import com.jnm.lib.core.structure.JMStructure;
import com.jnm.lib.core.structure.util.JMDate;
import com.sm1.EverySing.R;
import com.sm1.EverySing.lib.Tool_App;
import com.sm1.EverySing.lib.manager.Manager_Bitmap;
import com.sm1.EverySing.lib.manager.Manager_Bitmap.BJ_BitmapLoader;
import com.sm1.EverySing.lib.manager.Manager_Bitmap.Bitmap_KeyType;
import com.sm1.EverySing.lib.manager.Manager_Bitmap.OnCreatedBitmapListener;
import com.smtown.everysing.server.structure.SNArtist;
import com.smtown.everysing.server.structure.SNAudition;
import com.smtown.everysing.server.structure.SNCollection;
import com.smtown.everysing.server.structure.SNSong;
import com.smtown.everysing.server.structure.SNStaticValues;
import com.smtown.everysing.server.structure.SNTheme;
import com.smtown.everysing.server.structure.SNUser;
import com.smtown.everysing.server.structure.SNUser_Audition_Applied;

public class RobustDrawable extends Drawable {
	private static void logS(String pLog) {
//		JMLog.e("ManagedDrawable] "+pLog);
	}
	private void log(String pLog) {
//		JMLog.e("ManagedDrawable:"+this.hashCode()+"] "+pLog);
	}
	
	
	public static class BitmapKey {
		public Bitmap_KeyType mKeyType;
		public String mKey;
		public int mMaxWidth = -1;
		public int mMaxHeight = -1;
		
		public boolean mUseBlur = false;
		
		//		private BitmapKey(Bitmap_KeyType pKeyType, String pKey) {
		//			mKeyType = pKeyType;
		//			mKey = pKey;
		//		}
		
		@Override
		public String toString() {
			return mKeyType.name()+"___"+mKey+"_"+mMaxWidth+"_"+mMaxHeight+"_"+mUseBlur;
		}
		@Override
		public int hashCode() {
			//			return toString().hashCode();
			return (mKeyType.name()+"___"+mKey+"_"+mUseBlur).hashCode();
		}
		@Override
		public boolean equals(Object pO) {
//			logS("equals "+toString()+" "+pO.getClass().getSimpleName()+":"+pO.toString());
			if(pO instanceof BitmapKey) {
				BitmapKey bk = ((BitmapKey) pO);
//				logS("equals "+toString()+" "+pO.toString());
				if(mKeyType == bk.mKeyType && mKey.compareTo(bk.mKey) == 0) {
					if(mUseBlur == bk.mUseBlur) {
						if(mMaxWidth == -1 && mMaxHeight == -1) {
							return true;
						} else if(mMaxWidth == bk.mMaxWidth && mMaxHeight == bk.mMaxHeight) {
							return true;
						}
					}
				}
			}
			return super.equals(pO);
		}
		public static BitmapKey create(Bitmap_KeyType pKeyType, String pKey, int pMaxWidth, int pMaxHeight) {
			logS("BitmapKey create pKeyType:"+pKeyType+" Key:"+pKey);
			if(pKey == null)
				return null;
			BitmapKey ret 	= new BitmapKey();
			ret.mKeyType 	= pKeyType;
			ret.mKey 		= pKey;
			ret.mMaxWidth 	= pMaxWidth;
			ret.mMaxHeight 	= pMaxHeight;
			return ret;
		}
		
	}
	
	private static final 	Bitmap 				DefaultBitmap 	= BitmapFactory.decodeResource(Tool_App.getApplication().getAppContext().getResources(), R.drawable.aa_image);
	private static 			long 				sLastRecycledTime 	= -1L;
	private static 			int 				sTensionCount 	= 1;
	private static LruCache<BitmapKey, Bitmap> 	sBitmapCache 	= new LruCache<BitmapKey, Bitmap>(((ActivityManager)Tool_App.getApplication().getAppContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass()*1024*1024*4/128) {
		@Override
		protected void entryRemoved(boolean pEvicted, BitmapKey pKey, Bitmap pOldValue, Bitmap pNewValue) {
			super.entryRemoved(pEvicted, pKey, pOldValue, pNewValue);
			logS("entryRemoved "+pKey.toString());
			if(pOldValue.isRecycled() == false) {
				pOldValue.recycle();
				
				long now = JMDate.getCurrentTime();
				if(sLastRecycledTime < now - 10*JMDate.TIME_Second || now < sLastRecycledTime) {
					sLastRecycledTime = now;
					new Thread() {
						public void run() {
							super.run();
							logS("recycle ");
							System.gc();
						}
					}
					.start();
				}
			}
		}
		protected int sizeOf(BitmapKey key, Bitmap value) {
			return value.getRowBytes()*sTensionCount;
		}
	};
	
	private static final int DEFAULT_PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG;
	private BitmapState mState;
	private final Rect mDstRect = new Rect();
	
	private boolean mMutated;
	
	
	private RobustDrawable(BitmapState pState) {
		mState = pState;
		
		log("Constructor: mBitmap_IsDefault:"+mState.mBitmap_IsDefault+", sBitmapCache:"+sBitmapCache.size());
		if(mState.mBitmap_IsDefault) {
			setDefaultBitmap();
		} else {
			setBitmap(mState.mBitmapKey_Last, mState.mBitmap, false, false);
		}
	}
	
	
	/******************** Constructors *******************/
	public RobustDrawable(Bitmap_KeyType pKeyType, String pKey) {
		this(pKeyType, pKey, null);
	}
	public RobustDrawable(Bitmap_KeyType pKeyType, String pKey, File pTargetFile) {
		this(new BitmapState_Key(pKeyType, pKey, pTargetFile));
	}
	/******************** Constructors - Specific *******************/
	public static interface IBitmapKey_GetS3Key<T> {
		BitmapKey getBitmapKey(T pObj, int pWidth, int pHeight);
	}
	public RobustDrawable(int pResID) {
		this(Bitmap_KeyType.Resource, Integer.toString(pResID));
		// TODO 이것도 제공해보자!
		// setBitmap(mState.getBitmapKey(-1, -1), pBitmap, pDefault, pShowAnimation)
		mToCreate_WithAnimation = false;
		request(-1, -1);
	}
	public <T extends JMStructure> RobustDrawable(T pJMStructure, IBitmapKey_GetS3Key<T> pIGetS3Key) {
		this(new BitmapState_Structure<T>(pJMStructure, pIGetS3Key));
	}
	public RobustDrawable(SNSong pData) {
		this(new BitmapState_Structure<SNSong>(pData, new IBitmapKey_GetS3Key<SNSong>() {
			@Override
			public BitmapKey getBitmapKey(SNSong pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_JacketImage(pWidth), pWidth, pHeight);
//				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_JacketImage(700), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNArtist pData) {
		this(new BitmapState_Structure<SNArtist>(pData, new IBitmapKey_GetS3Key<SNArtist>() {
			@Override
			public BitmapKey getBitmapKey(SNArtist pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_Image(pWidth), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNAudition pData) {
		this(new BitmapState_Structure<SNAudition>(pData, new IBitmapKey_GetS3Key<SNAudition>() {
			@Override
			public BitmapKey getBitmapKey(SNAudition pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_Wide_Image(pWidth), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNCollection pData) {
		this(new BitmapState_Structure<SNCollection>(pData, new IBitmapKey_GetS3Key<SNCollection>() {
			@Override
			public BitmapKey getBitmapKey(SNCollection pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_Image(pWidth), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNTheme pData) {
		this(new BitmapState_Structure<SNTheme>(pData, new IBitmapKey_GetS3Key<SNTheme>() {
			@Override
			public BitmapKey getBitmapKey(SNTheme pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_Square_Image(pWidth), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNUser_Audition_Applied pData) {
		this(new BitmapState_Structure<SNUser_Audition_Applied>(pData, new IBitmapKey_GetS3Key<SNUser_Audition_Applied>() {
			@Override
			public BitmapKey getBitmapKey(SNUser_Audition_Applied pObj, int pWidth, int pHeight) {
				return BitmapKey.create(Bitmap_KeyType.S3Key_FromCloudFront, pObj.getS3Key_Square_Image(pWidth), pWidth, pHeight);
			}
		}));
	}
	public RobustDrawable(SNUser pData) {
		this(new BitmapState_Structure<SNUser>(pData, new IBitmapKey_GetS3Key<SNUser>() {
			@Override
			public BitmapKey getBitmapKey(SNUser pObj, int pWidth, int pHeight) {
				if(pObj.mUserUUID > SNStaticValues.SNUserUUID_Nobody) {
//					JMLog.e("getBitmapKey User: "+BitmapKey.create(Bitmap_KeyType.S3Key_FromS3, pObj.getS3Key_User_Image(pWidth), pWidth, pHeight));
					return BitmapKey.create(Bitmap_KeyType.S3Key_FromS3, pObj.getS3Key_User_Image(pWidth), pWidth, pHeight);
				} else {
					return BitmapKey.create(Bitmap_KeyType.Resource, Integer.toString(R.drawable.zs_setting_icon_profilethumbnail), pWidth, pHeight);
				}
			}
		}));
	}
	boolean mToCreate_WithAnimation = true;
	@Override
	public void draw(Canvas canvas) {
		boolean lToCreate = false;
		Bitmap bm = null;
		log("draw start =====================================================");
		BitmapKey key = mState.getBitmapKey(canvas.getWidth(), canvas.getHeight());
		log("key: "+key);
		if(key != null) {
			bm = sBitmapCache.get(key);
			log("draw get From Cache: "+key+", "+bm+", "+sBitmapCache.toString());
			if(bm != null) {
				if(bm.isRecycled()) {
					log("draw get From Cache remove "+key);
					sBitmapCache.remove(key);
					bm = null;
				}
			}
		}
		
		if(mState.mBitmap_IsDefault == false) {
			if(mState.mBitmap == null) {
				if(bm == null) {
					log("draw Check: Null");
					setDefaultBitmap();
					lToCreate = true;
				} else {
					log("draw Check: Null But On Cache");
					setBitmap(key, bm, false, false);
				}
			} else {
				if(mState.mBitmap.isRecycled()) {
					if(bm == null) {
						log("draw Check: Recycled ");
						setDefaultBitmap();
						lToCreate = true;
					} else {
						log("draw Check: Recycled But On Cache");
						setBitmap(key, bm, false, false);
					}
				} else {
					log("draw Check: Good cur:"+key+" old:"+mState.mBitmapKey_Last);
					if(key.equals(mState.mBitmapKey_Last) == false) {
						log("draw Check: Good But Need To Create");
						lToCreate = true;
						mToCreate_WithAnimation = false;
					}
				}
			}
		} else {
			if(bm == null) {
				log("draw Check: Default");
				lToCreate = true;
			} else {
				log("draw Check: Default But On Cache");
				setBitmap(key, bm, false, false);
			}
		}
		
		log("drawbitmap BitmapSize:("+mState.mBitmap.getWidth()+","+mState.mBitmap.getHeight()+") mSrcRect:"+mSrcRect+" mDstRect:"+mDstRect+" Canvas("+canvas.getWidth()+","+canvas.getHeight()+")");
		
		mOption.draw_Before(canvas);
		
		if(mState.mShowAnimation) {
			int rtime = (int)(JMDate.getCurrentTime() - mState.mShowAnimation_StartTime_msec);
			if(0 <= rtime && rtime < 500) {
				canvas.drawBitmap(DefaultBitmap, mSrcRect_Default, mDstRect, mState.mPaint);
				
				mState.mPaint.setAlpha(mAlpha * rtime / 500);
			} else {
				mState.mShowAnimation = false;
				mState.mPaint.setAlpha(mAlpha);
			}
			
			canvas.drawBitmap(mState.mBitmap, mSrcRect, mDstRect, mState.mPaint);
			log("draw invaldateSelf mState.mBitmapKey_Last:"+mState.mBitmapKey_Last+", rtime:"+rtime);
			invalidateSelf();
		} else {
			if(mState.mBitmap_IsDefault) {
				mState.mPaint.setAlpha(mAlpha);
				canvas.drawBitmap(DefaultBitmap, mSrcRect_Default, mDstRect, mState.mPaint);
			} else {
				log("draw Bitmap Size "+mState.mBitmap.getWidth()+", "+mState.mBitmap.getHeight());
				canvas.drawBitmap(mState.mBitmap, mSrcRect, mDstRect, mState.mPaint);
			}
		}
		mOption.draw_After(canvas);
		
		if(lToCreate) {
			request(canvas.getWidth(), canvas.getHeight());
		}
		log("draw end =====================================================");
	}
	private Rect mSrcRect = new Rect();
	private Rect mSrcRect_Default = new Rect();
	//private void request(Canvas pCanvas) {
	private void request(int pWidth, int pHeight) {
		BitmapKey bk = mState.getBitmapKey(pWidth, pHeight);
		log("request1 "+pWidth+", "+pHeight+", bk:"+bk);
		if(bk != null) {
			log("request2 "+pWidth+", "+pHeight+", bk:"+bk);
			//Manager_Bitmap.createRequester(sBitmapCache, this, bk.mKeyType, bk.mKey, mState.getTargetFile(), false)
			Manager_Bitmap.createRequester(sBitmapCache, this, bk, mState.getTargetFile(), false, pWidth, pHeight)
			.setOnCreatedListener(new OnCreatedBitmapListener<RobustDrawable>() {
				@Override
				public void onCreated(RobustDrawable pDrawable, BJ_BitmapLoader<RobustDrawable> pRequester) {
					log("onCreated "+pRequester.mBitmap+" "+sBitmapCache.size()+", ");
					if(pRequester.mBitmap == null) {
						if(pRequester.mThrowable != null) {
							if(pRequester.mThrowable instanceof OutOfMemoryError) {
								sTensionCount++;
								sBitmapCache.trimToSize(1);
							}
						}
					} else {
						//BitmapKey key = pDrawable.mState.getBitmapKey(getBounds().width());
//						BitmapKey key = pDrawable.mState.getBitmapKey(pRequester.mMaxWidth, pRequester.mMaxHeight);
//						if(key != null) {
							sBitmapCache.put(pRequester.mKey, pRequester.mBitmap);
							log("onCreated put:"+pRequester.mKey+", ("+pRequester.mBitmap.getWidth()+","+pRequester.mBitmap.getHeight()+") "+", "+sBitmapCache.toString());
							pDrawable.setBitmap(pRequester.mKey, pRequester.mBitmap, false, mToCreate_WithAnimation);
							mToCreate_WithAnimation = false;
							//pDrawable.setBounds(0, 0, pRequester.mBitmap.getWidth(), pRequester.mBitmap.getHeight());
							// pDrawable.setBounds(0, 0, getIntrinsicWidth(), pRequester.mBitmap.getHeight()*getIntrinsicWidth()/pRequester.mBitmap.getWidth());
							
							//							if((getBounds().width()*100/getBounds().height()) > (pRequester.mBitmap.getWidth()*100/pRequester.mBitmap.getHeight())) {
							//								// Bitmap의 세로가 더 긴 경우 
							//								pDrawable.setBounds(0, 0, getBounds().width(), pRequester.mBitmap.getHeight()*getBounds().width()/pRequester.mBitmap.getWidth());
							////								int h = pRequester.mBitmap.getHeight()*getBounds().width()/pRequester.mBitmap.getWidth();
							////								pDrawable.setBounds(0, 0, getBounds().width(), h);
							//							} else {
							//								// Bitmap의 가로가 더 긴 경우
							//								pDrawable.setBounds(0, 0, pRequester.mBitmap.getWidth()*getBounds().height()/pRequester.mBitmap.getHeight(), getBounds().height());
							////								int w = pRequester.mBitmap.getWidth()*getBounds().height()/pRequester.mBitmap.getHeight();
							////								pDrawable.setBounds(0, 0, w, getBounds().height());
							//							}
							//							pDrawable.invalidateSelf();
//						}
					}
				}
			})
//			.start(Manager_Bitmap.CheckImageFileMD5Ratio);
			.start();
		}
	}
	
	//	protected void setBitmap(Bitmap pBitmap, boolean pB, boolean pB2) {
	//		// TODO Auto-generated method stub
	//		
	//	}
	private void setDefaultBitmap() {
		setBitmap(null, DefaultBitmap, true, false);
	}
	private void setBitmap(BitmapKey pBitmapKey, Bitmap pBitmap, boolean pDefault, boolean pShowAnimation) {
		if(pBitmap == null)
			throw new NullPointerException();
		
		if(pDefault == false) {
			mState.mBitmapKey_Last = pBitmapKey;
		}
		mState.mBitmap = pBitmap;
		mState.mBitmap_IsDefault = pDefault;
		
		if(pShowAnimation) {
			mState.mShowAnimation = true;
			mState.mShowAnimation_StartTime_msec = JMDate.getCurrentTime();
		}
		
		computeBitmapSize();
	}
	private void computeSrcRect(Bitmap pBitmap, Rect pTargetRect) {
		if(getBounds().width() > 0 && getBounds().height() > 0) {
			if((getBounds().width()*100/getBounds().height()) > (pBitmap.getWidth()*100/pBitmap.getHeight())) {
				// Bitmap의 세로가 더 긴 경우 
				//			pDrawable.setBounds(0, 0, getBounds().width(), pRequester.mBitmap.getHeight()*getBounds().width()/pRequester.mBitmap.getWidth());
				//int h = mState.mBitmap.getHeight()*getBounds().width()/mState.mBitmap.getWidth();
				//			pDrawable.setBounds(0, 0, getBounds().width(), h);
				//				mSrcRect.set(0, (mState.mBitmap.getHeight()-getBounds().height())/2, getBounds().width(), getBounds().height()+(mState.mBitmap.getHeight()-getBounds().height())/2);
				int h = getBounds().height()*pBitmap.getWidth()/getBounds().width();
				pTargetRect.set(0, (pBitmap.getHeight()-h)/2, pBitmap.getWidth(), h+(pBitmap.getHeight()-h)/2);
			} else {
				// Bitmap의 가로가 더 긴 경우
				//			pDrawable.setBounds(0, 0, pRequester.mBitmap.getWidth()*getBounds().height()/pRequester.mBitmap.getHeight(), getBounds().height());
				//int w = mState.mBitmap.getWidth()*getBounds().height()/mState.mBitmap.getHeight();
				//			pDrawable.setBounds(0, 0, w, getBounds().height());
				//mSrcRect.set(0, 0, w, getBounds().height());
				//				mSrcRect.set((mState.mBitmap.getWidth()-getBounds().width())/2, 0, (mState.mBitmap.getWidth()-getBounds().width())/2+getBounds().width(), getBounds().height());
				int w = getBounds().width()*pBitmap.getHeight()/getBounds().height();
				pTargetRect.set((pBitmap.getWidth()-w)/2, 0, w+(pBitmap.getWidth()-w)/2, pBitmap.getHeight());
			}
		}
		
	}
	private void computeBitmapSize() {
		if(mState.mBitmap != null) {
			//			mState.mBitmapWidth = mState.mBitmap.getScaledWidth(mState.mTargetDensity);
			//			mState.mBitmapHeight = mState.mBitmap.getScaledHeight(mState.mTargetDensity);
			//			log("computeBitmapSize: "+mState.mBitmapWidth+", "+mState.mBitmapHeight+", getBounds:"+getBounds()+", ("+mState.mBitmap.getWidth()+", "+mState.mBitmap.getHeight()+")");
			//			log("computeBitmapSize: getBounds:"+getBounds()+", ("+mState.mBitmap.getWidth()+", "+mState.mBitmap.getHeight()+")");
			//			log("computeBitmapSize: mSrcRect:"+mSrcRect+", mDstRect:"+mDstRect);
			
			computeSrcRect(mState.mBitmap, mSrcRect);
			computeSrcRect(DefaultBitmap, mSrcRect_Default);
			
			
			// 그냥 CenterCrop으로 함
			//			if(getBounds().width() > 0 && getBounds().height() > 0) {
			//				if((getBounds().width()*100/getBounds().height()) > (mState.mBitmap.getWidth()*100/mState.mBitmap.getHeight())) {
			//					// Bitmap의 세로가 더 긴 경우 
			//					//			pDrawable.setBounds(0, 0, getBounds().width(), pRequester.mBitmap.getHeight()*getBounds().width()/pRequester.mBitmap.getWidth());
			//					//int h = mState.mBitmap.getHeight()*getBounds().width()/mState.mBitmap.getWidth();
			//					//			pDrawable.setBounds(0, 0, getBounds().width(), h);
			////					mSrcRect.set(0, (mState.mBitmap.getHeight()-getBounds().height())/2, getBounds().width(), getBounds().height()+(mState.mBitmap.getHeight()-getBounds().height())/2);
			//					int h = getBounds().height()*mState.mBitmap.getWidth()/getBounds().width();
			//					mSrcRect.set(0, (mState.mBitmap.getHeight()-h)/2, mState.mBitmap.getWidth(), h+(mState.mBitmap.getHeight()-h)/2);
			//				} else {
			//					// Bitmap의 가로가 더 긴 경우
			//					//			pDrawable.setBounds(0, 0, pRequester.mBitmap.getWidth()*getBounds().height()/pRequester.mBitmap.getHeight(), getBounds().height());
			//					//int w = mState.mBitmap.getWidth()*getBounds().height()/mState.mBitmap.getHeight();
			//					//			pDrawable.setBounds(0, 0, w, getBounds().height());
			//					//mSrcRect.set(0, 0, w, getBounds().height());
			////					mSrcRect.set((mState.mBitmap.getWidth()-getBounds().width())/2, 0, (mState.mBitmap.getWidth()-getBounds().width())/2+getBounds().width(), getBounds().height());
			//					int w = getBounds().width()*mState.mBitmap.getHeight()/getBounds().height();
			//					mSrcRect.set((mState.mBitmap.getWidth()-w)/2, 0, w+(mState.mBitmap.getWidth()-w)/2, mState.mBitmap.getHeight());
			//				}
			//			}
			
			//			mSrcRect.set(mDstRect);
			
			//		} else {
			//			mState.mBitmapWidth = mState.mBitmapHeight = -1;
			invalidateSelf();
		}
	}
	
	
	
	private static abstract class BitmapState extends ConstantState {
		private Paint 			mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private int 			mChangingConfigurations;
		private int 			mGravity = Gravity.FILL;
		//		private Shader.TileMode mTileModeX;
		//		private Shader.TileMode mTileModeY;
		//		private int 			mTargetDensity = DisplayMetrics.DENSITY_DEFAULT;
		//		private int 			mBitmapWidth;
		//		private int 			mBitmapHeight;
		
		protected BitmapKey		mBitmapKey_Last;
		private Bitmap 			mBitmap;
		private boolean 		mBitmap_IsDefault;
		
		private boolean 		mShowAnimation = false;
		private long 			mShowAnimation_StartTime_msec = -1;
		
		
		BitmapState(BitmapState bitmapState) {
			mPaint = new Paint(bitmapState.mPaint);
			mPaint.setAntiAlias(true);
			mPaint.setDither(true);
			mPaint.setFilterBitmap(true);
			mPaint.setStyle(Style.FILL);
			mChangingConfigurations = bitmapState.mChangingConfigurations;
			mGravity 			= bitmapState.mGravity;
			//			mTileModeX 		= bitmapState.mTileModeX;
			//			mTileModeY 		= bitmapState.mTileModeY;
			//			mTargetDensity 	= bitmapState.mTargetDensity;
			//			mBitmapWidth 	= bitmapState.mBitmapWidth;
			//			mBitmapHeight 	= bitmapState.mBitmapHeight;
			
			mBitmap = bitmapState.mBitmap;
			mBitmap_IsDefault = bitmapState.mBitmap_IsDefault;
			
			mShowAnimation = bitmapState.mShowAnimation;
			mShowAnimation_StartTime_msec = bitmapState.mShowAnimation_StartTime_msec;
		}
		

		public File getTargetFile() {
			return null;
		}
		
		BitmapState(BitmapKey pBitmapKey) {
			mPaint.setAntiAlias(true);
			mPaint.setDither(true);
			mPaint.setFilterBitmap(true);
			mPaint.setStyle(Style.FILL);
			
			if(pBitmapKey != null) {
				mBitmapKey_Last 	= pBitmapKey;
				mBitmap 			= sBitmapCache.get(mBitmapKey_Last);
				mBitmap_IsDefault 	= mBitmap == null;
			} else {
				mBitmapKey_Last 	= null;
				mBitmap 			= DefaultBitmap;
				mBitmap_IsDefault 	= true;
			}
			
//			logS("BitmapState Constructor: mBitmapKey_Last:"+mBitmapKey_Last+", mBitmap_IsDefault:"+mBitmap_IsDefault+", BitmapCacheSize:"+sBitmapCache.size());
			
			//			if(pBitmap == null) {
			//				setDefaultBitmap();
			//			} else {
			//				setBitmap(pBitmap, false, false);
			//			}
		}
		//		BitmapState() {
		//			// setDefaultBitmap();
		//			mPaint.setAntiAlias(true);
		//		}
		
		public BitmapKey getBitmapKey(int pWidth, int pHeight) {
			BitmapKey ret = getBitmapKey_Inner(pWidth, pHeight);
			if(ret != null) {
				ret.mUseBlur = mUseBlur;
			}
			return ret;
		}
		abstract protected BitmapKey getBitmapKey_Inner(int pWidth, int pHeight);
	
		
		@Override
		public Drawable newDrawable() {
			logS("newDrawable1: ");
			return new RobustDrawable(this);
		}
		
		@Override
		public Drawable newDrawable(Resources res) {
			logS("newDrawable2: ");
			return new RobustDrawable(this);
		}
		
		@Override
		public int getChangingConfigurations() {
			return mChangingConfigurations;
		}
		
		protected abstract BitmapState mutate();

		private boolean mUseBlur = false;
		public void setBlur(boolean pUse) {
			mUseBlur = pUse;
			if(mBitmapKey_Last != null) {
				mBitmapKey_Last.mUseBlur = pUse;
			}
		}
	}
	
	private static class BitmapState_Key extends BitmapState {
		private BitmapKey	mBitmapKey;
		private File		mTargetFile;
		
		@Override
		protected BitmapKey getBitmapKey_Inner(int pWidth, int pHeight) {
//			logS("getBitmapKey_Inner mBitmapKey:"+mBitmapKey+" mTargetFile:"+mTargetFile+", "+mBitmapKey_Last+", "+this.toString());
			
			mBitmapKey.mMaxWidth = pWidth;
			mBitmapKey.mMaxHeight = pHeight;
			return mBitmapKey;
		}
		
		public BitmapState_Key(BitmapState_Key pBitmapState_S3Key) {
			super(pBitmapState_S3Key);
//			logS("BitmapState_Key cons "+pBitmapState_S3Key+", "+this.toString());
			
			mBitmapKey = pBitmapState_S3Key.mBitmapKey;
			mTargetFile = pBitmapState_S3Key.mTargetFile;
		}
		
		BitmapState_Key(Bitmap_KeyType pKeyType, String pKey, File pTargetFile) {
			super(BitmapKey.create(pKeyType, pKey, -1, -1));
//			logS("BitmapState_Key cons pKeyType:"+pKeyType+" pKey:"+pKey+", "+this.toString());
			
			mBitmapKey = BitmapKey.create(pKeyType, pKey, -1, -1);
			mTargetFile = pTargetFile;
			
		}
		
		@Override
		protected BitmapState mutate() {
			return new BitmapState_Key(this);
		}
		
		@Override
		public File getTargetFile() {
			return mTargetFile;
		}
	}
	
	private static class BitmapState_Structure<T extends JMStructure> extends BitmapState {
		private T 						mObj;
		private IBitmapKey_GetS3Key<T>	mIGetS3Key;
		@Override
		protected BitmapKey getBitmapKey_Inner(int pWidth, int pHeight) {
			return mIGetS3Key.getBitmapKey(mObj, pWidth, pHeight);
		}
		
		public BitmapState_Structure(BitmapState_Structure<T> pBitmapState_Structure) {
			super(pBitmapState_Structure);
			mObj = pBitmapState_Structure.mObj;
			mIGetS3Key = pBitmapState_Structure.mIGetS3Key;
		}
		BitmapState_Structure(T pObj, IBitmapKey_GetS3Key<T> pIGetS3Key) {
			super(pIGetS3Key.getBitmapKey(pObj, -1, -1));
			//			super();
			mObj = pObj;
			mIGetS3Key = pIGetS3Key;
		}
		
		
		@Override
		protected BitmapState mutate() {
			return new BitmapState_Structure<T>(this);
		}
	}
	
	
	
	/******************** Properties ********************/
	public final Paint getPaint() {
		return mState.mPaint;
	}
	public final Bitmap getBitmap() {
		return mState.mBitmap;
	}
	//	public void setTargetDensity(Canvas canvas) {
	//		setTargetDensity(canvas.getDensity());
	//	}
	//	public void setTargetDensity(DisplayMetrics metrics) {
	//		mState.mTargetDensity = metrics.densityDpi;
	//		computeBitmapSize();
	//	}
	//	
	//	public void setTargetDensity(int density) {
	//		mState.mTargetDensity = density == 0 ? DisplayMetrics.DENSITY_DEFAULT : density;
	//		computeBitmapSize();
	//	}
	
	//	public int getGravity() {
	//		return mState.mGravity;
	//	}
	//	public void setGravity(int gravity) {
	//		mState.mGravity = gravity;
	//		mApplyGravity = true;
	//	}
	
	//	public void setAntiAlias(boolean aa) {
	//		mState.mPaint.setAntiAlias(aa);
	//	}
	
	@Override
	public void setFilterBitmap(boolean filter) {
		mState.mPaint.setFilterBitmap(filter);
	}
	
	public RobustDrawable setBlur() {
		mState.setBlur(true);
		return this;
	}
	
	@Override
	public void setDither(boolean dither) {
		mState.mPaint.setDither(dither);
	}
	
	//	public Shader.TileMode getTileModeX() {
	//		return mState.mTileModeX;
	//	}
	//	
	//	public Shader.TileMode getTileModeY() {
	//		return mState.mTileModeY;
	//	}
	//	
	//	public void setTileModeX(Shader.TileMode mode) {
	//		setTileModeXY(mode, mState.mTileModeY);
	//	}
	//	
	//	public final void setTileModeY(Shader.TileMode mode) {
	//		setTileModeXY(mState.mTileModeX, mode);
	//	}
	//	
	//	public void setTileModeXY(Shader.TileMode xmode, Shader.TileMode ymode) {
	//		BitmapState state = mState;
	//		if (state.mPaint.getShader() == null ||
	//			state.mTileModeX != xmode || state.mTileModeY != ymode) {
	//			state.mTileModeX = xmode;
	//			state.mTileModeY = ymode;
	//			mRebuildShader = true;
	//		}
	//	}
	
	@Override
	public int getChangingConfigurations() {
		log("getChangingConfigurations: "+super.getChangingConfigurations()+" | "+mState.mChangingConfigurations);
		return super.getChangingConfigurations() | mState.mChangingConfigurations;
	}
	
	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		log("onBoundsChange: "+bounds);
		//		mDstRect.set(bounds);
		copyBounds(mDstRect);
		computeBitmapSize();
		invalidateSelf();
		//		mApplyGravity = true;
	}
	@Override
	public int getIntrinsicWidth() {
		return -1;
		//		log("getIntrinsicWidth: "+mState.mBitmapWidth);
		//		if(mState.mBitmap_IsDefault)
		//			return -1;
		//		return mState.mBitmapWidth;
	}
	
	@Override
	public int getIntrinsicHeight() {
		return -1;
		//		log("getIntrinsicHeight: "+mState.mBitmapHeight);
		//		if(mState.mBitmap_IsDefault)
		//			return -1;
		//		return mState.mBitmapHeight;
	}
	
	
	@Override
	public int getOpacity() {
		if (mState.mGravity != Gravity.FILL) {
			return PixelFormat.TRANSLUCENT;
		}
		if((mState.mBitmap == null || mState.mBitmap.hasAlpha() || mState.mPaint.getAlpha() < 255))
			return PixelFormat.TRANSLUCENT;
		else 
			return PixelFormat.OPAQUE;
	}
	
	@Override
	public final ConstantState getConstantState() {
		log("getConstantState: "+mState.mChangingConfigurations+", "+super.getChangingConfigurations());
		mState.mChangingConfigurations = super.getChangingConfigurations();
		return mState;
	}
	
	private int mAlpha = 255;
	@Override
	public void setAlpha(int alpha) {
		mAlpha = alpha;
		mState.mPaint.setAlpha(alpha);
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
		mState.mPaint.setColorFilter(cf);
	}
	
	@Override
	public Drawable mutate() {
		if (!mMutated && super.mutate() == this) {
			mState = mState.mutate();
			mMutated = true;
		}
		return this;
	}
	
	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
		super.inflate(r, parser, attrs);
		
		log("inflate ");
		//        TypedArray a = r.obtainAttributes(attrs, com.android.internal.R.styleable.BitmapDrawable);
		//
		//        final int id = a.getResourceId(com.android.internal.R.styleable.BitmapDrawable_src, 0);
		//        if (id == 0) {
		//            throw new XmlPullParserException(parser.getPositionDescription() +
		//                    ": <bitmap> requires a valid src attribute");
		//        }
		//        final Bitmap bitmap = BitmapFactory.decodeResource(r, id);
		//        if (bitmap == null) {
		//            throw new XmlPullParserException(parser.getPositionDescription() +
		//                    ": <bitmap> requires a valid src attribute");
		//        }
		//        mBitmapState.mBitmap = bitmap;
		//        setBitmap(bitmap);
		//        setTargetDensity(r.getDisplayMetrics());
		//
		//        final Paint paint = mBitmapState.mPaint;
		//        paint.setAntiAlias(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_antialias,
		//                paint.isAntiAlias()));
		//        paint.setFilterBitmap(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_filter,
		//                paint.isFilterBitmap()));
		//        paint.setDither(a.getBoolean(com.android.internal.R.styleable.BitmapDrawable_dither,
		//                paint.isDither()));
		//        setGravity(a.getInt(com.android.internal.R.styleable.BitmapDrawable_gravity, Gravity.FILL));
		//        int tileMode = a.getInt(com.android.internal.R.styleable.BitmapDrawable_tileMode, -1);
		//        if (tileMode != -1) {
		//            switch (tileMode) {
		//                case 0:
		//                    setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		//                    break;
		//                case 1:
		//                    setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		//                    break;
		//                case 2:
		//                    setTileModeXY(Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
		//                    break;
		//            }
		//        }
		//
		//        a.recycle();
	}
	public static void recycleAll() {
		logS("recycleAll");
		sBitmapCache.trimToSize(1);
	}
	
	
	
	@SuppressLint("NewApi")
//	public static Bitmap fastblur(Context context, Bitmap sentBitmap, int radius) {
	public static Bitmap fastblur(Bitmap sentBitmap, int radius) {
		Context context = Tool_App.getContext();
		if (Build.VERSION.SDK_INT > 16) {
			if(0 < radius && radius <= 25) {
				Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
				
				final RenderScript rs = RenderScript.create(context);
				final Allocation input = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
				final Allocation output = Allocation.createTyped(rs, input.getType());
				final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
				script.setRadius(radius);
				script.setInput(input);
				script.forEach(output);
				output.copyTo(bitmap);
				return bitmap;
			}
		}
		
		Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
		
		if (radius < 1) {
			return (null);
		}
		
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		
		int[] pix = new int[w * h];
		logS("pix "+w + " " + h + " " + pix.length);
		bitmap.getPixels(pix, 0, w, 0, 0, w, h);
		
		int wm = w - 1;
		int hm = h - 1;
		int wh = w * h;
		int div = radius + radius + 1;
		
		int r[] = new int[wh];
		int g[] = new int[wh];
		int b[] = new int[wh];
		int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
		int vmin[] = new int[Math.max(w, h)];
		
		int divsum = (div + 1) >> 1;
		divsum *= divsum;
		int dv[] = new int[256 * divsum];
		for (i = 0; i < 256 * divsum; i++) {
			dv[i] = (i / divsum);
		}
		
		yw = yi = 0;
		
		int[][] stack = new int[div][3];
		int stackpointer;
		int stackstart;
		int[] sir;
		int rbs;
		int r1 = radius + 1;
		int routsum, goutsum, boutsum;
		int rinsum, ginsum, binsum;
		
		for (y = 0; y < h; y++) {
			rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
			for (i = -radius; i <= radius; i++) {
				p = pix[yi + Math.min(wm, Math.max(i, 0))];
				sir = stack[i + radius];
				sir[0] = (p & 0xff0000) >> 16;
			sir[1] = (p & 0x00ff00) >> 8;
		sir[2] = (p & 0x0000ff);
		rbs = r1 - Math.abs(i);
		rsum += sir[0] * rbs;
		gsum += sir[1] * rbs;
		bsum += sir[2] * rbs;
		if (i > 0) {
			rinsum += sir[0];
			ginsum += sir[1];
			binsum += sir[2];
		} else {
			routsum += sir[0];
			goutsum += sir[1];
			boutsum += sir[2];
		}
			}
			stackpointer = radius;
			
			for (x = 0; x < w; x++) {
				
				r[yi] = dv[rsum];
				g[yi] = dv[gsum];
				b[yi] = dv[bsum];
				
				rsum -= routsum;
				gsum -= goutsum;
				bsum -= boutsum;
				
				stackstart = stackpointer - radius + div;
				sir = stack[stackstart % div];
				
				routsum -= sir[0];
				goutsum -= sir[1];
				boutsum -= sir[2];
				
				if (y == 0) {
					vmin[x] = Math.min(x + radius + 1, wm);
				}
				p = pix[yw + vmin[x]];
				
				sir[0] = (p & 0xff0000) >> 16;
			sir[1] = (p & 0x00ff00) >> 8;
			sir[2] = (p & 0x0000ff);
			
			rinsum += sir[0];
			ginsum += sir[1];
			binsum += sir[2];
			
			rsum += rinsum;
			gsum += ginsum;
			bsum += binsum;
			
			stackpointer = (stackpointer + 1) % div;
			sir = stack[(stackpointer) % div];
			
			routsum += sir[0];
			goutsum += sir[1];
			boutsum += sir[2];
			
			rinsum -= sir[0];
			ginsum -= sir[1];
			binsum -= sir[2];
			
			yi++;
			}
			yw += w;
		}
		for (x = 0; x < w; x++) {
			rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
			yp = -radius * w;
			for (i = -radius; i <= radius; i++) {
				yi = Math.max(0, yp) + x;
				
				sir = stack[i + radius];
				
				sir[0] = r[yi];
				sir[1] = g[yi];
				sir[2] = b[yi];
				
				rbs = r1 - Math.abs(i);
				
				rsum += r[yi] * rbs;
				gsum += g[yi] * rbs;
				bsum += b[yi] * rbs;
				
				if (i > 0) {
					rinsum += sir[0];
					ginsum += sir[1];
					binsum += sir[2];
				} else {
					routsum += sir[0];
					goutsum += sir[1];
					boutsum += sir[2];
				}
				
				if (i < hm) {
					yp += w;
				}
			}
			yi = x;
			stackpointer = radius;
			for (y = 0; y < h; y++) {
				// Preserve alpha channel: ( 0xff000000 & pix[yi] )
				pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
				
				rsum -= routsum;
				gsum -= goutsum;
				bsum -= boutsum;
				
				stackstart = stackpointer - radius + div;
				sir = stack[stackstart % div];
				
				routsum -= sir[0];
				goutsum -= sir[1];
				boutsum -= sir[2];
				
				if (x == 0) {
					vmin[y] = Math.min(y + r1, hm) * w;
				}
				p = x + vmin[y];
				
				sir[0] = r[p];
				sir[1] = g[p];
				sir[2] = b[p];
				
				rinsum += sir[0];
				ginsum += sir[1];
				binsum += sir[2];
				
				rsum += rinsum;
				gsum += ginsum;
				bsum += binsum;
				
				stackpointer = (stackpointer + 1) % div;
				sir = stack[stackpointer];
				
				routsum += sir[0];
				goutsum += sir[1];
				boutsum += sir[2];
				
				rinsum -= sir[0];
				ginsum -= sir[1];
				binsum -= sir[2];
				
				yi += w;
			}
		}
		
		logS("pix "+ w + " " + h + " " + pix.length);
		bitmap.setPixels(pix, 0, w, 0, 0, w, h);
		return (bitmap);
	}
	
	////////////////// Option ////////////////////////////
	private Option mOption = new Option(this);
	
	private static class Option {
		private final RobustDrawable mManagedDrawable;
		public Option(RobustDrawable pManagedDrawable) {
			mManagedDrawable = pManagedDrawable;
		}
		public void draw_Before(Canvas canvas) {
		}
		public void draw_After(Canvas canvas) {
		}
		protected Paint getMainPaint() {
			return mManagedDrawable.mState.mPaint;
		}
		protected Rect getDstRect() {
			return mManagedDrawable.mDstRect;
		}
	}
	
	//////////////////// WhiteCircle /////////////////////////
	private static class Option_WhiteCircle extends Option {
		private static Bitmap 	sBitmap = null;
		private static Rect 	sRect = null;
		private static final int Size_Stroke = 6;
		
		private Paint	mPaint = null;
		private int		mSaveCount;
		public Option_WhiteCircle(RobustDrawable pManagedDrawable) {
			super(pManagedDrawable);
			if(sBitmap == null) {
				sBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_4444);
				Canvas c = new Canvas(sBitmap);
				Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
				p.setAntiAlias(true);
				p.setColor(Color.RED);
				p.setStyle(Style.FILL);
				//c.drawOval(new RectF(0, 0, 400, 400), p);
				c.drawCircle(200, 200, 200-Size_Stroke/2, p);
				sRect = new Rect(0, 0, 400, 400);
			}
			
			mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mPaint.setFilterBitmap(true);
			mPaint.setAntiAlias(true);
			mPaint.setDither(true);
			mPaint.setColor(Color.WHITE);
			mPaint.setStrokeWidth(Size_Stroke);
			mPaint.setStyle(Style.STROKE);
		}
		@Override
		public void draw_Before(Canvas canvas) {
			super.draw_Before(canvas);

			canvas.drawColor(Color.TRANSPARENT);
			
			mSaveCount = canvas.saveLayer(0, 0, canvas.getWidth(), canvas.getHeight(), null,
				Canvas.MATRIX_SAVE_FLAG |
				Canvas.CLIP_SAVE_FLAG |
				Canvas.HAS_ALPHA_LAYER_SAVE_FLAG |
				Canvas.FULL_COLOR_LAYER_SAVE_FLAG |
				Canvas.CLIP_TO_LAYER_SAVE_FLAG);
		}
		@Override
		public void draw_After(Canvas canvas) {
			super.draw_After(canvas);
			
			getMainPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
			canvas.drawBitmap(sBitmap, sRect, getDstRect(), getMainPaint());
			
			getMainPaint().setXfermode(null);
			canvas.restoreToCount(mSaveCount);
			
			int r = Math.min(getDstRect().width()/2, getDstRect().height()/2);
			canvas.drawCircle(getDstRect().width()/2, getDstRect().height()/2, 
				r-Size_Stroke/2-r*Size_Stroke/400/2, mPaint);
		}
	}
	public RobustDrawable setOption_WhiteCircle() {
		mOption = new Option_WhiteCircle(this);
		return this;
	}
	
	private static class Option_RoundedRect extends Option {
		private static Bitmap 	sBitmap = null;
		private static Rect 	sRect = null;
		private static final int Size_Round = 20;
		private static final int Size_Stroke = 6;
		
		private Paint	mPaint = null;
		private int		mSaveCount;
		public Option_RoundedRect(RobustDrawable pManagedDrawable) {
			super(pManagedDrawable);
			if(sBitmap == null) {
				sBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_4444);
				Canvas c = new Canvas(sBitmap);
				Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
				p.setAntiAlias(true);
				p.setColor(Color.RED);
				//c.drawOval(new RectF(0, 0, 400, 400), p);
				//c.drawRect(new RectF(0, 0, 400, 400), p);
				c.drawRoundRect(new RectF(Size_Stroke/2, Size_Stroke/2, 400-Size_Stroke/2, 400-Size_Stroke/2), Size_Round, Size_Round, p);
				
				sRect = new Rect(0, 0, 400, 400);
			}
			
			mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mPaint.setFilterBitmap(true);
			mPaint.setAntiAlias(true);
			mPaint.setDither(true);
			mPaint.setColor(Color.WHITE);
			mPaint.setStrokeWidth(Size_Stroke);
			mPaint.setStrokeCap(Cap.ROUND);
			mPaint.setStrokeJoin(Join.ROUND);
			mPaint.setStyle(Style.STROKE);
		}
		@Override
		public void draw_Before(Canvas canvas) {
			super.draw_Before(canvas);

			canvas.drawColor(Color.TRANSPARENT);
			
			mSaveCount = canvas.saveLayer(0, 0, canvas.getWidth(), canvas.getHeight(), null,
				Canvas.MATRIX_SAVE_FLAG |
				Canvas.CLIP_SAVE_FLAG |
				Canvas.HAS_ALPHA_LAYER_SAVE_FLAG |
				Canvas.FULL_COLOR_LAYER_SAVE_FLAG |
				Canvas.CLIP_TO_LAYER_SAVE_FLAG );
		}
		@Override
		public void draw_After(Canvas canvas) {
			super.draw_After(canvas);
			
			getMainPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
			canvas.drawBitmap(sBitmap, sRect, getDstRect(), getMainPaint());
			
			getMainPaint().setXfermode(null);
			canvas.restoreToCount(mSaveCount);
			
			// canvas.drawCircle(getDstRect().width() / 2, getDstRect().height() / 2, Math.min(getDstRect().width() / 2, getDstRect().height())-3, mPaint);
			canvas.drawRoundRect(new RectF(Size_Stroke/2, Size_Stroke/2, getDstRect().width()-Size_Stroke/2, getDstRect().height()-Size_Stroke/2), 
				Size_Round*getDstRect().width()/400, Size_Round*getDstRect().height()/400, mPaint);
//			canvas.drawArc(new RectF(0, 0, Size_Round, Size_Round), 270, 90, false, mPaint);
		}
	}
	public RobustDrawable setOption_RoundedRect() {
		mOption = new Option_RoundedRect(this);
		return this;
	}
}

