import com.alibaba.fastjson.JSONObject;
import org.w3c.dom.CharacterData;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackEndServer {
    int port;
    public BackEndServer(int port){
        this.port = port;
    }
    public void startServer() throws Exception{
        ExecutorService pool = Executors.newCachedThreadPool();
        DatagramSocket dsock = new DatagramSocket(port);
        while(true){
            //listener
            byte[] recArr = new byte[2048];
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            dsock.receive(dpack);
            ListenerHeader header = JSONObject.parseObject(new String(recArr), ListenerHeader.class);
            System.out.println(header.toString());
            // message from http server
            if (header.getSrc() == 0){
                pool.execute(new BackEndRequest(header.frontEndIp, header.frontEndPort, header.peerIp, header.peerPort, header.fileName, header.start, header.length));
            }
            // message from peer back-end server
            else {
                pool.execute(new BackEndResponse(dpack.getAddress(), dpack.getPort()));
            }
        }
    }
    public static void main(String[] args) throws Exception{
        BackEndServer server = new BackEndServer(8081);
        server.startServer();
    }
}

class BackEndRequest extends Thread{
    //server initialization
    final int chunkSize = 1024;
    int windowSize = 2;
    //start：browser请求的文件的起始点； 假设browser请求文件从0开始，实际值要从前端获取
    //length：browser请求的文件的长度；  实际值要从前端获取
    DatagramSocket dsock;
    DatagramPacket dpack;
    long start;
    long length;
    InetAddress frontEndAddress;
    int frontEndPort;
    InetAddress peerListenAddress;
    int peerListenPort;
    String fileName;

    public BackEndRequest(InetAddress frontEndAddress, int frontEndPort, InetAddress peerListenAddress, int peerListenPort, String fileName, long start, long length){
        this.frontEndAddress = frontEndAddress;
        this.frontEndPort = frontEndPort;
        this.peerListenAddress = peerListenAddress;
        this.peerListenPort = peerListenPort;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
    }
    @Override
    public void run(){
        try {startRequest(); }
        catch (Exception e) {throw new RuntimeException(e); }
    }
    public void startRequest() throws Exception {
        dsock = new DatagramSocket();
        dsock.setSoTimeout(5000);
        // say hello to Peer listener thread
        System.out.println("say hello to Peer listener thread");
        String message = JSONObject.toJSONString(new ListenerHeader(1));
        byte[] sendArr = message.getBytes();
        dpack = new DatagramPacket(sendArr, sendArr.length, peerListenAddress, peerListenPort);
        dsock.send(dpack);

        // wait for hello from Peer response thread
        byte[] recArr = new byte[1024];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        InetAddress peerResAddress = dpack.getAddress();
        int peerResPort = dpack.getPort();
        System.out.println(new String(dpack.getData()));
        getFileInfo(peerResAddress, peerResPort);

        //initialization
        long fileLen = length;
        int recePointer = 0;
        int receSize = 0;
        HashMap<Integer, byte[]> fileMap = new HashMap<>();

        L1:
        while (true) {
            recArr = new byte[20480];
            dpack.setData(recArr, 0, recArr.length);
            //AIMD过程
            try {
                dsock.receive(dpack);                           // receive the packet
            }catch (SocketTimeoutException e){
                System.out.println("cut window");
                windowSize = Math.max(windowSize/2, 1);
                requestRange();
                receSize = 0;
                //TODO CFR觉得这里有问题
            }

            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] info = dpack.getData();
            int headerLen = convertByteToInt(info, 0);
            int contentLen = convertByteToInt(info, 4);
            ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());

