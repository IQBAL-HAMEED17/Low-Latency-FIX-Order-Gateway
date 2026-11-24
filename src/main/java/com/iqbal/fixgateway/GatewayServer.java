package com.iqbal.fixgateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking TCP server built on Java NIO.
 *
 * One I/O thread runs a Selector and handles every connection (accept, read, write).
 * Parsed orders are handed to a small set of worker lanes. A connection always uses
 * the same lane, so replies on one session keep the order the requests arrived in.
 * Workers never touch the socket directly: they queue the reply and wake the selector.
 */
public final class GatewayServer {

    private static final int READ_BUFFER_BYTES = 16 * 1024;

    private final int requestedPort;
    private final OrderProcessor processor;
    private final ExecutorService[] lanes;
    private final Queue<Connection> pendingWrites = new ConcurrentLinkedQueue<>();
    private final AtomicLong connectionIds = new AtomicLong();
    private final AtomicLong messagesProcessed = new AtomicLong();

    private volatile boolean running;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Thread ioThread;

    public GatewayServer(int port, int workerLanes, OrderProcessor processor) {
        this.requestedPort = port;
        this.processor = processor;
        this.lanes = new ExecutorService[Math.max(1, workerLanes)];
        for (int i = 0; i < lanes.length; i++) {
            lanes[i] = Executors.newSingleThreadExecutor();
        }
    }

    /** Opens the port and starts the I/O thread. Use port 0 to let the OS choose a free port. */
    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(requestedPort));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        running = true;
        ioThread = new Thread(this::eventLoop, "nio-io");
        ioThread.start();
    }

    public int getPort() throws IOException {
        return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public void stop() throws IOException, InterruptedException {
        running = false;
        selector.wakeup();
        ioThread.join(2000);
        for (ExecutorService lane : lanes) {
            lane.shutdown();
            lane.awaitTermination(2, TimeUnit.SECONDS);
        }
        for (SelectionKey key : selector.keys()) {
            closeQuietly(key.channel());
        }
        selector.close();
    }

    // ---- I/O thread -----------------------------------------------------------------

    private void eventLoop() {
        while (running) {
            try {
                selector.select();
                drainPendingWrites();
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    try {
                        if (key.isAcceptable()) {
                            accept();
                        } else {
                            if (key.isReadable()) {
                                read(key);
                            }
                            if (key.isValid() && key.isWritable()) {
                                flush((Connection) key.attachment());
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        close(key);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Selector error: " + e.getMessage());
                }
            }
        }
    }

    private void accept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel == null) {
            return;
        }
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);   // do not delay small messages
        Connection conn = new Connection(connectionIds.incrementAndGet(), channel);
        conn.key = channel.register(selector, SelectionKey.OP_READ, conn);
    }

    private void read(SelectionKey key) throws IOException {
        Connection conn = (Connection) key.attachment();
        int n = conn.channel.read(conn.in);
        if (n < 0) {
            close(key);
            return;
        }
        conn.in.flip();                                   // switch buffer from writing to reading
        String frame;
        while ((frame = FixFramer.nextFrame(conn.in)) != null) {
            dispatch(conn, frame);
        }
        conn.in.compact();                                // keep any half-received message
        if (!conn.in.hasRemaining()) {
            throw new IllegalStateException("Message too large");
        }
    }

    private void dispatch(Connection conn, String rawFrame) {
        lanes[(int) (conn.id % lanes.length)].execute(() -> {
            String reply;
            try {
                if (!FixCodec.hasValidChecksum(rawFrame)) {
                    reply = FixCodec.encode(rejectText("Bad checksum(10)"));
                } else {
                    reply = FixCodec.encode(processor.process(FixCodec.decode(rawFrame)));
                }
            } catch (RuntimeException e) {
                reply = FixCodec.encode(rejectText("Malformed message: " + e.getMessage()));
            }
            messagesProcessed.incrementAndGet();
            conn.out.add(ByteBuffer.wrap(reply.getBytes(StandardCharsets.ISO_8859_1)));
            pendingWrites.add(conn);
            selector.wakeup();                            // tell the I/O thread there is data to send
        });
    }

    private static FixMessage rejectText(String text) {
        return new FixMessage()
                .set(FixConstants.MSG_TYPE, FixConstants.MSG_REJECT)
                .set(FixConstants.TEXT, text);
    }

    private void drainPendingWrites() {
        Connection conn;
        while ((conn = pendingWrites.poll()) != null) {
            if (conn.key != null && conn.key.isValid()) {
                try {
                    flush(conn);
                } catch (IOException e) {
                    close(conn.key);
                }
            }
        }
    }

    /** Writes queued replies. If the socket is full, asks the selector to tell us when it has room. */
    private void flush(Connection conn) throws IOException {
        ByteBuffer next;
        while ((next = conn.out.peek()) != null) {
            conn.channel.write(next);
            if (next.hasRemaining()) {
                conn.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                return;
            }
            conn.out.poll();
        }
        conn.key.interestOps(SelectionKey.OP_READ);
    }

    private void close(SelectionKey key) {
        key.cancel();
        closeQuietly(key.channel());
    }

    private static void closeQuietly(java.nio.channels.Channel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    /** State kept for one client connection. */
    private static final class Connection {
        final long id;
        final SocketChannel channel;
        final ByteBuffer in = ByteBuffer.allocate(READ_BUFFER_BYTES);
        final Queue<ByteBuffer> out = new ConcurrentLinkedQueue<>();
        volatile SelectionKey key;

        Connection(long id, SocketChannel channel) {
            this.id = id;
            this.channel = channel;
        }
    }
}
