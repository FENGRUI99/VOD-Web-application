import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

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
    public Router(String configName){
        File configFile = new File(configName);
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
        send(this.uuid, seq);

        //Start asker thread periodic inquiring neighbor whether alive or not
        Asker asker = new Asker(peers, uuid, routerMap);
        asker.start();

        while(true){
            dsock.receive(dpack);
            // 0:35 uuid
            // 36: 67 sequence
            // 68:  JSON
            byte[] recArr = dpack.getData();
            String id = new String(recArr, 0, 36);
            int sequence = Integer.valueOf(new String(recArr, 36, 32).trim());
            //-1 keep Alive
            // TODO 这里接收可能有问题
            JSONObject peerMap = JSONObject.parseObject(new String(recArr, 68, recArr.length - 68).trim());
            if (sequence > peerSeq.getOrDefault(id, -1)){
                peerSeq.put(id, sequence);
                routerMap.put(id, peerMap);
                send(id, sequence);
            }else if(sequence == -1){
                replyAlive(id, dsock, dpack);
            }

            System.out.println("local router map size: " + routerMap.size());
//            for(String s : routerMap.keySet()){
//                System.out.println(routerMap.get(s));
//            }
            System.out.println(routerMap.toJSONString());
        }
    }

    public void replyAlive(String id, DatagramSocket dsock, DatagramPacket dpack) throws Exception{
        String header = id + "-1";
        String reply = "yes";
        byte[] sendArr = new byte[68+reply.length()];
        System.arraycopy(header.getBytes(), 0, sendArr, 0, header.length());
        System.arraycopy(reply.getBytes(), 68, sendArr, 0, reply.length());
        dpack.setData(sendArr);
        dsock.send(dpack);
    }
    public synchronized byte[] readRouterMap(){
        return routerMap.toJSONString().getBytes();
    }
    //发送自身路由表 或 转发peers路由表
    public void send(String id, int sequence) throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        DatagramPacket dpack;

        byte[] sendArr = readRouterMap();

        if(id == uuid){
            byte[] message = new byte[68+sendArr.length];
            String header = id+sequence;
            System.out.println("header string:" + header);
            System.arraycopy(header.getBytes(), 0, message, 0, header.length());

            System.arraycopy(sendArr, 0, message, 68, sendArr.length);

            sendArr = new byte[message.length];

            System.arraycopy(message, 0, sendArr, 0, message.length);
            System.out.println("sendArr string:" + new String(sendArr));
            seq++;
        }

        for(int i = 0; i < peers.size(); i++){//1，3
            String[] peerInfo = peers.get(i).split(",");
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(peerInfo[1]), Integer.valueOf(peerInfo[3]));
            dsock.send(dpack);
        }
    }

    public static void main(String[] args) throws Exception {
        Router router = new Router("routerConfig/" + args[0]);
//        System.out.println(router.routerMap.get("bbbee632-56b5-4a15-88ef-7bd3b7081141"));
        router.start();
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

class Asker extends Thread{
    List<String> peers;
    String uuid;
    JSONObject routerMap;
    HashMap<String, Integer> peerCount;
    HashMap<String, String> peerDistance;
    public Asker(List<String> peers, String uuid, JSONObject routerMap){
        this.peers = peers;
        this.uuid = uuid;
        this.routerMap = routerMap;
        this.peerCount = new HashMap<>();
        this.peerDistance = new HashMap<>();
    }
    public synchronized void modifyMap(){
        JSONObject localMap = routerMap.getJSONObject(uuid); //TODO 这句可能有问题
        for (String id : peerCount.keySet()){
            if (peerCount.get(id) <= 0 && localMap.containsKey(id)){
                localMap.remove(id);
            }
            else if (peerCount.get(id) > 0 && !localMap.containsKey(id)){
                localMap.put(id, peerDistance.get(id));
            }
        }
        routerMap.put(uuid, localMap);
    }
    public void startThread() throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        DatagramPacket dpack;
        dsock.setSoTimeout(1000);
        for (String peer : peers){
            String[] tmp = peer.split(",");
            peerCount.put(tmp[0], 0);
            peerDistance.put(tmp[0], tmp[4]);
        }
        while (true){
            long start = System.currentTimeMillis();
            for (String peer : peers){
                String[] tmp = peer.split(",");
                byte[] header = (uuid + "-1").getBytes();
                byte[] content = "alive".getBytes();
                byte[] sendArr = new byte[header.length + content.length];
                System.arraycopy(header, 0, sendArr, 0, header.length);
                System.arraycopy(content, 0, sendArr, header.length, content.length);
                dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(tmp[1]), Integer.valueOf(tmp[3]));
                dsock.send(dpack);
            }
            while (true){
                try{
                    byte[] recArr = new byte[2048];
                    dpack = new DatagramPacket(recArr, recArr.length);
                    dsock.receive(dpack);
                } catch (SocketTimeoutException e){
                    //TODO 怎么写，既要考虑node下线，也要考虑node上线
                    modifyMap(); //TODO 线程同步
                    break;
                }
                byte[] recArr = dpack.getData();
                String id = new String(recArr, 0, 36);
                int count = peerCount.get(id);
                peerCount.put(id, count - 1);
            }

            long end = System.currentTimeMillis();
            try {
                sleep(10 - (end - start) / 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    @Override
    public void run() {
        try{
            startThread();
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
