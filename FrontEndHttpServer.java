import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrontEndHttpServer extends Thread{
    public static HashMap<String, ArrayList<String>> sharedPeersInfo = new HashMap<>();
    public static HashMap<String, Long> sharedFileSize = new HashMap<>();

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

    public static void main(String[] args) {
        FrontEndHttpServer server = new FrontEndHttpServer(8080, 8081);
        server.startServer();
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
                System.out.println("@Frontend: Get input from browser: " + inputLine);
                info = inputLine.split(" ");

                while (!(inputLine = sIn.readLine()).equals("")) {
                    //System.out.println("@Frontend: Get input from browser: " + inputLine);
                    String[] tmp = inputLine.split(": ");
                    clientRequest.put(tmp[0], tmp[1]);
                }
                // System.out.println(info[1]);


                // 请求本地文件
                if (!info[1].startsWith("/peer")){
                    System.out.println("@Frontend: 分析client header得出文件在本地");
                    f = findFile(info[1]);
                    if (f.exists() && !clientRequest.containsKey("Range")){
                        // System.out.println("response code: 200");
                        response200();
                    }
                    else if (f.exists()){
                        // System.out.println("response code: 206");
                        // System.out.println(request.get("Range"));
                        String[] headTail = clientRequest.get("Range").split("bytes=")[1].split("-");
                        String tail = "";
                        if (headTail.length > 1) tail = headTail[1];
                        response206(headTail[0], tail);
                    }
                    else {
                        // System.out.println("response code: 404");
                        response404();
                    }
                    if (in != null) {
                        in.close();
                    }
                }
                //向peers请求文件
                else{
                    //Store peers info.
                    //System.out.println("@Frontend: 分析client header得出文件在peers");
                    if (info[1].startsWith("/peer/add?path")) {
                        //System.out.println("@Frontend: 开始储存peers信息");
                        String[] tmp = info[1].substring(15).split("&");
                        peerFilePath = tmp[0];
                        if (!FrontEndHttpServer.sharedPeersInfo.containsKey(peerFilePath)) {
                            FrontEndHttpServer.sharedPeersInfo.put(peerFilePath, new ArrayList<>());
                        }
                        FrontEndHttpServer.sharedPeersInfo.get(peerFilePath).add(info[1].substring(15));
                        //System.out.println("mapsize: " + FrontEndHttpServer.sharedPeersInfo.size()+" peersNum: " + FrontEndHttpServer.sharedPeersInfo.get(peerFilePath).size());
                        System.out.println("@Frontend: peers信息储存成功");
                        continue;
                    }
                    else if (info[1].startsWith("/peer/view")){
                        //System.out.println("@Frontend: client请求文件（200/206）");
                        //info[1]: peer/view/content/test.png
                        String nameKey = info[1].substring(11);
                        //System.out.println("namekey: "+nameKey);
                        if (!clientRequest.containsKey("Range")){
                            System.out.println("@Frontend: 向client发送200...");
                            httpRetransfer200(info[1]);
                            System.out.println("@Frontend: 向client发送200成功");
                        }
                        else {
                            //TODO LZJ's responsibility
                            System.out.println("@Frontend: 向client发送206...");
                            String[] headTail = clientRequest.get("Range").split("bytes=")[1].split("-");
                            String tail = ""+FrontEndHttpServer.sharedFileSize.get(nameKey);
                            long max = 5 * 1000 * 1000;
                            if (headTail.length > 1) tail = headTail[1];
                            else if (Long.valueOf(tail) > Long.valueOf(headTail[0]) + max){
                                tail = String.valueOf((Long.valueOf(headTail[0]) + max));
//                                System.out.println("yes");
                            }
                            //System.out.println("@Frontend: head: " + headTail[0] +" tail: "+tail);
                            httpRetransfer206(info[1], headTail[0], tail);
                            System.out.println("@Frontend: 向client发送206成功");
                        }
                    }
                    else if (info[1].startsWith("/peer/status")){
                        //TODO ben's responsibility
                    }
                    else if (info[1].startsWith("/peer/config")){
                        //TODO CFR's responsibility
                    }
                    else {
                        response404();
                    }
                }
                break;
            }
            System.out.println("@Frontend: ########close###########");
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
    private void response200() throws IOException {
        //header
        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        Date lastModified = new Date(f.lastModified());
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        if (fType.equals("audio/ogg")){
            fType = "video/ogg";
        }
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
        String fType = URLConnection.guessContentTypeFromName(this.f.getName());
        Date date = new Date();
        Date lastModified = new Date(f.lastModified());
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        if (fType.equals("audio/ogg")){
            fType = "video/ogg";
        }
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

        byte[] recArr = new byte[204800];
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
                            System.out.println(closeAck + mapPointer);
                            return;
                        }
                        mapPointer += fileMap.get(pq.poll()).length;  //get 为空
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

        byte[] recArr = new byte[204800];
        String fileName = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = Long.parseLong(head);
        boolean headerFlag = false;

        //一直向所有后端接收

        while(true) {
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                System.out.println("waiting for data: " + mapPointer);
                System.out.println(mapPointer + " 是否存在： " + fileMap.containsKey(mapPointer));
                System.out.println(pq.peek());
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
                fileMap.put(header.start, content);
                pq.add(header.start);
//                System.out.println("@Frontend: fileMap size: " + fileMap.size() + " ");

                //发送206给browser
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
                            System.out.println(closeAck + mapPointer);
                            return;
                        }
                        mapPointer += fileMap.get(pq.poll()).length;  //get 为空
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
                    System.out.println(closeAck);
                    break;
                }
            }
            else { //Not found// todo: deal with not found
                response404();
                break;
            }
        }

    }

}