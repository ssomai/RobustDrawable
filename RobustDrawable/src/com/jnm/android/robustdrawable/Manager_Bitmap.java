package com.jnm.android.robustdrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.Semaphore;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

import com.jnm.lib.android.JMProject_AndroidApp;
import com.jnm.lib.android.manager.JMManager_Bitmap;
import com.jnm.lib.android.ml.MLContent;
import com.jnm.lib.core.JMLog;
import com.jnm.lib.core.structure.util.JMVector;
import com.sm1.EverySing.lib.Tool_App;
import com.sm1.EverySing.lib.manager.Manager_Downloader.BJ_Download;
import com.sm1.EverySing.lib.manager.Manager_Downloader.OnDownloadListener;
import com.sm1.EverySing.lib.manager.Manager_File.CacheFileType;
import com.sm1.EverySing.view.RobustDrawable;
import com.sm1.EverySing.view.RobustDrawable.BitmapKey;


public final class Manager_Bitmap {
	private static void log(String pLog) {
//		JMLog.e("Manager_Bitmap] "+pLog);
	}
	
	public enum Bitmap_KeyType {
		S3Key_FromS3,
		S3Key_FromCloudFront,
		LocalFilePath_Video, 
		LocalFilePath_Image,
		WebURL, 
		Resource, 
	}
	
	public static interface OnCreatedBitmapListener<T> {
		void onCreated(T pView, BJ_BitmapLoader<T> pRequester);
	}
	public static final float CheckImageFileMD5Ratio = 0.10f;
	
	public static void start() {
		sThread_Loader.setDaemon(true);
		sThread_Loader.setPriority(Thread.MIN_PRIORITY);
		sThread_Loader.start();
	}
	
	private static 			Thread 		sAccessingThread 	= null;
	private static final 	Semaphore 	sAccessSema 		= new Semaphore(1);
	private static void acquireAccess() throws InterruptedException {
		synchronized (sAccessSema) {
			if(sAccessingThread != null && Thread.currentThread() != null) {
				if(sAccessingThread.equals(Thread.currentThread())) {
					return;
				}
			}
		}
		sAccessSema.acquire();
		sAccessingThread = Thread.currentThread();
	}
	private static void releaseAccess() {
		synchronized (sAccessSema) {
			sAccessingThread = null;
		}
		sAccessSema.drainPermits();
		sAccessSema.release();
	}
	
	private static final JMVector<BJ_BitmapLoader<?>> sQueue_Loader = new JMVector<BJ_BitmapLoader<?>>();
	private static final Semaphore sSemaphore_Loader = new Semaphore(0);
	private static final Thread sThread_Loader = new Thread() {
		@Override
		public void run() {
			super.run();
			while(true) {
				try {
					BJ_BitmapLoader<?> pRequester = null;
					try {
						acquireAccess();
						if(sQueue_Loader.size() > 0) {
							pRequester = sQueue_Loader.remove(0);
						}
					} catch (Exception e) {
						JMLog.ex(e);
					} finally {
						releaseAccess();
					}
					
					if(pRequester == null) {
						sSemaphore_Loader.acquire();
						try {
							acquireAccess();
							//					synchronized (sQueue_Loader) {
							if(sQueue_Loader.size() > 0) {
								pRequester = sQueue_Loader.remove(0);
							}
							//					}
						} catch (Throwable e) {
							JMLog.ex(e);
						} finally {
							releaseAccess();
						}
					}
					
					log("Loader1 "+pRequester);
					if(pRequester == null) 
						continue;
					log("Loader2 "+pRequester.mKey);
					
					if(pRequester.mWithAcquirement) {
						pRequester.onCreated(JMManager_Bitmap.acquire(pRequester.mObject, pRequester.mTargetFile.getPath(), pRequester.mMaxWidth, pRequester.mMaxHeight));
					} else {
						//pRequester.onCreated(JMManager_Bitmap.createBitmap(fis));
						log("create "+pRequester.mTargetFile+", "+pRequester.mKey);
						Bitmap bm = null;
						try {
							bm = Tool_App.rotateByEXIF(
								JMManager_Bitmap.createBitmap(pRequester.mTargetFile, pRequester.mMaxWidth, pRequester.mMaxHeight), 
								pRequester.mTargetFile.getPath());
							if(pRequester.mKey.mUseBlur) {
								bm = RobustDrawable.fastblur(bm, 100);
							}
						} catch (Throwable e) {
							pRequester.mThrowable = e;
							throw e;
						} finally {
							pRequester.onCreated(bm);
						}
					}
				} catch (Throwable e) {
					JMLog.ex(e);
					
					try { Thread.sleep(50); } catch (InterruptedException ie) { }
				}
			}
		}
	};
	