            //judge header
            if (header.statusCode == 0) {
                requestRange();
            }
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(info, 8 + headerLen, content, 0, contentLen);
                fileMap.put(header.sequence, content);
                while (fileMap.containsKey(recePointer)) {
                    recePointer++;
                    start += chunkSize;
                    fileLen -= chunkSize;
                    if (fileLen < 0) { //到达接受长度
                        close();
                        break L1;
                    }
                    receSize++;
                }
                if (receSize == windowSize) {
                    requestRange();
                    receSize = 0;
                    windowSize++;
                }
            }
            else{ //Not found
                break;
            }
        }
        System.out.println("end this transmission.");
        System.out.println(length);
        byte[] file = map2File(fileMap, 102400);
        System.out.println("md5: " + getMD5Str(file));


        //测试用：将接收文件的map转存为byte数组求md5，将文件保存到本地。
        /*
        byte[] file = map2File(fileMap, fileSize);
        System.out.println("md5: " + getMD5Str(file));
        DataOutputStream sOut = new DataOutputStream(new FileOutputStream(new File("./content/"+"test1.png")));
        sOut.write(file, 0, file.length);
        sOut.flush();
        sOut.close();
        */
    }
    public byte[] map2File(HashMap<Integer, byte[]> map, int fileSize){
        byte[] file = new byte[fileSize];
        int pointer = 0;
        int mapSize = map.size();
        for(int i = 0; i < mapSize; i++){
            for(int j = 0; j < map.get(i).length; j++){
                file[pointer] = map.get(i)[j];
                pointer++;
            }
        }
        System.out.println(pointer);
        return file;
    }
    //发送相应报文
    public void getFileInfo(InetAddress peerResAddress, int peerResPort) throws Exception{
        byte[] header = getReqHeader(0, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);
        dpack = new DatagramPacket(sendArr, sendArr.length, peerResAddress, peerResPort);
        dsock.send(dpack);
        System.out.println("Status_0 send success");
    }
    public void requestRange() throws Exception{
        byte[] header = getReqHeader(1, fileName, start, length).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        dpack.setData(sendArr, 0, sendArr.length);
        dsock.send(dpack);
        System.out.println("Status_1 send success");
    }
    public void close () throws Exception{
        byte[] header = getReqHeader(2, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        dpack.setData(sendArr, 0, sendArr.length);
        dsock.send(dpack);
        dsock.close();
        System.out.println("Status_2 send success");
    }

    public byte[] getPreHeader(int headerLen, int contentLen){
        byte[] headerLenBytes = convertIntToByte(headerLen);
        byte[] contentLenBytes = convertIntToByte(contentLen);
        byte[] res = addTwoBytes(headerLenBytes, contentLenBytes);
        return res;
    }
    public byte[] addTwoBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }
    public int convertByteToInt(byte[] bytes, int start){
        return ((bytes[start + 0] & 0xFF) << 24) |
                ((bytes[start + 1] & 0xFF) << 16) |
                ((bytes[start + 2] & 0xFF) << 8 ) |
                ((bytes[start + 3] & 0xFF) << 0 );
    }
    public byte[] convertIntToByte(int value){
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value };
    }
    public String getReqHeader (int statusCode, String fileName, long start, long length){
        return JSONObject.toJSONString(new RequestHeader(statusCode, fileName, start, length));
    }
    public static String getMD5Str(byte[] digest) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            digest  = md5.digest(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //16是表示转换为16进制数
        String md5Str = new BigInteger(1, digest).toString(16);
        return md5Str;
    }
}


