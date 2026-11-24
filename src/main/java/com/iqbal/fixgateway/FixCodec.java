package com.iqbal.fixgateway;

import static com.iqbal.fixgateway.FixConstants.BEGIN_STRING;
import static com.iqbal.fixgateway.FixConstants.BEGIN_STRING_VALUE;
import static com.iqbal.fixgateway.FixConstants.BODY_LENGTH;
import static com.iqbal.fixgateway.FixConstants.CHECKSUM;
import static com.iqbal.fixgateway.FixConstants.MSG_TYPE;
import static com.iqbal.fixgateway.FixConstants.SOH;

/**
 * Encodes and decodes FIX tag=value messages.
 *
 * Wire format: 8=FIX.4.2|9=<body length>|35=...|...|10=<checksum>|
 * BodyLength counts the characters after the 9= field up to and including the SOH
 * that precedes 10=. CheckSum is the sum of all bytes before 10= modulo 256, as 3 digits.
 */
public final class FixCodec {

    private FixCodec() { }

    /** Turns a message into its wire form, adding BeginString, BodyLength and CheckSum. */
    public static String encode(FixMessage message) {
        StringBuilder body = new StringBuilder(128);
        // MsgType must be the first field of the body.
        if (message.has(MSG_TYPE)) {
            appendField(body, MSG_TYPE, message.get(MSG_TYPE));
        }
        message.fields().forEach((tag, value) -> {
            if (tag != BEGIN_STRING && tag != BODY_LENGTH && tag != CHECKSUM && tag != MSG_TYPE) {
                appendField(body, tag, value);
            }
        });

        StringBuilder out = new StringBuilder(body.length() + 32);
        appendField(out, BEGIN_STRING, BEGIN_STRING_VALUE);
        appendField(out, BODY_LENGTH, Integer.toString(body.length()));
        out.append(body);
        String cs = checksum(out.toString());   // computed before "10=" is appended
        out.append("10=").append(cs).append(SOH);
        return out.toString();
    }

    /** Parses a raw FIX string. Does not verify the checksum; call {@link #hasValidChecksum}. */
    public static FixMessage decode(String raw) {
        FixMessage message = new FixMessage();
        int start = 0;
        while (start < raw.length()) {
            int end = raw.indexOf(SOH, start);
            if (end < 0) {
                end = raw.length();
            }
            if (end > start) {
                int eq = raw.indexOf('=', start);
                if (eq < 0 || eq >= end || eq == start) {
                    throw new IllegalArgumentException("Malformed FIX field: " + raw.substring(start, end));
                }
                int tag;
                try {
                    tag = Integer.parseInt(raw.substring(start, eq));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Non-numeric FIX tag: " + raw.substring(start, eq));
                }
                message.set(tag, raw.substring(eq + 1, end));
            }
            start = end + 1;
        }
        return message;
    }

    /** Checks tag 10 against the bytes that precede it. */
    public static boolean hasValidChecksum(String raw) {
        String marker = SOH + "10=";
        int idx = raw.lastIndexOf(marker);
        if (idx < 0) {
            return false;
        }
        String expected = checksum(raw.substring(0, idx + 1));
        int valueStart = idx + marker.length();
        if (raw.length() < valueStart + 3) {
            return false;
        }
        return expected.equals(raw.substring(valueStart, valueStart + 3));
    }

    /** Sum of the character codes modulo 256, left-padded to 3 digits. */
    public static String checksum(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum += s.charAt(i);
        }
        return String.format("%03d", sum % 256);
    }

    private static void appendField(StringBuilder sb, int tag, String value) {
        sb.append(tag).append('=').append(value).append(SOH);
    }
}
