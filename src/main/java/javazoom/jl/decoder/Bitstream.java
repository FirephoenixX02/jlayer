/*
 * 11/19/04  1.0 moved to LGPL.
 *
 * 11/17/04     Uncomplete frames discarded. E.B, javalayer@javazoom.net
 *
 * 12/05/03     ID3v2 tag returned. E.B, javalayer@javazoom.net
 *
 * 12/12/99     Based on Ibitstream. Exceptions thrown on errors,
 *             Temporary removed seek functionality. mdm@techie.com
 *
 * 02/12/99 : Java Conversion by E.B , javalayer@javazoom.net
 *
 * 04/14/97 : Added function prototypes for new syncing and seeking
 * mechanisms. Also made this file portable. Changes made by Jeff Tsay
 *
 *  @(#) ibitstream.h 1.5, last edit: 6/15/94 16:55:34
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package javazoom.jl.decoder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;


/**
 * The <code>Bitstream</code> class is responsible for parsing
 * an MPEG audio bitstream. It handles frame synchronization,
 * reading frame data, and extracting bits from the stream.
 * <p>
 * The bitstream class supports:
 * <ul>
 *   <li>MPEG-1, MPEG-2, and MPEG-2.5 audio layers</li>
 *   <li>ID3v2 tag detection and retrieval</li>
 *   <li>CRC error checking for protected frames</li>
 *   <li>VBR (XING/VBRI) header detection</li>
 * </ul>
 * <p>
 * <b>REVIEW:</b> much of the parsing currently occurs in the
 * various decoders. This should be moved into this class and associated
 * inner classes.
 * <p>
 * This class is not thread-safe and should be used by a single thread.
 */
public final class Bitstream implements BitstreamErrors {

    /**
     * Synchronization control constant for the initial
     * synchronization to the start of a frame.
     */
    static final byte INITIAL_SYNC = 0;

    /**
     * Synchronization control constant for non-initial frame
     * synchronizations.
     */
    static final byte STRICT_SYNC = 1;

    /**
     * Maximum size of the frame buffer in integers.
     * <p>
     * Supports max. 1730 bytes per frame:
     * 144 * 384kbit/s / 32000 Hz + 2 Bytes CRC
     */
    private static final int BUFFER_INT_SIZE = 433;

    /**
     * The frame buffer that holds the data for the current frame.
     */
    private final int[] frameBuffer = new int[BUFFER_INT_SIZE];

    /**
     * Number of valid bytes in the frame buffer.
     */
    private int frameSize;

    /**
     * The bytes read from the stream.
     */
    private final byte[] frameBytes = new byte[BUFFER_INT_SIZE * 4];

    /**
     * Index into {@code frameBuffer} where the next bits are
     * retrieved.
     */
    private int wordPointer;

    /**
     * Number (0-31, from MSB to LSB) of next bit for get_bits().
     */
    private int bitindex;

    /**
     * The current specified syncword for frame synchronization.
     */
    private int syncWord;

    /**
     * Audio header position in stream (offset after ID3v2 tag).
     */
    private int headerPos = 0;

    /**
     * Flag indicating single channel (mono) mode.
     */
    private boolean singleChMode;

    /**
     * Bitmask array for extracting variable-length bit fields.
     * Index corresponds to number of bits to extract.
     */
    private final int[] bitmask = {
            0, // dummy
            0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F, 0x0000003F, 0x0000007F, 0x000000FF, 0x000001FF, 0x000003FF,
            0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF, 0x00007FFF, 0x0000FFFF, 0x0001FFFF
    };

    /**
     * The source input stream wrapped in a PushbackInputStream.
     */
    private final PushbackInputStream source;

    /**
     * The header object for the current frame.
     */
    private final Header header = new Header();

    /**
     * Temporary buffer for reading sync bytes.
     */
    private final byte[] syncBuf = new byte[4];

    /**
     * CRC calculator for frame protection checking.
     */
    private final Crc16[] crc = new Crc16[1];

    /**
     * Raw ID3v2 tag data if present in the stream.
     */
    private byte[] rawid3v2 = null;

    /**
     * Flag indicating if this is the first frame being read.
     */
    private boolean firstframe;

    /**
     * Constructs a Bitstream that reads data from a given InputStream.
     * <p>
     * The input stream is wrapped in a BufferedInputStream for
     * efficient reading. ID3v2 tags are detected and stored if
     * present at the beginning of the stream.
     *
     * @param in The InputStream to read from. Must not be null.
     * @throws NullPointerException if in is null.
     */
    public Bitstream(InputStream in) {
        if (in == null)
            throw new NullPointerException("in");
        in = new BufferedInputStream(in);
        loadID3v2(in);
        firstframe = true;
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

        closeFrame();
    }

