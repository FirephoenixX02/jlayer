/*
 * 11/19/04        1.0 moved to LGPL.
 * 29/01/00        Initial version. mdm@techie.com
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

package javazoom.jl.player;

import java.io.InputStream;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;


/**
 * The <code>Player</code> class implements a simple player for playback
 * of an MPEG audio stream. It handles opening the audio device,
 * decoding frames, and writing audio samples to the output device.
 * <p>
 * The player opens the audio device immediately upon construction.
 * Frames are decoded and played sequentially when {@link #play()}
 * or {@link #play(int)} is called.
 * <p>
 * This class is not thread-safe. All methods should be called
 * from the same thread.
 * <p>
 * Example usage:
 * <pre>
 * try (Player player = new Player(inputStream)) {
 *     player.play();
 * } catch (JavaLayerException e) {
 *     e.printStackTrace();
 * }
 * </pre>
 *
 * @author Mat McGowan
 * @since 0.0.8
 */
public class Player {

    /**
     * The current frame number (reserved for future use).
     */
    @SuppressWarnings("unused")
    private final int frame = 0;

    /**
     * The MPEG audio bitstream from which frames are read.
     */
    private final Bitstream bitstream;

    /**
     * The MPEG audio decoder used to decode frames.
     */
    private final Decoder decoder;

    /**
     * The AudioDevice to which audio samples are written.
     */
    private AudioDevice audio;

    /**
     * Flag indicating whether the player has been closed.
     */
    private boolean closed = false;

    /**
     * Flag indicating whether all frames have been played back.
     */
    private boolean complete = false;

    /**
     * The last known position in milliseconds.
     */
    private int lastPosition = 0;

    /**
     * Creates a new <code>Player</code> instance.
     * <p>
     * The audio device is opened immediately using the system
     * default audio device factory. The player is ready to
     * play when constructed.
     *
     * @param stream The input stream containing MPEG audio data.
     * @throws JavaLayerException if the player cannot be created
     *                            due to stream or device errors.
     */
    public Player(InputStream stream) throws JavaLayerException {
        this(stream, null);
    }

    /**
     * Creates a new <code>Player</code> instance with a specific
     * audio device.
     * <p>
     * The specified audio device is opened immediately. If null,
     * the system default audio device is used.
     *
     * @param stream  The input stream containing MPEG audio data.
     * @param device  The audio device to use for playback. If null,
     *                the system default device is used.
     * @throws JavaLayerException if the player cannot be created
     *                            due to stream or device errors.
     */
    public Player(InputStream stream, AudioDevice device) throws JavaLayerException {
        bitstream = new Bitstream(stream);
        decoder = new Decoder();

        if (device != null) {
            audio = device;
        } else {
            FactoryRegistry r = FactoryRegistry.systemRegistry();
            audio = r.createAudioDevice();
        }
        audio.open(decoder);
    }

    /**
     * Plays all frames from the input stream.
     * <p>
     * This method blocks until all frames have been played
     * or an error occurs. The player is automatically closed
     * when playback completes.
     *
     * @throws JavaLayerException if an error occurs during playback.
     */
    public void play() throws JavaLayerException {
        play(Integer.MAX_VALUE);
    }

    /**
     * Plays a specified number of MPEG audio frames.
     * <p>
     * This method blocks until the specified number of frames
     * have been played, the end of stream is reached, or an
     * error occurs. If the end of stream is reached before
     * playing all requested frames, the player is automatically
     * closed.
     *
     * @param frames The number of frames to play. Use
     *               {@code Integer.MAX_VALUE} to play all frames.
     * @throws JavaLayerException if an error occurs during playback.
     */
    public void play(int frames) throws JavaLayerException {
        boolean ret = true;

        while (frames-- > 0 && ret) {
            ret = decodeFrame();
        }

        if (!ret) {
            // last frame, ensure all data flushed to the audio device.
            AudioDevice out = audio;
            if (out != null) {
                out.flush();
                synchronized (this) {
                    complete = (!closed);
                    close();
                }
            }
        }
    }

    /**
     * Closes this player. Any audio currently playing is stopped
     * immediately.
     * <p>
     * This method closes the audio device and the bitstream.
     * After calling this method, the player cannot be reused.
     * The player is automatically closed when playback completes.
     */
    public synchronized void close() {
        AudioDevice out = audio;
        if (out != null) {
            closed = true;
            audio = null;
            // this may fail, so ensure object state is set up before
            // calling this method.
            out.close();
            lastPosition = out.getPosition();
            try {
                bitstream.close();
            } catch (BitstreamException ex) {
                // Silently ignore - it's not critical
            }
        }
    }

    /**
     * Returns the completed status of this player.
     * <p>
     * This method returns true when all available MPEG audio
     * frames have been decoded and played. The player is
     * automatically closed when complete.
     *
     * @return true if all available MPEG audio frames have been
     * decoded, or false otherwise.
     */
    public synchronized boolean isComplete() {
        return complete;
    }

    /**
     * Retrieves the position in milliseconds of the current audio
     * sample being played. This method delegates to the
     * {@code AudioDevice} that is used by this player to sound
     * the decoded audio samples.
     * <p>
     * After the player is closed, this method returns the last
     * known position before closing.
     *
     * @return The current position in milliseconds, or the last
     * known position if the player has been closed.
     */
    public int getPosition() {
        AudioDevice out = audio;
        if (out != null) {
            return out.getPosition();
        }
        return lastPosition;
    }

    /**
     * Decodes a single frame from the bitstream and writes it
     * to the audio device.
     * <p>
     * This method is called by {@link #play()} to decode and
     * play frames sequentially.
     *
     * @return true if there are no more frames to decode, false
     * otherwise.
     * @throws JavaLayerException if an error occurs during decoding.
     */
    protected boolean decodeFrame() throws JavaLayerException {
        try {
            AudioDevice out = audio;
            if (out == null)
                return false;

            Header h = bitstream.readFrame();

            if (h == null)
                return false;

            // sample buffer set when decoder constructed
            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);

            synchronized (this) {
                out = audio;
                if (out != null) {
                    out.write(output.getBuffer(), 0, output.getBufferLength());
                }
            }

            bitstream.closeFrame();
        } catch (RuntimeException ex) {
            throw new JavaLayerException("Exception decoding audio frame", ex);
        }

        return true;
    }
}
