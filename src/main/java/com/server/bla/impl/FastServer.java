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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by jonhkr on 5/5/16.
 */
public class FastServer implements Server {

    private final ServerOptions options;
    private final Logger logger = LoggerFactory.getLogger(FastServer.class);
    private Node<Worker> currentWorker;

    public FastServer(ServerOptions options) {
        this.options = options;
    }

    @Override
    public void listen() throws IOException {
        setupWorkers(2);

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
                    SocketChannel sChannel = ((ServerSocketChannel) key.channel()).accept();

                    Worker worker = currentWorker.item;
                    currentWorker = currentWorker.next;

                    worker.accept(sChannel);
                }
            }
        }
    }

    private void setupWorkers(int n) {
        Node<Worker> current;
        Node<Worker> last = null;
        for(int i = 0; i < n; i++) {
            Worker worker = new Worker();
            new Thread(worker).start();

            current = new Node<>(worker, null);

            if (currentWorker == null) {
                currentWorker = current;
            } else {
                last.next = current;
            }

            last = current;
        }

        last.next = currentWorker;
    }

    class Request {

        ByteBuffer buffer = ByteBuffer.allocate(64*1024);
        SocketChannel channel;
        int mark = 0;
        int keepAlive = 1;

        public Request(SocketChannel channel) {
            this.channel = channel;
        }

        public void readData() throws IOException {
            int byteCount = channel.read(buffer);

            if (byteCount == -1) {
                keepAlive = 0;
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

        public void setKeepAlive(int keepAlive) {
            this.keepAlive = keepAlive;
        }

        public int getKeepAlive() {
            return keepAlive;
        }
    }

    class Worker implements Runnable {
        final Selector selector;
        final Queue<SocketChannel> connected = new ArrayBlockingQueue<>(1000);

        public Worker() {
            try {
                selector = Selector.open();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            try {
               loop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void loop() throws IOException {
            while (true) {
                SocketChannel c;
                while((c = connected.poll()) != null) {
                    c.configureBlocking(false);
                    c.register(selector, SelectionKey.OP_READ);
                }

                if (selector.select() == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isReadable()) {
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        Request request = (Request) key.attachment();

                        if (request == null) {
                            request = new Request(socketChannel);
                            key.attach(request);
                        } else {
                            request.setChannel(socketChannel);
                        }

                        request.readData();

                        String line;
                        while((line = request.readLine()) != null) {

                            if (line.equals("Connection: close") || line.contains("HTTP/1.0")) {
                                request.setKeepAlive(0);
                            }

                            if (line.isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("HTTP/1.0 200 Ok\r\n");
                                sb.append("Content-Length: 0\r\n\r\n");

                                socketChannel.write(ByteBuffer.wrap(sb.toString().getBytes()));
                            }
                        }

                        if (request.getKeepAlive() == 0) {
                            socketChannel.close();
                        }
                    }
                }
            }
        }

        public void accept(SocketChannel channel) throws IOException {
            connected.add(channel);
            selector.wakeup();
        }
    }

    private class Node<E> {
        E item;
        Node<E> next;

        Node(E element, Node<E> next) {
            this.item = element;
            this.next = next;
        }
    }
}
