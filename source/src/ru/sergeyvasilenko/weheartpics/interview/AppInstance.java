package ru.sergeyvasilenko.weheartpics.interview;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.StatFs;
import com.nostra13.universalimageloader.cache.disc.BaseDiscCache;
import com.nostra13.universalimageloader.cache.disc.impl.TotalSizeLimitedDiscCache;
import com.nostra13.universalimageloader.cache.memory.impl.UsingFreqLimitedMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * User: Serg
 * Date: 23.02.13
 * Time: 14:25
 */
public class AppInstance extends Application {

    private static final int EXTERNAL_DISC_CACHE_SIZE = 20 * 1024 * 1014;
    private static final int INTERNAL_DISC_CACHE_SIZE = 8 * 1024 * 1024;
    private static final int MEMORY_CACHE_SIZE = 4 * 1024 * 1024;

    private static AppInstance sInstance;

    private ContentProvider mContentProvider;

    private boolean mExternalStorageAvailable = false;
    private boolean mExternalStorageWriteable = false;

    private DisplayImageOptions mDisplayImageOptionsNoDiskCache;
    private DisplayImageOptions mDisplayImageOptionsWithDiskCache;

    private Executor mExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Request executor");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        });
        configureImageLoader();
        mContentProvider = new ContentProviderImpl();
    }

    public static AppInstance getInstance() {
        return sInstance;
    }

    public static ContentProvider getContentProvider() {
        return sInstance.mContentProvider;
    }

    public static Executor getExecutor() {
        return sInstance.mExecutor;
    }

    public static DisplayImageOptions getDisplayImageOptions() {
        return getInstance().getDisplayImageOptionsInner();
    }

    public static boolean checkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected() && networkInfo.isAvailable();
    }

    private void configureImageLoader() {
        mDisplayImageOptionsWithDiskCache = new DisplayImageOptions.Builder()
                .cacheInMemory()
                .cacheOnDisc()
                .build();

        mDisplayImageOptionsNoDiskCache = new DisplayImageOptions.Builder()
                .cacheInMemory()
                .build();

        //init disc cache
        BaseDiscCache discCache = null;
        int discCacheSize;
        File cacheDir;
        updateExternalStorageState();
        if (mExternalStorageAvailable && mExternalStorageWriteable) {
            discCacheSize = EXTERNAL_DISC_CACHE_SIZE;
            long externalFreeSpace = getExternalStorageAvailableSpace();
            cacheDir = getExternalCacheDir();
            if (discCacheSize > externalFreeSpace) {
                discCacheSize = (int) (externalFreeSpace - 1024 * 1024);
            }
        } else {
            discCacheSize = INTERNAL_DISC_CACHE_SIZE;
            cacheDir = getCacheDir();
            long internalFreeSpace = getInternalStorageAvailableSpace();
            if (discCacheSize > internalFreeSpace) {
                discCacheSize = (int) (internalFreeSpace - 1024 * 1024);
            }
        }
        if (discCacheSize > 0) {
            discCache = new TotalSizeLimitedDiscCache(cacheDir, discCacheSize);
        }

        //image loader
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(getApplicationContext())
                .threadPoolSize(3) // default
                .threadPriority(Thread.NORM_PRIORITY - 1) // default
                .memoryCache(new UsingFreqLimitedMemoryCache(MEMORY_CACHE_SIZE))
                .discCache(discCache)
                .imageDownloader(new BaseImageDownloader(this)) // default
                .tasksProcessingOrder(QueueProcessingType.LIFO)
                .defaultDisplayImageOptions(getDisplayImageOptionsInner())
                .enableLogging()
                .build();
        ImageLoader.getInstance().init(config);
    }

    private DisplayImageOptions getDisplayImageOptionsInner() {
        updateExternalStorageState();
        if (mExternalStorageAvailable && mExternalStorageWriteable) {
            return mDisplayImageOptionsWithDiskCache;
        } else {
            return mDisplayImageOptionsNoDiskCache;
        }
    }

    private void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
    }

    /**
     * @return Number of bytes available on external storage
     */
    private static long getExternalStorageAvailableSpace() {
        long availableSpace = -1L;
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            stat.restat(Environment.getExternalStorageDirectory().getPath());
            availableSpace = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return availableSpace;
    }

    /**
     * @return Number of bytes available on internal storage
     */
    private static long getInternalStorageAvailableSpace() {
        long availableSpace = -1L;
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            stat.restat(Environment.getDataDirectory().getPath());
            availableSpace = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return availableSpace;
    }
}
