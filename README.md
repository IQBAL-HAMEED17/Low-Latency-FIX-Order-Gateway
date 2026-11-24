# Low-Latency FIX Order Gateway

A small exchange-style gateway written with plain Java NIO (no frameworks). A client sends FIX 4.2
`NewOrderSingle` (35=D) orders over TCP and the gateway answers each one with an `ExecutionReport` (35=8):
filled if the order is valid, rejected with a reason if not.

I built it to learn how non-blocking servers and the FIX protocol work, not to be a real exchange.

## What it demonstrates

- **Java NIO:** one I/O thread runs a `Selector` and serves every connection with `ServerSocketChannel`,
  `SocketChannel` and `ByteBuffer` (`flip`, `compact`).
- **FIX protocol basics:** tag=value messages separated by SOH, `BodyLength` (9) and `CheckSum` (10)
  calculated and verified.
- **TCP framing:** a stream can deliver half a message or several at once. `FixFramer` uses BodyLength to
  find message boundaries. The tests split a message at every byte position.
- **Concurrency:** orders are processed on worker lanes (single-thread executors). A connection always maps to
  the same lane, so replies keep the order of the requests. Workers queue replies and wake the selector, so
  only the I/O thread touches sockets.
- **Back-pressure:** if a socket cannot take more data, the server asks the selector for `OP_WRITE`.
- **Latency measurement:** `LatencyClient` reports average, p50, p99, max and throughput.

## Architecture

```
LatencyClient (mock client)                GatewayServer (mock exchange)
  NewOrderSingle 35=D  ───────TCP───────▶  I/O thread: Selector
                                              │ accept / read / write
                                              ▼
                                           FixFramer ──▶ worker lane (by connection)
                                                             │ FixCodec.decode
                                                             │ OrderProcessor.process
                                                             ▼
  ExecutionReport 35=8  ◀──────TCP───────  reply queue ──▶ selector.wakeup ──▶ write
```

## Run it

Requires Java 17+ and Maven.

```bash
mvn clean verify                                   # build and run the tests
java -jar target/fix-order-gateway.jar 9876 4      # start the gateway (port, worker lanes)
java -cp target/fix-order-gateway.jar com.iqbal.fixgateway.LatencyClient localhost 9876 20000
```

Or run everything with `./run.sh`.

The client prints average, p50, p99 and max round-trip time plus throughput. Numbers depend heavily on your
machine, so run it yourself and record your own results here:

| Date | Machine | Orders | Avg (us) | p50 (us) | p99 (us) | Orders/sec |
|------|---------|--------|----------|----------|----------|------------|
|      |         |        |          |          |          |            |

## Message example

Shown with `|` in place of the SOH character:

```
8=FIX.4.2|9=<length>|35=D|49=CLIENT|56=EXCHANGE|34=1|11=CL1|55=INFY|54=1|38=100|40=2|44=1500.50|10=<checksum>|
```

Reply (fully filled): `35=8`, `39=2` (OrdStatus Filled), `150=2`, `14=100` (CumQty), `151=0` (LeavesQty).
Invalid orders get `39=8` (Rejected) and the reason in tag 58.

## Tests

`mvn test` runs JUnit 5 tests for the codec (checksum, body length, round trip), the framer (partial, merged and
split messages) and the server (valid order, rejected order, unsupported message, ordering on one connection,
several clients at once).

## Limits (what this is not)

- No FIX session layer: no Logon (35=A), Heartbeat, sequence-number recovery or resend.
- Only NewOrderSingle is handled. There is no order book or matching, so every valid order is filled instantly.
- Messages are decoded into Strings and maps, which allocates objects. A real low-latency system would parse bytes
  in place and reuse buffers (see Ideas).
- Not tuned for production: no TLS, authentication or metrics.

## Ideas to extend it

- Reuse buffers and parse fields directly from bytes to cut garbage collection.
- Add Logon and Heartbeat handling, and check sequence numbers.
- Add a simple order book with price-time matching.
- Compare against QuickFIX/J.
- Use JMH for proper micro-benchmarks.
