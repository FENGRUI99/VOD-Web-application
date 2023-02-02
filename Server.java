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
