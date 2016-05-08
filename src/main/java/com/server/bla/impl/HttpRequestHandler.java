package com.server.bla.impl;

import com.server.bla.RequestHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by jonhkr on 5/8/16.
 */
public class HttpRequestHandler implements RequestHandler {

    final ByteBuffer buffer = ByteBuffer.allocate(64*1024);
    final SocketChannel channel;
    int mark = 0;
    int keepAlive = 1;

    public HttpRequestHandler(SocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void read() throws IOException {
        clear();

        int byteCount = channel.read(buffer);

        if (byteCount == -1) {
            keepAlive = 0;
        }

        buffer.flip();
        buffer.position(mark);

        parse();
    }

    public void parse() throws IOException {
        String line;
        while((line = readLine()) != null) {

            if (line.equals("Connection: close") || line.contains("HTTP/1.0")) {
                keepAlive(0);
            }

            if (line.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("HTTP/1.1 200 Ok\r\n");
                sb.append("Content-Length: 0\r\n");
                if (keepAlive() == 1) {
                    sb.append("Connection: keep-alive\r\n\r\n");
                }

                channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
            }
        }

        if (keepAlive() == 0) {
            channel.close();
        }
    }

    public String readLine() {
        StringBuilder sb = new StringBuilder();
        int l = -1;

        while(buffer.hasRemaining()) {
            char c = (char) buffer.get();
            sb.append(c);
            if (c == '\n' && l == '\r') {
                mark = buffer.position();

                return sb.substring(0, sb.length() - 2);
            }

            l = c;
        }

        return null;
    }


    public void clear() {
        mark = 0;
        buffer.clear();
    }

    public void keepAlive(int keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int keepAlive() {
        return keepAlive;
    }
}
