package com.github.dockerjava.core.io;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 */
public class AttachedContainerStreams extends InputStream {
    private static final String CRLF = "\r\n";
    private final Logger logger = LoggerFactory.getLogger(AttachedContainerStreams.class);
    private final Socket socket;
    private final DataInputStream mux;
    private final OutputStream stdin;
    private final InputStream stdout, stderr, delegate;

    /**
     * 
     */
    public AttachedContainerStreams(final Socket socket,
            final String path, final String containerId,
            final boolean attachStdin, final boolean attachStdout, final boolean attachStderr,
            final boolean isTty)
                    throws IOException {
        this.socket = socket;
        final OutputStream socketOut = socket.getOutputStream();
        final InputStream socketIn = socket.getInputStream();
        sendReqHeader(socketOut, path, containerId, attachStdin, attachStdout, attachStderr);
        consumeHTTPHeader(logger, socketIn);
        mux = new DataInputStream(socketIn);
        if (attachStdin) {
            stdin = socketOut;
        } else {
            stdin = null;
            socket.shutdownOutput();
        }
        if (isTty) {
            delegate = socketIn;
            stdout = stderr = null;
        } else {
            stdout = attachStdout ? new DemuxedInputStream(Stream.STDOUT) : null;
            stderr = attachStderr ? new DemuxedInputStream(Stream.STDERR) : null;
            delegate = null == stdout ? stderr : stdout;
        }
        if (null == delegate) {
            socket.shutdownInput();
        }
    }

    public OutputStream getStdin() {
        return stdin;
    }

    public InputStream getStdout() {
        return stdout;
    }

    public InputStream getStderr() {
        return stderr;
    }

    /*
     * (non-Javadoc)
     * @see java.io.InputStream#available()
     */
    @Override
    public int available() throws IOException {
        if (null == delegate) {
            throw new IOException("no open stream");
        } else {
            return delegate.available();
        }
    }

    /* (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        IOException lastIOE = null;
        try {
            final InputStream[] ins = {mux, stdout, stderr};
            for (final InputStream is : ins) {
                if (null != is) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        lastIOE = e;
                    }
                }
            }
            if (null != stdin) {
                try {
                    stdin.close();
                } catch (IOException e) {
                    lastIOE = e;
                }
            }
            if (null != lastIOE) {
                throw lastIOE;
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                if (null != lastIOE) {
                    logger.error("socket exception masking earlier exception", lastIOE);
                }
                throw e;
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    @Override
    public int read() throws IOException {
        if (null == delegate) {
            throw new IOException("no open stream");
        } else {
            return delegate.read();
        }
    }

    /*
     * (non-Javadoc)
     * @see java.io.InputStream#read(byte[])
     */
    @Override
    public int read(final byte[] b) throws IOException {
        if (null == delegate) {
            throw new IOException("no open stream");
        } else {
            return delegate.read(b);
        }
    }

    /*
     * (non-Javadoc)
     * @see java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (null == delegate) {
            throw new IOException("no open stream");
        } else {
            return delegate.read(b, off, len);
        }
    }

    /*
     * (non-Javadoc)
     * @see java.io.InputStream#skip(long)
     */
    @Override
    public long skip(final long n) throws IOException {
        if (null == delegate) {
            throw new IOException("no open stream");
        } else {
            return delegate.skip(n);
        }
    }
    
    public void shutdownOutput() throws IOException {
        socket.shutdownOutput();
    }


    private void sendReqHeader(final OutputStream out, final String path, final String containerId,
            final boolean attachStdin, final boolean attachStdout, final boolean attachStderr)
                    throws IOException {
        final StringBuilder req = new StringBuilder("POST ");
        req.append(path).append("?");
        //        final boolean logs = attachStdout || attachStderr;  // TODO: what about this?
        req.append("logs=").append(false);  // TODO: really? not so sure about this.
        req.append("&stream=").append(attachStdin);
        req.append("&stdin=").append(attachStdin);
        req.append("&stdout=").append(attachStdout);
        req.append("&stderr=").append(attachStderr);
        req.append(" HTTP/1.1").append(CRLF);
        // TODO: should send header info indicating streaming content
        req.append(CRLF);
        out.write(req.toString().getBytes());
        out.flush();
        logger.trace("sent request header: {}", req);        
    }

