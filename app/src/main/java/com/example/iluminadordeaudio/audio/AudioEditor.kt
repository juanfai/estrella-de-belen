package com.example.iluminadordeaudio.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object AudioEditor {

    /** Corta el audio hasta [endFraction × duración] y devuelve el último PTS real escrito. */
    fun trimTo(source: File, output: File, endFraction: Float): Long {
        val totalUs = getDurationUs(source)
        val endUs   = (endFraction * totalUs).toLong()

        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        val trackIdx = findAudioTrack(extractor) ?: return 0L
        extractor.selectTrack(trackIdx)

        val muxer    = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxTrack = muxer.addTrack(extractor.getTrackFormat(trackIdx))
        muxer.start()

        val buffer = ByteBuffer.allocate(512 * 1024)
        val info   = MediaCodec.BufferInfo()
        var lastPts = 0L

        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val pts = extractor.sampleTime
            if (pts > endUs) break
            info.offset = 0; info.size = size
            info.presentationTimeUs = pts
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxTrack, buffer, info)
            lastPts = pts
            extractor.advance()
        }

        extractor.release(); muxer.stop(); muxer.release()
        return lastPts
    }

    /** Concatena file1 + file2 → output.
     *  Calcula el offset automáticamente a partir de la duración real de file1. */
    fun concatenate(file1: File, file2: File, output: File) {
        val dur1Us = getDurationUs(file1)

        val ext1 = MediaExtractor().apply { setDataSource(file1.absolutePath) }
        val ext2 = MediaExtractor().apply { setDataSource(file2.absolutePath) }

        val t1 = findAudioTrack(ext1) ?: return
        val t2 = findAudioTrack(ext2) ?: return
        ext1.selectTrack(t1); ext2.selectTrack(t2)

        val fmt2     = ext2.getTrackFormat(t2)
        val sr       = fmt2.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val frameUs  = 1_024_000_000L / sr
        val offset   = dur1Us + frameUs

        val muxer    = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxTrack = muxer.addTrack(ext1.getTrackFormat(t1))
        muxer.start()

        val buffer = ByteBuffer.allocate(512 * 1024)
        val info   = MediaCodec.BufferInfo()

        // file1
        while (true) {
            val size = ext1.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0; info.size = size
            info.presentationTimeUs = ext1.sampleTime
            info.flags = ext1.sampleFlags
            muxer.writeSampleData(muxTrack, buffer, info)
            ext1.advance()
        }
        ext1.release()

        // file2 con offset de tiempo
        while (true) {
            val size = ext2.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0; info.size = size
            info.presentationTimeUs = ext2.sampleTime + offset
            info.flags = ext2.sampleFlags
            muxer.writeSampleData(muxTrack, buffer, info)
            ext2.advance()
        }
        ext2.release()

        muxer.stop(); muxer.release()
    }

    fun getDurationUs(file: File): Long {
        val ret = MediaMetadataRetriever()
        return try {
            ret.setDataSource(file.absolutePath)
            (ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L) * 1000L
        } catch (_: Exception) { 0L } finally { ret.release() }
    }

    private fun findAudioTrack(ext: MediaExtractor): Int? =
        (0 until ext.trackCount).firstOrNull {
            ext.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
}
