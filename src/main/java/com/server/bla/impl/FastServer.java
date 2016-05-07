package com.server.bla.impl;

import com.server.bla.Server;
import com.server.bla.ServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by jonhkr on 5/5/16.
 */
public class FastServer implements Server {

    private final ServerOptions options;
    private final Logger logger = LoggerFactory.getLogger(FastServer.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public FastServer(ServerOptions options) {
        this.options = options;
    }

    @Override
    public void listen() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        Selector selector = Selector.open();

        channel.socket().bind(new InetSocketAddress(options.host, options.port));

        logger.info("Listening on {}:{}", options.host, options.port);

        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            if (selector.select() == 0) {
                continue;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
                    logger.debug("Accept connection from: " + socketChannel.getRemoteAddress());
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                }

                if (key.isReadable()) {
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    Request request = (Request) key.attachment();

                    if (request == null) {
                        request = new Request(socketChannel);
                        key.attach(request);
                    } else {
                        request.setChannel(socketChannel);
                    }

                    boolean keepAlive = true;

                    try {
                        request.readData();
                    } catch (IOException e) {
                        keepAlive = false;
                    }

                    String line;
                    while((line = request.readLine()) != null) {
                        logger.debug(line);
                        if (line.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("HTTP/1.1 204 No content\r\n");
                            sb.append("Cache-Control: private\r\n");
                            sb.append("Content-Length: 0\r\n");
                            sb.append("Date: Sat, 07 May 2016 16:14:47 GMT\r\n\r\n");

                            socketChannel.write(ByteBuffer.wrap(sb.toString().getBytes()));
                        }
                    }

                    if (!keepAlive) {
                        socketChannel.close();
                    }
                }
            }
        }
    }

    class Request {

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel channel;
        int mark = 0;

        public Request(SocketChannel channel) {
            this.channel = channel;
        }

        public void readData() throws IOException {
            int byteCount = channel.read(buffer);

            if (byteCount == -1) {
                throw new IOException("EOF");
            }

            buffer.flip();
            buffer.position(mark);
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

        public void setChannel(SocketChannel channel) {
            this.channel = channel;
            clear();
        }

        public void clear() {
            mark = 0;
            buffer.clear();
        }
    }
}
