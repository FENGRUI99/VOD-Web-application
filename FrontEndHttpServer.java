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
    public static HashMap<String, ArrayList<String>> threadShare = new HashMap<>();
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
            HashMap<String, String> request = new HashMap<>();
            String inputLine;
            String[] info;

            while ((inputLine = sIn.readLine()) != null){
                System.out.println(inputLine);
                info = inputLine.split(" ");
                while (!(inputLine = sIn.readLine()).equals("")) {
                    String[] tmp = inputLine.split(": ");
                    request.put(tmp[0], tmp[1]);
                }
                // System.out.println(info[1]);

                // 请求本地文件
                if (!info[1].startsWith("/peer")){
                    f = findFile(info[1]);
                    if (f.exists() && !request.containsKey("Range")){
                        // System.out.println("response code: 200");
                        response200();
                    }
                    else if (f.exists()){
                        // System.out.println("response code: 206");
                        // System.out.println(request.get("Range"));
                        String[] headTail = request.get("Range").split("bytes=")[1].split("-");
                        String tail = "";
                        if (headTail.length > 1) tail = headTail[1];
                        response206(headTail[0], tail);
                    }
                    else {
                        // System.out.println("response code: 404");
                        response404();
                    }
                    in.close();
                }

                //向peers请求文件
                else{
                    //Store peers info.
                    if (info[1].startsWith("/peer/add?path")) {
                        String[] tmp = info[1].substring(15).split("&");
                        peerFilePath = tmp[0];
                        if (!FrontEndHttpServer.threadShare.containsKey(peerFilePath)) {
                            FrontEndHttpServer.threadShare.put(peerFilePath, new ArrayList<>());
                        }
                        FrontEndHttpServer.threadShare.get(peerFilePath).add(info[1].substring(15));
                        System.out.println("threadShareMap size: " + FrontEndHttpServer.threadShare.size() + " ");
//                        for(int i =0; i < FrontEndHttpServer.threadShare.get("content/video.ogg").size(); i++){
//                            System.out.println(FrontEndHttpServer.threadShare.get("content/video.ogg").get(i));
//                        }
                    }

                    //p t0 p 200
                    else if(!request.containsKey("Range")){
                        httpRetransfer200(info[1]);
                    }
                    //p t0 p 206
                    else{
                        String[] headTail = request.get("Range").split("bytes=")[1].split("-");
                        String tail = "";
                        if (headTail.length > 1) tail = headTail[1];
                        response206(headTail[0], tail);
                        httpRetransfer206(info[1], headTail[0], tail);
                    }
                }
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
        System.out.println(pointer);
        return file;
    }
    public int convertByteToInt(byte[] bytes, int start){
        return ((bytes[start + 0] & 0xFF) << 24) |
                ((bytes[start + 1] & 0xFF) << 16) |
                ((bytes[start + 2] & 0xFF) << 8 ) |
                ((bytes[start + 3] & 0xFF) << 0 );
    }
    private File findFile(String path) {
        File file = new File("./content" + path);
        if (!file.exists()) file = new File("./content/video" + path);
        return file;
    }
    private void response200() throws IOException {
        //header
        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        Date lastModified = new Date(f.lastModified());
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + f.length() + CRLF +
                "Content-Type: " + fType + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
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

        //form and send header
        String header = "HTTP/1.1 206 Partial Content" + CRLF +
                "Content-Length: " + (endByte - startByte) + CRLF +
                "Content-Type: " + fType + CRLF +
                "Cache-Control: " + "public" + CRLF +
                "Connection: " + "keep-alive" + CRLF +
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
        ArrayList<String> peerInfo = FrontEndHttpServer.threadShare.get(peerFilePath);

        //向几个peers要文件就发送几次报文
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(10000);
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
        }

        //wait for response
        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<Long>();

        byte[] recArr = new byte[204800];
        int fileLen = 0;
        String fileName = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = 0;

        //一直向所有后端接收
        while(true) {
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                break;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);
            System.out.println("##########Frontend############");
            System.out.println(header.toString());
            //judge header
            if (header.statusCode == 0) {
                fileLen = (int) header.getLength();
                fileName = header.getFileName();
                lastModified = header.getLastModified();
                //form header
                String fType = URLConnection.guessContentTypeFromName(fileName);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                httpHeader = "HTTP/1.1 200 OK" + CRLF +
                    "Content-Length: " + fileLen + CRLF +
                    "Content-Type: " + fType + CRLF +
                    "Cache-Control: " + "public" + CRLF +
                    "Connection: " + "keep-alive" + CRLF +
                    "Accept-Ranges: " + "bytes" + CRLF +
                    "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                    "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF; //todo:可能有问题
                sOut.writeUTF(httpHeader);
            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.out.println(bendPackage.length);
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                fileMap.put(header.start, content);
                pq.add(header.start);
                System.out.println(header.start);
                System.out.println("fileMap size: " + fileMap.size() + " ");

                //发送200给browser
                while(pq.size() != 0 && mapPointer == pq.peek()){
                    try {
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        sOut.write(bytes, 0, bytes.length);
                        sOut.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mapPointer += fileMap.get(pq.poll()).length;  //get 为空
                }
            }
            else { //Not found// todo: deal with not found
                break;
            }
        }
    }
    private void httpRetransfer206(String info, String head, String tail) throws IOException {
//        send info to backend listener
        peerFilePath = info.substring(11);
        ArrayList<String> peerInfo = FrontEndHttpServer.threadShare.get(peerFilePath);
        long splitSize = (Long.parseLong(tail) - Long.parseLong(head)) / peerInfo.size();

        //向几个peers要文件就发送几次报文
        DatagramSocket dsock = new DatagramSocket();
        dsock.setSoTimeout(10000);

        for(int i = 0; i < peerInfo.size(); i++){
            //length 表示总共开了多少个peers
            //start：向后端传递你是第几个peer
            long start = i * splitSize;
            long length = start + splitSize;
            if(i == peerInfo.size()-1) length = Long.parseLong(tail);
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
        }

        //wait for response
        HashMap<Long, byte[]> fileMap = new HashMap<>();
        PriorityQueue<Long> pq = new PriorityQueue<Long>();

        byte[] recArr = new byte[1024];
        int fileLen = 0;
        String fileName = null;
        long lastModified = 0;
        String httpHeader = null;
        long mapPointer = Long.parseLong(head);

        //一直向所有后端接收
        while(true) {
            DatagramPacket dpack = new DatagramPacket(recArr, recArr.length);
            try{
                dsock.receive(dpack);
            }
            catch (SocketTimeoutException e){
                break;
            }
            //从dpack中获取header和content信息，分别存在header和content[]中
            byte[] bendPackage = dpack.getData();
            int headerLen = convertByteToInt(bendPackage, 0);
            int contentLen = convertByteToInt(bendPackage, 4);
            ResponseHeader header = JSONObject.parseObject(new String(bendPackage, 8, headerLen), ResponseHeader.class);
            System.out.println(header.toString());
            //judge header
            if (header.statusCode == 0) {
                fileLen = (int) header.getLength();
                fileName = header.getFileName();
                lastModified = header.getLastModified();
                //form header
                String fType = URLConnection.guessContentTypeFromName(fileName);
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                httpHeader = "HTTP/1.1 206 OK" + CRLF +
                        "Content-Length: " + fileLen + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Cache-Control: " + "public" + CRLF +
                        "Connection: " + "keep-alive" + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
                        "Date: " + dateFormat1.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + dateFormat1.format(lastModified) + " GMT" + CRLF +CRLF; //todo:可能有问题
                sOut.writeUTF(httpHeader);
            }
            //接受文件存在map中
            else if (header.statusCode == 1) {
                byte[] content = new byte[contentLen];
                System.arraycopy(bendPackage, 8 + headerLen, content, 0, contentLen);
                fileMap.put(header.start, content);
                pq.add(header.start);
                System.out.println("fileMap size: " + fileMap.size() + " ");

                //发送206给browser
                while(pq.size() != 0 && mapPointer == pq.peek()){
                    try {
                        byte[] bytes = fileMap.get(mapPointer);    //bytes为空
                        sOut.write(bytes, 0, bytes.length);
                        sOut.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mapPointer += fileMap.get(pq.poll()).length;  //get 为空
                }
            }
            else { //Not found// todo: deal with not found
                break;
            }
        }
    }

}

