import com.alibaba.fastjson.JSONObject;

import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BackEndTest {
    public static void main(String[] args) throws Exception{
        DatagramSocket dsock = new DatagramSocket(8080);
        int src = 0; // 0 for http server, 1 for peer back-end server
        InetAddress frontEndIp = InetAddress.getByName("127.0.0.1");
        int frontEndPort = 8080;
        InetAddress peerIp = InetAddress.getByName("172.16.7.16");
        int peerPort = 8081;
        String fileName = "content/test.ogg";
        long start = 0;
        long length = 4360399;
        int rate = 1600;
        String message = JSONObject.toJSONString(new ListenerHeader(src, frontEndIp, frontEndPort, peerIp, peerPort, fileName, start, length, rate));
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, frontEndIp, 8081);
        dsock.send(dpack);

        byte[] recArr = new byte[7202];
        dpack = new DatagramPacket(recArr, recArr.length);
//        dsock.receive(dpack);
//        System.out.println(getMD5Str(dpack.getData()));
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
