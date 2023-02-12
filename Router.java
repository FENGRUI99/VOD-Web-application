import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class Router {
    static String frontEndPort;
    static String backEndPort;
    static String name;
    static List<String> peers;
    static String peerCount;
    static String uuid;
    static String content;
    static HashMap<String, String> peerInfo; // peerName -> sequence
    public Router(){
        File configFile = new File("node.config");
        // Create a Properties object
        Properties configProperties = new Properties();
        //Read the properties file
        try (FileInputStream input = new FileInputStream(configFile)) {
            configProperties.load(input);
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }

        frontEndPort = configProperties.getProperty("frontend_port");
        backEndPort = configProperties.getProperty("backend_port");
        name = configProperties.getProperty("name");
        peerCount = configProperties.getProperty("peer_count");
        content = configProperties.getProperty("content_dir");
        peers = new ArrayList<String>();
        for(int i = 0; i < Integer.valueOf(peerCount); i++){
            peers.add(configProperties.getProperty("peer_" + i));
        }
        uuid = configProperties.getProperty("uuid");
        if(uuid.length() != 36){
            uuid = UUID.randomUUID().toString();
        }
        peerInfo = new HashMap<>();
    }
    public static void receive(){

    }

    public static void send(){

    }

    public static void main(String[] args) {
        Router r = new Router();
        System.out.println(Router.frontEndPort);
        System.out.println(Router.backEndPort);
        System.out.println(Router.name);
        System.out.println(Router.peers.size());
        for(int i = 0; i < peers.size(); i++){
            System.out.println(Router.peers.get(i));
        }
        System.out.println(Router.peerCount);
        System.out.println(Router.uuid);
        System.out.println(Router.content);
    }
    // change the information in node.config
    private static void setConfig(String fileName, String uuid, String name, int frontEndPort, int backEndPort, String contentDir, int peerCount, List<String> peers){
        File configFile = new File(fileName);
        Properties configProperties = new Properties();
        // Modify the properties
        configProperties.setProperty("uuid", uuid);
        configProperties.setProperty("name", name);
        configProperties.setProperty("frontend_port", ""+frontEndPort);
        configProperties.setProperty("backend_port", ""+backEndPort);
        configProperties.setProperty("content_dir", contentDir);
        configProperties.setProperty("peer_count", ""+peerCount);
        for(int i = 0; i < peerCount; i++){
            configProperties.setProperty("peer_"+i, peers.get(i));
        }
        // Write the properties back to the file
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            configProperties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
        }
    }
}