	public static <T> BJ_BitmapLoader<T> createRequester(Object pObject, T pTarget, BitmapKey pKey) {
		return createRequester(pObject, pTarget, pKey, true);
	}
	public static <T> BJ_BitmapLoader<T> createRequester(Object pObject, T pTarget, BitmapKey pKey, boolean pWithAcquirement) {
		return createRequester(pObject, pTarget, pKey, null, pWithAcquirement);
	}
	public static <T> BJ_BitmapLoader<T> createRequester(Object pObject, T pTarget, BitmapKey pKey, File pTargetFile) {
		return createRequester(pObject, pTarget, pKey, pTargetFile, true);
	}
	public static <T> BJ_BitmapLoader<T> createRequester(Object pObject, T pTarget, BitmapKey pKey, File pTargetFile, boolean pWithAcquirement) {
		return createRequester(pObject, pTarget, pKey, pTargetFile, pWithAcquirement, -1, -1);
	}
	public static <T> BJ_BitmapLoader<T> createRequester(Object pObject, T pTarget, BitmapKey pKey, File pTargetFile, boolean pWithAcquirement, int pMaxWidth, int pMaxHeight) {
		BJ_BitmapLoader<T> ret = new BJ_BitmapLoader<T>();
		ret.mObject = pObject;
		ret.mView = pTarget;
//		ret.mKeyType = pKeyType;
//		ret.mKey = pKey;
		if(pTargetFile == null) {
			switch (pKey.mKeyType) {
			case LocalFilePath_Image:
				ret.mTargetFile = new File(pKey.mKey);
				break;
			case LocalFilePath_Video:
				ret.mTargetFile = Manager_File.getFile_Cache(CacheFileType.Temp, pKey.mKey);
				break;
			case WebURL:
				ret.mTargetFile = Manager_File.getFile_Cache(CacheFileType.Url, pKey.mKey);
				break;
			case Resource:
				ret.mTargetFile = Manager_File.getFile_Cache(CacheFileType.Temp, pKey.mKey);
				break;
			case S3Key_FromCloudFront:
			case S3Key_FromS3:
				ret.mTargetFile = Manager_File.getFile_Cache(CacheFileType.S3, pKey.mKey);
				break;
			default:
				ret.mTargetFile = Manager_File.getFile_Cache(CacheFileType.Temp, pKey.mKey);
				break;
			}
		} else {
			ret.mTargetFile = pTargetFile;
		}
		ret.mKey = pKey;
		ret.mWithAcquirement = pWithAcquirement;
		ret.mMaxWidth = pMaxWidth;
		ret.mMaxHeight = pMaxHeight;
		return ret;
	}
	
	public static class BJ_BitmapLoader<T> implements Runnable {
		@Deprecated
		public boolean		mWithAcquirement = true;
		OnCreatedBitmapListener<T>	mListener;
		T			mView;
		public T getView() { return mView; }
		private int mProgress = 0;
		public int getProgress() {
			if(mRequester_Downloader != null) {
				return (int) (mRequester_Downloader.getProgress() * 80f / 100f);
			} 
			return mProgress;
		}
		
