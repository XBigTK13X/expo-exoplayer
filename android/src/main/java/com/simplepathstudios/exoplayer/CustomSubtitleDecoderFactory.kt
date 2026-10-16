package com.simplepathstudios.exoplayer

import androidx.media3.common.Format
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.extractor.text.SubtitleDecoder

class CustomSubtitleDecoderFactory : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean {
        val sampleMimeType = format.sampleMimeType ?: return false
        return sampleMimeType == "application/vobsub" ||
            sampleMimeType == "application/x-vobsub" ||
            SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        val sampleMimeType = format.sampleMimeType
        if (sampleMimeType == "application/vobsub" || sampleMimeType == "application/x-vobsub") {
            return VobSubDecoder(format.initializationData)
        }
        return SubtitleDecoderFactory.DEFAULT.createDecoder(format)
    }
}
