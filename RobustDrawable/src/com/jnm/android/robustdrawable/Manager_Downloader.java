package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import org.apache.http.client.methods.HttpGet;

import android.os.Looper;

import com.jnm.lib.android.JMProject_AndroidApp;
import com.jnm.lib.core.JMLog;
import com.jnm.lib.core.structure.util.JMDate;
import com.jnm.lib.java.JMCrypto;
import com.jnm.lib.java.io.JMFileStream;
import com.sm1.EverySing.lib.Tool_App;
import com.sm1.EverySing.lib.manager.Manager_Bitmap.Bitmap_KeyType;
import com.sm1.EverySing.lib.manager.Manager_FFMPEG.FFMPEG;
import com.sm1.EverySing.lib.manager.Manager_File.CacheFileType;
import com.sm1.EverySing.lib.media.codec.CMCodecFDKAAC.ISOParser;
import com.sm1.EverySing.view.RobustDrawable.BitmapKey;
import com.smtown.everysing.server.message.JMM_ZZ_S3_Download;


/** 
 * 실패하였을 경우에 대해 Listener가 발동해야 되지 않나?
 * 동시에 3개까지 돌릴수 있음
 * 항상 최신의 Requester를 우선으로 처리해주는것으로 (이미 시작한건 끝까지하고)
 * 
 * 추후에 그냥 http에 있는 파일에 대해서도 지원할 필요가 생기게 되면 그때 생각하자
 * @author YMKIM
 */

public final class Manager_Downloader {
	private static void log(String pLog, Object... vargs) {
//		JMLog.e(String.format("Manager_Downloader] "+pLog, vargs));
	}
	
	public static void start() {
		for(int i=0;i<sThread_Downloaders.length;i++) {
			sThread_Downloaders[i] = new Thread_Downloader(i);
			sThread_Downloaders[i].setDaemon(true);
			sThread_Downloaders[i].setPriority(Thread.MIN_PRIORITY);
			sThread_Downloaders[i].start();
		}
	}
	
	private static Thread 					sAccessingThread = null;
	private static final Semaphore 		sAccessSema = new Semaphore(1);
	private static void acquireAccess() throws InterruptedException {
		if(sAccessingThread != null) {
			if(sAccessingThread.equals(Thread.currentThread())) {
				return;
			}
		}
		sAccessSema.acquire();
		sAccessingThread = Thread.currentThread();
	}
	private static void releaseAccess() {
		sAccessingThread = null;
		sAccessSema.drainPermits();
		sAccessSema.release();
	}
	
	private static final Semaphore 				sSemaphore_Queue_Downloads = new Semaphore(0);
	private static final Vector<BJ_Download> 	sQueue_Downloads = new Vector<BJ_Download>();
	private static final Thread_Downloader[] 	sThread_Downloaders = new Thread_Downloader[3];
	private static final class Thread_Downloader extends Thread {
		private int mThreadIndex;
		private BJ_Download	mCurDownlod;
		public Thread_Downloader(int pThreadIndex) {
			mThreadIndex = pThreadIndex;
		}
		
		@Override
		public void run() {
			super.run();
			while(true) {
				try {
					mCurDownlod = null;
					//					log("thread_downloader start1 "+sAccessSema.availablePermits());
					try {
						synchronized (sQueue_Downloads) {
							acquireAccess();
							if(sQueue_Downloads.size() > 0) {
								mCurDownlod = sQueue_Downloads.remove(0);
							}
						}
					} catch (Exception e) {
						JMLog.ex(e);
					} finally {
						releaseAccess();
					}
					//					log("thread_downloader start2 "+sAccessSema.availablePermits());
					
					if(mCurDownlod == null) {
						sSemaphore_Queue_Downloads.acquire();
						
						try {
							acquireAccess();
							synchronized (sQueue_Downloads) {
								if(sQueue_Downloads.size() > 0) {
									mCurDownlod = sQueue_Downloads.remove(0);
								}
							}
						} catch (Exception e) {
							JMLog.ex(e);
						} finally {
							releaseAccess();
						}
					}
					//					log("thread_downloader start3 "+sAccessSema.availablePermits());
					
					if(mCurDownlod == null) 
						continue;
					
					//					log("Runnable Downloader "+mThreadIndex);
//					log("AAAA mCurDownload.downloadRun(): "+mCurDownlod);
					mCurDownlod.downloadRun();
				} catch (Throwable e) {
					JMLog.ex(e);
					
					try { Thread.sleep(50); } catch (InterruptedException ie) { }
				} finally {
					mCurDownlod = null;
				}
			}
		}
	}
	public static interface OnDownloadListener {
		void onDownload(BJ_Download pRequester);
	}
	@SuppressWarnings("unchecked")
	private static <T extends BJ_Download> T getCurrentBJ(T pDownload) {
//		log("getCurrentBJ Start "+sAccessSema.availablePermits());
		try {
//		synchronized (sQueue_Downloads) {
			//sAccessSema.acquire();
			acquireAccess();
			for(Thread_Downloader t : sThread_Downloaders) {
//							log("?1 "+);
//							log("?2 "+t.mCurDownlod.getKey());
				if(t.mCurDownlod != null) {
					if(t.mCurDownlod.equals(pDownload)) {
						log("getCurrentBJ return cur "+t.mThreadIndex);
						return (T) t.mCurDownlod;
					}
				}
			}
			synchronized (sQueue_Downloads) {
				for(BJ_Download bj : sQueue_Downloads) {
					if(bj.equals(pDownload)) {
						log("getCurrentBJ return queue "+sQueue_Downloads.indexOf(bj));
						return (T) bj;
					}
				}
			}
		} catch (Exception e) {
			JMLog.ex(e);
		} finally {
			releaseAccess();
		}
		return null;
	}
	
