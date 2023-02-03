public class frontHeader {
    String fileName;
    long fileSize;
    long start;
    long length;
    long lastModified;


    public frontHeader(String fileName, long fileSize, long start, long length, long lastModified) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.start = start;
        this.length = length;
        this.lastModified = lastModified;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getStart() {
        return start;
    }

    public long getLength() {
        return length;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public String toString() {
        return "frontHeader{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", start=" + start +
                ", length=" + length +
                ", lastModified=" + lastModified +
                '}';
    }
}
