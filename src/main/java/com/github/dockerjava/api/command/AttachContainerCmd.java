package com.github.dockerjava.api.command;

import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.core.io.AttachedContainerStreams;

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
public interface AttachContainerCmd extends DockerCmd<AttachedContainerStreams>{

	public String getContainerId();

	public boolean hasLogsEnabled();

	public boolean hasFollowStreamEnabled();

	public boolean hasTimestampsEnabled();
	
	public boolean hasStdinEnabled();

	public boolean hasStdoutEnabled();

	public boolean hasStderrEnabled();
	
	public boolean isTty();

	public AttachContainerCmd withContainerId(String containerId);

	public AttachContainerCmd withFollowStream();

	public AttachContainerCmd withFollowStream(boolean followStream);

	public AttachContainerCmd withTimestamps(boolean timestamps);
	
	public AttachContainerCmd withStdIn();
	
	public AttachContainerCmd withStdIn(boolean stdin);

	public AttachContainerCmd withStdOut();

	public AttachContainerCmd withStdOut(boolean stdout);

	public AttachContainerCmd withStdErr();

	public AttachContainerCmd withStdErr(boolean stderr);

	public AttachContainerCmd withLogs(boolean logs);
	
	public AttachContainerCmd withTty();
	
	public AttachContainerCmd withTty(boolean tty);

	/**
	 * @throws NotFoundException No such container 
	 */
	@Override
	public AttachedContainerStreams exec() throws NotFoundException;
	
	public static interface Exec extends DockerCmdExec<AttachContainerCmd,AttachedContainerStreams> {
	}

}