package com.github.dockerjava.jaxrs;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

import javax.ws.rs.client.WebTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.core.io.AttachedContainerStreams;

public class AttachContainerCmdExec extends AbstrDockerCmdExec<AttachContainerCmd,AttachedContainerStreams> implements AttachContainerCmd.Exec {
	private final Logger logger = LoggerFactory.getLogger(AttachContainerCmdExec.class);
	
	public AttachContainerCmdExec(WebTarget baseResource) {
		super(baseResource);
	}

	private final static int getPort(final URI uri) {
	    final int p = uri.getPort();
	    if (-1 == p) {
	        if ("http".equals(uri.getScheme())) {
	            return 80;
	        } else if ("https".equals(uri.getScheme())) {
	            return 443;
	        } else {
	            throw new UnsupportedOperationException("no default port known for scheme " + uri.getScheme());
	        }
	    } else {
	        return p;
	    }
	}
	
	@Override
	protected AttachedContainerStreams execute(final AttachContainerCmd command) {
	    final WebTarget resource = getBaseResource().path("/containers/{id}/attach")
	            .resolveTemplate("id", command.getContainerId());
	    final URI uri = resource.getUri();
	    final int port = getPort(uri);
	    final String host = uri.getHost();
	    final String path = uri.getPath();
	    try {
	        final Socket socket = new Socket(host, port);
	        return new AttachedContainerStreams(socket, path, command.getContainerId(),
	                command.hasStdinEnabled(), command.hasStdoutEnabled(), command.hasStderrEnabled(),
	                command.isTty());
	    } catch (IOException e) {
	        // TODO: something roughly API-compliant
	        logger.error("unable to attach to container " + command.getContainerId(), e);
	        throw new RuntimeException("oh, foo", e);
	    }
	}
	    /*
		WebTarget webResource = getBaseResource().path("/containers/{id}/attach")
                .resolveTemplate("id", command.getContainerId())
                .queryParam("logs", command.hasLogsEnabled() ? "1" : "0")
                .queryParam("stdin", command.hasStdinEnabled() ? "1" : "0")
                .queryParam("stdout", command.hasStdoutEnabled() ? "1" : "0")
                .queryParam("stderr", command.hasStderrEnabled() ? "1" : "0")
                .queryParam("stream", command.hasFollowStreamEnabled() ? "1" : "0");

		LOGGER.trace("POST: {}", webResource);
		
		return webResource.request().accept(MediaType.APPLICATION_OCTET_STREAM_TYPE)
				.post(entity(null, MediaType.APPLICATION_JSON), Response.class).readEntity(InputStream.class);
				
	} */

}
