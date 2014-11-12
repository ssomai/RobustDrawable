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
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Build;


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
		if(RobustDrawable__Parent.isShowLog()) {
			RDTool.log("RDBitmapLoader] "+pLog);
		}
	}
	
	private static void check() {
		if(sThread_BitmapLoaders == null) {
			sThread_BitmapLoaders = new Thread_BitmapLoader[Math.max(3, Math.min(1, RobustDrawable__Parent.getMemoryClass()/8/2))];
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
		private RDBitmapLoaderJob		mCurBitmapProcessor;
		public Thread_BitmapLoader(int pThreadIndex) {
			mThreadIndex = pThreadIndex;
		}
		
		@Override
		public void run() {
			super.run();
			while(true) {
				try {
					mCurBitmapProcessor = null;
//					log("thread_downloader start1 "+sAccessSema.availablePermits());
					try {
						synchronized (sQueue) {
							if(sQueue.size() > 0) {
								mCurBitmapProcessor = sQueue.remove(0);
							}
						}
					} catch (Exception e) {
						RobustDrawable__Parent.ex(e);
					}
//					log("thread_downloader start2 "+sAccessSema.availablePermits());
					
					if(mCurBitmapProcessor == null) {
						sSemaphore_Queue.acquire();
						
						try {
//							acquireAccess();
							synchronized (sQueue) {
								if(sQueue.size() > 0) {
									mCurBitmapProcessor = sQueue.remove(0);
								}
							}
						} catch (Exception e) {
							RobustDrawable__Parent.ex(e);
						} finally {
//							releaseAccess();
						}
					}
//					log("thread_downloader start3 "+sAccessSema.availablePermits());
					
					if(mCurBitmapProcessor == null) 
						continue;
					
					mCurBitmapProcessor.mThreadIndex = mThreadIndex;
					mCurBitmapProcessor.processJob(false);
				} catch (OutOfMemoryError e) {
					
				} catch (Throwable e) {
					RobustDrawable__Parent.ex(e);
					try { Thread.sleep(50); } catch (InterruptedException ie) { }
				} finally {
					if(mCurBitmapProcessor != null) {
						synchronized (mCurBitmapProcessor) {
							mCurBitmapProcessor = null;
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
					try {
						if(t.mCurBitmapProcessor.mState == BJBitmapLoaderJobState.S1_BitmapLoading) {
							//								if(	t.mCurBitmapLoader.mKey != null &&
							//									t.mCurBitmapLoader.mKey.getCacheFile_Result() != null && 
							//									pLoad != null &&
							//									pLoad.mKey != null &&
							//									pLoad.mKey.getCacheFile_Result() != null) {
							
							if(t.mCurBitmapProcessor.mKey.getCacheFile_Result() == pLoad.mKey.getCacheFile_Result()) {
								return t.mCurBitmapProcessor;
							}
							//								}
						}
					} catch (NullPointerException e) {
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
//			if(Tool_App.isMainThread()) {
//				if(mKey instanceof RD_Resource) {
//					if(((RD_Resource) mKey).getResID() == R.drawable.aa_image_horizontal) {
//						JMLog.e("!!:"+mThreadIndex+"] "+pLog);
//					}
//				}
//			}
			RDBitmapLoader.log("RD_BitmapLoaderJob:"+mThreadIndex+"] "+pLog);
		}
		int 			mThreadIndex = -1;
		Bitmap 			mBitmap;
		Throwable 		mThrowable = null;
		
		BJBitmapLoaderJobState 		mState = BJBitmapLoaderJobState.S0_InQueue;
		RD__BitmapKey 	mKey;
		private Bitmap loadBitmapWithCacheFile() throws Throwable {
			File lCacheFile_Original = mKey.getCacheFile_Original();
			if(lCacheFile_Original.exists() == false) {
				return null;
			}
			Bitmap bm = null;
			File lCacheFile_Result = mKey.getCacheFile_Result();
			FileOutputStream fos = null;
			try {
//				if(mKey.isPrompt()) {
//					log("loadBitmapWithCacheFile from direct Key:"+mKey);
//					bm = mKey.loadResultBitmap();
//				} else 
//				log("loadBitmapWithCacheFile from direct Key:"+mKey);
//				log("loadBitmapWithCacheFile 0 "+lCacheFile_Result.exists()+","+lCacheFile_Original.exists()+","+(lCacheFile_Result.lastModified() > mKey.getCacheFile_Original().lastModified()));
				if(lCacheFile_Result.exists() && 
//					lCacheFile_Result.lastModified() > mKey.getCacheFile_Original().lastModified() && 
//					lCacheFile_Result.lastModified() > JMDate.getCurrentTime() - 7*JMDate.TIME_Day) {
					lCacheFile_Result.lastModified() > mKey.getCacheFile_Original().lastModified()) {
					log("loadBitmapWithCacheFile 1");
					
//					log("loadBitmapWithCacheFile from CacheFile_Result Key:"+mKey);
//					bm = createBitmap(lCacheFile_Result, mKey.getDstWidth(), mKey.getDstHeight(), mKey);

//					JMLog.e("loadBitmapWithCacheFile from CacheFile_Result 1");
					
					{
						FileInputStream fis = new FileInputStream(lCacheFile_Result);
//						log("createBitmap Key:"+pKey+" lSampleSize:"+lSampleSize);
//						bm = createBitmap(fis, lSampleSize, pMaxWidth, pMaxHeight);

//						BitmapFactory.Options options = new BitmapFactory.Options();
//						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//							options.inPreferredConfig = Config.ARGB_8888;
//						}
//						bm = BitmapFactory.decodeStream(fis, null, options);
						bm = BitmapFactory.decodeStream(fis);
						fis.close();
					}
				} else {
					log("loadBitmapWithCacheFile 2.0");
//					JMLog.e("loadBitmapWithCacheFile from CacheFile_Original Key:"+mKey+" ScaleType:"+mKey.getScaleType()+" Dst:("+mKey.getDstWidth()+","+mKey.getDstHeight()+")");
					bm = createBitmap(mKey.getCacheFile_Original(), mKey.getDstWidth(), mKey.getDstHeight(), mKey);
					log("loadBitmapWithCacheFile from CacheFile_Original 1 bm("+bm.getWidth()+","+bm.getHeight()+")");
//					log("loadBitmapWithCacheFile 2.1");
					bm = rotateByEXIF(bm, mKey.getCacheFile_Original());
					log("loadBitmapWithCacheFile from CacheFile_Original 2 bm("+bm.getWidth()+","+bm.getHeight()+")");
					
					switch (mKey.getScaleType()) {
					case CENTER_CROP:
						bm = scaleCenterCrop(bm, mKey.getDstWidth(), mKey.getDstHeight());
						break;
					default:
						break;
					}
//					log("loadBitmapWithCacheFile 2.2");
					log("loadBitmapWithCacheFile from CacheFile_Original 3 bm("+bm.getWidth()+","+bm.getHeight()+")");
					
					bm = mKey.applyOptions(bm);
					
//					log("write Bitmap To CacheFile_Result bm("+bm.getWidth()+","+bm.getHeight()+")");
					fos = new FileOutputStream(lCacheFile_Result);
					bm.compress(CompressFormat.PNG, 90, fos);
					log("loadBitmapWithCacheFile 2.3");
				}
			} catch (Throwable e) {
				if(bm != null) {
					if(bm.isRecycled() == false) {
//						synchronized (bm) {
							bm.recycle();							
//						}
					}
					bm = null;
				}

				throw e;
			} finally {
				if(fos != null) { try { fos.close(); } catch (Throwable e) { } }
				
				if(bm == null) {
					mKey.deleteCacheFiles_BecauseThrowed();
//					mKey.getCacheFile_Original().delete();
//					mKey.getCacheFile_Result().delete();
				}
			}
			return bm;
		}
		
		
		private static final int MaxRetryCount = 2;
		void processJob(boolean pIsPrompt) {
			long start = RDTool.getCurrentTime();
				log("processJob 1 Key:"+mKey);
			
			for(int i=0;i<MaxRetryCount;i++) {
				try {
					mKey.lock_Acquire_CacheFiles();
					mState = BJBitmapLoaderJobState.S1_BitmapLoading;
					
					log("Runnable Loader "+i);
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
						log("create 2.0: ");
//						log("create 2.0: "+mKey+" IsMainThread:"+Tool_App.isMainThread());
						if(mKey.getKey() != null && mKey.getKeyWithoutSize() != null) {
//							if(mKey.isPrompt() == false) {
//								log("create 2.2: Key:"+mKey+", len:"+mKey.getCacheFile_Original().length()+" rlen:"+mKey.getCacheFile_Result().length()+" pIsPrompt:"+pIsPrompt);
//								if(pIsPrompt == false) {
//									if(mKey.getCacheFile_Original().exists() == false) {
//									log("create 2.2.1: ");
//									mKey.download_To_CacheFile_Original();
//									mKey.onDownloaded_To_CacheFile_Original();
									mKey.doDownload_To_CacheFile_Original();
									log("create 2.2.1: ");
//									} 
//								}
								bm = loadBitmapWithCacheFile();
//							} else {
//								log("create 2.3: Key:"+mKey+", CacheFile_Orignal:"+mKey.getCacheFile_Original().length()+" rlen:"+mKey.getCacheFile_Result().length());
//								bm = mKey.loadResultBitmap();
////								bm = mKey.applyOptions(bm);
//							}
						}
					}
					log("create 3.0: "+mKey+" "+mKey.getDstWidth()+", "+mKey.getDstHeight()+" "+mKey.getDstRect()+", "+mKey.onBounded());
					
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
					mKey.deleteCacheFiles_BecauseThrowed();
//					mKey.getCacheFile_Result().delete();
					mBitmap 	= null;
					mThrowable 	= e;
					mState 		= BJBitmapLoaderJobState.S9_Failed;
					
					if(e instanceof OutOfMemoryError) {
						RobustDrawable__Parent.recycleAll(true, true);
					}
					
					RobustDrawable__Parent.ex(e, "Key: "+mKey.toString());
				} finally {
					mKey.lock_Release_CacheFiles();
					if(mState == BJBitmapLoaderJobState.S9_Successed || i >= MaxRetryCount-1) {
						submitBitmapResult();
						break;
					}
				}
			}
			
				log("processJob End "+(RDTool.getCurrentTime()-start)+" Key:"+mKey);
		}
		private void submitBitmapResult() {
			synchronized (mListeners) {
//			synchronized (this) {
//				for(final OnRDBitmapLoadListener a : mListeners) {
				log("submitBitmapResult Listeners.Size:"+mListeners.size()+" State:"+mState);
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
						bm = Bitmap.createBitmap(pBitmap, 0, 0, pBitmap.getWidth(), pBitmap.getHeight(), matrix, true);
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
						if(bm.isRecycled() == false) {
							pBitmap.recycle();
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
			synchronized (mListeners) {
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
								RDBitmapDownloader.start(mKey, new RDBitmapDownloader.OnRDBitmapDownloadListener() {
									@Override
									public void onBitmapLoaded(RDBitmapDownloader.RDBitmapDownloaderJob pRequester) {
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
											synchronized (sQueue) {
												sQueue.add(0, RDBitmapLoaderJob.this);
                                            }
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
	

//	protected static int getSampleSize(int pSrcWidth, int pSrcHeight, int pMaxWidth, int pMaxHeight) {
//		int ret = 1;
//		if(pSrcWidth > 1 && pSrcHeight > 1 && pMaxWidth > 1 && pMaxHeight > 1) {
//			if(Tool_App.isTablet() || Tool_App.getDisplayMetrics().densityDpi <= DisplayMetrics.DENSITY_HIGH) {
//				while((pSrcWidth / ret > pMaxWidth * 2f) && (pSrcHeight / ret > pMaxHeight * 2f)) {
//					ret *= 2;
//				}
//			} else {
//				while((pSrcWidth /  ret > pMaxWidth * 1.5f) && (pSrcHeight / ret > pMaxHeight * 1.5f)) {
//					ret *= 2;
//				}
//			}
//		}
//		return ret;
//	}
	static Bitmap createBitmap(File pFile, int pMaxWidth, int pMaxHeight, RD__BitmapKey pKey) throws Throwable {
		Bitmap bm = null;
		try {
			log("createBitmap IsMainThread:"+RDTool.isMainThread());
			int lSampleSize = 1;

			BitmapFactory.Options getsize = new BitmapFactory.Options();
			{
				InputStream fis = new FileInputStream(pFile);
				getsize.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(fis, null, getsize);
				
				lSampleSize = RDBitmapLoader.calculateSampleSize(getsize.outWidth, getsize.outHeight, pMaxWidth, pMaxHeight);
				
				
//				lSampleSize = 1;
//				
//				// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 SampleSize 지정
//				if((pMaxWidth*100/pMaxHeight) > (getsize.outWidth*100/getsize.outHeight)) {
//					while(getsize.outWidth > pMaxWidth*2) {
//						lSampleSize *= 2;
//						getsize.outWidth /= 2;
//					}
//				} else {
//					while(getsize.outHeight > pMaxHeight*2) {
//						lSampleSize *= 2;
//						getsize.outHeight /= 2;
//					}
//				}
				
				fis.close();
			}
			
			{
				FileInputStream fis = new FileInputStream(pFile);
				log("createBitmap Key:"+pKey+" lSampleSize:"+lSampleSize);
//				bm = createBitmap(fis, lSampleSize, pMaxWidth, pMaxHeight);

				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = lSampleSize;
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					options.inPreferredConfig = Config.ARGB_8888;
				}
				
				bm = BitmapFactory.decodeStream(fis, null, options);
				fis.close();
			}
			
			log("createBitmap Key:"+pKey+" File result bm:"+bm);
			if(bm == null) {
				throw new NullPointerException(" pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" pFile:"+pFile+" Exist:"+pFile.exists()+" Length:"+pFile.length());
			}
			
			log("createBitmap Key:"+pKey+" Color:"+bm.getConfig().name()+" pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" getWidth:"+bm.getWidth()+" getHeight:"+bm.getHeight());
			if(pMaxWidth > 0 && pMaxHeight > 0 && (pMaxWidth < bm.getWidth() || pMaxHeight < bm.getHeight())) {
				Bitmap sbm = null;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
					bm.setHasAlpha(true);
				}
				
				log("loaded fit_xy Key:"+pKey+" ScaleType:"+pKey.getScaleType());
				switch (pKey.getScaleType()) {
				case FIT_XY: {
					sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, pMaxHeight, true);
				} break;
				default: {
					if((pMaxWidth*100/pMaxHeight) > (bm.getWidth()*100/bm.getHeight())) {
						// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
						if(pMaxWidth < bm.getWidth()) {
							sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), true);
//							sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), false);
						}
					} else {
						// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
						if(pMaxHeight < bm.getHeight()) {
							sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, true);
//							sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, false);
						}
					}
				} break;
				}
				
				if(sbm != null && sbm != bm) {
					bm.recycle();
					bm = sbm;
				}
			}
		} catch(Throwable e) {
			if(bm != null) {
				if(bm.isRecycled() == false) {
//					synchronized (bm) {
						bm.recycle();
//					}
				}
				bm = null;
			}
			throw e;
		}
		log("createBitmap Key:"+pKey+" result bm:"+bm);
		if(bm != null) {
			log("createBitmap Key:"+pKey+" bm Config:"+bm.getConfig().name());
		}
		return bm;
	}
//	private static Bitmap createBitmap(InputStream pIS, int inSampleSize, int pMaxWidth, int pMaxHeight) throws Throwable {
//		Bitmap bm = null;
//		try {
//			BitmapFactory.Options options = new BitmapFactory.Options();
//			options.inSampleSize = inSampleSize;
//			
//			bm = BitmapFactory.decodeStream(pIS, null, options);
//			
////			log("createBitmap InputStream bm:"+bm);
////			if(bm == null) {
////				throw new NullPointerException(" "+pIS.available()+" "+inSampleSize);
////			}
////			
////			log("createBitmap pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" getWidth:"+bm.getWidth()+" getHeight:"+bm.getHeight());
////			if(pMaxWidth > 0 && pMaxHeight > 0 && (pMaxWidth < bm.getWidth() || pMaxHeight < bm.getHeight())) {
////				Bitmap sbm = null;
////				if((pMaxWidth*100/pMaxHeight) > (bm.getWidth()*100/bm.getHeight())) {
////					// Bitmap의 세로가 더 긴 경우, 목표의 가로에 맞춰서 스케일
////					if(pMaxWidth < bm.getWidth()) {
////						sbm = Bitmap.createScaledBitmap(bm, pMaxWidth, Math.round(((float)pMaxWidth)*((float)bm.getHeight())/((float)bm.getWidth())), true);
////					}
////				} else {
////					// Bitmap의 가로가 더 긴 경우, 목표의 세로에 맞춰서 스케일
////					if(pMaxHeight < bm.getHeight()) {
////						sbm = Bitmap.createScaledBitmap(bm, Math.round(((float)pMaxHeight)*((float)bm.getWidth())/((float)bm.getHeight())), pMaxHeight, true);
////					}
////					
////				}
////				if(sbm != null && sbm != bm) {
//////					synchronized (bm) {
////						bm.recycle();							
//////					}
////					bm = sbm;
////				}
////			}
//		} catch(Throwable e) {
//			if(bm != null && bm.isRecycled() == false) {
////				synchronized (bm) {
//					bm.recycle();					
////				}
//			}
//			bm = null;
//			throw e;
//		}
//		return bm;
//	}

	public static int calculateSampleSize(int pSrcWidth, int pSrcHeight, int pMaxWidth, int pMaxHeight) {
    		int ret = 1;
    		if(pSrcWidth > 1 && pSrcHeight > 1 && pMaxWidth > 1 && pMaxHeight > 1) {
    //			if(Tool_App.isTablet() || Tool_App.getDisplayMetrics().densityDpi <= DisplayMetrics.DENSITY_HIGH) {
    //				while(pSrcWidth / ret > pMaxWidth * 2f) {
    //					ret *= 2;
    //				}
    //				while(pSrcHeight / ret > pMaxHeight * 3f) {
    //					ret *= 2;
    //				}
    //			} else {
    //				while(pSrcWidth / ret > pMaxWidth * 2f) {
    //					ret *= 2;
    //				}
    //				while(pSrcHeight / ret > pMaxHeight * 3f) {
    //					ret *= 2;
    //				}
    //			}
    			
    			while(pSrcWidth / ret > pMaxWidth * 3f) {
    				ret *= 2;
    			}
    			while(pSrcHeight / ret > pMaxHeight * 3f) {
    				ret *= 2;
    			}
    			ret = Math.min(ret, 16);
    			
    		}
    		log("calculateSampleSize pSrcWidth:"+pSrcWidth+" pSrcHeight:"+pSrcHeight+" pMaxWidth:"+pMaxWidth+" pMaxHeight:"+pMaxHeight+" ret:"+ret);
    		return ret;
    	}
}
