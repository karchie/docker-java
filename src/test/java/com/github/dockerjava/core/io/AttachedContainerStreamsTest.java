package com.github.dockerjava.core.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.NotModifiedException;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.client.AbstractDockerClientTest;

public class AttachedContainerStreamsTest extends AbstractDockerClientTest {
    private final Logger logger = LoggerFactory.getLogger(AttachedContainerStreams.class);

    CreateContainerResponse container;

    @BeforeTest
    public void beforeTest() throws DockerException {
        super.beforeTest();
    }

    @AfterTest
    public void afterTest() {
        super.afterTest();
    }

    @BeforeMethod
    public void beforeMethod(final Method method) {
        super.beforeMethod(method);
    }

    @AfterMethod
    public void afterMethod(final ITestResult result) {
        super.afterMethod(result);
    }

    @Test
    public void AttachedContainerStreamsStdinStdoutStderr() throws IOException {
        // open a shell
        container = dockerClient
                .createContainerCmd("busybox")
                .withCmd("/bin/sh")
                .withStdinOpen(true)
                .withAttachStdin(true)
                .withStdInOnce(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            final AttachedContainerStreams attached = dockerClient.attachContainerCmd(container.getId())
                    .withStdIn().withStdOut().withStdErr().withFollowStream().exec();
            final PrintStream stdin = new PrintStream(attached.getStdin());
            final InputStream stdout = attached.getStdout(), stderr = attached.getStderr();
            assert 0 == stdout.available();

            // echo foo >stdout
            logger.trace("sending application input: echo foo");
            stdin.print("echo foo\n");
            stdin.flush();
            logger.trace("stdin flushed; reading response from stdout");
            while (0 == stdout.available()) {
                logger.trace("waiting for stdout");
                try {
                    Thread.sleep(1000);
                } catch (Throwable ignore) {}
            }
            int n_avail;
            byte[] buf;

            n_avail = stdout.available();
            logger.trace("ready with {} bytes", n_avail);
            buf = new byte[n_avail];
            stdout.read(buf);
            logger.trace("received response {}", new String(buf));
            assert Arrays.equals("foo\n".getBytes(), buf);
            assert 0 == stdout.available();
            assert 0 == stderr.available();

            // echo bar >stderr
            logger.trace("sending application input: echo bar >&2");
            stdin.print("echo bar >&2\n");
            stdin.flush();
            logger.trace("stdin flushed; reading response from stderr");
            while (0 == stderr.available()) {
                logger.trace("waiting for stderr");
                try {
                    Thread.sleep(1000);
                } catch (Throwable ignore) {}
            }
            n_avail = stderr.available();
            buf = new byte[n_avail];
            stderr.read(buf);
            logger.trace("received response {}", new String(buf));
            assert Arrays.equals("bar\n".getBytes(), buf);
            assert 0 == stdout.available();
            assert 0 == stderr.available();

            // close stdin, should exit shell in container
            logger.trace("closing stdin to end shell");
            stdin.close();

            logger.trace("inspecting state");
            final InspectContainerResponse inspect = dockerClient.inspectContainerCmd(container.getId()).exec();
            final ContainerState state = inspect.getState();
            assert !state.isRunning();
            assert 0 == state.getExitCode();
        } finally {
            String id = container.getId();
            try {
                dockerClient.stopContainerCmd(id).exec();
            } catch (NotModifiedException okAlreadyClosed) {
                // ignore
            } finally {
                dockerClient.removeContainerCmd(id);
            }
        }
    }

    @Test
    public void AttachedContainerStreamsStdinStdoutWithEOF() throws IOException {
        // open a shell
        container = dockerClient
                .createContainerCmd("busybox")
                .withCmd("/bin/sh")
                .withStdinOpen(true)
                .withAttachStdin(true)
                .withStdInOnce(true)
                .withAttachStdout(true)
                .withAttachStderr(false)
                .exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            final AttachedContainerStreams attached = dockerClient.attachContainerCmd(container.getId())
                    .withStdIn().withStdOut().withFollowStream().exec();
            final PrintStream stdin = new PrintStream(attached.getStdin());
            final InputStream stdout = attached.getStdout();
            assert null == attached.getStderr();
            assert 0 == stdout.available();

            // echo foo >stdout
            logger.trace("sending application input: echo baz");
            stdin.print("echo baz\n");
            stdin.flush();
            logger.trace("stdin flushed; reading response from stdout");
            while (0 == stdout.available()) {
                logger.trace("waiting for stdout");
                try {
                    Thread.sleep(1000);
                } catch (Throwable ignore) {}
            }
            int n_avail;
            byte[] buf;

            n_avail = stdout.available();
            logger.trace("ready with {} bytes", n_avail);
            buf = new byte[n_avail];
            stdout.read(buf);
            logger.trace("received response {}", new String(buf));
            assert Arrays.equals("baz\n".getBytes(), buf);
            assert 0 == stdout.available();

            // close stdin to exit shell
            stdin.close();

            // now try to read
            try {
                stdout.read();
                assert false;
            } catch (EOFException expected) {
                logger.trace("saw expected EOF after closing stdin");
            }
        } finally {
            String id = container.getId();
            try {
                dockerClient.stopContainerCmd(id).exec();
            } catch (NotModifiedException okAlreadyClosed) {
                // ignore
            } finally {
                dockerClient.removeContainerCmd(id);
            }
        }
    }

    @Test
    public void close() {
        throw new RuntimeException("Test not implemented");
    }

    @Test
    public void getStderr() {
        throw new RuntimeException("Test not implemented");
    }

    @Test
    public void getStdin() {
        throw new RuntimeException("Test not implemented");
    }

    @Test
    public void getStdout() {
        throw new RuntimeException("Test not implemented");
    }
}
