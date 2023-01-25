//import java.io.*;
import com.alibaba.fastjson.JSONObject;
import java.io.InputStream;
import java.net.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;

public class BackEndUdpServer {
    DatagramSocket dscok = null;
    DatagramPacket dpack = null;
    public void getServer() throws Exception{
        dscok = new DatagramSocket(7077);
        byte[] arr1 = new byte[150];
        dpack = new DatagramPacket(arr1, arr1.length);
        while (true){
            dscok.receive(dpack);
            byte[] request = dpack.getData();
            int len = dpack.getLength();
            String fileName = new String(request, 0, len);

            File f = new File("./content/" + fileName);
            InputStream fis = new FileInputStream(f);
            byte[] b = new byte[fis.available()];
            fis.read(b);
            fis.close();

            byte[] fileInfo = (fileName + ":" + b.length).getBytes();
            DatagramPacket dp = new DatagramPacket(fileInfo,0, fileInfo.length, dpack.getAddress(), dpack.getPort());
            dscok.send(dp);

            dp = new DatagramPacket(b,0, b.length, dpack.getAddress(), dpack.getPort());
            dscok.send(dp);
            System.out.println("Successful");
        }
    }

    public String getReqHeader (int statusCode, String fileName, long start, long length){
        RequestHeader req = new RequestHeader(statusCode, fileName, start, length);
        return JSONObject.toJSONString(req);
    }

    public String getResHeader (int statusCode, String fileName, long start, long length, String type, Date lastModified, String md5){
        ResponseHeader resp = new ResponseHeader(statusCode, fileName, start, length, type, lastModified, md5);
        return JSONObject.toJSONString(resp);
    }

    public byte[] preHeader (String JSON, int byteLen){
        int len = JSON.getBytes().length;
        String s = String.format("%0" + byteLen + "d",len);
        byte[] header = s.getBytes();
        return header;
    }

    public static void main(String[] args) throws Exception{
        BackEndUdpServer server = new BackEndUdpServer();
        String s = server.getReqHeader(0, "test.png", 0, 1000);
        //System.out.println(s);
        RequestHeader r = JSONObject.parseObject(s, RequestHeader.class);
        //System.out.println(r);

        //test for preHeader
        byte[] a = server.preHeader("fhhdskhjfdfh",4);
        System.out.println(new String(a));

    }



}

