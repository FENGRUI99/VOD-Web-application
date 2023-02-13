import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
    HashMap<String, Integer> peerSeq; // peerName -> sequence
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

    public void start() throws Exception {
        DatagramSocket dsock = new DatagramSocket(Integer.parseInt(backEndPort));
        byte[] sendArr = new byte[2048];
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length);
        send(uuid, seq);
        while(true){
            dsock.receive(dpack);
            // 0:35 uuid
            // 36: 67 sequence
            // 68:  JSON
            byte[] recArr = dpack.getData();
            String uuid = new String(recArr, 0, 36);
            int sequence = Integer.valueOf(new String(recArr, 36, 32));
            // TODO 这里接收可能有问题
            JSONObject peerMap = JSONObject.parseObject(new String(recArr, 68, recArr.length - 68).trim());
            if (sequence > peerSeq.getOrDefault(uuid, -1)){
                peerSeq.put(uuid, sequence);
                routerMap.put(uuid, peerMap);
                send(uuid, sequence);
            }
        }
    }

    //接收的uuid自己or node 和seq
    public void send(String id, int sequence) throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        byte[] sendArr = routerMap.toJSONString().getBytes();
        DatagramPacket dpack;
        if(id == uuid){
            byte[] message = new byte[68+sendArr.length];
            id = id+sequence;
            System.arraycopy(id, 0, message, 0, id.length());
            System.arraycopy(sendArr, 0, message, 68, sendArr.length);
            sendArr = new byte[message.length];
            System.arraycopy(message, 0, message, 0, message.length);
            seq++;
        }

        for(int i = 0; i < peers.size(); i++){//1，3
            String[] peerInfo = peers.get(i).split(",");
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(peerInfo[1]), Integer.valueOf(peerInfo[3]));
            dsock.send(dpack);
        }

        dsock.close();
        //dpack.close();
    }

    public static void main(String[] args) {

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
