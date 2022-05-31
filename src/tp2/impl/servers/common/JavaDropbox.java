package tp2.impl.servers.common;

import java.util.List;

import tp2.api.service.java.Files;
import tp2.api.service.java.Result;
import tp2.api.service.rest.RestFiles;
import tp2.impl.servers.dropbox.CreateDirectory;
import tp2.impl.servers.dropbox.DeleteDirectoryOrFile;
import tp2.impl.servers.dropbox.DownloadFile;
import tp2.impl.servers.dropbox.ListDirectory;
import tp2.impl.servers.dropbox.UploadFile;
import util.Hash;
import util.Token;

public class JavaDropbox implements Files {

    private DownloadFile download = new DownloadFile();
    private UploadFile upload = new UploadFile();
    private CreateDirectory cd = new CreateDirectory();
    private ListDirectory ld = new ListDirectory();
    private DeleteDirectoryOrFile ddof = new DeleteDirectoryOrFile();

    @Override
    public Result<byte[]> getFile(String fileId, String token) {
        String path = JavaFiles.ROOT + fileId.replace(JavaFiles.DELIMITER, "/");
        byte[] data;
        try {
            data = download.execute(path);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }

        return Result.ok(data);
    }

    @Override
    public Result<Void> deleteFile(String fileId, String token) {
        String path = JavaFiles.ROOT + fileId.replace(JavaFiles.DELIMITER, "/");
        try {
            ddof.execute(path);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok();
    }

    @Override
    public Result<Void> writeFile(String fileId, byte[] data, String token) {
        String parentPath = JavaFiles.ROOT + fileId.split("\\$\\$\\$")[0];
        String path = JavaFiles.ROOT + fileId.replace(JavaFiles.DELIMITER, "/");

        try {
            ld.execute(parentPath); // pelo que percebi isto devolve erro se nao encontrar nada
        } catch (Exception e) {

            try {
                cd.execute(parentPath);
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        }

        try {
            upload.execute(path, data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.ok();
    }

    @Override
    public Result<Void> deleteUserFiles(String userId, String token) {
        String path = JavaFiles.ROOT + userId;
        try {
            ddof.execute(path);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
        return Result.ok();
    }
}
