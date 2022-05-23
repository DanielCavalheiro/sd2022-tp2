package tp2.impl.servers.dropbox.msgs;

public record DeleteDirectoryOrFileV2Args(String path, boolean autorename) {
}