import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Date;
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
            while ((inputLine = sIn.readLine()) != null){
                System.out.println("Server: " + inputLine);
                String[] tmp = inputLine.split(" ");
                if (tmp[0].equals("GET")){
                    info = tmp;
                }
                if (inputLine.equals("")) break;
            }
            //TODO
            System.out.println(info);
            if (info != null && info[0].equals("GET")) {
                System.out.println(info);
                f = findFile(info[1]);
                if (!f.exists()) continue;

                String fType = URLConnection.guessContentTypeFromName(f.getName());
                Date date = new Date();
                SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEEE, dd ");
                SimpleDateFormat dateFormat3 = new SimpleDateFormat(" yyyy hh:mm:ss");
                LocalDate localDate = LocalDate.now();
                Month dateFormat2 = localDate.getMonth();


                String header = "HTTP/1.1 200 OK" + CRLF +
                        "Content-Length: " + f.length() + CRLF +
                        "Content-Type: " + fType + CRLF +
                        "Accept-Ranges: " + "bytes" + CRLF +
//                            "Content-Range:" + CRLF + start-end/size
                        "Date: " + dateFormat1.format(date) + dateFormat2.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + dateFormat3.format(date) + " GMT" + CRLF +
                        "Last-Modified: " + f.lastModified() + CRLF + CRLF;
//                System.out.println(header);
                try {
                    FileInputStream fis = new FileInputStream(f);
                    in = new DataInputStream(fis);
                    int max = 10000000;
                    byte[] bytes = new byte[1024000];
                    int length;
                    sOut.writeUTF(header);
                    while ((length = fis.read(bytes, 0, bytes.length)) != -1) {
                        sOut.write(bytes, 0, length);
                        sOut.flush();
                        max -= length;
                        if (max < 0) break;
                    }
                    System.out.println("successful");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (clientSocket.isClosed()) {
                break;
            }

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


    public static void main(String[] args) throws IOException{
        Server frame = new Server();
        frame.getServer();
    }


}
