
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SpertaServer {
	public static void main(String[] args) {
		System.out.println("servidor: main");
		SpertaServer server = new SpertaServer();
		server.startServer();
	}

	public void startServer (){
		try(ServerSocket sSoc = new ServerSocket(23456)) {
			
		while(true) {
			try {
				Socket inSoc = sSoc.accept();
				ServerThread newServerThread = new ServerThread(inSoc);
				newServerThread.start();
			}
			catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(-1);
			}
		}
	} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
	}
}

class ServerThread extends Thread {

	private Socket socket = null;

	ServerThread(Socket inSoc) {
		socket = inSoc;
		System.out.println("thread do server para cada cliente");
	}
	@Override
	public void run() {
		try(ObjectInput clientInfo = new ObjectInputStream(socket.getInputStream()); ObjectOutputStream serverInfo = new ObjectOutputStream(socket.getOutputStream())) {
			while(true) {
				String client_Command = (String) clientInfo.readObject();

				switch (client_Command) {
					case "CREATE" -> {
                                }
					case "ADD" -> {
                                }
					case "RD" -> {
                                }
					case "EC" -> {
                                }
					case "RT" -> {
                                }
					case "RH" -> {
                                }
					default -> serverInfo.writeObject("NOCOMMAND");
				}
			}
		} catch (IOException | ClassNotFoundException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
	}
}