    // TODO: handle error responses
    // TODO: parse header fields so client can inspect
    private static Callable<byte[]> consumeHTTPHeader(final Logger logger, final InputStream in) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        logger.debug("starting header consumer");
        try {
            for (int state = 0; state < 4;) {
                int b = in.read();
                if ('\n' == b && 1 == state % 2) {
                    state++;
                } else if ('\r' == b && 0 == state % 2) {
                    state++;
                } else if (-1 == b) {
                    bytes.close();
                    logger.error("unexpected EOF indicates incomplete response header: {}",
                            new String(bytes.toByteArray()));
                    throw new EOFException("reached unexpected end of input in header");
                } else {
                    // not part of CRLFx2. Unwind any pushed CR/LFs and start over
                    while (state > 0) {
                        bytes.write(0 == state-- % 2 ? '\n' : '\r');
                    }
                    bytes.write(b);
                    state = 0;
                }
            }
            bytes.write(CRLF.getBytes());
            logger.trace("received header: {}", bytes);
            return new Callable<byte[]>() {
                public byte[] call() { return bytes.toByteArray(); }
            };
        } finally {
            bytes.close();  // noop close prevents dumb compiler warnings
        }
    }

    public static enum Stream {
        STDOUT(1), STDERR(2);

        private byte index;
        private Stream(final int index) {
            this.index = (byte)index;
        };
    }

    private final Map<Byte,DemuxedInputStream> streams = Maps.newHashMap();

    private final void readOneFrame() throws IOException {
        // First 4 bytes of frame header: {STREAM_TYPE, 0, 0, 0}
        final byte[] header = new byte[4];
        logger.trace("reading next frame header");
        mux.readFully(header);
        logger.trace("frame header: {}", Arrays.toString(header));
        final DemuxedInputStream stream = streams.get(header[0]);
        // Second 4 bytes of frame header: data size, as big-endian uint32
        for (long remaining = 0L | mux.readInt(); remaining > 0;) {
            // handle unlikely but possible case of size > MAX_VALUE
            logger.trace("frame size {} bytes ({})", remaining, Long.toHexString(remaining));
            final long chunk = Math.min(remaining, (long)Integer.MAX_VALUE);
            final byte[] data = new byte[(int)chunk];
            try {
                mux.readFully(data);
            } catch (EOFException e) {
                logger.error("incomplete frame", e);
                throw new IOException("EOF reached in middle of frame", e);
            }
            remaining -= chunk;
            stream.bufs.add(data);
        }
    }

    private class DemuxedInputStream extends InputStream {
        private final Queue<byte[]> bufs = Lists.newLinkedList();
        private final Stream stream;
        private boolean open;
        private int position = 0;

        private DemuxedInputStream(final Stream stream) {
            this.stream = stream;
            open = true;
            streams.put(stream.index, this);
        }

        @Override
        public int available() throws IOException {
            int n = 0;
            for (final byte[] buf : bufs) {
                n += buf.length;
            }
            if (0 == n && mux.available() > 0) {
                readOneFrame();
                return available();
            } else {
                return n;
            }
        }

        @Override
        public void close() throws IOException {
            open = false;
            bufs.clear();

            // if no demux readers are left we can half-close
            for (final DemuxedInputStream s : streams.values()) {
                if (s.open) {
                    return;
                }
            }
            try {
                socket.shutdownInput();
            } catch (SocketException e) { 
                // probably just already input-half-closed; log in case not
                logger.debug("unable to (re?)half-close socket", e);
            }
        }

        @Override
        public int read() throws IOException {
            logger.trace("reading {}", stream);
            if (!open) {
                return -1;
            }
            while (bufs.isEmpty()) {
                try {
                    readOneFrame();
                } catch (EOFException e) {
                    if (bufs.isEmpty()) {
                        open = false;
                        return -1;
                    }
                } catch (SocketException e) {
                    if (socket.isClosed() || socket.isInputShutdown()) {
                        if (bufs.isEmpty()) {
                            open = false;
                            throw new EOFException(e.getMessage());
                        }
                    } else {
                        // not just closed/half-closed socket; likely a real problem
                        throw e;
                    }
                }
            }
            logger.trace("bytes available for {}", stream);
            final byte[] buf = bufs.peek();            
            final int b = buf[position++];
            if (position == buf.length) {
                position = 0;
                bufs.poll();
            }
            logger.trace("returning {} for {}", b, stream);
            return b;
        }     
    }
}
