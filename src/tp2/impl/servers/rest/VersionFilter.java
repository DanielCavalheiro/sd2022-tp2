package tp2.impl.servers.rest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import tp2.api.service.rest.RestDirectory;
import tp2.impl.kafka.sync.SyncPoint;

@Provider
public class VersionFilter implements ContainerResponseFilter {
    SyncPoint syncPoint;

    VersionFilter(SyncPoint syncPoint) {
        this.syncPoint = syncPoint;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        String csv = syncPoint.toString().substring(1, syncPoint.toString().length() - 1).replace(" ", "");
        String[] values = csv.split(",");
        int value = 0;
        if (!csv.isEmpty()) {
            for (int i = 0; i < values.length; i++) {
                if (Integer.parseInt(values[i]) > value)
                    value = Integer.parseInt(values[i]);
            }
        }

        responseContext.getHeaders().add(RestDirectory.HEADER_VERSION, value);
    }

}