		public BitmapKey 			mKey;
		public File 			mTargetFile;
//		public Bitmap_KeyType	mKeyType;
		
		public Bitmap 			mBitmap;
		public Object 			mObject;
		public int				mMaxWidth = -1;
		public int				mMaxHeight = -1;
		private BJ_Download	mRequester_Downloader;
		
		public Throwable 		mThrowable = null;
		
		private BJ_BitmapLoader() {
		}
		
		private void onCreated(Bitmap pBitmap) {
			mBitmap = pBitmap;
			JMProject_AndroidApp.getHandler().post(this);
		}
		
		public BJ_BitmapLoader<T> setOnCreatedListener(OnCreatedBitmapListener<T> pListener) {
			mListener = pListener;
			return this;
		}
		public BJ_BitmapLoader<T> start() {
			if(mTargetFile.exists()) {
				log("addToLoadQueue "+mKey);
				mProgress = 80;
				addToLoadQueue();
			} else {
				log("startDownload "+mKey);
				mProgress = 0;
				start_Download();
			}
			return this;
		}
//		public BJ_BitmapLoader<T> start(double pDownloadRateIfFileExist) {
//			log("start "+mKey+" "+pDownloadRateIfFileExist);
//			if(mTargetFile.exists() && Math.random() > pDownloadRateIfFileExist) {
//				log("addToLoadQueue "+mKey);
//				mProgress = 80;
//				addToLoadQueue();
//			} else {
//				log("startDownload "+mKey);
//				mProgress = 0;
//				start_Download();
//			}
//			return this;
//		}
		
