package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for BitReserve bit reservoir implementation.
 * Tests bit-level reading and writing operations.
 */
public class BitReserveTest {

    @Test
    public void testHget1bit() {
        BitReserve br = new BitReserve();

        // Write a known bit pattern (MSB first: 10110101)
        br.hputbuf(0b10110101);

        // Read back the bits (returns 0x80, 0x40, 0x20, etc. for set bits, 0 for clear)
        int bit0 = br.hget1bit();
        int bit1 = br.hget1bit();
        int bit2 = br.hget1bit();
        int bit3 = br.hget1bit();

        assertEquals(0x80, bit0, "First bit should be 0x80");
        assertEquals(0, bit1, "Second bit should be 0");
        assertEquals(0x20, bit2, "Third bit should be 0x20 (32)");
        assertEquals(0x10, bit3, "Fourth bit should be 0x10 (16)");
    }

    @Test
    public void testHget1bitZeroPattern() {
        BitReserve br = new BitReserve();

        // Write a zero byte
        br.hputbuf(0x00);

        // Read back the bits - all should be 0
        for (int i = 0; i < 8; i++) {
            int bit = br.hget1bit();
            assertEquals(0, bit, "Bit " + i + " should be 0");
        }
    }

    @Test
    public void testHget1bitOnePattern() {
        BitReserve br = new BitReserve();

        // Write a one byte (0xFF = all bits set)
        br.hputbuf(0xFF);

        // Read back the bits - each returns its bit position value (0x80, 0x40, etc.)
        int[] expectedBits = {0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01};
        for (int i = 0; i < 8; i++) {
            int bit = br.hget1bit();
            assertEquals(expectedBits[i], bit, "Bit " + i + " should be 0x" + Integer.toHexString(expectedBits[i]).toUpperCase());
        }
    }

    @Test
    public void testHgetbits() {
        BitReserve br = new BitReserve();

        // Write two bytes: 0b10110101 0b11001100
        br.hputbuf(0b10110101);
        br.hputbuf(0b11001100);

        // Read 8 bits - should get first byte
        int value1 = br.hgetbits(8);
        assertEquals(0b10110101, value1, "First 8 bits should match first byte");

        // Read 8 bits - should get second byte
        int value2 = br.hgetbits(8);
        assertEquals(0b11001100, value2, "Second 8 bits should match second byte");
    }

    @Test
    public void testHgetbitsPartialByte() {
        BitReserve br = new BitReserve();

        // Write a byte
        br.hputbuf(0b10110101);

        // Read 4 bits - should get upper nibble
        int value = br.hgetbits(4);
        assertEquals(0b1011, value, "Upper 4 bits should be 1011");

        // Read remaining 4 bits
        value = br.hgetbits(4);
        assertEquals(0b0101, value, "Lower 4 bits should be 0101");
    }

    @Test
    public void testHgetbitsMultipleBytes() {
        BitReserve br = new BitReserve();

        // Write 3 bytes
        br.hputbuf(0xAA);
        br.hputbuf(0x55);
        br.hputbuf(0xFF);

        // Read 16 bits (2 bytes)
        int value = br.hgetbits(16);
        assertEquals(0xAA55, value, "16 bits should span two bytes");
    }

    @Test
    public void testHstell() {
        BitReserve br = new BitReserve();

        // Initially totbit should be 0
        assertEquals(0, br.hsstell(), "Initial totbit should be 0");

        // Write and read bits
        br.hputbuf(0xFF);
        br.hgetbits(4);

        // totbit should be 4 (bits read)
        assertEquals(4, br.hsstell(), "totbit should be 4 after reading 4 bits");
    }

    @Test
    public void testRewindNBits() {
        BitReserve br = new BitReserve();

        // Write a byte
        br.hputbuf(0b10110101);

        // Read 4 bits
        int value1 = br.hgetbits(4);
        assertEquals(0b1011, value1, "First 4 bits should be 1011");

        // Rewind 2 bits
        br.rewindNBits(2);

        // Read 4 bits again
        int value2 = br.hgetbits(4);
        assertEquals(0b1101, value2, "After rewinding 2 bits, should get different value");
    }

    @Test
    public void testRewindNBytes() {
        BitReserve br = new BitReserve();

        // Write 3 bytes
        br.hputbuf(0xAA);
        br.hputbuf(0x55);
        br.hputbuf(0xFF);

        // Read 8 bits
        int value1 = br.hgetbits(8);
        assertEquals(0xAA, value1, "First byte should be 0xAA");

        // Rewind 1 byte
        br.rewindNBytes(1);

        // Read 8 bits again
        int value2 = br.hgetbits(8);
        assertEquals(0xAA, value2, "After rewinding 1 byte, should get same value");
    }

    @Test
    public void testMultiplePutGetCycles() {
        BitReserve br = new BitReserve();

        // Multiple write/read cycles
        for (int i = 0; i < 10; i++) {
            int value = 0xABCD & (i << 8);
            br.hputbuf((byte) value);
            br.hputbuf((byte) (value >> 8));

            int read1 = br.hgetbits(8);
            int read2 = br.hgetbits(8);

            assertEquals(value & 0xFF, read1, "Cycle " + i + " byte 1 mismatch");
            assertEquals((value >> 8) & 0xFF, read2, "Cycle " + i + " byte 2 mismatch");
        }
    }

    @Test
    public void testBitReserveBufferCapacity() {
        BitReserve br = new BitReserve();

        // Write many bytes to test buffer wrapping
        for (int i = 0; i < 100; i++) {
            br.hputbuf((byte) i);
        }

        // Read them back
        for (int i = 0; i < 100; i++) {
            int value = br.hgetbits(8);
            assertEquals(i & 0xFF, value, "Byte " + i + " should match");
        }
    }

    @Test
    public void testMixedOperations() {
        BitReserve br = new BitReserve();

        // Mix of put and get operations
        br.hputbuf(0xFF);
        br.hget1bit();
        br.hputbuf(0x00);
        br.hgetbits(4);
        br.hputbuf(0xAA);

        // After operations: bufByteIdx=5, offset=24
        // 0xFF bits at positions 0-7: 0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
        // 0x00 bits at positions 8-15: 0, 0, 0, 0, 0, 0, 0, 0
        // 0xAA bits at positions 16-23: 0x80, 0, 0x20, 0, 0x08, 0, 0x02, 0
        // Reading 4 bits from position 5: positions 5,6,7,8
        // Position 5: 0x04 (non-zero = 1), pos 6: 0x02 (1), pos 7: 0x01 (1), pos 8: 0 (0)
        // Result: 0b1110 = 14
        int value = br.hgetbits(4);
        assertEquals(14, value, "Should read 0b1110 from mixed buffer");

        value = br.hgetbits(8);
        // Continue from position 9: 0x00 bits at 9-15 (all 0), then 0xAA at 16
        // Position 16: 0x80 (non-zero = 1)
        // Reading 8 bits: 0,0,0,0,0,0,0,1 = 0b00000001 = 1
        assertEquals(1, value, "Should read next 8 bits");
    }
}