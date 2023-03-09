import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FrontEndHttpServer extends Thread{
    public static HashMap<String, ArrayList<String>> sharedPeersInfo = new HashMap<>();
    public static HashMap<String, Long> sharedFileSize = new HashMap<>();

    public static long oneFileStart = 0;
    public static long bitRate = 0;
    int frontEndPort;
    int backEndPort;
    public FrontEndHttpServer(int frontEndPort, int backEndPort){
        this.frontEndPort = frontEndPort;
        this.backEndPort = backEndPort;
    }

    @Override
    public void run() {
        startServer();
    }

    public void startServer(){
        ServerSocket frontEndSocket = null;
        try {
            frontEndSocket = new ServerSocket(Integer.valueOf(this.frontEndPort));
        } catch (Exception e) {
            e.printStackTrace();
        }
        ExecutorService pool = Executors.newCachedThreadPool();
        // if (frontEndPort == 18343){
        //     File htmlFile = new File("html/views/homePage.html");
        //     try {
        //         Desktop.getDesktop().browse(htmlFile.toURI());
        //     } catch (IOException e) {
        //         e.printStackTrace();
        //     }
        // }
        while (true){
            Socket clientSocket = null;
            try {
                clientSocket = frontEndSocket.accept();
            } catch (IOException e){
                e.printStackTrace();
            }

            // System.out.println("Client IP: " + clientSocket.getInetAddress() + ": " + clientSocket.getPort());
            pool.execute(new Sender(clientSocket, Integer.valueOf(backEndPort)));
        }
    }
}

