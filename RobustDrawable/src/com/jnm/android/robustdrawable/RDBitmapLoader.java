package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;

import com.jnm.android.robustdrawable.RDBitmapDownloader.OnRDBitmapDownloadListener;
import com.jnm.android.robustdrawable.RDBitmapDownloader.RDBitmapDownloaderJob;


/** 
 * 실패하였을 경우에 대해 Listener가 발동해야 되지 않나?
 * 동시에 1개까지 돌릴수 있음
 * 항상 최신의 Requester를 우선으로 처리해주는것으로 (이미 시작한건 끝까지하고)
 * 
 * 추후에 그냥 http에 있는 파일에 대해서도 지원할 필요가 생기게 되면 그때 생각하자
 * @author YMKIM
 */
public final class RDBitmapLoader {
	private static void log(String pLog) {
//		JMLog.e("RD_BitmapLoader] "+pLog);
	}
	
	private static void check() {
		if(sThread_BitmapLoaders == null) {
//			sThread_BitmapLoaders = new Thread_BitmapLoader[2];
			sThread_BitmapLoaders = new Thread_BitmapLoader[Math.max(2, Math.min(1, RobustDrawable__Parent.getMemoryClass()/32))];
			for(int i=0;i<sThread_BitmapLoaders.length;i++) {
				sThread_BitmapLoaders[i] = new Thread_BitmapLoader(i);
				sThread_BitmapLoaders[i].setDaemon(true);
				sThread_BitmapLoaders[i].setPriority(Thread.MIN_PRIORITY+1);
				sThread_BitmapLoaders[i].start();
			}
		}
	}
	
	
	private static Vector<RDBitmapLoaderJob> 	sQueue 					= new Vector<RDBitmapLoaderJob>();
	private static Semaphore 					sSemaphore_Queue 		= new Semaphore(0);
	private static Thread_BitmapLoader[] 		sThread_BitmapLoaders 	= null;
	private static final class Thread_BitmapLoader extends Thread {
		private int 				mThreadIndex;
		private RDBitmapLoaderJob		mCurBitmapLoader;
		public Thread_BitmapLoader(int pThreadIndex) {
			mThreadIndex = pThreadIndex;
		}
		
		@Override
		public void run() {
			super.run();
			while(true) {
				try {
					mCurBitmapLoader = null;
//					log("thread_downloader start1 "+sAccessSema.availablePermits());
					try {
						synchronized (sQueue) {
							if(sQueue.size() > 0) {
								mCurBitmapLoader = sQueue.remove(0);
							}
						}
					} catch (Exception e) {
						RobustDrawable__Parent.ex(e);
					}
//					log("thread_downloader start2 "+sAccessSema.availablePermits());
					
					if(mCurBitmapLoader == null) {
						sSemaphore_Queue.acquire();
						
						try {
//							acquireAccess();
							synchronized (sQueue) {
								if(sQueue.size() > 0) {
									mCurBitmapLoader = sQueue.remove(0);
								}
							}
						} catch (Exception e) {
							RobustDrawable__Parent.ex(e);
						} finally {
//							releaseAccess();
						}
					}
//					log("thread_downloader start3 "+sAccessSema.availablePermits());
					
					if(mCurBitmapLoader == null) 
						continue;
					
					mCurBitmapLoader.mThreadIndex = mThreadIndex;
					mCurBitmapLoader.processJob(false);
				} catch (Throwable e) {
					RobustDrawable__Parent.ex(e);
					try { Thread.sleep(50); } catch (InterruptedException ie) { }
				} finally {
					if(mCurBitmapLoader != null) {
						synchronized (mCurBitmapLoader) {
							mCurBitmapLoader = null;
						}
					}
				}
			}
		}
	}
	public static interface OnRDBitmapLoadListener {
		void onBitmapLoaded(RDBitmapLoaderJob pRequester);
	}
	private static RDBitmapLoaderJob getCurrentBJ(RDBitmapLoaderJob pLoad) {
		synchronized (sQueue) {
			try {
				for(Thread_BitmapLoader t : sThread_BitmapLoaders) {
					if(t != null) {
						synchronized (t) {
							if(t.mCurBitmapLoader != null) {
								if(t.mCurBitmapLoader.mState == BJBitmapLoaderJobState.S1_BitmapLoading) {
									if(	t.mCurBitmapLoader.mKey != null &&
										t.mCurBitmapLoader.mKey.getCacheFile_Result() != null && 
										pLoad != null &&
										pLoad.mKey != null &&
										pLoad.mKey.getCacheFile_Result() != null) {
										
//										JMLog.e("t0:"+t);
//										JMLog.e("t1:"+t.mCurBitmapLoader);
//										JMLog.e("t2:"+t.mCurBitmapLoader.mKey);
//										JMLog.e("t3:"+t.mCurBitmapLoader.mKey.getCacheFile_Result());
//										
//										JMLog.e("p0:"+pLoad);
//										JMLog.e("p1:"+pLoad.mKey);
//										JMLog.e("p2:"+pLoad.mKey.getCacheFile_Result());
										
										if(t.mCurBitmapLoader.mKey.getCacheFile_Result() == pLoad.mKey.getCacheFile_Result()) {
											return t.mCurBitmapLoader;
										}
									}
								}
							}
						}
					}
				}
				
				for(RDBitmapLoaderJob bj : sQueue) {
					if(bj.equals(pLoad)) {
						log("getCurrentBJ return queue "+sQueue.indexOf(bj));
						return bj;
					}
				}
			} catch (Throwable e) {
				RobustDrawable__Parent.ex(e);
			}
		}
		return null;
	}
	static void start(final RD__BitmapKey pBitmapKey, final OnRDBitmapLoadListener pListener) {
		check();
		
		try {
			RDBitmapLoaderJob bj = new RDBitmapLoaderJob();
			bj.mKey = pBitmapKey.clone();
			bj.addOnBitmapLoadListener(pListener);
			bj.start();
		} catch (Throwable e) {
			RobustDrawable__Parent.ex(e);
		}
	}
	
