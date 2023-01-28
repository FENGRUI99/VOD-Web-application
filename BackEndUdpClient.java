//import java.io.*;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.net.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.function.BiConsumer;


public class BackEndUdpClient {

    public void startClient() throws Exception {
        InetAddress serverAdd = InetAddress.getByName("127.0.0.1");
        DatagramSocket dsocket = new DatagramSocket( );
        try {
            getFileInfo("test.png", serverAdd, dsocket);
        } catch (Exception e){
            e.printStackTrace();
        }

        ResponseHeader header = getRequest(serverAdd, dsocket);
        System.out.println(header.toString());

        HashMap<Integer, DatagramPacket> store = new HashMap<>();
        if(header.statusCode==0){

        }
        else if(header.statusCode==1){
//            if(){
//
//            }
//            else{ //到达接受长度
//                close(header.fileName, serverAdd, dsocket);
//            }

        }else{ //Not found

        }

    }
    public ResponseHeader getRequest(InetAddress serverAdd, DatagramSocket dsocket) throws Exception {
        byte[] receiveArr = new byte[9000];
        DatagramPacket dpacket = new DatagramPacket(receiveArr, receiveArr.length, serverAdd, 7077);
        dsocket.receive(dpacket);                                // receive the packet
        byte[] info = dpacket.getData();

        int headerLen = convertByteToInt(info, 0);
        int contentLen = convertByteToInt(info, 4);
        ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
        return header;
    }
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
