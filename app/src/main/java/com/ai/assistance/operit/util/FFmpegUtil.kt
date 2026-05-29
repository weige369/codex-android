package com.ai.assistance.operit.util

import java.io.File

class StreamInfo(
    val index: Int = 0,
    val type: String = "",
    val codec: String = "",
    val resolution: String = "",
    val bitrate: String = ""
)

class MediaInfo(
    val format: String = "",
    val duration: String = "0",
    val durationMs: Long = 0L,
    val bitrate: String = "0",
    val width: Int = 0,
    val height: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val codec: String = "",
    val rotation: Int = 0,
    val streams: List<StreamInfo> = emptyList(),
    val videoStreams: List<StreamInfo> = emptyList(),
    val audioStreams: List<StreamInfo> = emptyList()
)

object FFmpegUtil {
    private const val TAG = "FFmpegUtilStub"
    private const val STUB_NO_FFMPEG = "FFmpeg not available in this build"

    fun isAvailable(): Boolean = false
    fun executeCommand(cmd: String): Boolean = false
    fun executeCommandWithOutput(cmd: String): String? = null
    fun getMediaInfo(path: String): MediaInfo? = MediaInfo()
    fun getVideoThumbnail(path: String, outputPath: String, maxWidth: Int = 640): Boolean = false
    fun scaleFilterMaxWidth(maxWidth: Int): String = "scale='min($maxWidth,iw)':min'($maxWidth,ih)':force_original_aspect_ratio=decrease"
    fun getFrameAtTime(path: String, timeMs: Long, outputPath: String): Boolean = false
    fun convertToWav(input: String, output: String): Boolean = false
    fun concatVideos(inputs: List<String>, output: String): Boolean = false
}
