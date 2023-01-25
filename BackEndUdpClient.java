//import java.io.*;
import java.net.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.function.BiConsumer;


public class BackEndUdpClient {

    public void requestFile() throws Exception{
        InetAddress add = InetAddress.getByName("127.0.0.1");
        DatagramSocket dsocket = new DatagramSocket( );
        String message1 = "test.png";
        byte[] arr = message1.getBytes();
        DatagramPacket dpacket = new DatagramPacket(arr, arr.length, add, 7077);
        dsocket.send(dpacket);                                   // send the packet

        arr = new byte[25];
        dpacket = new DatagramPacket(arr, arr.length, add, 7077);
        dsocket.receive(dpacket);                                // receive the packet

        String[] fileInfo = (new String(dpacket.getData( ))).split(":");

        int fileLen = Integer.valueOf(fileInfo[1].trim());
        byte[] arr1 = new byte[fileLen];
        dpacket = new DatagramPacket(arr1, fileLen, add, 7077);
        dsocket.receive(dpacket);
        System.out.println(dpacket.getLength());

    }
    public static void main(String[] args) throws Exception{
        BackEndUdpClient client = new BackEndUdpClient();
        client.requestFile();
    }
}
