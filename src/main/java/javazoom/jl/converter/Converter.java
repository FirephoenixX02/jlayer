/*
 * 11/19/04 1.0 moved to LGPL.
 * 12/12/99 Original version. mdm@techie.com.
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

package javazoom.jl.converter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Obuffer;


/**
 * The <code>Converter</code> class implements the conversion of
 * an MPEG audio file to a .WAV file. To convert an MPEG audio stream,
 * just create an instance of this class and call the convert()
 * method, passing in the names of the input and output files. You can
 * pass in optional <code>ProgressListener</code> and
 * <code>Decoder.Params</code> objects also to customize the conversion.
 *
 * @author MDM 12/12/99
 * @since 0.0.7
 */
public class Converter {

    /**
     * Creates a new converter instance.
     */
    public Converter() {
    }

    /**
     * Converts an MPEG audio file to a WAV file.
     * <p>
     * This method uses default progress logging to standard output
     * and default decoder parameters.
     *
     * @param sourceName The path to the source MPEG audio file.
     * @param destName   The path to the destination WAV file.
     * @throws JavaLayerException if conversion fails.
     */
    public synchronized void convert(String sourceName, String destName) throws JavaLayerException {
        convert(sourceName, destName, null, null);
    }

    /**
     * Converts an MPEG audio file to a WAV file with progress monitoring.
     * <p>
     * This method uses default decoder parameters and the specified
     * progress listener for notifications.
     *
     * @param sourceName        The path to the source MPEG audio file.
     * @param destName          The path to the destination WAV file.
     * @param progressListener  The listener for progress notifications.
     *                          If null, a default listener is used.
     * @throws JavaLayerException if conversion fails.
     */
    public synchronized void convert(String sourceName,
                                     String destName,
                                     ProgressListener progressListener) throws JavaLayerException {
        convert(sourceName, destName, progressListener, null);
    }

    /**
     * Converts an MPEG audio file to a WAV file with full customization.
     * <p>
     * This method allows specifying a progress listener and decoder
     * parameters for complete control over the conversion process.
     *
     * @param sourceName        The path to the source MPEG audio file.
     * @param destName          The path to the destination WAV file.
     * @param progressListener  The listener for progress notifications.
     *                          If null, a default listener is used.
     * @param decoderParams     The decoder configuration parameters.
     *                          If null, default parameters are used.
     * @throws JavaLayerException if conversion fails.
     */
    public void convert(String sourceName,
                        String destName,
                        ProgressListener progressListener,
                        Decoder.Params decoderParams) throws JavaLayerException {
        if (destName.isEmpty())
            destName = null;
        try {
            InputStream in = openInput(sourceName);
            convert(in, destName, progressListener, decoderParams);
            in.close();
        } catch (IOException ioe) {
            throw new JavaLayerException(ioe.getLocalizedMessage(), ioe);
        }
    }

