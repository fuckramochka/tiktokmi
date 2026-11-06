package mi.tiktokmi;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints of a video's sound, the way Shazam knows a song: not the sound itself but its loudest
 * points, pitch against time, taken in pairs. The adverts read out in videos are mostly one
 * recording put into many videos, and its prints come back wherever it is, through music laid
 * over it, another loudness or another encoding. A print is a list of numbers: a hash of a pair
 * of points and the time of the first, over and over.
 */
final class Hear {

    private Hear() {}

    /** What the sound is brought down to before it is looked at. */
    static final int RATE = 11025;
    private static final int WINDOW = 1024, HOP = 512;
    /** How long one step of the prints is, in milliseconds. */
    static final float STEP_MS = HOP * 1000f / RATE;
    private static final int[] BANDS = {10, 20, 40, 80, 160, 300};
    private static final int FAN = 3, REACH = 30;

    // ------------------------------------------------------------ the sound of a file

    /** The file's sound, one channel at RATE, at most `longest` milliseconds of it. */
    static short[] sound(String path, long longest) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(path);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat one = extractor.getTrackFormat(i);
                String mime = one.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = one;
                    break;
                }
            }
            if (track < 0) return new short[0];
            extractor.selectTrack(track);
            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
            Resampler out = new Resampler(rate, (int) Math.min(Integer.MAX_VALUE / 2, longest * RATE / 1000));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            while (!outputDone && !out.full()) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(in);
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        java.nio.ShortBuffer samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int frames = samples.remaining() / Math.max(1, channels);
                        for (int f = 0; f < frames; f++) {
                            int sum = 0;
                            for (int c = 0; c < channels; c++) sum += samples.get(f * channels + c);
                            out.add(sum / (float) channels);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat now = codec.getOutputFormat();
                    if (now.containsKey(MediaFormat.KEY_SAMPLE_RATE)) out.from(now.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    if (now.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = now.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
            }
            return out.done();
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Throwable ignored) {
            }
            extractor.release();
        }
    }

    /** Takes samples at any rate and keeps them at RATE, each the mean of what it stands for. */
    private static final class Resampler {
        private float step;
        private float carried, sum;
        private int counted;
        private short[] kept = new short[RATE * 30];
        private int size;
        private final int most;

        Resampler(int rate, int most) {
            this.most = most;
            from(rate);
        }

        void from(int rate) {
            step = rate / (float) RATE;
        }

        boolean full() {
            return size >= most;
        }

        void add(float sample) {
            sum += sample;
            counted++;
            carried += 1f;
            if (carried >= step) {
                carried -= step;
                if (size == kept.length) kept = java.util.Arrays.copyOf(kept, kept.length * 2);
                kept[size++] = (short) Math.max(-32768, Math.min(32767, sum / counted));
                sum = 0;
                counted = 0;
            }
        }

        short[] done() {
            return java.util.Arrays.copyOf(kept, Math.min(size, most));
        }
    }

    // ------------------------------------------------------------ prints

    /** The loudest points: for each step, the step and the frequency bin of each. */
    private static List<int[]> points(short[] sound) {
        List<int[]> out = new ArrayList<int[]>();
        if (sound.length < WINDOW) return out;
        float[] re = new float[WINDOW], im = new float[WINDOW];
        float[] shape = new float[WINDOW];
        for (int i = 0; i < WINDOW; i++) shape[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (WINDOW - 1)));
        float[] best = new float[BANDS.length - 1];
        int[] at = new int[BANDS.length - 1];
        for (int step = 0; (long) step * HOP + WINDOW <= sound.length; step++) {
            int base = step * HOP;
            for (int i = 0; i < WINDOW; i++) {
                re[i] = sound[base + i] * shape[i];
                im[i] = 0f;
            }
            fft(re, im);
            float mean = 0f;
            for (int b = 0; b < best.length; b++) {
                best[b] = 0f;
                at[b] = BANDS[b];
                for (int k = BANDS[b]; k < BANDS[b + 1]; k++) {
                    float power = re[k] * re[k] + im[k] * im[k];
                    if (power > best[b]) {
                        best[b] = power;
                        at[b] = k;
                    }
                }
                mean += best[b];
            }
            mean /= best.length;
            for (int b = 0; b < best.length; b++) {
                // a point stands out of its own step and is not silence
                if (best[b] >= 1.5f * mean && best[b] > 1e7f) out.add(new int[] {step, at[b]});
            }
        }
        return out;
    }

    /** Pairs of points near one another, as [hash, step of the first] over and over. */
    private static int[] pairs(List<int[]> points, int fromStep, int toStep) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < points.size(); i++) {
            int[] a = points.get(i);
            if (a[0] < fromStep || a[0] >= toStep) continue;
            int made = 0;
            for (int j = i + 1; j < points.size() && made < FAN; j++) {
                int[] b = points.get(j);
                int dt = b[0] - a[0];
                if (dt <= 0) continue;
                if (dt > REACH) break;
                if (b[0] >= toStep) break;
                out.add((a[1] << 15) | (b[1] << 6) | dt);
                out.add(a[0] - fromStep);
                made++;
            }
        }
        int[] flat = new int[out.size()];
        for (int i = 0; i < flat.length; i++) flat[i] = out.get(i);
        return flat;
    }

    /** The print of a part of a sound, from and to in milliseconds. */
    static int[] print(short[] sound, long from, long to) {
        int a = (int) (from / STEP_MS), b = (int) Math.ceil(to / STEP_MS);
        return pairs(points(sound), a, b);
    }

    /** The prints of the whole of a sound. */
    static int[] whole(short[] sound) {
        return pairs(points(sound), 0, Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------ finding a known print

    /** The known prints, by hash: which print and at what step in it. */
    static final class Index {
        final Map<Integer, long[]> byHash = new HashMap<Integer, long[]>();
        final Map<Long, Integer> sizes = new HashMap<Long, Integer>();
        final Map<Long, Long> lengths = new HashMap<Long, Long>();

        void add(long id, long length, int[] flat) {
            sizes.put(id, flat.length / 2);
            lengths.put(id, length);
            for (int i = 0; i + 1 < flat.length; i += 2) {
                long entry = (id << 20) | (flat[i + 1] & 0xFFFFF);
                long[] was = byHash.get(flat[i]);
                long[] now;
                if (was == null) {
                    now = new long[] {entry};
                } else {
                    if (was.length >= 64) continue;
                    now = java.util.Arrays.copyOf(was, was.length + 1);
                    now[was.length] = entry;
                }
                byHash.put(flat[i], now);
            }
        }

        boolean empty() {
            return sizes.isEmpty();
        }
    }

    /**
     * Where known prints sound in a video: [start, stop, id] in milliseconds. A print is found where
     * enough of its pairs come at one and the same offset, which chance does not line up.
     */
    static List<long[]> find(int[] video, Index index) {
        Map<Long, Integer> votes = new HashMap<Long, Integer>();
        for (int i = 0; i + 1 < video.length; i += 2) {
            long[] hits = index.byHash.get(video[i]);
            if (hits == null) continue;
            for (long hit : hits) {
                long id = hit >>> 20;
                int offset = video[i + 1] - (int) (hit & 0xFFFFF);
                long key = (id << 24) | ((offset + 0x800000) & 0xFFFFFF);
                Integer was = votes.get(key);
                votes.put(key, was == null ? 1 : was + 1);
            }
        }
        Map<Long, long[]> best = new HashMap<Long, long[]>();
        for (Map.Entry<Long, Integer> vote : votes.entrySet()) {
            long id = vote.getKey() >>> 24;
            int offset = (int) (vote.getKey() & 0xFFFFFF) - 0x800000;
            int need = Math.max(12, Math.round(0.15f * index.sizes.get(id)));
            if (vote.getValue() < need) continue;
            long[] known = best.get(id);
            if (known == null || vote.getValue() > known[1]) best.put(id, new long[] {offset, vote.getValue()});
        }
        List<long[]> out = new ArrayList<long[]>();
        for (Map.Entry<Long, long[]> one : best.entrySet()) {
            long start = Math.max(0, Math.round(one.getValue()[0] * STEP_MS));
            Long length = index.lengths.get(one.getKey());
            out.add(new long[] {start, start + (length == null ? 0 : length), -one.getKey()});
        }
        return out;
    }

    // ------------------------------------------------------------ the transform

    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            float wr = (float) Math.cos(angle), wi = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float cr = 1f, ci = 0f;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    float nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }
}