    /**
     * Returns the position of the first audio header in the stream.
     * <p>
     * This value represents the size of any ID3v2 tag frames
     * plus the 10-byte ID3v2 header. If no ID3v2 tag is present,
     * returns 0.
     *
     * @return The offset in bytes from the start of the stream
     *         to the first audio header.
     */
    public int headerPos() {
        return headerPos;
    }

    /**
     * Loads ID3v2 frames from the input stream if present.
     * <p>
     * This method reads the 10-byte ID3v2 header to determine
     * the tag size, then stores the complete tag for later
     * retrieval via {@link #getRawID3v2()}.
     *
     * @param in The MP3 InputStream to read from.
     */
    private void loadID3v2(InputStream in) {
        int size = -1;
        try {
            // Read ID3v2 header (10 bytes).
            in.mark(10);
            size = readID3v2Header(in);
            headerPos = size;
        } catch (IOException e) {
        } finally {
            try {
                // Unread ID3v2 header (10 bytes).
                in.reset();
            } catch (IOException e) {
            }
        }
        // Load ID3v2 tags.
        try {
            if (size > 0) {
                rawid3v2 = new byte[size];
                in.read(rawid3v2, 0, rawid3v2.length);
            }
        } catch (IOException e) {
        }
    }

    /**
     * Parses the ID3v2 tag header to determine the size of ID3v2 frames.
     * <p>
     * Reads the 10-byte ID3v2 header and extracts the tag size
     * from the synched integer in bytes 6-9.
     *
     * @param in The MP3 InputStream containing the ID3v2 header.
     * @return The size of ID3v2 frames plus header (10 bytes),
     *         or -10 if not an ID3v2 tag.
     * @throws IOException if reading the header fails.
     */
    private int readID3v2Header(InputStream in) throws IOException {
        byte[] id3header = new byte[4];
        int size = -10;
        in.read(id3header, 0, 3);
        // Look for ID3v2
        if ((id3header[0] == 'I') && (id3header[1] == 'D') && (id3header[2] == '3')) {
            in.read(id3header, 0, 3);
            int majorVersion = id3header[0];
            int revision = id3header[1];
            in.read(id3header, 0, 4);
            size = (id3header[0] << 21) + (id3header[1] << 14) + (id3header[2] << 7) + (id3header[3]);
        }
        return (size + 10);
    }

    /**
     * Returns raw ID3v2 frames plus header as an InputStream.
     * <p>
     * This method provides access to the complete ID3v2 tag
     * data if one was present at the beginning of the stream.
     * The returned stream can be read to parse ID3v2 frame data.
     *
     * @return An InputStream containing the raw ID3v2 data,
     *         or null if no ID3v2 tag was found.
     */
    public InputStream getRawID3v2() {
        if (rawid3v2 == null)
            return null;
        else {
            return new ByteArrayInputStream(rawid3v2);
        }
    }

