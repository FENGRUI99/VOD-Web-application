import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class testConfig {
    public static void main(String[] args) {
        // Define the file path
        File configFile = new File("nodeB.config");
        Properties configProperties = new Properties();
        // Modify the properties
        configProperties.setProperty("uuid", "aaaee632-56b5-4a15-88ef-7bd3b7081141");
        configProperties.setProperty("name", "nodeB");
        configProperties.setProperty("frontend_port", "18343");
        configProperties.setProperty("backend_port", "18344");
        configProperties.setProperty("content_dir", "content/");
        configProperties.setProperty("peer_count", "2");
        configProperties.setProperty("peer_0", "aaaee632-56b5-4a15-88ef-7bd3b7081141,127.0.0.1,18343,18344,10");
        configProperties.setProperty("peer_1", "eeef4e34-6d21-4dda-aa78-796e3507903c,127.0.0.1,18351,18352,25");
        // Write the properties back to the file
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            configProperties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
        }
    }
}
