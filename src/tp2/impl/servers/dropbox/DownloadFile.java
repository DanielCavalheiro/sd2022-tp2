package tp2.impl.servers.dropbox;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import tp2.impl.servers.dropbox.msgs.DownloadArgs;

public class DownloadFile {

    private static final String apiKey = "7xabutsco4qlge6";
    private static final String apiSecret = "zlc0lxwg809kpf6";
    private static final String accessTokenStr = "sl.BIpYksin8Qa_ItjA0zTL4lEvheHFI9x5H9xoevzQZEVJ2LOKSXzJBFvktDrtZgjuQWqSBAuchgaC4vxdznY9O0MdaBDxDSI8KofaRIMkB8df81QWZtM6L5SlkHPPgPNaFNvZhhU";

    private static final String DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download";

    private static final String CONTENT_TYPE_HDR = "Content-Type";
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

    private static final String DROPBOX_API_HDR = "Dropbox-API-Arg";

    private static final int HTTP_SUCCESS = 200;

    private final Gson json;
    private final OAuth20Service service;
    private final OAuth2AccessToken accessToken;

    public DownloadFile() {
        json = new Gson();
        accessToken = new OAuth2AccessToken(accessTokenStr);
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
    }

    public byte[] execute(String path) throws Exception {

        var download = new OAuthRequest(Verb.POST, DOWNLOAD_URL);
        download.addHeader(CONTENT_TYPE_HDR, OCTET_STREAM_CONTENT_TYPE);

        download.addHeader(DROPBOX_API_HDR,
                json.toJson(new DownloadArgs(path)));

        service.signRequest(accessToken, download);

        Response r = service.execute(download);

        if (r.getCode() != HTTP_SUCCESS)
            throw new RuntimeException(String.format("Failed to download file: %s, Status: %d, \nReason: %s\n", path,
                    r.getCode(), r.getBody()));

        return r.getStream().readAllBytes();
    }

}
