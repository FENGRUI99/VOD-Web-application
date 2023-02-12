import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class testConfig {
    public static void main(String[] args) {
        // Define the file path
        File configFile = new File("node.config");
        Properties configProperties = new Properties();
        // Modify the properties
        configProperties.setProperty("uuid", "123ee632-56b5-4a15-88ef-7bd3b7081141");
        configProperties.setProperty("name", "node1");
        configProperties.setProperty("frontend_port", "18345");
        configProperties.setProperty("backend_port", "18346");
        configProperties.setProperty("content_dir", "content/");
        configProperties.setProperty("peer_count", "2");
        configProperties.setProperty("peer_0", "24f22a83-16f4-4bd5-af63-b5c6e979dbb,pi.ece.cmu.edu,18345,18346,10");
        configProperties.setProperty("peer_1", "3d2f4e34-6d21-4dda-aa78-796e3507903c,mu.ece.cmu.edu,18345,18346,20");
        // Write the properties back to the file
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            configProperties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error writing config file: " + e.getMessage());
        }
    }
}
