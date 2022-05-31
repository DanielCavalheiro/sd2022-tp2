package tp2.impl.servers.dropbox.msgs;

public record UploadArgs(String path, String mode, boolean autorename, boolean mute, boolean strict_conflict) {
}