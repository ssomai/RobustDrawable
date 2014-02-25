package com.jnm.android.robustdrawable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;


/** 
 * 실패하였을 경우에 대해 Listener가 발동해야 되지 않나?
 * 동시에 3개까지 돌릴수 있음
 * 항상 최신의 Requester를 우선으로 처리해주는것으로 (이미 시작한건 끝까지하고)
 * 
 * 추후에 그냥 http에 있는 파일에 대해서도 지원할 필요가 생기게 되면 그때 생각하자
 * @author YMKIM
 */
public final class RDBitmapDownloader {
	private static void log(String pLog) {
//		JMLog.e("RDBitmapDownloader] "+pLog);
	}
	
	private static void check() {
		if(sThread_BitmapDownloaders == null) {
			sThread_BitmapDownloaders = new Thread_BitmapDownloader[Math.max(5, Math.min(1, RobustDrawable__Parent.getMemoryClass()/32*2))];
//			sThread_BitmapDownloaders = new Thread_BitmapDownloader[2];
			for(int i=0;i<sThread_BitmapDownloaders.length;i++) {
				sThread_BitmapDownloaders[i] = new Thread_BitmapDownloader(i);
				sThread_BitmapDownloaders[i].setDaemon(true);
				sThread_BitmapDownloaders[i].setPriority(Thread.MIN_PRIORITY);
				sThread_BitmapDownloaders[i].start();
			}
		}
	}
	
//	private static Thread 			sAccessingThread 	= null;
//	private static Semaphore 		sAccessSema 		= new Semaphore(1);
//	private static void acquireAccess() throws InterruptedException {
//		if(sAccessingThread != null) {
//			if(sAccessingThread.equals(Thread.currentThread())) {
//				return;
//			}
//		}
//		sAccessSema.acquire();
//		sAccessingThread = Thread.currentThread();
//	}
//	private static void releaseAccess() {
//		sAccessingThread = null;
//		sAccessSema.drainPermits();
//		sAccessSema.release();
//	}
	
	
//	private static List<RD_BitmapDownloaderJob> 	sQueue 					= Collections.synchronizedList(new Vector<RD_BitmapDownloaderJob>());
	private static Vector<RDBitmapDownloaderJob> 	sQueue 					= new Vector<RDBitmapDownloaderJob>();
	private static Semaphore 						sSemaphore_Queue 		= new Semaphore(0);
	private static Thread_BitmapDownloader[] 		sThread_BitmapDownloaders 	= null;
	private static final class Thread_BitmapDownloader extends Thread {
		private int 				mThreadIndex;
		private RDBitmapDownloaderJob		mCurBitmapLoader;
		public Thread_BitmapDownloader(int pThreadIndex) {
			mThreadIndex = pThreadIndex;
		}
		
		@Override
		public void run() {
			super.run();
			while(true) {
				try {
					mCurBitmapLoader = null;
					//					log("thread_downloader start1 "+sAccessSema.availablePermits());
					synchronized (sQueue) {
						try {
							//						acquireAccess();
							if(sQueue.size() > 0) {
								mCurBitmapLoader = sQueue.remove(0);
							}
						} catch (Exception e) {
							RobustDrawable__Parent.ex(e);
						} finally {
							//						releaseAccess();
						}
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
						//					log("thread_downloader start3 "+sAccessSema.availablePermits());
					}
					
					if(mCurBitmapLoader == null) 
						continue;
					
					mCurBitmapLoader.processJob(false);
				} catch (Throwable e) {
					RobustDrawable__Parent.ex(e);
					try { Thread.sleep(50); } catch (InterruptedException ie) { }
				} finally {
					if(mCurBitmapLoader != null)
						log("thread_downloader end key:"+mCurBitmapLoader.mKey);
					mCurBitmapLoader = null;
				}
			}
		}
	}
	public static interface OnRDBitmapDownloadListener {
		void onBitmapLoaded(RDBitmapDownloaderJob pRequester);
	}
	private static RDBitmapDownloaderJob getCurrentBJ(RDBitmapDownloaderJob pDownload) {
		try {
			for(Thread_BitmapDownloader t : sThread_BitmapDownloaders) {
				if(t.mCurBitmapLoader != null) {
					if(t.mCurBitmapLoader.mState == BJState.S1_BitmapDownloading) {
						if(t.mCurBitmapLoader.mKey.getCacheFile_Original().equals(pDownload.mKey.getCacheFile_Original())) {
							log("getCurrentBJ return cur "+t.mThreadIndex);
							return t.mCurBitmapLoader;
						}
					}
				}
			}
			synchronized (sQueue) {
				for(RDBitmapDownloaderJob bj : sQueue) {
					if(bj.equals(pDownload)) {
						log("getCurrentBJ return queue "+sQueue.indexOf(bj));
						return bj;
					}
				}
			}
		} catch (Exception e) {
			RobustDrawable__Parent.ex(e);
		}
		return null;
	}
	static void start(RD__BitmapKey pBitmapKey, OnRDBitmapDownloadListener pListener) {
		check();
		try {
//			JMLog.e("RDBitmapDownloader start Key:"+pBitmapKey);
			
			RDBitmapDownloaderJob bj = new RDBitmapDownloaderJob();
			bj.mKey = pBitmapKey.clone();
			bj.addOnBitmapDownloadListener(pListener);
			bj.start();
		} catch (Throwable e) {
			RobustDrawable__Parent.ex(e);
		}
	}
	
