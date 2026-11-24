package com.iqbal.fixgateway;

import static com.iqbal.fixgateway.FixConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayServerIntegrationTest {

    private GatewayServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new GatewayServer(0, 2, new OrderProcessor("EXCHANGE"));   // port 0 = any free port
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
    }

    @Test
    void validOrderGetsFilledExecutionReport() throws IOException {
        try (LatencyClient client = new LatencyClient("localhost", server.getPort())) {
            FixMessage reply = client.sendAndReceive(LatencyClient.newOrder(1, "INFY", SIDE_BUY, "100", "1500.50"));

            assertEquals(MSG_EXECUTION_REPORT, reply.msgType());
            assertEquals(STATUS_FILLED, reply.get(ORD_STATUS));
            assertEquals("CL1", reply.get(CL_ORD_ID));
            assertEquals("100", reply.get(CUM_QTY));
            assertEquals("0", reply.get(LEAVES_QTY));
        }
    }

    @Test
    void invalidQuantityIsRejected() throws IOException {
        try (LatencyClient client = new LatencyClient("localhost", server.getPort())) {
            FixMessage reply = client.sendAndReceive(LatencyClient.newOrder(1, "INFY", SIDE_BUY, "0", "1500.50"));

            assertEquals(STATUS_REJECTED, reply.get(ORD_STATUS));
            assertTrue(reply.get(TEXT).contains("OrderQty"));
        }
    }

    @Test
    void unsupportedMessageTypeGetsReject() throws IOException {
        try (LatencyClient client = new LatencyClient("localhost", server.getPort())) {
            FixMessage heartbeat = new FixMessage().set(MSG_TYPE, "0").set(SENDER_COMP_ID, "CLIENT");
            FixMessage reply = client.sendAndReceive(heartbeat);

            assertEquals(MSG_REJECT, reply.msgType());
        }
    }

    @Test
    void manyOrdersOnOneConnectionKeepTheirOrder() throws IOException {
        try (LatencyClient client = new LatencyClient("localhost", server.getPort())) {
            for (int i = 1; i <= 200; i++) {
                FixMessage reply = client.sendAndReceive(LatencyClient.newOrder(i, "TCS", SIDE_SELL, "10", "3500"));
                assertEquals("CL" + i, reply.get(CL_ORD_ID));
            }
        }
        assertEquals(200L, server.getMessagesProcessed());
    }

    @Test
    void severalClientsAtTheSameTime() throws Exception {
        Thread[] threads = new Thread[5];
        boolean[] ok = new boolean[5];
        for (int t = 0; t < threads.length; t++) {
            final int idx = t;
            threads[t] = new Thread(() -> {
                try (LatencyClient client = new LatencyClient("localhost", server.getPort())) {
                    boolean all = true;
                    for (int i = 1; i <= 50; i++) {
                        FixMessage r = client.sendAndReceive(LatencyClient.newOrder(i, "INFY", SIDE_BUY, "5", "100"));
                        all &= STATUS_FILLED.equals(r.get(ORD_STATUS)) && ("CL" + i).equals(r.get(CL_ORD_ID));
                    }
                    ok[idx] = all;
                } catch (IOException e) {
                    ok[idx] = false;
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        for (boolean b : ok) {
            assertTrue(b);
        }
    }
}
