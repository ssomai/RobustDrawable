package com.jnm.android.robustdrawable;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.widget.ImageView.ScaleType;




public class RD_Resource extends RD__BitmapKey {
	private int	mResID;
	public int getResID() {
		return mResID;
	}
	public void setResID(int pResID) {
		mResID = pResID;
    }
	
	public RD_Resource(int pResID) {
//		this(pResID, false);
		this(pResID, true);
	}
	public RD_Resource(int pResID, boolean pIsDefault) {
		mResID = pResID;
		setScaleType(ScaleType.FIT_XY);
//		setScaleType(ScaleType.CENTER_CROP);
//		setScaleType(ScaleType.CENTER_INSIDE);
		mIsDefaultBitmapKey = pIsDefault;
		if(mIsDefaultBitmapKey == false) {
//			setDefaultBitmapResource(R.drawable.aa_transparent);
		}
	}
	
	@Override
	protected String getKey() {
		return String.format("ResID-0x%08x-", mResID);
	}
	
	@Override
	protected String getKeyWithoutSize() {
		return getKey();
	}
	
	@Override
	public boolean equals(Object pO) {
		boolean ret = false;
		if(pO instanceof RD__BitmapKey && this.getClass().isInstance(pO)) {
			RD__BitmapKey bk = ((RD__BitmapKey) pO);
			if(getKeyWithoutSize().compareTo(bk.getKeyWithoutSize()) == 0 && getStringOfOptions().compareTo(bk.getStringOfOptions()) == 0) {
				if(getDstWidth() == bk.getDstWidth() && getDstHeight() == bk.getDstHeight()) {
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

	
	@Override
	public boolean isPrompt() { 
//		return false;
		
		if(onBounded() == false) {
			return false;
		}
		if(getDstWidth() <= 0 || getDstHeight() <= 0) {
			return false;
		}
//		if(mIsMustPrompt) {
//			return true;
//		}
		
////		JMLog.e("isPrompt "+getDstWidth()+">"+(Tool_App.getDisplayWidth()*6/10)+", "+getDstHeight()+">"+(Tool_App.getDisplayHeight()*6/10));
//		if(getDstWidth() > Tool_App.getDisplayWidth()*6/10 && getDstHeight() > Tool_App.getDisplayHeight()*6/10) {
////			JMLog.e("isPrompt "+(getDstWidth() > Tool_App.getDisplayWidth()*8/10)+", "+(getDstHeight() > Tool_App.getDisplayHeight()*8/10));
//			return false;
//		}
		
		if(isHardOption()) {
			return false;
		}
		
		return true; 
	}

	@Override
	public void download_To_CacheFile_Original() throws Throwable {
		InputStream 	is 	= null;
		OutputStream 	os 	= null;
		try {
//			input = RobustDrawable__Parent.getContext().getResources().openRawResource(mResID);
//			long lLastModified = con.getLastModified();
//			log("ContentLength: "+con.getContentLength()+" LastModified: "+new JMDate(lLastModified).toStringForDateTime());
//			
//			int file_total = con.getContentLength();
//			
//			log("파일길이 비교: "+con.getContentLength()+", "+getCacheFile_Original().length()+" Result: "+(lLastModified < getCacheFile_Original().lastModified()));
//			log("getCacheFile_Original: "+getCacheFile_Original().exists());
//			log("캐쉬파일 체크");
				is = RobustDrawable__Parent.getContext().getResources().openRawResource(mResID);
			if(getCacheFile_Original().exists()) {
//				input = RobustDrawable__Parent.getContext().getResources().openRawResource(mResID);
//				log("length:"+getCacheFile_Original().length());
				
//				log("캐쉬파일 체크 "+getCacheFile_Original().length()+" == "+ input.available());
				if(getCacheFile_Original().length() == is.available()) {
//					log("동일함");

					return;
				} else {
					log("삭제");
					getCacheFile_Original().delete();
				}
			}
			
			os = new FileOutputStream(getCacheFile_Original());
			
			byte[] buffer = new byte[8192];
			int read = is.read(buffer);
			while(read >= 0) {
				os.write(buffer, 0, read);
				read = is.read(buffer);
			}
			log("getCacheFile_Original "+getCacheFile_Original().length());
		} catch (Throwable e) {
			throw e;
		} finally {
			log("BJ_Download download =====End");
			if (os != null) { try { os.close(); } catch (Throwable e) { RobustDrawable__Parent.ex(e); } }
			if (is != null) { try { is.close(); } catch (Throwable e) { RobustDrawable__Parent.ex(e); } }
//			if (con != null) { con.disconnect(); }
		}
	}
	
//	@Override
//	protected Bitmap loadResultBitmap() throws Throwable {
////		log(" loadResultBitmap onBounded:"+onBounded()+" getDstWidth:"+getDstWidth()+" getDstHeight:"+getDstHeight());
////		JMLog.e("RDBitmapKey] Class:"+this.getClass().getSimpleName()+
////			" Key:"+getKey()+" Log:"+" loadResultBitmap onBounded:"+onBounded()+" getDstWidth:"+getDstWidth()+" getDstHeight:"+getDstHeight()+"\n"+
////			JMLog.getCurrentThreadStackTrace());
//		
//		if(onBounded() == false) {
//			return null;
//		}
//		if(getDstWidth() <= 0 || getDstHeight() <= 0) {
//			return null;
//		}
//		
//		boolean retry 	= false;
//		Bitmap bm 		= null;
//		Options options 	= new Options();
////		options.inSampleSize = getSampleSize();
//		options.inSampleSize = Manager_Bitmap.calculateSampleSize(getIntrinsicWidth(), getIntrinsicHeight(), getDstWidth(), getDstHeight());
////		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//			options.inPreferredConfig = Config.ARGB_8888;
////		}
//
////		if(getIntrinsicWidth() > 1 && getIntrinsicHeight() > 1 && getDstWidth() > 1 && getDstHeight() > 1) {
////			if(Tool_App.isTablet()) {
////				while(getIntrinsicWidth() /  options.inSampleSize > getDstWidth() * 2f && getIntrinsicHeight() / options.inSampleSize > getDstHeight() * 2f) {
////					options.inSampleSize *= 2;
////				}
////			} else {
////				while(getIntrinsicWidth() /  options.inSampleSize > getDstWidth() * 1.5f && getIntrinsicHeight() / options.inSampleSize > getDstHeight() * 1.5f) {
////					options.inSampleSize *= 2;
////				}
////			}
////		}
//		do {
//			try {
//				bm = BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), mResID, options);
//				
////				log(" loadResultBitmap DstSize:("+getDstWidth()+","+getDstHeight()+") BMSize:("+bm.getWidth()+","+bm.getHeight()+") getScaleType():"+getScaleType());
////				if(getDstWidth() > 0 && getDstHeight() > 0 && (bm.getWidth() > getDstWidth() || bm.getHeight() > getDstHeight())) {
////					Bitmap sbm = null;
////					switch (getScaleType()) {
////					case FIT_XY:
////						sbm = Bitmap.createScaledBitmap(bm, getDstWidth(), getDstHeight(), true);
////						break;
////					default: {
////						if((getDstWidth()*100/getDstHeight()) > (bm.getWidth()*100/bm.getHeight())) {
////							// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
////							if(getDstWidth() < bm.getWidth()) {
////								sbm = Bitmap.createScaledBitmap(bm, getDstWidth(), Math.round(((float)getDstWidth())*((float)bm.getHeight())/((float)bm.getWidth())), true);
////							}
////						} else {
////							// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
////							if(getDstHeight() < bm.getHeight()) {
////								sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)getDstHeight())*((float)bm.getWidth())/((float)bm.getHeight())), getDstHeight(), true);
////							}
////						}
////					} break;
////					}
////					
////					if(sbm != null && sbm != bm) {
////						bm.recycle();
////						bm = sbm;
////					}
////				}
//
//				bm = applyOptions(bm);
//				retry = false;
//			} catch (OutOfMemoryError e) {
//				if(bm != null) {
//					if(bm.isRecycled() == false) 
//						bm.recycle();
//					bm = null;
//				}
//				if(options.inSampleSize <= 1) {
//					options.inSampleSize = 2;
//				} else {
//					options.inSampleSize *= 2;
//				}
//				retry = true;
//			} catch (Throwable e) {
//				if(bm != null) {
//					if(bm.isRecycled() == false) 
//						bm.recycle();
//					bm = null;
//				}
//				
//				retry = false;
//				throw e;
//			}
//		} while(retry);
//		
//		if(bm != null) {
//			log(" loadResultBitmap result "+bm.getWidth()+", "+bm.getHeight());
//		}
//		return bm;
//	}
	
//	private Bitmap get() {
//		Bitmap bm = RobustDrawable__Parent.getCache(this);
//		if(bm != null) {
//			if(bm.isRecycled()) {
//				RobustDrawable__Parent.removeCache(this);
//				bm = null;
//			}
//		}
//		
//		if(bm == null) {
//			try {
//				RDBitmapLoaderJob job = new RDBitmapLoaderJob();
//				job.mKey = clone();
//				job.processJob(true);
//				log("isPrompatable "+job.mBitmap);
//				if(job.mBitmap != null) {
//					bm = job.mBitmap;
//					RobustDrawable__Parent.putCache(this, bm);
//				}
//			} catch (Throwable e) {
//				RobustDrawable__Parent.ex(e);
//				if(bm != null) {
//					if(bm.isRecycled() == false) {
//						synchronized (bm) {
//							bm.recycle();
//						}
//					}
//					bm = null;
//				}
//			}
//		}
//		return bm;
//	}
	
//	private int mIntrinsicWidth = -1, mIntrinsicHeight = -1;
//	private void initIntrinsic() {
//		try {
//			BitmapFactory.Options dimensions = new BitmapFactory.Options(); 
//			dimensions.inJustDecodeBounds = true;
//			BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), mResID, dimensions);
//			mIntrinsicWidth = dimensions.outWidth;
//			mIntrinsicHeight = dimensions.outHeight;
//			
//			// 약간의 꼼수!
//			if(mIntrinsicWidth > Tool_App.getDisplayWidth()) {
//				mIntrinsicHeight = mIntrinsicHeight * Tool_App.getDisplayWidth() / mIntrinsicWidth;
//				mIntrinsicWidth = Tool_App.getDisplayWidth();
//			}
//			if(mIntrinsicHeight > Tool_App.getDisplayHeight()) {
//				mIntrinsicHeight = Tool_App.getDisplayHeight();
//				mIntrinsicWidth = mIntrinsicWidth * Tool_App.getDisplayHeight() / mIntrinsicHeight;
//			}
//		} catch (Throwable e) {
//			RobustDrawable__Parent.ex(e);
//			mIntrinsicWidth = -1;
//			mIntrinsicHeight = -1;
//		}
//	}
	
	@Override
	protected int getIntrinsicWidth() {
//		if(mIntrinsicWidth < 0) {
//			initIntrinsic();
//			log("getIntrinsicWidth 2 "+mIntrinsicWidth);
//		}
//		
//		if(mIntrinsicWidth < 0) {
//			mIntrinsicWidth = super.getIntrinsicWidth();
//			log("getIntrinsicWidth 3 "+mIntrinsicWidth);
//		}
//		return mIntrinsicWidth;
		return -1;
	}
	
	@Override
	protected int getIntrinsicHeight() {
//		if(getScaleType() != ScaleType.FIT_XY) {
//		Bitmap bm = get();
//		if(bm != null) {
//			log("getIntrinsicHeight 1 "+bm.getHeight());
//			return bm.getHeight();
//		}
//		}
//		if(mIntrinsicHeight > 0) {
//			return mIntrinsicHeight;
//		}
//		try {
//			BitmapFactory.Options dimensions = new BitmapFactory.Options(); 
//			dimensions.inJustDecodeBounds = true;
//			BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), mResID, dimensions);
//			log("getIntrinsicHeight 2 "+dimensions.outHeight);
//			return dimensions.outHeight;
//		} catch (Throwable e) {
//			RobustDrawable__Parent.ex(e);
//		}
//		
//		log("getIntrinsicHeight 3 "+super.getIntrinsicHeight());
//		return super.getIntrinsicHeight();
		
		

//		if(mIntrinsicHeight < 0) {
//			initIntrinsic();
////			try {
////				BitmapFactory.Options dimensions = new BitmapFactory.Options(); 
////				dimensions.inJustDecodeBounds = true;
////				BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), mResID, dimensions);
////				mIntrinsicWidth = dimensions.outWidth;
////				mIntrinsicHeight = dimensions.outHeight;
////			} catch (Throwable e) {
////				RobustDrawable__Parent.ex(e);
////				mIntrinsicHeight = -1;
////			}
//			log("getIntrinsicHeight 2 "+mIntrinsicHeight);
//		}
//		
//		if(mIntrinsicHeight < 0) {
//			mIntrinsicHeight = super.getIntrinsicHeight();
//			log("getIntrinsicHeight 3 "+mIntrinsicHeight);
//		}
//		return mIntrinsicHeight;
		
		return -1;
	}

	
}