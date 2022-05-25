package tp2.impl.servers.soap;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.xml.ws.Endpoint;
import tp2.impl.discovery.Discovery;
import util.Token;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;

public class FilesSoapServer {

	public static final int PORT = 15678;
	public static final String SERVICE_NAME = "files";
	public static String SERVER_BASE_URI = "https://%s:%s/soap";

	private static Logger Log = Logger.getLogger(FilesSoapServer.class.getName());

	public static void main(String[] args) throws Exception {

		Token.set( args[0 ] );

//		System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true");
//		System.setProperty("com.sun.xml.internal.ws.transport.http.client.HttpTransportPipe.dump", "true");
//		System.setProperty("com.sun.xml.ws.transport.http.HttpAdapter.dump", "true");
//		System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dump", "true");

		Log.setLevel(Level.FINER);

		var ip = InetAddress.getLocalHost().getHostAddress();   
		var configurator = new HttpsConfigurator(SSLContext.getDefault());
		var server = HttpsServer.create(new InetSocketAddress(ip, PORT), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.setHttpsConfigurator(configurator);
		
		var endpoint = Endpoint.create(new SoapUsersWebService());		
		endpoint.publish(server.createContext("/soap"));
		
		server.start();

		String serverURI = String.format(SERVER_BASE_URI, ip, PORT);
		Discovery.getInstance().announce(SERVICE_NAME, serverURI);

		Log.info(String.format("%s Soap Server ready @ %s\n", SERVICE_NAME, serverURI));

	}
}
