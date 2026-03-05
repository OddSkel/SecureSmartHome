
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SpertaClient {
  private int port;
  public static void main(String[] args) {
    System.out.println("cliente: main");
    SpertaClient client = new SpertaClient();
    client.port = Integer.parseInt(args[0]);
    client.startClient();
  }

  public void startClient(){
    Socket cliSoc;
    ObjectInputStream inStream;
    ObjectOutputStream outStream;

    try {
      cliSoc = new Socket("localhost", port);
      inStream = new ObjectInputStream(cliSoc.getInputStream());
      outStream = new ObjectOutputStream(cliSoc.getOutputStream());
      

      outStream.close();
      inStream.close();
      cliSoc.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
