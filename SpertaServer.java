
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SpertaServer {
  public static void main(String[] args) {
		System.out.println("servidor: main");
		SpertaServer server = new SpertaServer();
		server.startServer();
	}

	public void startServer (){
		ServerSocket sSoc = null;
    
		try {
			sSoc = new ServerSocket(23456);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}

		while(true) {
			try {
				Socket inSoc = sSoc.accept();
				ServerThread newServerThread = new ServerThread(inSoc);
				newServerThread.start();
      }
      catch (IOException e) {
          e.printStackTrace();
      }
		}
  }
}

class ServerThread extends Thread {

  private Socket socket = null;

  ServerThread(Socket inSoc) {
    socket = inSoc;
    System.out.println("thread do server para cada cliente");
  }

  public void run() {}
}