import java.util.Date;

public class ResponseHeader{
    int statusCode;
    String fileName;
    long start;
    long length;
    int sequence;
    long lastModified;
    String md5;

    public ResponseHeader(int statusCode, String fileName, long start, long length, int sequence, long lastModified, String md5){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.sequence = sequence;
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

    public int getSequence() {
        return sequence;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getMd5() {
        return md5;
    }

    @Override
    public String toString() {
        return "status code: " + statusCode + ", file name: " + fileName + ", start: " + start +
                ", length: " + length + ", sequence: " + sequence + ", lastModified: " + lastModified + ", md5: " + md5;
    }
}
