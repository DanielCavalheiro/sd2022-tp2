package tp2.impl.servers.rest;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import tp2.impl.discovery.Discovery;

public abstract class AbstractRestServer {

	protected static String SERVER_BASE_URI = "https://%s:%s/rest";

	final int port;
	final String service;
	final private Logger Log;

	protected AbstractRestServer(Logger log, String service, int port) {
		this.service = service;
		this.port = port;
		this.Log = log;
	}

	protected void start() {
		try {

			String ip = InetAddress.getLocalHost().getHostAddress();
			String serverURI = String.format(SERVER_BASE_URI, ip, port);

			ResourceConfig config = new ResourceConfig();

			registerResources(config);

			System.err.println(">>>>>" + port);
			JdkHttpServerFactory.createHttpServer(URI.create(serverURI.replace(ip, "0.0.0.0")), config,
					SSLContext.getDefault());

			Log.info(String.format("%s Server ready @ %s\n", service, serverURI));

			Discovery.getInstance().announce(service, serverURI);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	abstract void registerResources(ResourceConfig config);

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}
}
