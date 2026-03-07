/*
 * 11/19/04        1.0 moved to LGPL.
 * 01/12/99        Initial version.    mdm@techie.com
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

/**
 * The <code>Decoder</code> class encapsulates the details of
 * decoding an MPEG audio frame. It manages the decoding process
 * for all three MPEG audio layers (I, II, and III) and provides
 * access to decoder configuration parameters.
 * <p>
 * The decoder is initialized lazily on the first call to
 * {@link #decodeFrame(Header, Bitstream)}. It maintains state
 * for synthesis filters, output buffers, and equalizer settings.
 * <p>
 * This class is not thread-safe. All methods must be called
 * from the same thread.
 *
 * @author MDM
 * @version 0.0.7 12/12/99
 * @since 0.0.5
 */
public class Decoder implements DecoderErrors {
    static private final Params DEFAULT_PARAMS = new Params();

    /**
     * The Obuffer instance that will receive the decoded
     * PCM samples.
     */
    private Obuffer output;

    /**
     * Synthesis filter for the left channel.
     */
    private SynthesisFilter filter1;

    /**
     * Synthesis filter for the right channel.
     */
    private SynthesisFilter filter2;

    /**
     * The decoder used to decode layer III frames.
     */
    private LayerIIIDecoder l3decoder;

    /**
     * The decoder used to decode layer II frames.
     */
    private LayerIIDecoder l2decoder;

    /**
     * The decoder used to decode layer I frames.
     */
    private LayerIDecoder l1decoder;

    /**
     * The sample frequency of the output PCM samples in Hz.
     */
    private int outputFrequency;

    /**
     * The number of output channels (1 for mono, 2 for stereo).
     */
    private int outputChannels;

    /**
     * The equalizer used to apply frequency adjustments during decoding.
     */
    private final Equalizer equalizer = new Equalizer();

    /**
     * The decoder parameters configuration.
     */
    private final Params params;

    /**
     * Flag indicating whether the decoder has been initialized.
     */
    private boolean initialized;

    /**
     * Creates a new <code>Decoder</code> instance with default
     * parameters.
     * <p>
     * The decoder will use default equalizer settings and output
     * configuration. Initialization occurs lazily on the first
     * call to {@link #decodeFrame(Header, Bitstream)}.
     */
    public Decoder() {
        this(null);
    }

    /**
     * Creates a new <code>Decoder</code> instance with default
     * parameters.
     * <p>
     * The decoder will use default equalizer settings and output
     * configuration. Initialization occurs lazily on the first
     * call to {@link #decodeFrame(Header, Bitstream)}.
     *
     * @param params The <code>Params</code> instance that describes
     *               the customizable aspects of the decoder.
     *               If null, default parameters are used.
     */
    public Decoder(Params params) {
        if (params == null)
            params = DEFAULT_PARAMS;

        this.params = params;

        Equalizer eq = this.params.getInitialEqualizerSettings();
        if (eq != null) {
            equalizer.setFrom(eq);
        }
    }

    /**
     * Retrieves a clone of the default decoder parameters.
     * <p>
     * This method can be used to create a new {@link Params}
     * instance with default settings, which can then be
     * customized before creating a decoder.
     *
     * @return A clone of the default parameters.
     */
    static public Params getDefaultParams() {
        return (Params) DEFAULT_PARAMS.clone();
    }

    /**
     * Sets the equalizer configuration for this decoder.
     * <p>
     * The equalizer settings are applied to both synthesis
     * filters. This method can be called at any time to
     * modify the frequency response during playback.
     *
     * @param eq The equalizer configuration. If null, a
     *           pass-through equalizer is used.
     */
    public void setEqualizer(Equalizer eq) {
        if (eq == null)
            eq = Equalizer.PASS_THRU_EQ;

        equalizer.setFrom(eq);

        float[] factors = equalizer.getBandFactors();

        if (filter1 != null)
            filter1.setEQ(factors);

        if (filter2 != null)
            filter2.setEQ(factors);
    }

