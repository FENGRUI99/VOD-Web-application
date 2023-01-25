
//import java.io.*;
import java.io.InputStream;
import java.net.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

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


    public static void main(String[] args) throws Exception{
        BackEndUdpServer server = new BackEndUdpServer();
        server.getServer();
    }
}

