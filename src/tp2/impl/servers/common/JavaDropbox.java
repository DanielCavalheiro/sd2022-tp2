package tp2.impl.servers.common;

import tp2.api.service.java.Files;
import tp2.api.service.java.Result;
import tp2.impl.servers.dropbox.CreateDirectory;
import tp2.impl.servers.dropbox.UploadFile;
import util.Hash;

public class JavaDropbox implements Files {

    UploadFile upload = new UploadFile();
    CreateDirectory cd = new CreateDirectory();

    @Override
    public Result<byte[]> getFile(String fileId, String token) {
        return null;
    }

    @Override
    public Result<Void> deleteFile(String fileId, String token) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result<Void> writeFile(String fileId, byte[] data, String token) {
        String path = fileId.replace(JavaFiles.DELIMITER, "/");
        byte[] content_hash = Hash.digest(data);
        try {
            upload.execute(path, content_hash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.ok();
    }

    @Override
    public Result<Void> deleteUserFiles(String userId, String token) {
        // TODO Auto-generated method stub
        return null;
    }

}
