import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class testConfig {
    public static void main(String[] args) {
        // Define the file path
        String configFile = "node.config";

        // Create a Properties object
        Properties configProperties = new Properties();

        // Read the properties file
//        try (FileInputStream input = new FileInputStream(configFile)) {
//            configProperties.load(input);
//        } catch (IOException e) {
//            System.out.println("Error reading config file: " + e.getMessage());
//        }
//        String property1 = configProperties.getProperty("property1");
//        System.out.println(property1);

        // Modify the properties
        configProperties.setProperty("uuid", "f94fc272-5611-4a61-8b27-de7fe233797f");
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
