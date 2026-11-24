package com.iqbal.fixgateway;

/** Starts the gateway. Usage: java -jar fix-order-gateway.jar [port] [workerLanes] */
public final class GatewayMain {

    private GatewayMain() { }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9876;
        int lanes = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        GatewayServer server = new GatewayServer(port, lanes, new OrderProcessor("EXCHANGE"));
        server.start();
        System.out.println("FIX gateway listening on port " + server.getPort() + " with " + lanes + " worker lanes");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // shutting down
            }
        }));
        Thread.currentThread().join();
    }
}
