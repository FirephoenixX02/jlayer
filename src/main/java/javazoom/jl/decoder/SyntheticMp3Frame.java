/*
 * Synthetic MP3 frame generator for unit tests.
 * Creates valid MP3 frame headers for testing without requiring actual MP3 files.
 */

package javazoom.jl.decoder;

/**
 * Utility class for generating synthetic MP3 frame data for unit tests.
 * This allows testing decoder components without requiring external MP3 files.
 */
public class SyntheticMp3Frame {

    /**
     * Creates a valid MPEG-1 Layer III frame header.
     *
     * @param bitrateIndex    bitrate index (1-14 for Layer III)
     * @param sampleRateIndex sample rate index (0=44.1kHz, 1=48kHz, 2=32kHz)
     * @param mode            channel mode (0=stereo, 1=joint stereo, 2=dual channel, 3=mono)
     * @param padding         1 if frame is padded, 0 otherwise
     * @param protectionBit   0 if CRC protected, 1 if not protected
     * @return 32-bit frame header
     */
    public static int createMpeg1Layer3Header(int bitrateIndex, int sampleRateIndex,
                                              int mode, int padding, int protectionBit) {
        // MPEG-1 Layer III header format:
        // Bits 0-11:   0xFFF (sync word)
        // Bit 12:      0 (MPEG version)
        // Bit 13-14:   11 (Layer III)
        // Bit 15:      protection bit
        // Bit 16-19:   bitrate index
        // Bit 20-21:   sample rate index
        // Bit 22:      padding bit
        // Bit 23:      0 (private bit)
        // Bit 24-25:   mode
        // Bit 26-27:   mode extension
        // Bit 28:      0 (copyright)
        // Bit 29:      0 (original)
        // Bit 30-31:   01 (emphasis - none)

        int header = 0xFFF80000; // Sync word + version + layer
        header |= (protectionBit << 15);
        header |= (bitrateIndex << 12);
        header |= (sampleRateIndex << 10);
        header |= (padding << 9);
        header |= (mode << 6);
        header |= (0); // mode extension
        header |= (0); // copyright
        header |= (0); // original
        header |= (0); // emphasis

        return header;
    }

    /**
     * Creates a valid MPEG-2 Layer III frame header (LSF).
     *
     * @param bitrateIndex    bitrate index (1-8 for Layer III LSF)
     * @param sampleRateIndex sample rate index (0=24kHz, 1=22.05kHz, 2=16kHz)
     * @param mode            channel mode
     * @param padding         1 if frame is padded, 0 otherwise
     * @param protectionBit   0 if CRC protected, 1 if not protected
     * @return 32-bit frame header
     */
    public static int createMpeg2Layer3Header(int bitrateIndex, int sampleRateIndex,
                                              int mode, int padding, int protectionBit) {
        // MPEG-2 Layer III LSF header format:
        // Bits 0-11:   0xFFF (sync word)
        // Bit 12:      0 (MPEG-2)
        // Bit 13-14:   11 (Layer III)
        // ... rest same as MPEG-1

        int header = 0xFFF10000; // Sync word + MPEG-2 + Layer III
        header |= (protectionBit << 15);
        header |= (bitrateIndex << 12);
        header |= (sampleRateIndex << 10);
        header |= (padding << 9);
        header |= (mode << 6);
        header |= (0); // mode extension
        header |= (0); // copyright
        header |= (0); // original
        header |= (0); // emphasis

        return header;
    }

    /**
     * Creates a valid MPEG-2.5 Layer III frame header (LSF).
     *
     * @param bitrateIndex    bitrate index
     * @param sampleRateIndex sample rate index (0=12kHz, 1=11.025kHz, 2=8kHz)
     * @param mode            channel mode
     * @param padding         1 if frame is padded, 0 otherwise
     * @param protectionBit   0 if CRC protected, 1 if not protected
     * @return 32-bit frame header
     */
    public static int createMpeg25Layer3Header(int bitrateIndex, int sampleRateIndex,
                                               int mode, int padding, int protectionBit) {
        int header = 0xFFF10000; // Sync word + MPEG-2 + Layer III

        // MPEG-2.5 detection: version=0, layer=11, but sample rate index indicates MPEG-2.5
        header |= (protectionBit << 15);
        header |= (bitrateIndex << 12);
        header |= (sampleRateIndex << 10);
        header |= (padding << 9);
        header |= (mode << 6);
        header |= (0);
        header |= (0);
        header |= (0);
        header |= (0);

        return header;
    }

