package com.server.bla.impl;

import com.server.bla.Server;
import com.server.bla.ServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
                    executorService.submit(new Worker(socketChannel));
                }
            }
        }
    }

    class Worker implements Runnable {

        final SocketChannel channel;
        Selector selector;

        public Worker(SocketChannel channel) {
            this.channel = channel;
            try {
                this.selector = Selector.open();
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while(channel.isOpen()) {
                try {
                    selector.select();
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (key.isReadable()) {
                            read(key);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        protected void read(SelectionKey key) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();
            SocketAddress address = channel.getRemoteAddress();
            logger.debug("read from: {}", address);

            ByteBuffer buffer = ByteBuffer.allocate(10);

            int byteCount = channel.read(buffer);

            if (byteCount == -1) {
                channel.close();
            }

            if (byteCount > 0) {
                buffer.flip();
                channel.write(buffer);
            }

            logger.debug("done reading from: {}", address);
        }
    }
}
