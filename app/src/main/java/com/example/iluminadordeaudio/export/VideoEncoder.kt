package com.example.iluminadordeaudio.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File

/**
 * Codifica frames dibujados en [inputSurface] como H.264 y los muxea en un MP4
 * de video-only (sin audio). El audio se añade en un segundo paso en VideoExporter.
 */
class VideoEncoder(
    width: Int,
    height: Int,
    fps: Int,
    outputFile: File
) {
    private val codec: MediaCodec
    val inputSurface: Surface
    private val muxer: MediaMuxer
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    /**
     * Drena la cola de salida del encoder hacia el muxer.
     * Llamar con [endOfStream] = true al terminar todos los frames.
     */
    fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) codec.signalEndOfInputStream()

        loop@ while (true) {
            val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break@loop
                }
                outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Formato de salida cambió dos veces" }
                    videoTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIdx >= 0 -> {
                    // Descartar SPS/PPS (ya van embebidos en el stream)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outputIdx, false)
                        continue@loop
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        val outBuf = codec.getOutputBuffer(outputIdx)!!
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                }
            }
        }
    }

    fun release() {
        codec.stop()
        codec.release()
        inputSurface.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }
}
