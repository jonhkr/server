package com.server.bla;

import java.io.IOException;

/**
 * Created by jonhkr on 5/8/16.
 */
public interface RequestHandler extends Handler {

    void read() throws IOException;
}