		private void addToLoadQueue() {
			boolean isExist = false;
			try {
				acquireAccess();
				for(BJ_BitmapLoader<?> r : sQueue_Loader) {
					if(r.mView.equals(mView) && r.mKey == mKey) {
						isExist = true;
						break;
					}
				}
				
				if(isExist == false) {
					sQueue_Loader.add(this);
					sSemaphore_Loader.release();
				}
			} catch (Exception e) {
				JMLog.ex(e);
			} finally {
				releaseAccess();
			}
		}
		
		
		private BJ_BitmapLoader<T> start_Download() {
//			log("start_Download "+mKey.mKeyType+", Key:"+mKey.mKey);
			
//			switch (mKey.mKeyType) {
//			case LocalFilePath_Video:
//				new Thread(new Runnable() {
//					@Override
//					public void run() {
//						try {
////							Bitmap bm = ThumbnailUtils.createVideoThumbnail(mKey.mKey, Thumbnails.MINI_KIND);
////							FileOutputStream fos = new FileOutputStream(mTargetFile);
////							bm.compress(CompressFormat.JPEG, 100, fos);
////							fos.close();
////							
////							addToLoadQueue();
//							
//							ISOParser p = new ISOParser(mKey.mKey);
//							// p.getRotation(mKey.mKey);
//							log("start_Download LocalFilePath_Video: "+p.mRotation+", "+p.mWidth+", "+p.mHeight);
//							
//							Manager_FFMPEG.createExtractThumbnails(new OnFFMPEGListener() {
//								@Override
//								public void onResult(boolean pSuccess) {
//									log("One Thumbnail onResult "+pSuccess);
//									try {
//										if(pSuccess) {
//											Manager_File.copyFile(Manager_FFMPEG.getFile_ExtractedThumbnailFirst(), mTargetFile);
//											addToLoadQueue();
//										}
//									} catch (Exception e) {
//										JMLog.ex(e);
//										pSuccess = false;
//									}
//									
//									if(pSuccess) {
//										onCreated(null);
//									}
//								}
//								@Override
//								public void onProgress(int pProgress) {
//								}
//							}, p.mRotation, mKey.mKey, -1, (int)p.mWidth, (int)p.mHeight)
//							.start();
//						} catch (Throwable e) {
//							JMLog.ex(e);
//							onCreated(null);
//						}
//					}
//				})
//				.start();
//				break;
//			case Resource:
////				new Thread(new Runnable() {
////					@Override
////					public void run() {
////						try {
////							InputStream input = Tool_App.getContext().getResources().openRawResource(Integer.parseInt(mKey.mKey));
////							OutputStream output = new FileOutputStream(mTargetFile);
////
////							byte[] buffer = new byte[1024 * 4];
////							int a;
////							while((a = input.read(buffer)) > 0)
////								output.write(buffer, 0, a);
////							
////							input.close();
////							output.close();
////							
////							addToLoadQueue();
////						} catch (Throwable e) {
////							JMLog.ex(e);
////							onCreated(null);
////						}
////					}
////				})
////				.start();
//				
//				mRequester_Downloader = Manager_Downloader.createBJ(mKey.mKeyType, mKey.mKey)
//				.addOnDownloadedListener(new OnDownloadListener() {
//					@Override
//					public void onDownload(BJ_Download pRequester) {
//						if(pRequester.mIsReceived == true) {
//							mProgress = 80;
//							mRequester_Downloader = null;
//							addToLoadQueue();
//						} else {
//							onCreated(null);
//						}
//					}
//				})
//				.start();
//				break;
//			case WebURL:
//				mRequester_Downloader = Manager_Downloader.createBJ_Url(mKey.mKey)
//				.addOnDownloadedListener(new OnDownloadListener() {
//					@Override
//					public void onDownload(BJ_Download pRequester) {
//						if(pRequester.mIsReceived == true) {
//							mProgress = 80;
//							mRequester_Downloader = null;
//							addToLoadQueue();
//						} else {
//							onCreated(null);
//						}
//					}
//				})
//				.start();
//				break;
//			case S3Key_FromS3:
//			case S3Key_FromCloudFront:
//				mRequester_Downloader = Manager_Downloader.createBJ_S3(mKey.mKey, false, mKey.mKeyType)
//				.addOnDownloadedListener(new OnDownloadListener() {
//					@Override
//					public void onDownload(BJ_Download pRequester) {
//						if(pRequester.mIsReceived == true) {
//							mProgress = 80;
//							mRequester_Downloader = null;
//							addToLoadQueue();
//						} else {
//							onCreated(null);
//						}
//					}
//				})
//				.start();
//				break;
//			default :
//				addToLoadQueue();
//				break;
//			}
			mRequester_Downloader = Manager_Downloader.createBJ(mKey, mTargetFile)
				.addOnDownloadedListener(new OnDownloadListener() {
					@Override
					public void onDownload(BJ_Download pRequester) {
						if(pRequester.mIsReceived == true) {
							mProgress = 80;
							mRequester_Downloader = null;
							addToLoadQueue();
						} else {
							onCreated(null);
						}
					}
				})
				.start();
			
			return this;
		}
		
		@Override
		public void run() {
			mListener.onCreated(mView, this);
		}

	}
	
//	public static void setS3Bitmap(Object pObject, ImageView pImageView, SNUser pUser) {
//		String lKey = pUser.getS3Key_User_Image(Tool_App.dp(100f));
//		setS3Bitmap_Final(pObject, pImageView, lKey, Bitmap_KeyType.S3Key_FromS3, Manager_File.getFile_Cache(CacheFileType.S3, lKey), -1, -1, pImageView.getContext().getResources().getDrawable(Manager_Login.getDefaultThumbnailResID()), null);
//	}
//	public static void setS3Bitmap(Object pObject, ImageView pImageView, SNSong pSong, int pSize, int pDefaultResID) {
//		String lKey = pSong.getS3Key_JacketImage(pSize);
//		if(lKey != null)
//			setS3Bitmap_Final(pObject, pImageView, lKey, Bitmap_KeyType.S3Key_FromCloudFront, Manager_File.getFile_Cache(CacheFileType.S3, lKey), -1, -1, pImageView.getContext().getResources().getDrawable(pDefaultResID), null);
//		else
//			pImageView.setImageResource(pDefaultResID);
//	}
	
