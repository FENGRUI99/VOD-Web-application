import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Todo: if I use brower enter: localhost:8000/peer/add?path=content/video.ogg&host=172.16.7.12&port=8002&rate=1600
//             httpServer get: GET /peer/add?path=content/video.ogg&host=172.16.7.12&port=8002&rate=1600 HTTP/1.1
//             从=分割
//
//Todo: if I use brower enter: http://localhost:8000/peer/view/content/video.ogg
//             httpServer get: GET /peer/view/content/video.ogg HTTP/1.1
//


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

            //Todo: receive file from backend and store it locally
            //Todo: send the file to browser and delete the file when close

            /*
            Get info example:
            GET /video.ogg undefined
            GET /peer/add?path=content/video.ogg&host=pi.ece.cmu.edu&port=8346 undefined
            GET /peer/view/content/video.ogg undefined
            */
            while ((inputLine = sIn.readLine()) != null){
                System.out.println(inputLine);
                info = inputLine.split(" ");    
                while (!(inputLine = sIn.readLine()).equals("")) {
                    String[] tmp = inputLine.split(": ");
                    request.put(tmp[0], tmp[1]);
                }
                // System.out.println(info[1]);
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

                else {
                    //Associate peer node with content
                    if (info[1].startsWith("/peer/add?path")){
                        //remove"/peer/add?path="
                        String[] tmp = info[1].substring(15).split("&");
                        peerFilePath = tmp[0];
//                        peerIp = InetAddress.getByName(tmp[1].substring(5));
//                        peerPort = Integer.valueOf(tmp[2].substring(5));
//                        if (tmp.length > 3){
//                            rate = Integer.valueOf(tmp[3].substring(5));
//                        }
                        if(!FrontEndHttpServer.threadShare.containsKey(peerFilePath)) {
                            FrontEndHttpServer.threadShare.put(peerFilePath, new ArrayList<>());
                        }
                        FrontEndHttpServer.threadShare.get(peerFilePath).add(info[1].substring(15));

                    }
                    //View content
                    /*
                        Get info example:
                        GET /video.ogg undefined
                        GET /peer/add?path=content/video.ogg&host=pi.ece.cmu.edu&port=8346 undefined
                        GET /peer/view/content/video.ogg undefined
                     */
                    else {
                        //send info to backend listener
                        //TODO start和length要怎么得到
                        peerFilePath = info[1].substring(11);
                        ArrayList<String> peerInfo = FrontEndHttpServer.threadShare.get(peerFilePath);

                        //向几个peers要文件就发送几次报文
                        for(int i = 0; i < peerInfo.size(); i++){
                            DatagramSocket dsock = new DatagramSocket();
                            int start = -1;
                            int length = -1;
                            int rate = 0;
                            String[] tmp = peerInfo.get(i).split("&");
                            InetAddress peerIp = InetAddress.getByName(tmp[1].substring(5));
                            int peerPort = Integer.valueOf(tmp[2].substring(5));
                            if (tmp.length > 3){
                               rate = Integer.valueOf(tmp[3].substring(5));
                            }
                            String message = JSONObject.toJSONString(new ListenerHeader(0, dsock.getInetAddress(), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
                            byte[] sendArr = message.getBytes();
                            DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, dsock.getInetAddress(), backEndPort);
                            dsock.send(dpack);
                        }

                        //wait for response

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
}

