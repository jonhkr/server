package com.server.bla;

import java.nio.channels.SocketChannel;

/**
 * Created by jonhkr on 5/8/16.
 */
public interface ServerWorker extends Runnable{

    void accept(SocketChannel channel);
}
