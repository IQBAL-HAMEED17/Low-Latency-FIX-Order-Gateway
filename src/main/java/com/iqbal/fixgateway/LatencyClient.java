package com.iqbal.fixgateway;

import static com.iqbal.fixgateway.FixConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Mock client. Sends NewOrderSingle messages one at a time and measures the round trip
 * until the ExecutionReport comes back.
 * Usage: java -cp fix-order-gateway.jar com.iqbal.fixgateway.LatencyClient [host] [port] [count]
 */
public final class LatencyClient implements AutoCloseable {

    /** Result of a run. Times are in microseconds. */
    public record Stats(int count, double avgMicros, double p50Micros, double p99Micros,
                        double maxMicros, double ordersPerSecond) { }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final ByteBuffer pending = ByteBuffer.allocate(64 * 1024);

    public LatencyClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /** Sends one message and waits for the reply. */
    public FixMessage sendAndReceive(FixMessage message) throws IOException {
        out.write(FixCodec.encode(message).getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        while (true) {
            pending.flip();
            String frame = FixFramer.nextFrame(pending);
            pending.compact();
            if (frame != null) {
                return FixCodec.decode(frame);
            }
            int n = in.read(pending.array(), pending.position(), pending.remaining());
            if (n < 0) {
                throw new IOException("Server closed the connection");
            }
            pending.position(pending.position() + n);
        }
    }

    public static FixMessage newOrder(long seq, String symbol, String side, String qty, String price) {
        return new FixMessage()
                .set(MSG_TYPE, MSG_NEW_ORDER_SINGLE)
                .set(SENDER_COMP_ID, "CLIENT")
                .set(TARGET_COMP_ID, "EXCHANGE")
                .set(MSG_SEQ_NUM, seq)
                .set(CL_ORD_ID, "CL" + seq)
                .set(SYMBOL, symbol)
                .set(SIDE, side)
                .set(ORDER_QTY, qty)
                .set(ORD_TYPE, ORD_TYPE_LIMIT)
                .set(PRICE, price);
    }

    public Stats measure(int warmup, int count) throws IOException {
        long seq = 0;
        for (int i = 0; i < warmup; i++) {
            sendAndReceive(newOrder(++seq, "INFY", SIDE_BUY, "100", "1500.50"));
        }
        long[] nanos = new long[count];
        long begin = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long t0 = System.nanoTime();
            FixMessage reply = sendAndReceive(newOrder(++seq, "INFY", SIDE_BUY, "100", "1500.50"));
            nanos[i] = System.nanoTime() - t0;
            if (!STATUS_FILLED.equals(reply.get(ORD_STATUS))) {
                throw new IllegalStateException("Order not filled: " + reply);
            }
        }
        double seconds = (System.nanoTime() - begin) / 1e9;
        Arrays.sort(nanos);
        double avg = Arrays.stream(nanos).average().orElse(0) / 1000.0;
        return new Stats(count, avg, nanos[count / 2] / 1000.0,
                nanos[Math.min(count - 1, (int) (count * 0.99))] / 1000.0,
                nanos[count - 1] / 1000.0, count / seconds);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9876;
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 10_000;
        try (LatencyClient client = new LatencyClient(host, port)) {
            Stats s = client.measure(1_000, count);
            System.out.printf("Orders: %d%n", s.count());
            System.out.printf("Average round trip: %.1f us%n", s.avgMicros());
            System.out.printf("p50: %.1f us   p99: %.1f us   max: %.1f us%n", s.p50Micros(), s.p99Micros(), s.maxMicros());
            System.out.printf("Throughput: %.0f orders/sec (one at a time)%n", s.ordersPerSecond());
        }
    }
}
