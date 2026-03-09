import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class SpertaClient {
  private int port;
  private String host;
  private String user, pwd;

  private static final String COMMAND_LIST =
  "Available Commands:\n" +
  "CREATE <hm>\n" +
  "ADD <user> <hm> <a>\n" +
  "RD <hm> <s>\n" +
  "EC <hm> <d> <int>\n" +
  "RT <hm>\n" +
  "RH <hm> <d>";
  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("Usage: java SpertaClient <host:port> <username> <password>");
      System.exit(-1);
    }

    String[] serverAddress = args[0].split(":");

    SpertaClient client = new SpertaClient();
    client.user = args[1];
    client.pwd = args[2];
    client.host = serverAddress[0];
    switch (serverAddress.length) {
      case 2 -> client.port = Integer.parseInt(serverAddress[1]);
      case 1 -> client.port = 22345;
      default -> {
        System.out.println("Usage: server address should be in the format <host> or <host:port>");
        System.exit(-1);
      }
    }

    client.startClient();
  }

  public void startClient(){
    try(Socket cliSoc = new Socket(host, port);
        ObjectInput inStream = new ObjectInputStream(cliSoc.getInputStream());
        ObjectOutputStream outStream = new ObjectOutputStream(cliSoc.getOutputStream());
        Scanner user_input = new Scanner(System.in)) {

      outStream.writeObject(user);
      outStream.writeObject(pwd);
      outStream.flush();
      checkSResp(inStream, outStream, user_input);
      
      System.out.println(COMMAND_LIST);
			while(true) {
        System.out.print("Insert command: ");
        String line = user_input.nextLine().trim();
        if (line.isEmpty()) {
            continue; 
        }
				String[] cmd = line.split(" ");
        String user_Command = cmd[0].toUpperCase();
				switch (user_Command) {
					case "CREATE" -> {
            if (cmd.length != 2) {
              System.out.println("Usage: CREATE <home_name>");
              continue;
            }else {
              outStream.writeObject(user_Command);
              outStream.writeObject(cmd[1]);
              outStream.flush();
              String server_Response = (String) inStream.readObject();
              System.out.println(server_Response);
            }
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
					default -> {
            outStream.writeObject(user_Command);
            String server_Response = (String) inStream.readObject();
            System.out.println(server_Response + COMMAND_LIST);
          }
				}
			}
		} catch (IOException | ClassNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
		}
	}

  private void checkSResp(ObjectInput in, ObjectOutputStream out, Scanner sc) {
    try{
      boolean userOk = false;
      while(!userOk){
        String serverMsg = (String) in.readObject();
        if(serverMsg.equals("WRONG_PWD")){
          System.out.print("Inserir novamente palavra-passe:");
          pwd = sc.nextLine();
          out.writeObject(pwd);
          out.flush();
        } else {
          userOk = true;
        }
      }
    } catch (IOException | ClassNotFoundException e) {
      System.err.println(e.getMessage());
    }
  }


}
