package com.baidu.iknow.imageloader.request;

import java.io.IOException;
import java.io.InputStream;

import com.baidu.iknow.imageloader.cache.PoolingByteArrayOutputStream;
import com.baidu.iknow.imageloader.cache.UrlSizeKey;
import com.baidu.iknow.imageloader.cache.DiskLruCache.Snapshot;
import com.baidu.iknow.imageloader.decoder.BaseDecoder;
import com.baidu.iknow.imageloader.decoder.DecodeInfo;
import com.baidu.iknow.imageloader.decoder.DecoderFactory;
import com.baidu.iknow.imageloader.drawable.CustomDrawable;
import com.baidu.iknow.imageloader.request.DataFetcher.DataCallback;

/**
 * 图片加载任务
 *
 * @author zhaoxuyang
 * @since 2015-10-12
 */
public class ImageLoadTask extends AsyncTask<UrlSizeKey, Integer, CustomDrawable> {

    private static final String TAG = ImageLoadTask.class.getSimpleName();

    public static final  int RETYR_MAX_COUNT = 1;

    public ImageLoadingListener mImageLoadingListener;

    public UrlSizeKey mKey;

    private DecodeInfo mDecodeInfo;

    public int retryCount;

    private DataFetcher<InputStream> mHttpUrlFetcher;

    private Exception mException;

    public ImageLoadTask(){
        mDecodeInfo = new DecodeInfo.DecodeInfoBuilder().build();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mImageLoadingListener != null) {
            mImageLoadingListener.onLoadingStarted(mKey);
        }
    }

    @Override
    protected CustomDrawable doInBackground(UrlSizeKey... params) {
        Snapshot ss = ImageLoader.getInstance().mDiskLruCache.get(mKey.mUrl.toString());
        if (ss == null) {
            fetchDataFromNet();
            ss = ImageLoader.getInstance().mDiskLruCache.get(mKey.mUrl.toString());
        }

        if (ss == null) {
            if (mException == null) {
                mException = new Exception("save sdcard failed");
            }
            return null;
        }
        return doDecode(ss);
    }

    @Override
    protected void onPostExecute(CustomDrawable result) {
        super.onPostExecute(result);
        if (mImageLoadingListener != null) {
            if (result != null) {
                mImageLoadingListener.onLoadingComplete(mKey, result, false);
            } else {
                mImageLoadingListener.onLoadingFailed(mKey, mException);
            }
        }
        if (mKey != null) {
            mKey.recycle();
            mKey = null;
        }
    }

    @Override
    protected void onCancelled(CustomDrawable result) {
        super.onCancelled(result);
        if (mHttpUrlFetcher != null) {
            mHttpUrlFetcher.cancel();
        }
        if (mImageLoadingListener != null) {
            if(result!=null){
                mImageLoadingListener.onLoadingComplete(mKey,result,false);
            }else{
                mImageLoadingListener.onLoadingCancelled(mKey);
            }

        }
        if (mKey != null) {
            mKey.recycle();
            mKey = null;
        }
    }

    private void fetchDataFromNet() {

        mHttpUrlFetcher = DataFatcherFactory.getNetworkDataFetcher(mKey);
        mHttpUrlFetcher.loadData(new DataCallback<InputStream>() {

            @Override
            public void onDataReady(InputStream data) {
                ImageLoader.getInstance().mDiskLruCache.put(mKey.mUrl.toString(), data);
                if (data != null) {
                    try {
                        data.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onLoadFailed(Exception e) {
                mException = e;
            }
        });

    }

    private CustomDrawable doDecode(Snapshot ss) {
        if (ss == null) {
            return null;
        }
        InputStream is = ss.getInputStream(0);
        PoolingByteArrayOutputStream baos = new PoolingByteArrayOutputStream(ImageLoader.getInstance().mByteArrayPool);
        try {
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = is.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            byte[] bytes = baos.toByteArray();
            BaseDecoder decoder = DecoderFactory.getDecoder(bytes);
            return decoder.doDecode(bytes, mDecodeInfo, mKey.mViewWidth, mKey.mViewHeight);
        } catch (IOException e) {
            mException = e;
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                ss.close();
            }

        }
        return null;
    }

}
