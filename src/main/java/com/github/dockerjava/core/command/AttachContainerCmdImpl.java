package com.github.dockerjava.core.command;

import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.core.io.AttachedContainerStreams;
import com.google.common.base.Preconditions;

/**
 * Attach to container
 * 
 * @param logs
 *            - true or false, includes logs. Defaults to false.
 * 
 * @param followStream
 *            - true or false, return stream. Defaults to false.
 * @param stdout
 *            - true or false, includes stdout log. Defaults to false.
 * @param stderr
 *            - true or false, includes stderr log. Defaults to false.
 * @param timestamps
 *            - true or false, if true, print timestamps for every log line.
 *            Defaults to false.
 */
public class AttachContainerCmdImpl extends	AbstrDockerCmd<AttachContainerCmd,AttachedContainerStreams> implements AttachContainerCmd {

	private String containerId;

	private boolean logs, followStream, timestamps, stdin, stdout, stderr, isTty;

	public AttachContainerCmdImpl(AttachContainerCmd.Exec exec, String containerId) {
		super(exec);
		withContainerId(containerId);
	}

	@Override
	public String getContainerId() {
		return containerId;
	}

	@Override
	public boolean hasLogsEnabled() {
		return logs;
	}

	@Override
	public boolean hasFollowStreamEnabled() {
		return followStream;
	}

	@Override
	public boolean hasTimestampsEnabled() {
		return timestamps;
	}
	
	@Override
	public boolean hasStdinEnabled() {
	    return stdin;
	}

	@Override
	public boolean hasStdoutEnabled() {
		return stdout;
	}

	@Override
	public boolean hasStderrEnabled() {
		return stderr;
	}
	
	public boolean isTty() {
	    return isTty;
	}

	@Override
	public AttachContainerCmd withContainerId(String containerId) {
		Preconditions.checkNotNull(containerId, "containerId was not specified");
		this.containerId = containerId;
		return this;
	}

	@Override
	public AttachContainerCmd withFollowStream() {
		return withFollowStream(true);
	}

	@Override
	public AttachContainerCmd withFollowStream(boolean followStream) {
		this.followStream = followStream;
		return this;
	}

	@Override
	public AttachContainerCmd withTimestamps(boolean timestamps) {
		this.timestamps = timestamps;
		return this;
	}

	@Override
	public AttachContainerCmd withStdIn() {
	    return withStdIn(true);
	}
	
	@Override
	public AttachContainerCmd withStdIn(boolean stdin) {
	    this.stdin = stdin;
	    return this;
	}
	
	@Override
	public AttachContainerCmd withStdOut() {
		return withStdOut(true);
	}

	@Override
	public AttachContainerCmd withStdOut(boolean stdout) {
		this.stdout = stdout;
		return this;
	}

	@Override
	public AttachContainerCmd withStdErr() {
		return withStdErr(true);
	}

	@Override
	public AttachContainerCmd withStdErr(boolean stderr) {
		this.stderr = stderr;
		return this;
	}

	@Override
	public AttachContainerCmd withLogs(boolean logs) {
		this.logs = logs;
		return this;
	}
	
	public AttachContainerCmd withTty() {
	    return withTty(true);
	}
	
	public AttachContainerCmd withTty(boolean isTty) {
	    this.isTty = isTty;
	    return this;
	}
	
	/**
	 * @throws NotFoundException No such container 
	 */
	@Override
	public AttachedContainerStreams exec() throws NotFoundException {
		return super.exec();
	}
}
