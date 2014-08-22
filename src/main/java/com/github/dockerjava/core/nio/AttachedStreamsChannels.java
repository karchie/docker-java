/**
 * Copyright (c) 2014 Washington University School of Medicine
 */
package com.github.dockerjava.core.nio;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Sets;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class AttachedStreamsChannels implements AutoCloseable, Closeable {
    private static final String EOF_HEADER_MSG = "EOF reading Docker frame header";
    private static final String EOF_DATA_MSG = "EOF reading Docker frame data";
    private static final int HEADER_SIZE = 8, HEADER_SIZE_OFFSET = 4;
    private final Logger logger = LoggerFactory.getLogger(AttachedStreamsChannels.class);
    private final ReadableByteChannel input;
    private final ByteBuffer headerbuf = ByteBuffer.allocate(HEADER_SIZE);
    private final LinkedListMultimap<Byte,ByteBuffer> bufs = LinkedListMultimap.create();
    private final Set<Byte> open = Sets.newLinkedHashSet();
    
    public static enum Stream {
        STDOUT(1), STDERR(2);
        
        private byte index;
        private Stream(final int index) {
            this.index = (byte)index;
        };
    }

    /**
     * 
     */
    public AttachedStreamsChannels(final ReadableByteChannel input, final Stream...streams) {
        this.input = input;
        headerbuf.order(ByteOrder.BIG_ENDIAN);
        for (final Stream stream : streams) {
            open.add(stream.index);
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        input.close();
    }

    public ReadableByteChannel getChannel(final Stream stream) throws IOException {
        if (!open.contains(stream.index)) {
            throw new ClosedChannelException();
        }
        return new DemuxedChannel(stream.index);
    }
    
    public ReadableByteChannel getStdout() throws IOException {
        return getChannel(Stream.STDOUT);
    }
    
    public ReadableByteChannel getStderr() throws IOException {
        return getChannel(Stream.STDERR);
    }
    
    /**
     * Read the next Docker stream frame and put into the right buffer queue.
     * @return stream index for the received frame
     * @throws IOException
     */
    private byte getNextFrame() throws IOException {
        headerbuf.clear();
        try {
            NIOUtils.readFully(input, headerbuf);
        } catch (EOFException e) {
            throw new EOFException(EOF_HEADER_MSG);
        }
        headerbuf.position(0);
        final byte stream = headerbuf.get();
        final int size = headerbuf.getInt(HEADER_SIZE_OFFSET);
        if (size > 0) { // TODO: is this a thing? is zero frame size valid?
            final ByteBuffer data = ByteBuffer.allocate(size);
            try {
                NIOUtils.readFully(input, data);
                if (open.contains(stream)) {
                    bufs.put(stream, data);
                }
             } catch (EOFException e) {
                throw new EOFException(EOF_DATA_MSG);
            }
        }
        return stream;
    }

    private class DemuxedChannel implements ReadableByteChannel {
        private final byte index;

        DemuxedChannel(final byte index) {
            this.index = index;
        }

        /* (non-Javadoc)
         * @see java.nio.channels.Channel#close()
         */
        @Override
        public void close() throws IOException {
            open.remove(index);
            bufs.removeAll(index);
            if (open.isEmpty()) {
                AttachedStreamsChannels.this.close();
            }
        }

        /* (non-Javadoc)
         * @see java.nio.channels.Channel#isOpen()
         */
        @Override
        public boolean isOpen() {
            return open.contains(index);
        }

        /* (non-Javadoc)
         * @see java.nio.channels.ReadableByteChannel#read(java.nio.ByteBuffer)
         */
        @Override
        public synchronized int read(final ByteBuffer dst) throws IOException {
            if (!open.contains(index)) {
                throw new ClosedChannelException();
            }
            if (0 == dst.remaining()) {
                return 0;
            }
            while (bufs.get(index).isEmpty()) {
                getNextFrame(); // throws 
            }
            int n = 0;
            while (!bufs.get(index).isEmpty()) {
                final ByteBuffer src = bufs.get(index).get(0);
                final int sr = src.remaining(), dr = dst.remaining();
                assert sr > 0;
                assert dr > 0;
                if (sr <= dr) {
                    dst.get(sr);
                    n += sr;
                    bufs.get(index).remove(0);
                    if (sr == dr) {
                        return 0;
                    }
                } else {                    
                    final byte[] srcb = new byte[dr];
                    src.put(srcb);
                    dst.get(srcb);
                    return n + dr;
                }
            }
            return n;
        }      
    }
}
