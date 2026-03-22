package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for Crc16 checksum calculator.
 * Tests the CRC-16 calculation with known values.
 */
public class Crc16Test {

    @Test
    public void testInitialValue() {
        Crc16 crc = new Crc16();
        // Initial value should be 0xFFFF
        short checksum = crc.checksum();
        assertEquals(-1, checksum, "Initial CRC value should be 0xFFFF");
    }

    @Test
    public void testAddBitsSingleBit() {
        Crc16 crc = new Crc16();
        crc.addBits(1, 1);
        short checksum = crc.checksum();
        // After adding 1 bit, checksum should not be 0xFFFF
        assertNotEquals(-1, checksum, "Checksum should change after adding bits");
    }

    @Test
    public void testAddBitsMultipleBits() {
        Crc16 crc = new Crc16();
        crc.addBits(0b1011, 4);
        crc.addBits(0b1100, 4);
        short checksum1 = crc.checksum();

        Crc16 crc2 = new Crc16();
        crc2.addBits(0b1011, 4);
        crc2.addBits(0b1100, 4);
        short checksum2 = crc2.checksum();

        assertEquals(checksum1, checksum2, "Same input should produce same checksum");
    }

    @Test
    public void testAddBitsDifferentLengths() {
        Crc16 crc1 = new Crc16();
        crc1.addBits(0b101, 3);

        Crc16 crc2 = new Crc16();
        crc2.addBits(0b0101, 4);

        // These should produce different checksums
        assertNotEquals(crc1.checksum(), crc2.checksum(),
                "Different bit representations should produce different checksums");
    }

    @Test
    public void testAddBitsZero() {
        Crc16 crc = new Crc16();
        crc.addBits(0, 8);
        short checksum = crc.checksum();
        // Adding zeros should still produce a valid checksum
        assertNotEquals(-1, checksum, "Checksum should be valid after adding zeros");
    }

    @Test
    public void testAddBitsMaxValue() {
        Crc16 crc = new Crc16();
        crc.addBits(0xFFFFFFFF, 32);
        short checksum = crc.checksum();
        // Should handle maximum 32-bit value
        assertNotEquals(-1, checksum, "Checksum should handle max 32-bit value");
    }

    @Test
    public void testMultipleOperations() {
        Crc16 crc = new Crc16();

        crc.addBits(0xABCD, 16);
        short checksum1 = crc.checksum();

        crc.addBits(0x1234, 16);
        short checksum2 = crc.checksum();

        assertNotEquals(checksum1, checksum2,
                "Multiple addBits operations should produce different checksums");
    }

    @Test
    public void testAddBitsReinitialization() {
        Crc16 crc = new Crc16();
        crc.addBits(0x1234, 16);
        short checksum1 = crc.checksum();

        // checksum() should reset the CRC
        crc.addBits(0x1234, 16);
        short checksum2 = crc.checksum();

        assertEquals(checksum1, checksum2,
                "Reusing CRC after checksum() should produce same result");
    }
}