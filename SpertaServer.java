import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

			users = new File("usersLog.txt");
			if (!users.exists()) {
				users.createNewFile();
			}

			homes = new File("homesLog.txt");
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
                  if(checkOwner(homeName, userToAdd)){
                    out.writeObject("USER_ADDING_SELF");
                    out.flush();
                    System.out.println("["+ user +" Thread] ADD command failed. User cannot add itself to home: " + homeName);
                  } else{
                    if(!isValidSection(section)) {
                    out.writeObject("INVALID_SECTION");
                    out.flush();
                    System.out.println("["+ user +" Thread] ADD command failed. Invalid section: " + section);
                    }else{
                    addUserToHome(userToAdd, homeName, section);
                    }
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
								out.writeObject("NOPERM");
							} else {
								updateDeviceState(homeName, device, value);
								//Registar no histórico da casa em homes/<casa>/history.txt
								logAction(homeName, user, device, value);
					
								out.writeObject("OK");
							}
							out.flush();
						}
						case "RT" -> {
							Number result = getHistory(client_Commands[1], user);
							if(result instanceof Long) {
								out.writeObject(new String[]{"OK", Long.toString((long) result)});
								try(FileInputStream history_To_Send = new FileInputStream("homes/" + client_Commands[1] + "/recent.txt")){
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
									case 0 -> out.writeObject(new String[]{"NODATA"});
									case 1 -> out.writeObject(new String[]{"NOPERM"});
									case -1 -> out.writeObject(new String[]{"NOHM"});
									default -> throw new AssertionError();
								}
								out.flush();
							}
						}
						case "RH" -> {
							String homeName = client_Commands[1];
    						// Se o utilizador escreveu "RH casa disp", o filtro é o 3º argumento
							String deviceFilter = (client_Commands.length == 3) ? client_Commands[2] : null;

							if (!homeExists(homeName)) {
								out.writeObject("NOHM");
							} else if (!checkOwner(homeName, user) && !verifyUserPermission(homeName, user)) {
								out.writeObject("NOPERM");
							} else {
								ArrayList<String> history = getHistoryCSV(homeName, deviceFilter);
								if (history.isEmpty()) {
									out.writeObject("NODATA");
								} else {
									out.writeObject(history); // Envia a lista de linhas do CSV
								}
							}
							out.flush();
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
					fw.write(homeName + ":"+ user + ">>E:0;G:0;L:0;M:0;P:0;S:0" + System.lineSeparator());

					File newHomeFolder = new File(homesFolder, homeName);
					newHomeFolder.mkdirs();
					File devicesFile = new File(newHomeFolder, "devicesLog.txt");
					devicesFile.createNewFile();

					for(String section : PERMS) {
						if(section.equals("all")) continue;
						File sectionFolder = new File(newHomeFolder, section);
						sectionFolder.mkdirs();
					}

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
                            userEntry = userName + ":" + section;
                        } else if (section.equals("all")) {
                            userEntry = userName + ":all";
                        }else {
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

	private Number getHistory(String house, String user) {
		File home = new File("homes/" + house);
		if(!homeExists(house)) return (int) -1; //NOHM
		try(FileOutputStream recent = new FileOutputStream(home.getAbsolutePath() + "/recent.txt");
			Scanner sc = new Scanner(new File("homesLog.txt"))) {

			List<String> devices_Lines = Files.readAllLines(Path.of(home.getAbsolutePath() + "/devicesLog.txt"));
			Map<String, String> latestByDevice = new LinkedHashMap<>();
			long countLength = 0;

			if(checkOwner(house, user)) {
				File devicesLog = new File(home.getPath() + "/devicesLog.txt");
				if (devicesLog.length() == 0) return (int) 0; //NODATA
				for (String line : devices_Lines) {
					String[] parts = line.split(":");
					latestByDevice.put(parts[0], parts[1]);
				}
			}

			else if(verifyUserPermission(house, user)) {
				File devicesLog = new File(home.getPath() + "/devicesLog.txt");
				if (devicesLog.length() == 0) return (int) 0; //NODATA
				while (sc.hasNextLine()) {
					String homesLine = sc.nextLine();
					if (homesLine.contains(house)) {
						String [] owners = homesLine.split(">");
						String [] users_from_File = owners[1].split("/");

						for (String user1 : users_from_File) {
							String [] devices = user1.split(":");

							if (user.equals(devices[0])) {
								for (String line : devices_Lines) {
									String[] parts = line.split(":");

									if (parts[0].contains(devices[1]) || devices[1].equals("all"))
										latestByDevice.put(parts[0], parts[1]);
								}
							}
						}
					}
				}
			} else {
				return (int) 1; //NOPERM
			}
			for (Map.Entry<String, String> entry : latestByDevice.entrySet()) {
				String new_line = entry.getKey() + ":" + entry.getValue() + System.lineSeparator();
				recent.write(new_line.getBytes());
				countLength += new_line.getBytes().length;
			}
			return countLength; //OK

		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}

        return (int) 2; //ERROR
    }

	private int verify(String[] commands, String user) {
		if(!homeExists(commands[1])) return -1; //NOHM
		try(Scanner sc = new Scanner(new File("homesLog.txt"))) {
			Path path = Path.of("homesLog.txt");
			List<String> lines = Files.readAllLines(path);
			List<String> updated = new ArrayList<>();
			while (sc.hasNextLine()) {
				if (checkOwner(commands[1], user)) {
					for (String line : lines) {
						int last = line.lastIndexOf('>');
						String devicesPart = line.substring(last + 1);
						String owners = line.substring(0, last);
						String [] house_Owner = line.split(">");
						if (house_Owner[0].contains(user) && house_Owner[0].contains(commands[1])) {
							String [] devices = devicesPart.split(";");
							int i = 0;
							while (!devices[i].contains(commands[2])) i++;
							line = updatedDevice(devices, devices[i], commands);
							updated.add(owners + ">" + line);
						} else updated.add(line);
					}
					Files.write(path, updated);
					return 1; //OK
				} else return 0; //NOPERM
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		return 2;
	}

	private String updatedDevice(String[] devices, String Key, String [] target) {
		StringBuilder sB = new StringBuilder();
		String [] targetKey = Key.split(":");
		for (int i = 0; i < devices.length; i++) {
			String [] kV = devices[i].split(":");
			String key = kV[0];
			String value = kV[1];
			if (key.equals(targetKey[0])){
				int counter = Integer.parseInt(value);
				counter++;
				value = String.valueOf(counter);
				try(FileWriter fW = new FileWriter(Paths.get("homes/" + target[1], target[2], target[2] + value + ".txt").toString())) {
					fW.write(System.currentTimeMillis() + "," + key + ":" + value + System.lineSeparator());
				} catch (Exception e) {
					System.err.println(e.getMessage());
					System.exit(-1);
				}
			}
			sB.append(key).append(":").append(value);
			if (i < devices.length - 1) sB.append(";");
		}
		return sB.toString();
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

	private void logAction(String homeName, String userName, String device, String value) {
    File historyFile = new File(homesFolder, homeName + "/history.txt");
    try (FileWriter fw = new FileWriter(historyFile, true)) {
        String csvLine = System.currentTimeMillis() + "," + userName + "," + device + "," + value;
        fw.write(csvLine + System.lineSeparator());
    } catch (IOException e) { 
        System.err.println("Erro ao gravar histórico."); 
    }	
	}

	private ArrayList<String> getHistoryCSV(String homeName, String deviceFilter) {
    ArrayList<String> entries = new ArrayList<>();
    File historyFile = new File(homesFolder, homeName + "/history.txt");

    if (!historyFile.exists()) return entries;

    try (Scanner scanner = new Scanner(historyFile)) {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] columns = line.split(","); // Divide o CSV pelas vírgulas
            // columns[2] é o dispositivo. Se não houver filtro OU se o dispositivo coincidir:
            if (deviceFilter == null || (columns.length > 2 && columns[2].equals(deviceFilter))) {
                entries.add(line);
            }
        }
    } catch (IOException e) {
        System.err.println("Erro ao ler o histórico CSV.");
    }
    return entries;
	}
}