package com.example.iluminadordeaudio.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecoder {

    data class DecodeResult(val pcm: ShortArray, val sampleRate: Int)

    fun decodeToPcm(context: Context, uri: Uri): DecodeResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: error("No se encontró pista de audio en el archivo")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val rawSamples = mutableListOf<Short>()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputIdx = codec.dequeueInputBuffer(10_000L)
                if (inputIdx >= 0) {
                    val buf = codec.getInputBuffer(inputIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inputIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIdx = codec.dequeueOutputBuffer(info, 10_000L)
            if (outputIdx >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outputIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    outBuf.get(bytes)
                    val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shorts.hasRemaining()) rawSamples.add(shorts.get())
                }
                codec.releaseOutputBuffer(outputIdx, false)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val raw = rawSamples.toShortArray()
        // Mix multicanal a mono
        val mono = if (channelCount > 1) {
            ShortArray(raw.size / channelCount) { i ->
                val sum = (0 until channelCount).sumOf { ch -> raw[i * channelCount + ch].toInt() }
                (sum / channelCount).toShort()
            }
        } else raw

        return DecodeResult(mono, sampleRate)
    }
}
