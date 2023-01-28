
import com.alibaba.fastjson.JSONObject;

import java.net.*;
import java.io.File;

public class BackEndUdpServer {
    DatagramSocket dsock = null;
    DatagramPacket dpack = null;
    final int chunkSize = 1024;
    public void getServer() throws Exception{
        dsock = new DatagramSocket(7077);
        while (true){
            // receive request
            byte[] recArr = new byte[20480];
            dpack = new DatagramPacket(recArr, recArr.length);
            dsock.receive(dpack);
            System.out.println("Client ip: " + dpack.getAddress() + ", port: " + dpack.getPort());
            recArr = dpack.getData();

            //preheader
            int headerLen = convertByteToInt(recArr, 0);    //recArr[0:3]
            int contentLen = convertByteToInt(recArr, 4); //recArr[4:7]

            //request header
            RequestHeader header = JSONObject.parseObject(new String(recArr, 8, 8 + headerLen), RequestHeader.class);
            System.out.println(header.toString());
            if (header.statusCode == 0){
                String fileName = header.fileName;
                File f = new File("./content/" + fileName);
                // send file info
                if (f.exists()){
                    String resHeader = getResHeader(0, fileName, 0, f.length(), -1, f.lastModified(), "");
                    byte[] preheader = getPreHeader(resHeader.length(), 0);
                    byte[] sendArr = addTwoBytes(preheader, resHeader.getBytes());
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                    System.out.println("send successful");
                }
                else {
                    //404
                }
            }
            else if (header.statusCode == 1){
//                // header
//                String resHeader = getResHeader(0, fileName, 0, f.length(), URLConnection.guessContentTypeFromName(f.getName()), f.lastModified(), "");
//                InputStream fis = new FileInputStream(f);
//                byte[] sendArr = new byte[8 + resHeader.length() + fis.available()];
//
//                //preheader
//                byte[] preheader = preHeader(resHeader.length(), fis.available());
//                fis.read(sendArr, 8 + resHeader.length(), fis.available());
//                fis.close();
//
//                System.arraycopy(data1, 0, data3, 0, data1.length);
//                System.arraycopy(data1, 0, data3, 0, data1.length);
//
//                //receive ACK
//                recArr = new byte[3];
//                dpack.setData(recArr, 0, recArr.length);
//                dsock.receive(dpack);
//                if (new String(dpack.getData()).equals("ACK")){
//                    // send file info
//                    sendArr = addBytes(resHeader.getBytes(), fileArr);
//                    dpack.setData(sendArr);
//                    dsock.send(dpack);
//                }
            }
            else {

            }

        }
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
    public String getResHeader (int statusCode, String fileName, long start, long length, int sequence, long lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, sequence, lastModified, md5));
    }
    public static void main(String[] args) throws Exception{
        BackEndUdpServer server = new BackEndUdpServer();
        server.getServer();
    }
}