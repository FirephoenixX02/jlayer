package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for SampleBuffer class.
 * Tests sample buffer operations for stereo and mono audio.
 */
public class SampleBufferTest {

    @Test
    public void testConstructorStereo() {
        SampleBuffer buffer = new SampleBuffer(44100, 2);

        assertEquals(2, buffer.getChannelCount(), "Should have 2 channels");
        assertEquals(44100, buffer.getSampleFrequency(), "Sample frequency should be 44100");
        assertEquals(Obuffer.OBUFFERSIZE, buffer.getBuffer().length,
                "Buffer size should be OBUFFERSIZE");
    }

    @Test
    public void testConstructorMono() {
        SampleBuffer buffer = new SampleBuffer(48000, 1);

        assertEquals(1, buffer.getChannelCount(), "Should have 1 channel");
        assertEquals(48000, buffer.getSampleFrequency(), "Sample frequency should be 48000");
    }

    @Test
    public void testAppendMono() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        // Append 32 samples
        for (int i = 0; i < 32; i++) {
            buffer.append(0, (short) (i * 100));
        }

        assertEquals(32, buffer.getBufferLength(), "Should have 32 samples");

        // Verify samples
        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            assertEquals((short) (i * 100), buf[i], "Sample " + i + " should match");
        }
    }

    @Test
    public void testAppendStereo() {
        SampleBuffer buffer = new SampleBuffer(44100, 2);

        // Append 32 stereo samples (64 values total, interleaved)
        for (int i = 0; i < 32; i++) {
            buffer.append(0, (short) (i * 100));  // Left channel
            buffer.append(1, (short) (i * 200));  // Right channel
        }

        assertEquals(64, buffer.getBufferLength(), "Should have 64 buffer positions (32 stereo samples interleaved)");

        // Verify samples are interleaved
        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            assertEquals((short) (i * 100), buf[i * 2], "Left channel sample " + i + " should match");
            assertEquals((short) (i * 200), buf[i * 2 + 1], "Right channel sample " + i + " should match");
        }
    }

    @Test
    public void testAppendSamplesMono() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        float[] samples = new float[32];
        for (int i = 0; i < 32; i++) {
            samples[i] = i * 1000.0f;
        }

        buffer.appendSamples(0, samples);

        assertEquals(32, buffer.getBufferLength(), "Should have 32 samples");

        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            // Values should be clamped to short range [-32767, 32767]
            short expected = (short) Math.min(Math.max(samples[i], -32767.0), 32767.0);
            assertEquals(expected, buf[i], "Sample " + i + " should match");
        }
    }

    @Test
    public void testAppendSamplesStereo() {
        SampleBuffer buffer = new SampleBuffer(44100, 2);

        float[] samples = new float[32];
        for (int i = 0; i < 32; i++) {
            samples[i] = i * 1000.0f;
        }

        buffer.appendSamples(0, samples);
        buffer.appendSamples(1, samples);

        assertEquals(64, buffer.getBufferLength(), "Should have 64 buffer positions (32 stereo samples interleaved)");

        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            short expected = (short) Math.min(Math.max(samples[i], -32767.0), 32767.0);
            assertEquals(expected, buf[i * 2], "Left channel sample " + i + " should match");
            assertEquals(expected, buf[i * 2 + 1], "Right channel sample " + i + " should match");
        }
    }

    @Test
    public void testClamping() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        float[] samples = new float[32];
        // Values outside short range
        samples[0] = 50000.0f;   // Above max
        samples[1] = -50000.0f;  // Below min
        samples[2] = 32767.0f;   // At max
        samples[3] = -32767.0f;  // At min
        samples[4] = 0.0f;       // Zero

        buffer.appendSamples(0, samples);

        short[] buf = buffer.getBuffer();
        assertEquals((short) 32767, buf[0], "Value above max should be clamped to 32767");
        assertEquals((short) -32767, buf[1], "Value below min should be clamped to -32767");
        assertEquals((short) 32767, buf[2], "Value at max should remain 32767");
        assertEquals((short) -32767, buf[3], "Value at min should remain -32767");
        assertEquals((short) 0, buf[4], "Zero should remain zero");
    }

    @Test
    public void testClearBuffer() {
        SampleBuffer buffer = new SampleBuffer(44100, 2);

        // Add some samples
        for (int i = 0; i < 32; i++) {
            buffer.append(0, (short) i);
            buffer.append(1, (short) (i + 100));
        }

        assertEquals(64, buffer.getBufferLength(), "Should have 64 buffer positions before clear");

        buffer.clearBuffer();

        assertEquals(0, buffer.getBufferLength(), "Buffer length should be 0 after clear");
    }

    @Test
    public void testMultipleAppends() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        // First batch
        for (int i = 0; i < 16; i++) {
            buffer.append(0, (short) i);
        }

        assertEquals(16, buffer.getBufferLength(), "Should have 16 samples");

        // Second batch
        for (int i = 16; i < 32; i++) {
            buffer.append(0, (short) i);
        }

        assertEquals(32, buffer.getBufferLength(), "Should have 32 samples after second batch");
    }

    @Test
    public void testDifferentSampleRates() {
        int[] sampleRates = {8000, 11025, 12000, 16000, 22050, 24000,
                32000, 44100, 48000};

        for (int rate : sampleRates) {
            SampleBuffer buffer = new SampleBuffer(rate, 1);
            assertEquals(rate, buffer.getSampleFrequency(),
                    "Sample rate " + rate + " should match");
        }
    }

    @Test
    public void testGetBuffer() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        short[] buf = buffer.getBuffer();
        assertNotNull(buf, "Buffer should not be null");
        assertEquals(Obuffer.OBUFFERSIZE, buf.length, "Buffer should have OBUFFERSIZE length");
    }

    @Test
    public void testZeroValues() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        float[] samples = new float[32];
        buffer.appendSamples(0, samples);

        assertEquals(32, buffer.getBufferLength(), "Should have 32 samples");

        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            assertEquals((short) 0, buf[i], "Zero sample should remain zero");
        }
    }

    @Test
    public void testNegativeValues() {
        SampleBuffer buffer = new SampleBuffer(44100, 1);

        float[] samples = new float[32];
        for (int i = 0; i < 32; i++) {
            samples[i] = -10000.0f;
        }

        buffer.appendSamples(0, samples);

        short[] buf = buffer.getBuffer();
        for (int i = 0; i < 32; i++) {
            assertEquals((short) -10000, buf[i], "Negative sample should be preserved");
        }
    }
}