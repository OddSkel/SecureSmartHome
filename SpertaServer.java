import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
		try{
			out = new ObjectOutputStream(socket.getOutputStream());
      in = new ObjectInputStream(socket.getInputStream());
      String user, pwd;
			users = new File("usersLog.txt");
      if (!users.exists()) {
        users.createNewFile();
      }
      workspaces = new File("workspaces.txt");
      if (!workspaces.exists()) {
        workspaces.createNewFile();
      }

      try {
				user = (String) in.readObject();
				pwd = (String) in.readObject();
				System.out.println("["+ user +" Thread] Authentication request received for user: " + user);
        authenticate(user, pwd);
        while(running){
          String client_Command = (String) in.readObject();
          switch (client_Command) {
            case "CREATE" -> {
                  }
            case "ADD" -> {
                  }
            case "RD" -> {
                  }
            case "EC" -> {
				String[] parts = client_Command.split(" ");
    			String response = processEC(parts); 
    			out.writeObject(response);         
    			out.flush();
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
      System.out.println("[Thread] New user created: " + user);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
    }

	private String processEC(String[] parts){
		if (parts.length != 4) return "NOK";

    	String casa = parts[1];
    	String dispositivo = parts[2];
    	int valor;

    	try {
        	valor = Integer.parseInt(parts[3]);
        	if (valor < 0 || valor > 600) return "NOK"; 
    	} catch (NumberFormatException e) {
        	return "NOK";
    	}
    	saveLog(casa, dispositivo, valor);
    	return "OK";
	}

	private void saveLog(String casa, String dispositivo, int valor) {
    	String fileName = casa + "_" + dispositivo + "_log.csv";
    	File logFile = new File(fileName);
    
    	try (FileWriter fw = new FileWriter(logFile, true)) {
        	String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        	fw.write(timestamp + "," + valor + System.lineSeparator());
        
        	updateCurrentState(casa, dispositivo, valor);
    	} catch (IOException e) {
        	System.err.println("Erro ao gravar log: " + e.getMessage());
    	}
	}

	private void updateCurrentState(String casa, String dispositivo, int valor) {
    	File statesFile = new File("current_states.txt");
    	try (FileWriter fw = new FileWriter(statesFile, true)) {
       		String logEntry = String.format("Casa: %s, Dispositivo: %s, Estado: %d, Data: %s%n", 
                          casa, dispositivo, valor, LocalDateTime.now().toString());
        	fw.write(logEntry);
        	System.out.println("[" + this.getName() + "] Estado atualizado em current_states.txt");
    	} catch (IOException e) {
        	System.err.println("Erro ao atualizar estado: " + e.getMessage());
    	}
	}

}