package tp2.impl.servers.common;

import tp2.api.service.java.Files;
import tp2.api.service.java.Result;
import tp2.impl.servers.dropbox.CreateDirectory;
import tp2.impl.servers.dropbox.ListDirectory;
import tp2.impl.servers.dropbox.UploadFile;
import util.Hash;

public class JavaDropbox implements Files {

    private UploadFile upload = new UploadFile();
    private CreateDirectory cd = new CreateDirectory();
    private ListDirectory ld = new ListDirectory();

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
        String parentPath = "/" + fileId.split(JavaFiles.DELIMITER)[0];
        String path =  "/" + fileId.replace(JavaFiles.DELIMITER, "/");
        byte[] content_hash = Hash.digest(data);
        
        //TODO TOKEN

        try {
            ld.execute(parentPath); //pelo que percebi isto devolve erro se nao encontrar nada
        } catch (Exception e) {
        
            try {
                cd.execute(parentPath);
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        }
        
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
