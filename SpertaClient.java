
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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
    private static final String COMMAND_LIST
            = """
  Available Commands:
  CREATE <hm>
  ADD <user> <hm> <a>
  RD <hm> <s>
  EC <hm> <d> <int>
  RT <hm>
  RH <hm> <d>
  """;

    private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};
    private static final String[] SECTIONS = {"E", "G", "L", "M", "P", "S"};
    private SecretKey macKey;

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
            case 2 ->
                client.port = Integer.parseInt(serverAddress[1]);
            case 1 ->
                client.port = 22345;
            default -> {
                System.out.println("Usage: server address should be in the format <host> or <host:port>");
                System.exit(-1);
            }
        }

        client.startClient();
    }

    public void startClient() {
        System.setProperty("javax.net.ssl.trustStore", "Certs/truststore.client");
        System.setProperty("javax.net.ssl.trustStorePassword", "Truststore");
        SocketFactory sf = SSLSocketFactory.getDefault();
        try (SSLSocket cliSoc = (SSLSocket) sf.createSocket(host, port); ObjectOutputStream outStream = new ObjectOutputStream(cliSoc.getOutputStream()); ObjectInputStream inStream = new ObjectInputStream(cliSoc.getInputStream()); Scanner user_input = new Scanner(System.in)) {

            try {
                byte[] nounce_rec = (byte[]) inStream.readObject();
                byte[] jarBytes = Files.readAllBytes(Paths.get("SpertaClient.jar"));
                byte[] combined = new byte[nounce_rec.length + jarBytes.length];
                System.arraycopy(nounce_rec, 0, combined, 0, nounce_rec.length);
                System.arraycopy(jarBytes, 0, combined, nounce_rec.length, jarBytes.length);

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = md.digest(combined);

                outStream.writeObject(hashBytes);
                outStream.flush();
            } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
                System.err.println("SHA-256 algorithm not found: " + e.getMessage());
                System.exit(-1);
            }

            String integrity_check = (String) inStream.readObject();
            if (integrity_check.equals("OK-ATTEST")) {
                outStream.writeObject(keystore);
                outStream.writeObject(pass_keystore);
                outStream.writeObject(user);
                outStream.writeObject(pwd);
                outStream.flush();

                checkSResp(inStream, outStream, user_input);

                while (true) {
                    System.out.print(COMMAND_LIST + "\n" + "Insert Command: ");

                    String user_Command = "";
                    if (user_input.hasNextLine()) {
                        user_Command = user_input.nextLine();
                    }

                    if (user_Command.isEmpty() && user_input.hasNextLine()) {
                        user_Command = user_input.nextLine();
                    }

                    String[] command_Args = user_Command.split(" ");

                    switch (command_Args[0]) {
                        case "CREATE" -> {
                            if (command_Args.length != 2) {
                                System.out.println("Usage: CREATE <home_name>");
                            } else {
                                outStream.writeObject(command_Args);
                                outStream.flush();
                                String server_Response = (String) inStream.readObject();
                                if ("HOME_CREATED".equals(server_Response)) {
                                    try {
                                        KeyStore ks = KeyStore.getInstance("JCEKS");
                                        ks.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
                                        PublicKey pk = ks.getCertificate("keyrsa").getPublicKey();
                                        Cipher cRSA = Cipher.getInstance("RSA");
                                        cRSA.init(Cipher.WRAP_MODE, pk);

                                        KeyGenerator kg = KeyGenerator.getInstance("AES");
                                        kg.init(128);

                                        SecretKey homeKey = kg.generateKey();
                                        outStream.writeObject(cRSA.wrap(homeKey));

                                        // Generate and send one section key per section
                                        String[] sections = {"E", "G", "L", "M", "P", "S"};
                                        for (String section : sections) {
                                            SecretKey sectionKey = kg.generateKey();
                                            outStream.writeObject(cRSA.wrap(sectionKey));
                                        }
                                        outStream.flush();
                                        System.out.println("OK");
                                    } catch (IOException | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException | IllegalBlockSizeException | NoSuchPaddingException e) {
                                        System.err.println("Error generating keys in CREATE: " + e.getMessage());
                                        System.exit(-1);
                                    }
                                } else {
                                    System.out.println("NOK");
                                }
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

                            String userToAdd = command_Args[1];

                            PublicKey userPublicKey = null;
                            File certFile = new File("Certs/" + userToAdd + ".cer");

                            if (certFile.exists()) {
                                try {
                                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                    FileInputStream certInput = new FileInputStream(certFile);
                                    Certificate cert = cf.generateCertificate(certInput);
                                    userPublicKey = cert.getPublicKey();
                                } catch (FileNotFoundException | CertificateException e) {
                                    System.err.println("Error loading local certificate: " + e.getMessage());
                                    break;
                                }
                            } else {
                                outStream.writeObject(new String[]{"GET_CERT", userToAdd});
                                outStream.flush();

                                String certResponse = (String) inStream.readObject();
                                if (certResponse.equals("NO_CERT")) {
                                    System.out.println("NOCERT");
                                    break;
                                }

                                try {
                                    byte[] certBytes = (byte[]) inStream.readObject();
                                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                    Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));
                                    userPublicKey = cert.getPublicKey();
                                    Files.createDirectories(Paths.get("Certs"));
                                    Files.write(certFile.toPath(), certBytes);
                                } catch (IOException | ClassNotFoundException | CertificateException e) {
                                    System.err.println("Error handling certificate: " + e.getMessage());
                                    break;
                                }
                            }

                            outStream.writeObject(command_Args);
                            outStream.flush();

                            String keyResponse = (String) inStream.readObject();
                            switch (keyResponse) {
                                case "USER_NOT_FOUND" ->
                                    System.out.println("NOUSER");
                                case "HOME_NOT_FOUND" ->
                                    System.out.println("NOHM");
                                case "NO_USER_PERMS" ->
                                    System.out.println("NOPERM");
                                case "USER_ADDING_SELF" ->
                                    System.out.println("NOK # cannot add yourself");
                                case "INVALID_SECTION" ->
                                    System.out.println("NOK # invalid section");
                                case "SECTION_KEY" -> {
                                    try {
                                        KeyStore kstore = KeyStore.getInstance("JCEKS");
                                        kstore.load(new FileInputStream("Keys/" + keystore), pass_keystore.toCharArray());
                                        Key myPrivateKey = kstore.getKey("keyrsa", pass_keystore.toCharArray());
                                        Cipher cipher = Cipher.getInstance("RSA");

                                        // Handle first section (already received "SECTION_KEY" signal)
                                        // Then loop for remaining sections
                                        String[] sectionsToProcess = command_Args[3].equals("all") ? SECTIONS : new String[]{command_Args[3]};
                                        
                                        for (int i = 0; i < sectionsToProcess.length; i++) {
                                            // First iteration already consumed "SECTION_KEY", read it for subsequent ones
                                            if (i > 0) {
                                                Object signal = inStream.readObject();
                                            }

                                            byte[] encryptedSectionKey = (byte[]) inStream.readObject();
                                            byte[] encryptedHomeKey = (byte[]) inStream.readObject();

                                            // Unwrap section key with own private key
                                            cipher.init(Cipher.UNWRAP_MODE, myPrivateKey);
                                            Key sectionKey = cipher.unwrap(encryptedSectionKey, "AES", Cipher.SECRET_KEY);

                                            // Re-wrap section key with userToAdd's public key
                                            cipher.init(Cipher.WRAP_MODE, userPublicKey);
                                            byte[] reEncryptedSectionKey = cipher.wrap(sectionKey);
                                            outStream.writeObject(reEncryptedSectionKey);

                                            // Unwrap home key with own private key
                                            cipher.init(Cipher.UNWRAP_MODE, myPrivateKey);
                                            Key homeKey = cipher.unwrap(encryptedHomeKey, "AES", Cipher.SECRET_KEY);

                                            // Re-wrap home key with userToAdd's public key
                                            cipher.init(Cipher.WRAP_MODE, userPublicKey);
                                            byte[] reEncryptedHomeKey = cipher.wrap(homeKey);
                                            outStream.writeObject(reEncryptedHomeKey);

                                            outStream.flush();
                                        }

                                        // Read final USER_ADDED after all sections processed
                                        String server_Response = (String) inStream.readObject();
                                        if (server_Response.equals("USER_ADDED")) {
                                            System.out.println("OK");
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error during key handling: " + e.getMessage());
                                    }
                                }
                                default ->
                                    System.out.println("NOK");
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
                            String[] server_Response = (String[]) inStream.readObject();
                            switch (server_Response[0]) {
                                case "OK" -> {
                                    File f = new File("key." + command_Args[1] + "." + command_Args[2] + "." + user);
                                    byte[] keyBytes = (byte[]) inStream.readObject();
                                    try (FileOutputStream key = new FileOutputStream(f)) {
                                        key.write(keyBytes);
                                    }
                                    Key encKey = getKey(f);
                                    macKey = deriveMacKey(encKey);

                                    File log;
                                    if (!"0".equals(server_Response[1])) {
                                        log = new File(server_Response[2]);

                                        byte[] fileBytes = (byte[]) inStream.readObject();

                                        byte[] receivedMac = (byte[]) inStream.readObject();

                                        try (FileOutputStream device = new FileOutputStream(log)) {
                                            device.write(fileBytes);
                                        }

                                        if (!verifyMac(macKey, fileBytes, receivedMac)) {
                                            System.err.println("INTEGRITY VIOLATION: file has been tampered with!");
                                            System.exit(-1);
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
                                case "NOPERM" ->
                                    System.out.println("NOPERM # no permissions");
                                case "NOHM" ->
                                    System.out.println("NOHM # no such house");
                                default ->
                                    throw new AssertionError();
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
                                    byte[] sectionKeyBytes = (byte[]) inStream.readObject();
                                    File tempSecKey = new File("temp_ec_sec.key");
                                    try (FileOutputStream fos = new FileOutputStream(tempSecKey)) {
                                        fos.write(sectionKeyBytes);
                                    }
                                    Key sectionKey = getKey(tempSecKey);
                                    tempSecKey.delete();
                                    byte[] homeKeyBytes = (byte[]) inStream.readObject();
                                    File tempHomeKey = new File("temp_ec_home.key");
                                    try (FileOutputStream fos = new FileOutputStream(tempHomeKey)) {
                                        fos.write(homeKeyBytes);
                                    }
                                    Key homeKey = getKey(tempHomeKey);
                                    tempHomeKey.delete();

                                    byte[] encryptedLog = (byte[]) inStream.readObject();
                                    byte[] receivedLogMac = (byte[]) inStream.readObject();

                                    if (sectionKey != null && homeKey != null) {
                                        SecretKey sectionMacKey = deriveMacKey(sectionKey);
                                        SecretKey homeMacKey = deriveMacKey(homeKey);

                                        Map<String, String> deviceStates = new LinkedHashMap<>();

                                        if (encryptedLog.length > 0) {
                                            if (receivedLogMac.length > 0 && !verifyMac(homeMacKey, encryptedLog, receivedLogMac)) {
                                                System.err.println("INTEGRITY VIOLATION: devicesLog has been tampered with!");
                                                System.exit(-1);
                                            }

                                            Cipher cipherDec = Cipher.getInstance("AES");
                                            cipherDec.init(Cipher.DECRYPT_MODE, homeKey);
                                            byte[] decryptedLog = cipherDec.doFinal(encryptedLog);
                                            String logContent = new String(decryptedLog);

                                            String[] lines = logContent.split(System.lineSeparator());
                                            for (String line : lines) {
                                                if (line.trim().isEmpty()) {
                                                    continue;
                                                }
                                                String[] parts = line.split(":");
                                                if (parts.length >= 2) {
                                                    deviceStates.put(parts[0], parts[1]);
                                                }
                                            }
                                        }

                                        deviceStates.put(command_Args[2], command_Args[3]);

                                        StringBuilder newLogContent = new StringBuilder();
                                        for (Map.Entry<String, String> entry : deviceStates.entrySet()) {
                                            newLogContent.append(entry.getKey()).append(":").append(entry.getValue()).append(System.lineSeparator());
                                        }

                                        Cipher cipherEncHome = Cipher.getInstance("AES");
                                        cipherEncHome.init(Cipher.ENCRYPT_MODE, homeKey);
                                        byte[] newEncryptedLog = cipherEncHome.doFinal(newLogContent.toString().getBytes());
                                        byte[] newLogMac = generateMac(homeMacKey, newEncryptedLog);

                                        Cipher cipherEncSec = Cipher.getInstance("AES");
                                        cipherEncSec.init(Cipher.ENCRYPT_MODE, sectionKey);
                                        byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
                                        byte[] encryptedValue = cipherEncSec.doFinal(valueBytes);
                                        byte[] sectionMac = generateMac(sectionMacKey, encryptedValue);

                                        outStream.writeObject(encryptedValue);
                                        outStream.writeObject(sectionMac);
                                        outStream.writeObject(newEncryptedLog);
                                        outStream.writeObject(newLogMac);
                                        outStream.flush();

                                        System.out.println((String) inStream.readObject());
                                    } else {
                                        System.out.println("Erro ao obter as chaves.");
                                    }
                                } else {
                                    System.out.println(response);
                                }
                            } catch (IOException | ClassNotFoundException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
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
                            String[] server_Response = (String[]) inStream.readObject();
                            switch (server_Response[0]) {
                                case "OK" -> {
                                    try {
                                        byte[] keyBytes = (byte[]) inStream.readObject();
                                        byte[] logBytes = (byte[]) inStream.readObject();
                                        byte[] receivedLogMac = (byte[]) inStream.readObject();

                                        File keyFile = new File(server_Response[3]);
                                        try (FileOutputStream kos = new FileOutputStream(keyFile)) {
                                            kos.write(keyBytes);
                                        }

                                        Key homeKey = getKey(keyFile);
                                        SecretKey homeMacKey = deriveMacKey(homeKey);
                                        if (!verifyMac(homeMacKey, logBytes, receivedLogMac)) {
                                            System.err.println("INTEGRITY VIOLATION: devicesLog has been tampered with!");
                                            System.exit(-1);
                                        }

                                        File logFile = new File("temp.enc");
                                        try (FileOutputStream los = new FileOutputStream(logFile)) {
                                            los.write(logBytes);
                                        }

                                        decipher(keyFile, logFile, "devicesLog_" + command_Args[1] + ".txt");

                                        File decryptedLog = new File("devicesLog_" + command_Args[1] + ".txt");
                                        String[] user_devices = Arrays.copyOfRange(server_Response, 5, server_Response.length);
                                        handle_file(decryptedLog, user_devices);
                                        keyFile.delete();
                                        logFile.delete();
                                        decryptedLog.delete();
                                        System.out.println("OK, " + server_Response[4] + " (long).");
                                    } catch (IOException | ClassNotFoundException e) {
                                        System.err.println(e.getMessage());
                                        System.exit(-1);
                                    }
                                }
                                case "NODATA" ->
                                    System.out.println("NODATA # No data to send.");
                                case "NOHM" ->
                                    System.out.println("NOHM # " + command_Args[1] + " doesn't exist.");
                                case "NOPERM" ->
                                    System.out.println("NOPERM # no permissions");
                                default ->
                                    throw new AssertionError();
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

                                            byte[] receivedMac = (byte[]) inStream.readObject();
                                            SecretKey sectionMacKey = deriveMacKey(sectionKey);
                                            if (!verifyMac(sectionMacKey, encryptedFileContent, receivedMac)) {
                                                System.err.println("INTEGRITY VIOLATION: historical file has been tampered with!");
                                                System.exit(-1);
                                            }

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
                                                    return;
                                                }
                                                while (index + 16 <= encryptedFileContent.length) {
                                                    byte[] cipherChunk = Arrays.copyOfRange(encryptedFileContent, index, index + 16);
                                                    byte[] decryptedValue = c.doFinal(cipherChunk);

                                                    int val = ByteBuffer.wrap(decryptedValue).getInt();
                                                    fw.write(val + "\n");

                                                    index += 16;
                                                }
                                            }
                                            System.out.println("Historico guardado com sucesso: " + fileName);
                                        }
                                    } catch (java.security.GeneralSecurityException e) {
                                        System.err.println("Erro de segurança: Falha na decifragem. Verifique se os dados estão corrompidos.");
                                    } catch (IOException e) {
                                        System.err.println("Erro ao processar ficheiros: " + e.getMessage());
                                    }
                                } else {
                                    switch (response) {
                                        case "NOHM" ->
                                            System.out.println("NOHM # esta casa não existe");
                                        case "NOD" ->
                                            System.out.println("NOD # dispositivo não existe");
                                        case "NOPERM" ->
                                            System.out.println("NOPERM # sem permissoes");
                                        case "NODATA" ->
                                            System.out.println("NODATA # sem dados");
                                        case "NOKEY" ->
                                            System.out.println("NOKEY # chave da secção inexistente para este utilizador");
                                        default ->
                                            System.out.println(response);
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
            } else {
                System.out.println("INTEGRITY VIOLATION. EXITING...");
                System.exit(-1);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private void handle_file(File file, String[] string) {
        File finaFile = new File("output_" + file.getName());
        try (FileWriter fW = new FileWriter(finaFile); Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String raw = sc.nextLine().trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String[] line = raw.split(":");
                boolean allowed = false;
                for (String section : string) {
                    if (line[0].startsWith(section)) {
                        allowed = true;
                        break;
                    }
                }
                if (allowed) {
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
            if (aesKey == null) {
                throw new KeyException("Key not found!");
            }

            String nameWithoutExt = decrypted_file.substring(0, decrypted_file.lastIndexOf('.'));
            int number = Character.getNumericValue(nameWithoutExt.charAt(1));
            number++;

            String filename = nameWithoutExt.substring(0, 1) + number + ".txt";

            try (FileWriter fW = new FileWriter(decrypted_file, true)) {
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
            try (FileInputStream logFile = new FileInputStream(d); FileOutputStream tempOut = new FileOutputStream(tempFile); CipherOutputStream cout = new CipherOutputStream(tempOut, c)) {
                int bytesToRead;
                byte[] buf = new byte[1024];
                while ((bytesToRead = logFile.read(buf, 0, buf.length)) != -1) {
                    cout.write(buf, 0, bytesToRead);
                }
            }

            outStream.writeObject(filename);
            byte[] encBytes = Files.readAllBytes(tempFile.toPath());
            outStream.writeObject(encBytes);
            byte[] newMac = generateMac(macKey, encBytes);
            outStream.writeObject(newMac);
            outStream.flush();
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
            if (aesKey == null) {
                throw new KeyException("Key not found!");
            }

            byte[] encryptedBytes = Files.readAllBytes(log.toPath());

            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, aesKey);
            byte[] decryptedBytes = c.doFinal(encryptedBytes);
            try (FileOutputStream fos = new FileOutputStream(name)) {
                fos.write(decryptedBytes);
            }

        } catch (IOException | KeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private SecretKey deriveMacKey(Key encKey) {
        byte[] encKeyBytes = encKey.getEncoded();
        return new SecretKeySpec(encKeyBytes, "HmacSHA256");
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
                        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                            System.err.println(e.getMessage());
                            System.exit(-1);
                        }
                    }
                    case "OK_USER", "OK_NEW_USER" ->
                        userOk = true;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }

    private byte[] generateMac(SecretKey key, byte[] b) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(b);
            return mac.doFinal();
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return null;
    }

    private boolean verifyMac(SecretKey macKey, byte[] file, byte[] storedMac) {
        byte[] generatedMac = generateMac(macKey, file);
        return Arrays.equals(generatedMac, storedMac);
    }
}
