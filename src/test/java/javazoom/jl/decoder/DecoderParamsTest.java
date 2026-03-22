package javazoom.jl.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecoderParamsTest {
    @Test
    public void testParamsInitialization() {
        Decoder.Params params = Decoder.getDefaultParams();
        assertEquals(OutputChannels.BOTH, params.getOutputChannels());
    }

}
