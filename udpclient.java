import java.net.*;
import java.util.*;
  
public class udpclient
{
    public static void main( String args[] ) throws Exception 
    {
	InetAddress address = InetAddress.getByName("127.0.0.1");
                                         
	DatagramSocket dsock = new DatagramSocket( );
	String message1 = "test.txt";
	byte arr[] = message1.getBytes( );  
	DatagramPacket dpack = new DatagramPacket(arr, arr.length, address, 7078);
	dsock.send(dpack);                   // send the packet
	//Date sendTime = new Date();        // note the time of sending the messag

	dpack = new DatagramPacket(new byte[128], 128, address, 7078);
	int count = 0;
	while(true){
		dsock.receive(dpack);                                // receive the packet
		String message2 = new String(dpack.getData());
		count += message2.length();
		System.out.println(message2);
		System.out.println(count);
		if(message2 == null) break;
	}

    }
}