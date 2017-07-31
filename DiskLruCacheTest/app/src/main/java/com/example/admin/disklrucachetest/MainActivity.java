package com.example.admin.disklrucachetest;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.admin.disklrucachetest.libcore.io.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private ImageView mImage;//imageview
    private TextView TextSpace;
    private Double space;//统计占用缓存空间

    private String TAG="Mainactivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImage= (ImageView) findViewById(R.id.photoimage);
        TextSpace= (TextView) findViewById(R.id.photospace);

        DiskLruCache mDiskLruCache = null;
        try {
            /**
             * 判断该路径是否存在，不存在就创建
             */
            File cacheDir = getDiskCacheDir(getApplicationContext(), "bitmap");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            Log.i(TAG,cacheDir+"");
            /**
             * open方法创建缓存实例
             */
            mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(getApplicationContext()), 1, 10 * 1024 * 1024);


        } catch (IOException e) {
            e.printStackTrace();
        }
        final DiskLruCache finalMDiskLruCache = mDiskLruCache;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /**
                     * 使用DiskLruCache.Editor进行写入
                     */

                    String imageUrl = "http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
                    String key = hashKeyForDisk(imageUrl);
                    DiskLruCache.Editor editor = finalMDiskLruCache.edit(key);
                    Log.i(TAG,key);
                    if (editor != null) {
                        OutputStream outputStream = editor.newOutputStream(0);
                        Log.i(TAG,editor+"");
                        if (downloadUrlToStream(imageUrl, outputStream)) {
                            Log.i(TAG,outputStream+"");
                            editor.commit();
                        } else {
                            editor.abort();
                        }
                    }
                    /**
                     * 将内存中的操作记录同步到日志文件（也就是journal文件）当中
                     * 建议Activity的onPause()方法中去调用一次flush()方法
                     */


                    finalMDiskLruCache.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        Toast.makeText(MainActivity.this,"缓存完成",Toast.LENGTH_LONG).show();


        /**
         * 读取缓存
         */
        try {
            String imageUrl = "http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
            String key = hashKeyForDisk(imageUrl);
            DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
            if (snapShot != null) {
                InputStream is = snapShot.getInputStream(0);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                mImage.setImageBitmap(bitmap);
                space=(double)mDiskLruCache.size()/1048576;
                DecimalFormat df=new DecimalFormat("######0.00");

                TextSpace.setText((String.valueOf(df.format(space)))+"MB");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        /**
         * 移除缓存
         */
        /*try {
            String imageUrl = "http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
            String key = hashKeyForDisk(imageUrl);
            mDiskLruCache.remove(key);
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        /**
         * close()通常只应该在Activity的onDestroy()方法中去调用close()方法
         * delete()这个方法用于将所有的缓存数据全部删除
         */
    }

    /**
     * 获取缓存路径
     * @param context
     * @param uniqueName 对于不同数据进行区分的唯一值
     * @return
     */

    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        /**
         * 当SD卡存在或者SD卡不可被移除的时候，就调用getExternalCacheDir()方法来获取缓存路径，否则就调用getCacheDir()方法来获取缓存路径。
         */
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 获取app版本号，manifest中设置版本号
     * 每当版本号改变，缓存路径下存储的所有数据都会被清除掉
     * @param context
     * @return
     */
    public int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }


    /**
     * 写入缓存
     * @param urlString
     * @param outputStream
     * @return
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * MD5编码
     * @param key 缓存文件文件名
     * @return
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

}
