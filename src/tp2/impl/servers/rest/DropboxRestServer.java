package tp2.impl.servers.rest;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tp2.api.service.java.Files;
import tp2.impl.servers.rest.util.GenericExceptionMapper;
import util.Debug;
import util.Token;

public class DropboxRestServer extends AbstractRestServer {

    public static final int PORT = 6789;

    private static Logger Log = Logger.getLogger(DropboxRestServer.class.getName());

    DropboxRestServer(int port) {
        super(Log, Files.SERVICE_NAME, port);
    }

    @Override
    void registerResources(ResourceConfig config) {
        config.register(DropboxResources.class);
        config.register(GenericExceptionMapper.class);
        // config.register( CustomLoggingFilter.class);
    }

    public static void main(String[] args) throws Exception {

        Debug.setLogLevel(Level.INFO, Debug.TP2);

        Token.set(args.length == 0 ? "" : args[0]);

        new DropboxRestServer(PORT).start();
    }

}
