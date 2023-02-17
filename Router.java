import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static java.lang.Thread.sleep;

public class Router {
    String frontEndPort;
    String backEndPort;
    String name;
    List<String> peers;
    String peerCount;
    String uuid;
    String content;
    Map<String, Object> peerSeq; // peerName -> sequence
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
        peerSeq.put(uuid, 0);
        routerMap.put(uuid, localMap);
//        System.out.println("本地map初始化: " + routerMap.toJSONString());
    }

    public void start() throws Exception {
        //System.out.println(routerMap.toJSONString());
        //test();
        DatagramSocket dsock = new DatagramSocket(Integer.parseInt(backEndPort));
        byte[] sendArr;
        DatagramPacket dpack;

        //Start asker thread periodic inquiring neighbor whether alive or not
        Asker asker = new Asker(peers, uuid, peerSeq, routerMap);
        asker.start();

        while(true){
            sendArr = new byte[2048];
            dpack = new DatagramPacket(sendArr, sendArr.length);
            dsock.receive(dpack);
            // 0:35 uuid
            // 36-41 router
            // 42: 73 sequence
            // 74:  JSON
            byte[] recArr = dpack.getData();
//            System.out.println("receive data: " + new String(recArr));
            String id = new String(recArr, 0, 36);
            //TODO 36-41是router
            int sequence = Integer.valueOf(new String(recArr, 42, 32).trim());
//            System.out.println("######receive data: " + new String(recArr));
            //-1 keep Alive
            if (sequence > getPeerSeq(id)){
                JSONObject peerMap = JSONObject.parseObject(new String(recArr, 74, recArr.length - 74).trim());
//                System.out.println("######receive data: " + new String(recArr));

                // neighbor's peerSeq
                Map<String, Object> tmp = (Map<String, Object>) peerMap.get("seq"); //TODO 这里可能有问题
                for (String s : tmp.keySet()){
                    if ((int)tmp.get(s) > (int)peerSeq.getOrDefault(s, -1)){
                        //更新路由表
                        if (peerMap.containsKey(s)){
                            peerSeq.put(s, tmp.get(s));
                            routerMap.put(s, peerMap.get(s)); //TODO 这里可能有问题
                        }
                        //删除路由表
                        else {
                            peerSeq.put(s, -1);
                            if (routerMap.containsKey(s)) routerMap.remove(s);
                        }
                    }
                }
                System.out.println("########接收并更新路由表: " + routerMap.toJSONString());
                send(id, sequence, recArr);
            }else if(sequence == -1){
                replyAlive(uuid, dsock, dpack);
            }

//            System.out.println("local router map size: " + routerMap.size());
//            System.out.println(routerMap.toJSONString());
        }
    }

    public void replyAlive(String id, DatagramSocket dsock, DatagramPacket dpack) throws Exception{
        String header = uuid + "router-1";
        String reply = "yes";
        byte[] sendArr = new byte[74+reply.length()];
        System.arraycopy(header.getBytes(), 0, sendArr, 0, header.length());
        System.arraycopy(reply.getBytes(), 0, sendArr, 74, reply.length());
        dpack.setData(sendArr);
        dsock.send(dpack);
    }
    public synchronized byte[] readRouterMap(){
        routerMap.put("seq", new JSONObject(peerSeq));
        byte[] res =  routerMap.toJSONString().getBytes();
        routerMap.remove("seq");
        return res;
    }

    public synchronized int getPeerSeq(String id){
        return (int) peerSeq.getOrDefault(id, -1);
    }
    //发送自身路由表 或 转发peers路由表
    public void send(String id, int sequence, byte[] recArr) throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        DatagramPacket dpack;
        byte[] sendArr;

        if(id == uuid){
            sendArr = readRouterMap();
            byte[] message = new byte[74+sendArr.length];
            String header = id+"router"+sequence;
            System.arraycopy(header.getBytes(), 0, message, 0, header.length());
            System.arraycopy(sendArr, 0, message, 74, sendArr.length);
            sendArr = new byte[message.length];
            System.arraycopy(message, 0, sendArr, 0, message.length);
        }else{
            sendArr = new byte[recArr.length];
            System.arraycopy(recArr, 0, sendArr, 0, recArr.length);
        }
        for(int i = 0; i < peers.size(); i++){//1，3
            String[] peerInfo = peers.get(i).split(",");
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(peerInfo[1]), Integer.valueOf(peerInfo[3]));
            dsock.send(dpack);
        }
    }
    public void test(){
        try {
            // Read the entire file into a string
            String jsonString = new String(Files.readAllBytes(Paths.get("dTest.json")));
            // Parse the string into a JSONObject
            JSONObject testMap = JSONObject.parseObject(jsonString);
            HashMap<String, Integer> verticeSeq = new HashMap<>();
            int sequence = 0;
            for(String s : testMap.keySet()){
                verticeSeq.put(s, sequence);
                sequence++;
            }

            int numVertices = testMap.keySet().size();
            int[][] graph = new int[numVertices][numVertices];

            for(String s : testMap.keySet()){
                JSONObject subMap = (JSONObject)testMap.get(s);
                for(String id : subMap.keySet()){
                    graph[verticeSeq.get(s)][verticeSeq.get(id)] = Integer.valueOf((String)subMap.get(id));
                }
            }
            System.out.println("distance graph size: " + graph.length + " " + graph[0].length);
            System.out.println("distance graph: ");
            System.out.println(graph[0][0] + " " + graph[0][1] + " " + graph[0][2]);
            System.out.println(graph[1][0] + " " + graph[1][1] + " " + graph[1][2]);
            System.out.println(graph[2][0] + " " + graph[2][1] + " " + graph[2][2]);

            System.out.println("distance array for curNode: " + dijkstra().toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // the JSONObject routerMap input is for test only, simply delete it when used
    public synchronized List<String> dijkstra() {
        // todo: Assign unique seq to all uuid (uuid0 -> 0; uuid1 -> 1; ...)
        HashMap<String, Integer> uuidToInteger = new HashMap<>();
        HashMap<Integer, String> integerToUuid = new HashMap<>();

        int sequence = 0;
        System.out.println("routerMap.size(): " + routerMap.keySet().size());
        for(String ID : routerMap.keySet()){
            if(!uuidToInteger.containsKey(ID)){
                uuidToInteger.put(ID, sequence);
                integerToUuid.put(sequence, ID);
                sequence++;
            }

            JSONObject subMap = (JSONObject)routerMap.get(ID);
            System.out.println("subMap.size(): " + subMap.keySet().size());
            for(String id : subMap.keySet()){
                if(!uuidToInteger.containsKey(id)){
                    uuidToInteger.put(id, sequence);
                    integerToUuid.put(sequence, id);
                    sequence++;
                }
            }
        }
        System.out.println("sequence: " + sequence);

        //todo: form graph
        int numVertices = sequence;
        int[][] graph = new int[numVertices][numVertices];
        for(String s : routerMap.keySet()){
            JSONObject subMap = (JSONObject)routerMap.get(s);
            for(String id : subMap.keySet()){
                graph[uuidToInteger.get(s)][uuidToInteger.get(id)] = Integer.valueOf((String)subMap.get(id));
            }
        }

        int start = uuidToInteger.get(uuid);
        // Create an array to store the shortest distances to each vertex
        int[] distances = new int[numVertices];
        Arrays.fill(distances, Integer.MAX_VALUE);
        distances[start] = 0;

        // Create a set to keep track of visited vertices
        Set<Integer> visited = new HashSet<>();

        // Create a priority queue to select the next vertex with the shortest distance
        PriorityQueue<Integer> pq = new PriorityQueue<>(numVertices, Comparator.comparingInt(i -> distances[i]));
        pq.offer(start);

        while (!pq.isEmpty()) {
            int vertex = pq.poll();
            // Add the vertex to the visited set
            visited.add(vertex);
            // Check the neighbors of the vertex and renew the distance array
            for (int neighbor = 0; neighbor < numVertices; neighbor++) {
                int edgeWeight = graph[vertex][neighbor];

                if (edgeWeight > 0 && !visited.contains(neighbor)) {
                    int newDistance = distances[vertex] + edgeWeight;

                    if (newDistance < distances[neighbor]) {
                        distances[neighbor] = newDistance;
                        pq.offer(neighbor);
                    }
                }
            }
        }

        // Make output ordered
        PriorityQueue<Integer> makeRank = new PriorityQueue<>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return distances[o1] - distances[o2];
            }
        });

        for (int i = 0; i < distances.length; i++){
            makeRank.add(i);
        }

        List<String> rank = new ArrayList<>();
        while(!makeRank.isEmpty()) {
            int idx = makeRank.poll();
            if (distances[idx] != 0) {
                rank.add(integerToUuid.get(idx)+": " + distances[idx]);
            }
        }
        return rank;
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