    /**
     * Converts an MPEG audio stream to a WAV file.
     * <p>
     * This method reads from an input stream and writes to a WAV file.
     * The WAV file format is determined by the first frame's header.
     * <p>
     * If the input stream supports marking, the method performs
     * a pre-pass to count frames before conversion.
     *
     * @param sourceStream      The input stream containing MPEG audio data.
     * @param destName          The path to the destination WAV file.
     *                          If null, no WAV file is written.
     * @param progressListener  The listener for progress notifications.
     *                          If null, a default listener is used.
     * @param decoderParams     The decoder configuration parameters.
     *                          If null, default parameters are used.
     * @throws JavaLayerException if conversion fails.
     */
    public synchronized void convert(InputStream sourceStream,
                                     String destName,
                                     ProgressListener progressListener,
                                     Decoder.Params decoderParams) throws JavaLayerException {
        if (progressListener == null)
            progressListener = PrintWriterProgressListener.newStdOut(PrintWriterProgressListener.NO_DETAIL);
        try {
            if (!(sourceStream instanceof BufferedInputStream))
                sourceStream = new BufferedInputStream(sourceStream);
            int frameCount = -1;
            if (sourceStream.markSupported()) {
                sourceStream.mark(-1);
                frameCount = countFrames(sourceStream);
                sourceStream.reset();
            }
            progressListener.converterUpdate(ProgressListener.UPDATE_FRAME_COUNT, frameCount, 0);

            Obuffer output = null;
            Decoder decoder = new Decoder(decoderParams);
            Bitstream stream = new Bitstream(sourceStream);

            if (frameCount == -1)
                frameCount = Integer.MAX_VALUE;

            int frame = 0;
            long startTime = System.currentTimeMillis();

            try {
                for (; frame < frameCount; frame++) {
                    try {
                        Header header = stream.readFrame();
                        if (header == null)
                            break;

                        progressListener.readFrame(frame, header);

                        if (output == null) {
                            // REVIEW: Incorrect functionality.
                            // the decoder should provide decoded
                            // frequency and channels output as it may differ from
                            // the source (e.g. when downmixing stereo to mono.)
                            int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                            int freq = header.frequency();
                            output = new WaveFileObuffer(channels, freq, destName);
                            decoder.setOutputBuffer(output);
                        }

                        Obuffer decoderOutput = decoder.decodeFrame(header, stream);

                        // REVIEW: the way the output buffer is set
                        // on the decoder is a bit dodgy. Even though
                        // this exception should never happen, we test to be sure.
                        if (decoderOutput != output)
                            throw new InternalError("Output buffers are different.");

                        progressListener.decodedFrame(frame, header, output);

                        stream.closeFrame();

                    } catch (Exception ex) {
                        boolean stop = !progressListener.converterException(ex);

                        if (stop) {
                            throw new JavaLayerException(ex.getLocalizedMessage(), ex);
                        }
                    }
                }

            } finally {

                if (output != null)
                    output.close();
            }

            int time = (int) (System.currentTimeMillis() - startTime);
            progressListener.converterUpdate(ProgressListener.UPDATE_CONVERT_COMPLETE, time, frame);
        } catch (IOException ex) {
            throw new JavaLayerException(ex.getLocalizedMessage(), ex);
        }
    }

    /**
     * Counts the number of frames in the input stream.
     * <p>
     * This method can be overridden to provide accurate frame
     * counting for progress reporting. The default implementation
     * returns -1, indicating the frame count is unknown.
     *
     * @param in The input stream to count frames in.
     * @return The number of frames, or -1 if unknown.
     */
    protected int countFrames(InputStream in) {
        return -1;
    }

    /**
     * Opens an input stream for the specified file.
     * <p>
     * This method ensures the file name is converted to an
     * abstract path name and wraps the stream in a buffered
     * stream for efficient reading.
     *
     * @param fileName The path to the file to open.
     * @return An input stream for reading the file.
     * @throws IOException if the file cannot be opened.
     */
    protected InputStream openInput(String fileName) throws IOException {
        // ensure name is abstract path name
        File file = new File(fileName);
        InputStream fileIn = Files.newInputStream(file.toPath());

        return new BufferedInputStream(fileIn);
    }

    /**
     * This interface is used by the Converter to provide
     * notification of tasks being carried out by the converter,
     * and to provide new information as it becomes available.
     * <p>
     * Implement this interface to receive progress updates
     * during file conversion, including frame counts, decoding
     * progress, and error handling.
     * <p>
     * Example implementation:
     * <pre>
     * ProgressListener listener = new ProgressListener() {
     *     public void converterUpdate(int updateID, int param1, int param2) {
     *         System.out.println("Progress: " + param1);
     *     }
     *     // Implement other methods...
     * };
     * </pre>
     */
    public interface ProgressListener {
        /**
         * Update code indicating the frame count is available.
         * Parameter 1 contains the frame count, or -1 if unknown.
         */
        int UPDATE_FRAME_COUNT = 1;

