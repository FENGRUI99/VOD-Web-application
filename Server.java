//f I use brower enter: localhost:8080/peer/add?path=content/test.ogg&host=172.16.7.16&port=8081&rate=1600
//      httpServer get: GET /peer/add?path=content/test.png&host=172.16.7.16&port=8081&rate=1600 HTTP/1.1

//if I use brower enter: http://localhost:8080/peer/view/content/test.ogg
//       httpServer get: GET /peer/view/content/test.png HTTP/1.1

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Properties;

public class Server {
    public static void main(String[] args) {
        File configFile = new File("routerConfig/" + args[0]);
        // Create a Properties object
        Properties configProperties = new Properties();
        //Read the properties file
        try (FileInputStream input = new FileInputStream(configFile)) {
            configProperties.load(input);
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }

        int frontEndPort = Integer.valueOf(configProperties.getProperty("frontend_port"));
        int backEndPort = Integer.valueOf(configProperties.getProperty("backend_port"));
        FrontEndHttpServer frontEndListener = new FrontEndHttpServer(frontEndPort, backEndPort);
        BackEndServer backEndListener = new BackEndServer(args[0]);
        frontEndListener.start();
        backEndListener.start();
    }
}