	private static enum BJState {
		S0_InQueue, S1_BitmapDownloading, S9_Failed, S9_Successed, S9_Finished,
	}
	static class RDBitmapDownloaderJob {
		Throwable 		mThrowable = null;
		
		BJState 		mState;
		RD__BitmapKey 	mKey;
		
		void processJob(boolean pIsPrompt) {
			try {
				mState = BJState.S1_BitmapDownloading;
				
				log("Runnable Downloader ");
				Bitmap bm = null;
				if(pIsPrompt == false) {
					bm = RobustDrawable__Parent.getCache(mKey);
					if(bm != null) {
						log("exist at RobustDrawable.Cache Key:"+mKey+" Bitmap:"+bm+" Recycled:"+bm.isRecycled());
						// 그렇군 정확히 이건 새로 생성된건 아닌데 그치
						if(bm.isRecycled()) {
							bm = null;
						}
					}
				}
				
				if(bm == null) {
					log("create 2: "+mKey);
					if(mKey.getKey() != null && mKey.getKeyWithoutSize() != null) {
						mKey.download_To_CacheFile_Original();
						mKey.onDownloaded_To_CacheFile_Original();
						log("create 2.2: Key:"+mKey+", len:"+mKey.getCacheFile_Original().length());
					}
				}
				log("create 3: "+mKey);
				
				mState = BJState.S9_Successed;
				mThrowable = null;
			} catch (Throwable e) {
				mState 		= BJState.S9_Failed;
				mThrowable 	= e;
				
				if(e instanceof OutOfMemoryError) {
					RobustDrawable__Parent.recycleAll();
				}
				
				RobustDrawable__Parent.ex(e);
			} finally {
				synchronized (mListeners) {
					for(OnRDBitmapDownloadListener a : mListeners) {
						a.onBitmapLoaded(RDBitmapDownloaderJob.this);
					}
					RDBitmapDownloaderJob.this.mState = BJState.S9_Finished;
				}
			}
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof RDBitmapDownloaderJob) {
				if(mKey.equals(((RDBitmapDownloaderJob) o).mKey)) {
					return true;
				}
			}
			return super.equals(o);
		}
		
//		private HashSet<OnRDBitmapLoadListener> 	mListeners = new HashSet<OnRDBitmapLoadListener>();
		private Set<OnRDBitmapDownloadListener> 	mListeners = Collections.synchronizedSet(new HashSet<OnRDBitmapDownloadListener>());
		public RDBitmapDownloaderJob addOnBitmapDownloadListener(OnRDBitmapDownloadListener pListener) {
			synchronized (mListeners) {
				mListeners.add(pListener);
			}
			return this;
		}

		RDBitmapDownloaderJob start() {
			new Thread() {
				@Override
				public void run() {
					super.run();
					
					log("Start ");
					synchronized (sQueue) {
						try {
							RDBitmapDownloaderJob lCurBJ = getCurrentBJ(RDBitmapDownloaderJob.this);
							log("CurBJ:"+lCurBJ+" New:"+RDBitmapDownloaderJob.this);
							if(lCurBJ != null) {
								if(lCurBJ.mState == BJState.S0_InQueue) {
									log("큐에 있으므로 리스너추가하고 큐에서 젤 앞으로 보냄 CurBJ.key:"+lCurBJ.mKey+" key:"+mKey);
									sQueue.remove(lCurBJ);
									synchronized (lCurBJ.mListeners) {
										lCurBJ.mListeners.addAll(mListeners);
									}
									sQueue.add(0, lCurBJ);
								} else {
									log("작업중이므로 리스너만 추가 CurBJ.key:"+lCurBJ.mKey+" key:"+mKey);
									log("addAll 0 "+lCurBJ.mListeners.size()+" CurBJ.key:"+lCurBJ.mKey+" key:"+mKey);
									synchronized (lCurBJ.mListeners) {
										lCurBJ.mListeners.addAll(mListeners);
									}
									log("addAll 1 "+lCurBJ.mListeners.size()+" CurBJ.key:"+lCurBJ.mKey+" key:"+mKey);
								}
							} else {
								log("큐에 추가 key:"+mKey);
								sQueue.add(0, RDBitmapDownloaderJob.this);
							}
							sSemaphore_Queue.release();
						} catch (Exception e) {
							RobustDrawable__Parent.ex(e);
						}
					}
				}
			}
			.start();
			return this;
		}
	}
}