	static enum BJBitmapLoaderJobState {
		S0_InQueue, S1_BitmapLoading, S9_Failed, S9_Successed, S9_Finished,
	}
	static class RDBitmapLoaderJob {
		private void log(String pLog) {
//			RDBitmapLoader.log("RD_BitmapLoaderJob:"+mThreadIndex+"] "+pLog);
//			JMLog.e("RD_BitmapLoaderJob:"+mThreadIndex+"] "+pLog);
		}
		int 			mThreadIndex = -1;
		Bitmap 			mBitmap;
		Throwable 		mThrowable = null;
		
		BJBitmapLoaderJobState 		mState = BJBitmapLoaderJobState.S0_InQueue;
		RD__BitmapKey 	mKey;
		private Bitmap loadBitmapWithCacheFile() throws Throwable {
			Bitmap bm = null;
			File tf = mKey.getCacheFile_Result();
			FileOutputStream fos = null;
			try {
				if(tf.exists() && tf.lastModified() > mKey.getCacheFile_Original().lastModified() && tf.lastModified() > RobustDrawable_Tool.getCurrentTime() - 7*24*60*60*1000) {
					log("loadBitmapWithCacheFile from CacheFile_Result Key:"+mKey);
					bm = createBitmap(tf, mKey.getDstWidth(), mKey.getDstHeight(), mKey);
				} else {
					log("loadBitmapWithCacheFile from CacheFile_Original Key:"+mKey+" ScaleType:"+mKey.getScaleType());
					bm = createBitmap(mKey.getCacheFile_Original(), mKey.getDstWidth(), mKey.getDstHeight(), mKey);
					bm = rotateByEXIF(bm, mKey.getCacheFile_Original());
					
					switch (mKey.getScaleType()) {
					case CENTER_CROP:
						bm = scaleCenterCrop(bm, mKey.getDstWidth(), mKey.getDstHeight());
						break;
					default:
						break;
					}
					bm = mKey.applyOptions(bm);
					
					log("write Bitmap To CacheFile_Result bm("+bm.getWidth()+", "+bm.getHeight()+")");
					fos = new FileOutputStream(tf);
					bm.compress(CompressFormat.PNG, 90, fos);
				}
			} catch (Throwable e) {
				if(bm != null) {
					if(bm.isRecycled() == false) {
						bm.recycle();
					}
					bm = null;
				}

				throw e;
			} finally {
				if(fos != null) { try { fos.close(); } catch (Throwable e2) { } }
			}
			return bm;
		}
		