    /**
     * Decodes one frame from an MPEG audio bitstream.
     * <p>
     * The decoder is initialized on the first call to this method
     * based on the provided header. Subsequent calls use the
     * established configuration.
     * <p>
     * This method is not thread-safe and should only be called
     * from a single thread.
     *
     * @param header The header describing the frame to decode.
     *               Must not be null.
     * @param stream The bit stream that provides the bits for
     *               the body of the frame. Must not be null.
     * @return A SampleBuffer containing the decoded samples.
     * @throws DecoderException if decoding fails due to invalid
     *                          frame data or unsupported layer.
     */
    public Obuffer decodeFrame(Header header, Bitstream stream)
            throws DecoderException {
        if (!initialized) {
            initialize(header);
        }

        int layer = header.layer();

        output.clearBuffer();

        FrameDecoder decoder = retrieveDecoder(header, stream, layer);

        decoder.decodeFrame();

        output.writeBuffer(1);

        return output;
    }

    /**
     * Changes the output buffer. This will take effect the next time
     * {@link #decodeFrame(Header, Bitstream)} is called.
     * <p>
     * If not set, a default {@link SampleBuffer} is created
     * during initialization based on the first frame's header.
     *
     * @param out The output buffer to use for decoded samples.
     *            May be null to use the default buffer.
     */
    public void setOutputBuffer(Obuffer out) {
        output = out;
    }

    /**
     * Retrieves the sample frequency of the PCM samples output
     * by this decoder. This typically corresponds to the sample
     * rate encoded in the MPEG audio stream.
     * <p>
     * The value is determined from the first frame's header
     * during decoder initialization and remains constant
     * throughout the decoder's lifetime.
     *
     * @return the sample rate (in Hz) of the samples written to the
     * output buffer when decoding. Common values are 8000, 11025,
     * 12000, 16000, 22050, 24000, 32000, 44100, and 48000.
     */
    public int getOutputFrequency() {
        return outputFrequency;
    }

    /**
     * Retrieves the number of channels of PCM samples output by
     * this decoder. This usually corresponds to the number of
     * channels in the MPEG audio stream, although it may differ
     * if downmixing is configured.
     * <p>
     * The value is determined from the first frame's header
     * during decoder initialization and remains constant.
     *
     * @return The number of output channels in the decoded samples: 1
     * for mono, or 2 for stereo.
     */
    public int getOutputChannels() {
        return outputChannels;
    }

    /**
     * Retrieves the maximum number of samples that will be written to
     * the output buffer when one frame is decoded. This can be used to
     * help calculate the size of other buffers whose size is based upon
     * the number of samples written to the output buffer.
     * <p>
     * Note: this is an upper bound and fewer samples may actually
     * be written, depending upon the sample rate and number of channels.
     *
     * @return The maximum number of samples that are written to the
     * output buffer when decoding a single frame of MPEG audio.
     * The value is constant and depends on the buffer implementation.
     */
    public int getOutputBlockSize() {
        return Obuffer.OBUFFERSIZE;
    }

    protected DecoderException newDecoderException(int errorCode) {
        return new DecoderException(errorCode, null);
    }

    protected DecoderException newDecoderException(int errorCode, Throwable throwable) {
        return new DecoderException(errorCode, throwable);
    }

