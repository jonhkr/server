package com.server.bla.impl;

import com.server.bla.RequestHandler;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by jonhkr on 5/8/16.
 */
public class Worker extends AbstractWorker {

    final Queue<SocketChannel> connected = new ArrayBlockingQueue<>(1000);
    final Class<? extends RequestHandler> requestHandlerClass;

    public Worker(Class<? extends RequestHandler> requestHandlerClass) {
        this.requestHandlerClass = requestHandlerClass;
    }

    @Override
    public void accept(SocketChannel channel) {
        connected.add(channel);
        selector.wakeup();
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

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while(iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();

                if (selectionKey.isReadable()) {
                    SocketChannel channel = (SocketChannel) selectionKey.channel();
                    RequestHandler handler = (RequestHandler) selectionKey.attachment();

                    if (handler == null) {
                        handler = getRequestHandler(channel);
                        selectionKey.attach(handler);
                    }

                    handler.read();
                }

                iterator.remove();
            }
        }
    }

    private RequestHandler getRequestHandler(SocketChannel channel) {
        RequestHandler handler;

        try {
            handler = requestHandlerClass.getConstructor(SocketChannel.class).newInstance(channel);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return handler;
    }
}