		private static int MaxRetryCount = 2;
		void processJob(boolean pIsPrompt) {
			for(int i=0;i<MaxRetryCount;i++) {
				try {
					mState = BJBitmapLoaderJobState.S1_BitmapLoading;
					
					log("Runnable Downloader ");
					Bitmap bm = null;
					if(pIsPrompt == false) {
						bm = RobustDrawable__Parent.getCache(mKey);
						if(bm != null) {
							log("exist at RobustDrawable.Cache Key:"+mKey+" Bitmap:"+bm+" Recycled:"+bm.isRecycled());
							if(bm.isRecycled()) {
								bm = null;
							}
						}
					}
					
					if(bm == null) {
						log("create 2.0: "+mKey);
						if(mKey.getKey() != null && mKey.getKeyWithoutSize() != null) {
							if(mKey.isPrompt() == false) {
								log("create 2.2: Key:"+mKey+", len:"+mKey.getCacheFile_Original().length()+" rlen:"+mKey.getCacheFile_Result().length());
								if(pIsPrompt == false && mKey.getCacheFile_Original().exists() == false) {
									mKey.download_To_CacheFile_Original();
									mKey.onDownloaded_To_CacheFile_Original();
								}
								bm = loadBitmapWithCacheFile();
							} else {
								log("create 2.3: Key:"+mKey+", CacheFile_Orignal:"+mKey.getCacheFile_Original().length()+" rlen:"+mKey.getCacheFile_Result().length());
								bm = mKey.loadResultBitmap();
//								bm = mKey.applyOptions(bm);

//								FileOutputStream fos = null;
//								try {
//									fos = new FileOutputStream(mKey.getCacheFile_Result());
//									bm.compress(CompressFormat.PNG, 100, fos);
//								} catch (Throwable e) {
//									throw e;
//								} finally {
//									if(fos != null) { try { fos.close(); } catch (Throwable e2) { } }
//								}
							}
						}
					}
					log("create 3.0: "+mKey);
//					if(bm != null) {
//						throw new IllegalStateException("고의 에러!");
//					}
					
					if(bm != null) {
						if(bm.isRecycled() == false) {
							log("create 4.0: success "+mKey+" "+bm.getWidth()+", "+bm.getHeight());
							mState = BJBitmapLoaderJobState.S9_Successed;
							mBitmap = bm;
							mThrowable = null;
						} else {
							throw new IllegalStateException("Bitmap이 Recycle되어 있넹");
						}
					} else {
						mState = BJBitmapLoaderJobState.S9_Failed;
					}
				} catch (Throwable e) {
					if(mBitmap != null) {
						if(mBitmap.isRecycled() == false) {
							mBitmap.recycle();
						}
					}
					if(mKey.isDeletable_CacheFile_Original())
						mKey.getCacheFile_Original().delete();
					mKey.getCacheFile_Result().delete();
					mBitmap 	= null;
					mThrowable 	= e;
					mState 		= BJBitmapLoaderJobState.S9_Failed;
					
					if(e instanceof OutOfMemoryError) {
						RobustDrawable__Parent.recycleAll();
					}
					
					RobustDrawable__Parent.ex(e);
				} finally {
					if(mState == BJBitmapLoaderJobState.S9_Successed || i >= MaxRetryCount-1) {
						submitBitmapResult();
						break;
					}
				}
			}
		}
		private void submitBitmapResult() {
//			synchronized (mListeners) {
			synchronized (this) {
//				for(final OnRDBitmapLoadListener a : mListeners) {
				for(OnRDBitmapLoadListener a : mListeners) {
//					JMProject_AndroidApp.getHandler().post(new Runnable() {
//						@Override
//						public void run() {
							a.onBitmapLoaded(RDBitmapLoaderJob.this);
//						}
//					});
				}
				mState = BJBitmapLoaderJobState.S9_Finished;
			}
		}
		