        /**
         * Update code indicating conversion is complete.
         * Parameter 1 contains the time to convert in milliseconds.
         * Parameter 2 contains the number of MPEG audio frames converted.
         */
        int UPDATE_CONVERT_COMPLETE = 2;

        /**
         * Notifies the listener that new information is available.
         * <p>
         * This method is called when update information becomes
         * available during conversion.
         *
         * @param updateID Code indicating the information that has been
         *                 updated. Can be {@link #UPDATE_FRAME_COUNT}
         *                 or {@link #UPDATE_CONVERT_COMPLETE}.
         * @param param1   Parameter whose value depends upon the update code.
         *                 For UPDATE_FRAME_COUNT, this is the frame count.
         *                 For UPDATE_CONVERT_COMPLETE, this is the conversion time.
         * @param param2   Parameter whose value depends upon the update code.
         *                 For UPDATE_CONVERT_COMPLETE, this is the number of
         *                 frames converted.
         */
        void converterUpdate(int updateID, int param1, int param2);

        /**
         * If the converter wishes to make a first pass over the
         * audio frames, this is called as each frame is parsed.
         * <p>
         * This method may be called during a pre-pass to count
         * frames before actual conversion begins.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the frame just parsed.
         */
        void parsedFrame(int frameNo, Header header);

        /**
         * This method is called after each frame has been read,
         * but before it has been decoded.
         * <p>
         * This provides an opportunity to inspect frames before
         * the computationally expensive decoding step.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the frame just read.
         */
        void readFrame(int frameNo, Header header);

        /**
         * This method is called after a frame has been decoded.
         * <p>
         * At this point, the decoded audio samples are available
         * in the provided Obuffer.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the frame just read.
         * @param o       The Obuffer containing the decoded data.
         */
        void decodedFrame(int frameNo, Header header, Obuffer o);

        /**
         * Called when an exception is thrown during while converting
         * a frame.
         * <p>
         * This method allows the listener to decide whether to
         * continue processing or abort the conversion.
         *
         * @param t The Throwable instance that was thrown.
         * @return true to continue processing, or false to abort
         * conversion.
         * <p>
         * If this method returns false, the exception
         * is propagated to the caller of the convert() method. If
         * true is returned, the exception is silently
         * ignored and the converter moves onto the next frame.
         */
        boolean converterException(Throwable t);
    }

    /**
     * Implementation of {@code ProgressListener} that writes
     * notification text to a {@code PrintWriter}.
     * <p>
     * This class provides different levels of detail for output:
     * <ul>
     *   <li>{@link #NO_DETAIL} - No output</li>
     *   <li>{@link #EXPERT_DETAIL} - Expert level output</li>
     *   <li>{@link #VERBOSE_DETAIL} - Verbose progress indicators</li>
     *   <li>{@link #DEBUG_DETAIL} - Debug output for all frames</li>
     *   <li>{@link #MAX_DETAIL} - Maximum detail output</li>
     * </ul>
     * <p>
     * Example usage:
     * <pre>
     * ProgressListener listener = PrintWriterProgressListener.newStdOut(VERBOSE_DETAIL);
     * converter.convert("input.mp3", "output.wav", listener);
     * </pre>
     * <p>
     * Note: Internationalization (i18n) of text and order required.
     */
    static public class PrintWriterProgressListener implements ProgressListener {
        /**
         * Level of detail with no output.
         */
        static public final int NO_DETAIL = 0;

        /**
         * Level of detail typically expected of expert
         * users.
         */
        static public final int EXPERT_DETAIL = 1;

        /**
         * Verbose detail level showing progress indicators.
         */
        static public final int VERBOSE_DETAIL = 2;

        /**
         * Debug detail level showing all frame read notifications.
         */
        static public final int DEBUG_DETAIL = 7;

        /**
         * Maximum detail level showing all available information.
         */
        static public final int MAX_DETAIL = 10;

        private final PrintWriter pw;

        private final int detailLevel;

