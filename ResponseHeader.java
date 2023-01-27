import java.util.Date;

public class ResponseHeader{
    int statusCode;
    String fileName;
    long start;
    long length;
    String type;
    long lastModified;
    String md5;

    public ResponseHeader(int statusCode, String fileName, long start, long length, String type, long lastModified, String md5){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.type = type;
        this.lastModified = lastModified;
        this.md5 = md5;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getFileName() {
        return fileName;
    }

    public long getStart() {
        return start;
    }

    public long getLength() {
        return length;
    }

    public String getType() {
        return type;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public String getMd5() {
        return md5;
    }
}
