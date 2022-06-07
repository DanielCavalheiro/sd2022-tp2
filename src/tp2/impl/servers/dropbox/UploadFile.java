package tp2.impl.servers.dropbox;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import tp2.impl.servers.dropbox.msgs.UploadArgs;

public class UploadFile {

	private static final String apiKey = "7xabutsco4qlge6";
	private static final String apiSecret = "zlc0lxwg809kpf6";
	private static final String accessTokenStr = "sl.BJHimO8j5roSf6bIawfn1Q8vYEAViFk8Lt5tFcjZ0V96_vAGC-3bxvczbRG5tvgvvNSBB3-TlZKpFw0YYTE0wSj4oZYNt5Uj6J9udbZAZs2Q6tduv9uwDDeN9M72dM5jgUZgtgw";

	private static final String UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload";
	private static final String WRITE_MODE = "overwrite";

	private static final int HTTP_SUCCESS = 200;

	private static final String CONTENT_TYPE_HDR = "Content-Type";
	private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

	private static final String DROPBOX_API_HDR = "Dropbox-API-Arg";

	private final Gson json;
	private final OAuth20Service service;
	private final OAuth2AccessToken accessToken;

	public UploadFile() {
		json = new Gson();
		accessToken = new OAuth2AccessToken(accessTokenStr);
		service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
	}

	public void execute(String path, byte[] data) throws Exception {

		var upload = new OAuthRequest(Verb.POST, UPLOAD_URL);
		upload.addHeader(CONTENT_TYPE_HDR, OCTET_STREAM_CONTENT_TYPE);
		upload.addHeader(DROPBOX_API_HDR,
				json.toJson(new UploadArgs(path, WRITE_MODE, false, false, false)));

		upload.setPayload(data);

		service.signRequest(accessToken, upload);

		Response r = service.execute(upload);
		if (r.getCode() != HTTP_SUCCESS)
			throw new RuntimeException(String.format("Failed to upload file: %s, Status: %d, \nReason: %s\n", path,
					r.getCode(), r.getBody()));
	}

}
