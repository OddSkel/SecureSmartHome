
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
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
		try(ObjectInput clientInfo = new ObjectInputStream(socket.getInputStream()); ObjectOutputStream serverInfo = new ObjectOutputStream(socket.getOutputStream())) {
			String user, password;
			user = "admin";
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
						serverInfo.flush();
                    }
					case "EC" -> {
                                }
					case "RT" -> {
						Number result = getHistory(command_Args[1], user);
						if(result instanceof Long) {
							String [] response_To_Client = {"Ok", Long.toString((long) result)};
							serverInfo.writeObject(response_To_Client);
							try(FileInputStream history_To_Send = new FileInputStream("history_to_send.txt")){
								int bytesToRead;
								byte [] buf = new byte[1024];
								while((bytesToRead = history_To_Send.read(buf, 0, buf.length))!= -1){
									serverInfo.write(buf, 0, bytesToRead);
									serverInfo.flush();
								}
							}
						}
						else if(result instanceof Integer) {
							switch ((int) result) {
								case 0 -> serverInfo.writeObject("NODATA");
								case 1 -> serverInfo.writeObject("NOPERM");
								case -1 -> serverInfo.writeObject("NOHM");
								default -> throw new AssertionError();
							}
        				}
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
    private static Number getHistory(String house, String user) {
		File house_Dir = new File(house);
		if(!house_Dir.exists()) return -1; //NOHM
        try(Scanner sc = new Scanner(new File("users.txt"))) {
			while(sc.hasNextLine()){
				String [] line = sc.nextLine().split(":");
				if(line[0].equals(house)) {
					for (int idx = 1; idx < line.length; idx++) {
						if(line[idx].equals(user)) {
							File history = new File(house + "/history.txt");
							if(history.length() == 0) {
								return 0; //NODATA
							}
							File history_to_send = new File("history_to_send.txt");
							try(Scanner sc1 = new Scanner(history); FileWriter file_To_Send = new FileWriter(history_to_send)) {
								while(sc1.hasNextLine()){
									String [] line1 = sc1.nextLine().split(":");
									file_To_Send.write("Last Operation of " + line1[0] + ": " + line1[line1.length - 1] + "\n");
								}
								return history_to_send.length(); //OK
							} catch (IOException e) {
								System.err.println(e.getMessage());
								System.exit(-1);
							}
						}
					}
					return 1; //NOPERM
				}
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return 2;
    }
}