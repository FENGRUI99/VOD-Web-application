import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Server {
    private DataOutputStream sOut = null;
    private DataInputStream in = null;
    private BufferedReader sIn = null;
    private ServerSocket serverSocket = null;

    private static File f = null;
    final String CRLF = "\r\n";
    public void getServer() throws IOException {

        try {
            serverSocket = new ServerSocket(10008);
        } catch (Exception e) {
            e.printStackTrace();
        }
        while(true) {
            Socket clientSocket = null;
            System.out.println("Waiting for connection.....");
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Connection successful");
            System.out.println("Waiting for input.....");

            sOut = new DataOutputStream(clientSocket.getOutputStream());
            sIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            String[] info = null;

            HashMap<String, String> request = new HashMap<>();

                while ((inputLine = sIn.readLine()) != null){
                    info = inputLine.split(" ");
//                    System.out.println(inputLine);
                    while (!(inputLine = sIn.readLine()).equals("")){
                        String[] tmp = inputLine.split(": ");
                        request.put(tmp[0], tmp[1]);
//                        System.out.println(tmp[0] + ": " + tmp[1]);
                    }
                    if (info[0].equals("GET")){
                        if (info != null && info[0].equals("GET")) {
                            if (info[1].equals("/favicon.ico")) break;
                            System.out.println(info[1]);
                            f = findFile(info[1]);
                            if (!f.exists()) continue;
                            String header;
                            if (request.containsKey("Range")){
                                System.out.println("#######################");
                                String[] startEnd = request.get("Range").split("=")[1].split("-");
                                System.out.println(request.get("Range").split("=")[1]);
                                if (startEnd.length == 1){
                                    partialContent(startEnd[0], "");
                                }
                                else {
                                    partialContent(startEnd[0], startEnd[1]);
                                }
                            }
                            else {
                                ok();
                            }

                        } else if (clientSocket.isClosed()) {
                            break;
                        }
                    }
//                if (inputLine.equals("")) break;
                }

            //TODO


            sIn.close();
            sOut.close();
            in.close();
        }
    }


    //TODO
    public File findFile(String path) {
        File file = new File("." + path);
        return file;
    }

    public void partialContent(String head, String tail) throws IOException {
        long startByte = Long.parseLong(head);
        long endByte;
        if (tail.equals("")){
            endByte = f.length();
        }
        else{
            endByte = Long.parseLong(tail);
        }

        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEEE, dd ");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat(" yyyy hh:mm:ss");
        LocalDate localDate = LocalDate.now();
        Month dateFormat2 = localDate.getMonth();

        //form and send header
        String header = "HTTP/1.1 206 Partial Content" + CRLF +
                "Content-Length: " + (endByte - startByte + 1) + CRLF +
                "Content-Type: " + fType + CRLF +
                "Connection: " + "Keep-Alive" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Content-Range: " + "bytes " + startByte + "-" + endByte + "/" + f.length() +  CRLF +
                "Date: " + dateFormat1.format(date) + dateFormat2.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + dateFormat3.format(date) + " GMT" + CRLF +
                "Last-Modified: " + f.lastModified() + CRLF + CRLF;
        sOut.writeUTF(header);

        //form and send file
        try {
            FileInputStream fis = new FileInputStream(f);
            in = new DataInputStream(fis);
            int max = 1024 * 1024 * 10;
            byte[] bytes = new byte[1024 * 1024]; //1MB

            int length;
            long count = 0;  //total bytes that read

            in.skip(startByte);  //Skip 'startbytes' bytes tp reach the start point
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {   //[)
                count += length;
                if(count <= endByte-startByte+1){
                    sOut.write(bytes, 0, length);
                    sOut.flush();
                }
                else{
                    if(count-length == endByte-startByte+1) break;
                    sOut.write(bytes, 0, (length-(int)(count-(endByte-startByte+1))));
                    sOut.flush();
                    break;
                }
            }
            System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    private void ok() throws IOException {
        //header
        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEEE, dd ");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat(" yyyy hh:mm:ss");
        LocalDate localDate = LocalDate.now();
        Month dateFormat2 = localDate.getMonth();

        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + f.length() + CRLF +
                "Content-Type: " + fType + CRLF +
                "Connection: " + "Keep-Alive" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + dateFormat2.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + dateFormat3.format(date) + " GMT" + CRLF +
                "Last-Modified: " + f.lastModified() + CRLF + CRLF;
        try {
            FileInputStream fis = new FileInputStream(f);
            in = new DataInputStream(fis);
            int max = 1024 * 1024 * 10;
            byte[] bytes = new byte[1024 * 1024];
            int length;
            sOut.writeUTF(header);
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {
                sOut.write(bytes, 0, length);
                sOut.flush();
                max -= length;
                if (max <= 0) break;
            }
            System.out.println(max);
            System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public static void main(String[] args) throws IOException{
        Server frame = new Server();
        frame.getServer();
    }

    class Listener extends Thread{
        public Listener(){

        }

        public Listener(String name){
            super(name);
        }

        @Override
        public void run(){
            try {
                serverSocket = new ServerSocket(10008);
            } catch (Exception e) {
                e.printStackTrace();
            }

            while (true){
                System.out.println("Waiting for connection.....");
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                } catch (IOException e){
                    e.printStackTrace();
                }
                System.out.println("Connection successful");
                System.out.println("Waiting for input.....");
                try {
                    BufferedReader sIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Sender sender = new Sender(clientSocket);
                sender.start();

            }

        }
    }

    class Sender extends Thread{
        Socket clientSocket;
        public Sender(Socket clientSocket){
            this.clientSocket = clientSocket;
        }
    }



}
