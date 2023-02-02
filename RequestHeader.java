public class RequestHeader{
    int statusCode;
    String fileName;
    long start;
    long length;

    int RTT;

    public RequestHeader(int statusCode, String fileName, long start, long length, int RTT){
        this.statusCode = statusCode;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.RTT = RTT;
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

    public int getRTT() {
        return RTT;
    }

    @Override
    public String toString() {
        return "status code: " + statusCode + ", file name: " + fileName + ", start: " + start + ", length: " + length + ", RTT: ";
    }
}