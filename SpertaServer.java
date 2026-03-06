import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class SpertaServer {
	public static void main(String[] args) {
		System.out.println("servidor: main");
		SpertaServer server = new SpertaServer();
            switch (args.length) {
                case 1 -> server.startServer(Integer.parseInt(args[0]));
                case 0 -> server.startServer(22345);
                default -> {
                    System.out.println("Usage: java SpertaServer <port>");
                    System.exit(-1);
                }
            }
	}

	public void startServer (int port){
		try(ServerSocket sSoc = new ServerSocket(port)) {
			System.out.println("[SERVER] Server started on port " + port);
			while(true) {
				try {
					File users = new File("usersLog.txt");
					if (!users.exists()) {
						users.createNewFile();
					}
					File workspaces = new File("workspaces.txt");
					if (!workspaces.exists()) {
						workspaces.createNewFile();
					}
					Socket inSoc = sSoc.accept();
					ServerThread newServerThread = new ServerThread(inSoc, users, workspaces);
					newServerThread.start();
				} catch (IOException e) {
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

	private boolean running = true;
	private File users, workspaces;

	ObjectInputStream in;
	ObjectOutputStream out;

	ServerThread(Socket inSoc, File users, File workspaces) {
		socket = inSoc;
		this.users = users;
		this.workspaces = workspaces;
		System.out.println("thread do server para cada cliente");
	}

	@Override
	public void run() {
		try(ObjectInputStream clientInfo = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream serverInfo = new ObjectOutputStream(socket.getOutputStream())) {
			String user, pwd;
			try {
				user = (String) in.readObject();
				pwd = (String) in.readObject();
				System.out.println("[* Thread] Authentication request received for user: " + user);
				try {
					authenticate(users, user, pwd, in, out);
					while(running){
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
			} catch (ClassNotFoundException e1) {
				System.err.println(e1.getMessage());
				System.exit(-1);
			}
		} catch (IOException ex) {
			System.out.println("Client disconnected!");
		}
	}

	private void authenticate(File usersFile, String user, String pwd, ObjectInputStream in, ObjectOutputStream out) {
		boolean isAuthed = false;

		try(Scanner sc = new Scanner(usersFile)) {
			while (sc.hasNextLine()) {
				String[] credentials = sc.nextLine().split(":");
				if (credentials[0].equals(user)) {
					while(!isAuthed){
						if (credentials[1].equals(pwd)) {
							isAuthed = true;
							out.writeObject("OK_USER");
							out.flush();
							System.out.println("[Thread] Authentication successful for user: " + user);
						} else {
							out.writeObject("WRONG_PWD");
							out.flush();
							pwd = (String) in.readObject();
						}
					}
				}
			}
			createUser(user, pwd, usersFile);
			out.writeObject("OK_NEW_USER");
			out.flush();
		}catch (IOException | ClassNotFoundException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
    }

    private void createUser(String user, String pwd, File usersFile) {
		String newUser = user + ":" + pwd;
		try(FileWriter fw = new FileWriter(usersFile)) {
			fw.write(newUser + System.lineSeparator());
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
    }
}