//f I use brower enter: localhost:8080/peer/add?path=content/test.ogg&host=172.16.7.16&port=8081&rate=1600
//      httpServer get: GET /peer/add?path=content/test.png&host=172.16.7.16&port=8081&rate=1600 HTTP/1.1

//if I use brower enter: http://localhost:8080/peer/view/content/test.ogg
//       httpServer get: GET /peer/view/content/test.png HTTP/1.1

import java.util.Comparator;

public class Server {
    public static void main(String[] args) {
//        int frontEndPort = Integer.valueOf(args[0]);
//        int backEndPort = Integer.valueOf(args[1]);
        int frontEndPort = 8080;
        int backEndPort = 8081;
        FrontEndHttpServer frontEndListener = new FrontEndHttpServer(frontEndPort, backEndPort);
        BackEndServer backEndListener = new BackEndServer(backEndPort);
        frontEndListener.start();
        backEndListener.start();
    }
}
