import com.alibaba.fastjson.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
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


public class FrontEndHttpServer {
    String frontEndPort;
    String backEndPort;
    //Todo : changed
    public FrontEndHttpServer(String frontEndPort, String backEndPort){
        this.frontEndPort = frontEndPort;
        this.backEndPort = backEndPort;
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

    public static void main(String[] args) throws IOException{
//        File htmlFile = new File("Search/frontPage.html");
//        Desktop.getDesktop().browse(htmlFile.toURI());
        System.out.println(args[0]);
        System.out.println(args[1]);
        FrontEndHttpServer frontEndHttpServer = new FrontEndHttpServer(args[0], args[1]);
        frontEndHttpServer.startServer();

//        String s = "/peer/add?path=content/video.ogg&host=pi.ece.cmu.edu&port=8346";
//        String[] tmp = s.substring(15).split("&");
//        System.out.println(tmp[0]);
//        System.out.println(tmp[1].substring(5));
//        System.out.println(Integer.valueOf(tmp[2].substring(5)));
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
    private int rate;
    InetAddress peerIp;
    private String peerFilePath;
    int peerPort;
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

//            // receive peer info from browser and store it in frontEnd, contact backEnd to send the file.
//            while ((inputLine = sIn.readLine()) != null) {
//                //first info from browser
//                if (inputLine.split("view").length != 2) {
//                    //GET /peer/add?path=content/video.ogg&host=172.16.7.12&port=8002&rate=1600 HTTP/1.1
//                    String[] tmp = inputLine.split("=");
//                    fileName = tmp[1].split("&")[0];
//                    rate = Integer.valueOf(tmp[4].split(" ")[0]);
//                    peerIp = InetAddress.getByName(tmp[2].split("&")[0]);
//                    peerPort = Integer.valueOf(tmp[3].split("&")[0]);
//                }
//                //second info from browser
//                else {
//                    DatagramSocket dsock = new DatagramSocket(8080);
//                    int src = 0; // 0 for http server, 1 for peer back-end server
//                    InetAddress frontEndIp = InetAddress.getByName("127.0.0.1");
//                    int frontEndPort = 8080;
//                    long start = 0;
//                    long length = 7202;
//                    String message = JSONObject.toJSONString(new ListenerHeader(src, frontEndIp, frontEndPort, peerIp, peerPort, fileName, start, length, rate));
//                    byte[] sendArr = message.getBytes();
//                    DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, frontEndIp, 8081);
//                    dsock.send(dpack);
//
//                    //byte[] recArr = new byte[7202];
//                    //dpack = new DatagramPacket(recArr, recArr.length);
//                }
//            }


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
                        peerIp = InetAddress.getByName(tmp[1].substring(5));
                        peerPort = Integer.valueOf(tmp[2].substring(5));
                        if (tmp.length > 3){
                            rate = Integer.valueOf(tmp[3].substring(5));
                        }
                    }
                    //View content
                    else {
                        //send info to backend listener
                        DatagramSocket dsock = new DatagramSocket();
                        //TODO start和length要怎么得到
                        int start = 0;
                        int length = 0;
                        String message = JSONObject.toJSONString(new ListenerHeader(0, dsock.getInetAddress(), dsock.getPort(), peerIp, peerPort, peerFilePath, start, length, rate));
                        byte[] sendArr = message.getBytes();
                        DatagramPacket dpack = new DatagramPacket(sendArr, sendArr.length, dsock.getInetAddress(), backEndPort);
                        dsock.send(dpack);

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

