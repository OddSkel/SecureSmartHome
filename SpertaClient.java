import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class SpertaClient {
  private int port;
  public static void main(String[] args) {
    System.out.println("cliente: main");

    SpertaClient client = new SpertaClient();
    client.port = Integer.parseInt("23456");

    client.startClient();
  }

  public void startClient(){
    try(Socket cliSoc = new Socket("localhost", port);
        ObjectOutputStream clientInfo = new ObjectOutputStream(cliSoc.getOutputStream());
        ObjectInputStream serverInfo = new ObjectInputStream(cliSoc.getInputStream());
        Scanner user_input = new Scanner(System.in)) {
			while(true) {

        System.out.println("""
                            Commands Available: CREATE <hm>: Create house in server
                                                ADD <user1> <hm> <s>: Add <user1> to house <hm>, section <s>
                                                RD <hm> <s>: Register device in <hm>, section <s>
                                                EC <hm> <d> <int>: Send <int> to manage device <d> in house <hm>
                                                RT <hm>: Obtain last commands of state saved in server related to <hm> 
                                                RH <hm> <d>: Obtain history of device <d> in house <hm>""");
        System.out.print("Enter command: ");
				String user_Command = user_input.nextLine().trim();
        String [] command_Args = user_Command.split(" ");

				switch (command_Args[0]) {
					case "CREATE" -> {
            
          }
					case "ADD" -> {
            
          }
					case "RD" -> {
            clientInfo.writeObject(command_Args);
            clientInfo.flush();
            String server_Response = (String) serverInfo.readObject();
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
            clientInfo.writeObject(user_Command);
            String server_Response = (String) serverInfo.readObject();
            System.out.println("\n" + server_Response + ": Command not recognized by server \n");
          }
				}
			}
		} catch (IOException | ClassNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
		}
	}
}
