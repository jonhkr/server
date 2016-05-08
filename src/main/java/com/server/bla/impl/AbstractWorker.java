package com.server.bla.impl;

import com.server.bla.ServerWorker;

import java.io.IOException;
import java.nio.channels.Selector;

/**
 * Created by jonhkr on 5/8/16.
 */
public abstract class AbstractWorker implements ServerWorker {
    final Selector selector;

    public AbstractWorker() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
