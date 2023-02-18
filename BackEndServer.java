import com.alibaba.fastjson.JSONObject;
import org.w3c.dom.CharacterData;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackEndServer extends Thread{
    int frontEndPort;
    int backEndPort;
    String name;
    List<String> peers;
    String peerCount;
    String uuid;
    String content;
    Map<String, Object> peerSeq; // peerName -> sequence
    JSONObject routerMap;

    public BackEndServer(String configName){
        File configFile = new File("routerConfig/" + configName);
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
        this.peers = new ArrayList<String>();
        for(int i = 0; i < Integer.valueOf(peerCount); i++){
            peers.add(configProperties.getProperty("peer_" + i));
        }
        this.uuid = configProperties.getProperty("uuid");
        if(uuid.length() != 36){
            uuid = UUID.randomUUID().toString();
        }

        this.peerSeq = new HashMap<>();
        this.routerMap = new JSONObject();

        JSONObject localMap = new JSONObject();
        peerSeq.put(uuid, 0);
        routerMap.put(uuid, localMap);
    }

    @Override
    public void run() {
        try {
            startServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public static void main(String[] args) throws Exception{
//        BackEndServer backend = new BackEndServer(8081);
//        backend.startServer();
//    }

    public void startServer() throws Exception{
        ExecutorService pool = Executors.newCachedThreadPool();
        DatagramSocket dsock = new DatagramSocket(backEndPort);
        //Start asker thread periodic inquiring neighbor whether alive or not
        Asker asker = new Asker(peers, uuid, peerSeq, routerMap);
        asker.start();

        while(true){
            //listener
            byte[] recArr = new byte[2048];
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            dsock.receive(dpack);
            String msg = new String(recArr).trim();
            System.out.println(msg);
            //message from peer's router
            if (new String(recArr, 36, 6).equals("router")){
                String id = new String(recArr, 0, 36);
                int sequence = Integer.valueOf(new String(recArr, 42, 32).trim());
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
            }
            //TODO
            else if (msg.startsWith("/peer")){
                if (msg.equals("/peer/uuid")){

                }
                else if (msg.equals("/peer/neighbors")){

                }
                else if (msg.startsWith("/peer/addneighbor?")){

                }
                else if (msg.equals("/peer/map")){

                }
                else if (msg.equals("/peer/rank/")){

                }
            }
            else {
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
    public Asker(List<String> peers, String uuid,Map<String, Object> peerSeq, JSONObject routerMap){
        this.peers = peers;
        this.uuid = uuid;
        this.routerMap = routerMap;
        this.peerSeq = peerSeq;
        this.peerCount = new HashMap<>();
        this.peerDistance = new HashMap<>();
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
                System.out.println("########第"+ Math.abs(peerCount.get(id)) +"次找不到: " + id);
            }
            if (peerCount.get(id) <= -3 && localMap.containsKey(id)){
                localMap.remove(id);
//                peerSeq.put(id, -1);
                removeId.add(id);
                System.out.println("########remove: " + id);
                flag = true;
            }
            else if (peerCount.get(id) >= 0 && !localMap.containsKey(id)){
                localMap.put(id, peerDistance.get(id));
                System.out.println("########add: " + id);
                flag = true;
            }
        }
        if (flag){
            routerMap.put(uuid, localMap);
            System.out.println("########更新路由表:" + routerMap.toJSONString());
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
//                System.out.println("########receive date:" + new String(recArr));
                String id = new String(recArr, 0, 36);
                peerCount.put(id, 1);
            }
            saveRouterMap(routerMap, uuid.substring(0, 3)+".json");
            System.out.println("###dijkstra###: " + dijkstra().toString());
            long end = System.currentTimeMillis();

            try {
//                System.out.println(new JSONObject(peerSeq).toJSONString());
                System.out.println("sleep time: " + (5 - (end - start) / 1000));
                sleep(5000 - (end - start));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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
            dsock.send(dpack);
        }
    }
    public synchronized byte[] readRouterMap(List<String> removeId){
        for (String s : removeId){
            peerSeq.put(s, (int)peerSeq.getOrDefault(s, -1) + 1);
            routerMap.remove(s);
        }
        routerMap.put("seq", new JSONObject(peerSeq));
        byte[] res =  routerMap.toJSONString().getBytes();
        routerMap.remove("seq");
        for (String s : removeId){
            peerSeq.put(s, -1);
        }
        return res;
    }
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
    @Override
    public void run() {
        try{
            startThread();
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}