    /**
     * Closes the Bitstream and releases underlying resources.
     * <p>
     * This method closes the underlying input stream. After
     * calling this method, the bitstream cannot be used to
     * read additional frames.
     *
     * @throws BitstreamException if an error occurs while
     *                            closing the stream.
     */
    public void close() throws BitstreamException {
        try {
            source.close();
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
    }

    /**
     * Reads and parses the next frame from the input source.
     * <p>
     * This method performs frame synchronization, reads the
     * frame data, and parses the frame header. If an invalid
     * frame is encountered, it attempts to skip to the next
     * valid frame.
     * <p>
     * VBR (XING/VBRI) header information is parsed from the
     * first frame if present.
     *
     * @return The Header describing details of the frame read,
     *         or null if the end of the stream has been reached.
     * @throws BitstreamException if a fatal error occurs while
     *                            reading the frame.
     */
    public Header readFrame() throws BitstreamException {
        Header result = null;
        try {
            result = readNextFrame();
            // E.B, Parse VBR (if any) first frame.
            if (firstframe) {
                result.parseVBR(frameBytes);
                firstframe = false;
            }
        } catch (BitstreamException ex) {
            if ((ex.getErrorCode() == INVALIDFRAME)) {
                // Try to skip this frame.
                //System.out.println("INVALIDFRAME");
                try {
                    closeFrame();
                    result = readNextFrame();
                } catch (BitstreamException e) {
                    if ((e.getErrorCode() != STREAM_EOF)) {
                        // wrap original exception so stack trace is maintained.
                        throw newBitstreamException(e.getErrorCode(), e);
                    }
                }
            } else if ((ex.getErrorCode() != STREAM_EOF)) {
                // wrap original exception so stack trace is maintained.
                throw newBitstreamException(ex.getErrorCode(), ex);
            }
        }
        return result;
    }

    /**
     * Read next MP3 frame.
     *
     * @return MP3 frame header.
     */
    private Header readNextFrame() throws BitstreamException {
        if (frameSize == -1) {
            nextFrame();
        }
        return header;
    }

    /**
     * Read next MP3 frame.
     *
     */
    private void nextFrame() throws BitstreamException {
        // entire frame is read by the header class.
        header.read_header(this, crc);
    }

    /**
     * Unreads the bytes read from the frame back into the stream.
     * <p>
     * This method allows the frame data to be re-read, which
     * is useful when frame validation fails and the frame
     * needs to be re-examined.
     * <p>
     * <b>REVIEW:</b> add new error codes for this operation.
     *
     * @throws BitstreamException if an error occurs while
     *                            unread the frame data.
     */
    public void unreadFrame() throws BitstreamException {
        if (wordPointer == -1 && bitindex == -1 && (frameSize > 0)) {
            try {
                source.unread(frameBytes, 0, frameSize);
            } catch (IOException ex) {
                throw newBitstreamException(STREAM_ERROR);
            }
        }
    }

    /**
     * Closes the current MP3 frame and resets internal state.
     * <p>
     * This method prepares the bitstream for reading the next
     * frame by resetting pointers and counters.
     */
    public void closeFrame() {
        frameSize = -1;
        wordPointer = -1;
        bitindex = -1;
    }

    /**
     * Determines if the next 4 bytes of the stream represent a
     * frame header.
     * <p>
     * This method checks the sync mark without consuming the
     * bytes from the stream, allowing peek-ahead synchronization.
     *
     * @param syncmode The synchronization mode. Use {@link #INITIAL_SYNC}
     *                 for initial sync or {@link #STRICT_SYNC} for
     *                 subsequent frames.
     * @return true if the bytes represent a valid frame header,
     *         false otherwise.
     * @throws BitstreamException if an error occurs while reading.
     */
    public boolean isSyncCurrentPosition(int syncmode) throws BitstreamException {
        int read = readBytes(syncBuf, 0, 4);
        int headerString = ((syncBuf[0] << 24) & 0xFF000000) | ((syncBuf[1] << 16) & 0x00FF0000)
                | ((syncBuf[2] << 8) & 0x0000FF00) | ((syncBuf[3]) & 0x000000FF);

        try {
            source.unread(syncBuf, 0, read);
        } catch (IOException ex) {
        }

        return switch (read) {
            case 0 -> true;
            case 4 -> isSyncMark(headerString, syncmode, syncWord);
            default -> false;
        };
    }

    /**
     * Reads bits from the bitstream.
     * <p>
     * This is a convenience method that delegates to
     * {@link #getBits(int)}.
     *
     * @param n The number of bits to read (1-16).
     * @return The bits read, in the lower bits of an int.
     */
    public int readBits(int n) {
        return getBits(n);
    }

    /**
     * Reads bits from the bitstream with CRC checking.
     * <p>
     * <b>REVIEW:</b> CRC check not yet implemented.
     * This method currently behaves the same as {@link #readBits(int)}.
     *
     * @param n The number of bits to read (1-16).
     * @return The bits read, in the lower bits of an int.
     */
    public int readCheckedBits(int n) {
        // REVIEW: implement CRC check.
        return getBits(n);
    }

    BitstreamException newBitstreamException(int errorcode) {
        return new BitstreamException(errorcode, null);
    }

    BitstreamException newBitstreamException(int errorcode, Throwable throwable) {
        return new BitstreamException(errorcode, throwable);
    }

    /**
     * Get next 32 bits from bitstream.
     * They are stored in the headerString.
     * syncMode allows Synchro flag ID
     * The returned value is False at the end of stream.
     */
    int syncHeader(byte syncMode) throws BitstreamException {
        boolean sync;
        int headerString;
        // read additional 2 bytes
        int bytesRead = readBytes(syncBuf, 0, 3);

        if (bytesRead != 3)
            throw newBitstreamException(STREAM_EOF, null);

        headerString = ((syncBuf[0] << 16) & 0x00FF0000) | ((syncBuf[1] << 8) & 0x0000FF00) | ((syncBuf[2]) & 0x000000FF);

        do {
            headerString <<= 8;

            if (readBytes(syncBuf, 3, 1) != 1)
                throw newBitstreamException(STREAM_EOF, null);

            headerString |= (syncBuf[3] & 0x000000FF);

            sync = isSyncMark(headerString, syncMode, syncWord);
        } while (!sync);

        return headerString;
    }

    public boolean isSyncMark(int headerstring, int syncmode, int word) {
        boolean sync;

        if (syncmode == INITIAL_SYNC) {
            //sync =  ((headerstring & 0xFFF00000) == 0xFFF00000);
            sync = ((headerstring & 0xFFE00000) == 0xFFE00000); // SZD: MPEG 2.5
        } else {
            sync = ((headerstring & 0xFFF80C00) == word) && (((headerstring & 0x000000C0) == 0x000000C0) == singleChMode);
        }

        // filter out invalid sample rate
        if (sync)
            sync = (((headerstring >>> 10) & 3) != 3);
        // filter out invalid layer
        if (sync)
            sync = (((headerstring >>> 17) & 3) != 0);
        // filter out invalid version
        if (sync)
            sync = (((headerstring >>> 19) & 3) != 1);

        return sync;
    }

    /**
     * Reads the data for the next frame. The frame is not parsed
     * until parse frame is called.
     */
    int readFrameData(int bytesize) throws BitstreamException {
        int numread;
        numread = readFully(frameBytes, 0, bytesize);
        frameSize = bytesize;
        wordPointer = -1;
        bitindex = -1;
        return numread;
    }

    /**
     * Parses the data previously read with read_frame_data().
     */
    void parseFrame() {
        // Convert Bytes read to int
        int b = 0;
        byte[] byteread = frameBytes;
        int bytesize = frameSize;

        // Check ID3v1 TAG (True only if last frame).

        for (int k = 0; k < bytesize; k = k + 4) {
            int convert = 0;
            byte b0;
            byte b1 = 0;
            byte b2 = 0;
            byte b3 = 0;
            b0 = byteread[k];
            if (k + 1 < bytesize)
                b1 = byteread[k + 1];
            if (k + 2 < bytesize)
                b2 = byteread[k + 2];
            if (k + 3 < bytesize)
                b3 = byteread[k + 3];
            frameBuffer[b++] = ((b0 << 24) & 0xFF000000) | ((b1 << 16) & 0x00FF0000) | ((b2 << 8) & 0x0000FF00)
                    | (b3 & 0x000000FF);
        }
        wordPointer = 0;
        bitindex = 0;
    }

    /**
     * Read bits from buffer into the lower bits of an unsigned int.
     * The LSB contains the latest read bit of the stream.
     * <p>
     * Number of bits must be between 1 and 16.
     *
     * @param number_of_bits The number of bits to read (1-16).
     * @return The bits read, in the lower bits of an int.
     */
    public int getBits(int number_of_bits) {
        int returnvalue;
        int sum = bitindex + number_of_bits;

        // E.B
        // There is a problem here, wordPointer could be -1 ?!
        if (wordPointer < 0)
            wordPointer = 0;
        // E.B : End.

        if (sum <= 32) {
            // all bits contained in *wordPointer
            returnvalue = (frameBuffer[wordPointer] >>> (32 - sum)) & bitmask[number_of_bits];
            if ((bitindex += number_of_bits) == 32) {
                bitindex = 0;
                wordPointer++; // added by me!
            }
            return returnvalue;
        }

        int Right = (frameBuffer[wordPointer] & 0x0000FFFF);
        wordPointer++;
        int Left = (frameBuffer[wordPointer] & 0xFFFF0000);
        returnvalue = ((Right << 16) & 0xFFFF0000) | ((Left >>> 16) & 0x0000FFFF);

        returnvalue >>>= 48 - sum;
        returnvalue &= bitmask[number_of_bits];
        bitindex = sum - 32;
        return returnvalue;
    }

    /**
     * Set the word we want to sync the header to.
     * In Big-Endian byte order
     */
    void setSyncWord(int syncWord) {
        this.syncWord = syncWord & 0xFFFFFF3F;
        singleChMode = ((syncWord & 0x000000C0) == 0x000000C0);
    }

    /**
     * Reads the exact number of bytes from the source
     * input stream into a byte array.
     *
     * @param b    The byte array to read the specified number
     *             of bytes into.
     * @param offs The index in the array where the first byte
     *             read should be stored.
     * @param len  the number of bytes to read.
     * @throws BitstreamException is thrown if the specified
     *                            number of bytes could not be read from the stream.
     */
    private int readFully(byte[] b, int offs, int len) throws BitstreamException {
        int nRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) {
                    while (len-- > 0) {
                        b[offs++] = 0;
                    }
                    break;
                    //throw newBitstreamException(UNEXPECTED_EOF, new EOFException());
                }
                nRead = nRead + bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return nRead;
    }

    /**
     * Similar to readFully, but doesn't throw exception when
     * EOF is reached.
     */
    private int readBytes(byte[] b, int offs, int len) throws BitstreamException {
        int totalBytesRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) {
                    break;
                }
                totalBytesRead += bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return totalBytesRead;
    }
}
