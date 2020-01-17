package com.example.meidacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaRecorder {

    private MediaMuxer mMediaMuxer;
    private MediaCodec mMediaCodec;
    private int track;
    private String mPath;
    private int mWidth;
    private int mHeight;
    private Handler mHandler;

    private boolean isStart = false;
    private long mStartTime = 0;


    public void setOutputFile(String path) {
        mPath = path;
    }

    public void setVideoSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void prepare() throws IOException {

        //1.创建视频复用器，组装音视频，输出支持mp4,3pg两种格式
        mMediaMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        //创建编码器
        mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1500_000);  //码率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 25);      //帧率
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10); //关键帧间隔，直播关键帧间隔大会等待时间长
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);


        //准备控制变量与线程
        isStart = true;
        mStartTime = -1;
        HandlerThread handlerThread = new HandlerThread("videoCodec");
        handlerThread.start();
        //这个mHandler所在线程是子线程
        mHandler = new Handler(handlerThread.getLooper());//一个封装了Looper的线程，可以用主线程给子线程发消息。

    }

    public void start() {
        mMediaCodec.start();
    }

    public void addFrame(final byte[] data) {
        if (!isStart) {
            return;
        }

        /**
         * Handler的post和sendMessage的区别
         *
         * post和sendMessage功能其实差不多，post其实也是通过sendMessage来实现的，都是发送消息到Handler所在的
         * 线程的消息队列中post的用法更方便，经常会post一个Runnable，处理的代码直接写在Runnable的run方法中，
         * 其实就是将这个Runnable发送到Handler所在线程（一般是主线程）的消息队列中。sendMessage方法主线程处
         * 理方法一般则是写在handleMessage中。
         *
         */
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                long pts;
                if (mStartTime == -1) {
                    mStartTime = System.nanoTime();
                    pts = 0;
                } else {
                    pts = (System.nanoTime() - mStartTime) / 1000;
                }

                /**
                 * 获取可使用缓冲区位置得到索引
                 *
                 * 如果存在可用的缓冲区，此方法会返回其位置索引，否则返回-1，参数为超时时间，
                 * 单位是毫秒，如果此参数是0，则立即返回，如果参数小于0，则无限等待直到有可
                 * 使用的缓冲区，如果参数大于0，则等待时间为传入的毫秒值。
                 */
                int index = mMediaCodec.dequeueInputBuffer(2_000);

                if (index > 0) {
                    //传入原始数据
                    ByteBuffer inBuf = mMediaCodec.getInputBuffer(index);
                    inBuf.clear();
                    inBuf.put(data); //向缓冲区中填写待编码YUV数据
                    //将带有数据的缓冲区送回到缓冲区队列中去
                    mMediaCodec.queueInputBuffer(index, 0, data.length, pts, 0);
                }

                /**
                 * 5.获取其输出数据，获取输入原始数据和获取输出数据最好是异步进行，
                 * 因为输入一帧数据不代表编码器马上就会输出对应的编码数据，可能输入
                 * 好几帧才会输出一帧。获取输出数据的步骤与输入数据的步骤相似：
                 */
                codec();
            }
        });
    }

    private void codec() {
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //获取可用的输出缓冲区  参数一是一个BufferInfo类型的实例,参数二为超时时间，负数代表无限等待
            int encoderStatus = mMediaCodec.dequeueOutputBuffer(bufferInfo, 2_000);

            //稍后重试：1、需要更多数据才能编码一个图像（帧）2、编码是一个耗时操作，需要更多点时间，现在还没编码完
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
                //可以开始编码了
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                track = mMediaMuxer.addTrack(newFormat);
                mMediaMuxer.start();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

            } else {
                ByteBuffer outBuf = mMediaCodec.getOutputBuffer(encoderStatus);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    continue;
                }
                if (bufferInfo.size != 0) {
                    bufferInfo.presentationTimeUs = (long) (bufferInfo.presentationTimeUs / 2);
                    outBuf.position(bufferInfo.offset);
                    outBuf.limit(bufferInfo.offset + bufferInfo.size);
                    mMediaMuxer.writeSampleData(track, outBuf, bufferInfo);
                }

                //释放输出缓冲区
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        }
    }
}
