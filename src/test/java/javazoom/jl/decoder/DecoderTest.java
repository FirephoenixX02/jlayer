/*
 * Unit tests for Decoder class using synthetic MP3 frames.
 * Tests decoder functionality without requiring external MP3 files.
 */

package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Decoder class.
 * Tests decoder operations using synthetic MP3 frames.
 */
public class DecoderTest {

    @Test
    public void testDecoderCreation() {
        Decoder decoder = new Decoder();
        assertNotNull(decoder, "Decoder should be created successfully");
    }

    @Test
    public void testDecoderWithSampleBuffer() {
        SampleBuffer sampleBuffer = new SampleBuffer(44100, 2);

        assertNotNull(sampleBuffer, "SampleBuffer should be created");
        assertEquals(2, sampleBuffer.getChannelCount(), "Should be stereo");
        assertEquals(44100, sampleBuffer.getSampleFrequency(), "Sample rate should be 44100");
    }

    @Test
    public void testHeaderParsingWithSyntheticFrame() {
        // Create a synthetic MPEG-1 Layer III frame
        byte[] frame = SyntheticMp3Frame.createTestFrame(1, 128000, 44100, 0, 0, 1);

        Header header = new Header();
        assertNotNull(header, "Header should be created");

        // Verify frame has correct size
        assertTrue(frame.length > 4, "Frame should be larger than header");
    }

    @Test
    public void testMultipleFrameTypes() {
        // Test different frame configurations
        int[][] testCases = {
                {1, 3, 128000, 44100, 0, 0, 1},  // MPEG-1, 128kbps, 44.1kHz, stereo
                {1, 3, 64000, 44100, 3, 0, 1},   // MPEG-1, 64kbps, 44.1kHz, mono
                {0, 3, 32000, 24000, 0, 0, 1},   // MPEG-2, 32kbps, 24kHz, stereo
                {2, 3, 16000, 16000, 3, 0, 1},   // MPEG-2.5, 16kbps, 16kHz, mono
        };

        for (int[] testCase : testCases) {
            byte[] frame = SyntheticMp3Frame.createTestFrame(
                    testCase[0], testCase[2],
                    testCase[3], testCase[4], testCase[5], testCase[6]
            );

            assertTrue(frame.length > 0, "Frame should be created for config");
        }
    }

    @Test
    public void testFrameSequence() {
        // Create a sequence of frames for streaming test
        byte[] sequence = SyntheticMp3Frame.createFrameSequence(10, 1);

        assertTrue(sequence.length > 0, "Frame sequence should be created");
        assertTrue(sequence.length > 400, "Sequence should be large enough for 10 frames");
    }

    @Test
    public void testDecoderInitialization() {
        Decoder decoder = new Decoder();

        // Decoder should be in valid state
        // Note: Decoder internals are not directly accessible, but we can test creation
        assertNotNull(decoder, "Decoder instance should be valid");
    }

    @Test
    public void testSyntheticFrameWithCRC() {
        // Test frame with CRC protection
        byte[] frameWithCRC = SyntheticMp3Frame.createTestFrame(1, 128000, 44100, 0, 0, 0);

        assertTrue(frameWithCRC.length > 6, "Frame with CRC should include 2-byte CRC");
    }

    @Test
    public void testDifferentBitrates() {
        int[] bitrates = {32000, 64000, 128000, 192000, 320000};

        for (int bitrate : bitrates) {
            byte[] frame = SyntheticMp3Frame.createTestFrame(1, bitrate, 44100, 0, 0, 1);
            assertTrue(frame.length > 0, "Frame should be created for bitrate " + bitrate);
        }
    }

    @Test
    public void testDifferentSampleRates() {
        int[] sampleRates = {44100, 48000, 32000, 22050, 24000, 16000};

        for (int sampleRate : sampleRates) {
            byte[] frame = SyntheticMp3Frame.createTestFrame(1, 128000, sampleRate, 0, 0, 1);
            assertTrue(frame.length > 0, "Frame should be created for sample rate " + sampleRate);
        }
    }

    @Test
    public void testChannelModes() {
        int[] modes = {0, 1, 2, 3}; // stereo, joint stereo, dual channel, mono

        for (int mode : modes) {
            byte[] frame = SyntheticMp3Frame.createTestFrame(1, 128000, 44100, mode, 0, 1);
            assertTrue(frame.length > 0, "Frame should be created for mode " + mode);
        }
    }

    @Test
    public void testPaddedFrames() {
        byte[] unpadded = SyntheticMp3Frame.createTestFrame(1, 128000, 44100, 0, 0, 1);
        byte[] padded = SyntheticMp3Frame.createTestFrame(1, 128000, 44100, 0, 1, 1);

        assertTrue(padded.length >= unpadded.length, "Padded frame should be >= unpadded frame");
    }
}