class BackEndResponse extends Thread{
    InetAddress peerIp;
    int peerPort;
    DatagramSocket dsock = null;
    DatagramPacket dpack = null;
    final int chunkSize = 1024;
    int windowSize = 4;
    private DataInputStream in = null;
    int minIndex = 0;     //多client可能会有问题
    public BackEndResponse(InetAddress peerIp, int peerPort){
        this.peerIp = peerIp;
        this.peerPort = peerPort;
    }
    @Override
    public void run(){
        try {
            startResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void startResponse() throws Exception{
        dsock = new DatagramSocket();
        byte[] message = "hello".getBytes();
        dpack = new DatagramPacket(message, message.length, peerIp, peerPort);
        dsock.send(dpack);
//        dsock.setSoTimeout(5 * 1000);
        while (true){
            // receive request
            RequestHeader header = waitRequest();
            File f = new File("./content/" + header.fileName);
            // get info
            if (header.statusCode == 0 && f.exists()){
                sendInfo(f);
            }
            else if (!f.exists()){
                // 404
            }
            // get range of file
            else if (header.statusCode == 1){
                sendRange(header);
            }
            // close
            else {
                // status code == 2 -> finish
                System.out.println("Close");
            }
        }
    }
    public RequestHeader waitRequest() throws Exception{
        byte[] recArr = new byte[20480];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        System.out.println("Client ip: " + dpack.getAddress() + ", port: " + dpack.getPort());
        recArr = dpack.getData();
        //preheader
        int headerLen = convertByteToInt(recArr, 0);    //recArr[0:3]
        //request header
        RequestHeader header = JSONObject.parseObject(new String(recArr, 8, 8 + headerLen), RequestHeader.class);
        System.out.println(header.toString());
        return header;
    }
    public void sendInfo(File f) throws Exception{
        String resHeader = getResHeader(0, f.getName(), 0, f.length(), -1, f.lastModified(), "");
        byte[] preheader = getPreHeader(resHeader.length(), 0);
        byte[] sendArr = addTwoBytes(preheader, resHeader.getBytes());
        dpack.setData(sendArr);
        dsock.send(dpack);

        FileInputStream fis = new FileInputStream(f);
        in = new DataInputStream(fis);
        sendArr = new byte[fis.available()];
        in.read(sendArr);
        in.close();
        System.out.println(getMD5Str(sendArr));
    }
    public void sendRange(RequestHeader header) throws Exception{
        boolean flag = false;
        long start = header.start;
        long end = start + header.length;
        int startSeq = (int) (start / chunkSize);
        int startNumber = 0;
        if (startSeq > minIndex) windowSize++;
        else if (startSeq <= minIndex) {
            windowSize = Math.max(windowSize / 2, 1);
            minIndex = startSeq;
        }
        File f = new File("./content/" + header.fileName);
        FileInputStream fis = new FileInputStream(f);
        in = new DataInputStream(fis);
        in.skip(start);
        while (startNumber < windowSize){
            byte[] sendArr;
            byte[] preheader;
            String resHeader;
            int contentSize;
            if (start + chunkSize * (startNumber + 1) <= end){
                contentSize = chunkSize;
            }
            else {
                contentSize = (int) (end - start - chunkSize * startNumber);
                flag = true;
            }
            resHeader = getResHeader(1, header.fileName, start + chunkSize * startNumber, contentSize, startSeq, 0, "");
            preheader = getPreHeader(resHeader.length(), contentSize);
            sendArr = new byte[8 + resHeader.length() + contentSize];
            in.read(sendArr, 8 + resHeader.length(), contentSize);
            System.arraycopy(preheader, 0, sendArr, 0, preheader.length);
            System.arraycopy(resHeader.getBytes(), 0, sendArr, 8, resHeader.length());
            dpack.setData(sendArr);
            dsock.send(dpack);
            startNumber++;
            startSeq++;
            if (flag) break;
        }
        in.close();
    }
    public byte[] getPreHeader(int headerLen, int contentLen){
        byte[] headerLenBytes = convertIntToByte(headerLen);
        byte[] contentLenBytes = convertIntToByte(contentLen);
        byte[] res = addTwoBytes(headerLenBytes, contentLenBytes);
        return res;
    }
    public byte[] addTwoBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }
    public int convertByteToInt(byte[] bytes, int start){
        return ((bytes[start + 0] & 0xFF) << 24) |
                ((bytes[start + 1] & 0xFF) << 16) |
                ((bytes[start + 2] & 0xFF) << 8 ) |
                ((bytes[start + 3] & 0xFF) << 0 );
    }
    public byte[] convertIntToByte(int value){
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value };
    }
    public String getResHeader (int statusCode, String fileName, long start, long length, int sequence, long lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, sequence, lastModified, md5));
    }
    public static String getMD5Str(byte[] digest) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            digest  = md5.digest(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String md5Str = new BigInteger(1, digest).toString(16);
        return md5Str;
    }
}