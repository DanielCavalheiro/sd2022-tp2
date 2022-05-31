package tp2.impl.servers.rest;

import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import tp2.impl.clients.Clients;
import tp2.api.service.java.Files;
import tp2.api.service.java.Result;
import tp2.impl.servers.common.JavaFiles;
import tp2.impl.servers.dropbox.CreateDirectory;
import tp2.impl.servers.dropbox.DeleteDirectoryOrFile;
import tp2.impl.servers.dropbox.ListDirectory;
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

        ListDirectory ld = new ListDirectory();
        CreateDirectory cd = new CreateDirectory();
        DeleteDirectoryOrFile ddof = new DeleteDirectoryOrFile();

        if (!args[0].equals("true")) {

            try {
                ld.execute("/tmp");
            } catch (Exception e) {
                try {
                    cd.execute("/tmp");
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        } else {

            try {
                ddof.execute("/tmp");
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                cd.execute("/tmp");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        Debug.setLogLevel(Level.INFO, Debug.TP2);

        Token.set(args.length == 0 ? "" : args[0]);

        new DropboxRestServer(PORT).start();
    }

}
