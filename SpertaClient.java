import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class SpertaClient {
  private int port;
  private String host;
  private String user, pwd;

  private String truststore;      
  private String pass_truststore;

  private String keystore;
  private String pass_keystore;
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
    if (args.length != 7) {
      System.out.println("Usage: java SpertaClient <host:port> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>");
      System.exit(-1);
    }

    String[] serverAddress = args[0].split(":");

    SpertaClient client = new SpertaClient();
    
    client.host = serverAddress[0];
    client.port = (serverAddress.length == 2) ? Integer.parseInt(serverAddress[1]) : 22345;
    client.truststore = args[1];          
    client.pass_truststore = args[2];   
    client.keystore = args[3];         
    client.pass_keystore = args[4];     
    client.user = args[5];              
    client.pwd = args[6];

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
        ObjectOutputStream outStream = new ObjectOutputStream(cliSoc.getOutputStream());
        ObjectInputStream inStream = new ObjectInputStream(cliSoc.getInputStream());
        Scanner user_input = new Scanner(System.in)) {
        
        byte [] nounce_rec = (byte[]) inStream.readObject();
        byte [] res = nounce_rec;
        outStream.writeObject(res);
        outStream.flush();
        String integrity_check = (String) inStream.readObject();
        if (integrity_check.equals("OK-ATTEST")) {
          outStream.writeObject(truststore);
          outStream.writeObject(pass_truststore);
          outStream.writeObject(keystore);
          outStream.writeObject(pass_keystore);
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
                  break;
                } 
                if (!Arrays.asList(PERMS).contains(command_Args[3])) {
                  System.out.println("Device doesn't exist. Devices available: " + Arrays.toString(PERMS));
                  break;
                }
                
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
              case "RD" -> {
                if (command_Args.length != 3) {
                  System.out.println("Usage: RD <home> <s>");
                  break;
                }
                if (!Arrays.asList(Arrays.copyOfRange(PERMS, 1, PERMS.length)).contains(command_Args[2])) {
                  System.out.println("Device doesn't exist. Devices available: " + Arrays.toString(Arrays.copyOfRange(PERMS, 1, PERMS.length)));
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
                    System.out.println("Usage: EC <hm> <d> <int>");
                } else {
                    outStream.writeObject(new String[]{"EC", command_Args[1], command_Args[2]});
                    outStream.flush();

                    Object response = inStream.readObject();
                    
                    if (response instanceof byte[] wrappedKey) {

                        try {
                            KeyStore ks = KeyStore.getInstance("JCEKS");
                            ks.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
                            PrivateKey privKey = (PrivateKey) ks.getKey("keyrsa", pass_keystore.toCharArray());

                            Cipher rsaCipher = Cipher.getInstance("RSA");
                            rsaCipher.init(Cipher.UNWRAP_MODE, privKey);
                            SecretKey sKey = (SecretKey) rsaCipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

                            String home = command_Args[1];
                            File localLog = new File(home + "_devicesLog.txt");
                            StringBuilder logData = new StringBuilder();
                            
                            if (localLog.exists()) {
                                logData.append(Files.readString(localLog.toPath()));
                            }
                            // Adicionar nova entrada
                            logData.append(System.currentTimeMillis()).append(",")
                                  .append(command_Args[2]).append(",")
                                  .append(command_Args[3]).append("\n");
                            
                            Files.writeString(localLog.toPath(), logData.toString());
                            

                            Cipher aesCipher = Cipher.getInstance("AES");
                            aesCipher.init(Cipher.ENCRYPT_MODE, sKey);

                            byte[] encryptedVal = aesCipher.doFinal(command_Args[3].getBytes());

                            outStream.writeObject(encryptedVal);
                            outStream.flush();

                            System.out.println(inStream.readObject());

                        } catch (Exception e) {
                            System.err.println("Erro na criptografia: " + e.getMessage());
                        }
                    } else {
                        // Se não for byte[], é uma mensagem de erro
                        System.out.println("Erro do servidor: " + response);
                    }
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
                    try(FileOutputStream history = new FileOutputStream(command_Args[1] + "_history.txt")) {
                      int bytesRead;
                      long size = Long.parseLong(server_Response[1]);
                      byte[] buffer = new byte[1024];
                      while(size > 0 && (bytesRead = inStream.read(buffer, 0, (int) Math.min(size, (long) buffer.length))) != -1) {
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
                if (command_Args.length != 4) {
                    System.out.println("Usage: EC <hm> <d> <int>");
                } else {
                    outStream.writeObject(new String[]{"EC", command_Args[1], command_Args[2]});
                    outStream.flush();

                    Object responseObj = inStream.readObject();
                    
                    // Se a resposta for byte[], significa que o servidor enviou a chave com sucesso
                    if (responseObj instanceof byte[] wrappedKey) {
                        try {
                            //Decifrar a chave de secção (AES) usando a chave privada RSA do cliente
                            KeyStore ks = KeyStore.getInstance("JCEKS");
                            ks.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
                            PrivateKey privKey = (PrivateKey) ks.getKey("keyrsa", pass_keystore.toCharArray());

                            Cipher rsaCipher = Cipher.getInstance("RSA");
                            rsaCipher.init(Cipher.UNWRAP_MODE, privKey);
                            SecretKey sKey = (SecretKey) rsaCipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

                            //Lógica do LOG LOCAL para evitar duplicação do mesmo dispositivo
                            String home = command_Args[1];
                            File localLog = new File(home + "_devicesLog.txt");

                            //Usar um Map para garantir que cada dispositivo guarda apenas o último valor
                            java.util.Map<String, String> deviceStates = new java.util.LinkedHashMap<>();

                            if (localLog.exists()) {
                                java.util.List<String> lines = java.nio.file.Files.readAllLines(localLog.toPath());
                                for (String line : lines) {
                                    String[] parts = line.split(",");
                                    // O ficheiro tem o formato: timestamp, dispositivo, valor
                                    if (parts.length >= 3) {
                                        deviceStates.put(parts[1], line); 
                                    }
                                }
                            }

                            //Atualizar ou inserir a nova entrada com o último estado e o respetivo timestamp
                            String newLine = System.currentTimeMillis() + "," + command_Args[2] + "," + command_Args[3];
                            deviceStates.put(command_Args[2], newLine);

                            //Reconstruir o conteúdo do ficheiro com os últimos estados de cada dispositivo
                            StringBuilder logData = new StringBuilder();
                            for (String line : deviceStates.values()) {
                                logData.append(line).append("\n");
                            }

                            java.nio.file.Files.writeString(localLog.toPath(), logData.toString());

                            //Cifrar o novo valor usando a chave de secção AES decifrada
                            Cipher aesCipher = Cipher.getInstance("AES");
                            aesCipher.init(Cipher.ENCRYPT_MODE, sKey);

                            byte[] encryptedVal = aesCipher.doFinal(command_Args[3].getBytes());

                            //Enviar o valor cifrado para o servidor
                            outStream.writeObject(encryptedVal);
                            outStream.flush();

                            System.out.println(inStream.readObject());

                        } catch (Exception e) {
                            System.err.println("Erro na criptografia: " + e.getMessage());
                        }
                    } 
                    // Se a resposta for uma String, significa que o servidor enviou uma mensagem de erro
                    else if (responseObj instanceof String response) {
                        switch (response) {
                            case "NOHM" -> System.out.println("NOHM # esta casa não existe");
                            case "NOD" -> System.out.println("NOD # dispositivo não existe");
                            case "NOPERM" -> System.out.println("NOPERM # sem permissões");
                            case "NODATA" -> System.out.println("NODATA # sem dados");
                            case "NOKEY" -> System.out.println("NOKEY # erro de chave no servidor");
                            default -> System.out.println(response);
                        }
                    } else {
                        System.out.println("Erro desconhecido do servidor.");
                    }
                  }
                }
              default -> {
                outStream.writeObject(command_Args);
                String server_Response = (String) inStream.readObject();
                System.out.println(server_Response + " Commands Available: CREATE, ADD, RD, EC, RT, RH");
              }
            }
          }
        }
        else{
          System.out.println("INTEGRITY VIOLATION. EXITING...");
          System.exit(-1);
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
        } else if (serverMsg.equals("SEND_CERT")) {
          // O servidor pediu o certificado, vamos enviá-lo
          File certFile = new File("Certs/" + user + ".cer");
          if (certFile.exists()) {
              out.writeLong(certFile.length()); // Envia o tamanho
              byte[] content = Files.readAllBytes(certFile.toPath());
              out.write(content); // Envia o conteúdo
              out.flush();
          } else {
              System.err.println("Erro: Certificado não encontrado em " + certFile.getPath());
              out.writeLong(0);
              out.flush();
          }
        } else if (serverMsg.equals("OK_USER") || serverMsg.equals("OK_NEW_USER")) {
          // Só consideramos o user autenticado nestes dois casos explícitos
          userOk = true;
        }
      }
    } catch (IOException | ClassNotFoundException e) {
      System.err.println(e.getMessage());
    }
  }


}