        /**
         * Creates a new PrintWriterProgressListener that writes to
         * standard output.
         *
         * @param detail The level of detail to output.
         * @return A new PrintWriterProgressListener instance.
         */
        static public PrintWriterProgressListener newStdOut(int detail) {
            return new PrintWriterProgressListener(new PrintWriter(System.out, true), detail);
        }

        /**
         * Creates a new PrintWriterProgressListener.
         *
         * @param writer     The PrintWriter to write notifications to.
         * @param detailLevel The level of detail to output.
         */
        public PrintWriterProgressListener(PrintWriter writer, int detailLevel) {
            this.pw = writer;
            this.detailLevel = detailLevel;
        }

        /**
         * Checks if the specified detail level should be output.
         *
         * @param detail The detail level to check.
         * @return true if the detail level is at or below the current
         *         detail level setting.
         */
        public boolean isDetail(int detail) {
            return (this.detailLevel >= detail);
        }

        /**
         * Notifies the listener of converter updates.
         * <p>
         * At VERBOSE_DETAIL level, this outputs conversion
         * statistics when complete.
         *
         * @param updateID The type of update.
         * @param param1   First parameter (time or frame count).
         * @param param2   Second parameter (frame count).
         */
        @Override
        public void converterUpdate(int updateID, int param1, int param2) {
            if (isDetail(VERBOSE_DETAIL)) {
                if (updateID == UPDATE_CONVERT_COMPLETE) {// catch divide by zero errors.
                    if (param2 == 0)
                        param2 = 1;

                    pw.println();
                    pw.println("Converted " + param2 + " frames in " + param1 + " ms (" + (param1 / param2)
                            + " ms per frame.)");
                }
            }
        }

        /**
         * Notifies the listener that a frame has been parsed.
         * <p>
         * At VERBOSE_DETAIL level, outputs file header info for
         * the first frame. At MAX_DETAIL, outputs all parsed frames.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the parsed frame.
         */
        @Override
        public void parsedFrame(int frameNo, Header header) {
            if ((frameNo == 0) && isDetail(VERBOSE_DETAIL)) {
                String headerString = header.toString();
                pw.println("File is a " + headerString);
            } else if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Prased frame " + frameNo + ": " + headerString);
            }
        }

        /**
         * Notifies the listener that a frame has been read.
         * <p>
         * At VERBOSE_DETAIL level, outputs file header info for
         * the first frame. At MAX_DETAIL, outputs all read frames.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the read frame.
         */
        @Override
        public void readFrame(int frameNo, Header header) {
            if ((frameNo == 0) && isDetail(VERBOSE_DETAIL)) {
                String headerString = header.toString();
                pw.println("File is a " + headerString);
            } else if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Read frame " + frameNo + ": " + headerString);
            }
        }

        /**
         * Notifies the listener that a frame has been decoded.
         * <p>
         * At VERBOSE_DETAIL level, outputs progress dots.
         * At MAX_DETAIL, outputs full frame information.
         *
         * @param frameNo The 0-based sequence number of the frame.
         * @param header  The Header representing the decoded frame.
         * @param o       The Obuffer containing decoded data.
         */
        @Override
        public void decodedFrame(int frameNo, Header header, Obuffer o) {
            if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Decoded frame " + frameNo + ": " + headerString);
                pw.println("Output: " + o);
            } else if (isDetail(VERBOSE_DETAIL)) {
                if (frameNo == 0) {
                    pw.print("Converting.");
                    pw.flush();
                }

                if ((frameNo % 10) == 0) {
                    pw.print('.');
                    pw.flush();
                }
            }
        }

        /**
         * Handles exceptions during conversion.
         * <p>
         * At detail levels greater than NO_DETAIL, prints the
         * stack trace and returns false to abort conversion.
         *
         * @param t The Throwable that was thrown.
         * @return false to abort conversion, true to continue.
         */
        @Override
        public boolean converterException(Throwable t) {
            if (this.detailLevel > NO_DETAIL) {
                t.printStackTrace(pw);
                pw.flush();
            }
            return false;
        }
    }
}
