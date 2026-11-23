package mi.tiktokmi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Synthesizes an MP4 video from photos and an audio track.
 */
public final class PhotoVideo {

    private PhotoVideo() {}

    public static void create(Context context, List<File> imageFiles, File audioFile, File outputFile, DownloadTasks.Control control) throws Exception {
        if (imageFiles == null || imageFiles.isEmpty()) throw new IllegalArgumentException("No images provided");
        if (outputFile == null) throw new IllegalArgumentException("Output file missing");

        long durationUs = getAudioDurationUs(audioFile);
        if (durationUs <= 0) {
            durationUs = Math.max(15_000_000L, imageFiles.size() * 3_000_000L);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFiles.get(0).getAbsolutePath(), bounds);
        int origW = bounds.outWidth > 0 ? bounds.outWidth : 720;
        int origH = bounds.outHeight > 0 ? bounds.outHeight : 1280;

        final int targetW = (origW > origH) ? 1280 : 720;
        final int targetH = (origW > origH) ? 720 : 1280;

        MediaCodec videoEncoder = createVideoEncoder();
        int colorFormat = selectColorFormat(videoEncoder);
        boolean semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;

        byte[][] yuvFrames = new byte[imageFiles.size()][];
        for (int i = 0; i < imageFiles.size(); i++) {
            if (control != null && control.cancelled()) {
                videoEncoder.release();
                throw new IOException("Cancelled");
            }
            yuvFrames[i] = loadYuv(imageFiles.get(i), targetW, targetH, semiPlanar);
        }

        File readyAudioFile = audioFile;
        File tempTranscodedAudio = null;
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            try {
                if (!isDirectlyMuxableAac(audioFile)) {
                    tempTranscodedAudio = new File(context.getCacheDir(), "transcoded_" + System.currentTimeMillis() + ".m4a");
                    transcodeAudioToAac(audioFile, tempTranscodedAudio, control);
                    readyAudioFile = tempTranscodedAudio;
                }
            } catch (Throwable transcodeErr) {
                Diary.note("photo-video audio transcode fallback: " + transcodeErr);
                readyAudioFile = null;
            }
        } else {
            readyAudioFile = null;
        }

        try {
            int fps = 25;
            long frameDurationUs = 1_000_000L / fps;
            int totalFrames = (int) Math.max(1, (durationUs + frameDurationUs - 1) / frameDurationUs);

            MediaFormat vFormat = MediaFormat.createVideoFormat("video/avc", targetW, targetH);
            vFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            vFormat.setInteger(MediaFormat.KEY_BIT_RATE, Math.min(3_000_000, targetW * targetH * 3));
            vFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            vFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();

            MediaExtractor audioExtractor = null;
            int audioTrackIndex = -1;
            MediaFormat aFormat = null;
            if (readyAudioFile != null && readyAudioFile.exists() && readyAudioFile.length() > 0) {
                try {
                    audioExtractor = new MediaExtractor();
                    audioExtractor.setDataSource(readyAudioFile.getAbsolutePath());
                    for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                        MediaFormat fmt = audioExtractor.getTrackFormat(i);
                        String mime = fmt.getString(MediaFormat.KEY_MIME);
                        if (mime != null && mime.startsWith("audio/")) {
                            audioTrackIndex = i;
                            aFormat = fmt;
                            break;
                        }
                    }
                    if (audioTrackIndex >= 0) audioExtractor.selectTrack(audioTrackIndex);
                } catch (Throwable e) {
                    Diary.note("audio extractor failed: " + e);
                    if (audioExtractor != null) {
                        try { audioExtractor.release(); } catch (Throwable ignored) {}
                        audioExtractor = null;
                    }
                }
            }

            MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerVideoTrack = -1;
            int muxerAudioTrack = -1;
            boolean muxerStarted = false;

