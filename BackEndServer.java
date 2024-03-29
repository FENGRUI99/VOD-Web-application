import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;


import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackEndServer extends Thread{
    int frontEndPort;
    int backEndPort;
    String name;
    List<String> peers;
    HashMap<String, String[]> peerInfo;
    String peerCount;
    String uuid;
    String content;
    Map<String, Object> peerSeq; // peerName -> sequence
    JSONObject routerMap;
    Map<String, Object> peerAddress;
//    JSONObject
    HashMap<String, String> peerDistance;
    int ttl;
    int interval;
    List<String> requestList;
    JSONObject peerHashMap; // filePath -> jsonArray of uuid
    JSONArray fileList; // file in peers
    List<Integer> frontPortList;

    public BackEndServer(String configName) throws UnknownHostException {
        File configFile = new File( configName);
        // Create a Properties object
        Properties configProperties = new Properties();
        //Read the properties file
        try (FileInputStream input = new FileInputStream(configFile)) {
            configProperties.load(input);
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }
        this.frontEndPort = Integer.valueOf(configProperties.getProperty("frontend_port"));
        this.backEndPort = Integer.valueOf(configProperties.getProperty("backend_port"));
        this.name = configProperties.getProperty("name");
        this.peerCount = configProperties.getProperty("peer_count");
        this.content = configProperties.getProperty("content_dir");
        this.peers = new ArrayList<>();
        for(int i = 0; i < Integer.valueOf(peerCount); i++){
            peers.add(configProperties.getProperty("peer_" + i));
        }
        this.uuid = configProperties.getProperty("uuid");
        if(uuid.length() != 36){
            uuid = UUID.randomUUID().toString();
        }

        this.peerSeq = new HashMap<>();
        this.routerMap = new JSONObject();
        this.peerAddress = new HashMap<>();
        this.fileList = new JSONArray();
        JSONObject localMap = new JSONObject();
        peerSeq.put(uuid, 0);
        routerMap.put(uuid, localMap);
        peerAddress.put(uuid, getServerIp() + "," + backEndPort);

        peerInfo = new HashMap<>();
        peerDistance = new HashMap<>();
        for (String s : peers){
            String[] tmp = s.split(",");
            peerInfo.put(tmp[0], tmp);
            peerDistance.put(tmp[0], tmp[4]);
        }

        if (configProperties.getProperty("search_ttl") == null){
            this.ttl = 15;
        }
        else {
            this.ttl = Integer.valueOf(configProperties.getProperty("search_ttl"));
        }

        if (configProperties.getProperty("search_interval") == null){
            this.interval = 100;
        }
        else {
            this.interval = Integer.valueOf(configProperties.getProperty("search_interval"));
        }

        this.requestList = new ArrayList<>();
        this.peerHashMap = new JSONObject();
        this.frontPortList = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            startServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void startServer() throws Exception{
        ExecutorService pool = Executors.newCachedThreadPool();
        DatagramSocket dsock = new DatagramSocket(backEndPort);
        //Start asker thread periodic inquiring neighbor whether alive or not
        Asker asker = new Asker(peers, uuid, peerSeq, routerMap, peerDistance, peerAddress);
        asker.start();
        Gossiper gossiper = new Gossiper(peers, uuid, requestList, peerHashMap, peerAddress);
        gossiper.start();
        Monitor monitor = new Monitor(InetAddress.getByName("127.0.0.1"), frontPortList, peers, uuid, fileList);
        monitor.start();
        while(true){
            //listener
            byte[] recArr = new byte[2048];
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            dsock.receive(dpack);
            String msg = new String(recArr).trim();
            //message from peer's router
            if (new String(recArr, 36, 6).equals("router")){
                String id = new String(recArr, 0, 36);
                int sequence = Integer.valueOf(new String(recArr, 42, 32).trim());
                //-1 keep Alive
                if (sequence > getPeerSeq(id)){
                    JSONObject peerMap = JSONObject.parseObject(new String(recArr, 74, recArr.length - 74).trim());
//                System.out.println("######receive data: " + new String(recArr));

                    // neighbor's peerSeq
                    Map<String, Object> tmp = (Map<String, Object>) peerMap.get("seq");
                    for (String s : tmp.keySet()){
                        if ((int)tmp.get(s) > (int)peerSeq.getOrDefault(s, -1)){
                            //更新路由表
                            if (peerMap.containsKey(s)){
                                peerSeq.put(s, tmp.get(s));
                                routerMap.put(s, peerMap.get(s));
                            }
                            //删除路由表
                            else {
                                peerSeq.put(s, -1);
                                if (routerMap.containsKey(s)) routerMap.remove(s);
                            }
                        }
                    }
                    //TODO 获取全局IP
                    Map<String, Object> tmp1 = (Map<String, Object>) peerMap.get("address");
                    for (String s : tmp1.keySet()){
                        if (!peerAddress.containsKey(tmp1.get(s))){
                            peerAddress.put(s, tmp1.get(s));
                        }
                    }
//                    System.out.println("########接收并更新路由表: " + routerMap.toJSONString());
                    send(id, sequence, recArr);
                }else if(sequence == -1){
                    replyAlive(uuid, dsock, dpack);
                }
            }
            else if(msg.startsWith("contentExist?")){
                String[] tmp = msg.split("-"); // todo: tmp[1] -> filePath;
                File f = new File(tmp[1]);

                if (f.exists()){
                    String sendString = uuid + "-" + "fileExist!";
                    byte[] sendArr = sendString.getBytes();
                    InetAddress sourceIp = dpack.getAddress();
                    int sourcePort = dpack.getPort();
                    dpack = new DatagramPacket(sendArr, sendArr.length, sourceIp, sourcePort);
                    dsock.send(dpack);
                }
            }
            else if (msg.startsWith("gossip")) {
                String[] tmp = new String(recArr).trim().split(",");
                String filePath = tmp[1];
                byte[] sendArr;
                JSONArray files = findAll(filePath.split("tent/")[1]);
                sendArr = (uuid + files.toJSONString()).getBytes();
                dpack.setData(sendArr);
                dsock.send(dpack);
            }
            else if (msg.startsWith("monitor")){
                JSONArray arr = JSONArray.parseArray(new String(recArr, 64, recArr.length - 64).trim());
                synchronized (this){
                    for (Object file : arr){
                        if (!fileList.contains(file)){
                            fileList.add(file);
                        }
                    }
                }
            }
            else if (msg.equals("/")){
                synchronized (this){
                    frontPortList.add(dpack.getPort());
                }
            }
            else if (msg.startsWith("/peer")){
                if (msg.equals("/peer/uuid")){
                    JSONObject sendJson = new JSONObject();
                    sendJson.put("uuid", uuid);
                    String sendString = sendJson.toJSONString();
                    byte[] sendArr = sendString.getBytes();
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                }
                //24f22a83-16f4-4bd5-af63-b5c6e979dbb,pi.ece.cmu.edu,18345,18346,10
                else if (msg.startsWith("/peer/neighbors")){
                    JSONObject localMap = (JSONObject) routerMap.get(uuid);
                    List<JSONObject> ans = new ArrayList<>();
                    for (String id : localMap.keySet()){
                        JSONObject tmp = new JSONObject();
                        String[] info = peerInfo.get(id);
                        tmp.put("uuid", info[0]);
                        tmp.put("host", info[1]);
                        tmp.put("frontend", info[2]);
                        tmp.put("backend", info[3]);
                        tmp.put("metric", info[4]);
                        ans.add(tmp);
                    }
                    byte[] sendArr = JSONObject.toJSONString(ans).getBytes();
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                }
                else if (msg.startsWith("/peer/addneighbor?")){
//                    peers.add(msg.split("or?")[1]);
                    synchronized (this){
                        String[] tmp = msg.substring(18).split("&");
                        peerCount += 1;
                        String[] inf = new String[5];
                        for (String s : tmp){
                            System.out.println(s);
                        }
                        inf[0] = tmp[0].split("uuid=")[1];
                        inf[1] = tmp[1].split("host=")[1];
                        inf[2] = tmp[2].split("frontend=")[1];
                        inf[3] = tmp[3].split("backend=")[1];
                        inf[4] = tmp[4].split("metric=")[1];
                        peerInfo.put(inf[0], inf);
                        String ans = inf[0] + "," + inf[1] + "," + inf[2] + "," + inf[3] + "," + inf[4];
                        System.out.println(ans);
                        peers.add(ans);
                        peerSeq.put(inf[0], -1);
                        peerDistance.put(inf[0], inf[4]);
                    }
                }
                else if (msg.startsWith("/peer/map")){
                    String sendString = routerMap.toJSONString();
                    byte[] sendArr = sendString.getBytes();
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                }
                else if (msg.startsWith("/peer/rank/")){
                    HashSet<String> containFile = new HashSet<>(); // uuid that contains specific file
                    DatagramSocket tmpSocket = new DatagramSocket();
                    tmpSocket.setSoTimeout(500);
                    //System.out.println("test: Start request containFile?");
                    String filePath = msg.split("/peer/rank/")[1];
                    // Ask each node in network if they have File: filePath
                    for(String ID : peerAddress.keySet()) {
                        if(ID.equals(uuid)) continue; // skip curNode
                        String message = (String)peerAddress.get(ID);
                        System.out.println(message);
//                        InetAddress peerIp = InetAddress.getByName(message.split(",")[0].split("/")[1]);
                        InetAddress peerIp = InetAddress.getByName(message.split(",")[0]);
                        int peerPort = Integer.valueOf(message.split(",")[1]);
                        String sendStr = "contentExist?-" + filePath;
                        byte[] sendArr = sendStr.getBytes();
                        DatagramPacket tmpPack = new DatagramPacket(sendArr, sendArr.length, peerIp, peerPort);
                        tmpSocket.send(tmpPack);
                    }

                    while(true){
                        DatagramPacket tmpPack= new DatagramPacket(recArr, recArr.length);
                        try{
                            tmpSocket.receive(tmpPack);
//                            System.out.println("received");
                        } catch (SocketTimeoutException e){
                            break;
                        }

                        String message = new String(tmpPack.getData()).trim();
                        if(message.substring(37).startsWith("fileExist!")) {
                            containFile.add(message.substring(0, 36));
                        }
                    }
                    JSONObject address = new JSONObject();
                    for (String s : containFile){
                        if (!s.equals(uuid)){
                            address.put(s, peerAddress.get(s));
                        }
                    }

                    String sendString = dijkstra(containFile).toString();
                    byte[] sendArr = (sendString + "&" + address.toJSONString()).getBytes();
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                }
                else if (msg.startsWith("/peer/search")){
                    String filePath = msg.split("search/")[1];
                    synchronized (this){
                        requestList.add(filePath);
                    }
                    //TODO 给前端response
                    while (requestList.contains(filePath)){
                        sleep(interval);
                    }
                    String content = ((JSONObject)peerHashMap.get(filePath)).toJSONString();
                    byte[] sendArr = content.getBytes();
                    dpack.setData(sendArr);
                    dsock.send(dpack);
                }
            }
            else {
//                System.out.println(new String(recArr));
                ListenerHeader header = JSONObject.parseObject(new String(recArr), ListenerHeader.class);
                // message from http server
                if (header.getSrc() == 0){
                    pool.execute(new BackEndRequest(dpack.getAddress(), dpack.getPort(), header.peerIp, header.peerPort, header.fileName, header.start, header.length, header.rate));
                }
                // message from peer back-end server
                else {
                    pool.execute(new BackEndResponse(dpack.getAddress(), dpack.getPort()));
                }
            }
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
        routerMap.put("address", new JSONObject(peerAddress));
        byte[] res =  routerMap.toJSONString().getBytes();
        routerMap.remove("seq");
        routerMap.remove("address");
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
            try{
                dsock.send(dpack);
            } catch (SocketException s){

            }

        }
    }
    public synchronized List<String> dijkstra(HashSet<String> containFile) {
        // todo: Assign unique seq to all uuid (uuid0 -> 0; uuid1 -> 1; ...)
        HashMap<String, Integer> uuidToInteger = new HashMap<>();
        HashMap<Integer, String> integerToUuid = new HashMap<>();

        int sequence = 0;
//        System.out.println("routerMap.size(): " + routerMap.keySet().size());
        for(String ID : routerMap.keySet()){
            if(!uuidToInteger.containsKey(ID)){
                uuidToInteger.put(ID, sequence);
                integerToUuid.put(sequence, ID);
                sequence++;
            }

            JSONObject subMap = (JSONObject)routerMap.get(ID);
//            System.out.println("subMap.size(): " + subMap.keySet().size());
            for(String id : subMap.keySet()){
                if(!uuidToInteger.containsKey(id)){
                    uuidToInteger.put(id, sequence);
                    integerToUuid.put(sequence, id);
                    sequence++;
                }
            }
        }
//        System.out.println("sequence: " + sequence);

        //todo: form graph
        int numVertices = sequence;
        int[][] graph = new int[numVertices][numVertices];
        for(String s : routerMap.keySet()){
//            System.out.println(s);
            JSONObject subMap = (JSONObject)routerMap.get(s);
            for(String id : subMap.keySet()){
//                System.out.println(id);
//                System.out.println(subMap.toJSONString());
                try{
                    graph[uuidToInteger.get(s)][uuidToInteger.get(id)] = Integer.valueOf((String)subMap.get(id));
                }
                catch(NumberFormatException e){
                    continue;
                }
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
            String UUID = integerToUuid.get(idx);
            if (distances[idx] != 0 && containFile.contains(UUID)) {
                rank.add(UUID + ": " + distances[idx]);
            }
        }
        return rank;
    }
    public String getServerIp() {
        String localip = null;// 本地IP，如果没有配置外网IP则返回它
        String netip = null;// 外网IP
        try {
            Enumeration netInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            boolean finded = false;// 是否找到外网IP
            while (netInterfaces.hasMoreElements() && !finded) {
                NetworkInterface ni = (NetworkInterface) netInterfaces.nextElement();
                Enumeration address = ni.getInetAddresses();
                while (address.hasMoreElements()) {
                    ip = (InetAddress) address.nextElement();
                    if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {// 外网IP
                        netip = ip.getHostAddress();
                        finded = true;
                        break;
                    } else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {// 内网IP
                        localip = ip.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (netip != null && !"".equals(netip)) {
            return netip;
        } else {
            return localip;
        }
    }
    public JSONArray findAll(String name){
        JSONArray files = new JSONArray();
        File directory = new File("./content");
        // Get all the files in the directory
        File[] f = directory.listFiles();
        //Search
        for (File file : f) {
            if (file.isFile()) {
                if(Pattern.matches(".*"+name+".*" ,file.getName())) files.add("content/" + file.getName());
            }
        }
        return files;
    }
}

class BackEndRequest extends Thread{
    //server initialization
    int chunkSize = 1024;
    int windowSize = 2;
    DatagramSocket dsock;
    DatagramPacket dpack;
    DatagramSocket frontSock;
    DatagramPacket frontPack;
    long start;
    long length;
    long fileLen;
    InetAddress frontEndAddress;
    int frontEndPort;
    InetAddress peerListenAddress;
    int peerListenPort;
    String fileName;
    int rate;

    public BackEndRequest(InetAddress frontEndAddress, int frontEndPort, InetAddress peerListenAddress, int peerListenPort, String fileName, long start, long length, int rate){
        this.frontEndAddress = frontEndAddress;
        this.frontEndPort = frontEndPort;
        this.peerListenAddress = peerListenAddress;
        this.peerListenPort = peerListenPort;
        this.fileName = fileName;
        this.start = start;
        this.length = length;
        this.fileLen = length;
        this.rate = rate;
    }
    @Override
    public void run(){
        try {startRequest(); }
        catch (Exception e) {throw new RuntimeException(e); }
    }
    public void startRequest() throws Exception {
        dsock = new DatagramSocket();
        dsock.setSoTimeout(1000);
        // say hello to Peer listener thread
        String message = JSONObject.toJSONString(new ListenerHeader(1));
        byte[] sendArr = message.getBytes();
        dpack = new DatagramPacket(sendArr, sendArr.length, peerListenAddress, peerListenPort);
        dsock.send(dpack);
        // wait for hello from Peer response thread
        byte[] recArr = new byte[1024];
        dpack = new DatagramPacket(recArr, recArr.length);
        while (true){
            try{
                dsock.receive(dpack);
            } catch (SocketTimeoutException e){
                System.out.println("restart to peer say hello");
                dpack = new DatagramPacket(sendArr, sendArr.length, peerListenAddress, peerListenPort);
                dsock.send(dpack);
                continue;
            }
            break;
        }

        InetAddress peerResAddress = dpack.getAddress();
        int peerResPort = dpack.getPort();
//        System.out.println(new String(dpack.getData()));
        getFileInfo(peerResAddress, peerResPort);
        //计时
        long startTime = Calendar.getInstance().getTimeInMillis();

        //initialization
        long fileSize = 0;
        int recePointer = 0;
        int receSize = 0;
        int RTT = 0;
//        String fileName = null;
        HashMap<Integer, byte[]> fileMap = new HashMap<>();
        frontSock = new DatagramSocket();
        int cutNumber = 5;

        L1:
        while (true) {
            recArr = new byte[Math.min(2*chunkSize, 60000)];
            dpack.setData(recArr, 0, recArr.length);
            //AIMD过程
            try {
                dsock.receive(dpack);                           // receive the packet

                //todo:simulate the packet loss
                if(Packet_Loss_Simulation(0.0)){
                    System.out.println("#############Packge loss#############");
                    windowSize = Math.max(windowSize/2, 1);
                    requestRange(fileName, start, length, chunkSize);
                    receSize = 0;
                    continue;
                }

            }catch (SocketTimeoutException e){
                System.out.println("cut window, start: " + start + ", length: " + length);
                windowSize = Math.max(windowSize/2, 1);
                requestRange(fileName, start, length, chunkSize);
                receSize = 0;
                continue;
            }
//            cutNumber = 5;
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] info = dpack.getData();
            int headerLen = convertByteToInt(info, 0);
            int contentLen = convertByteToInt(info, 4);
            ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());

            //judge header
            if (header.statusCode == 0) {
                //send file info to frontend
                frontPack = new DatagramPacket(info, info.length, frontEndAddress, frontEndPort);
                frontSock.send(frontPack);

                //读取rtt, RTO = 2*RTT
                long endTime = Calendar.getInstance().getTimeInMillis();
                RTT = (int) (endTime-startTime);
                if (rate == 0) rate = 8000000;
                chunkSize = Math.min(50000, RTT * rate/8);
                if (chunkSize == 0) chunkSize = 50000;
                dsock.setSoTimeout(100*RTT);
                frontSock.setSoTimeout(100*RTT);
                fileSize = header.length;

                //length = fileSize - start;
//                fileName = header.fileName;

                //peerToPeer
                if(start<0){
                    start = Math.abs(start);
                    length = Math.abs(length);
                    long tmp = fileSize/length;
                    if(start == length){
                        start = tmp * (start-1);
                        length = fileSize - start;
                    }else{
                        length = tmp;
                        start = tmp * (start-1);
                    }
                }
//                else{
//                    length = fileSize - start;
//                }
                recePointer = (int) (start / chunkSize);
                requestRange(fileName, start, length, chunkSize);
            }
            else if (header.statusCode == 1) {
//                byte[] content = new byte[contentLen];
                //System.out.println(contentLen);
//                System.arraycopy(info, 8 + headerLen, content, 0, contentLen);

                fileMap.put(header.sequence, info);
                while (fileMap.containsKey(recePointer)) {
                    //TODO 可能有问题 mark一下
                    byte[] tmp = fileMap.get(recePointer);
                    frontPack = new DatagramPacket(tmp, tmp.length, frontEndAddress, frontEndPort);
//                    System.out.println(frontPack.getLength());
                    frontSock.send(frontPack);
                    tmp = new byte[50];
                    frontPack = new DatagramPacket(tmp, 0, tmp.length);
                    try{
                        frontSock.receive(frontPack);
                    }catch (SocketTimeoutException e){
                        System.out.println("##########前端失联, 重发: " + recePointer * chunkSize);
                        continue;
                    }

                    System.out.println(new String(frontPack.getData()).trim());
                    if (new String(frontPack.getData()).trim().equals("close")){
                        System.out.println("close: " + new String(frontPack.getData()).trim());
                        close();
                        break L1;
                    }

                    recePointer++;
                    start += chunkSize;
                    length -= chunkSize;
                    if (length<=0) { //到达接受长度
                        close();
                        break L1;
                    }
                    receSize++;
                }
                if (receSize == windowSize) {
                    requestRange(fileName, start, length, chunkSize);
                    receSize = 0;
                    windowSize ++;
                }
            }

            else{ //Not found
                break;
            }
        }
//        System.out.println("end this transmission.");

      /*  //测试用：将接收文件的map转存为byte数组求md5，将文件保存到本地。
        System.out.println("length: "+length+" fileSize: "+fileSize);
        byte[] fi = map2File(fileMap, fileSize);

        System.out.println("md5: " + getMD5Str(fi));
        DataOutputStream sOut = new DataOutputStream(new FileOutputStream(new File("./content/"+"test1.png")));
        sOut.write(fi, 0, fi.length);
        sOut.flush();
        sOut.close();
       */

    }
    public boolean Packet_Loss_Simulation(double lossRate) throws Exception {
        Random random = new Random();
        double randomNumber = random.nextDouble();
        if (randomNumber < lossRate) {
            return true;
        } else {
            return false;
        }
    }
    public byte[] map2File(HashMap<Integer, byte[]> map, int fileSize){
        byte[] file = new byte[fileSize];
        int pointer = 0;
        int mapSize = map.size();
        for(int i = 0; i < mapSize; i++){
            for(int j = 0; j < map.get(i).length; j++){
                file[pointer] = map.get(i)[j];
                pointer++;
            }
        }
        System.out.println(pointer);
        return file;
    }
    //发送相应报文
    public void getFileInfo(InetAddress peerResAddress, int peerResPort) throws Exception{
        byte[] header = getReqHeader(0, fileName, 0, 0,0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);
        dpack = new DatagramPacket(sendArr, sendArr.length, peerResAddress, peerResPort);
        dsock.send(dpack);
        //System.out.println("Status_0 send success");
    }
    public void requestRange(String fileName, long start, long length, int chunkSize) throws Exception{
        byte[] header = getReqHeader(1, fileName, start, length, chunkSize).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        dpack.setData(sendArr, 0, sendArr.length);
        dsock.send(dpack);
        // System.out.println("Status_1 send success");
    }
    public void close () throws Exception{
        byte[] header = getReqHeader(2, fileName, 0, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        dpack.setData(sendArr, 0, sendArr.length);
        dsock.send(dpack);
        dsock.close();
        //System.out.println("Status_2 send success");
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
    public String getReqHeader (int statusCode, String fileName, long start, long length, int chunkSize){
        return JSONObject.toJSONString(new RequestHeader(statusCode, fileName, start, length, chunkSize));
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

class BackEndResponse extends Thread{
    InetAddress peerIp;
    int peerPort;
    DatagramSocket dsock = null;
    DatagramPacket dpack = null;
    int chunkSize = 1024;
    int windowSize = 4;
    private DataInputStream in = null;
    int minIndex = 0;     //多client可能会有问题
    public BackEndResponse(InetAddress peerIp, int peerPort){
        this.peerIp = peerIp;
        this.peerPort = peerPort;
    }
    @Override
    public void run(){
        try {
            startResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void startResponse() throws Exception{
        dsock = new DatagramSocket();
        byte[] message = "hello".getBytes();
        dpack = new DatagramPacket(message, message.length, peerIp, peerPort);
        dsock.send(dpack);
        //dsock.setSoTimeout(60 * 1000);
        while (true){
            // receive request
            RequestHeader header = waitRequest();
            File f = new File("./" + header.fileName);
            // get info
            if (header.statusCode == 0 && f.exists()){
                sendInfo(f, header.fileName);
            }
            else if (!f.exists()){
                // 404
                break;
            }
            // get range of file
            else if (header.statusCode == 1){
                chunkSize = header.getChunkSize();
                sendRange(header);
            }
            // close
            else {
                // status code == 2 -> finish
                System.out.println("Close");
                break;
            }
        }
    }
    public RequestHeader waitRequest() throws Exception{
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        System.out.println("Client ip: " + dpack.getAddress() + ", port: " + dpack.getPort());
        recArr = dpack.getData();
        //preheader
        int headerLen = convertByteToInt(recArr, 0);    //recArr[0:3]
        //request header
        RequestHeader header = JSONObject.parseObject(new String(recArr, 8, 8 + headerLen), RequestHeader.class);
        System.out.println(header.toString());
        return header;
    }
    public void sendInfo(File f, String filePath) throws Exception{
        String resHeader = getResHeader(0, filePath, 0, f.length(), -1, f.lastModified(), "");
        byte[] preheader = getPreHeader(resHeader.length(), 0);
        byte[] sendArr = addTwoBytes(preheader, resHeader.getBytes());
        dpack.setData(sendArr);
        dsock.send(dpack);

        FileInputStream fis = new FileInputStream(f);
        in = new DataInputStream(fis);
        sendArr = new byte[fis.available()];
        in.read(sendArr);
        in.close();
        System.out.println(getMD5Str(sendArr));
    }
    public void sendRange(RequestHeader header) throws Exception{
        boolean flag = false;
        long start = header.start;
        long end = start + header.length;
        int startSeq = (int) (start / chunkSize);
        int startNumber = 0;
        if (startSeq > minIndex) windowSize++;
        else if (startSeq <= minIndex) {
            windowSize = Math.max(windowSize / 2, 1);
            minIndex = startSeq;
        }
        File f = new File("./" + header.fileName);
        FileInputStream fis = new FileInputStream(f);
        in = new DataInputStream(fis);
        in.skip(start);
        while (startNumber < windowSize){
            byte[] sendArr;
            byte[] preheader;
            String resHeader;
            int contentSize;
            if (start + chunkSize * (startNumber + 1) <= end){
                contentSize = chunkSize;
            }
            else {
                contentSize = (int) (end - start - chunkSize * startNumber);
                flag = true;
            }
            resHeader = getResHeader(1, header.fileName, start + chunkSize * startNumber, contentSize, startSeq, 0, "");
            preheader = getPreHeader(resHeader.length(), contentSize);
            sendArr = new byte[8 + resHeader.length() + contentSize];
            in.read(sendArr, 8 + resHeader.length(), contentSize);
            System.arraycopy(preheader, 0, sendArr, 0, preheader.length);
            System.arraycopy(resHeader.getBytes(), 0, sendArr, 8, resHeader.length());
            dpack.setData(sendArr);
            dsock.send(dpack);
            System.out.println("response header: " + resHeader.toString());
            startNumber++;
            startSeq++;
            if (flag) break;
        }
        in.close();
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
    public String getResHeader (int statusCode, String fileName, long start, long length, int sequence, long lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, sequence, lastModified, md5));
    }
    public static String getMD5Str(byte[] digest) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            digest  = md5.digest(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String md5Str = new BigInteger(1, digest).toString(16);
        return md5Str;
    }
}

class Asker extends Thread{
    List<String> peers;
    String uuid;
    JSONObject routerMap;
    Map<String, Object> peerSeq;
    HashMap<String, Integer> peerCount;
    HashMap<String, String> peerDistance;
    Map<String, Object> peerAddress;
    String localIp = getServerIp();
    public Asker(List<String> peers, String uuid,Map<String, Object> peerSeq, JSONObject routerMap, HashMap<String, String> peerDistance, Map<String, Object> peerAddress){
        this.peers = peers;
        this.uuid = uuid;
        this.routerMap = routerMap;
        this.peerSeq = peerSeq;
        this.peerCount = new HashMap<>();
        this.peerDistance = peerDistance;
        this.peerAddress = peerAddress;
//        System.out.println("Asker routerMap: " + routerMap.toJSONString());
    }
    public synchronized void modifyMap() throws Exception {
        //TODO 删除不仅仅是uuid里的id这一项，也要删除id这一项
        JSONObject localMap = routerMap.getJSONObject(uuid); //TODO 这句可能有问题
        boolean flag = false;
        List<String> removeId = new ArrayList<>();
        for (String id : peerCount.keySet()){
            peerCount.put(id, peerCount.get(id) - 1);
            if (peerCount.get(id) < 0 && peerCount.get(id) >= -3){
//                System.out.println("########第"+ Math.abs(peerCount.get(id)) +"次找不到: " + id);
            }
            if (peerCount.get(id) <= -3 && localMap.containsKey(id)){
                localMap.remove(id);
//                peerSeq.put(id, -1);
                removeId.add(id);
//                System.out.println("########remove: " + id);
                flag = true;
            }
            else if (peerCount.get(id) >= 0 && !localMap.containsKey(id)){
                localMap.put(id, peerDistance.get(id));
//                System.out.println("########add: " + id);
                flag = true;
            }
        }
        if (flag){
            routerMap.put(uuid, localMap);
//            System.out.println("########更新路由表:" + routerMap.toJSONString());
            // TODO 更新路由表后要往外发
            send(uuid, (int)peerSeq.get(uuid), removeId);
            peerSeq.put(uuid, (int)peerSeq.get(uuid) + 1);
        }
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
                byte[] header = (uuid + "router-1").getBytes();
                byte[] content = "alive".getBytes();
                byte[] sendArr = new byte[74 + content.length];
                System.arraycopy(header, 0, sendArr, 0, header.length);
                System.arraycopy(content, 0, sendArr, 74, content.length);
                dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(tmp[1]), Integer.valueOf(tmp[3]));
                try {
                    dsock.send(dpack);
                }catch (SocketException s){
                }
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
//                System.out.println("########receive date:" + new String(recArr));
                String id = new String(recArr, 0, 36);
                peerCount.put(id, 1);
//                System.out.println("####################"+dpack.getAddress().toString());
                if (dpack.getAddress().toString().equals("/127.0.0.1")){
                    peerAddress.put(id,  localIp + "," + dpack.getPort());
                }
                else {
                    if (!dpack.getAddress().toString().startsWith("/")){
                        peerAddress.put(id, dpack.getAddress().toString() + "," + dpack.getPort());
                    }
                    else {
                        peerAddress.put(id, dpack.getAddress().toString().substring(1) + "," + dpack.getPort());
                    }
                }
            }
//            saveRouterMap(routerMap, uuid.substring(0, 3)+".json");
//            System.out.println("###dijkstra###: " + dijkstra().toString());
            long end = System.currentTimeMillis();

            try {
//                System.out.println(new JSONObject(peerSeq).toJSONString());
//                System.out.println(new JSONObject(peerAddress).toJSONString());
//                System.out.println("sleep time: " + (10 - (end - start) / 1000));
                sleep(Math.max(10000 - (end - start), 1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public String getServerIp() {
        String localip = null;// 本地IP，如果没有配置外网IP则返回它
        String netip = null;// 外网IP
        try {
            Enumeration netInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            boolean finded = false;// 是否找到外网IP
            while (netInterfaces.hasMoreElements() && !finded) {
                NetworkInterface ni = (NetworkInterface) netInterfaces.nextElement();
                Enumeration address = ni.getInetAddresses();
                while (address.hasMoreElements()) {
                    ip = (InetAddress) address.nextElement();
                    if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {// 外网IP
                        netip = ip.getHostAddress();
                        finded = true;
                        break;
                    } else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {// 内网IP
                        localip = ip.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (netip != null && !"".equals(netip)) {
            return netip;
        } else {
            return localip;
        }
    }
    public void saveRouterMap(JSONObject routerMap, String fileName) throws Exception{
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(routerMap.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void send(String id, int sequence, List<String> removeId) throws Exception{
        DatagramSocket dsock = new DatagramSocket();
        DatagramPacket dpack;
        byte[] sendArr;

        sendArr = readRouterMap(removeId);
        byte[] message = new byte[74+sendArr.length];
        String header = id+"router"+sequence;
        System.arraycopy(header.getBytes(), 0, message, 0, header.length());
        System.arraycopy(sendArr, 0, message, 74, sendArr.length);
        sendArr = new byte[message.length];
        System.arraycopy(message, 0, sendArr, 0, message.length);

        for(int i = 0; i < peers.size(); i++){//1，3
            String[] peerInfo = peers.get(i).split(",");
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName(peerInfo[1]), Integer.valueOf(peerInfo[3]));
            try{
                dsock.send(dpack);
            }catch (SocketException s){
            }

        }
    }
    public synchronized byte[] readRouterMap(List<String> removeId){
        for (String s : removeId){
            peerSeq.put(s, (int)peerSeq.getOrDefault(s, -1) + 1);
            routerMap.remove(s);
        }
        routerMap.put("seq", new JSONObject(peerSeq));
        routerMap.put("address", new JSONObject(peerAddress));
        byte[] res =  routerMap.toJSONString().getBytes();
        routerMap.remove("seq");
        routerMap.remove("address");
        for (String s : removeId){
            peerSeq.put(s, -1);
        }
        return res;
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

class Gossiper extends Thread{
    List<String> peers;
    String uuid;
    List<String> requestList;
    JSONObject peerHashMap; // Partial file name -> (filePath -> JsonArray of uuid)
    Map<String, Object> peerAddress;
    public Gossiper(List<String> peers, String uuid, List<String> requestList, JSONObject peerHashMap, Map<String, Object> peerAddress){
        this.peers = peers;
        this.uuid = uuid;
        this.requestList = requestList;
        this.peerHashMap = peerHashMap;
        this.peerAddress = peerAddress;
    }
    @Override
    public void run() {
        try{
            startThread();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
//    public void startThread() throws Exception{
//        while (true){
//            long start = System.currentTimeMillis();
//            if (requestList.size() > 0) {
//                synchronized (this){
//                    for (String f : requestList){
//                        System.out.println(f);
//                        if (ttlHashMap.get(f) == 0){
//                            requestList.remove(f);
//                            ttlHashMap.remove(f);
//                            break;
//                        }
//                        File file = new File("./" + f);
//                        JSONArray arr = (JSONArray) peerHashMap.getOrDefault(f, new JSONArray());
//                        if (file.exists() && !arr.contains(uuid)){
//                            arr.add(uuid);
//                        }
//                        int randomPeer = (int) (Math.random() * (peers.size()));
//                        String[] peer = peers.get(randomPeer).split(",");
//                        String peerId = peer[0];
//                        System.out.println("randomly choose : " + peerId);
//                        InetAddress peerIp;
//                        peerIp = InetAddress.getByName(peer[1]);
//                        int peerPort = Integer.valueOf(peer[3]);
//                        sendAndReceive(peerIp, peerPort, f, ttlHashMap.getOrDefault(f, 15), arr, peerHashMap);
//                        peerHashMap.put(f, arr);
//                        ttlHashMap.put(f, ttlHashMap.get(f) - 1);
//                        System.out.println(((JSONArray)peerHashMap.get(f)).toJSONString());
//                    }
//                }
//            }
//            long end = System.currentTimeMillis();
//            sleep(Math.max(1, interval - (end - start)));
//        }
//    }
//
//    public void sendAndReceive(InetAddress peerIp, int peerPort, String filePath, int ttl, JSONArray exchangeList, JSONObject peerHashMap) throws IOException {
//        DatagramSocket dsock = new DatagramSocket();
//        DatagramPacket dpack;
//        dsock.setSoTimeout(500);
//        String header = "gossip," + filePath + "," + ttl;
//        String content = exchangeList.toJSONString();
//        byte[] sendArr = new byte[64 + content.length()];
//        System.arraycopy(header.getBytes(), 0, sendArr, 0, header.length());
//        System.arraycopy(content.getBytes(), 0, sendArr, 64, content.length());
//        dpack = new DatagramPacket(sendArr, sendArr.length, peerIp, peerPort);
//        dsock.send(dpack);
//        byte[] recArr = new byte[2048];
//        dpack.setData(recArr);
//        try{
//            dsock.receive(dpack);
//        }catch (SocketTimeoutException e){
//            return;
//        }
//        String flag = new String(recArr, 0, 1);
//        if (flag.equals("1")){
//            JSONArray arr = JSONArray.parseArray(new String(recArr, 1, recArr.length - 1).trim());
//            for (Object id : arr){
//                if (!exchangeList.contains(id)){
//                    exchangeList.add(id);
//                }
//            }
//            peerHashMap.put(filePath, exchangeList);
//        }
//    }
    public void startThread() throws Exception{
        while (true){
            long start = System.currentTimeMillis();
            if (requestList.size() > 0) {
                synchronized (this){
                    for (String f : requestList){
                        System.out.println(f);
                        JSONObject hm = (JSONObject) peerHashMap.getOrDefault(f, new JSONObject());
                        List<String> files = findAll(f.split("tent/")[1]);
                        for (String file : files){
                            file = "content/" + file;
                            JSONArray arr = (JSONArray) hm.getOrDefault(file, new JSONArray());
                            if (!arr.contains(uuid)){
                                arr.add(uuid);
                            }
                            hm.put(file, arr);
                        }
                        peerHashMap.put(f, hm);
                        send(peerAddress, f, peerHashMap);
                    }
                    requestList.clear();
                }
            }
            long end = System.currentTimeMillis();
//            System.out.println("sleep: " + (100 - (end - start)));
            sleep(Math.max(1, 100 - (end - start)));
        }
    }
    public void send(Map<String, Object> peerAddress, String filePath, JSONObject peerHashMap) throws IOException {
        DatagramSocket dscok = new DatagramSocket();
        dscok.setSoTimeout(500);
        for(String ID : peerAddress.keySet()) {
            if(ID == uuid) continue; // skip curNode
            String message = (String)peerAddress.get(ID);
            InetAddress peerIp = InetAddress.getByName(message.split(",")[0]);
            int peerPort = Integer.valueOf(message.split(",")[1]);

            String header = "gossip," + filePath;
//            String content = ((JSONObject) peerHashMap.get(filePath)).toJSONString();
            byte[] sendArr = header.getBytes();
//            System.arraycopy(header.getBytes(), 0, sendArr, 0, header.length());
//            System.arraycopy(content.getBytes(), 0, sendArr, 64, content.length());
            DatagramPacket tmpPack = new DatagramPacket(sendArr, sendArr.length, peerIp, peerPort);
            dscok.send(tmpPack);
        }

        while(true){
            byte[] recArr = new byte[2048];
            DatagramPacket tmpPack= new DatagramPacket(recArr, recArr.length);
            try{
                dscok.receive(tmpPack);
            } catch (SocketTimeoutException e){
                break;
            }
            String peerId = new String(recArr, 0, 36);
            JSONArray arr = JSONArray.parseArray(new String(recArr, 36, recArr.length - 36).trim());
            JSONObject localHm = (JSONObject) peerHashMap.get(filePath);
            for (Object file : arr){
                if (!localHm.containsKey(file)){
                    JSONArray tmp = new JSONArray();
                    tmp.add(peerId);
                    localHm.put((String) file, tmp);
                }
                else{
                    JSONArray tmp = (JSONArray) localHm.get(file);
                    if (!tmp.contains(peerId)){
                        tmp.add(peerId);
                    }
                    localHm.put((String) file, tmp);
                }
            }
            peerHashMap.put(filePath, localHm);
        }
    }
    public List<String> findAll(String name){
        List<String> files = new ArrayList<>();
        File directory = new File("./content");
        // Get all the files in the directory
        File[] f = directory.listFiles();
        //Search
        for (File file : f) {
            if (file.isFile()) {
                if(Pattern.matches(".*"+name+".*" ,file.getName())) files.add(file.getName());
            }
        }
        return files;
    }
}

class Monitor extends Thread{
    InetAddress frontIp;
    List<Integer> frontPortList;
    List<String> peers;
    String uuid;
    JSONArray fileList;
    public Monitor(InetAddress frontIp, List<Integer> frontPortList, List<String> peers, String uuid, JSONArray fileList){
        this.frontIp = frontIp;
        this.frontPortList = frontPortList;
        this.peers = peers;
        this.uuid = uuid;
        this.fileList = fileList;
    }
    @Override
    public void run() {
        try{
            startThread();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void startThread() throws Exception{
        while (true){
            long start = System.currentTimeMillis();
            String path = "./content/";
            File file = new File(path);
            File[] files = file.listFiles();
            synchronized (this){
                for (File f : files){
                    if (!fileList.contains("content/" + f.getName())){
                        fileList.add("content/" + f.getName());
                    }
                }
            }
            for (String p : peers){
                String[] peer = p.split(",");
                String peerId = peer[0];
                InetAddress peerIp;
                peerIp = InetAddress.getByName(peer[1]);
                int peerPort = Integer.valueOf(peer[3]);
                try{
                    send(peerIp, peerPort, fileList);
                }catch (SocketException e){

                }
            }
            synchronized (this){
                for (Integer port : frontPortList){
                    DatagramSocket dsock = new DatagramSocket();
                    byte[] sendArr = fileList.toJSONString().getBytes();
                    DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, frontIp, port);
                    dsock.send(dpack);
                }
                frontPortList.clear();
            }
            long end = System.currentTimeMillis();
            sleep(Math.max(500 - (end - start), 1));

        }
    }
    public void send(InetAddress peerIp, int peerPort, JSONArray fileList) throws IOException {
        DatagramSocket dsock = new DatagramSocket();
        DatagramPacket dpack;
        String header = "monitor";
        String content = fileList.toJSONString();
        byte[] sendArr = new byte[64 + content.length()];
        System.arraycopy(header.getBytes(), 0, sendArr, 0, header.length());
        System.arraycopy(content.getBytes(), 0, sendArr, 64, content.length());
        dpack = new DatagramPacket(sendArr, sendArr.length, peerIp, peerPort);
        dsock.send(dpack);
    }

}