    /**
     * Retrieves the appropriate frame decoder for the specified
     * MPEG layer.
     * <p>
     * Decoders are created lazily and cached for reuse. This method
     * returns the same decoder instance for the same layer across
     * multiple calls.
     *
     * @param header The frame header containing layer information.
     * @param stream The bitstream providing frame data.
     * @param layer  The MPEG layer number (1, 2, or 3).
     * @return The frame decoder for the specified layer.
     * @throws DecoderException if the layer is not supported.
     */
    protected FrameDecoder retrieveDecoder(Header header, Bitstream stream, int layer) throws DecoderException {
        FrameDecoder decoder = switch (layer) {
            case 3 -> {
                if (l3decoder == null) {
                    l3decoder = new LayerIIIDecoder(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }

                yield l3decoder;
            }
            case 2 -> {
                if (l2decoder == null) {
                    l2decoder = new LayerIIDecoder();
                    l2decoder.create(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }
                yield l2decoder;
            }
            case 1 -> {
                if (l1decoder == null) {
                    l1decoder = new LayerIDecoder();
                    l1decoder.create(stream,
                            header, filter1, filter2,
                            output, OutputChannels.BOTH_CHANNELS);
                }
                yield l1decoder;
            }
            default -> null;

            // REVIEW: allow channel output selection type
            // (LEFT, RIGHT, BOTH, DOWNMIX)
        };

        if (decoder == null) {
            throw newDecoderException(UNSUPPORTED_LAYER, null);
        }

        return decoder;
    }

    /**
     * Initializes the decoder based on the provided frame header.
     * <p>
     * This method creates the synthesis filters, output buffer,
     * and configures the equalizer settings. It is called lazily
     * on the first decode operation.
     *
     * @param header The frame header containing sample rate,
     *               channel mode, and other configuration data.
     */
    private void initialize(Header header) {

        // REVIEW: allow customizable scale factor
        float scalefactor = 32700.0f;

        int mode = header.mode();
        @SuppressWarnings("unused")
        int layer = header.layer();
        int channels = mode == Header.SINGLE_CHANNEL ? 1 : 2;


        // set up output buffer if not set up by client.
        if (output == null)
            output = new SampleBuffer(header.frequency(), channels);

        float[] factors = equalizer.getBandFactors();
        filter1 = new SynthesisFilter(0, scalefactor, factors);

        // REVIEW: allow mono output for stereo
        if (channels == 2)
            filter2 = new SynthesisFilter(1, scalefactor, factors);

        outputChannels = channels;
        outputFrequency = header.frequency();

        initialized = true;
    }

    /**
     * The <code>Params</code> class presents the customizable
     * aspects of the decoder.
     * <p>
     * This class holds configuration options such as output
     * channel mode and initial equalizer settings. Instances
     * are not thread-safe and should be used by a single thread.
     * <p>
     * Example usage:
     * <pre>
     * Decoder.Params params = Decoder.getDefaultParams();
     * params.setOutputChannels(OutputChannels.LEFT);
     * Decoder decoder = new Decoder(params);
     * </pre>
     */
    public static class Params implements Cloneable {

        /**
         * The output channel mode configuration.
         */
        private OutputChannels outputChannels = OutputChannels.BOTH;

        /**
         * The initial equalizer settings for the decoder.
         */
        private final Equalizer equalizer = new Equalizer();

        /**
         * Creates a new Params instance with default settings.
         */
        public Params() {
        }

        /**
         * Creates and returns a shallow copy of this Params instance.
         *
         * @return A clone of this instance.
         * @throws InternalError if cloning fails (should never occur).
         */
        @Override
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException ex) {
                throw new InternalError(this + ": " + ex);
            }
        }

        /**
         * Sets the output channel mode.
         *
         * @param out The output channel mode. Must not be null.
         * @throws NullPointerException if out is null.
         */
        public void setOutputChannels(OutputChannels out) {
            if (out == null)
                throw new NullPointerException("out");

            outputChannels = out;
        }

        /**
         * Retrieves the output channel mode.
         *
         * @return The output channel mode.
         */
        public OutputChannels getOutputChannels() {
            return outputChannels;
        }

        /**
         * Retrieves the equalizer settings that the decoder's equalizer
         * will be initialized from.
         * <p>
         * The <code>Equalizer</code> instance returned
         * cannot be changed in real time to affect the
         * decoder output as it is used only to initialize the decoder's
         * EQ settings. To affect the decoder's output in real-time,
         * call {@link #setEqualizer(Equalizer)} on the decoder.
         * <p>
         * This method returns the same instance that was passed to
         * the {@link Decoder} constructor or the default equalizer
         * if no parameters were provided.
         *
         * @return The <code>Equalizer</code> used to initialize the
         * EQ settings of the decoder.
         */
        public Equalizer getInitialEqualizerSettings() {
            return equalizer;
        }
    }
}

