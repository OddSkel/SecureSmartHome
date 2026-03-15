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
        ObjectInputStream inStream = new ObjectInputStream(cliSoc.getInputStream());
        ObjectOutputStream outStream = new ObjectOutputStream(cliSoc.getOutputStream());
        Scanner user_input = new Scanner(System.in)) {

      outStream.writeObject(user);
      outStream.writeObject(pwd);
      outStream.flush();
      checkSResp(inStream, outStream, user_input);
      
			while(true) {
        System.out.println(COMMAND_LIST);

				String user_Command = user_input.next();
        String [] command_Args = user_Command.split(" ");

				switch (command_Args[0]) {
					case "CREATE" -> {
            if (command_Args.length != 2) {
              System.out.println("Usage: CREATE <home_name>");
            }else {
              outStream.writeObject(user_Command);
              outStream.writeObject(command_Args[1]);
              outStream.flush();
              String server_Response = (String) inStream.readObject();
              if(server_Response.equals("HOME_CREATED")){
                System.out.println("OK");
              } else{
                System.out.println("NOK");
              }
            }
          }
					case "ADD" -> {
            if (command_Args.length != 4) {
              System.out.println("Usage: ADD <user> <home> <secção>");
            } else {
              outStream.writeObject(user_Command);
              outStream.writeObject(command_Args[1]);
              outStream.writeObject(command_Args[2]);
              outStream.writeObject(command_Args[3]);
              outStream.flush();
              String server_Response = (String) inStream.readObject();
              if(server_Response.equals("USER_ADDED")){
                System.out.println("OK");
              } else if(server_Response.equals("USER_NOT_FOUND")){
                System.out.println("NOUSER");
              } else if(server_Response.equals("HOME_NOT_FOUND")){
                System.out.println("NOHM");
              } else if (server_Response.equals("NO_USER_PERMS")){
                System.out.println("NOPERM");
              } else {
                System.out.println("NOK");
              }
            }
          }
					case "RD" -> {
            outStream.writeObject(command_Args);
            outStream.flush();
            String server_Response = (String) inStream.readObject();
            switch (server_Response) {
                case "OK"-> System.out.println("OK");
                case "NOPERM" -> System.out.println("NOPERM # no permissions");
                case "NOHM" -> System.out.println("NOHM # no such house");
                default -> throw new AssertionError();
            }
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
            System.out.println(server_Response + "Commands Available: CREATE, ADD, RD, EC, RT, RH");
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
          System.out.print("Inserir novamente palavra-passe: ");
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
