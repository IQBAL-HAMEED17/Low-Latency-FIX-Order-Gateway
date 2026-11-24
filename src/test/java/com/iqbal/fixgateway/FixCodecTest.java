package com.iqbal.fixgateway;

import static com.iqbal.fixgateway.FixConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixCodecTest {

    private FixMessage sampleOrder() {
        return new FixMessage()
                .set(MSG_TYPE, MSG_NEW_ORDER_SINGLE)
                .set(SENDER_COMP_ID, "CLIENT")
                .set(CL_ORD_ID, "CL1")
                .set(SYMBOL, "INFY")
                .set(SIDE, SIDE_BUY)
                .set(ORDER_QTY, "100")
                .set(PRICE, "1500.50");
    }

    @Test
    void encodedMessageHasHeaderAndValidChecksum() {
        String raw = FixCodec.encode(sampleOrder());
        assertTrue(raw.startsWith("8=FIX.4.2" + SOH + "9="));
        assertTrue(FixCodec.hasValidChecksum(raw));
    }

    @Test
    void bodyLengthMatchesTheBody() {
        String raw = FixCodec.encode(sampleOrder());
        int afterNine = raw.indexOf(SOH, raw.indexOf("9=")) + 1;
        int trailerStart = raw.lastIndexOf(SOH + "10=") + 1;
        int declared = Integer.parseInt(raw.substring(raw.indexOf("9=") + 2, afterNine - 1));
        assertEquals(trailerStart - afterNine, declared);
    }

    @Test
    void decodeReadsBackWhatWasEncoded() {
        FixMessage decoded = FixCodec.decode(FixCodec.encode(sampleOrder()));
        assertEquals("D", decoded.msgType());
        assertEquals("INFY", decoded.get(SYMBOL));
        assertEquals("100", decoded.get(ORDER_QTY));
        assertEquals("1500.50", decoded.get(PRICE));
    }

    @Test
    void tamperedMessageFailsChecksum() {
        String raw = FixCodec.encode(sampleOrder()).replace("INFY", "TCS1");
        assertFalse(FixCodec.hasValidChecksum(raw));
    }

    @Test
    void malformedFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FixCodec.decode("abc" + SOH));
        assertThrows(IllegalArgumentException.class, () -> FixCodec.decode("x=1" + SOH));
    }
}
