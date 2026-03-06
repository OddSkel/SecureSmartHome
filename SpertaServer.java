
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

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
		String user,passwd;
		user = "admin";
		try(ObjectInput clientInfo = new ObjectInputStream(socket.getInputStream()); ObjectOutputStream serverInfo = new ObjectOutputStream(socket.getOutputStream())) {
			while(true) {

				String [] command_Args = (String []) clientInfo.readObject();
				switch (command_Args[0]) {
					case "CREATE" -> {
                                }
					case "ADD" -> {
                                }
					case "RD" -> {
						int result = verify(command_Args[1], command_Args[2], user);
						switch (result) {
							case 0 -> serverInfo.writeObject("NOPERM");
							case 1 -> serverInfo.writeObject("OK");
							case -1 -> serverInfo.writeObject("NOHM");
							default -> throw new AssertionError();
						}
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
			System.out.println("Client disconnected");
		}
	}
	private static int verify(String house, String section, String user) {
		try(Scanner sc = new Scanner(new File("workspaces.txt"))) {
			List<String> lines = Files.readAllLines(Paths.get("workspaces.txt"));
			int counter_lines = 0;
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] lineArgs = line.split(":");
				if (lineArgs[0].equals(house) && lineArgs[1].equals(user)) {
					lines.set(counter_lines, line + ":" + section);
					Files.write(Paths.get("workspaces.txt"), lines);
					return 1;
				}
				else if (lineArgs[0].equals(house) && !lineArgs[1].equals(user)) {
					return 0;
				}
				counter_lines++;
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return -1;
	}
}