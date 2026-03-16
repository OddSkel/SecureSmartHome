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
import java.util.ArrayList;
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

	private File users, homes, homesFolder;
	private String user, pwd;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};

	ServerThread(Socket inSoc) {
		socket = inSoc;
		System.out.println("thread do server para cada cliente");
	}

	@Override
	public void run() {
		try{
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());

			users = new File("users.txt");
			if (!users.exists()) {
				users.createNewFile();
			}

			homes = new File("homes.txt");
			if (!homes.exists()) {
				homes.createNewFile();
			}

			homesFolder = new File("homes");
			if (!homesFolder.exists()) {
				homesFolder.mkdir();
			}
	
			try {
				user = (String) in.readObject();
				pwd = (String) in.readObject();
				System.out.println("["+ user +" Thread] Authentication request received for user: " + user);
				authenticate(user, pwd);
				while(true){
					String [] client_Commands = (String[]) in.readObject();
					switch (client_Commands[0]) {
						case "CREATE" -> {
							String houseName = client_Commands[1];
							System.out.println("["+ user +" Thread] CREATE command received for home: " + houseName);
							createHome(houseName);
						}
						case "ADD" -> {
							String userToAdd = client_Commands[1];
							String homeName = client_Commands[2];
							String section = client_Commands[3];
							System.out.println("["+ user +" Thread] ADD command received to add user: " + userToAdd + " to home: " + homeName + " with section: " + section);
							if(userExists(userToAdd)) {
								if(homeExists(homeName)){
								if(checkOwner(homeName, user)) {
									if(!isValidSection(section)) {
									out.writeObject("INVALID_SECTION");
									out.flush();
									System.out.println("["+ user +" Thread] ADD command failed. Invalid section: " + section);
									}else{
									addUserToHome(userToAdd, homeName, section);
									}
								} else {
									out.writeObject("NO_USER_PERMS");
									out.flush();
									System.out.println("["+ user +" Thread] ADD command failed. User does not have permissions to add users to home: " + homeName);
								}
								} else {
								out.writeObject("HOME_NOT_FOUND");
								out.flush();
								System.out.println("["+ user +" Thread] ADD command failed. Home not found: " + homeName);
								}
							} else {
								out.writeObject("USER_NOT_FOUND");
								out.flush();
								System.out.println("["+ user +" Thread] ADD command failed. User not found: " + userToAdd);
							}
						}
						case "RD" -> {
							int result = verify(client_Commands, user);
							switch (result) {
								case 0 -> out.writeObject("NOPERM");
								case 1 -> out.writeObject("OK");
								case -1 -> out.writeObject("NOHM");
								default -> throw new AssertionError();
							}
							out.flush();
						}
						case "EC" -> {
							String homeName = client_Commands[1];
    						String device = client_Commands[2];
    						String value = client_Commands[3];
    						System.out.println("[" + user + " Thread] EC command: " + homeName + " " + device + " " + value);

    						if (!homeExists(homeName)) {
        						out.writeObject("NOHM");
    						} else if (!checkOwner(homeName, user) && !verifyUserPermission(homeName, user)) {
        					// checkOwner já existe no teu código, verifyUserPermission verifica se o user foi adicionado via ADD
        						out.writeObject("NOPERM");
    						} else {
        					// 1. Atualizar o estado do dispositivo em homes/<casa>/devicesLog.txt
        						updateDeviceState(homeName, device, value);
        					// 2. Registar no histórico da casa em homes/<casa>/history.txt
        						logAction(homeName, "User " + user + " set " + device + " to " + value);
        
        						out.writeObject("OK");
    						}
    						out.flush();
						}
						case "RT" -> {
							Number result = getHistory(client_Commands[1], user);
							if(result instanceof Long) {
								String [] response_To_Client = {"Ok", Long.toString((long) result)};
								out.writeObject(response_To_Client);
								try(FileInputStream history_To_Send = new FileInputStream("history_to_send.txt")){
									int bytesToRead;
									byte [] buf = new byte[1024];
									while((bytesToRead = history_To_Send.read(buf, 0, buf.length))!= -1){
										out.write(buf, 0, bytesToRead);
										out.flush();
									}
								}
							}
							else if(result instanceof Integer) {
								switch ((int) result) {
									case 0 -> out.writeObject("NODATA");
									case 1 -> out.writeObject("NOPERM");
									case -1 -> out.writeObject("NOHM");
									default -> throw new AssertionError();
								}
								out.flush();
							}
						}
						case "RH" -> {
						}
						default -> out.writeObject("NOCOMMAND");
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
		try(Scanner sc = new Scanner(users)) {
			while (sc.hasNextLine()) {
				String[] credentials = sc.nextLine().split(":");
				if (credentials[0].equals(user)) {
					while(true){
						if (credentials[1].equals(pwd)) {
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
			System.out.println("[" + user + " Thread] New user created: " + user);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
    }

    private void createHome(String homeName) {
		try {
			if(homeExists(homeName)){
				out.writeObject("HOME_EXISTS");
				out.flush();
				System.out.println("[" + user + " Thread] Home creation failed. Home already exists: " + homeName);
			} else {
				try(FileWriter fw = new FileWriter(homes, true)) {
					fw.write(homeName + ":"+ user + ">>" + System.lineSeparator());

					File newHomeFolder = new File(homesFolder, homeName);
					newHomeFolder.mkdirs();
					File devicesFile = new File(newHomeFolder, "devicesLog.txt");
					devicesFile.createNewFile();

					System.out.println("[" + user + " Thread] Home created: " + homeName);
					out.writeObject("HOME_CREATED");
					out.flush();
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

    private boolean userExists(String user) {
		try(Scanner sc = new Scanner(users)) {
        while (sc.hasNextLine()) {
			String[] credentials = sc.nextLine().split(":");
			if (credentials[0].equals(user)) return true;
        }
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return false;
    }

    private boolean homeExists(String homeName) {
		try(Scanner sc = new Scanner(homes)) {
			while (sc.hasNextLine()) {
			String[] homeData = sc.nextLine().split(":");
			if (homeData[0].equals(homeName)) return true;
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return false;
    }

    private boolean checkOwner(String homeName, String user) {
		try(Scanner sc = new Scanner(homes)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] homeData = line.split(":");
				if (homeData[0].equals(homeName)) {
					String[] permissions = line.split(">");
					String[] owner = permissions[0].split(":");
					return owner[1].equals(user);
				}
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return false;
    }

    private void addUserToHome(String userToAdd, String homeName, String section) {
        List<String> lines = new ArrayList<>();

        try (Scanner sc = new Scanner(homes)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] parts = line.split(">");
                String[] homeOwner = parts[0].split(":");
                String currentHome = homeOwner[0];

                if (!currentHome.equals(homeName)) {
                    lines.add(line);
                    continue;
                }

                String usersPart = parts.length > 1 ? parts[1] : "";
                String[] usersList = usersPart.isEmpty() ? new String[0] : usersPart.split("/");
                boolean userFound = false;
                StringBuilder newUsersPart = new StringBuilder();

                for (int i = 0; i < usersList.length; i++) {
                    String userEntry = usersList[i];
                    String[] userData = userEntry.split(":", 2);
                    String userName = userData[0];
                    String perms = userData.length > 1 ? userData[1] : "";
                    if (userName.equals(userToAdd)) {
                        userFound = true;
                        if (perms.equals("all") || hasPerm(perms, section)) {
                            out.writeObject("USER_ALREADY_HAS_PERMS");
                            out.flush();
                            System.out.println("[" + user + " Thread] User already has perms.");
                            return;
                        } else {
                            userEntry = userName + ":" + perms + "," + section;
                        }
                    }
                    if (i > 0) newUsersPart.append("/");
                    newUsersPart.append(userEntry);
                }

                if (!userFound) {
                    if (newUsersPart.length() > 0) newUsersPart.append("/");
                    newUsersPart.append(userToAdd).append(":").append(section);
                }

                String sectionsPart = parts.length > 2 ? ">" + parts[2] : ">";
                lines.add(parts[0] + ">" + newUsersPart + sectionsPart);
            }

            try(FileWriter fw = new FileWriter(homes, false)){
				for (String l : lines){
					fw.write(l + System.lineSeparator());
				}
			}
            out.writeObject("USER_ADDED");
            out.flush();
            System.out.println("[" + user + " Thread] User " + userToAdd + " added to home " + homeName + " with section " + section);

        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private boolean hasPerm(String data, String section) {
		String[] userPerms = data.split(",");
		for (String perm : userPerms) {
			if (perm.equals(section)) return true;
		}
		return false;
    }

    private boolean isValidSection(String section) {
		for (String perm : PERMS) {
			if (perm.equals(section)) return true;
		}
		return false;
    }

	private static Number getHistory(String house, String user) {
		File house_Dir = new File(house);
		if(!house_Dir.exists()) return (int) -1; //NOHM
        try(Scanner sc = new Scanner(new File("users.txt"))) {
			while(sc.hasNextLine()){
				String [] line = sc.nextLine().split(":");
				if(line[0].equals(house)) {
					for (int idx = 1; idx < line.length; idx++) {
						if(line[idx].equals(user)) {
							File history = new File(house + "/history.txt");
							if(history.length() == 0) return (int) 0; //NODATA
							File history_to_send = new File("history_to_send.txt");
							try(Scanner sc1 = new Scanner(history); FileWriter file_To_Send = new FileWriter(history_to_send)) {
								while(sc1.hasNextLine()){
									String [] line1 = sc1.nextLine().split(":");
									file_To_Send.write("Last Operation of " + line1[0] + ": " + line1[line1.length - 1] + "\n");
								}
								return (long) history_to_send.length(); //OK
							} catch (IOException e) {
								System.err.println(e.getMessage());
								System.exit(-1);
							}
						}
					}
					return (int) 1; //NOPERM
				}
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return (int) 2;
    }

	private static int verify(String[] commands, String user) {
		try(Scanner sc = new Scanner(new File("workspaces.txt"))) {
			List<String> lines = Files.readAllLines(Paths.get("workspaces.txt"));
			int counter_lines = 0;
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] lineArgs = line.split(":");
				if (lineArgs[0].equals(commands[1]) && lineArgs[1].equals(user)) {
					lines.set(counter_lines, commands[0] + ":" + commands[1] + "," + commands[2]);
					Files.write(Paths.get("history.txt"), lines);
					return 1;
				}
				else if (lineArgs[0].equals(commands[1]) && !lineArgs[1].equals(user)) {
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

	private boolean verifyUserPermission(String homeName, String user) {
    	try (Scanner sc = new Scanner(homes)) { // Usa o ficheiro homes.txt que já tens
        	while (sc.hasNextLine()) {
           		String line = sc.nextLine();
            	if (line.startsWith(homeName + ":")) {
                	return line.contains(">" + user + ":") || line.contains("/" + user + ":");
            	}
        	}
    	} catch (IOException e) { return false; }
    	return false;
	}

	private void updateDeviceState(String homeName, String device, String value) {
    	File deviceFile = new File("homes/" + homeName + "/devicesLog.txt");
    	try (FileWriter fw = new FileWriter(deviceFile, true)) {
        	fw.write(device + ":" + value + System.lineSeparator());
    	} catch (IOException e) { System.err.println("Erro ao gravar dispositivo."); }
	}

	private void logAction(String homeName, String action) {
    	File historyFile = new File("homes/" + homeName + "/history.txt");
    	try (FileWriter fw = new FileWriter(historyFile, true)) {
        	fw.write(System.currentTimeMillis() + " - " + action + System.lineSeparator());
    	} catch (IOException e) { System.err.println("Erro ao gravar histórico."); }
	}
}