	@Deprecated
	public static void setS3Bitmap(Object pObject, ImageView pImageView, String pS3Key) {
		setS3Bitmap_Final(pObject, pImageView, pS3Key, Bitmap_KeyType.S3Key_FromCloudFront, Manager_File.getFile_Cache(CacheFileType.S3, pS3Key), -1, -1, null, null);
	}
	@Deprecated
	public static void setS3Bitmap(Object pObject, ImageView pImageView, String pS3Key, int pDefaultResID) {
		setS3Bitmap_Final(pObject, pImageView, pS3Key, Bitmap_KeyType.S3Key_FromCloudFront, Manager_File.getFile_Cache(CacheFileType.S3, pS3Key), -1, -1, pImageView.getContext().getResources().getDrawable(pDefaultResID), null);
	}
	@Deprecated
	private static void setS3Bitmap_Final(Object pObject, ImageView pImageView, String pKey, Bitmap_KeyType pKeyType, File pTargetFile, int pMaxWidth, int pMaxHeight, Drawable pDefaultDrawable, OnCreatedBitmapListener<ImageView> pListener) {
		BitmapKey bk = BitmapKey.create(pKeyType, pKey, -1, -1);
		pImageView.setTag(bk);
		synchronized (pObject) {
			if(JMManager_Bitmap.isAcquired(pObject, pTargetFile.getPath())) {
				Bitmap bm = JMManager_Bitmap.acquire(pObject, pTargetFile.getPath(), pMaxWidth, pMaxHeight);
				pImageView.setImageBitmap(bm);
//				 pImageView.setImageDrawable(new MLDrawable(bm));
				return;
			}
		}
		pImageView.setImageDrawable(pDefaultDrawable);
		
		BJ_BitmapLoader<ImageView> r = createRequester(pObject, pImageView, bk, pTargetFile);
		r.mMaxWidth = pMaxWidth;
		r.mMaxHeight = pMaxHeight;
		
		if(pListener == null) {
			r.setOnCreatedListener(new OnCreatedBitmapListener<ImageView>() {
				@Override
				public void onCreated(ImageView pView, BJ_BitmapLoader<ImageView> pRequester) {
					if(pRequester.mBitmap == null || pRequester.mBitmap.isRecycled() == true)
						return;
					
					// 일치할 경우
					if(pView.getTag() != null && ((BitmapKey)pView.getTag()) == pRequester.mKey) {
						boolean issame = false;
						// log("setBitmap "+pRequester.mRequested_S3Key+pView.getAnimation());
						if(pView.getDrawable() != null) {
							// log("setBitmap "+((BitmapDrawable)pView.getDrawable()).getBitmap()+", "+pRequester.mBitmap);
							if(pView.getDrawable() instanceof BitmapDrawable) {
								if(((BitmapDrawable)pView.getDrawable()).getBitmap() == pRequester.mBitmap) {
									issame = true;
								}
							}
							else if(pView.getDrawable() instanceof RobustDrawable) {
								if(((RobustDrawable)pView.getDrawable()).getBitmap() == pRequester.mBitmap) {
									issame = true;
								}
							}
						}
						if(issame == false) {
							//							log("setBitmap on View:"+pView+" Bitmap:"+pRequester.mBitmap+" isRecycled:"+pRequester.mBitmap.isRecycled()+" AcquiredSize:"+JMManager_Bitmap.getAcquiredObjects(pRequester.mBitmap).size());
							pView.setImageBitmap(pRequester.mBitmap);
							//pView.setImageDrawable(new MLDrawable(pRequester.mBitmap));
							Animation ca = pView.getAnimation();
							if(ca != null) {
								ca.reset();
								ca.start();
							} else {
								AlphaAnimation a = new AlphaAnimation(0f, 1f);
								a.setDuration(400);
								pView.startAnimation(a);
							}
						}
					} else {
						if(pRequester.mBitmap != null && pRequester.mObject instanceof MLContent == false) {
							JMManager_Bitmap.release(pRequester.mObject, pRequester.mBitmap);
							//							log("releaseBitmap on S3Key:"+pRequester.mRequested_S3Key+" Bitmap:"+pRequester.mBitmap+" isRecycled:"+pRequester.mBitmap.isRecycled()+" Object:"+pRequester.mObject);
						}
					}
				}
			});
		} else {
			r.setOnCreatedListener(pListener);
		}
		
//		r.start(CheckImageFileMD5Ratio);
		r.start();
	}
	
