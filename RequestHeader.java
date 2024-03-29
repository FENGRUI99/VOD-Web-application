public class RequestHeader{
    int statusCode;
    String fileName;
    long start;
    long length;
    int chunkSize;

    public RequestHeader(int statusCode, String fileName, long start, long length, int chunkSize){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.chunkSize = chunkSize;
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

    public int getChunkSize() {
        return chunkSize;
    }

    @Override
    public String toString() {
        return "status code: " + statusCode + ", file name: " + fileName + ", start: " + start + ", length: " + length + ", chunkSize: " +chunkSize;
    }
}