	public static BJ_Download_S3 createBJ_S3(String pS3Key, boolean pMustCheck, boolean pFromS3) {
		if(pFromS3) {
			return createBJ_S3(pS3Key, pMustCheck, Bitmap_KeyType.S3Key_FromS3);
		} else {
			return createBJ_S3(pS3Key, pMustCheck, Bitmap_KeyType.S3Key_FromCloudFront);
		}
	}
	public static BJ_Download_S3 createBJ_S3(String pS3Key, boolean pMustCheck, Bitmap_KeyType pKeyType) {
		return new BJ_Download_S3(pKeyType, pS3Key, pMustCheck);
	}
	public static BJ_Download_Url createBJ_Url(String pUrl) {
		return new BJ_Download_Url(pUrl);
	}
	public static BJ_Download createBJ(BitmapKey pBitmapKey, File pTargetFile) {
		switch (pBitmapKey.mKeyType) {
		case S3Key_FromCloudFront:
		case S3Key_FromS3:
			return new BJ_Download_S3(pBitmapKey.mKeyType, pBitmapKey.mKey, true);
		case Resource:
			return new BJ_Download_Resource(pBitmapKey, pTargetFile);
		case LocalFilePath_Video:
			return new BJ_Download_Video(pBitmapKey, pTargetFile);
		case WebURL:
		default:
			return new BJ_Download_Url(pBitmapKey.mKey);
		}
	}
	
	public static abstract class BJ_Download implements Runnable {
		File 				mTargetFile;
		private int 		mProgress = -1;
		public boolean 		mIsReceived;
		public int getProgress() { return mProgress; }
		abstract public String getKey();
		abstract public void download() throws Throwable;
		private void downloadRun() {
			mProgress = 0;
			try {
				download();
			} catch (Throwable e) {
				JMLog.ex(e);
			} finally {
				JMProject_AndroidApp.getHandler().post(this);
			}
		}
		
		protected void setProgress(int pProgress) { 
			mProgress = pProgress; 
			log("Progress: "+pProgress+" "+getKey());
		}
		@Override
		public String toString() {
			return super.toString()+" Key:"+getKey();
		}
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof BJ_Download) {
				if(getKey().compareTo(((BJ_Download) o).getKey()) == 0) {
					return true;
				}
			}
			return super.equals(o);
		}
		
		private HashSet<OnDownloadListener>	mListeners = new HashSet<OnDownloadListener>();
//		private JMVector<OnDownloadListener>	mListeners = new JMVector<Manager_Downloader.OnDownloadListener>();
		
		private BJ_Download(File pTarget) {
			mTargetFile = pTarget;
		}
		public BJ_Download addOnDownloadedListener(OnDownloadListener pListener) {
			mListeners.add(pListener);
			return this;
		}

		@Override
		public void run() {
//			log("BJ_Download run Listeners ===== Start "+mListeners.size());
			for(OnDownloadListener a : mListeners) {
				a.onDownload(this);
			}
//			log("BJ_Download run Listeners ===== End");
		}

		public BJ_Download start() {
			new Thread() {
				@Override
				public void run() {
					super.run();
					
					//boolean isExist = false;
					log("BJ_Download Start Start "+sAccessSema.availablePermits());
					try {
						synchronized (sQueue_Downloads) {
							//sAccessSema.acquire();
							acquireAccess();
							// log("BJ_Download Start 뭐하나?");
							BJ_Download lCurBJ = getCurrentBJ(BJ_Download.this);
							log("AAAA BJ_Download "+lCurBJ+", New "+BJ_Download.this);
							if(lCurBJ != null) {
								if(lCurBJ.mProgress < 0) {
									log("BJ_Download 큐에 있으므로 리스너추가하고 큐에서 젤 앞으로 보냄 == ");
									sQueue_Downloads.removeElement(lCurBJ);
									lCurBJ.mListeners.addAll(mListeners);
									// log("BJ_Download 큐에 있으므로 리스너추가하고 큐에서 젤 앞으로 보냄2 == ");
									sQueue_Downloads.insertElementAt(lCurBJ, 0);
								} else {
									log("BJ_Download 작업중이므로 리스너만 추가");
									lCurBJ.mListeners.addAll(mListeners);
								}
							} else {
								sQueue_Downloads.insertElementAt(BJ_Download.this, 0);
								sSemaphore_Queue_Downloads.release();
							}
						}

					} catch (Exception e) {
						JMLog.ex(e);
					} finally {
//						sAccessSema.drainPermits();
//						sAccessSema.release();
						releaseAccess();
					}
					log("BJ_Download Start End "+sAccessSema.availablePermits());
				}
			}
			.start();
			return this;
		}
	}

