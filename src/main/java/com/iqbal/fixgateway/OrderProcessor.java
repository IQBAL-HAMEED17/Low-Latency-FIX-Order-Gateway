package com.iqbal.fixgateway;

import static com.iqbal.fixgateway.FixConstants.*;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock exchange logic. Accepts NewOrderSingle (35=D) and answers with an
 * ExecutionReport (35=8): fully filled if valid, rejected if not.
 */
public final class OrderProcessor {

    private static final DateTimeFormatter FIX_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final String gatewayCompId;
    private final AtomicLong orderIds = new AtomicLong();
    private final AtomicLong execIds = new AtomicLong();
    private final AtomicLong outSeq = new AtomicLong();

    public OrderProcessor(String gatewayCompId) {
        this.gatewayCompId = gatewayCompId;
    }

    public FixMessage process(FixMessage request) {
        if (!MSG_NEW_ORDER_SINGLE.equals(request.msgType())) {
            return new FixMessage()
                    .set(MSG_TYPE, MSG_REJECT)
                    .set(SENDER_COMP_ID, gatewayCompId)
                    .set(MSG_SEQ_NUM, outSeq.incrementAndGet())
                    .set(SENDING_TIME, FIX_TIME.format(Instant.now()))
                    .set(TEXT, "Unsupported MsgType " + request.msgType());
        }
        String error = validate(request);
        return error == null ? filled(request) : rejected(request, error);
    }

    private String validate(FixMessage r) {
        if (!r.has(CL_ORD_ID)) {
            return "Missing ClOrdID(11)";
        }
        if (!r.has(SYMBOL) || r.get(SYMBOL).isBlank()) {
            return "Missing Symbol(55)";
        }
        String side = r.get(SIDE);
        if (!SIDE_BUY.equals(side) && !SIDE_SELL.equals(side)) {
            return "Invalid Side(54), expected 1 or 2";
        }
        long qty;
        try {
            qty = Long.parseLong(String.valueOf(r.get(ORDER_QTY)));
        } catch (NumberFormatException e) {
            return "Invalid OrderQty(38)";
        }
        if (qty <= 0) {
            return "OrderQty(38) must be greater than 0";
        }
        if (ORD_TYPE_LIMIT.equals(r.get(ORD_TYPE))) {
            try {
                if (new BigDecimal(String.valueOf(r.get(PRICE))).signum() <= 0) {
                    return "Price(44) must be greater than 0 for a limit order";
                }
            } catch (NumberFormatException e) {
                return "Invalid Price(44)";
            }
        }
        return null;
    }

    private FixMessage filled(FixMessage r) {
        String qty = r.get(ORDER_QTY);
        String px = r.has(PRICE) ? r.get(PRICE) : "0";
        return base(r)
                .set(ORD_STATUS, STATUS_FILLED)
                .set(EXEC_TYPE, STATUS_FILLED)
                .set(LAST_SHARES, qty)
                .set(LAST_PX, px)
                .set(CUM_QTY, qty)
                .set(AVG_PX, px)
                .set(LEAVES_QTY, "0");
    }

    private FixMessage rejected(FixMessage r, String reason) {
        return base(r)
                .set(ORD_STATUS, STATUS_REJECTED)
                .set(EXEC_TYPE, STATUS_REJECTED)
                .set(ORD_REJ_REASON, "0")
                .set(CUM_QTY, "0")
                .set(AVG_PX, "0")
                .set(LEAVES_QTY, "0")
                .set(TEXT, reason);
    }

    private FixMessage base(FixMessage r) {
        FixMessage m = new FixMessage()
                .set(MSG_TYPE, MSG_EXECUTION_REPORT)
                .set(SENDER_COMP_ID, gatewayCompId)
                .set(MSG_SEQ_NUM, outSeq.incrementAndGet())
                .set(SENDING_TIME, FIX_TIME.format(Instant.now()))
                .set(ORDER_ID, "ORD" + orderIds.incrementAndGet())
                .set(EXEC_ID, "EXE" + execIds.incrementAndGet())
                .set(EXEC_TRANS_TYPE, "0");
        if (r.has(SENDER_COMP_ID)) {
            m.set(TARGET_COMP_ID, r.get(SENDER_COMP_ID));
        }
        if (r.has(CL_ORD_ID)) {
            m.set(CL_ORD_ID, r.get(CL_ORD_ID));
        }
        if (r.has(SYMBOL)) {
            m.set(SYMBOL, r.get(SYMBOL));
        }
        if (r.has(SIDE)) {
            m.set(SIDE, r.get(SIDE));
        }
        if (r.has(ORDER_QTY)) {
            m.set(ORDER_QTY, r.get(ORDER_QTY));
        }
        return m;
    }
}
