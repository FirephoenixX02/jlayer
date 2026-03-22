package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for Header class.
 * Tests MPEG audio frame header parsing and metadata extraction.
 */
public class HeaderTest {

    @Test
    public void testSampleFrequencies() {
        // MPEG-1 frequencies
        assertEquals(44100, Header.frequencies[Header.MPEG1][Header.FOURTYFOUR_POINT_ONE]);
        assertEquals(48000, Header.frequencies[Header.MPEG1][Header.FOURTYEIGHT]);
        assertEquals(32000, Header.frequencies[Header.MPEG1][Header.THIRTYTWO]);

        // MPEG-2 frequencies
        assertEquals(22050, Header.frequencies[Header.MPEG2_LSF][Header.FOURTYFOUR_POINT_ONE]);
        assertEquals(24000, Header.frequencies[Header.MPEG2_LSF][Header.FOURTYEIGHT]);
        assertEquals(16000, Header.frequencies[Header.MPEG2_LSF][Header.THIRTYTWO]);

        // MPEG-2.5 frequencies
        assertEquals(11025, Header.frequencies[Header.MPEG25_LSF][Header.FOURTYFOUR_POINT_ONE]);
        assertEquals(12000, Header.frequencies[Header.MPEG25_LSF][Header.FOURTYEIGHT]);
        assertEquals(8000, Header.frequencies[Header.MPEG25_LSF][Header.THIRTYTWO]);
    }

    @Test
    public void testModeConstants() {
        assertEquals(0, Header.STEREO);
        assertEquals(1, Header.JOINT_STEREO);
        assertEquals(2, Header.DUAL_CHANNEL);
        assertEquals(3, Header.SINGLE_CHANNEL);
    }

    @Test
    public void testVersionConstants() {
        assertEquals(1, Header.MPEG1);
        assertEquals(0, Header.MPEG2_LSF);
        assertEquals(2, Header.MPEG25_LSF);
    }

    @Test
    public void testBitrateTableMPEG1() {
        // Layer I bitrate table for MPEG-1
        int[] layer1Bitrates = Header.bitrates[Header.MPEG1][0];
        assertEquals(32000, layer1Bitrates[1]);
        assertEquals(64000, layer1Bitrates[2]);
        assertEquals(96000, layer1Bitrates[3]);
        assertEquals(128000, layer1Bitrates[4]);
        assertEquals(160000, layer1Bitrates[5]);
        assertEquals(192000, layer1Bitrates[6]);
        assertEquals(224000, layer1Bitrates[7]);
        assertEquals(256000, layer1Bitrates[8]);
    }

    @Test
    public void testBitrateTableMPEG1Layer3() {
        // Layer III bitrate table for MPEG-1
        int[] layer3Bitrates = Header.bitrates[Header.MPEG1][2];
        assertEquals(32000, layer3Bitrates[1]);
        assertEquals(40000, layer3Bitrates[2]);
        assertEquals(48000, layer3Bitrates[3]);
        assertEquals(56000, layer3Bitrates[4]);
        assertEquals(64000, layer3Bitrates[5]);
        assertEquals(80000, layer3Bitrates[6]);
        assertEquals(96000, layer3Bitrates[7]);
        assertEquals(112000, layer3Bitrates[8]);
        assertEquals(128000, layer3Bitrates[9]);
        assertEquals(320000, layer3Bitrates[14]);
    }

    @Test
    public void testBitrateTableMPEG2() {
        // Layer III bitrate table for MPEG-2
        int[] layer3Bitrates = Header.bitrates[Header.MPEG2_LSF][2];
        assertEquals(8000, layer3Bitrates[1]);
        assertEquals(16000, layer3Bitrates[2]);
        assertEquals(32000, layer3Bitrates[4]);
    }

    @Test
    public void testBitrateStringMPEG1Layer3() {
        String[] bitrateStrings = Header.bitrate_str[Header.MPEG1][2];
        assertEquals("32 kbit/s", bitrateStrings[1]);
        assertEquals("40 kbit/s", bitrateStrings[2]);
        assertEquals("48 kbit/s", bitrateStrings[3]);
        assertEquals("56 kbit/s", bitrateStrings[4]);
        assertEquals("64 kbit/s", bitrateStrings[5]);
        assertEquals("80 kbit/s", bitrateStrings[6]);
        assertEquals("96 kbit/s", bitrateStrings[7]);
        assertEquals("112 kbit/s", bitrateStrings[8]);
        assertEquals("128 kbit/s", bitrateStrings[9]);
        assertEquals("320 kbit/s", bitrateStrings[14]);
        assertEquals("forbidden", bitrateStrings[15]);
    }
}