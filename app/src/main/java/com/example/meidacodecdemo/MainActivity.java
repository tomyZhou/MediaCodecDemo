package com.example.meidacodecdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author zhoguang@unipus.cn
 * @date 2020/1/13 16:13
 */
public class MainActivity extends AppCompatActivity {

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private TextView mTvTake;
    private TextView mTvChangeCamera;
    private Camera mCamera;
    private String savePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/";
    private int cameraType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        mSurfaceView = findViewById(R.id.surfaceview);
        mSurfaceHolder = mSurfaceView.getHolder();

        mTvTake = findViewById(R.id.tv_take);
        mTvChangeCamera = findViewById(R.id.tv_change_camera);


        SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

            //当surface创建(第一次进入当前页面)或者重新创建(切换后台再进入)的时候调用。
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                requestPermission();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                releaseCamera();
            }
        };
        mSurfaceHolder.addCallback(surfaceCallback);

        mTvTake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("xx", Camera.getNumberOfCameras() + "个摄像头");

                takePhoto();
            }
        });

        mTvChangeCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTvChangeCamera.getText().toString().equals("前置摄像头")) {
                    mTvChangeCamera.setText("后置摄像头");
                    cameraType = 1;
                    releaseCamera();
                    openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
                } else {
                    mTvChangeCamera.setText("前置摄像头");
                    cameraType = 0;
                    releaseCamera();
                    openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
                }
            }
        });
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void takePhoto() {

        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            //自动聚焦完成后拍照
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success && camera != null) {
                    mCamera.takePicture(new ShutterCallback(), null, new Camera.PictureCallback() {
                        //拍照回调接口
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            //这里可以对data加处理。 比如加个水印
                            savePhoto(data);
                            //停止预览
                            mCamera.stopPreview();
                            //重启预览
                            mCamera.startPreview();
                        }
                    });
                }
            }
        });
    }

    /**
     * 保存照片
     *
     * @param data
     */
    private void savePhoto(byte[] data) {
        //  //将data 转换为位图 或者你也可以直接保存为文件使用 FileOutputStream，这里用BitmapFactory
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (cameraType == Camera.CameraInfo.CAMERA_FACING_BACK) {
            bitmap = rotaingImageView(cameraType, 90, bitmap);
        } else {
            bitmap = rotaingImageView(cameraType, 270, bitmap);
        }

        String imageName = System.currentTimeMillis() + ".jpg";

        File dir = new File(savePath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File image = new File(dir + imageName);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(image));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            //通知重新扫描，更新到相册中
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{image.getPath()}, null, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 因为摄像头默认是横屏的，所以后置摄像头拍出来的照片需要旋转90度转正
     *
     * @param angle 旋转角度
     * @return bitmap 图片
     */
    private Bitmap rotaingImageView(int id, int angle, Bitmap bitmap) {
        //矩阵
        Matrix matrix = new Matrix();
        //照片旋转90度
        matrix.postRotate(angle);
        //如果是前置摄像头，还要加入镜像翻转
        if (id == 1) {
            matrix.postScale(-1, 1);
        }
        // 创建新的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return resizedBitmap;
    }


    /**
     * 快门回调接口，如果不想拍照声音，直接将new ShutterCallback()修改为null即可
     */
    private class ShutterCallback implements Camera.ShutterCallback {
        @Override
        public void onShutter() {
            MediaPlayer mPlayer = MediaPlayer.create(getApplicationContext(), R.raw.hit_cup);
            try {
                mPlayer.prepare();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayer.start();
        }
    }

    private void openCamera(int type) {
        if (mCamera == null) {
            mCamera = Camera.open(type);
        }

        Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Log.e("xx", System.currentTimeMillis() + "获得一帧数据:" + data);
            }
        };
        mCamera.setPreviewCallback(callback);


        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> cameraSizeList = parameters.getSupportedPictureSizes();
        for (Camera.Size size : cameraSizeList) {
            Log.e("xx", "相机支持的size：" + size.width + ":" + size.height + "比例:" + size.width / (double) size.height);
        }


        //设置surfaceview的宽高比例与拍摄的图像比例一致
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mSurfaceView.getLayoutParams();

        //取一个与手机屏幕最接近的拍照比例
        double ratio = UIHelper.getScreenPixHeight(this) / (double) UIHelper.getScreenPixWidth(this);
        Log.e("xx", "屏幕比例:" + ratio);
        Camera.Size bestSize = getBestRatio(ratio, cameraSizeList);

        //拍出来的图片尺寸
        parameters.setPictureSize(bestSize.width, bestSize.height);

        //预览尺寸
        parameters.setPreviewSize(bestSize.width, bestSize.height);

//        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setPictureFormat(PixelFormat.JPEG);
        //Android 中Google支持的 Camera Preview Callback的YUV常用格式有两种：一个是NV21，一个是YV12。Android一般默认使用YCbCr_420_SP的格式（NV21）。
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setParameters(parameters);

        //设置surfaceView的比例和拍照的一样
        layoutParams.width = UIHelper.getScreenPixWidth(this);
        layoutParams.height = (int) (UIHelper.getScreenPixWidth(this) * (bestSize.width / (double) bestSize.height));
        mSurfaceView.setLayoutParams(layoutParams);

        //后置摄像头与预览相差90度
        mCamera.setDisplayOrientation(90);
        try {
            //将摄像头获取的数据送到SurfaceHolder中去
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();

    }

    /**
     * 取摄像头支持的拍照比例中与显示屏最接近的比例尺寸
     *
     * @param ratio
     * @param list
     * @return
     */
    private Camera.Size getBestRatio(double ratio, List<Camera.Size> list) {
        Camera.Size bestSize = null;
        double cameraRatio = 1.0;
        double gap = 1;
        for (Camera.Size size : list) {
            cameraRatio = size.width / (double) size.height;
            if (Math.abs(cameraRatio - ratio) < gap) {
                gap = Math.abs(cameraRatio - ratio);
                bestSize = size;
            }
        }
        Log.e("xx", bestSize.width + ":" + bestSize.height + ":" + bestSize.width / (double) bestSize.height);
        return bestSize;

    }


    /**
     * 动态权限，打开摄像头（相机）
     */
    public void requestPermission() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            Log.i("TEST", "Granted");
            openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);//1 can be another integer
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
    }
}
