/**
 * Copyright (c) 2014 Washington University School of Medicine
 */
package com.github.dockerjava.core.nio;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public final class NIOUtils {
    private NIOUtils() {}
    
    /**
     * Do a blocking read on c until b is filled.
     * @param c channel
     * @param b ByteBuffer
     * @param maxSleep maximum backoff time, in msec, after c.read() returns 0
     * @return number of bytes read
     * @throws IOException
     */
    public static int readFully(final ReadableByteChannel c, final ByteBuffer b, final long maxSleep)
            throws IOException {
        final int n0 = b.position(), limit = b.limit();
        long sleep = 10;
        int n = n0;
        while (n < limit) {
            final int nr = c.read(b);
            if (nr < 0) {
                throw new EOFException();
            } else if (nr == 0) {
                // I don't really expect this to happen, but do exponential backoff if it does
                final Logger logger = LoggerFactory.getLogger(NIOUtils.class);
                logger.trace("Channel.read returned 0; sleeping {} ms", sleep);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ok) {       
                    logger.debug("read sleep interrupted", ok);
                } finally {
                    sleep = Math.min(maxSleep, sleep * 2);
                }
            } else {
                n += nr;
            }
        }
        return n - n0;
    }
    
    public static final int DEFAULT_READ_MAX_SLEEP_MS = 1000;

    /**
     * Do a blocking read on c until b is filled.
     * @param c channel
     * @param b ByteBuffer
     * @return number of bytes read
     * @throws IOException
     */

    public static int readFully(final ReadableByteChannel c, final ByteBuffer b) throws IOException {
        return readFully(c, b, DEFAULT_READ_MAX_SLEEP_MS);
    }
}