//	public static <T extends BJ_Download> T createRequester(Class<T> pClass, String pS3Key) {
//		return createRequester(pClass, pS3Key, Manager_File.getFile_Cache(pS3Key));
//	}
//	public static <T extends BJ_Download> T createRequester(Class<T> pClass, String pS3Key, File pTargetFile) {
//		//BJ_Download_S3 ret = new BJ_Download_S3();
//		T ret = pClass.newInstance();
//		ret.mRequested_Url = pS3Key;
//		ret.mRequested_Target = pTargetFile;
//		return ret;
//	}
//	public static boolean checkS3FileMD5(String pS3Key) throws IOException {
//		return JMCrypto.getMD5Checksum(Manager_File.getFile_Cache(CacheFileType.S3, pS3Key)).compareTo(Tool_S3.getMD5(pS3Key)) == 0;
//	}
	public static class BJ_Download_S3 extends BJ_Download {
		private String 	mRequested_S3Key;
		private boolean 	mMustCheckWithServer = false;
		private Bitmap_KeyType	mKeyType = Bitmap_KeyType.S3Key_FromCloudFront;
		private BJ_Download_S3(Bitmap_KeyType pKeyType, String pS3Key, boolean pMustCheck) {
			super(Manager_File.getFile_Cache(CacheFileType.S3, pS3Key));
			mRequested_S3Key = pS3Key;
			mMustCheckWithServer = pMustCheck;
			mKeyType = pKeyType;
		}
		@Override
		public boolean equals(Object o) {
			if(o instanceof BJ_Download_S3) {
				if(((BJ_Download_S3)o).mRequested_S3Key.compareTo(mRequested_S3Key) == 0) {
					return true;
				}
			}
			return false;
		}
		
		@Override
		public void download() throws Throwable {
			try {
				log("BJ_Download download =====Start");
				log("Runnable Downloader "+mRequested_S3Key+ ", "+(Looper.getMainLooper().getThread() == Thread.currentThread()));
				mIsReceived = false;

				setProgress(2);
				if(mMustCheckWithServer == false) {
					if(mTargetFile.exists() && JMDate.getCurrentTime() - 1 * JMDate.TIME_Day < mTargetFile.lastModified() && mTargetFile.lastModified() <= JMDate.getCurrentTime()) {
						log("mMustCheckWithServer: false 안 받아도 되는 것으로 결론");
						mIsReceived = true;
						return;
					}
				}

				if(mKeyType == Bitmap_KeyType.S3Key_FromS3) {
					try {
						if(mTargetFile.exists()) {
							try {
								if(JMCrypto.getMD5Checksum(mTargetFile).compareTo(Tool_App.getMD5FromS3(mRequested_S3Key)) == 0) {
									log("MD5 일치하므로 안 받아도 되는 것으로 결론");
									mIsReceived = true;
									return;
								}
							} catch (IOException e) {
								JMLog.ex(e);
							}
						}
						setProgress(5);
						
//						// S3라이브러리
//						S3Object obj = Tool_S3.getS3Client().getObject(Tool_Common.getAmazonS3BucketName(), mRequested_S3Key);
//						S3ObjectInputStream bis = obj.getObjectContent();
//						JMFileStream outfs = new JMFileStream(mRequested_Target);
//						int file_total = (int) obj.getObjectMetadata().getContentLength();
//						int file_current = 0;
//						byte[] buffer = new byte[8192];
//						int read = bis.read(buffer);
//						while(read >= 0) {
//							file_current += read;
//							setProgress(5+file_current*90/file_total);
//							outfs.write(buffer, 0, read);
//							read = bis.read(buffer);
//						}
//						bis.close();
						JMM_ZZ_S3_Download jmm_s3down = new JMM_ZZ_S3_Download();
						jmm_s3down.Call_S3Key = mRequested_S3Key;
						if(Tool_App.sendJMM(jmm_s3down) == false || jmm_s3down.isSuccess() == false) {
							mIsReceived = false;
						} else {
//							String[] ss = new String(jmm_s3down.Reply_FileBuffer, "UTF-8").split("\n");
//							for(int i=0;i<ss.length;i++) {
//								log("jjjj "+i+": "+ss[i]);
//							}
//							JMFileStream outfs = new JMFileStream(mRequested_Target);
//							outfs.write(jmm_s3down.Reply_FileBuffer);
//							outfs.close();
							FileOutputStream outfs = new FileOutputStream(mTargetFile);
							outfs.write(jmm_s3down.Reply_FileBuffer);
							outfs.close();
							mIsReceived = true;
						}
					} catch (Exception e) {
						JMLog.ex(e);
						mIsReceived = false;
					}
				} else {
					HttpURLConnection 	con 	= null;
					InputStream 		input 	= null;
					JMFileStream  		outfs 	= null;
					try {
						String s = Manager_S3.createSignedURLForCloudFront(mRequested_S3Key);
						con = (HttpURLConnection)((new URL(s)).openConnection());
						con.setRequestMethod(HttpGet.METHOD_NAME);
						con.setConnectTimeout(10000);
						con.setUseCaches(false);
						con.setDefaultUseCaches(false);
						
						con.connect();
						input = con.getInputStream();
						long lLastModified = con.getLastModified();
						log("ContentLength: "+con.getContentLength()+" LastModified: "+new JMDate(lLastModified).toStringForDateTime());
						
						outfs = new JMFileStream(mTargetFile);
						int file_total = con.getContentLength();
						
						log("파일길이 비교: "+con.getContentLength()+", "+mTargetFile.length()+" Result: "+(lLastModified < mTargetFile.lastModified()));
						if(mTargetFile.exists()) {
							//if(mRequested_Target.length() == file_total && mRequested_Target.lastModified()-10*JMDate.TIME_Day < lLastModified && lLastModified < mRequested_Target.lastModified()) {
							if(mTargetFile.length() == file_total && lLastModified < mTargetFile.lastModified()) {
								log("파일길이 일치하므로 안 받아도 되는 것으로 결론");
								mIsReceived = true;
								return;
								// lNeedToReceive = false;
							}
						}
						
						int file_current = 0;
						byte[] buffer = new byte[8192];
						int read = input.read(buffer);
						while(read >= 0) {
							file_current += read;
							setProgress(2+file_current*93/file_total);
							// log("receive progress:%d ", getProgress());
							outfs.write(buffer, 0, read);
							read = input.read(buffer);
						}
						
						mIsReceived = true;
					} catch (Throwable e) {
						JMLog.uex(e, "S3Key:"+mRequested_S3Key+" mFromS3:"+mKeyType+" mMustCheckWithServer:"+mMustCheckWithServer);
						mIsReceived = false;
					} finally {
						if (outfs != null) { try { outfs.close(); } catch (Throwable e) { JMLog.ex(e); } }
						if (input != null) { try { input.close(); } catch (Throwable e) { JMLog.ex(e); } }
						if (con != null) { con.disconnect(); }
					}
				}
			} catch (Throwable e) {
				mIsReceived = false;
				throw e;
			} finally {
				if(mIsReceived) {
					setProgress(100);
				}
				log("BJ_Download download =====End");
			}
		}
		@Override
		public String getKey() {
			return "S3Key:"+mRequested_S3Key;
		}
	}
	public static class BJ_Download_Url extends BJ_Download {
		private String 				mRequested_Url;
		
		private BJ_Download_Url(String pUrl) {
//			this(pUrl, Manager_File.getFile_Cache(CacheFileType.Url, pUrl));
			super(Manager_File.getFile_Cache(CacheFileType.Url, pUrl));
			mRequested_Url = pUrl;
		}
//		public BJ_Download_Url(String pUrl, File pTargetFile) {
//			super(pTargetFile);
//			mRequested_Url = pUrl;
//		}
		@Override
		public void download() throws Throwable {
			HttpURLConnection 	con 	= null;
			InputStream 		input 	= null;
			JMFileStream  		outfs 	= null;
			try {
				con = (HttpURLConnection)((new URL(mRequested_Url)).openConnection());
				con.setRequestMethod(HttpGet.METHOD_NAME);
				con.setConnectTimeout(10000);
				con.setUseCaches(false);
				con.setDefaultUseCaches(false);
				
				con.connect();
				input = con.getInputStream();
				long lLastModified = con.getLastModified();
				log("ContentLength: "+con.getContentLength()+" LastModified: "+new JMDate(lLastModified).toStringForDateTime());
				
				outfs = new JMFileStream(mTargetFile);
				int file_total = con.getContentLength();
				
				log("파일길이 비교: "+con.getContentLength()+", "+mTargetFile.length()+" Result: "+(lLastModified < mTargetFile.lastModified()));
				if(mTargetFile.exists()) {
					//if(mRequested_Target.length() == file_total && mRequested_Target.lastModified()-10*JMDate.TIME_Day < lLastModified && lLastModified < mRequested_Target.lastModified()) {
					if(mTargetFile.length() == file_total && lLastModified < mTargetFile.lastModified()) {
						log("파일길이 일치하므로 안 받아도 되는 것으로 결론");
						mIsReceived = true;
						return;
						// lNeedToReceive = false;
					}
				}
				
				int file_current = 0;
				byte[] buffer = new byte[8192];
				int read = input.read(buffer);
				while(read >= 0) {
					file_current += read;
					setProgress(2+file_current*93/file_total);
					// log("receive progress:%d ", getProgress());
					outfs.write(buffer, 0, read);
					read = input.read(buffer);
				}
				
				mIsReceived = true;
			} catch (Throwable e) {
				mIsReceived = false;
				throw e;
			} finally {
				if (outfs != null) { try { outfs.close(); } catch (Throwable e) { JMLog.ex(e); } }
				if (input != null) { try { input.close(); } catch (Throwable e) { JMLog.ex(e); } }
				if (con != null) { con.disconnect(); }
				log("BJ_Download download =====End");
			}
			
			
//			throw new IllegalStateException("안됨");
			// TODO
//			try {
//				log("Runnable Downloader "+mRequested_Url);
//				boolean lNeedToReceive = true;
//				boolean lReceived = false;
//				
//				setProgress(2);
//				if(mRequested_Target.exists()) {
//					try {
//						lNeedToReceive = JMCrypto.getMD5Checksum(mRequested_Target).compareTo(Tool_S3.getMD5(mRequested_Url)) != 0;
//					} catch (IOException e) {
//						JMLog.ex(e);
//						lNeedToReceive = true;
//					}
//				}
//				
//				setProgress(5);
//				
//				if(lNeedToReceive == false) {
//					log("안 받아도 되는 것으로 결론");
//					 lReceived = true;
//				} else {
//					try {
//						S3Object obj = Tool_S3.getS3Client().getObject(Tool_Common.getAmazonS3BucketName(), mRequested_Url);
//						S3ObjectInputStream bis = obj.getObjectContent();
//						JMFileStream outfs = new JMFileStream(mRequested_Target);
//						int file_total = (int) obj.getObjectMetadata().getContentLength();
//						int file_current = 0;
//						byte[] buffer = new byte[8192];
//						int read = bis.read(buffer);
//						while(read >= 0) {
//							file_current += read;
//							setProgress(5+file_current*90/file_total);
////							log("receive progress:%d ", mCurDownlod.mProgress);
//							outfs.write(buffer, 0, read);
//							read = bis.read(buffer);
//						}
//						bis.close();
//						
//						lReceived = true;
//					} catch (Exception e) {
//						JMLog.ex(e);
//						lReceived = false;
//					}
//				}
//				
//				if(lReceived) {
//					setProgress(100);
//					JMProject_AndroidApp.getHandler().post(this);
//				}
//			} catch (Exception e) {
//				JMLog.ex(e);
//			}
		}
		@Override
		public String getKey() {
			return "URL:"+mRequested_Url;
		}
	}
	

	public static class BJ_Download_Resource extends BJ_Download {
		private int mResourceID;
		
		private BJ_Download_Resource(BitmapKey pBitmapKey, File pTargetFile) {
			super(pTargetFile);
			mResourceID = Integer.parseInt(pBitmapKey.mKey);
		}
		@Override
		public void download() throws Throwable {
			InputStream is = null;
			OutputStream os = null;
			try {
				is = Tool_App.getContext().getResources().openRawResource(mResourceID);
				os = new FileOutputStream(mTargetFile);
				
				byte[] buffer = new byte[1024 * 4];
				int a;
				while((a = is.read(buffer)) > 0) {
					os.write(buffer, 0, a);
				}
				
				mIsReceived = true;
			} catch (Throwable e) {
				mIsReceived = false;
				throw e;
			} finally {
				if (os != null) { try { os.close(); } catch (Throwable e) { JMLog.ex(e); } }
				if (is != null) { try { is.close(); } catch (Throwable e) { JMLog.ex(e); } }
			}
		}
		@Override
		public String getKey() {
			return "Resource:"+mResourceID;
		}
	}
	public static class BJ_Download_Video extends BJ_Download {
		private BitmapKey	mBitmapKey;
		public BJ_Download_Video(BitmapKey pBitmapKey, File pTargetFile) {
			super(pTargetFile);
			mBitmapKey = pBitmapKey;
		}
		@Override
		public void download() throws Throwable {
			try {
				ISOParser p = new ISOParser(mBitmapKey.mKey);
				log("startDownload LocalFilePath_Video:1 "+p.mRotation+", "+p.mWidth+", "+p.mHeight+", TargetFile:"+mTargetFile);
				
				FFMPEG d = Manager_FFMPEG.createExtractThumbnails(null, p.mRotation, mBitmapKey.mKey, mTargetFile.getName(), -1, (int)p.mWidth, (int)p.mHeight);
				d.startSyncronously();
				log("startDownload LocalFilePath_Video:2 "+d.getSuccess());
				
				if(d.getSuccess()) {
					Manager_File.copyFile(Manager_FFMPEG.getFile_ExtractedThumbnailFirst(mTargetFile.getName()), mTargetFile);
					mIsReceived = true;
				}
			} catch (Throwable e) {
				mIsReceived = false;
				throw e;
			}
		}
		@Override
		public String getKey() {
			return "Video:"+mBitmapKey.mKey;
		}
	}
	
	
