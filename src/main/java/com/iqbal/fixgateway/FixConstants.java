package com.iqbal.fixgateway;

/** FIX 4.2 tags and values used by this gateway. */
public final class FixConstants {

    private FixConstants() { }

    /** Field delimiter in a real FIX message (ASCII 0x01, shown as | in logs). */
    public static final char SOH = '\u0001';
    public static final String BEGIN_STRING_VALUE = "FIX.4.2";

    // Tags
    public static final int AVG_PX = 6;
    public static final int BEGIN_STRING = 8;
    public static final int BODY_LENGTH = 9;
    public static final int CHECKSUM = 10;
    public static final int CUM_QTY = 14;
    public static final int EXEC_ID = 17;
    public static final int EXEC_TRANS_TYPE = 20;
    public static final int LAST_PX = 31;
    public static final int LAST_SHARES = 32;
    public static final int MSG_SEQ_NUM = 34;
    public static final int MSG_TYPE = 35;
    public static final int ORDER_ID = 37;
    public static final int ORDER_QTY = 38;
    public static final int ORD_STATUS = 39;
    public static final int ORD_TYPE = 40;
    public static final int PRICE = 44;
    public static final int SENDER_COMP_ID = 49;
    public static final int SENDING_TIME = 52;
    public static final int SIDE = 54;
    public static final int SYMBOL = 55;
    public static final int TARGET_COMP_ID = 56;
    public static final int TEXT = 58;
    public static final int CL_ORD_ID = 11;
    public static final int ORD_REJ_REASON = 103;
    public static final int EXEC_TYPE = 150;
    public static final int LEAVES_QTY = 151;

    // Message types (tag 35)
    public static final String MSG_NEW_ORDER_SINGLE = "D";
    public static final String MSG_EXECUTION_REPORT = "8";
    public static final String MSG_REJECT = "3";

    // Side (tag 54)
    public static final String SIDE_BUY = "1";
    public static final String SIDE_SELL = "2";

    // OrdType (tag 40)
    public static final String ORD_TYPE_MARKET = "1";
    public static final String ORD_TYPE_LIMIT = "2";

    // OrdStatus (tag 39) / ExecType (tag 150)
    public static final String STATUS_FILLED = "2";
    public static final String STATUS_REJECTED = "8";
}