    /**
     * Creates a complete synthetic MP3 frame with header and dummy payload.
     *
     * @param header     32-bit frame header
     * @param includeCRC true if CRC should be included
     * @return byte array containing header + CRC (if applicable) + payload
     */
    public static byte[] createFrame(int header, boolean includeCRC) {

        // Calculate frame size based on header
        // This is a simplified calculation for test purposes
        int framesize = calculateTestFrameSize(header);

        int crcSize = includeCRC ? 2 : 0;
        int headerSize = 4;
        int totalSize = headerSize + crcSize + framesize;

        byte[] frame = new byte[totalSize];

        // Write header
        frame[0] = (byte) ((header >> 24) & 0xFF);
        frame[1] = (byte) ((header >> 16) & 0xFF);
        frame[2] = (byte) ((header >> 8) & 0xFF);
        frame[3] = (byte) (header & 0xFF);

        // Write CRC if included
        if (includeCRC) {
            Crc16 crc = new Crc16();
            crc.addBits(header, 16);
            short checksum = crc.checksum();
            frame[4] = (byte) ((checksum >> 8) & 0xFF);
            frame[5] = (byte) (checksum & 0xFF);
        }

        // Write dummy payload (all zeros)
        for (int i = headerSize + crcSize; i < totalSize; i++) {
            frame[i] = 0;
        }

        return frame;
    }

    /**
     * Calculates approximate frame size for test purposes.
     *
     * @param header 32-bit frame header
     * @return frame size in bytes (excluding header)
     */
    private static int calculateTestFrameSize(int header) {
        int version = (header >> 19) & 1;
        int bitrateIndex = (header >> 12) & 0xF;
        int sampleRateIndex = (header >> 10) & 3;
        int padding = (header >> 9) & 1;

        // Simplified frame size calculation
        // Layer III: framesize = (144 * bitrate) / sample_rate + padding
        int[] sampleRates = {44100, 48000, 32000, 1};
        int[] bitrates = {0, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                112000, 128000, 160000, 192000, 224000, 256000, 320000, 0};

        int sampleRate = sampleRates[sampleRateIndex];
        int bitrate = bitrates[bitrateIndex];

        if (sampleRate == 1 || bitrate == 0) {
            return 0; // Invalid
        }

        int framesize = (144 * bitrate) / sampleRate;
        if (version == 0) { // MPEG-2 LSF
            framesize /= 2;
        }
        framesize += padding;

        return Math.max(0, framesize - 4); // Subtract header size
    }

    /**
     * Creates a synthetic MP3 frame with specific test parameters.
     *
     * @param version    MPEG version (1=MPEG-1, 0=MPEG-2, 2=MPEG-2.5)
     * @param bitrate    Bitrate in bps
     * @param sampleRate Sample rate in Hz
     * @param mode       Channel mode
     * @param padding    1 if padded, 0 otherwise
     * @return byte array containing the frame
     */
    public static byte[] createTestFrame(int version, int bitrate,
                                         int sampleRate, int mode, int padding, int protection) {
        int header;

        if (version == 1) {
            // MPEG-1
            header = createMpeg1Layer3Header(getBitrateIndex(bitrate),
                    getSampleRateIndex(sampleRate), mode, padding, protection);
        } else if (version == 0) {
            // MPEG-2 LSF
            header = createMpeg2Layer3Header(getBitrateIndex(bitrate),
                    getSampleRateIndex(sampleRate), mode, padding, protection);
        } else {
            // MPEG-2.5 LSF
            header = createMpeg25Layer3Header(getBitrateIndex(bitrate),
                    getSampleRateIndex(sampleRate), mode, padding, protection);
        }

        return createFrame(header, protection == 0);
    }

    /**
     * Gets bitrate index for a given bitrate.
     */
    private static int getBitrateIndex(int bitrate) {
        int[] bitrates = {0, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                112000, 128000, 160000, 192000, 224000, 256000, 320000, 0};
        for (int i = 0; i < bitrates.length; i++) {
            if (bitrates[i] == bitrate) {
                return i;
            }
        }
        return 9; // Default to 128 kbps
    }

    /**
     * Gets sample rate index for a given sample rate.
     */
    private static int getSampleRateIndex(int sampleRate) {
        // MPEG-1
        if (sampleRate == 44100) return 0;
        if (sampleRate == 48000) return 1;
        if (sampleRate == 32000) return 2;

        // MPEG-2/2.5
        if (sampleRate == 22050) return 0;
        if (sampleRate == 24000) return 1;
        if (sampleRate == 16000) return 2;

        if (sampleRate == 11025) return 0;
        if (sampleRate == 12000) return 1;
        if (sampleRate == 8000) return 2;

        return 0; // Default
    }

    /**
     * Creates a sequence of synthetic frames for testing streaming.
     *
     * @param numFrames Number of frames to create
     * @param version   MPEG version
     * @return byte array containing all frames
     */
    public static byte[] createFrameSequence(int numFrames, int version) {
        int totalSize = 0;
        byte[][] frames = new byte[numFrames][];

        for (int i = 0; i < numFrames; i++) {
            frames[i] = createTestFrame(version, 128000, 44100, 0, 0, 1);
            totalSize += frames[i].length;
        }

        byte[] sequence = new byte[totalSize];
        int offset = 0;
        for (byte[] frame : frames) {
            System.arraycopy(frame, 0, sequence, offset, frame.length);
            offset += frame.length;
        }

        return sequence;
    }
}