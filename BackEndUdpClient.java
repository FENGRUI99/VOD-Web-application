//import java.io.*;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.net.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.Date;
import java.util.function.BiConsumer;


public class BackEndUdpClient {

    public void startClient() throws IOException {
        InetAddress serverAdd = InetAddress.getByName("127.0.0.1");
        DatagramSocket dsocket = new DatagramSocket( );
        try {
            getFileInfo("test.png", serverAdd, dsocket);
        } catch (Exception e){
            e.printStackTrace();
        }

        String header = getRequest(serverAdd, dsocket);

        System.out.println(header);
    }
    public String getRequest(InetAddress serverAdd, DatagramSocket dsocket) throws IOException {
        byte[] receiveArr = new byte[9000];
        DatagramPacket dpacket = new DatagramPacket(receiveArr, receiveArr.length, serverAdd, 7077);
        dsocket.receive(dpacket);                                // receive the packet
        byte[] info = dpacket.getData();

        int headerLen = convertByteToInt(info, 0);
        int contentLen = convertByteToInt(info, 4);
        ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
        return header.toString();
    }
    public void getFileInfo(String fileName, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        byte[] header = getReqHeader(0, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        System.out.println("success");

    }
    /*public void getRange(String fileName, long start, long length, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        InetAddress add = InetAddress.getByName("127.0.0.1");
        DatagramSocket dsocket = new DatagramSocket( );
        String message1 = "test.png";
        byte[] sendArr = message1.getBytes();
        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, add, 7078);
        dsocket.send(dpacket);                                   // send the packet

        sendArr = new byte[25];
        dpacket = new DatagramPacket(sendArr, sendArr.length, add, 7078);
        dsocket.receive(dpacket);                                // receive the packet

        String[] fileInfo = (new String(dpacket.getData( ))).split(":");

        int fileLen = Integer.valueOf(fileInfo[1].trim());
        byte[] recArr = new byte[fileLen];
        dpacket = new DatagramPacket(recArr, fileLen, add, 7078);
        dsocket.receive(dpacket);
        System.out.println(dpacket.getLength());
    }

    public void getRange(String fileName, InetAddress serverAdd, long start, long length) throws Exception{
        InetAddress add = InetAddress.getByName("127.0.0.1");
        DatagramSocket dsocket = new DatagramSocket( );
        String message1 = "test.png";
        byte[] sendArr = message1.getBytes();
        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, add, 7078);
        dsocket.send(dpacket);                                   // send the packet

        sendArr = new byte[25];
        dpacket = new DatagramPacket(sendArr, sendArr.length, add, 7078);
        dsocket.receive(dpacket);                                // receive the packet

        String[] fileInfo = (new String(dpacket.getData( ))).split(":");

        int fileLen = Integer.valueOf(fileInfo[1].trim());
        byte[] recArr = new byte[fileLen];
        dpacket = new DatagramPacket(recArr, fileLen, add, 7078);
        dsocket.receive(dpacket);
        System.out.println(dpacket.getLength());
    }*/
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
    public int convertByteToInt (byte[] bytes){
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8 ) |
                ((bytes[3] & 0xFF) << 0 );
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
        RequestHeader r = new RequestHeader(statusCode, fileName, start, length);
        return JSONObject.toJSONString(r);
    }
    public String getResHeader (int statusCode, String fileName, long start, long length, String type, long lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, type, lastModified, md5));
    }

    public static void main(String[] args) throws Exception{
        BackEndUdpClient client = new BackEndUdpClient();
        client.startClient();
    }
}
