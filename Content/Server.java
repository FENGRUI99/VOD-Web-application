import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        } catch (Exception e){
            e.printStackTrace();
        }
        Socket clientSocket = null;
        System.out.println("Waiting for connection.....");
        try {
            clientSocket = serverSocket.accept();
        } catch (IOException e){
            e.printStackTrace();
        }
        System.out.println ("Connection successful");
        System.out.println ("Waiting for input.....");

        sOut = new DataOutputStream(clientSocket.getOutputStream());
        sIn = new BufferedReader(new InputStreamReader( clientSocket.getInputStream()));

        String inputLine;

        while ((inputLine = sIn.readLine()) != null)
        {
            System.out.println ("Server: " + inputLine);
            //TODO
            String[] info = inputLine.split(" ");
            if (info[0].equals("GET")){
                f = findFile(info[1]);
                String fType = URLConnection.guessContentTypeFromName(f.getName());
                Date date = new Date();
                SimpleDateFormat dateFormat= new SimpleDateFormat("EEEE, dd-MM-yyyy hh:mm:ss");
                String header = "HTTP/1.1 200 OK" +CRLF+
                        "Content-Length:" + f.length() +CRLF+
                        "Content-Type:" + fType +CRLF+
                        "Date:" +  dateFormat.format(date) +" GMT"+CRLF+
                        "Last-Modified:" + f.lastModified() +CRLF;
                System.out.println(header);
                try{
                    FileInputStream fis = new FileInputStream(f);
                    in = new DataInputStream(fis);
                    byte[] bytes = new byte[1024];
                    int length = 0;
                    sOut.writeUTF(header);
                    while ((length = fis.read(bytes, 0, bytes.length)) != -1) {
                        sOut.write(bytes, 0, length);
                        sOut.flush();
                    }
                    System.out.println("successful");
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
            else {
                break;
            }


        }

        sIn.close();
        sOut.close();
        in.close();
    }

    //TODO
    public File findFile(String path) {
        byte[] bytes = new byte[1024];
        File file = new File("." + path);
        System.out.println(file);
        System.out.println(file.exists());
        return file;
    }


    public static void main(String[] args) throws IOException{
        Server frame = new Server();
        frame.getServer();
    }


}
