package com.iqbal.fixgateway;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Finds complete FIX messages in a byte stream.
 *
 * TCP is a stream, so one read can contain half a message or several messages.
 * FIX tells us the length: 8=...|9=<length>|<body of that length>10=xxx|
 * so a message is complete when the body plus the 7-byte trailer have arrived.
 */
public final class FixFramer {

    private static final byte SOH = 0x01;
    private static final int TRAILER_LENGTH = 7;   // "10=" + 3 digits + SOH

    private FixFramer() { }

    /**
     * Looks at the buffer (in read mode: position..limit).
     * Returns one complete message and advances the position past it,
     * or returns null (position unchanged) when more bytes are needed.
     *
     * @throws IllegalArgumentException if the bytes cannot be a FIX message
     */
    public static String nextFrame(ByteBuffer buf) {
        int start = buf.position();
        int end = buf.limit();
        if (end - start < 2) {
            return null;
        }
        if (buf.get(start) != '8' || buf.get(start + 1) != '=') {
            throw new IllegalArgumentException("Message must start with 8=");
        }
        int i = start;
        while (i < end && buf.get(i) != SOH) {
            i++;
        }
        if (i >= end) {
            return null;                       // BeginString not finished yet
        }
        int j = i + 1;
        if (j + 2 > end) {
            return null;
        }
        if (buf.get(j) != '9' || buf.get(j + 1) != '=') {
            throw new IllegalArgumentException("Second field must be 9= (BodyLength)");
        }
        int k = j + 2;
        int bodyLength = 0;
        while (k < end && buf.get(k) != SOH) {
            byte c = buf.get(k);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("BodyLength is not a number");
            }
            bodyLength = bodyLength * 10 + (c - '0');
            if (bodyLength > 1_000_000) {
                throw new IllegalArgumentException("BodyLength too large");
            }
            k++;
        }
        if (k >= end) {
            return null;                       // BodyLength not finished yet
        }
        int bodyStart = k + 1;
        int total = (bodyStart - start) + bodyLength + TRAILER_LENGTH;
        if (end - start < total) {
            return null;                       // rest of the message has not arrived
        }
        byte[] frame = new byte[total];
        buf.get(frame);                        // consumes exactly one message
        return new String(frame, StandardCharsets.ISO_8859_1);
    }
}
