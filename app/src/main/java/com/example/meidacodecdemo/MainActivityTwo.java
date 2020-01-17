package com.example.meidacodecdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author zhoguang@unipus.cn
 * @date 2020/1/13 16:13
 */
public class MainActivityTwo extends AppCompatActivity {

    private GLSurfaceView mSurfaceView;
    private GLSurfaceView.Renderer mRender;
    private TextView mTvTake;
    private TextView mTvChangeCamera;
    private Camera mCamera;
    private String savePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/";
    private SurfaceTexture mSurfaceTexture;
    private DirectDrawer mDirectDrawer;
    private int mTextureID;
    private CameraInfo mCameraInfo = new CameraInfo();
    private int mCameraId = CameraInfo.CAMERA_FACING_BACK;
    private String TAG = MainActivity.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main_2);

        mSurfaceView = findViewById(R.id.surfaceview);


        mTvTake = findViewById(R.id.tv_take);
        mTvChangeCamera = findViewById(R.id.tv_change_camera);


        //GLSurfaceview.Renderer相当于SurfaceView的 SurfaceHolder.Callback
        mRender = new GLSurfaceView.Renderer() {

            //在onSurfaceCreated()函数中，创建一个渲染的纹理，这个纹理就是用来显示Camera的图像，所以需要新创建
            //的SurfaceTexture绑定在一起，而SurfaceTexture是作为渲染的载体，另一方面需要和DirectDrawer绑定在一起，
            // DirectDrawer是用来绘制图像的
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                mTextureID = createTextureID();
                mSurfaceTexture = new SurfaceTexture(mTextureID);
                mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    //函数onFrameAvailable()在有新数据到来时，会被调用，在其中调用requestRender()，就可以完成新数据的渲染。
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        mSurfaceView.requestRender();
                    }
                });
                mDirectDrawer = new DirectDrawer(mTextureID);

                //打开相机
                requestPermission();
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                startPreview(mSurfaceTexture);
            }

            @Override
            public void onDrawFrame(GL10 gl) {

                GLES20.glClearColor(0, 0, 0, 0);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                //从图像流中将纹理图像更新为最近的帧
                mSurfaceTexture.updateTexImage();

                mDirectDrawer.draw(mCameraInfo.facing);
            }
        };


        /**
         * 如果说GLSurfaceView是画布，那么仅仅有一张白纸是没用的，我们还需要一支画笔，
         * Renderer的功能就是这里说的画笔。Renderer是一个接口，主要包含3个抽象的函数：
         * onSurfaceCreated、onDrawFrame、onSurfaceChanged，从名字就可以看出，分别是
         * 在SurfaceView创建、视图大小发生改变和绘制图形时调用。
         *
         */
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(mRender);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

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
                    switchCamera();
                    startPreview(mSurfaceTexture);
                } else {
                    mTvChangeCamera.setText("前置摄像头");
                    switchCamera();
                    startPreview(mSurfaceTexture);
                }
            }
        });
    }

    //java运算符 与（&）、非（~）、或（|）、异或（^）
    //https://www.cnblogs.com/jpfss/p/11014780.html
    public void switchCamera() {
        if (mCamera != null) {
            releaseCamera();
        }

        //两个数转为二进制，然后从高位开始比较，如果相同则为0，不相同则为1
        mCameraId = mCameraId ^ 1;

        Log.e("xx", "切换" + (mCameraId == 0 ? "后" : "前") + "摄像头:" + System.currentTimeMillis());
        openCamera();
    }

    public void releaseCamera() {
        if (mCamera != null) {
            Log.v(TAG, "releaseCamera");
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    //给GLSurfaceView用的方法
    public void startPreview(SurfaceTexture surface) {

        if (mCamera != null) {
            Log.v(TAG, "startPreview");
            try {
                mCamera.setPreviewTexture(surface);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.startPreview();
        }
    }

    private void openCamera() {
        if (mCamera == null) {
            mCamera = Camera.open(mCameraId);
        }

        //信息放在了mCamerInfo里面了
        Camera.getCameraInfo(mCameraId, mCameraInfo);

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> cameraSizeList = parameters.getSupportedPictureSizes();
        for (Camera.Size size : cameraSizeList) {
            Log.e("xx", "相机支持的size：" + size.width + ":" + size.height + "比例:" + size.width / (double) size.height);
        }

        //取一个与手机屏幕最接近的拍照比例
        double ratio = UIHelper.getScreenPixHeight(this) / (double) UIHelper.getScreenPixWidth(this);
        Log.e("xx", "屏幕比例:" + ratio);
        Camera.Size bestSize = getBestRatio(ratio, cameraSizeList);

        parameters.setPictureSize(bestSize.width, bestSize.height);
        parameters.setPreviewSize(bestSize.width, bestSize.height);
        parameters.setPictureFormat(PixelFormat.JPEG);
        mCamera.setParameters(parameters);

        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mCamera.setDisplayOrientation(270);
        } else {  // back-facing
            mCamera.setDisplayOrientation(90);
        }
    }

    public static int createTextureID() {
        int[] tex = new int[1];
        //生成一个纹理
        GLES20.glGenTextures(1, tex, 0);
        //将此纹理绑定到外部纹理上
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        //设置纹理过滤参数
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        //解除纹理绑定
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return tex[0];
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
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            bitmap = rotaingImageView(mCameraId, 90, bitmap);
        } else {
            bitmap = rotaingImageView(mCameraId, 270, bitmap);
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
            MediaScannerConnection.scanFile(MainActivityTwo.this, new String[]{image.getPath()}, null, null);
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

        if (ContextCompat.checkSelfPermission(MainActivityTwo.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivityTwo.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            Log.i("TEST", "Granted");
            openCamera();
        } else {
            ActivityCompat.requestPermissions(MainActivityTwo.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);//1 can be another integer
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            openCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
    }
}
