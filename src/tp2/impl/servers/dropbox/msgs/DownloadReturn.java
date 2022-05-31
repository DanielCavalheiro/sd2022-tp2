package tp2.impl.servers.dropbox.msgs;

import java.util.HashMap;
import java.util.List;

public class DownloadReturn {

    private List<FileEntry> entries;

    public List<FileEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<FileEntry> entries) {
        this.entries = entries;
    }

    public static class FileEntry extends HashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        private static final String NAME = "name";

        public String toString() {
            return get(NAME).toString();
        }
    }
}
