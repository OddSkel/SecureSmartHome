import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class SpertaClient {
  private int port;
  public static void main(String[] args) {
    System.out.println("cliente: main");

    SpertaClient client = new SpertaClient();
    client.port = Integer.parseInt(args[0]);

    client.startClient();
  }

  public void startClient(){
    try(Socket cliSoc = new Socket("localhost", port);
        ObjectInput serverInfo = new ObjectInputStream(cliSoc.getInputStream());
        ObjectOutputStream clientInfo = new ObjectOutputStream(cliSoc.getOutputStream());
        Scanner user_input = new Scanner(System.in)) {

			while(true) {

				String user_Command = user_input.next();

				switch (user_Command) {
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
					default -> {
            clientInfo.writeObject(user_Command);
            String server_Response = (String) serverInfo.readObject();
            System.out.println(server_Response + "Commands Available: CREATE, ADD, RD, EC, RT, RH");
          }
				}
			}
		} catch (IOException | ClassNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
		}
	}
}
