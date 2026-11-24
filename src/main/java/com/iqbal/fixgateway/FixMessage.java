package com.iqbal.fixgateway;

import java.util.LinkedHashMap;
import java.util.Map;

/** A FIX message as an ordered map of tag to value. */
public final class FixMessage {

    private final Map<Integer, String> fields = new LinkedHashMap<>();

    public FixMessage set(int tag, String value) {
        fields.put(tag, value);
        return this;
    }

    public FixMessage set(int tag, long value) {
        return set(tag, Long.toString(value));
    }

    public String get(int tag) {
        return fields.get(tag);
    }

    public boolean has(int tag) {
        return fields.containsKey(tag);
    }

    public String msgType() {
        return fields.get(FixConstants.MSG_TYPE);
    }

    Map<Integer, String> fields() {
        return fields;
    }

    /** Human-readable form with | instead of the SOH character. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        fields.forEach((tag, value) -> sb.append(tag).append('=').append(value).append('|'));
        return sb.toString();
    }
}