class Sender extends Thread{
    private Socket clientSocket;
    private int backEndPort;
    private DataOutputStream sOut = null;
    private DataInputStream in = null;
    private BufferedReader sIn = null;
    private File f = null;
    final String CRLF = "\r\n";
    //private int rate;
    //InetAddress peerIp;
    private String peerFilePath;
    //int peerPort;
    public Sender(Socket clientSocket, int backEndPort) {
        this.clientSocket = clientSocket;
        this.backEndPort = backEndPort;
    }
    @Override
    public void run() {
        try {
            sOut = new DataOutputStream(clientSocket.getOutputStream());
            sIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            HashMap<String, String> clientRequest = new HashMap<>();
            String inputLine;
            String[] info;

            while ((inputLine = sIn.readLine()) != null){
//                System.out.println("@Frontend: Get input from browser: " + inputLine);
                info = inputLine.split(" ");

                while (!(inputLine = sIn.readLine()).equals("")) {
                    //System.out.println("@Frontend: Get input from browser: " + inputLine);
                    String[] tmp = inputLine.split(": ");
                    clientRequest.put(tmp[0], tmp[1]);
                }
                if (info[1].equals("/")){
                    homePage();
                }
                else if (info[1].equals("/status/request")){
                    OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream());
                    String header = "HTTP/1.1 200 OK" + CRLF +
                            "Connection: " + "keep-alive" + CRLF +
                            "Access-Control-Allow-Origin: *" + CRLF + CRLF;
                    out.write(header);
                    JSONObject obj = new JSONObject();
                    String name = "";
                    for (String k : FrontEndHttpServer.sharedFileSize.keySet()){
                        name = k;
                        break;
                    }
                    obj.put("fileName",name);
                    obj.put("fileSize", FrontEndHttpServer.sharedFileSize.get(name));
                    obj.put("start", FrontEndHttpServer.oneFileStart);
                    obj.put("bitRate", FrontEndHttpServer.bitRate);
                    obj.put("progress", (double)FrontEndHttpServer.oneFileStart / FrontEndHttpServer.sharedFileSize.get(name));
                    out.write(obj.toString());
                    out.flush();
                    out.close();
                }
                // 请求本地文件
                else if (!info[1].startsWith("/peer")){
//                    System.out.println("@Frontend: 分析client header得出文件在本地");
                    f = findFile(info[1]);
                    if (f.exists() && !clientRequest.containsKey("Range")){
                        // System.out.println("response code: 200");
                        response200();
                        in.close();
                    }
                    else if (f.exists()){
                        String[] headTail = clientRequest.get("Range").split("bytes=")[1].split("-");
                        String tail = "";
                        if (headTail.length > 1) tail = headTail[1];
                        response206(headTail[0], tail);
                        in.close();
                    }
                    else {
                        // System.out.println("response code: 404");
                        response404();
                    }
                }
                //向peers请求文件
                else if(info[1].startsWith("/peer/kill")){
                    killThread();
                }
                //创造新的uuid
                else if(info[1].startsWith("/peer/uuid")){
                    UUID();
                }
                ///返回neighbors
                else if(info[1].startsWith("/peer/neighbors")){
                    sendNeighbors();
                }
                else if(info[1].startsWith("/peer/map")){
                    getMap();
                }
                else if(info[1].startsWith("/peer/rank")){
                    //String filePath = info[1].substring(10,info[1].length());
                    getRank(info[1]);
                }
                else if(info[1].startsWith("/peer/addneighbor")){
                    addNeighbor(info[1]);
                }
                else if(info[1].startsWith("/peer/search/")){
                    searchPeer(info[1]);
                }
                else{
                    //Store peers info.
                    //System.out.println("@Frontend: 分析client header得出文件在peers");
                    if (info[1].startsWith("/peer/add?path")) {
                        responseFake200();
                        System.out.println("start to store peer info");
                        String[] tmp = info[1].substring(15).split("&");
                        peerFilePath = tmp[0];
                        if (!FrontEndHttpServer.sharedPeersInfo.containsKey(peerFilePath)) {
                            FrontEndHttpServer.sharedPeersInfo.put(peerFilePath, new ArrayList<>());
                        }
                        FrontEndHttpServer.sharedPeersInfo.get(peerFilePath).add(info[1].substring(15));
                        System.out.println(info[1]);
//                        System.out.println("mapsize: " + FrontEndHttpServer.sharedPeersInfo.size()+" peersNum: " + FrontEndHttpServer.sharedPeersInfo.get(peerFilePath).size());
                        System.out.println("finish store peer info");
                        break;
                    }
                    else if (info[1].startsWith("/peer/view")){
                        //System.out.println("@Frontend: client请求文件（200/206）");
                        //info[1]: peer/view/content/test.png
                        String nameKey = info[1].substring(11);
                        //System.out.println("namekey: "+nameKey);
                        if (!clientRequest.containsKey("Range") || clientRequest.get("Range").equals("bytes=0-")){
                            System.out.println("start to response200 to browser");
                            //TODO 根据rank分块向peer请求文件
//                            httpRetransfer200New(info[1]);
                            response200BasedOnRank(info[1]);
                            System.out.println("finish response200 to browser");
                        }
                        else {
                            System.out.println("start to response206 to browser");
                            String[] headTail = clientRequest.get("Range").split("bytes=")[1].split("-");
                            String tail = ""+FrontEndHttpServer.sharedFileSize.get(nameKey);
                            long max = 5 * 1000 * 1000;
                            if (headTail.length > 1) tail = headTail[1];
                            else if (Long.valueOf(tail) > Long.valueOf(headTail[0]) + max){
                                tail = String.valueOf((Long.valueOf(headTail[0]) + max));
                            }
                            //System.out.println("@Frontend: head: " + headTail[0] +" tail: "+tail);
                            //TODO 根据rank分块向peer请求文件
//                            httpRetransfer206(info[1], headTail[0], tail);
                            response206BasedOnRank(info[1], headTail[0], tail);
                            System.out.println("finish response206 to browser");
                        }
                    }
                    else if (info[1].startsWith("/peer/status")){
                        responseFake200();
                        //TODO ben's responsibility
                        File htmlFile = new File("html/views/status.html");
                        try {
                            Desktop.getDesktop().browse(htmlFile.toURI());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else if (info[1].startsWith("/peer/config")){
                        //TODO CFR's responsibility
                        // /peer/config?rate=<bytes/s>
                        System.out.println("start to config rate");
                        responseFake200();
                        String rate = info[1].split("rate=")[1];
                        for (String fileName : FrontEndHttpServer.sharedPeersInfo.keySet()){
                            List<String> peers = FrontEndHttpServer.sharedPeersInfo.get(fileName);
                            for (int i = 0; i < peers.size(); i++){
                                if(peers.get(i).contains("rate")){
                                    peers.set(i, peers.get(i).split("rate")[0] + "rate=" + rate);
                                }
                                else {
                                    peers.set(i, peers.get(i) + "&rate=" + rate);
                                }
                                System.out.println("peer info: " + peers.get(i));
                            }
                        }
                        System.out.println("finish config rate");
                    }
                    else {
                        response404();
                    }
                }
                break;
            }
            sOut.close();
            sIn.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        return file;
    }
    public int convertByteToInt(byte[] bytes, int start){
        return ((bytes[start + 0] & 0xFF) << 24) |
                ((bytes[start + 1] & 0xFF) << 16) |
                ((bytes[start + 2] & 0xFF) << 8 ) |
                ((bytes[start + 3] & 0xFF) << 0 );
    }
    private File findFile(String path) {
        File file = new File("." + path);
        if (!file.exists()) file = new File("./content" + path);
        return file;
    }

    private void responseFake200() throws IOException {
        String header = "HTTP/1.1 200 OK" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF + CRLF;
        sOut.writeUTF(header);
        sOut.flush();
    }
    private void response200() throws IOException {
        //header
        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        Date lastModified = new Date(f.lastModified());
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
//        if (fType.equals("audio/ogg")){
//            fType = "video/ogg";
//        }
        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + f.length() + CRLF +
                "Content-Type: " + fType + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF;
        try {
//            System.out.println(header);
            FileInputStream fis = new FileInputStream(f);
            in = new DataInputStream(fis);
            byte[] bytes = new byte[1000 * 1000];
            int length;
            sOut.writeUTF(header);
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {
                sOut.write(bytes, 0, length);
                sOut.flush();
            }
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void response206(String head, String tail) throws IOException{
        long startByte = Long.parseLong(head);
        long endByte;
        long max = 1000 * 1000 * 20;
        if (tail.equals("")){
            endByte = startByte + max;
        }
        else{
            endByte = Long.parseLong(tail);
        }
        endByte = Math.min(endByte, f.length());

        String fType = URLConnection.guessContentTypeFromName(this.f.getName());
        Date date = new Date();
        Date lastModified = new Date(f.lastModified());
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
//        if (fType.equals("audio/ogg")){
//            fType = "video/ogg";
//        }
        //form and send header
        String header = "HTTP/1.1 206 Partial Content" + CRLF +
                "Content-Length: " + (endByte - startByte) + CRLF +
                "Content-Type: " + fType + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Content-Range: " + "bytes " + startByte + "-" + endByte + "/" + this.f.length() +  CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF;
        // System.out.println(header);
        try {
            FileInputStream fis = new FileInputStream(f);
            in = new DataInputStream(fis);
            byte[] bytes = new byte[1000 * 1000]; //1MB
            sOut.writeUTF(header);
            max = endByte - startByte;
            int length;
            in.skip(startByte);  //Skip 'startbytes' bytes tp reach the start point
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {
                System.out.println("send");
                if (max <= length){
                    sOut.write(bytes, 0, (int)max);
                    sOut.flush();
                    break;
                }
                else {
                    max -= length;
                    sOut.write(bytes, 0, length);
                    sOut.flush();
                }
            }

            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void response404() throws IOException{
        String header = "HTTP/1.1 404 Not Found"  + CRLF + CRLF;
        sOut.writeUTF(header);
        sOut.flush();
    }
    private void httpRetransfer200(String info) throws IOException {
//        send info to backend listener
        peerFilePath = info.substring(11);
        ArrayList<String> peerInfo = FrontEndHttpServer.sharedPeersInfo.get(peerFilePath);
        //向几个peers要文件就发送几次报文
        //System.out.println("@Frontend/httpRetransfer200: 向peers发送请求报文");
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(1000);

        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<>();

//        byte[] recArr = new byte[409600];
        String filePath = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = 0;
        boolean headerFlag = false;

        for(int i = 0; i < peerInfo.size(); i++){
            //length 表示总共开了多少个peers
            //start：向后端传递你是第几个peer
            long start = -(i+1);
            long length = -peerInfo.size();
            int rate = 0;
            String[] tmp = peerInfo.get(i).split("&");
            InetAddress peerIp = InetAddress.getByName(tmp[1].substring(5));
            int peerPort = Integer.valueOf(tmp[2].substring(5));
            if (tmp.length > 3){
                rate = Integer.valueOf(tmp[3].substring(5));
            }
            FrontEndHttpServer.bitRate = rate;
            String message = JSONObject.toJSONString(new ListenerHeader(0, InetAddress.getByName("127.0.0.1"), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
            byte[] sendArr = message.getBytes();
            DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
            dsock.send(dpack);
            //System.out.println("@Frontend/httpRetransfer200: peers_" + i + " 发送成功");
            //System.out.println("@Frontend/httpRetransfer200: message" + i + ": " + message.toString());
        }
        //System.out.println("@Frontend/httpRetransfer200: peers全发送成功");

        //wait for response
        //一直向所有后端接收
        //System.out.println("@Frontend/httpRetransfer200: 开始从peers接受并转发");
        while(true) {
            byte[] recArr = new byte[204800];
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                continue;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);

/////////////////////////////////////////// todo: mark!!!!!!!!!!!!!
            long ackStart = header.start;
            long ackLen = header.length;
            String ack = "start:"+ackStart + "/len:" + ackLen;
            byte[] ackb = ack.getBytes();
            DatagramPacket ACKPack = new DatagramPacket(ackb, ackb.length, dpack.getAddress(), dpack.getPort());
            dsock.send(ACKPack);


//            System.out.println(header.toString());
            //judge header
            if (header.statusCode == 0) {
                if(headerFlag == true) continue;

                long fileLen = header.getLength();
                filePath = header.getFileName();
                lastModified = header.getLastModified();
                //System.out.println("namekey in map:" + filePath);
                FrontEndHttpServer.sharedFileSize.put(filePath,fileLen);
                //form header
                String fType = URLConnection.guessContentTypeFromName(filePath);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                if (fType.equals("audio/ogg")){
                    fType = "video/ogg";
                }
                httpHeader = "HTTP/1.1 200 OK" + CRLF +
                        "Content-Length: " + fileLen + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Cache-Control: " + "public" + CRLF +
                        "Connection: " + "keep-alive" + CRLF +
                        "Access-Control-Allow-Origin: *" + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
                        "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF; //todo:可能有问题
                if(headerFlag == false){
                    sOut.writeUTF(httpHeader);
                    headerFlag = true;
                    //System.out.println("@Frontend/httpRetransfer200: 200 header发送成功");
                }
            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                fileMap.put(header.start, content);
                pq.add(header.start);

                //发送200给browser
                if(headerFlag == true){
                    while(pq.size() != 0 && mapPointer == pq.peek()){
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        try{
                            sOut.write(bytes, 0, bytes.length);
                            sOut.flush();
                        }catch (SocketException e){
                            String closeAck = "close";
                            byte[] closeByte = closeAck.getBytes();
                            DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                            dsock.send(closeACKPack);
                            return;
                        }
                        long top = pq.poll();
                        FrontEndHttpServer.oneFileStart += fileMap.get(top).length;
                        mapPointer += fileMap.get(top).length;  //get 为空
                    }
                }
                if(mapPointer >= FrontEndHttpServer.sharedFileSize.get(peerFilePath)){
                    break;
                }
                //System.out.println("@Frontend/httpRetransfer200: 200 content发送...");
            }
            else { //Not found// todo: deal with not found
                response404();
                break;
            }
        }
    }

    private void response200BasedOnRank(String info) throws IOException {
//        send info to backend listener
        peerFilePath = info.substring(11);
        //向几个peers要文件就发送几次报文
        //System.out.println("@Frontend/httpRetransfer200: 向peers发送请求报文");
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(5000);

        //将/peer/rank/<filePath>报文发送后端，后端返回dijkstra List & address of each node
        byte[] sendArr = ("/peer/rank/" + peerFilePath).getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        //TODO 如果为空怎么办
        String tmp1 = new String(dpack.getData()).trim().split("&")[1]; // tmp1 -> address
        JSONObject map = JSONObject.parseObject(tmp1);                        // map -> address Json map

        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<>();

//        byte[] recArr = new byte[409600];
        String filePath = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = 0;
        boolean headerFlag = false;

//      TODO peerInfo:  content/test.ogg&host=172.16.7.12&port=18344&rate=80000
        ArrayList<String> peerInfo = new ArrayList<>();
        for (String s : map.keySet()){
            String ip = map.get(s).toString().split(",")[0];
            String port = map.get(s).toString().split(",")[1];
            peerInfo.add(peerFilePath + "&host=" + ip + "&port=" + port + "&rate=" + 80000);
        }
        System.out.println(peerInfo.toString());
        FrontEndHttpServer.sharedPeersInfo.put(peerFilePath, peerInfo);      //sharedPeersInfo -> 根据fileName（path）存含有该文件的peers的信息，threads间共享
        List<String> addressFor200 = devideContent(new String(dpack.getData()).trim().getBytes());  // addressFor200 -> devideContent() output: ["ip,port","ip,port"...]


        HashMap<String, Integer> peersNum = new HashMap<>();
        for(String s : addressFor200){
            peersNum.put(s, peersNum.getOrDefault(s,0)+1);
        }
        System.out.println("@ response200BasedOnRank: Mapsize: " + peersNum.size());
        for(String s : peersNum.keySet()){
            System.out.println("@ response200BasedOnRank: key: " + s + "value: " + peersNum.get(s));
        }

        //TODO
        for(int i = 0; i < addressFor200.size(); i++){
            //length 表示总共开了多少个peers
            //start：向后端传递你是第几个peer
            long start = -(i+1);
            long length = -addressFor200.size();
            int rate = 80000;
            FrontEndHttpServer.bitRate = rate;
            InetAddress peerIp = InetAddress.getByName(addressFor200.get(i).split(",")[0]);
            int peerPort = Integer.valueOf(addressFor200.get(i).split(",")[1]);

            String message = JSONObject.toJSONString(new ListenerHeader(0, InetAddress.getByName("127.0.0.1"), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
            sendArr = message.getBytes();
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
            dsock.send(dpack);
            //System.out.println("@Frontend/httpRetransfer200: peers_" + i + " 发送成功");
            //System.out.println("@Frontend/httpRetransfer200: message" + i + ": " + message.toString());
        }

        //wait for response
        //一直向所有后端接收
        //System.out.println("@Frontend/httpRetransfer200: 开始从peers接受并转发");
        //TODO
        while(true) {
            recArr = new byte[204800];
            dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                continue;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);

/////////////////////////////////////////// todo: mark!!!!!!!!!!!!!
            long ackStart = header.start;
            long ackLen = header.length;
            String ack = "start:"+ackStart + "/len:" + ackLen;
            byte[] ackb = ack.getBytes();
            DatagramPacket ACKPack = new DatagramPacket(ackb, ackb.length, dpack.getAddress(), dpack.getPort());
            dsock.send(ACKPack);


//            System.out.println(header.toString());
            //judge header
            if (header.statusCode == 0) {
                if(headerFlag == true) continue;

                long fileLen = header.getLength();
                filePath = header.getFileName();
                lastModified = header.getLastModified();
                //System.out.println("namekey in map:" + filePath);
                FrontEndHttpServer.sharedFileSize.put(filePath,fileLen);
                //form header
                String fType = URLConnection.guessContentTypeFromName(filePath);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                if (fType.equals("audio/ogg")){
                    fType = "video/ogg";
                }
                httpHeader = "HTTP/1.1 200 OK" + CRLF +
                        "Content-Length: " + fileLen + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Cache-Control: " + "public" + CRLF +
                        "Connection: " + "keep-alive" + CRLF +
                        "Access-Control-Allow-Origin: *" + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
                        "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF; //todo:可能有问题
                if(headerFlag == false){
                    sOut.writeUTF(httpHeader);
                    headerFlag = true;
                    //System.out.println("@Frontend/httpRetransfer200: 200 header发送成功");
                }
            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                fileMap.put(header.start, content);
                pq.add(header.start);

                //发送200给browser
                if(headerFlag == true){
                    while(pq.size() != 0 && mapPointer == pq.peek()){
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        try{
                            sOut.write(bytes, 0, bytes.length);
                            sOut.flush();
                        }catch (SocketException e){
                            String closeAck = "close";
                            byte[] closeByte = closeAck.getBytes();
                            DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                            dsock.send(closeACKPack);
                            return;
                        }
                        long top = pq.poll();
                        FrontEndHttpServer.oneFileStart += fileMap.get(top).length;
                        mapPointer += fileMap.get(top).length;  //get 为空
                    }
                }
                if(mapPointer >= FrontEndHttpServer.sharedFileSize.get(peerFilePath)){
                    break;
                }
                //System.out.println("@Frontend/httpRetransfer200: 200 content发送...");
            }
            else { //Not found// todo: deal with not found
                response404();
                break;
            }
        }
    }
    private void httpRetransfer206(String info, String head, String tail) throws IOException {
//        send info to backend listener
        peerFilePath = info.substring(11);
        ArrayList<String> peerInfo = FrontEndHttpServer.sharedPeersInfo.get(peerFilePath);
        long splitSize = (Long.parseLong(tail) - Long.parseLong(head)) / peerInfo.size();

        //向几个peers要文件就发送几次报文
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(1000);
        long start = Long.parseLong(head);
        long length = splitSize;

        for(int i = 0; i < peerInfo.size(); i++){
            if(i == peerInfo.size()-1) length = (Long.parseLong(tail)-start);
            int rate = 0;

            String[] tmp = peerInfo.get(i).split("&");
            InetAddress peerIp = InetAddress.getByName(tmp[1].substring(5));
            int peerPort = Integer.valueOf(tmp[2].substring(5));
            if (tmp.length > 3){
                rate = Integer.valueOf(tmp[3].substring(5));
            }
            FrontEndHttpServer.bitRate = rate;
            String message = JSONObject.toJSONString(new ListenerHeader(0, InetAddress.getByName("127.0.0.1"), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
            byte[] sendArr = message.getBytes();
            DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
            dsock.send(dpack);
            start += splitSize;
            //System.out.println("@Frontend/httpRetransfer206: peers_" + i + " 发送成功");
            //System.out.println("@Frontend/httpRetransfer206: message" + i + ": " + message.toString());
        }

        //wait for response
        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<Long>();
        HashSet<Long> visited = new HashSet<>();
//        byte[] recArr = new byte[102400];
        String fileName = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = Long.parseLong(head);
        boolean headerFlag = false;

        //一直向所有后端接收

        while(true) {
            byte[] recArr = new byte[204800];
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                System.out.println("waiting for data: " + mapPointer);
                if (!fileMap.containsKey(mapPointer)) continue;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());
            //judge header

            /////////////////////////////////////////// todo: mark!!!!!!!!!!!!!
//            if (dpack.getAddress() != null){
                long ackStart = header.start;
                long ackLen = header.length;
                String ack = "start:"+ackStart + "/len:" + ackLen;
//                System.out.println("收到文件start: " + ack);
                byte[] ackb = ack.getBytes();
                DatagramPacket ACKPack = new DatagramPacket(ackb, ackb.length, dpack.getAddress(), dpack.getPort());
                dsock.send(ACKPack);

//            }


            if (header.statusCode == 0) {
                //System.out.println("fileName: " + fileName);
                fileName = header.getFileName();
                lastModified = header.getLastModified();
                //form header
                String fType = URLConnection.guessContentTypeFromName(fileName);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                if (fType.equals("audio/ogg")){
                    fType = "video/ogg";
                }
                httpHeader = "HTTP/1.1 206 OK" + CRLF +
                        "Content-Length: " + (Long.parseLong(tail)-Long.parseLong(head)) + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Cache-Control: " + "public" + CRLF +
                        "Connection: " + "keep-alive" + CRLF +
                        "Access-Control-Allow-Origin: *" + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
                        "Content-Range: " + "bytes " + head + "-" + tail + "/" + FrontEndHttpServer.sharedFileSize.get(fileName) +  CRLF +
                        "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF;
                if(headerFlag == false){
                    sOut.writeUTF(httpHeader);
                    headerFlag = true;
                }

            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                if(!visited.contains(header.start)){
                    fileMap.put(header.start, content);
                    pq.add(header.start);
                    visited.add(header.start);
                }
                else {
                    continue;
                }
//                System.out.println("@Frontend: fileMap size: " + fileMap.size() + " ");

                //发送206给browser
                if(headerFlag == true){
                    while(pq.size() != 0 && mapPointer == pq.peek()){
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        try{
                            sOut.write(bytes, 0, bytes.length);
                            sOut.flush();
//                            System.out.println("mapPointer: " + mapPointer + " pq.peek(): " + pq.peek() + " pq.size(): " + pq.size());
                        }catch (SocketException e){
                            System.out.println("browser close http");
                            String closeAck = "close";
                            byte[] closeByte = closeAck.getBytes();
                            DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                            dsock.send(closeACKPack);
                            return;
                        }
                        long top = pq.poll();
//                        System.out.println("top: " + top);
                        FrontEndHttpServer.oneFileStart += fileMap.get(top).length;
                        mapPointer += fileMap.get(top).length;  //get 为空
                    }
                }
//                if(mapPointer >= FrontEndHttpServer.sharedFileSize.get(peerFilePath)){
//                    break;
//                }
                if(mapPointer >= Long.valueOf(tail)){
                    String closeAck = "close";
                    byte[] closeByte = closeAck.getBytes();
                    DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                    dsock.send(closeACKPack);
                    break;
                }
            }
            else { //Not found// todo: deal with not found
                response404();
                break;
            }
        }

    }
    private void response206BasedOnRank(String info, String head, String tail) throws IOException{
        //        send info to backend listener
        peerFilePath = info.substring(11);
        //向几个peers要文件就发送几次报文
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(1000);
        //将/peer/rank/<filePath>报文发送后端，后端返回dijkstra List & address of each node
        byte[] sendArr = ("/peer/rank/" + peerFilePath).getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        //TODO 如果为空怎么办

        List<String> addressFor206 = devideContent(new String(dpack.getData()).trim().getBytes());  // addressFor206 -> devideContent() output: ["ip,port","ip,port"...]
        long splitSize = (Long.parseLong(tail) - Long.parseLong(head)) / (long)addressFor206.size();
        System.out.println("@ response206BasedOnRank: head: " + Long.parseLong(head));
        System.out.println("@ response206BasedOnRank: tail: " + Long.parseLong(tail));
        System.out.println("@ response206BasedOnRank: Splitesize: " + splitSize);
        //ArrayList<String> peerInfo = FrontEndHttpServer.sharedPeersInfo.get(peerFilePath);
        long start = Long.parseLong(head);

        // store the number of different peers in addressFor206
        HashMap<String, Integer> peersNum = new HashMap<>();
        for(String s : addressFor206){
            peersNum.put(s, peersNum.getOrDefault(s,0)+1);
        }

        System.out.println("@ response206BasedOnRank: Mapsize: " + peersNum.size());
        for(String s : peersNum.keySet()){
            System.out.println("@ response206BasedOnRank: key: " + s + " value: " + peersNum.get(s));
        }


        int idx = 0;
        for(String s : peersNum.keySet()){
            long length = splitSize * peersNum.get(s);
            System.out.println("@ response206BasedOnRank: peersNum.get(s): " + peersNum.get(s));
            System.out.println("@ response206BasedOnRank: idx: " + idx);
            if(idx == peersNum.size()-1) length = (Long.parseLong(tail)-start);
            System.out.println("@ response206BasedOnRank: start: " + start);
            System.out.println("@ response206BasedOnRank: length: " + length);
            int rate = 80000;
            FrontEndHttpServer.bitRate = rate;
            InetAddress peerIp = InetAddress.getByName(s.split(",")[0]);
            int peerPort = Integer.valueOf(s.split(",")[1]);

            String message = JSONObject.toJSONString(new ListenerHeader(0, InetAddress.getByName("127.0.0.1"), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
            sendArr = message.getBytes();
            dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
            dsock.send(dpack);
            start += length;
            idx++;
            //System.out.println("@Frontend/httpRetransfer206: peers_" + i + " 发送成功");
            //System.out.println("@Frontend/httpRetransfer206: message" + i + ": " + message.toString());
        }

        //wait for response
        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<Long>();
        HashSet<Long> visited = new HashSet<>();
//        byte[] recArr = new byte[102400];
        String fileName = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = Long.parseLong(head);
        boolean headerFlag = false;

        //一直向所有后端接收

        while(true) {
            recArr = new byte[204800];
            dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                System.out.println("waiting for data: " + mapPointer);
                if (!fileMap.containsKey(mapPointer)) continue;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());
            //judge header

            /////////////////////////////////////////// todo: mark!!!!!!!!!!!!!
//            if (dpack.getAddress() != null){
            long ackStart = header.start;
            long ackLen = header.length;
            String ack = "start:"+ackStart + "/len:" + ackLen;
//                System.out.println("收到文件start: " + ack);
            byte[] ackb = ack.getBytes();
            DatagramPacket ACKPack = new DatagramPacket(ackb, ackb.length, dpack.getAddress(), dpack.getPort());
            dsock.send(ACKPack);

//            }


            if (header.statusCode == 0) {
                //System.out.println("fileName: " + fileName);
                fileName = header.getFileName();
                lastModified = header.getLastModified();
                //form header
                String fType = URLConnection.guessContentTypeFromName(fileName);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                if (fType.equals("audio/ogg")){
                    fType = "video/ogg";
                }
                httpHeader = "HTTP/1.1 206 OK" + CRLF +
                        "Content-Length: " + (Long.parseLong(tail)-Long.parseLong(head)) + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Cache-Control: " + "public" + CRLF +
                        "Connection: " + "keep-alive" + CRLF +
                        "Access-Control-Allow-Origin: *" + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
                        "Content-Range: " + "bytes " + head + "-" + tail + "/" + FrontEndHttpServer.sharedFileSize.get(fileName) +  CRLF +
                        "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF;
                if(headerFlag == false){
                    sOut.writeUTF(httpHeader);
                    headerFlag = true;
                }

            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                if(!visited.contains(header.start)){
                    fileMap.put(header.start, content);
                    pq.add(header.start);
                    visited.add(header.start);
                }
                else {
                    continue;
                }
//                System.out.println("@Frontend: fileMap size: " + fileMap.size() + " ");

                //发送206给browser
                if(headerFlag == true){
                    while(pq.size() != 0 && mapPointer == pq.peek()){
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        try{
                            sOut.write(bytes, 0, bytes.length);
                            sOut.flush();
//                            System.out.println("mapPointer: " + mapPointer + " pq.peek(): " + pq.peek() + " pq.size(): " + pq.size());
                        }catch (SocketException e){
                            System.out.println("browser close http");
                            String closeAck = "close";
                            byte[] closeByte = closeAck.getBytes();
                            DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                            dsock.send(closeACKPack);
                            return;
                        }
                        long top = pq.poll();
//                        System.out.println("top: " + top);
                        FrontEndHttpServer.oneFileStart += fileMap.get(top).length;
                        mapPointer += fileMap.get(top).length;  //get 为空
                    }
                }
//                if(mapPointer >= FrontEndHttpServer.sharedFileSize.get(peerFilePath)){
//                    break;
//                }
                if(mapPointer >= Long.valueOf(tail)){
                    String closeAck = "close";
                    byte[] closeByte = closeAck.getBytes();
                    DatagramPacket closeACKPack = new DatagramPacket(closeByte, closeByte.length, dpack.getAddress(), dpack.getPort());
                    dsock.send(closeACKPack);
                    break;
                }
            }
            else { //Not found// todo: deal with not found
                response404();
                break;
            }
        }

    }
    private void killThread() throws IOException{
        responseFake200();
        System.exit(1);
    }
    private void UUID()throws IOException{
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        String message = "/peer/uuid";
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + "2048" + CRLF +
                "Content-Type: " + "text/plain" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        //send to page
        try {
            int length;
            sOut.writeUTF(header);
            byte[] uuid = dpack.getData();
            sOut.write(new String(uuid).trim().getBytes());
            sOut.flush();
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendNeighbors() throws IOException{
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        String message = "/peer/neighbors";
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + "2048" + CRLF +
                "Content-Type: " + "text/plain" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        //send to page
        try {
            int length;
            sOut.writeUTF(header);
            byte[] neighbor = dpack.getData();
            sOut.write(new String(neighbor).trim().getBytes());
            sOut.flush();
//            }
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getMap() throws IOException {
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        String message = "/peer/map";
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + "2048" + CRLF +
                "Content-Type: " + "text/plain" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        //send to page
        try {
            int length;
            sOut.writeUTF(header);
            byte[] bytes = dpack.getData();
            sOut.write(new String(bytes).trim().getBytes());
            sOut.flush();
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getRank(String filePath) throws IOException {
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        String message = filePath;
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + "2048" + CRLF +
                "Content-Type: " + "text/plain" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        //send to page
        try {
            int length;
            sOut.writeUTF(header);
            byte[] bytes = dpack.getData();
            String tmp = new String(bytes).trim();
            sOut.write(tmp.split("&")[0].getBytes());
            sOut.flush();
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    private void addNeighbor(String message) throws IOException {
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend

        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        responseFake200();

    }

    private void searchPeer(String filePath) throws IOException {
        //send to backend
        DatagramSocket dsock = new DatagramSocket();
        String message = filePath;
        byte[] sendArr = message.getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + "2048" + CRLF +
                "Content-Type: " + " application/json" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        //send to page
        try {
            int length;
            sOut.writeUTF(header);
            byte[] bytes = dpack.getData();
            JSONArray output = new JSONArray();
            JSONObject hm = JSONObject.parseObject(new String(bytes).trim());
            for (String f : hm.keySet()){
                JSONObject outputHm = new JSONObject();
                outputHm.put("peers", hm.get(f));
                outputHm.put("content", f);
                output.add(outputHm);
            }
//            output.put("content", "./" + filePath.split("search/")[1]);
//            output.put("peers",  new String(bytes).trim());
//            sOut.write(output.toJSONString().getBytes());
            sOut.write(output.toJSONString().getBytes());
            sOut.flush();
            // System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*根据dijkstra分割文件：
            List<String> orderedList = new ArrayList<>();
            orderedList.add("nodeA: 10");
            orderedList.add("nodeB: 20");
            orderedList.add("nodeC: 30");

            JSONObject address = new JSONObject();
            address.put("nodeA", "172.16.7.18,18344");
            address.put("nodeB", "172.16.7.10,18344");
            address.put("nodeC", "172.16.7.42,18344");


            String s1 = orderedList.toString();
            String s2 = "&";  // 分隔符
            String s3 = address.toJSONString();
            byte[] input = (s1 + s2 + s3).getBytes();


            output: List<String>: ["172.16.7.18,18344", "172.16.7.18,18344", "172.16.7.18,18344",
                                   "172.16.7.10,18344", "172.16.7.10,18344",
                                   "172.16.7.42,18344"];
            */
    private List<String> devideContent(byte[] input){
        List<String> res = new ArrayList<>();
        String[] tmp = new String(input).split("&");
        // address: Json;
        // distanceMap: <String, int>;
        JSONObject address = JSONObject.parseObject(tmp[1]);
        Map<String, Integer> distanceMap = new HashMap<>();

        String distanceString = tmp[0].substring(1, tmp[0].length()-1);
        String[] nodeDist = distanceString.split(", ");
        System.out.println(distanceString);

        // delete nodes that are too far away
        int i = 0;
        while(i < nodeDist.length && Double.valueOf(nodeDist[0].split(": ")[1]) / Double.valueOf(nodeDist[i].split(": ")[1]) > 0.1){
            distanceMap.put(nodeDist[i].split(": ")[0], Integer.valueOf(nodeDist[i].split(": ")[1]));
            i++;
        }

        int limit = Math.min(10, distanceMap.size()*2);
        Map<String, Integer> sortedPeers = distanceMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.max(1, Math.min(limit, limit+1 - entry.getValue() / limit)), // assign an integer value based on the distance
                        (oldValue, newValue) -> oldValue, // keep the old value if keys are duplicated
                        HashMap::new
                ));
        System.out.println(sortedPeers);

        // generate result
        for(int k = 0; k < distanceMap.size(); k++){
            String s = nodeDist[k].split(": ")[0];
            int times = sortedPeers.get(s);
            for(int j = 0; j < times; j++){
                res.add(address.get(s).toString());
            }
        }
        return res;
    }
    private void homePage() throws IOException {
        OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream());
        DatagramSocket dsock = new DatagramSocket();
        byte[] sendArr = "/".getBytes();
        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, InetAddress.getByName("127.0.0.1"), backEndPort);
        dsock.send(dpack);
        //hear from backend
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Type: " + " application/json" + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
                "Access-Control-Allow-Origin: *" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + " GMT" + CRLF + CRLF;
        out.write(header);
        byte[] recArr = new byte[2048];
        dpack = new DatagramPacket(recArr, recArr.length);
        dsock.receive(dpack);

//        obj.put("key", JSONArray.parseArray(new String(recArr).trim()).toJSONString());

        out.write(JSONArray.parseArray(new String(recArr).trim()).toJSONString());
        out.flush();
        out.close();
    }
}