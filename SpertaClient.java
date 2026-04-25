import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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
    System.setProperty("javax.net.ssl.trustStore", "Certs/truststore.client");
    System.setProperty("javax.net.ssl.trustStorePassword", "Truststore");
    SocketFactory sf = SSLSocketFactory.getDefault();
    try(SSLSocket cliSoc = (SSLSocket)sf.createSocket(host, port);
        //Socket cliSoc = new Socket(host, port);
        ObjectOutputStream outStream = new ObjectOutputStream(cliSoc.getOutputStream());
        ObjectInputStream inStream = new ObjectInputStream(cliSoc.getInputStream());
        Scanner user_input = new Scanner(System.in)) {
        
        try{
          byte[] nounce_rec = (byte[]) inStream.readObject();
          byte[] jarBytes = Files.readAllBytes(Paths.get("SpertaClient.jar"));
          byte[] combined = new byte[nounce_rec.length + jarBytes.length];
          System.arraycopy(nounce_rec, 0, combined, 0, nounce_rec.length);
          System.arraycopy(jarBytes, 0, combined, nounce_rec.length, jarBytes.length);

          MessageDigest md = MessageDigest.getInstance("SHA-256");
          byte[] hashBytes = md.digest(combined);

          outStream.writeObject(hashBytes);
          outStream.flush();
        } catch (Exception e) {
          System.err.println("SHA-256 algorithm not found: " + e.getMessage());
          System.exit(-1);
        }

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
                      // 1. Receber a chave como Objeto
                      byte[] keyBytes = (byte[]) inStream.readObject();
                      try(FileOutputStream key = new FileOutputStream(f)) {
                          key.write(keyBytes);
                      }
                      
                      File log;
                      if (!"0".equals(server_Response[1])){
                          log = new File(server_Response[2]);
                          
                          // 2. Receber o ficheiro do dispositivo como Objeto
                          byte[] fileBytes = (byte[]) inStream.readObject();
                          
                          try(FileOutputStream device = new FileOutputStream(log)) {
                              device.write(fileBytes);
                          }
                          
                          decipher(f, log, log.getName());
                          cipher(server_Response[2], f, outStream);
                          log.delete();
                      } else {
                          log = new File(command_Args[2] + "0.txt");
                          log.createNewFile();
                          cipher(log.getName(), f, outStream);
                          log.delete();
                      }
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
                        System.out.println("NOK");
                        break;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("NOK");
                    break;
                }

                try {
                    outStream.writeObject(command_Args);
                    outStream.flush();

                    Object response = inStream.readObject();
                    
                    if ("OK_EC".equals(response)) {
                        // 1. Receber e extrair a Chave da Secção
                        byte[] sectionKeyBytes = (byte[]) inStream.readObject();
                        File tempSecKey = new File("temp_ec_sec.key");
                        try (FileOutputStream fos = new FileOutputStream(tempSecKey)) { fos.write(sectionKeyBytes); }
                        Key sectionKey = getKey(tempSecKey);
                        tempSecKey.delete();
                        
                        // 2. Receber e extrair a Chave da Casa
                        byte[] homeKeyBytes = (byte[]) inStream.readObject();
                        File tempHomeKey = new File("temp_ec_home.key");
                        try (FileOutputStream fos = new FileOutputStream(tempHomeKey)) { fos.write(homeKeyBytes); }
                        Key homeKey = getKey(tempHomeKey);
                        tempHomeKey.delete();
                        
                        // 3. Receber o devicesLog cifrado do servidor
                        byte[] encryptedLog = (byte[]) inStream.readObject();
                        
                        if (sectionKey != null && homeKey != null) {
                            // --- A) Processar o devicesLog (decifrar, atualizar, cifrar) ---
                            Map<String, String> deviceStates = new LinkedHashMap<>();
                            
                            if (encryptedLog.length > 0) {
                                Cipher cipherDec = Cipher.getInstance("AES");
                                cipherDec.init(Cipher.DECRYPT_MODE, homeKey);
                                byte[] decryptedLog = cipherDec.doFinal(encryptedLog);
                                String logContent = new String(decryptedLog);
                                
                                // Carregar o estado atual para o Map
                                String[] lines = logContent.split(System.lineSeparator());
                                for (String line : lines) {
                                    if (line.trim().isEmpty()) continue;
                                    String[] parts = line.split(":");
                                    if (parts.length >= 2) {
                                        deviceStates.put(parts[0], parts[1]);
                                    }
                                }
                            }
                            
                            // Atualizar/Escrever apenas na linha do dispositivo específico
                            deviceStates.put(command_Args[2], command_Args[3]);
                            
                            // Reconstruir o texto
                            StringBuilder newLogContent = new StringBuilder();
                            for (Map.Entry<String, String> entry : deviceStates.entrySet()) {
                                newLogContent.append(entry.getKey()).append(":").append(entry.getValue()).append(System.lineSeparator());
                            }
                            
                            // Cifrar o novo devicesLog com a Chave da Casa
                            Cipher cipherEncHome = Cipher.getInstance("AES");
                            cipherEncHome.init(Cipher.ENCRYPT_MODE, homeKey);
                            byte[] newEncryptedLog = cipherEncHome.doFinal(newLogContent.toString().getBytes());

                            // --- B) Processar o valor do dispositivo para a Secção ---
                            Cipher cipherEncSec = Cipher.getInstance("AES");
                            cipherEncSec.init(Cipher.ENCRYPT_MODE, sectionKey);
                            byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
                            byte[] encryptedValue = cipherEncSec.doFinal(valueBytes);

                            // 4. Enviar ambos de volta ao servidor
                            outStream.writeObject(encryptedValue); // Valor para o ficheiro base
                            outStream.writeObject(newEncryptedLog); // O ficheiro devicesLog cifrado global
                            outStream.flush();

                            // Imprime a confirmação (OK) do Servidor
                            System.out.println((String) inStream.readObject());
                        } else {
                            System.out.println("Erro ao obter as chaves.");
                        }
                    } else {
                        // Imprimir respostas de Erro (NOPERM, NOHM, NOKEY)
                        System.out.println(response);
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
                  case "OK" -> {
                    try {
                        // ✅ Use readObject() to match server's writeObject()
                        byte[] keyBytes = (byte[]) inStream.readObject();
                        byte[] logBytes = (byte[]) inStream.readObject();


                        File keyFile = new File(server_Response[3]);
                        try (FileOutputStream kos = new FileOutputStream(keyFile)) {
                            kos.write(keyBytes);
                        }

                        File logFile = new File("temp.enc");
                        try (FileOutputStream los = new FileOutputStream(logFile)) {
                            los.write(logBytes);
                        }

                        // ✅ Pass the actual size, not the drained counter
                        decipher(keyFile, logFile, "devicesLog_" + command_Args[1] + ".txt");

                        String[] user_devices = Arrays.copyOfRange(server_Response, 5, server_Response.length);
                        handle_file(logFile, user_devices);
                        keyFile.delete();
                        logFile.delete();
                        System.out.println("OK, " + server_Response[4] + " (long).");
                    } catch (IOException | ClassNotFoundException e) {
                        System.err.println(e.getMessage());
                        System.exit(-1);
                    }
                } case "NODATA" -> System.out.println("NODATA # No data to send.");
                  case "NOHM" -> System.out.println("NOHM # " + command_Args[1] + " doesn't exist.");
                  case "NOPERM" -> System.out.println("NOPERM # no permissions");
                  default -> throw new AssertionError();
                }
              }
              case "RH" -> {
                    if (command_Args.length != 3) {
                        System.out.println("Usage: RH <hm> <d>");
                    } else {
                        outStream.writeObject(command_Args);
                        outStream.flush();
                        
                        Object responseObj = inStream.readObject();
                        String response = (String) responseObj;

                        if (response.equals("OK")) {
                            try {
                                byte[] wrappedKey = (byte[]) inStream.readObject();
                                File tempKey = new File("temp_rh.key");
                                try (FileOutputStream fos = new FileOutputStream(tempKey)) {
                                    fos.write(wrappedKey);
                                }
                                Key sectionKey = getKey(tempKey);
                                tempKey.delete();

                                if (sectionKey != null) {
                                    byte[] encryptedFileContent = (byte[]) inStream.readObject();
                                    System.out.println("OK, recebidos " + encryptedFileContent.length + " bytes. A gerar CSV...");

                                    String fileName = command_Args[1] + "_" + command_Args[2] + ".csv";
                                    try (FileWriter fw = new FileWriter(fileName)) {
                                        Cipher c = Cipher.getInstance("AES");
                                        c.init(Cipher.DECRYPT_MODE, sectionKey);

                                        int index = 0;
                                        boolean firstBlockFound = false;
                                        int blockSize = 16;

                                        while (blockSize <= encryptedFileContent.length && !firstBlockFound) {
                                            try {
                                                byte[] chunk = Arrays.copyOfRange(encryptedFileContent, 0, blockSize);
                                                byte[] decrypted = c.doFinal(chunk);
                                                
                                                fw.write(new String(decrypted)); 
                                                index = blockSize;               // Atualiza o index para saltar o cabeçalho
                                                firstBlockFound = true;
                                            } catch (java.security.GeneralSecurityException e) {
                                                // Deu erro de padding? A fatia era pequena demais. Aumenta 16 bytes.
                                                blockSize += 16; 
                                            }
                                        }

                                        if (!firstBlockFound) {
                                            System.err.println("Erro: Não foi possível decifrar a linha inicial do ficheiro.");
                                            return; // Sai se o ficheiro estiver totalmente corrompido logo no início
                                        }
                                        while (index + 16 <= encryptedFileContent.length) {
                                            byte[] cipherChunk = Arrays.copyOfRange(encryptedFileContent, index, index + 16);
                                            byte[] decryptedValue = c.doFinal(cipherChunk);
                                            
                                            // Converte de volta para Inteiro
                                            int val = ByteBuffer.wrap(decryptedValue).getInt();
                                            fw.write(val + "\n");
                                            
                                            index += 16; 
                                        }
                                    }
                                    System.out.println("Histórico guardado com sucesso: " + fileName);
                                }
                            } catch (java.security.GeneralSecurityException e) {
                                System.err.println("Erro de segurança: Falha na decifragem. Verifique se os dados estão corrompidos.");
                            } catch (IOException e) {
                                System.err.println("Erro ao processar ficheiros: " + e.getMessage());
                            }
                        } else {
                            // Tratamento das mensagens de erro (NOHM, NOD, NOPERM, etc.)
                            switch (response) {
                                case "NOHM" -> System.out.println("NOHM # esta casa não existe");
                                case "NOD" -> System.out.println("NOD # dispositivo não existe");
                                case "NOPERM" -> System.out.println("NOPERM # sem permissões");
                                case "NODATA" -> System.out.println("NODATA # sem dados");
                                case "NOKEY" -> System.out.println("NOKEY # chave da secção inexistente para este utilizador");
                                default -> System.out.println(response);
                            }
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
  
  private void handle_file(File file, String[] string) {
    File finaFile = new File(file.getName());
    try(FileWriter fW = new FileWriter(finaFile);
        Scanner sc = new Scanner(file)) {
      while (sc.hasNextLine()) {
        String[] line = sc.next().split(":");
          if (Arrays.asList(string).contains(line[0])) {
            fW.write(String.join(":", line) + "\n");
          }
        }
    } catch (Exception e) {
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
        //outStream.writeLong(tempFile.length());
        //outStream.flush();
        byte[] encBytes = Files.readAllBytes(tempFile.toPath());
        outStream.writeObject(encBytes);
        outStream.flush();

        //try (FileInputStream encStream = new FileInputStream(tempFile)) {
            //int bytesToRead;
            //byte[] buf = new byte[1024];
            //while ((bytesToRead = encStream.read(buf, 0, buf.length)) != -1) {
                //outStream.write(buf, 0, bytesToRead);
                //outStream.flush();
            //}
        //}
        tempFile.delete();
        f.delete();
    } catch (IOException | KeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
      System.err.println(e.getMessage());
			System.exit(-1);
    }
  }

  private void decipher(File key, File log, String name) {
    try {
        Key aesKey = getKey(key);
        if (aesKey == null) throw new KeyException("Key not found!");

        // Read the encrypted bytes directly from the log file
        byte[] encryptedBytes = Files.readAllBytes(log.toPath());

        // ✅ Use doFinal() to match how EC encrypted it (not CipherInputStream)
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, aesKey);
        byte[] decryptedBytes = c.doFinal(encryptedBytes);

        // ✅ Write decrypted content back to the same log file so handle_file can read it
        try (FileOutputStream fos = new FileOutputStream(name)) {
            fos.write(decryptedBytes);
        }

    } catch (Exception e) {
        System.err.println(e.getMessage());
        System.exit(-1);
    }
}

  private Key getKey(File f) {
    Key aesKey = null;
    try {
      byte[] chaveAEScifrada = Files.readAllBytes(f.toPath());
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
    try {
        boolean userOk = false;
        while (!userOk) {
            String serverMsg = (String) in.readObject();
            switch (serverMsg) {
                case "NO_CERT" -> {
                }
                case "WRONG_PWD" -> {
                    System.out.print("Inserir novamente palavra-passe: ");
                    pwd = sc.nextLine();
                    out.writeObject(pwd);
                    out.flush();
                }
                case "SEND_CERT" -> {
                    try {
                        KeyStore ks = KeyStore.getInstance("JCEKS");
                        ks.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
                        Certificate cert = ks.getCertificate("keyrsa");
                        out.writeObject(cert != null ? cert.getEncoded() : new byte[0]);
                        out.flush();
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                        System.exit(-1);
                    }
                }
                case "OK_USER", "OK_NEW_USER" -> userOk = true;
            }
        }
    } catch (IOException | ClassNotFoundException e) {
        System.err.println(e.getMessage());
    }
  }
}