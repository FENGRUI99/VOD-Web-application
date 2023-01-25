public class RequestHeader{
    int statusCode;
    String fileName;
    long start;
    long length;

    public RequestHeader(int statusCode, String fileName, long start, long length){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
    }

    public int getStatusCode(){
        return statusCode;
    }
    public String getFileName(){
        return fileName;
    }
    public long getStart(){
        return start;
    }
    public long getLength(){
        return length;
    }

}