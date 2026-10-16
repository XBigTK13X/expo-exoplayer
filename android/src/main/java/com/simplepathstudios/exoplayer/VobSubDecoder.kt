package com.simplepathstudios.exoplayer

import android.graphics.Bitmap
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleDecoderException

class VobSubDecoder(
    initializationData: List<ByteArray>?,
) : SimpleSubtitleDecoder("VobSubDecoder") {
    private val palette = IntArray(16)
    private var sourceWidth = 720
    private var sourceHeight = 480

    init {
        val defaultColors =
            intArrayOf(
                0x000000,
                0x111111,
                0x222222,
                0x333333,
                0x444444,
                0x555555,
                0x666666,
                0x777777,
                0x888888,
                0x999999,
                0xAAAAAA,
                0xBBBBBB,
                0xCCCCCC,
                0xDDDDDD,
                0xEEEEEE,
                0xFFFFFF,
            )
        for (ii in 0 until 16) {
            palette[ii] = defaultColors[ii]
        }

        if (!initializationData.isNullOrEmpty()) {
            parseIdxMetadata(initializationData[0])
        }
    }

    private fun parseIdxMetadata(metadataBytes: ByteArray) {
        val metadataText = String(metadataBytes, Charsets.UTF_8)
        val lines = metadataText.split("\n", "\r")

        for (lineIndex in lines.indices) {
            val line = lines[lineIndex].trim()
            if (line.startsWith("size:", ignoreCase = true)) {
                val sizePart = line.substring(5).trim()
                val dimensions = sizePart.split("x")
                if (dimensions.size == 2) {
                    sourceWidth = dimensions[0].trim().toIntOrNull() ?: 720
                    sourceHeight = dimensions[1].trim().toIntOrNull() ?: 480
                }
            } else if (line.startsWith("palette:", ignoreCase = true)) {
                val palettePart = line.substring(8).trim()
                val colorHexList = palettePart.split(",")
                for (colorIndex in colorHexList.indices) {
                    if (colorIndex >= 16) {
                        break
                    }
                    val hexClean = colorHexList[colorIndex].trim().removePrefix("#")
                    val parsedColor = hexClean.toLongOrNull(16)?.toInt() ?: palette[colorIndex]
                    palette[colorIndex] = parsedColor
                }
            }
        }
    }

    override fun decode(
        data: ByteArray,
        length: Int,
        reset: Boolean,
    ): Subtitle {
        if (length < 4) {
            return EmptyVobSubSubtitle()
        }

        val controlSequenceOffset = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (controlSequenceOffset !in 4 until length) {
            return EmptyVobSubSubtitle()
        }

        var startX = 0
        var endX = 0
        var startY = 0
        var endY = 0
        var topFieldOffset = 4
        var bottomFieldOffset = 4
        val colorIndices = IntArray(4)
        val alphaValues = IntArray(4)
        var durationMicroseconds = 4_000_000L

        var currentOffset = controlSequenceOffset
        while (currentOffset in 4 until length) {
            val nextOffset = ((data[currentOffset + 2].toInt() and 0xFF) shl 8) or (data[currentOffset + 3].toInt() and 0xFF)
            var commandPointer = currentOffset + 4

            var sequenceFinished = false
            while (commandPointer < length && !sequenceFinished) {
                val commandByte = data[commandPointer++].toInt() and 0xFF
                when (commandByte) {
                    0x00 -> {
                        Unit
                    }

                    0x01 -> {
                        Unit
                    }

                    0x02 -> {
                        val dateTicks = ((data[currentOffset].toInt() and 0xFF) shl 8) or (data[currentOffset + 1].toInt() and 0xFF)
                        if (dateTicks > 0) {
                            durationMicroseconds = dateTicks * 1_000_000L / 90L
                        }
                    }

                    0x03 -> {
                        if (commandPointer + 2 <= length) {
                            val byteAlpha = data[commandPointer++].toInt() and 0xFF
                            val byteBeta = data[commandPointer++].toInt() and 0xFF
                            colorIndices[3] = (byteAlpha ushr 4) and 0x0F
                            colorIndices[2] = byteAlpha and 0x0F
                            colorIndices[1] = (byteBeta ushr 4) and 0x0F
                            colorIndices[0] = byteBeta and 0x0F
                        }
                    }

                    0x04 -> {
                        if (commandPointer + 2 <= length) {
                            val byteAlpha = data[commandPointer++].toInt() and 0xFF
                            val byteBeta = data[commandPointer++].toInt() and 0xFF
                            alphaValues[3] = ((byteAlpha ushr 4) and 0x0F) * 17
                            alphaValues[2] = (byteAlpha and 0x0F) * 17
                            alphaValues[1] = ((byteBeta ushr 4) and 0x0F) * 17
                            alphaValues[0] = (byteBeta and 0x0F) * 17
                        }
                    }

                    0x05 -> {
                        if (commandPointer + 6 <= length) {
                            val b0 = data[commandPointer++].toInt() and 0xFF
                            val b1 = data[commandPointer++].toInt() and 0xFF
                            val b2 = data[commandPointer++].toInt() and 0xFF
                            val b3 = data[commandPointer++].toInt() and 0xFF
                            val b4 = data[commandPointer++].toInt() and 0xFF
                            val b5 = data[commandPointer++].toInt() and 0xFF
                            startX = (b0 shl 4) or (b1 ushr 4)
                            endX = ((b1 and 0x0F) shl 8) or b2
                            startY = (b3 shl 4) or (b4 ushr 4)
                            endY = ((b4 and 0x0F) shl 8) or b5
                        }
                    }

                    0x06 -> {
                        if (commandPointer + 4 <= length) {
                            topFieldOffset = ((data[commandPointer++].toInt() and 0xFF) shl 8) or (data[commandPointer++].toInt() and 0xFF)
                            bottomFieldOffset =
                                ((data[commandPointer++].toInt() and 0xFF) shl 8) or (data[commandPointer++].toInt() and 0xFF)
                        }
                    }

                    0xFF -> {
                        sequenceFinished = true
                    }

                    else -> {
                        sequenceFinished = true
                    }
                }
            }

            if (nextOffset <= currentOffset || nextOffset >= length) {
                break
            }
            currentOffset = nextOffset
        }

        val bitmapWidth = endX - startX + 1
        val bitmapHeight = endY - startY + 1
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || bitmapWidth > 3840 || bitmapHeight > 2160) {
            return EmptyVobSubSubtitle()
        }

        val colorTable = IntArray(4)
        for (index in 0 until 4) {
            val rawRgb = palette[colorIndices[index]] and 0x00FFFFFF
            colorTable[index] = (alphaValues[index] shl 24) or rawRgb
        }

        val pixelBuffer = IntArray(bitmapWidth * bitmapHeight)
        decompressField(data, topFieldOffset, pixelBuffer, bitmapWidth, bitmapHeight, 0, colorTable)
        decompressField(data, bottomFieldOffset, pixelBuffer, bitmapWidth, bitmapHeight, 1, colorTable)

        val bitmap = Bitmap.createBitmap(pixelBuffer, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        val cue =
            Cue
                .Builder()
                .setBitmap(bitmap)
                .setPosition(startX.toFloat() / sourceWidth.toFloat())
                .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                .setLine(startY.toFloat() / sourceHeight.toFloat(), Cue.LINE_TYPE_FRACTION)
                .setLineAnchor(Cue.ANCHOR_TYPE_START)
                .setSize(bitmapWidth.toFloat() / sourceWidth.toFloat())
                .setBitmapHeight(bitmapHeight.toFloat() / sourceHeight.toFloat())
                .build()

        return SingleVobSubSubtitle(cue, durationMicroseconds)
    }

    private fun decompressField(
        data: ByteArray,
        offset: Int,
        pixels: IntArray,
        width: Int,
        height: Int,
        startLine: Int,
        colorTable: IntArray,
    ) {
        val bitReader = VobSubBitReader(data, offset)
        var lineIndex = startLine

        while (lineIndex < height) {
            var currentColumn = 0
            while (currentColumn < width) {
                val peekFour = bitReader.peekBits(4)
                val runLength: Int
                val colorIndex: Int

                if (peekFour >= 4) {
                    bitReader.skipBits(4)
                    runLength = peekFour ushr 2
                    colorIndex = peekFour and 3
                } else {
                    val peekEight = bitReader.peekBits(8)
                    if (peekEight >= 16) {
                        bitReader.skipBits(8)
                        runLength = peekEight ushr 2
                        colorIndex = peekEight and 3
                    } else {
                        val peekTwelve = bitReader.peekBits(12)
                        if (peekTwelve >= 64) {
                            bitReader.skipBits(12)
                            runLength = peekTwelve ushr 2
                            colorIndex = peekTwelve and 3
                        } else {
                            val peekSixteen = bitReader.peekBits(16)
                            if (peekSixteen >= 256) {
                                bitReader.skipBits(16)
                                runLength = peekSixteen ushr 2
                                colorIndex = peekSixteen and 3
                            } else {
                                bitReader.skipBits(16)
                                runLength = width - currentColumn
                                colorIndex = peekSixteen and 3
                            }
                        }
                    }
                }

                val targetColor = colorTable[colorIndex]
                val boundedRun = minOf(runLength, width - currentColumn)
                val lineBaseIndex = lineIndex * width + currentColumn
                for (pixelStep in 0 until boundedRun) {
                    pixels[lineBaseIndex + pixelStep] = targetColor
                }
                currentColumn += boundedRun
            }
            bitReader.alignToByte()
            lineIndex += 2
        }
    }

    private class VobSubBitReader(
        private val data: ByteArray,
        private var byteIndex: Int,
    ) {
        private var bitBuffer = 0
        private var bitsLeft = 0

        private fun refill() {
            while (bitsLeft <= 24 && byteIndex < data.size) {
                val nextByte = data[byteIndex++].toInt() and 0xFF
                bitBuffer = (bitBuffer shl 8) or nextByte
                bitsLeft += 8
            }
        }

        fun peekBits(count: Int): Int {
            refill()
            if (bitsLeft < count) {
                return if (bitsLeft > 0) (bitBuffer shl (count - bitsLeft)) and ((1 shl count) - 1) else 0
            }
            val shift = bitsLeft - count
            return (bitBuffer ushr shift) and ((1 shl count) - 1)
        }

        fun skipBits(count: Int) {
            refill()
            bitsLeft = maxOf(0, bitsLeft - count)
        }

        fun alignToByte() {
            bitsLeft = 0
            bitBuffer = 0
        }
    }

    private class SingleVobSubSubtitle(
        private val cue: Cue,
        private val durationUs: Long,
    ) : Subtitle {
        override fun getNextEventTimeIndex(timeUs: Long): Int =
            if (timeUs < 0) {
                0
            } else if (timeUs < durationUs) {
                1
            } else {
                C.INDEX_UNSET
            }

        override fun getEventTime(index: Int): Long = if (index == 0) 0L else durationUs

        override fun getEventTimeCount(): Int = 2

        override fun getCues(timeUs: Long): List<Cue> = if (timeUs in 0 until durationUs) listOf(cue) else emptyList()
    }

    private class EmptyVobSubSubtitle : Subtitle {
        override fun getNextEventTimeIndex(timeUs: Long): Int = C.INDEX_UNSET

        override fun getEventTime(index: Int): Long = 0L

        override fun getEventTimeCount(): Int = 0

        override fun getCues(timeUs: Long): List<Cue> = emptyList()
    }
}
