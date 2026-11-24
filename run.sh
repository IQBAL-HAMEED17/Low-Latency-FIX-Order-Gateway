#!/usr/bin/env bash
# Builds the project, runs the tests, starts the gateway, then runs the latency client against it.
set -e

mvn -B clean verify                       # compile + run all tests + build target/fix-order-gateway.jar

java -jar target/fix-order-gateway.jar 9876 4 &    # start the gateway on port 9876 with 4 worker lanes
GATEWAY_PID=$!
trap 'kill $GATEWAY_PID' EXIT
sleep 1

java -cp target/fix-order-gateway.jar com.iqbal.fixgateway.LatencyClient localhost 9876 20000
