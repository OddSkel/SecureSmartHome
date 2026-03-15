import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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

	private boolean running = true;
	private File users, homes, homesFolder;
  private String user, pwd;
	private ObjectInputStream in;
	private ObjectOutputStream out;

  private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};

	ServerThread(Socket inSoc) {
		socket = inSoc;
		System.out.println("thread do server para cada cliente");
	}

	public void run() {
		try{
			out = new ObjectOutputStream(socket.getOutputStream());
      in = new ObjectInputStream(socket.getInputStream());

			users = new File("usersLog.txt");
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
        while(running){
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
            }
            case "EC" -> {
            }
            case "RT" -> {
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
          if (credentials[0].equals(user)) 
            return true;
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
          if (homeData[0].equals(homeName)) 
            return true;
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

            FileWriter fw = new FileWriter(homes, false);
            for (String l : lines){
              fw.write(l + System.lineSeparator());
            }
            fw.close();
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


}