
//import java.io.*;
import com.alibaba.fastjson.JSONObject;

import java.io.InputStream;
import java.net.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;

public class BackEndUdpServer {
    DatagramSocket dsocket = null;
    DatagramPacket dpacket = null;
    public void getServer() throws Exception{
        dsocket = new DatagramSocket(7077);
        byte[] arr1 = new byte[150];
        dpacket = new DatagramPacket(arr1, arr1.length);
        while (true){
            dsocket.receive(dpacket);
            byte[] request = dpacket.getData();
            int len = dpacket.getLength();
            String fileName = new String(request, 0, len);

            File f = new File("./content/" + fileName);
            InputStream fis = new FileInputStream(f);
            byte[] b = new byte[fis.available()];
            fis.read(b);
            fis.close();

            byte[] fileInfo = (fileName + ":" + b.length).getBytes();
            DatagramPacket dp = new DatagramPacket(fileInfo,0, fileInfo.length, dpacket.getAddress(), dpacket.getPort());
            dsocket.send(dp);

            dp = new DatagramPacket(b,0, b.length, dpacket.getAddress(), dpacket.getPort());
            dsocket.send(dp);
            System.out.println("Successful");
        }
    }

    public String getReqHeader (int statusCode, String fileName, long start, long length){
        return JSONObject.toJSONString(new RequestHeader(statusCode, fileName, start, length));
    }

    public String getResHeader (int statusCode, String fileName, long start, long length, String type, Date lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, type, lastModified, md5));
    }

    public static void main(String[] args) throws Exception{
        BackEndUdpServer server = new BackEndUdpServer();
    }

    class RequestHeader{
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
    }

    class ResponseHeader{
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

}

