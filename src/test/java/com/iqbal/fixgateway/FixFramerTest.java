package com.iqbal.fixgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FixFramerTest {

    private static String order(String id) {
        return FixCodec.encode(LatencyClient.newOrder(1, "INFY", "1", "100", "1500.50")
                .set(FixConstants.CL_ORD_ID, id));
    }

    private static ByteBuffer readable(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void completeMessageIsReturned() {
        String msg = order("A");
        ByteBuffer buf = readable(msg);
        assertEquals(msg, FixFramer.nextFrame(buf));
        assertEquals(0, buf.remaining());
    }

    @Test
    void partialMessageReturnsNullAndKeepsPosition() {
        String msg = order("A");
        ByteBuffer buf = readable(msg.substring(0, msg.length() - 3));
        assertNull(FixFramer.nextFrame(buf));
        assertEquals(0, buf.position());
    }

    @Test
    void twoMessagesInOneReadAreSplit() {
        String a = order("A");
        String b = order("B");
        ByteBuffer buf = readable(a + b);
        assertEquals(a, FixFramer.nextFrame(buf));
        assertEquals(b, FixFramer.nextFrame(buf));
        assertNull(FixFramer.nextFrame(buf));
    }

    @Test
    void messageSplitAtEveryPositionIsStillFound() {
        String msg = order("A");
        for (int cut = 1; cut < msg.length(); cut++) {
            ByteBuffer buf = ByteBuffer.allocate(msg.length() * 2);
            buf.put(msg.substring(0, cut).getBytes(StandardCharsets.ISO_8859_1));
            buf.flip();
            assertNull(FixFramer.nextFrame(buf), "cut at " + cut);
            buf.compact();
            buf.put(msg.substring(cut).getBytes(StandardCharsets.ISO_8859_1));
            buf.flip();
            assertNotNull(FixFramer.nextFrame(buf), "cut at " + cut);
        }
    }

    @Test
    void garbageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FixFramer.nextFrame(readable("hello world")));
    }
}