		private Bitmap rotateByEXIF(Bitmap pBitmap, File pFile) throws Throwable {
			try {
				ExifInterface exif = new ExifInterface(pFile.getPath());
				
				int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
				log("rotateByEXIF "+orientation);
				
				int degree = 0;
				switch(orientation) {
				case ExifInterface.ORIENTATION_ROTATE_90:
					degree = 90;
					break;
				case ExifInterface.ORIENTATION_ROTATE_270:
					degree = -90;
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					degree = 180;
					break;
				}
				log("rotateByEXIF1 "+degree+", "+pBitmap);
				
				if(degree == 0)
					return pBitmap;
				
				if (pBitmap != null) {
					Matrix matrix = new Matrix();
					matrix.setRotate(degree, (float) pBitmap.getWidth()/2f, (float) pBitmap.getHeight()/2f);
					
					Bitmap bm = null;
					log("rotateByEXIF2 "+pBitmap);
					try {
						bm = Bitmap.createBitmap(pBitmap, 0, 0, pBitmap.getWidth(), pBitmap.getHeight(), matrix, true );
					} catch (Throwable e) {
						if(bm != null) {
							if(bm.isRecycled() == false) {
								bm.recycle();
							}
							bm = null;
						}
						throw e;
					}
					if (bm != null) {
						log("rotateByEXIF3 "+bm);
						if(bm.isRecycled() == false) {
							pBitmap.recycle();
							log("rotateByEXIF4 "+bm+", "+pBitmap);
							pBitmap = bm;
						}
					}
				}
			} catch (Throwable e) {
				RobustDrawable__Parent.ex(e);
			}
			return pBitmap;
		}
		
		private Bitmap scaleCenterCrop(Bitmap source, int newWidth, int newHeight) {
			int sourceWidth = source.getWidth();
			int sourceHeight = source.getHeight();
			
			// Compute the scaling factors to fit the new height and width, respectively.
			// To cover the final image, the final scaling will be the bigger 
			// of these two.
			float xScale = (float) newWidth / sourceWidth;
			float yScale = (float) newHeight / sourceHeight;
			float scale = Math.max(xScale, yScale);
			
			// Now get the size of the source bitmap when scaled
			float scaledWidth = scale * sourceWidth;
			float scaledHeight = scale * sourceHeight;
			
			// Let's find out the upper left coordinates if the scaled bitmap
			// should be centered in the new size give by the parameters
			float left = (newWidth - scaledWidth) / 2;
			float top = (newHeight - scaledHeight) / 2;
			
			// The target rectangle for the new, scaled version of the source bitmap will now
			RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
			
			// Finally, we create a new bitmap of the specified size and draw our new,
			// scaled bitmap onto it.
			Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
			Canvas canvas = new Canvas(dest);
			canvas.drawBitmap(source, null, targetRect, null);
			
			return dest;
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof RDBitmapLoaderJob) {
				if(mKey.equals(((RDBitmapLoaderJob) o).mKey)) {
					return true;
				}
			}
			return super.equals(o);
		}
		
		private Set<OnRDBitmapLoadListener> 	mListeners = Collections.synchronizedSet(new HashSet<OnRDBitmapLoadListener>());
		private RDBitmapLoaderJob addOnBitmapLoadListener(OnRDBitmapLoadListener pListener) {
			synchronized (RDBitmapLoaderJob.this) {
				mListeners.add(pListener);
			}
			return this;
		}

