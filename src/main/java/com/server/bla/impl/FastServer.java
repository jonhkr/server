package com.server.bla.impl;

import com.server.bla.Server;
import com.server.bla.ServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

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
            Worker worker = new Worker(HttpRequestHandler.class);
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

    private class Node<E> {
        E item;
        Node<E> next;

        Node(E element, Node<E> next) {
            this.item = element;
            this.next = next;
        }
    }
}
