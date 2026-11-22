"""Verify PhotoVideo and VolumeBooster runtime behavior and contracts."""
import pathlib, shutil, subprocess, sys, tempfile, unittest
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import test_account_appearance_runtime as base

ROOT = pathlib.Path(__file__).resolve().parents[1]

class PhotoVideoAndVolumeBoosterTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_volume_booster_runtime_and_preferences(self):
        sources = {k: base.STUBS[k] for k in ['android/content/Context.java', 'android/content/SharedPreferences.java']}
        sources.update({
            'android/media/audiofx/LoudnessEnhancer.java': '''package android.media.audiofx;
public class LoudnessEnhancer {
    public static int lastSession = -1;
    public static int targetGain = 0;
    public static boolean enabled = false;
    public static boolean released = false;
    public LoudnessEnhancer(int audioSession) {
        lastSession = audioSession;
        released = false;
    }
    public void setTargetGain(int gainmB) {
        targetGain = gainmB;
    }
    public void setEnabled(boolean enable) {
        enabled = enable;
    }
    public void release() {
        released = true;
    }
}''',
            'mi/tiktokmi/Margy.java': '''package mi.tiktokmi;
public class Margy {
    public static final String PREFS = "tiktokmi";
    public static android.content.Context c = new android.content.Context();
    public static android.content.Context context() { return c; }
}''',
            'mi/tiktokmi/Diary.java': 'package mi.tiktokmi;public class Diary { public static void note(String s) {} }',
            'mi/tiktokmi/Text.java': '''package mi.tiktokmi;
public class Text {
    public static final String VOLUME_BOOST_OFF = "Off";
}''',
            'mi/tiktokmi/ModDialog.java': '''package mi.tiktokmi;
public class ModDialog {
    public interface Choice { void on(Object d, int i); }
    public static class Builder {
        public Builder(android.content.Context c) {}
        public Builder setItems(String[] s, Choice ch) { return this; }
        public void show() {}
    }
}''',
            'mi/tiktokmi/VolumeBooster.java': (ROOT / 'inject/java/mi/tiktokmi/VolumeBooster.java').read_text(encoding='utf-8'),
            'mi/tiktokmi/Test.java': '''package mi.tiktokmi;
import android.media.audiofx.LoudnessEnhancer;
public class Test {
    static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
    public static void main(String[] args) {
        VolumeBooster.reloadSettings();
        check(!VolumeBooster.isEnabled(), "default is disabled");
        check("Off".equals(VolumeBooster.label()), "label off when disabled");
        check(VolumeBooster.gain() == 1000, "default gain is 1000 mB (+10 dB)");
        check(VolumeBooster.gainIndex() == 1, "gain index 1 (+10 dB)");

        VolumeBooster.setEnabled(true);
        check(VolumeBooster.isEnabled(), "enabled after setEnabled");
        check("+10 dB".equals(VolumeBooster.label()), "label +10 dB");
        check(LoudnessEnhancer.enabled, "enhancer active");
        check(LoudnessEnhancer.targetGain == 1000, "target gain applied");

        VolumeBooster.setGainIndex(3);
        check(VolumeBooster.gain() == 2000, "gain changed to 2000 mB");
        check(VolumeBooster.gainIndex() == 3, "gain index updated");
        check("+20 dB".equals(VolumeBooster.label()), "label +20 dB");
        check(LoudnessEnhancer.targetGain == 2000, "new gain applied to enhancer");

        VolumeBooster.setEnabled(false);
        check(!VolumeBooster.isEnabled(), "disabled");
        check(!LoudnessEnhancer.enabled && LoudnessEnhancer.released, "enhancer released when disabled");
    }
}'''
        })
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            for name, source in sources.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(source, encoding='utf-8')
            res = subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-d', str(root)] + [str(p) for p in root.rglob('*.java')], capture_output=True, text=True)
            self.assertEqual(0, res.returncode, res.stderr)
            res = subprocess.run(['java', '-cp', str(root), 'mi.tiktokmi.Test'], capture_output=True, text=True)
            self.assertEqual(0, res.returncode, res.stderr)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_photo_video_rgb_to_yuv_conversion(self):
        sources = {
            'mi/tiktokmi/TestYuv.java': '''package mi.tiktokmi;
public class TestYuv {
    static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
    public static void main(String[] args) {
        int w = 4, h = 4;
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = 0xFFFFFFFF; // white
        }
        byte[] nv12 = PhotoVideo.rgbToYuv(argb, w, h, true);
        check(nv12.length == w * h * 3 / 2, "nv12 size correct: " + nv12.length);
        for (int i = 0; i < w * h; i++) {
            int y = nv12[i] & 0xFF;
            check(y >= 230, "white Y is high: " + y);
        }

        byte[] i420 = PhotoVideo.rgbToYuv(argb, w, h, false);
        check(i420.length == w * h * 3 / 2, "i420 size correct: " + i420.length);

        for (int i = 0; i < argb.length; i++) {
            argb[i] = 0xFF000000; // black
        }
        byte[] blackNv12 = PhotoVideo.rgbToYuv(argb, w, h, true);
        for (int i = 0; i < w * h; i++) {
            int y = blackNv12[i] & 0xFF;
            check(y <= 20, "black Y is low: " + y);
        }
    }
}'''
        }
        # Include PhotoVideo
        sources['mi/tiktokmi/PhotoVideo.java'] = (ROOT / 'inject/java/mi/tiktokmi/PhotoVideo.java').read_text(encoding='utf-8')
        # Add minimal stubs for PhotoVideo dependencies
        sources.update({
            'android/content/Context.java': 'package android.content; public class Context { public java.io.File getCacheDir() { return new java.io.File("."); } }',
            'android/graphics/Bitmap.java': 'package android.graphics; public class Bitmap { public int getWidth(){return 100;} public int getHeight(){return 100;} public void getPixels(int[] p,int o,int s,int x,int y,int w,int h){} public void recycle(){} public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean f){return b;} public static enum Config { ARGB_8888 } }',
            'android/graphics/BitmapFactory.java': 'package android.graphics; public class BitmapFactory { public static class Options { public boolean inJustDecodeBounds; public int outWidth=100, outHeight=100; public Bitmap.Config inPreferredConfig; } public static Bitmap decodeFile(String path, Options opts){ return new Bitmap(); } }',
            'android/media/MediaCodec.java': 'package android.media; public class MediaCodec { public static final int BUFFER_FLAG_END_OF_STREAM=4, BUFFER_FLAG_CODEC_CONFIG=2, CONFIGURE_FLAG_ENCODE=1, INFO_OUTPUT_FORMAT_CHANGED=-2; public static class BufferInfo { public int offset, size, flags; public long presentationTimeUs; } public static MediaCodec createEncoderByType(String type){return new MediaCodec();} public static MediaCodec createDecoderByType(String type){return new MediaCodec();} public MediaCodecInfo getCodecInfo(){return new MediaCodecInfo();} public void configure(MediaFormat f, Object s, Object c, int flags){} public void start(){} public void stop(){} public void release(){} public int dequeueInputBuffer(long t){return -1;} public int dequeueOutputBuffer(BufferInfo info, long t){return -1;} public java.nio.ByteBuffer getInputBuffer(int idx){return java.nio.ByteBuffer.allocate(100);} public java.nio.ByteBuffer getOutputBuffer(int idx){return java.nio.ByteBuffer.allocate(100);} public void queueInputBuffer(int idx, int offset, int size, long pts, int flags){} public void releaseOutputBuffer(int idx, boolean render){} public MediaFormat getOutputFormat(){return new MediaFormat();} }',
            'android/media/MediaCodecInfo.java': 'package android.media; public class MediaCodecInfo { public static class CodecCapabilities { public static final int COLOR_FormatYUV420Planar=19, COLOR_FormatYUV420SemiPlanar=21; public int[] colorFormats = {21}; } public static class CodecProfileLevel { public static final int AACObjectLC=2; } public CodecCapabilities getCapabilitiesForType(String type){return new CodecCapabilities();} }',
            'android/media/MediaExtractor.java': 'package android.media; public class MediaExtractor { public static final int SEEK_TO_CLOSEST_SYNC=2; public void setDataSource(String p){} public int getTrackCount(){return 0;} public MediaFormat getTrackFormat(int i){return new MediaFormat();} public void selectTrack(int i){} public void seekTo(long t, int mode){} public int readSampleData(java.nio.ByteBuffer buf, int offset){return -1;} public long getSampleTime(){return 0;} public int getSampleFlags(){return 0;} public boolean advance(){return false;} public void release(){} }',
            'android/media/MediaFormat.java': 'package android.media; public class MediaFormat { public static final String KEY_MIME="mime", KEY_COLOR_FORMAT="color-format", KEY_BIT_RATE="bitrate", KEY_FRAME_RATE="frame-rate", KEY_I_FRAME_INTERVAL="i-frame-interval", KEY_DURATION="durationUs", KEY_SAMPLE_RATE="sample-rate", KEY_CHANNEL_COUNT="channel-count", KEY_AAC_PROFILE="aac-profile"; public static MediaFormat createVideoFormat(String m, int w, int h){return new MediaFormat();} public static MediaFormat createAudioFormat(String m, int r, int c){return new MediaFormat();} public void setInteger(String k, int v){} public int getInteger(String k){return 0;} public String getString(String k){return "audio/mp4a-latm";} public long getLong(String k){return 1000000L;} public boolean containsKey(String k){return true;} }',
            'android/media/MediaMetadataRetriever.java': 'package android.media; public class MediaMetadataRetriever { public static final int METADATA_KEY_DURATION=9; public void setDataSource(String p){} public String extractMetadata(int k){return "10000";} public void release(){} }',
            'android/media/MediaMuxer.java': 'package android.media; public class MediaMuxer { public static class OutputFormat { public static final int MUXER_OUTPUT_MPEG_4=0; } public MediaMuxer(String p, int f){} public int addTrack(MediaFormat fmt){return 0;} public void start(){} public void stop(){} public void release(){} public void writeSampleData(int t, java.nio.ByteBuffer b, MediaCodec.BufferInfo info){} }',
            'mi/tiktokmi/DownloadTasks.java': 'package mi.tiktokmi; public class DownloadTasks { public static class Control { public boolean cancelled(){return false;} } }'
        })
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            for name, source in sources.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(source, encoding='utf-8')
            res = subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-d', str(root)] + [str(p) for p in root.rglob('*.java')], capture_output=True, text=True)
            self.assertEqual(0, res.returncode, res.stderr)
            res = subprocess.run(['java', '-cp', str(root), 'mi.tiktokmi.TestYuv'], capture_output=True, text=True)
            self.assertEqual(0, res.returncode, res.stderr)