		private RDBitmapLoaderJob start() {
			new Thread() {
				private void stt(RDBitmapLoaderJob pCurBJ) {
					if(pCurBJ.mState == BJBitmapLoaderJobState.S0_InQueue) {
						log("큐에 있으므로 리스너추가하고 큐에서 젤 앞으로 보냄 CurBJ.key:"+pCurBJ.mKey+", key:"+mKey);
						sQueue.remove(pCurBJ);
						synchronized (pCurBJ) {
							pCurBJ.mListeners.addAll(mListeners);
						}
						sQueue.add(0, pCurBJ);
					} else {
						log("작업중이므로 리스너만 추가 CurBJ.key:"+pCurBJ.mKey+", key:"+mKey);
						synchronized (pCurBJ) {
							pCurBJ.mListeners.addAll(mListeners);
						}
					}
				}
				
				@Override
				public void run() {
					super.run();
					
					synchronized (sQueue) {
						try {
							RDBitmapLoaderJob lCurBJ = getCurrentBJ(RDBitmapLoaderJob.this);
							log("AAAA1 "+lCurBJ+", New "+RDBitmapLoaderJob.this+", Key:"+mKey);
							if(lCurBJ != null) {
								stt(lCurBJ);
							} else {
								RDBitmapDownloader.start(mKey, new OnRDBitmapDownloadListener() {
									@Override
									public void onBitmapLoaded(RDBitmapDownloaderJob pRequester) {
										log("RDBitmapDownloader end Request.Key:"+pRequester.mKey+" key:"+RDBitmapLoaderJob.this.mKey);
										
										if(pRequester.mThrowable != null) {
											try {
												mBitmap = null;
												mThrowable = pRequester.mThrowable;
												mState = BJBitmapLoaderJobState.S9_Failed;
												submitBitmapResult();
											} catch (Throwable e) {
												RobustDrawable__Parent.ex(e);
											}
											return; 
										}
										
										RDBitmapLoaderJob lCurBJ = getCurrentBJ(RDBitmapLoaderJob.this);
										log("BBBB "+lCurBJ+", New "+RDBitmapLoaderJob.this);
										if(lCurBJ != null) {
											stt(lCurBJ);
										} else {
											log("큐에 추가 key:"+mKey);
											sQueue.add(0, RDBitmapLoaderJob.this);
											sSemaphore_Queue.release();
										}
									}
								});
							}
						} catch (Exception e) {
							RobustDrawable__Parent.ex(e);
						} finally {
						}
					}
				}
			}
			.start();
			return this;
		}
	}
	
	static Bitmap createBitmap(File pFile, int pMaxWidth, int pMaxHeight, RD__BitmapKey pKey) throws Throwable {
		Bitmap bm = null;
		try {
			int lSampleSize = 1;

			BitmapFactory.Options getsize = new BitmapFactory.Options();
			{
				InputStream fis = new FileInputStream(pFile);
				getsize.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(fis, null, getsize);
				
				lSampleSize = 1;
				
				// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 SampleSize 지정
				if((pMaxWidth*100/pMaxHeight) > (getsize.outWidth*100/getsize.outHeight)) {
					while(getsize.outWidth > pMaxWidth*2) {
						lSampleSize *= 2;
						getsize.outWidth /= 2;
					}
				} else {
					while(getsize.outHeight > pMaxHeight*2) {
						lSampleSize *= 2;
						getsize.outHeight /= 2;
					}
				}
				
				fis.close();
			}
			
			{
				FileInputStream fis = new FileInputStream(pFile);
				log("createBitmap lSampleSize:"+lSampleSize);
				bm = createBitmap(fis, lSampleSize, pMaxWidth, pMaxHeight);
				fis.close();
			}
			
//			log("createBitmap File result bm:"+bm);
			if(bm != null) {
//				log("createBitmap pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" getWidth:"+bm.getWidth()+" getHeight:"+bm.getHeight());
				if(pMaxWidth > 0 && pMaxHeight > 0 && (pMaxWidth < bm.getWidth() || pMaxHeight < bm.getHeight())) {
					Bitmap sbm = null;
					
					log("loaded fit_xy Key:"+pKey+" ScaleType:"+pKey.getScaleType());
					switch (pKey.getScaleType()) {
					case FIT_XY:
						sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, pMaxHeight, true);
						break;
					default: {
						if((pMaxWidth*100/pMaxHeight) > (bm.getWidth()*100/bm.getHeight())) {
							// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
							if(pMaxWidth < bm.getWidth()) {
								sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), true);
							}
						} else {
							// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
							if(pMaxHeight < bm.getHeight()) {
								sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, true);
							}
						}
					} break;
					}
					
					if(sbm != null && sbm != bm) {
						bm.recycle();
						bm = sbm;
					}
				}
			}
		} catch(Throwable e) {
			if(bm != null) {
				if(bm.isRecycled() == false) {
					bm.recycle();
				}
				bm = null;
			}
			throw e;
		}
		return bm;
	}
	private static Bitmap createBitmap(InputStream pIS, int inSampleSize, int pMaxWidth, int pMaxHeight) throws Throwable {
		Bitmap bm = null;
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = inSampleSize;
			
			bm = BitmapFactory.decodeStream(pIS, null, options);
			
			if(bm != null) {
//				log("createBitmap pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" getWidth:"+bm.getWidth()+" getHeight:"+bm.getHeight());
				if(pMaxWidth > 0 && pMaxHeight > 0 && (pMaxWidth < bm.getWidth() || pMaxHeight < bm.getHeight())) {
					Bitmap sbm = null;
					if((pMaxWidth*100/pMaxHeight) > (bm.getWidth()*100/bm.getHeight())) {
						// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
						if(pMaxWidth < bm.getWidth()) {
							sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), true);
						}
					} else {
						// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
						if(pMaxHeight < bm.getHeight()) {
							sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, true);
						}
						
					}
					if(sbm != null && sbm != bm) {
						
						bm.recycle();
						bm = sbm;
					}
				}
			}
		} catch(Throwable e) {
			if(bm != null && bm.isRecycled() == false) 
				bm.recycle();
			bm = null;
			throw e;
		}
		return bm;
	}
}
