import java.io.*;
import java.net.*;

public class Server {
    private BufferedOutputStream sOut = null;
    private BufferedInputStream in = null;
    private BufferedReader sIn = null;
    private ServerSocket serverSocket = null;

    public void getServer() throws IOException {
        try {
            serverSocket = new ServerSocket(10007);
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

        sOut = new BufferedOutputStream(clientSocket.getOutputStream());
        sIn = new BufferedReader(new InputStreamReader( clientSocket.getInputStream()));
        String inputLine;

        while ((inputLine = sIn.readLine()) != null)
        {
            System.out.println ("Server: " + inputLine);

            //TODO
//            send file to client
        }



    }

    //TODO
    public File findFile(String path) {
        File file = new File("./video/" + path);
        try{
            FileInputStream fis = new FileInputStream(file);
            in = new BufferedInputStream(fis);
            while(in.available() > 0 ){
                System.out.print(in.read());
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) throws IOException{
        Server frame = new Server();
        frame.findFile("test.png");
    }
}
