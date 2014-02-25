package com.jnm.android.robustdrawable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.widget.ImageView.ScaleType;




public class RD_Resource extends RD__BitmapKey {
	int	mResID;
	
	public RD_Resource(int pResID) {
		mResID = pResID;
		setScaleType(ScaleType.FIT_XY);
		// setDefaultBitmapResource(R.drawable.aa_transparent);
	}
//	@Override
//	public RDBitmapKey clone() throws CloneNotSupportedException {
//		RDBitmapKey c = super.clone();
//		if(c instanceof RD_Resource) {
//			((RD_Resource) c).mResID = mResID;
//		}
//		return c;
//	}
	
	@Override
	protected String getKey() {
		return String.format("ResID-0x%08x-", mResID);
	}
	
	@Override
	protected String getKeyWithoutSize() {
		return getKey();
	}
	
//	@Override
//	void downloadToOriginalFile() throws Throwable {
////		log("downloadToOriginalFile exist:"+getCacheFile_Original().exists());
////		if(getCacheFile_Original().exists())
////			return;
//		
//		InputStream is = null;
//		FileOutputStream os = null;
//		try {
//			is = RobustDrawable.getContext().getResources().openRawResource(mResID);
//			os = new FileOutputStream(getCacheFile_Original());
//			
//			byte[] buffer = new byte[1024];
//			int bytesRead;
//			while ((bytesRead = is.read(buffer)) != -1) {
//				os.write(buffer, 0, bytesRead);
//			}
//		} catch (Throwable e) {
//			throw e;
//		} finally {
//			if(is != null) { try { is.close(); } catch (Throwable e2) { } }
//			if(os != null) { try { os.close(); } catch (Throwable e2) { } }
//		}
//	}
	
	@Override
	public boolean isPrompt() { return true; }
	
	@Override
	public boolean isDeletable_CacheFile_Original() { return false; }
	
	@Override
	protected Bitmap loadResultBitmap() throws Throwable {
		boolean retry 	= false;
		Bitmap bm 		= null;
		Options opts 	= new Options();
		do {
			try {
				bm = BitmapFactory.decodeResource(RobustDrawable__Parent.getContext().getResources(), mResID, opts);
				
				if(getDstWidth()> 0 && getDstHeight() > 0) {
					Bitmap sbm = null;
					switch (getScaleType()) {
					case FIT_XY:
						sbm = Bitmap.createScaledBitmap(bm, getDstWidth(), getDstHeight(), true);
						break;
					default: {
						if((getDstWidth()*100/getDstHeight()) > (bm.getWidth()*100/bm.getHeight())) {
							// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
							if(getDstWidth() < bm.getWidth()) {
								sbm = Bitmap.createScaledBitmap(bm, getDstWidth(), Math.round(((float)getDstWidth())*((float)bm.getHeight())/((float)bm.getWidth())), true);
							}
						} else {
							// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
							if(getDstHeight() < bm.getHeight()) {
								sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)getDstHeight())*((float)bm.getWidth())/((float)bm.getHeight())), getDstHeight(), true);
							}
						}
					} break;
					}
					

					
//					log("loaded fit_xy Key:"+pKey+" ScaleType:"+pKey.getScaleType());
//					switch (pKey.getScaleType()) {
//					case FIT_XY:
//						sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, pMaxHeight, true);
//						break;
//					default: {
//						if((pMaxWidth*100/pMaxHeight) > (bm.getWidth()*100/bm.getHeight())) {
//							// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
//							if(pMaxWidth < bm.getWidth()) {
//								sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), true);
//							}
//						} else {
//							// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
//							if(pMaxHeight < bm.getHeight()) {
//								sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, true);
//							}
//						}
//					} break;
//					}
					
					if(sbm != null && sbm != bm) {
						bm.recycle();
						bm = sbm;
					}
				}
				
				bm = applyOptions(bm);
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
}