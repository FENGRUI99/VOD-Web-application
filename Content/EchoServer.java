import java.net.*; 
import java.io.*;
import java.util.ArrayList;

public class EchoServer 
{
//    final static String fileDir = "./Content/video/";
    public static File findFile(String fileName){
        return new File(fileName);
    }

    public static void main(String[] args) throws IOException {

        ServerSocket serverSocket = null;
        try {
             serverSocket = new ServerSocket(10007);
            }
        catch (IOException e)
            {
             System.err.println("Could not listen on port: 10007.");
             System.exit(1);
            }

        Socket clientSocket = null;
        System.out.println ("Waiting for connection.....");

        try {
             clientSocket = serverSocket.accept();
            }
        catch (IOException e)
            {
             System.err.println("Accept failed.");
             System.exit(1);
            }

        System.out.println ("Connection successful");
        System.out.println ("Waiting for input.....");

        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader( clientSocket.getInputStream()));

        String inputLine;

        while ((inputLine = in.readLine()) != null)
            {
             System.out.println ("Server: " + inputLine);
             String[] tmp = inputLine.split(" ");
             if (tmp[0].equals("GET")){
                 File f = findFile("." + tmp[1]);
                 System.out.println(f.exists());
             }
//             if (inputLine.equals("Bye."))
//                 break;
            }

        out.close();
        in.close();
        clientSocket.close();
        serverSocket.close();
   }
} 