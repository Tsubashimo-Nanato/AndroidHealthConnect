package com.example.healthconnectandroid.hc.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Buffer
import okio.GzipSink
import okio.buffer
import okio.ByteString

internal class GzipRequestBody(
    body: RequestBody
) : RequestBody() {
    private val mediaType = body.contentType()
    private val compressedBody: ByteString = Buffer().let { buffer ->
        GzipSink(buffer).buffer().use { gzipSink ->
            body.writeTo(gzipSink)
        }
        buffer.readByteString()
    }

    override fun contentType(): MediaType? = mediaType

    // A known length prevents reverse proxies from indefinitely buffering a chunked initial upload.
    override fun contentLength(): Long = compressedBody.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        sink.write(compressedBody)
    }
}
