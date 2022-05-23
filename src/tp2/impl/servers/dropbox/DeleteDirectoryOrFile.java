package tp2.impl.servers.dropbox;

import org.pac4j.scribe.builder.api.DropboxApi20;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import tp2.impl.servers.dropbox.msgs.DeleteDirectoryOrFileV2Args;

public class DeleteDirectoryOrFile{

    private static final String apiKey = "7xabutsco4qlge6";
	private static final String apiSecret = "zlc0lxwg809kpf6";
	private static final String accessTokenStr = "sl.BG5OzXOFmfe65AHd7c-GpTOOR6QdklkOODi-G4FLPYRoh8aG5wupRGeBJwcqdBhH_iyi7-ugSJh2Ck0xGCnU-f1hlMZeK9bbtnXvgyWDbt1uJTKqfk5AwTWW1hjQQR5zT9AA0OY";
	
	private static final String DELETE_URL = "https://api.dropboxapi.com/2/files/delete_v2";
	
	private static final int HTTP_SUCCESS = 200;
	private static final String CONTENT_TYPE_HDR = "Content-Type";
	private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
	
	private final Gson json;
	private final OAuth20Service service;
	private final OAuth2AccessToken accessToken;
		
	public DeleteDirectoryOrFile() {
		json = new Gson();
		accessToken = new OAuth2AccessToken(accessTokenStr);
		service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
	}
	
	public void execute( String directoryName ) throws Exception {
		
		var deleteDirectoryOrFile = new OAuthRequest(Verb.POST, DELETE_URL);
		deleteDirectoryOrFile.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);

		deleteDirectoryOrFile.setPayload(json.toJson(new DeleteDirectoryOrFileV2Args(directoryName, false)));

		service.signRequest(accessToken, deleteDirectoryOrFile);
		
		Response r = service.execute(deleteDirectoryOrFile);
		if (r.getCode() != HTTP_SUCCESS) 
			throw new RuntimeException(String.format("Failed to delete directory or file: %s, Status: %d, \nReason: %s\n", directoryName, r.getCode(), r.getBody()));
	}

}