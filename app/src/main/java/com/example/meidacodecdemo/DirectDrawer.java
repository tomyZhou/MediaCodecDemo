package com.example.meidacodecdemo;

import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 负责将SurfaceTexture（纹理的句柄）内容绘制到屏幕上。
 *
 * @author zhoguang@unipus.cn
 * @date 2020/1/16 10:07
 */
public class DirectDrawer {
    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec2 inputTextureCoordinate;" +
                    "varying vec2 textureCoordinate;" +
                    "void main()" +
                    "{" +
                    "gl_Position = vPosition;" +
                    "textureCoordinate = inputTextureCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform samplerExternalOES s_texture;\n" +
                    "void main() {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    "}";

    private FloatBuffer mVertextBuffer;
    private FloatBuffer mBackTextureBuffer;
    private FloatBuffer mFrontTextureBuffer;
    private ByteBuffer mDrawListBuffer;

    private int mTextureID;
    private int mProgram;
    private int mPositionHandle;
    private int mTextureCoordHandle;


    private static final float VERTEXES[] = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f,
    };


    // 后置摄像头使用的纹理坐标
    private static final float TEXTURE_BACK[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
    };

    // 前置摄像头使用的纹理坐标
    private static final float TEXTURE_FRONT[] = {
            1.0f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    private byte VERTEX_ORDER[] = {0, 1, 2, 3}; // order to draw vertices

    private static final int VERTEX_SIZE  = 2;

    private final int VERTEX_STRIDE  = VERTEX_SIZE  * 4; // 4 bytes per vertex


    public DirectDrawer(int textureId) {
        mTextureID = textureId;
        //init float buffer for vertext coordinates
        mVertextBuffer = ByteBuffer.allocateDirect(VERTEXES.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertextBuffer.put(VERTEXES).position(0);

        //init float buffer for back camera texture coordinates  后置摄像头坐标纹理
        mBackTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_BACK.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mBackTextureBuffer.put(TEXTURE_BACK).position(0);

        //init float buffer for front camera texture coordinates 前置摄像头坐标纹理
        mFrontTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_FRONT.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mFrontTextureBuffer.put(TEXTURE_FRONT).position(0);

        //init byte bufer for draw list
        mDrawListBuffer = ByteBuffer.allocateDirect(VERTEX_ORDER.length).order(ByteOrder.nativeOrder());
        mDrawListBuffer.put(VERTEX_ORDER).position(0);

        //编译着色器
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        // //纹理坐标
        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
    }

    public void draw(int type) {
        GLES20.glUseProgram(mProgram);
        GLES20.glEnable(GLES20.GL_CULL_FACE); // 启动剔除
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID); //绑定纹理

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, VERTEX_SIZE , GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertextBuffer);

        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        if (type == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            GLES20.glVertexAttribPointer(mTextureCoordHandle, VERTEX_SIZE, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mFrontTextureBuffer);
        } else {
            GLES20.glVertexAttribPointer(mTextureCoordHandle, VERTEX_SIZE, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mBackTextureBuffer);
        }

        // 真正绘制的操作
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN,VERTEX_ORDER.length, GLES20.GL_UNSIGNED_BYTE, mDrawListBuffer);

        //结束
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
    }

    //编译着色器
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

}