            int frameIndex = 0;
            boolean inputDone = false;
            boolean encoderDone = false;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            try {
                while (!encoderDone) {
                    if (control != null && control.cancelled()) throw new IOException("Cancelled");

                    if (!inputDone) {
                        int inIndex = videoEncoder.dequeueInputBuffer(5000);
                        if (inIndex >= 0) {
                            ByteBuffer inBuf = videoEncoder.getInputBuffer(inIndex);
                            inBuf.clear();
                            if (frameIndex < totalFrames) {
                                int imgIdx = (int) ((long) frameIndex * yuvFrames.length / totalFrames);
                                byte[] yuv = yuvFrames[Math.min(imgIdx, yuvFrames.length - 1)];
                                int toPut = Math.min(yuv.length, inBuf.remaining());
                                inBuf.put(yuv, 0, toPut);
                                long pts = frameIndex * frameDurationUs;
                                videoEncoder.queueInputBuffer(inIndex, 0, toPut, pts, 0);
                                frameIndex++;
                            } else {
                                videoEncoder.queueInputBuffer(inIndex, 0, 0, frameIndex * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            }
                        }
                    }

                    int outIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 5000);
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat videoOutputFormat = videoEncoder.getOutputFormat();
                        muxerVideoTrack = muxer.addTrack(videoOutputFormat);
                        if (aFormat != null) {
                            muxerAudioTrack = muxer.addTrack(aFormat);
                        }
                        muxer.start();
                        muxerStarted = true;
                    } else if (outIndex >= 0) {
                        ByteBuffer outBuf = videoEncoder.getOutputBuffer(outIndex);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0;
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset);
                            outBuf.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(muxerVideoTrack, outBuf, bufferInfo);
                        }
                        videoEncoder.releaseOutputBuffer(outIndex, false);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true;
                        }
                    }
                }

                if (muxerAudioTrack >= 0 && muxerStarted && audioExtractor != null) {
                    ByteBuffer aBuf = ByteBuffer.allocate(65536);
                    MediaCodec.BufferInfo aInfo = new MediaCodec.BufferInfo();
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    while (true) {
                        if (control != null && control.cancelled()) throw new IOException("Cancelled");
                        aBuf.clear();
                        int size = audioExtractor.readSampleData(aBuf, 0);
                        if (size < 0) break;
                        aInfo.offset = 0;
                        aInfo.size = size;
                        aInfo.presentationTimeUs = audioExtractor.getSampleTime();
                        aInfo.flags = audioExtractor.getSampleFlags();
                        muxer.writeSampleData(muxerAudioTrack, aBuf, aInfo);
                        if (!audioExtractor.advance() || (durationUs > 0 && aInfo.presentationTimeUs >= durationUs)) {
                            break;
                        }
                    }
                }
            } finally {
                if (muxerStarted) {
                    try { muxer.stop(); } catch (Throwable ignored) {}
                }
                try { muxer.release(); } catch (Throwable ignored) {}
                if (audioExtractor != null) {
                    try { audioExtractor.release(); } catch (Throwable ignored) {}
                }
            }
        } finally {
            try { videoEncoder.stop(); } catch (Throwable ignored) {}
            try { videoEncoder.release(); } catch (Throwable ignored) {}
            if (tempTranscodedAudio != null) tempTranscodedAudio.delete();
        }
    }

    private static MediaCodec createVideoEncoder() throws IOException {
        for (String name : new String[]{"c2.android.avc.encoder", "OMX.google.h264.encoder"}) {
            try {
                return MediaCodec.createByCodecName(name);
            } catch (Throwable ignored) {}
        }
        return MediaCodec.createEncoderByType("video/avc");
    }

    private static boolean isDirectlyMuxableAac(File audioFile) {
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) return false;
        MediaExtractor probe = new MediaExtractor();
        try {
            probe.setDataSource(audioFile.getAbsolutePath());
            for (int i = 0; i < probe.getTrackCount(); i++) {
                MediaFormat fmt = probe.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.equalsIgnoreCase("audio/mp4a-latm") || mime.equalsIgnoreCase("audio/aac"))) {
                    if (fmt.containsKey("csd-0")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { probe.release(); } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void transcodeAudioToAac(File input, File output, DownloadTasks.Control control) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input.getAbsolutePath());
        int trackIndex = -1;
        MediaFormat inputFormat = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                trackIndex = i;
                inputFormat = f;
                break;
            }
        }
        if (trackIndex < 0) {
            extractor.release();
            throw new IOException("No audio track in " + input);
        }
        extractor.selectTrack(trackIndex);

        int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channelCount = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
        if (channelCount < 1) channelCount = 1;
        if (channelCount > 2) channelCount = 2;

        MediaCodec decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(inputFormat, null, null, 0);
        decoder.start();

        MediaFormat aacFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount);
        aacFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        aacFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        aacFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
        aacFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);

        MediaCodec encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int muxerTrack = -1;
        boolean muxerStarted = false;

        MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();

        boolean extractorDone = false;
        boolean decoderDone = false;
        boolean encoderDone = false;
        boolean encoderInputEos = false;

        byte[] pcmBuffer = new byte[65536];
        int pcmBuffered = 0;
        long pcmPresentationTimeUs = 0;

        try {
            while (!encoderDone) {
                if (control != null && control.cancelled()) throw new IOException("Cancelled");

                if (!extractorDone) {
                    int inIndex = decoder.dequeueInputBuffer(2000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                        inBuf.clear();
                        int size = extractor.readSampleData(inBuf, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            extractorDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                if (!decoderDone && pcmBuffered < pcmBuffer.length) {
                    int decOut = decoder.dequeueOutputBuffer(decInfo, 2000);
                    if (decOut >= 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(decOut);
                        if (decInfo.size > 0) {
                            outBuf.position(decInfo.offset);
                            outBuf.limit(decInfo.offset + decInfo.size);
                            if (pcmBuffered + decInfo.size > pcmBuffer.length) {
                                byte[] expanded = new byte[Math.max(pcmBuffer.length * 2, pcmBuffered + decInfo.size)];
                                System.arraycopy(pcmBuffer, 0, expanded, 0, pcmBuffered);
                                pcmBuffer = expanded;
                            }
                            outBuf.get(pcmBuffer, pcmBuffered, decInfo.size);
                            pcmBuffered += decInfo.size;
                        }
                        if ((decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true;
                        }
                        decoder.releaseOutputBuffer(decOut, false);
                    }
                }

                if (!encoderInputEos) {
                    if (pcmBuffered > 0) {
                        int encIn = encoder.dequeueInputBuffer(2000);
                        if (encIn >= 0) {
                            ByteBuffer encInBuf = encoder.getInputBuffer(encIn);
                            encInBuf.clear();
                            int toSend = Math.min(pcmBuffered, encInBuf.remaining());
                            encInBuf.put(pcmBuffer, 0, toSend);
                            if (toSend < pcmBuffered) {
                                System.arraycopy(pcmBuffer, toSend, pcmBuffer, 0, pcmBuffered - toSend);
                            }
                            pcmBuffered -= toSend;
                            encoder.queueInputBuffer(encIn, 0, toSend, pcmPresentationTimeUs, 0);
                            pcmPresentationTimeUs += ((long) toSend / (channelCount * 2)) * 1_000_000L / sampleRate;
                        }
                    } else if (decoderDone) {
                        int encIn = encoder.dequeueInputBuffer(2000);
                        if (encIn >= 0) {
                            encoder.queueInputBuffer(encIn, 0, 0, pcmPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            encoderInputEos = true;
                        }
                    }
                }

                int encOut = encoder.dequeueOutputBuffer(encInfo, 2000);
                if (encOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = encoder.getOutputFormat();
                    muxerTrack = muxer.addTrack(outputFormat);
                    muxer.start();
                    muxerStarted = true;
                } else if (encOut >= 0) {
                    ByteBuffer encBuf = encoder.getOutputBuffer(encOut);
                    if ((encInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encInfo.size = 0;
                    }
                    if (encInfo.size > 0 && muxerStarted) {
                        encBuf.position(encInfo.offset);
                        encBuf.limit(encInfo.offset + encInfo.size);
                        muxer.writeSampleData(muxerTrack, encBuf, encInfo);
                    }
                    encoder.releaseOutputBuffer(encOut, false);
                    if ((encInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true;
                    }
                }
            }
        } finally {
            if (muxerStarted) {
                try { muxer.stop(); } catch (Throwable ignored) {}
            }
            try { muxer.release(); } catch (Throwable ignored) {}
            try { decoder.stop(); decoder.release(); } catch (Throwable ignored) {}
            try { encoder.stop(); encoder.release(); } catch (Throwable ignored) {}
            try { extractor.release(); } catch (Throwable ignored) {}
        }
    }

    private static long getAudioDurationUs(File audioFile) {
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) return 0;
        long durationUs = 0;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(audioFile.getAbsolutePath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur != null) durationUs = Long.parseLong(dur) * 1000L;
            mmr.release();
        } catch (Throwable ignored) {}
        if (durationUs <= 0) {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(audioFile.getAbsolutePath());
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String m = f.getString(MediaFormat.KEY_MIME);
                    if (m != null && m.startsWith("audio/") && f.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = f.getLong(MediaFormat.KEY_DURATION);
                        break;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                try { extractor.release(); } catch (Throwable ignored) {}
            }
        }
        return durationUs;
    }

    private static int selectColorFormat(MediaCodec encoder) {
        try {
            MediaCodecInfo info = encoder.getCodecInfo();
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/avc");
            for (int format : caps.colorFormats) {
                if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    return format;
                }
            }
            for (int format : caps.colorFormats) {
                if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    return format;
                }
            }
        } catch (Throwable ignored) {}
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private static byte[] loadYuv(File file, int targetW, int targetH, boolean semiPlanar) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap src = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (src == null) throw new IOException("Cannot decode image: " + file);

        Bitmap canvasBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(Color.BLACK);

        float scale = Math.min((float) targetW / src.getWidth(), (float) targetH / src.getHeight());
        float scaledW = scale * src.getWidth();
        float scaledH = scale * src.getHeight();
        float left = (targetW - scaledW) / 2.0f;
        float top = (targetH - scaledH) / 2.0f;

        RectF dstRect = new RectF(left, top, left + scaledW, top + scaledH);
        canvas.drawBitmap(src, null, dstRect, null);
        src.recycle();

        int[] pixels = new int[targetW * targetH];
        canvasBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH);
        canvasBitmap.recycle();
        return rgbToYuv(pixels, targetW, targetH, semiPlanar);
    }

    static byte[] rgbToYuv(int[] argb, int width, int height, boolean semiPlanar) {
        final int frameSize = width * height;
        byte[] yuv = new byte[frameSize * 3 / 2];
        int yIndex = 0;
        int uIndex = semiPlanar ? frameSize : frameSize;
        int vIndex = semiPlanar ? frameSize + 1 : frameSize + frameSize / 4;

        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int c = argb[index++];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;

                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));
                if (j % 2 == 0 && i % 2 == 0) {
                    byte uByte = (byte) Math.max(0, Math.min(255, u));
                    byte vByte = (byte) Math.max(0, Math.min(255, v));
                    if (semiPlanar) {
                        yuv[uIndex] = uByte;
                        yuv[uIndex + 1] = vByte;
                        uIndex += 2;
                    } else {
                        yuv[uIndex++] = uByte;
                        yuv[vIndex++] = vByte;
                    }
                }
            }
        }
        return yuv;
    }
}
