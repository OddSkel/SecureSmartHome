import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
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
    System.out.println("[SERVER] Starting server...");
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
					Socket inSoc = sSoc.accept();
					ServerThread newServerThread = new ServerThread(inSoc);
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

	ServerThread(Socket inSoc) {
		socket = inSoc;
		System.out.println("thread do server para cada cliente");
	}

	public void run() {
		try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            users = new File("usersLog.txt");
            if (!users.exists()) {
                users.createNewFile();
            }
            workspaces = new File("workspaces.txt");
            if (!workspaces.exists()) {
                workspaces.createNewFile();
            }

        try {
            String user = (String) in.readObject();
            String pwd = (String) in.readObject();
            System.out.println("[" + user + " Thread] Authentication request received for user: " + user);
            authenticate(user, pwd);
        while(running){
          	Object received = in.readObject();
		  	String command;
        	String[] command_Args = null;

            if (received instanceof String[]) {
                command_Args = (String[]) received;
                command = command_Args[0];
            } else {
                command = (String) received;
                command_Args = new String[]{command};
            }

		switch (command) {
                        case "CREATE" -> {
                        }
                        case "ADD" -> {
                        }
                        case "RD" -> {
                            int result = verify(command_Args[1], command_Args[2], user);
                            switch (result) {
                                case 0 -> out.writeObject("NOPERM");
                                case 1 -> out.writeObject("OK");
                                case -1 -> out.writeObject("NOHM");
                                default -> out.writeObject("ERROR");
                            }
                            out.flush();
                        }
                        case "EC" -> {
                        }
                        case "RT" -> {
                            Number result = getHistory(command_Args[1], user);
                            if (result instanceof Long) {
                                String[] resp = {"Ok", Long.toString((long) result)};
                                out.writeObject(resp);
                                out.flush();

                                try (FileInputStream fis = new FileInputStream("history_to_send.txt")) {
                                    byte[] buf = new byte[1024];
                                    int n;
                                    while ((n = fis.read(buf, 0, buf.length)) != -1) {
                                        out.write(buf, 0, n);
                                    }
                                    out.flush();
                                }
                            } else {
                                switch ((int) result) {
                                    case 0 -> out.writeObject("NODATA");
                                    case 1 -> out.writeObject("NOPERM");
                                    case -1 -> out.writeObject("NOHM");
                                    default -> out.writeObject("ERROR");
                                }
                                out.flush();
                            }
                        }
                        case "RH" -> {
                        }
                        default -> {
                            out.writeObject("NOCOMMAND");
                            out.flush();
                        }
                    }
                }
		} catch (ClassNotFoundException e1) {
				System.err.println(e1.getMessage());
				System.exit(-1);
			}
		} catch (IOException ex) {
			System.out.println("Client disconnected!");
		}
	}

    private void authenticate(String user, String pwd) {
		boolean isAuthed = false;
		try(Scanner sc = new Scanner(users)) {
			while (sc.hasNextLine()) {
				String[] credentials = sc.nextLine().split(":");
				if (credentials[0].equals(user)) {
					while(!isAuthed){
						if (credentials[1].equals(pwd)) {
							isAuthed = true;
							out.writeObject("OK_USER");
							out.flush();
							System.out.println("["+ user +" Thread] Authentication successful for user: " + user);
              return;
						} else {
							out.writeObject("WRONG_PWD");
							out.flush();
              System.out.println("["+ user +" Thread] Authentication failed for user: " + user + ". Incorrect password.");
							pwd = (String) in.readObject();
						}
					}
				}
			}
			createUser(user, pwd);
			out.writeObject("OK_NEW_USER");
			out.flush();
		}catch (IOException | ClassNotFoundException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
    }

    private void createUser(String user, String pwd) {
		String newUser = user + ":" + pwd;
		try(FileWriter fw = new FileWriter(users, true)) {
			fw.write(newUser + System.lineSeparator());
      		System.out.println("[Thread] New user created: " + user);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
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
		if(!house_Dir.exists()) return -1; 
        try(Scanner sc = new Scanner(new File("users.txt"))) {
			while(sc.hasNextLine()){
				String [] line = sc.nextLine().split(":");
				if(line[0].equals(house)) {
					for (int idx = 1; idx < line.length; idx++) {
						if(line[idx].equals(user)) {
							File history = new File(house + "/history.txt");
							if(history.length() == 0) {
								return 0; 
							}
							File history_to_send = new File("history_to_send.txt");
							try(Scanner sc1 = new Scanner(history); FileWriter file_To_Send = new FileWriter(history_to_send)) {
								while(sc1.hasNextLine()){
									String [] line1 = sc1.nextLine().split(":");
									file_To_Send.write("Last Operation of " + line1[0] + ": " + line1[line1.length - 1] + "\n");
								}
								return history_to_send.length(); 
							} catch (IOException e) {
								System.err.println(e.getMessage());
								System.exit(-1);
							}
						}
					}
					return 1;
				}
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return 2;
    }
}