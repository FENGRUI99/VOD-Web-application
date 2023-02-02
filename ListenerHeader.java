import java.net.InetAddress;

public class ListenerHeader {
    int src; // 0 for http server, 1 for peer back-end server
    InetAddress frontEndIp;
    int frontEndPort;
    InetAddress peerIp;
    int peerPort;
    String fileName;
    long start;
    long length;
    int rate;

    public ListenerHeader(int src){
        this.src = src;
        this.frontEndIp = null;
        this.frontEndPort = 0;
        this.peerIp = null;
        this.peerPort = 0;
        this.fileName = null;
        this.start = 0;
        this.length = 0;
        this.rate = 0;
    }

    public ListenerHeader(int src, InetAddress frontEndIp, int frontEndPort, InetAddress peerIp, int peerPort, String fileName, long start, long length, int rate){
        this.src = src;
        this.frontEndIp = frontEndIp;
        this.frontEndPort = frontEndPort;
        this.peerIp = peerIp;
        this.peerPort = peerPort;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.rate = rate;
    }

    public int getSrc() {
        return src;
    }

    public InetAddress getFrontEndIp() {
        return frontEndIp;
    }

    public int getFrontEndPort() {
        return frontEndPort;
    }

    public InetAddress getPeerIp() {
        return peerIp;
    }

    public int getPeerPort() {
        return peerPort;
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
    public int getRate() {
        return rate;
    }

    @Override
    public String toString() {
        return "src: " + src + ", frontEndIp: " + frontEndIp + ", frontEndPort: " + frontEndPort + ", PeerIp: " + peerIp + ", PeerPort: " + peerPort + ", fileName: " + fileName + ", start: " + start+ ", length: " + length + ", rate: " + rate;
    }
}