	@Deprecated
	public static void release(Object pObject, Bitmap pBitmap) {
		log("TAA release "+pObject+", "+pBitmap);
		JMManager_Bitmap.release(pObject, pBitmap);
	}
	@Deprecated
	public static void releaseAll(Object pObject) {
		log("TAA releaseAll "+pObject);
//		for(Bitmap bm : JMManager_Bitmap.getAcquiredBitmaps(pObject)) {
//			// JMManager_Bitmap.getAcquiredObjects(bm).size()
//		}
		JMManager_Bitmap.releaseAll(pObject);
	}
	
	
	
	
	
	@Deprecated
	public static File resizeBitmap(MLContent pMLContent, Uri pUri) throws Exception {
		String [] proj={ MediaStore.Images.Media.DATA };
		Cursor cursor = pMLContent.getMLActivity().getContentResolver().query(pUri, proj, null, null, null); 
		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();
		String path = cursor.getString(column_index);
		
		return resizeBitmap(pMLContent, path);
	}
	@Deprecated
	public static File resizeBitmap(MLContent pMLContent, String pFilePath) throws Exception {
		FileInputStream in = new FileInputStream(pFilePath);
		
		ExifInterface exif = new ExifInterface(pFilePath);
		int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		
		// Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(in, null, o);
		in.close();
		
		int scale = 1;
		while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > Manager_Login.IMAGE_MAX_SIZE) {
			scale++;
		}
		
		Bitmap b = null;
		in = new FileInputStream(pFilePath);
		if (scale > 1) {
			scale--;
			// scale to max possible inSampleSize that still yields an image
			// larger than target
			o = new BitmapFactory.Options();
			o.inSampleSize = scale;
			b = BitmapFactory.decodeStream(in, null, o);
			
			// resize to desired dimensions
			int width = b.getWidth();
			int height = b.getHeight();
			
			double y = Math.sqrt(Manager_Login.IMAGE_MAX_SIZE / (((double) width) / height));
			double x = (y / height) * width;
			
			Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, (int) x, (int) y, true);
			b.recycle();
			b = scaledBitmap;
			
			if(orientation != ExifInterface.ORIENTATION_NORMAL) {
				Matrix matrix = new Matrix();
				if(orientation == ExifInterface.ORIENTATION_ROTATE_90) {
					matrix.postRotate(90);
				} else if(orientation == ExifInterface.ORIENTATION_ROTATE_180) {
					matrix.postRotate(180);
				} else if(orientation == ExifInterface.ORIENTATION_ROTATE_270) {
					matrix.postRotate(270);
				}
				
				scaledBitmap = Bitmap.createBitmap(b, 0, 0, (int)x, (int)y, matrix, true);
				b.recycle();
				b = scaledBitmap;
			}
			
			System.gc();
		} else {
			b = BitmapFactory.decodeStream(in);
		}
		in.close();
		
		File ret = Manager_File.createTempCacheFile();
		FileOutputStream fos = new FileOutputStream(ret);
		b.compress(CompressFormat.JPEG, 100, fos);
		fos.close();
		
		return ret;
	}
}
