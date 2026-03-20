import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class SpertaClient {
  private int port;
  private String host;
  private String user, pwd;

  private static final String COMMAND_LIST =
  """
  Available Commands:
  CREATE <hm>
  ADD <user> <hm> <a>
  RD <hm> <s>
  EC <hm> <d> <int>
  RT <hm>
  RH <hm> <d>
  """;

  private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};

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
        System.out.print(COMMAND_LIST + "\n" + "Insert Command: ");

        //Garante que lemos a linha toda (comando + argumentos)
        String user_Command = "";
        if (user_input.hasNextLine()) {
          user_Command = user_input.nextLine();
        }
        //nao tirar isto
        //Limpeza técnica: se a linha vier vazia (comum após ler números anteriormente), tenta ler a próxima
        if (user_Command.isEmpty() && user_input.hasNextLine()) {
          user_Command = user_input.nextLine();
        }

        //Divide a string por espaços para obter os argumentos
        String[] command_Args = user_Command.split(" ");

				switch (command_Args[0]) {
					case "CREATE" -> {
            if (command_Args.length != 2) {
              System.out.println("Usage: CREATE <home_name>");
            }else {
              outStream.writeObject(command_Args);
              outStream.flush();
              String server_Response = (String) inStream.readObject();
              String response = server_Response.equals("HOME_CREATED") ? "OK" : "NOK";
              System.out.println(response);
            }
          }
					case "ADD" -> {
            if (command_Args.length != 4) {
              System.out.println("Usage: ADD <user> <home> <secção>");
            } else {
              outStream.writeObject(command_Args);
              outStream.flush();
              String server_Response = (String) inStream.readObject();
              switch (server_Response) {
                  case "USER_ADDED" -> System.out.println("OK");
                  case "USER_NOT_FOUND" -> System.out.println("NOUSER");
                  case "HOME_NOT_FOUND" -> System.out.println("NOHM");
                  case "NO_USER_PERMS" -> System.out.println("NOPERM");
                  default -> System.out.println("NOK");
              }
            }
          }
					case "RD" -> {
            if (command_Args.length != 3) {
              System.out.println("Usage: RD <home> <s>");
              break;
            }
            if (!Arrays.asList(PERMS).contains(command_Args[2])) {
              System.out.println("Device doesn't exist. Devices available: " + Arrays.toString(PERMS));
              break;
            }
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
            if (command_Args.length != 4) {
              System.out.println("Erro: Use EC <casa> <dispositivo> <valor>");
            } else {
            outStream.writeObject(command_Args);
            outStream.flush();
            String response = (String) inStream.readObject();
            System.out.println(response);
            }
          }
					case "RT" -> {
            if (command_Args.length != 2) {
              System.out.println("Usage: RT <home>");
              break;
            }
            outStream.writeObject(command_Args);
            outStream.flush();
            String [] server_Response = (String []) inStream.readObject();
            switch (server_Response[0]) {
              case "OK" ->{
                System.out.println("OK, " + server_Response[1] + " (long)." );
                try(FileOutputStream history = new FileOutputStream("history.txt")) {
                  int bytesRead;
                  int size = Integer.parseInt(server_Response[1]);
                  byte[] buffer = new byte[1024];
                  while(size > 0 && (bytesRead = inStream.read(buffer, 0, Math.min(size, buffer.length))) != -1) {
                    history.write(buffer, 0, bytesRead);
                    size -= bytesRead;
                  }
                } catch (IOException e) {
                  System.err.println(e.getMessage());
                  System.exit(-1);
                }
              }
              case "NODATA" -> System.out.println("NODATA # No data to send.");
              case "NOHM" -> System.out.println("NOHM # " + command_Args[1] + " doesn't exist.");
              case "NOPERM" -> System.out.println("NOPERM # no permissions");
              default -> throw new AssertionError();
            }
          }
					case "RH" -> {
            if (command_Args.length < 2) {
              System.out.println("Uso: RH <casa> [dispositivo]");
            } else {
              outStream.writeObject(command_Args);
              outStream.flush();
              Object response = inStream.readObject();

              if (response instanceof ArrayList<?>) {
                @SuppressWarnings("unchecked")
                ArrayList<String> lines = (ArrayList<String>) response;
                System.out.println("--- Histórico (CSV) da Casa " + command_Args[1] + " ---");
                System.out.println("Timestamp, Utilizador, Dispositivo, Valor");
                for (String l : lines) {
                  System.out.println(l);
              }
              } else {
                // Caso receba "NOHM", "NOPERM" ou "NODATA"
                System.out.println("Servidor: " + response);
              }
            }
            
          }
					default -> {
            outStream.writeObject(command_Args);
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
