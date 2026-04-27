package com.tpt.validator.external.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExecutorTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void successOn200() {
        server.createContext("/ok", ex -> {
            byte[] body = "hello".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        HttpExecutor exec = new HttpExecutor(new RateLimiter(100, 10));
        var res = exec.send(HttpExecutor.Request.get(URI.create("http://127.0.0.1:" + port + "/ok")));
        assertThat(res).isPresent();
        assertThat(res.get().statusCode()).isEqualTo(200);
        assertThat(res.get().body()).isEqualTo("hello");
    }

    @Test
    void retriesOn503ThenGivesUp() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/down", ex -> {
            calls.incrementAndGet();
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        HttpExecutor exec = new HttpExecutor(new RateLimiter(100, 10), 1);
        var res = exec.send(HttpExecutor.Request.get(URI.create("http://127.0.0.1:" + port + "/down")));
        assertThat(res).isEmpty();
        assertThat(calls.get()).isEqualTo(3);
    }
}
