import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

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
                String [] server_Response = (String []) inStream.readObject();
                switch (server_Response[0]) {
                    case "OK"->{
                      File f = new File("key." + command_Args[1] + "." + command_Args[2] + "." + user);
                      try(FileOutputStream key = new FileOutputStream(f)) {
                        int bytesRead;
                        long size = (long) inStream.readObject();
                        byte[] buffer = new byte[1024];
                        while(size > 0 && (bytesRead = inStream.read(buffer, 0, (int) Math.min(size, (long) buffer.length))) != -1) {
                          key.write(buffer, 0, bytesRead);
                          size -= bytesRead;
                        }
                      }catch (IOException e) {
                        System.err.println(e.getMessage());
                        System.exit(-1);
                      }
                      File log;
                      if (!"0".equals(server_Response[1])){
                        log = new File(server_Response[2]);
                        long size = Long.parseLong(server_Response[1]);
                        long filesize = size;
                        try(FileOutputStream device = new FileOutputStream(log)) {
                        int bytesRead;
                        byte[] buffer = new byte[1024];
                        while(size > 0 && (bytesRead = inStream.read(buffer, 0, (int) Math.min(size, (long) buffer.length))) != -1) {
                          device.write(buffer, 0, bytesRead);
                          size -= bytesRead;
                        }
                      }catch (IOException e) {
                        System.err.println(e.getMessage());
                        System.exit(-1);
                      }
                        decipher(f,log, filesize);
                        cipher(server_Response[2], f, outStream);

                        log.delete();
                      }else{
                        log = new File(command_Args[2] + "0.txt");
                        log.createNewFile();
                        cipher(log.getName(), f, outStream);
                        log.delete();
                      }
                      log.delete();
                      f.delete();
                      System.out.println("OK");
                    }
                    case "NOPERM" -> System.out.println("NOPERM # no permissions");
                    case "NOHM" -> System.out.println("NOHM # no such house");
                    default -> throw new AssertionError();
                }
              }
              case "EC" -> {
                if (command_Args.length != 4) {
                    System.out.println("Usage: EC <hm> <d> <int>");
                    break;
                } 

                int value;
                try {
                    value = Integer.parseInt(command_Args[3]);
                    if (value < 0 || value > 600) {
                        System.out.println("NOK"); // Valor fora dos limites, aborta
                        break; // Faz com que volte a pedir o "Insert Command:"
                    }
                } catch (NumberFormatException e) {
                    System.out.println("NOK"); // Não é um número inteiro válido
                    break;
                }

                // 2. Se o valor for válido, prossegue com a comunicação normal
                try {
                    // Enviar o comando inicial
                    outStream.writeObject(command_Args);
                    outStream.flush();

                    Object response = inStream.readObject();
                    
                    // O servidor envia a chave da secção cifrada
                    if (response instanceof byte[]) {
                        byte[] keyBytes = (byte[]) response;

                        // Criar ficheiro temporário para a tua função getKey(File f)
                        File tempKey = new File("temp_ec.key");
                        try (FileOutputStream fos = new FileOutputStream(tempKey)) {
                            fos.write(keyBytes);
                        }
                        // Usar a tua função para fazer o unwrap da chave AES
                        Key sectionKey = getKey(tempKey); 
                        tempKey.delete();
                        
                        if (sectionKey != null) {
                            // Passo c: Cifrar o valor e enviar (já não precisamos de verificar limites aqui)
                            Cipher c = Cipher.getInstance("AES");
                            c.init(Cipher.ENCRYPT_MODE, sectionKey);
                            
                            // Converter o int para bytes e cifrar
                            byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
                            byte[] encryptedValue = c.doFinal(valueBytes);

                            outStream.writeObject(encryptedValue);
                            outStream.flush();

                            System.out.println((String) inStream.readObject()); // Imprime o OK final do servidor
                        }
                    } else {
                        System.out.println(response); // Mensagens de erro do servidor como NOPERM ou NOHM
                    }
                } catch (Exception e) {
                    System.err.println("Erro no comando EC: " + e.getMessage());
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
                      break;
                    }

                    // 1. Validar o valor de <int> (0, 1, ou 2 a 600)
                    try {
                      int intValue = Integer.parseInt(command_Args[3]);
                      if (intValue < 0 || intValue > 600) {
                        System.out.println("NOK");
                        break;
                      }
                    } catch (NumberFormatException e) {
                      System.out.println("NOK");
                      break;
                    }
                    
                    // Enviar pedido inicial ao servidor
                    outStream.writeObject(command_Args);
                    outStream.flush();

                    String server_Response = (String) inStream.readObject();
                    switch (server_Response) {
                      case "OK" -> {
                        try {
                          //Receber a chave da secção cifrada (Wrapped Key)
                          byte[] wrappedKey = (byte[]) inStream.readObject();

                          //Carregar a Keystore para obter a Chave Privada
                          File kS = new File("Keys", keystore);
                          KeyStore kstore = KeyStore.getInstance("JCEKS");
                          try (FileInputStream kfile = new FileInputStream(kS)) {
                            kstore.load(kfile, pass_keystore.toCharArray());
                          }
                          PrivateKey pk = (PrivateKey) kstore.getKey("keyrsa", pass_keystore.toCharArray());

                          //Decifrar a Chave de Secção (AES) com a Privada (RSA)
                          Cipher rsaCipher = Cipher.getInstance("RSA");
                          rsaCipher.init(Cipher.UNWRAP_MODE, pk);
                          SecretKey sectionKey = (SecretKey) rsaCipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

                          //Preparar os dados: "timestamp,dispositivo:valor"
                          String payload = System.currentTimeMillis() + "," + command_Args[2] + ":" + command_Args[3];
                          
                          //Cifrar o payload com AES
                          Cipher aesCipher = Cipher.getInstance("AES");
                          aesCipher.init(Cipher.ENCRYPT_MODE, sectionKey);
                          byte[] encryptedPayload = aesCipher.doFinal(payload.getBytes());

                          //Enviar os bytes cifrados ao servidor
                          outStream.writeObject(encryptedPayload);
                          outStream.flush();

                          //Confirmar se o servidor guardou com sucesso
                          String finalStatus = (String) inStream.readObject();
                          System.out.println(finalStatus); // Imprime OK_EC ou NOK

                        } catch (Exception e) {
                          System.out.println("NOK");
                        }
                      }
                      case "NOHM" -> System.out.println("NOHM # no such house");
                      case "NOPERM" -> System.out.println("NOPERM # no permissions");
                      case "NOKEY" -> System.out.println("NOKEY # error fetching section key");
                      default -> System.out.println("NOK");
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
  
  private void cipher(String decrypted_file, File key, ObjectOutputStream outStream) {
    try {
        Key aesKey = getKey(key);
        if (aesKey == null) throw new KeyException("Key not found!");

        String nameWithoutExt = decrypted_file.substring(0, decrypted_file.lastIndexOf('.'));
        int number = Character.getNumericValue(nameWithoutExt.charAt(1));
        number++;

        String filename = nameWithoutExt.substring(0, 1) + number + ".txt";

        try(FileWriter fW = new FileWriter(decrypted_file, true)) {
					fW.write(System.currentTimeMillis() + "," + nameWithoutExt.charAt(0) + ":" + number + System.lineSeparator());
				} catch (Exception e) {
					System.err.println(e.getMessage());
					System.exit(-1);
				}

        File f = new File(filename);
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, aesKey);

        // Encrypt to temp file first
        File d = new File(decrypted_file);
        File tempFile = new File(filename + ".enc");
        try (FileInputStream logFile = new FileInputStream(d);
            FileOutputStream tempOut = new FileOutputStream(tempFile);
            CipherOutputStream cout = new CipherOutputStream(tempOut, c)) {
            int bytesToRead;
            byte[] buf = new byte[1024];
            while ((bytesToRead = logFile.read(buf, 0, buf.length)) != -1) {
                cout.write(buf, 0, bytesToRead);
            }
        }

        outStream.writeObject(filename);
        outStream.writeLong(tempFile.length());
        outStream.flush();

        try (FileInputStream encStream = new FileInputStream(tempFile)) {
            int bytesToRead;
            byte[] buf = new byte[1024];
            while ((bytesToRead = encStream.read(buf, 0, buf.length)) != -1) {
                outStream.write(buf, 0, bytesToRead);
                outStream.flush();
            }
        }
        tempFile.delete();
        f.delete();
    } catch (IOException | KeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
      System.err.println(e.getMessage());
			System.exit(-1);
    }
  }

  private void decipher(File key, File log, long size) {
    String fileName = "received_log_file.txt";
    try {
        Key aesKey = getKey(key);
        if (aesKey == null) throw new KeyException("Key not found!");

        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, aesKey);

        try ( FileOutputStream fileReceived = new FileOutputStream(fileName);
              FileInputStream fin = new FileInputStream(log);
              CipherInputStream cipherIn = new CipherInputStream(fin, c)) {
                
          int bytesRead;
          byte[] buffer = new byte[1024];
          while (size > 0 && (bytesRead = cipherIn.read(buffer, 0, (int) Math.min(size, buffer.length))) != -1) {
              fileReceived.write(buffer, 0, bytesRead);
              size -= bytesRead;
          }
      } catch (IOException e) {
          System.err.println(e.getMessage());
          System.exit(-1);
      }
    } catch (KeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }
    File f = new File(fileName);
    f.delete();
  }

  private Key getKey(File f) {
    Key aesKey = null;
    try {
      byte[] chaveAEScifrada;
      try (FileInputStream kos = new FileInputStream(f)) {
          chaveAEScifrada = new byte[kos.available()];
          kos.read(chaveAEScifrada);
      }

      KeyStore kstore = KeyStore.getInstance("JCEKS");
      kstore.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
      Key myprivateKey = kstore.getKey("keyrsa", pass_keystore.toCharArray());
      if (myprivateKey == null) {
          throw new Exception("Private key for user '" + user + "' not found in keystore.");
      }
      PrivateKey pk = (PrivateKey) myprivateKey;
  
      Cipher cRSA = Cipher.getInstance("RSA");
      cRSA.init(Cipher.UNWRAP_MODE, pk);
      aesKey = cRSA.unwrap(chaveAEScifrada, "AES", Cipher.SECRET_KEY);
  
      if (!(aesKey instanceof SecretKey)) {
          throw new Exception("Key is not a SecretKey!");
      }
      byte[] keyBytes = aesKey.getEncoded();
      if (keyBytes == null || (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32)) {
          throw new Exception("Invalid AES key length: " + (keyBytes == null ? "null" : keyBytes.length) + " bytes");
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }
    return aesKey;
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