//	private static JMVector<CreateRequester<?>> sQueue_Loader = new JMVector<CreateRequester<?>>();
//	private static final Semaphore sSemaphore_Loader = new Semaphore(0);
//	private static Thread sThread_Loader = null;
//	private static final Runnable sRunnable_Loader = new Runnable() {
//		@Override
//		public void run() {
//			while(true) {
//				try {
//					sSemaphore_Loader.acquire();
//					
//					CreateRequester<?> pRequester = null;
//					synchronized (sQueue_Loader) {
//						if(sQueue_Loader.size() > 0) {
//							pRequester = sQueue_Loader.remove(0);
//						}
//					}
//					
//					if(pRequester == null) 
//						continue;
////						}
////					log("r S3 "+pRequester.mRequested_S3);
//						pRequester.onCreated(JMManager_Bitmap.acquire(pRequester.mObject, pRequester.mRequested_Target.getPath(), pRequester.mMaxWidth, pRequester.mMaxHeight));
////					}
//				} catch (Throwable e) {
//					JMLog.ex(e);
//					
//					try {
//						Thread.sleep(50);
//					} catch (InterruptedException e1) { }
//				}
//			}
//		}
//	};
//	private static JMVector<CreateRequester<?>> sQueue_Downloader = new JMVector<CreateRequester<?>>();
//	private static final Semaphore sSemaphore_Downloader = new Semaphore(0);
//	private static Thread sThread_Downloader = null;
//	private static Runnable sRunnable_Downloader = new Runnable() {
//		@Override
//		public void run() {
//			while(true) {
//				try {
//					sSemaphore_Downloader.acquire();
//					
//					CreateRequester<?> pRequester = null;
//					synchronized (sQueue_Downloader) {
//						if(sQueue_Downloader.size() > 0) {
//							pRequester = sQueue_Downloader.remove(0);
//						}
//					}
//					
//					if(pRequester == null) 
//						continue;
//					
//					try {
//						log("sRunnable_Downloader "+pRequester.mRequested_S3);
//						Tool_S3.receiveIfDifferent(pRequester.mRequested_S3, pRequester.mRequested_Target);
//					} catch (Exception e) {
//						JMLog.ex(e);
//					}
//					
//					File f = new File(pRequester.mRequested_Target.getPath());
//					if(f.exists()) {
//						pRequester.start_Load();
//					} else {
//						pRequester.onCreated(null);
//					}
//				} catch (Throwable e) {
//					JMLog.ex(e);
//					
//					try {
//						Thread.sleep(50);
//					} catch (InterruptedException e1) { }
//				}
//			}
//		}
//	};
//	
//	
//	public static class CreateRequester<T> implements Runnable {
//		private OnCreatedBitmapListener<T>	mListener;
//		private T			mView;
//		private File 		mRequested_Target;
//		public String 		mRequested_S3;
//		public Bitmap 		mBitmap;
//		public Object 		mObject;
//		public int			mMaxWidth = -1;
//		public int			mMaxHeight = -1;
//		
//		public CreateRequester(Object pObject, T pView, String pS3, File pTargetFile) {
//			mObject = pObject;
//			mView = pView;
//			mRequested_S3 = pS3;
//			mRequested_Target = pTargetFile;
//		}
//		
//		private void onCreated(Bitmap pBitmap) {
//			mBitmap = pBitmap;
//			JMProject_AndroidApp.getHandler().post(this);
//		}
//
//		public CreateRequester<T> setOnCreatedListener(OnCreatedBitmapListener<T> pListener) {
//			mListener = pListener;
//			return this;
//		}
//		public CreateRequester<T> start_Load() {
//			boolean isExist = false;
//			synchronized (sQueue_Loader) {
//				for(CreateRequester<?> r : sQueue_Loader) {
//					if(r.mView.equals(mView) && r.mRequested_S3.compareTo(mRequested_S3) == 0) {
////						log("Load 이미 있으므로 작업 취소 "+mRequested_S3);
//						isExist = true;
//						break;
//					}
//				}
//				
//				if(isExist == false) {
//					//log("Load 작업! "+mRequested_S3);
//					sQueue_Loader.add(this);
//					sSemaphore_Loader.release();
//				}
//			}
//			return this;
//		}
//		public CreateRequester<T> start_Download() {
//			boolean isExist = false;
//			synchronized (sQueue_Downloader) {
//				for(CreateRequester<?> r : sQueue_Downloader) {
//					if(r.mView.equals(mView) && r.mRequested_S3.compareTo(mRequested_S3) == 0) {
////						log("Download 이미 있으므로 작업 취소 "+mRequested_S3);
//						isExist = true;
//						break;
//					}
//				}
//				
//				if(isExist == false) {
////					log("Download 작업! "+mRequested_S3);
//					sQueue_Downloader.add(this);
//					sSemaphore_Downloader.release();
//				}
//			}
//			
//			return this;
//		}
//
//		@Override
//		public void run() {
//			mListener.onCreated(mView, this);
//		}
//	}
//	
////	private static HashMap<String, Bitmap> sBitmaps = new HashMap<String, Bitmap>();
//
////	public static void setS3Bitmap(JMPageComposer_PageElem__Parent<?> pObject, JMBitmapView pImageView, long pS3UUID, S3ImageType pImageType) {
////		setS3Bitmap(pObject, pImageView, pS3UUID+pImageType.getSuitableImageName());
////	}
////	public static void setS3Bitmap(final JMPageComposer_PageElem__Parent<?> pObject, JMBitmapView pImageView, long pS3UUID, S3ImageType pImageType) {
////		setS3Bitmap(pObject, pImageView, pS3UUID+pImageType.getSuitableImageName(), Manager_File.getFile_Cache(pS3UUID+pImageType.getSuitableImageName()), -1, -1, new OnCreatedBitmapListener<JMBitmapView>() {
////			@Override
////			public void onCreated(JMBitmapView pView, CreateRequester<JMBitmapView> pRequester) {
////				if(((String)pView.getTag()).compareTo(pRequester.mRequested_S3) == 0) {
////					pObject.setDirty(true);
////					pView.setImageBitmap(pRequester.mBitmap);
////				}
////			}
////		});
////	}
//	
//	public static void setS3ButtonDrawable(final Object pObject, final ImageView pImageView, String pS3_Normal, final String pS3_Pressed) {
//		pImageView.setTag(pS3_Normal);
//		
//		final File pTargetFile_Normal = Manager_File.getFile_Cache(pS3_Normal);
//		CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3_Normal, pTargetFile_Normal);
//		r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//			@Override
//			public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//				if(((String)pView.getTag()).compareTo(pRequester.mRequested_S3) == 0) {
//					// pView.setImageBitmap(pRequester.mBitmap);
//					pImageView.setTag(pS3_Pressed);
//					
//					final File pTargetFile_Pressed = Manager_File.getFile_Cache(pS3_Pressed);
//					if(JMManager_Bitmap.isAcquired(pObject, pTargetFile_Pressed.getPath())) {
//						//pImageView.setImageBitmap(JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1));
//						
//						pImageView.setImageDrawable(
//								Tool_App.createButtonDrawable(
//									new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Normal.getPath(), -1, -1)), 
//									new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(),JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1))));
//						return;
//					}
//					
//					CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3_Pressed, pTargetFile_Pressed);
//					r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//						@Override
//						public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//							pImageView.setImageDrawable(
//									Tool_App.createButtonDrawable(
//										new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(),JMManager_Bitmap.acquire(pObject, pTargetFile_Normal.getPath(), -1, -1)), 
//										new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(),JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1))));
//						}
//					});
//					
//					pImageView.setImageBitmap(null);
//					if(pTargetFile_Pressed.exists() && Math.random() > CheckImageFileMD5Ratio) {
//						r.start_Load();
//					} else {
//						r.start_Download();
//					}
//				}
//			}
//		});
//		
//		pImageView.setImageBitmap(null);
//		if(pTargetFile_Normal.exists() && Math.random() > CheckImageFileMD5Ratio) {
//			r.start_Load();
//		} else {
//			r.start_Download();
//		}
//	}
//	
//	public static void setS3ButtonDrawable(final Object pObject, final ImageView pImageView, String pS3_Normal, final String pS3_Pressed, final String pS3_Disabled) {
//		pImageView.setTag(pS3_Normal);
//		
//		final File pTargetFile_Normal = Manager_File.getFile_Cache(pS3_Normal);
//		final File pTargetFile_Pressed = Manager_File.getFile_Cache(pS3_Pressed);
//		final File pTargetFile_Disabled = Manager_File.getFile_Cache(pS3_Disabled);
//					
//		CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3_Normal, pTargetFile_Normal);
//		r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//			@Override
//			public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//				if(((String)pView.getTag()).compareTo(pRequester.mRequested_S3) == 0) {
//					pImageView.setTag(pS3_Pressed);
//					
//					if(JMManager_Bitmap.isAcquired(pObject, pTargetFile_Pressed.getPath()) && JMManager_Bitmap.isAcquired(pObject, pTargetFile_Disabled.getPath())) {
//						pImageView.setImageDrawable(
//								Tool_App.createButtonDrawable(
//									new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Normal.getPath(), -1, -1)), 
//									new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1)),
//									new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Disabled.getPath(), -1, -1))
//									));
//						return;
//					}
//					
//					CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3_Pressed, pTargetFile_Pressed);
//					r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//						@Override
//						public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//							if(((String)pView.getTag()).compareTo(pRequester.mRequested_S3) == 0) {
//								pImageView.setTag(pS3_Disabled);
//								
//								if(JMManager_Bitmap.isAcquired(pObject, pTargetFile_Disabled.getPath())) {
//									pImageView.setImageDrawable(
//											Tool_App.createButtonDrawable(
//												new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Normal.getPath(), -1, -1)), 
//												new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1)), 
//												new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Disabled.getPath(), -1, -1))));
//									return;
//								}
//								
//								CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3_Disabled, pTargetFile_Disabled);
//								r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//									@Override
//									public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//										pImageView.setImageDrawable(
//												Tool_App.createButtonDrawable(
//													new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Normal.getPath(), -1, -1)), 
//													new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Pressed.getPath(), -1, -1)), 
//													new BitmapDrawable(JMProject_AndroidApp.getApplication().getAppContext().getResources(), JMManager_Bitmap.acquire(pObject, pTargetFile_Disabled.getPath(), -1, -1))));
//									}
//								});
//								
//								pImageView.setImageBitmap(null);
//								if(pTargetFile_Disabled.exists() && Math.random() > CheckImageFileMD5Ratio) {
//									r.start_Load();
//								} else {
//									r.start_Download();
//								}
//							}
//						}
//					});
//					
//					pImageView.setImageBitmap(null);
//					if(pTargetFile_Pressed.exists() && Math.random() > CheckImageFileMD5Ratio) {
//						r.start_Load();
//					} else {
//						r.start_Download();
//					}
//				}
//			}
//		});
//		
//		pImageView.setImageBitmap(null);
//		if(pTargetFile_Normal.exists() && Math.random() > CheckImageFileMD5Ratio) {
//			r.start_Load();
//		} else {
//			r.start_Download();
//		}
//	}
//	
//	static String getSuitableImageName(S3ImageType pImageType) {
//		if(pImageType.mSuitableImageName == null) {
//			int w = Tool_App.getDisplayWidth();
//			ImageSizeAndName[] mVals = pImageType.getSizes();
//			for(int i=0;i<mVals.length; i++) {
//				if(pImageType.getSizes()[i].getMinimumWidth() < w) {
//					pImageType.mSuitableImageName = mVals[i].getName();
//					return pImageType.mSuitableImageName;
//				}
//			}
//		}
//		return pImageType.mSuitableImageName;
//	}
//
//	public static void setS3Bitmap(Object pObject, ImageView pImageView, String pUrl) {
//		 setS3Bitmap(pObject, pImageView, pUrl, Manager_File.getFile_Cache(pUrl), -1, -1, null);
//	}
//	public static void setS3Bitmap(Object pObject, ImageView pImageView, String pS3Url, File pTargetFile, int pMaxWidth, int pMaxHeight, OnCreatedBitmapListener<ImageView> pListener) {
//		setS3Bitmap(pObject, pImageView, pS3Url, pTargetFile, pMaxWidth, pMaxHeight, null, pListener);
//	}
//	public static void setS3Bitmap(Object pObject, ImageView pImageView, String pS3Url, File pTargetFile, int pMaxWidth, int pMaxHeight, Drawable pDefaultDrawable, OnCreatedBitmapListener<ImageView> pListener) {
//		pImageView.setTag(pS3Url);
//		if(JMManager_Bitmap.isAcquired(pObject, pTargetFile.getPath())) {
//			pImageView.setImageBitmap(JMManager_Bitmap.acquire(pObject, pTargetFile.getPath(), pMaxWidth, pMaxHeight));
//			return;
//		}
//		
//		CreateRequester<ImageView> r = new CreateRequester<ImageView>(pObject, pImageView, pS3Url, pTargetFile);
//		r.mMaxWidth = pMaxWidth;
//		r.mMaxHeight = pMaxHeight;
//		
//		if(pListener == null) {
//			r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
//				@Override
//				public void onCreated(ImageView pView, CreateRequester<ImageView> pRequester) {
//					if(((String)pView.getTag()).compareTo(pRequester.mRequested_S3) == 0 && pRequester.mBitmap != null) {
//						pView.setImageBitmap(pRequester.mBitmap);
//					} else {
//						// pView.setImageBitmap(null);
//						if(pRequester.mBitmap != null) {
//							JMManager_Bitmap.release(pRequester.mObject, pRequester.mBitmap);
//						}
//					}
//				}
//			});
//		} else {
//			r.setOnCreatedListener(pListener);
//		}
//		
//		pImageView.setImageDrawable(pDefaultDrawable);
//		if(pTargetFile.exists() && Math.random() > CheckImageFileMD5Ratio) {
//			r.start_Load();
//		} else {
//			r.start_Download();
//		}
//	}
//	
//	
//	public static int releaseAll(Object pObject) {
//		return JMManager_Bitmap.releaseAll(pObject);
//	}
//
//	public static File resizeBitmap(MLContent pMLContent, Uri pUri) throws Exception {
//		String [] proj={MediaStore.Images.Media.DATA};
//		Cursor cursor = pMLContent.getMLActivity().getContentResolver().query(pUri, proj, null, null, null); 
//		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
//		cursor.moveToFirst();
//		String path = cursor.getString(column_index);
//		
//		FileInputStream in = new FileInputStream(path);
//		
//		// Decode image size
//		BitmapFactory.Options o = new BitmapFactory.Options();
//		o.inJustDecodeBounds = true;
//		BitmapFactory.decodeStream(in, null, o);
//		in.close();
//		
//		int scale = 1;
//		while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > Manager_Login.IMAGE_MAX_SIZE) {
//			scale++;
//		}
//		
//		Bitmap b = null;
//		in = new FileInputStream(path);
//		if (scale > 1) {
//			scale--;
//			// scale to max possible inSampleSize that still yields an image
//			// larger than target
//			o = new BitmapFactory.Options();
//			o.inSampleSize = scale;
//			b = BitmapFactory.decodeStream(in, null, o);
//			
//			// resize to desired dimensions
//			int height = b.getHeight();
//			int width = b.getWidth();
//			
//			double y = Math.sqrt(Manager_Login.IMAGE_MAX_SIZE / (((double) width) / height));
//			double x = (y / height) * width;
//			
//			Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, (int) x, (int) y, true);
//			b.recycle();
//			b = scaledBitmap;
//			
//			System.gc();
//		} else {
//			b = BitmapFactory.decodeStream(in);
//		}
//		in.close();
//		
//		File ret = Manager_File.createTempCacheFile();
//		FileOutputStream fos = new FileOutputStream(ret);
//		b.compress(CompressFormat.JPEG, 100, fos);
//		fos.close();
//		
//		return ret;
//	}
}
