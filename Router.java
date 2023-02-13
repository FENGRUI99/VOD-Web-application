import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;

public class Router {
    String frontEndPort;
    String backEndPort;
    String name;
    List<String> peers;
    String peerCount;
    String uuid;
    String content;
    int seq;
    HashMap<String, String> peerSeq; // peerName -> sequence
    JSONObject routerMap;
    public Router(){
        File configFile = new File("node.config");
        // Create a Properties object
        Properties configProperties = new Properties();
        //Read the properties file
        try (FileInputStream input = new FileInputStream(configFile)) {
            configProperties.load(input);
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }

        frontEndPort = configProperties.getProperty("frontend_port");
        backEndPort = configProperties.getProperty("backend_port");
        name = configProperties.getProperty("name");
        peerCount = configProperties.getProperty("peer_count");
        content = configProperties.getProperty("content_dir");
        peers = new ArrayList<String>();
        for(int i = 0; i < Integer.valueOf(peerCount); i++){
            peers.add(configProperties.getProperty("peer_" + i));
        }
        uuid = configProperties.getProperty("uuid");
        if(uuid.length() != 36){
            uuid = UUID.randomUUID().toString();
        }
        peerSeq = new HashMap<>();
        routerMap = new JSONObject();
        JSONObject localMap = new JSONObject();
        for (String peer : peers){
            String[] peerInfo = peer.split(",");
            localMap.put(peerInfo[0], peerInfo[4]);  // uuid -> distance
        }
        routerMap.put(uuid, localMap);
    }
    public void receive() throws Exception{
        DatagramSocket dsock = new DatagramSocket(Integer.parseInt(backEndPort));
        byte[] recArr = new byte[2048];
        DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
        while(true){
            dsock.receive(dpack);
            // TODO 这里接收可能有问题
            JSON.parse(dpack.getData());

        }
    }
    //接收的uuid自己or node 和seq
    public void send(String id, int sequence) throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        byte[] sendArr = routerMap.toJSONString().getBytes();
        if(id == uuid){
            byte[] message = new byte[68+sendArr.length];
            id = id+sequence;
            System.arraycopy(id, 0, message, 0, id.length());
            System.arraycopy(sendArr, 0, message, 68, sendArr.length);
        }

        for(int i = 0; i < peers.size(); i++){
            //DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, peerip, peerport);
            //dsock.send(dpack);
        }

        seq++;
    }

    public static void main(String[] args) {
        Router r = new Router();
        System.out.println(r.frontEndPort);
        System.out.println(r.backEndPort);
        System.out.println(r.name);
        System.out.println(r.peers.size());
        for(int i = 0; i < r.peers.size(); i++){
            System.out.println(r.peers.get(i));
        }
        System.out.println(r.peerCount);
        System.out.println(r.uuid);
        System.out.println(r.content);
    }
    // change the information in node.config
    private void setConfig(String fileName, String uuid, String name, int frontEndPort, int backEndPort, String contentDir, int peerCount, List<String> peers){
        File configFile = new File(fileName);
        Properties configProperties = new Properties();
        // Modify the properties
        configProperties.setProperty("uuid", uuid);
        configProperties.setProperty("name", name);
        configProperties.setProperty("frontend_port", ""+frontEndPort);
        configProperties.setProperty("backend_port", ""+backEndPort);
        configProperties.setProperty("content_dir", contentDir);
        configProperties.setProperty("peer_count", ""+peerCount);
        for(int i = 0; i < peerCount; i++){
            configProperties.setProperty("peer_"+i, peers.get(i));
        }
        // Write the properties back to the file
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            configProperties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
        }
    }
}
