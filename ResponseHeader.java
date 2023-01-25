import java.util.Date;

public class ResponseHeader{
    int statusCode;
    String fileName;
    long start;
    long length;
    String type;
    Date lastModified;
    String md5;

    public ResponseHeader(int statusCode, String fileName, long start, long length, String type, Date lastModified, String md5){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.type = type;
        this.lastModified = lastModified;
        this.md5 = md5;
    }
}