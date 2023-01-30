import com.alibaba.fastjson.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackEndServer {
    String port;
    public BackEndServer(String port){
        this.port = port;
    }
    public void startServer(){
        ExecutorService pool = Executors.newCachedThreadPool();
        while(true){
            //listener
        }

    }
}

class BackEndRequest extends Thread{
    //server initialization
    final int chunkSize = 1024;
    int windowSize = 2;
    //start：browser请求的文件的起始点； 假设browser请求文件从0开始，实际值要从前端获取
    //length：browser请求的文件的长度；  实际值要从前端获取
    int start;
    int length;
    InetAddress serverAdd;
    String fileName;

    public BackEndRequest(int start, int length, InetAddress serverAdd, String fileName){
        this.start = start;
        this.length = length;
        this.serverAdd = serverAdd;
        this.fileName = fileName;
    }
    @Override
    public void run(){
        try {startClient(); }
        catch (Exception e) {throw new RuntimeException(e); }

    }
    public void startClient() throws Exception {
        DatagramSocket dsocket = new DatagramSocket( );
        getFileInfo(fileName, serverAdd, dsocket);

        //initialization
        int fileSize = 0;
        int recePointer = 0;
        int receSize = 0;
        String fileName = null;
        HashMap<Integer, byte[]> fileMap = new HashMap<>();

        L1:
        while (true) {
            byte[] receiveArr = new byte[9000];
            DatagramPacket dpacket = new DatagramPacket(receiveArr, receiveArr.length, serverAdd, 7077);

            //AIMD过程
            try {
                dsocket.setSoTimeout(5000);
                dsocket.receive(dpacket);                           // receive the packet
            }catch (SocketTimeoutException e){ System.out.println("cut window");
                windowSize = Math.max(windowSize/2, 1);
                requestRange(fileName, serverAdd, dsocket, start, length);
                receSize = 0;
            }

            //从dpacket中获取header和content信息，分别存在header和content[]中
            byte[] info = dpacket.getData();
            int headerLen = convertByteToInt(info, 0);
            int contentLen = convertByteToInt(info, 4);
            ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());
            byte[] content = new byte[contentLen];
            for (int i = 0; i < content.length; i++) {
                content[i] = info[8 + headerLen + i];
            }

            //judge header
            if (header.statusCode == 0) {
                fileSize = (int) header.length;
                length = fileSize - start;
                fileName = header.fileName;
                requestRange(header.fileName, serverAdd, dsocket, start, length);
            }
            else if (header.statusCode == 1) {
                fileMap.put(header.sequence, content);
                while (fileMap.containsKey(recePointer)) {
                    recePointer++;
                    start += chunkSize;
                    length -= chunkSize;
                    if (length < 0) { //到达接受长度
                        close(header.fileName, serverAdd, dsocket);
                        break L1;
                    }
                    receSize++;
                }
                if (receSize == windowSize) {
                    requestRange(header.fileName, serverAdd, dsocket, start, length);
                    receSize = 0;
                    windowSize++;
                }
            }
            else{ //Not found
                break;
            }
        }
        System.out.println("end this transmission.");

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
        System.out.println("pointer: " + pointer);
        return file;
    }
    //发送相应报文
    public void getFileInfo(String fileName, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        byte[] header = getReqHeader(0, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        System.out.println("Status_0 send success");
    }
    public void requestRange(String fileName, InetAddress serverAdd, DatagramSocket dsocket, long start, long length) throws Exception{
        byte[] header = getReqHeader(1, fileName, start, length).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        System.out.println("Status_1 send success");
    }
    public void close (String fileName, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        byte[] header = getReqHeader(2, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        dsocket.close();
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

    int ip;
    DatagramSocket dsock = null;
    DatagramPacket dpack = null;
    final int chunkSize = 1024;
    int windowSize = 4;
    private DataInputStream in = null;
    int minIndex = 0;     //多client可能会有问题
    public BackEndResponse(int ip){
        this.ip = ip;
    }

    @Override
    public void run(){
        try {
            getServer(ip);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void getServer(int ip) throws Exception{
        dsock = new DatagramSocket(ip);
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
//    public static void main(String[] args) throws Exception{
//        FileInputStream fis = new FileInputStream(new File("./content/test.png"));
//        DataInputStream qqq = new DataInputStream(fis);
//        byte[] bytes = new byte[fis.available()];
//        qqq.read(bytes);
//        System.out.println(getMD5Str(bytes));
//        BackEndResponse server = new BackEndResponse();
//        server.getServer();
//
//    }
}