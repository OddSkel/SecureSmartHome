
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SpertaServer {

    private static final int MAX_CLIENTS = 3;
    private static final Semaphore signal = new Semaphore(MAX_CLIENTS);
    private static final Semaphore command_signal = new Semaphore(1);
    private String serverPwdCifra;
    private static final String SALT_FILE = "server.salt";
    private static final int AES_KEY_SIZE = 128;
    private static final int ITERATIONS = 310_000;
    private Key[] serverKeys;
    private Key serverKey;
    private SecretKey macKey;

    public static void main(String[] args) {
        System.out.println("[SERVER] Starting server...");
        SpertaServer server = new SpertaServer();

        // Agora aceita os 4 argumentos: porta, pwd-cifra, keystore, pwd-keystore
        if (args.length == 4) {
            server.startServer(Integer.parseInt(args[0]), args[1], args[2], args[3]);
        } else if (args.length == 0) {
            // Caso não passes nada, usa valores por omissão (ajusta se necessário)
            server.startServer(22345, "default_pwd", "Keys/keystore.server", "123456");
        } else {
            System.out.println("Usage: java SpertaServer <port> <password-cifra> <keystore> <password-keystore>");
            System.exit(-1);
        }
    }

    public void startServer(int port, String pwdCifra, String keystorePath, String keystorePwd) {
        try {
            serverKeys = generateOrLoadKeys(pwdCifra);
            serverKey = serverKeys[0];
            macKey = (SecretKey) serverKeys[1];

            File salt = new File(SALT_FILE);
            File saltMacFile = new File(SALT_FILE + ".mac");
            byte[] saltBytes = Files.readAllBytes(salt.toPath());

            if (saltMacFile.exists()) {
                byte[] storedMac = Files.readAllBytes(saltMacFile.toPath());
                if (!verifyMac(macKey, saltBytes, storedMac)) {
                    System.err.println("[SERVER] INTEGRITY VIOLATION: salt has been tampered with!");
                    System.exit(-1);
                }
            } else {
                byte[] saltMac = generateMac(macKey, saltBytes);
                Files.write(saltMacFile.toPath(), saltMac);
            }

            System.out.println("[SERVER] Encryption key loaded successfully.");
        } catch (Exception e) {
            System.err.println("[SERVER] Failed to generate encryption key: " + e.getMessage());
            System.exit(-1);
        }
        try (ServerSocket sSoc = new ServerSocket(port)) {
            System.out.println("[SERVER] Server started on port " + port);
            while (true) {
                try {
                    Socket inSoc = sSoc.accept();
                    SecureRandom secure_random = new SecureRandom();
                    ObjectOutputStream check = new ObjectOutputStream(inSoc.getOutputStream());
                    ObjectInputStream rec = new ObjectInputStream(inSoc.getInputStream());

                    byte[] nounce = new byte[8];
                    secure_random.nextBytes(nounce);
                    check.writeObject(nounce);
                    check.flush();

                    byte[] receive = (byte[]) rec.readObject();
                    if (!Arrays.equals(nounce, receive)) {
                        check.writeObject("NOK-ATTEST");
                        check.flush();
                        inSoc.close();
                        continue;
                    }
                    check.writeObject("OK-ATTEST");
                    check.flush();

                    signal.acquire();
                    ServerThread newServerThread = new ServerThread(inSoc, signal, command_signal, check,
                            rec, pwdCifra, keystorePwd, serverKey, macKey);
                    newServerThread.start();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    System.exit(-1);
                } catch (InterruptedException | ClassNotFoundException e1) {
                    Thread.currentThread().interrupt();
                    System.err.println(e1.getMessage());
                    System.exit(-1);
                }

            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private static Key[] generateOrLoadKeys(String password) throws Exception {
        byte[] salt = loadOrCreateSalt();

        // Encryption key
        PBEKeySpec encSpec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBEWithHmacSHA256AndAES_128");
        SecretKey pbeKey = factory.generateSecret(encSpec);
        encSpec.clearPassword();
        byte[] encKeyBytes = Arrays.copyOf(pbeKey.getEncoded(), 16);
        Key encKey = new SecretKeySpec(encKeyBytes, "AES");

        // MAC key — same password, same salt, but hashed differently
        PBEKeySpec macSpec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS);
        SecretKey pbeMacKey = factory.generateSecret(macSpec);
        macSpec.clearPassword();
        byte[] macKeyBytes = Arrays.copyOfRange(pbeMacKey.getEncoded(), 0, 32); // 256 bits for HmacSHA256
        SecretKey macKey = new SecretKeySpec(macKeyBytes, "HmacSHA256");

        return new Key[]{encKey, macKey};
    }

    private static byte[] loadOrCreateSalt() throws Exception {
        File saltFile = new File(SALT_FILE);

        if (saltFile.exists()) {
            // Load existing salt so the key stays the same
            return Files.readAllBytes(saltFile.toPath());
        } else {
            // First run: generate and save a new salt
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            Files.write(saltFile.toPath(), salt);
            return salt;
        }
    }

    private byte[] generateMac(SecretKey key, byte[] b) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(b);
            return mac.doFinal();
        } catch (Exception e) {
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

class ServerThread extends Thread {

    private Socket socket = null;
    private Semaphore signal = null;
    private Semaphore command = null;

    Key serverKey;
    SecretKey macKey;

    private File users, homes, homesFolder;
    private String user, pwd, trustore, pass_truststore, keystore, pass_keystore, keyStorePwd, serverPwdCifra;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private byte[] usersMac, homesMac, devicesLogMac;

    private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};

    ServerThread(Socket inSoc, Semaphore signal, Semaphore command_signal, ObjectOutputStream out, ObjectInputStream in,
            String pwdCifra, String keyStorePwd, Key serverKey, Key macKey) {
        socket = inSoc;
        this.signal = signal;
        command = command_signal;
        this.out = out;
        this.in = in;
        serverPwdCifra = pwdCifra;
        this.keyStorePwd = keyStorePwd;
        this.serverKey = serverKey;
        this.macKey = (SecretKey) macKey;
        System.out.println("thread do server para cada cliente");
    }

    @Override
    public void run() {
        try {

            users = new File("usersLog.txt");
            if (!users.exists()) {
                users.createNewFile();
            }

            homes = new File("homesLog.txt");
            if (!homes.exists()) {
                homes.createNewFile();
            }

            homesFolder = new File("homes");
            if (!homesFolder.exists()) {
                homesFolder.mkdir();
            }

            usersMac = generateMac(macKey, Files.readAllBytes(users.toPath()));
            homesMac = generateMac(macKey, Files.readAllBytes(homes.toPath()));

            try {
                //Maybe delete some things that client sends to user
                trustore = (String) in.readObject();
                pass_truststore = (String) in.readObject();
                keystore = (String) in.readObject();
                pass_keystore = (String) in.readObject();
                user = (String) in.readObject();
                pwd = (String) in.readObject();
                String[] client_args = {trustore, pass_truststore, keystore, pass_keystore, user, pwd};
                System.out.println("[" + user + " Thread] Authentication request received for user: " + user);
                authenticate(client_args, serverKey);
                while (true) {
                    try {
                        if (!verifyMac(macKey, Files.readAllBytes(users.toPath()), usersMac) || !verifyMac(macKey, Files.readAllBytes(homes.toPath()), homesMac)) {
                            throw new Exception("INTEGRITY VIOLATION");
                        }
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                        System.exit(-1);
                    }

                    String[] client_Commands = (String[]) in.readObject();
                    command.acquire();
                    try {
                        switch (client_Commands[0]) {
                            case "CREATE" -> {
                                String houseName = client_Commands[1];
                                System.out.println("[" + user + " Thread] CREATE command received for home: " + houseName);
                                createHome(houseName);
                                try {
                                    // 1. Carregar Keystore e Certificado UMA ÚNICA VEZ
                                    File kS = new File("Keys", keystore);
                                    FileInputStream kfile = new FileInputStream(kS);
                                    KeyStore kstore = KeyStore.getInstance("JCEKS");
                                    kstore.load(kfile, pass_keystore.toCharArray());
                                    Certificate cert = kstore.getCertificate("keyrsa");
                                    PublicKey pk = cert.getPublicKey();
                                    Cipher cRSA = Cipher.getInstance("RSA");
                                    cRSA.init(Cipher.WRAP_MODE, pk);

                                    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBEWithHmacSHA256AndAES_128");

                                    // 2. Criar e Guardar a Chave da Casa (Geral)
                                    KeySpec specHome = new PBEKeySpec(pwd.toCharArray(), generateSalt(), 20);
                                    SecretKey tmpHome = factory.generateSecret(specHome);
                                    SecretKey homeKey = new SecretKeySpec(Arrays.copyOf(tmpHome.getEncoded(), 16), "AES");

                                    File homeKeyFile = new File("homes/" + houseName, "key." + houseName + "." + user);
                                    try (FileOutputStream keyHomeOut = new FileOutputStream(homeKeyFile)) {
                                        keyHomeOut.write(cRSA.wrap(homeKey));
                                    }

                                    // 3. Criar e Guardar as Chaves das Secções (Loop)
                                    for (String section : Arrays.asList(Arrays.copyOfRange(PERMS, 1, PERMS.length))) {
                                        KeySpec specSec = new PBEKeySpec(pwd.toCharArray(), generateSalt(), 20);
                                        SecretKey tmpSec = factory.generateSecret(specSec);
                                        SecretKey secKey = new SecretKeySpec(Arrays.copyOf(tmpSec.getEncoded(), 16), "AES");

                                        File secKeyFile = new File("homes/" + houseName + "/" + section, "key." + houseName + "." + section + "." + user);
                                        try (FileOutputStream keySecOut = new FileOutputStream(secKeyFile)) {
                                            keySecOut.write(cRSA.wrap(secKey));
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Erro ao gerar chaves no CREATE: " + e.getMessage());
                                    System.exit(-1);
                                }
                            }
                            case "ADD" -> {
                                String userToAdd = client_Commands[1];
                                String homeName = client_Commands[2];
                                String section = client_Commands[3];
                                System.out.println("[" + user + " Thread] ADD command received to add user: " + userToAdd + " to home: " + homeName + " with section: " + section);
                                if (userExists(userToAdd)) {
                                    if (homeExists(homeName)) {
                                        if (checkOwner(homeName, user)) {
                                            if (checkOwner(homeName, userToAdd)) {
                                                out.writeObject("USER_ADDING_SELF");
                                                out.flush();
                                                System.out.println("[" + user + " Thread] ADD command failed. User cannot add itself to home: " + homeName);
                                            } else {
                                                if (!isValidSection(section)) {
                                                    out.writeObject("INVALID_SECTION");
                                                    out.flush();
                                                    System.out.println("[" + user + " Thread] ADD command failed. Invalid section: " + section);
                                                } else {
                                                    addUserToHome(userToAdd, homeName, section);
                                                }
                                            }
                                        } else {
                                            out.writeObject("NO_USER_PERMS");
                                            out.flush();
                                            System.out.println("[" + user + " Thread] ADD command failed. User does not have permissions to add users to home: " + homeName);
                                        }
                                    } else {
                                        out.writeObject("HOME_NOT_FOUND");
                                        out.flush();
                                        System.out.println("[" + user + " Thread] ADD command failed. Home not found: " + homeName);
                                    }
                                } else {
                                    out.writeObject("USER_NOT_FOUND");
                                    out.flush();
                                    System.out.println("[" + user + " Thread] ADD command failed. User not found: " + userToAdd);
                                }
                            }
                            case "RD" -> {
                                int result = verify(client_Commands, user);
                                switch (result) {
                                    case 0 ->
                                        out.writeObject(new String[]{"NOPERM"});
                                    case 1 -> {
                                        File dir = new File("homes/" + client_Commands[1] + "/" + client_Commands[2]);
                                        File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith(client_Commands[2]) && name.endsWith(".txt"));

                                        File keyFile = new File(dir, "key." + client_Commands[1] + "." + client_Commands[2] + "." + user);
                                        byte[] keyBytes = Files.readAllBytes(keyFile.toPath());

                                        Key sectionKey = getKey(keyFile);
                                        SecretKey rdMacKey = deriveMacKey(sectionKey);
                                        if (matchingFiles.length == 1) {
                                            out.writeObject(new String[]{"OK", "1", matchingFiles[0].getName()});
                                            out.writeObject(keyBytes);

                                            // 2. Enviar o ficheiro do dispositivo (como Objeto)
                                            byte[] fileBytes = Files.readAllBytes(matchingFiles[0].toPath());
                                            out.writeObject(fileBytes);
                                            byte[] fileMac = generateMac(rdMacKey, fileBytes);
                                            out.writeObject(fileMac);
                                            matchingFiles[0].delete();
                                            new File(matchingFiles[0].getPath() + ".mac").delete();
                                        } else {
                                            out.writeObject(new String[]{"OK", "0"});
                                            out.writeObject(keyBytes);
                                        }

                                        String name = (String) in.readObject();
                                        byte[] newFileBytes = (byte[]) in.readObject();

                                        byte[] newMac = (byte[]) in.readObject();

                                        if (!verifyMac(rdMacKey, newFileBytes, newMac)) {
                                            System.err.println("INTEGRITY VIOLATION: received file has been tampered with!");
                                            System.exit(-1);
                                        }

                                        File deviceFile = new File(dir, name);
                                        Files.write(deviceFile.toPath(), newFileBytes);
                                        Files.write(Path.of(deviceFile.getPath() + ".mac"), newMac);
                                    }
                                    case -1 ->
                                        out.writeObject(new String[]{"NOHM"});
                                    default ->
                                        throw new AssertionError();
                                }
                                out.flush();
                            }
                            case "EC" -> {
                                String hm = client_Commands[1];
                                String dev = client_Commands[2];
                                String section = dev.substring(0, 1).toUpperCase();

                                File decFile = new File("ho_dec.txt");
                                if (homes.length() != 0) {
                                    decipher(serverKey, homes, "ho_dec.txt");
                                } else {
                                    decFile.createNewFile();
                                }

                                if (!homeExists(hm, decFile)) {
                                    out.writeObject("NOHM");
                                } else if (!checkOwner(hm, user, decFile) && !verifyUserPermission(hm, user, section, decFile)) {
                                    out.writeObject("NOPERM");
                                } else {
                                    File sectionKeyFile = new File("homes/" + hm + "/" + section, "key." + hm + "." + section + "." + user);
                                    File homeKeyFile = new File("homes/" + hm, "key." + hm + "." + user);

                                    if (!sectionKeyFile.exists() || !homeKeyFile.exists()) {
                                        out.writeObject("NOKEY");
                                    } else {
                                        File devFile = new File("homes/" + hm + "/" + section + "/" + dev + ".txt");
                                        if (!devFile.exists()) {
                                            out.writeObject("NOD");
                                            decFile.delete();
                                            out.flush();
                                            break;
                                        }
                                        out.writeObject("OK_EC");

                                        // Derive MAC keys from section and home keys
                                        Key sectionKey = getKey(sectionKeyFile);
                                        Key homeKey = getKey(homeKeyFile);
                                        SecretKey sectionMacKey = deriveMacKey(sectionKey);
                                        SecretKey homeMacKey = deriveMacKey(homeKey);

                                        // 1. Envia a Chave da Secção
                                        byte[] wrappedSectionKey = Files.readAllBytes(sectionKeyFile.toPath());
                                        out.writeObject(wrappedSectionKey);

                                        // 2. Envia a Chave da Casa
                                        byte[] wrappedHomeKey = Files.readAllBytes(homeKeyFile.toPath());
                                        out.writeObject(wrappedHomeKey);

                                        // 3. Envia o devicesLog cifrado e o seu MAC
                                        File globalLog = new File("homes/" + hm + "/devicesLog.txt");
                                        File globalLogMac = new File("homes/" + hm + "/devicesLog.txt.mac");
                                        if (globalLog.exists() && globalLog.length() > 0) {
                                            byte[] encryptedLog = Files.readAllBytes(globalLog.toPath());
                                            out.writeObject(encryptedLog);
                                            // Send MAC of devicesLog if it exists
                                            if (globalLogMac.exists()) {
                                                out.writeObject(Files.readAllBytes(globalLogMac.toPath()));
                                            } else {
                                                out.writeObject(new byte[0]);
                                            }
                                        } else {
                                            out.writeObject(new byte[0]); // empty log
                                            out.writeObject(new byte[0]); // empty MAC
                                        }
                                        out.flush();

                                        // 4. Recebe do cliente o valor cifrado para a secção individual
                                        byte[] encryptedDataFromClient = (byte[]) in.readObject();
                                        byte[] sectionMac = (byte[]) in.readObject();

                                        // Verify section file MAC
                                        if (!verifyMac(sectionMacKey, encryptedDataFromClient, sectionMac)) {
                                            System.err.println("INTEGRITY VIOLATION: section file tampered!");
                                            System.exit(-1);
                                        }
                                        try (FileOutputStream fos = new FileOutputStream(devFile, true)) {
                                            fos.write(encryptedDataFromClient);
                                        }
                                        // Save section MAC
                                        Files.write(new File(devFile.getPath() + ".mac").toPath(), sectionMac);

                                        // 5. Recebe do cliente o devicesLog cifrado já atualizado
                                        byte[] updatedEncryptedLog = (byte[]) in.readObject();
                                        byte[] updatedLogMac = (byte[]) in.readObject();

                                        // Verify devicesLog MAC
                                        if (!verifyMac(homeMacKey, updatedEncryptedLog, updatedLogMac)) {
                                            System.err.println("INTEGRITY VIOLATION: devicesLog tampered!");
                                            System.exit(-1);
                                        }
                                        try (FileOutputStream fos = new FileOutputStream(globalLog, false)) {
                                            fos.write(updatedEncryptedLog);
                                        }
                                        // Save devicesLog MAC
                                        Files.write(globalLogMac.toPath(), updatedLogMac);

                                        out.writeObject("OK");
                                    }
                                }
                                decFile.delete();
                                out.flush();
                            }
                            case "RT" -> {
                                Entry<Number, String[]> result = getHistory(client_Commands[1], user);
                                if (result.getValue().length != 0) {
                                    File keyFile = new File("homes/" + client_Commands[1], "key." + client_Commands[1] + "." + user);
                                    File logFile = new File("homes/" + client_Commands[1] + "/devicesLog.txt");

                                    if (logFile.length() == 0) {
                                        out.writeObject(new String[]{"NODATA"});
                                        out.flush();
                                        break;
                                    }

                                    String[] prefix = {"OK", Long.toString((long) result.getKey()),
                                        Long.toString(keyFile.length()), keyFile.getName(),
                                        Long.toString(logFile.length())};
                                    String[] sent = Stream.concat(Arrays.stream(prefix), Arrays.stream(result.getValue()))
                                            .toArray(String[]::new);
                                    out.writeObject(sent);

                                    try {
                                        byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                                        out.writeObject(keyBytes);

                                        byte[] logBytes = Files.readAllBytes(logFile.toPath());
                                        out.writeObject(logBytes);

										Key homeKey = getKey(keyFile);
										SecretKey homeMacKey = deriveMacKey(homeKey);
										byte[] logMac = generateMac(homeMacKey, logBytes);
										out.writeObject(logMac);

                                        out.flush();
                                    } catch (Exception e) {
                                        System.err.println(e.getMessage());
                                        System.exit(-1);
                                    }
                                } else {
                                    switch ((int) result.getKey()) {
                                        case 1 ->
                                            out.writeObject(new String[]{"NOPERM"});
                                        case -2 ->
                                            out.writeObject(new String[]{"NOPERM"});
                                        case -1 ->
                                            out.writeObject(new String[]{"NOHM"});
                                        default ->
                                            throw new AssertionError();
                                    }
                                }
                            }
                            case "RH" -> {
                                String hm = client_Commands[1];
                                String dev = client_Commands[2];
                                String section = dev.substring(0, 1).toUpperCase();

                                File decFile = new File("ho_dec.txt");
                                if (homes.length() != 0) {
                                    decipher(serverKey, homes, "ho_dec.txt");
                                } else {
                                    decFile.createNewFile();
                                }

                                if (!homeExists(hm, decFile)) {
                                    out.writeObject("NOHM");
                                } else if (!checkOwner(hm, user, decFile) && !verifyUserPermission(hm, user, section, decFile)) {
                                    out.writeObject("NOPERM");
                                } else {
                                    File logFile = new File("homes/" + hm + "/" + section + "/" + dev + ".txt");
                                    File keyFile = new File("homes/" + hm + "/" + section, "key." + hm + "." + section + "." + user);

                                    if (!logFile.exists()) {
                                        out.writeObject("NOD");
                                    } else if (!keyFile.exists()) {
                                        out.writeObject("NOKEY");
                                    } else if (logFile.length() == 0) {
                                        out.writeObject("NODATA");
                                    } else {
                                        out.writeObject("OK");

                                        // 1. Enviar a chave (como Objeto)
                                        byte[] wrappedKey = Files.readAllBytes(keyFile.toPath());
                                        out.writeObject(wrappedKey);

                                        // 2. Enviar o ficheiro histórico todo de uma vez (como Objeto)
                                        byte[] fileContent = Files.readAllBytes(logFile.toPath());
                                        out.writeObject(fileContent);

										Key sectionKey = getKey(keyFile);
										SecretKey sectionMacKey = deriveMacKey(sectionKey);
										byte[] fileMac = generateMac(sectionMacKey, fileContent);
										out.writeObject(fileMac);

                                        System.out.println("[" + user + " Thread] RH: Enviada chave e " + fileContent.length + " bytes para " + dev);
                                    }
                                }
                                decFile.delete();
                                out.flush();
                            }

                            default ->
                                out.writeObject("NOCOMMAND");
                        }

                    } finally {
                        command.release();
                    }
                }
            } catch (ClassNotFoundException e1) {
                System.err.println(e1.getMessage());
                System.exit(-1);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
                Thread.currentThread().interrupt();
            }
        } catch (IOException ex) {
            System.out.println("Client disconnected!");
        } finally {
            signal.release();
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private SecretKey deriveMacKey(Key encKey) {
        byte[] encKeyBytes = encKey.getEncoded();
        return new SecretKeySpec(encKeyBytes, "HmacSHA256");
    }

    private boolean verifyMac(SecretKey macKey, byte[] file, byte[] storedMac) {
        byte[] generatedMac = generateMac(macKey, file);
        return Arrays.equals(generatedMac, storedMac);
    }

    private byte[] generateMac(SecretKey key, byte[] b) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(b);
            return mac.doFinal();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return null;
    }

    private void authenticate(String[] args, Key key) {
        try {
            if (users.length() != 0) {
                decipher(key, users, "users_dec.txt");
            }
            File decFile = new File("users_dec.txt");
            if (decFile.exists()) {
                try (Scanner sc = new Scanner(decFile)) {
                    while (sc.hasNextLine()) {
                        String[] credentials = sc.nextLine().split(":");
                        if (credentials[0].equals(args[args.length - 2])) {
                            while (true) {
                                out.writeObject("NO_CERT");
                                out.flush();
                                byte[] salt = Base64.getDecoder().decode(credentials[2]);
                                String passHash = hashPassword(pwd, salt);
                                if (credentials[1].equals(passHash)) {
                                    out.writeObject("OK_USER");
                                    out.flush();
                                    System.out.println("[" + user + " Thread] Authentication successful for user: " + user);
                                    decFile.delete();
                                    return;
                                } else {
                                    out.writeObject("WRONG_PWD");
                                    out.flush();
                                    System.out.println("[" + user + " Thread] Authentication failed for user: " + user);
                                    pwd = (String) in.readObject();
                                }
                            }
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println(e.getMessage());
                    System.exit(-1);
                }
                decFile.delete();
            }
            createUser(user, pwd);
            decFile.delete();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private void createUser(String user, String pwd) {
        try {
            if (users.length() != 0) {
                decipher(serverKey, users, "users_dec.txt");
            }
            byte[] salt = generateSalt();
            String hash = hashPassword(pwd, salt);
            String newUser = user + ":" + hash + ":" + Base64.getEncoder().encodeToString(salt);
            try (FileWriter fw = new FileWriter("users_dec.txt", true)) {
                fw.write(newUser + System.lineSeparator());
                System.out.println("[" + user + " Thread] New user created: " + user);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }
            Files.copy(Path.of("users_dec.txt"), users.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            cipher(users, serverKey);
            usersMac = generateMac(macKey, Files.readAllBytes(users.toPath()));
            out.writeObject("SEND_CERT");
            out.flush();
            File cer = new File("Certs", user + ".cer");
            try (FileOutputStream cert = new FileOutputStream(cer)) {
                byte[] certBytes = (byte[]) in.readObject();
                cert.write(certBytes);
            } catch (ClassNotFoundException e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }
            out.writeObject("OK_NEW_USER");
            out.flush();
        } catch (IOException e) {
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

    private void cipher(File fileToEncrypt, Key key) {
        try {
            if (key == null) {
                throw new KeyException("Key not found!");
            }

            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, key);

            // Encrypt to temp file first
            File tempFile = new File(fileToEncrypt.getName() + ".enc");
            try (FileInputStream logFile = new FileInputStream(fileToEncrypt); FileOutputStream tempOut = new FileOutputStream(tempFile); CipherOutputStream cout = new CipherOutputStream(tempOut, c)) {
                int bytesToRead;
                byte[] buf = new byte[1024];
                while ((bytesToRead = logFile.read(buf, 0, buf.length)) != -1) {
                    cout.write(buf, 0, bytesToRead);
                }
            }

            Files.move(tempFile.toPath(), fileToEncrypt.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private void decipher(Key key, File log, String name) {
        try {

            if (key == null) {
                throw new KeyException("Key not found!");
            }

            byte[] encryptedBytes = Files.readAllBytes(log.toPath());

            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedBytes = c.doFinal(encryptedBytes);

            try (FileOutputStream fos = new FileOutputStream(name)) {
                fos.write(decryptedBytes);
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private String hashPassword(String pwd2, byte[] salt) {
        MessageDigest md;
        byte[] hash;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            hash = md.digest(pwd2.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return null;
    }

    private byte[] generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return saltBytes;
    }

    private void createHome(String homeName) {
        try {
            File decFile = new File("ho_dec.txt");
            if (homes.length() != 0) {
                decipher(serverKey, homes, "ho_dec.txt");
            } else {
                decFile.createNewFile();
            }

            if (homeExists(homeName, new File("ho_dec.txt"))) {

                out.writeObject("HOME_EXISTS");
                out.flush();
                System.out.println("[" + user + " Thread] Home creation failed. Home already exists: " + homeName);
            } else {
                try (FileWriter fw = new FileWriter(decFile, true)) {
                    fw.write(homeName + ":" + user + ">>E:0;G:0;L:0;M:0;P:0;S:0" + System.lineSeparator());
                }

                File newHomeFolder = new File(homesFolder, homeName);
                newHomeFolder.mkdirs();
                File devicesFile = new File(newHomeFolder, "devicesLog.txt");
                devicesFile.createNewFile();

                for (String section : PERMS) {
                    if (section.equals("all")) {
                        continue;
                    }
                    File sectionFolder = new File(newHomeFolder, section);
                    sectionFolder.mkdirs();
                }

                // Now safe to copy and encrypt
                Files.copy(decFile.toPath(), homes.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                cipher(homes, serverKey);
                homesMac = generateMac(macKey, Files.readAllBytes(homes.toPath()));
                devicesLogMac = generateMac(macKey, Files.readAllBytes(devicesFile.toPath()));
                decFile.delete();
                System.out.println("[" + user + " Thread] Home created: " + homeName);
                out.writeObject("HOME_CREATED");
                out.flush();
            }
            decFile.delete();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private boolean userExists(String user) {
        try (Scanner sc = new Scanner(users)) {
            while (sc.hasNextLine()) {
                String[] credentials = sc.nextLine().split(":");
                if (credentials[0].equals(user)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return false;
    }

    private boolean homeExists(String homeName) {
        return homeExists(homeName, homes); // default: use homes (encrypted)
    }

    private boolean homeExists(String homeName, File file) {
        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String[] homeData = sc.nextLine().split(":");
                if (homeData[0].equals(homeName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return false;
    }

    private boolean checkOwner(String homeName, String user) {
        return checkOwner(homeName, user, homes);
    }

    private boolean checkOwner(String homeName, String user, File file) {
        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] homeData = line.split(":");
                if (homeData[0].equals(homeName)) {
                    String[] permissions = line.split(">");
                    String[] owner = permissions[0].split(":");
                    return owner[1].equals(user);
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return false;
    }

    private void addUserToHome(String userToAdd, String homeName, String section) {
        List<String> lines = new ArrayList<>();

        try (Scanner sc = new Scanner(homes)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] parts = line.split(">");
                String[] homeOwner = parts[0].split(":");
                String currentHome = homeOwner[0];

                if (!currentHome.equals(homeName)) {
                    lines.add(line);
                    continue;
                }

                String usersPart = parts.length > 1 ? parts[1] : "";
                String[] usersList = usersPart.isEmpty() ? new String[0] : usersPart.split("/");
                boolean userFound = false;
                StringBuilder newUsersPart = new StringBuilder();

                for (int i = 0; i < usersList.length; i++) {
                    String userEntry = usersList[i];
                    String[] userData = userEntry.split(":", 2);
                    String userName = userData[0];
                    String perms = userData.length > 1 ? userData[1] : "";
                    if (userName.equals(userToAdd)) {
                        userFound = true;
                        if (perms.equals("all") || hasPerm(perms, section)) {
                            userEntry = userName + ":" + section;
                        } else if (section.equals("all")) {
                            userEntry = userName + ":all";
                        } else {
                            userEntry = userName + ":" + perms + "," + section;
                        }
                    }
                    if (i > 0) {
                        newUsersPart.append("/");
                    }
                    newUsersPart.append(userEntry);
                }

                if (!userFound) {
                    if (newUsersPart.length() > 0) {
                        newUsersPart.append("/");
                    }
                    newUsersPart.append(userToAdd).append(":").append(section);
                }

                String sectionsPart = parts.length > 2 ? ">" + parts[2] : ">";
                lines.add(parts[0] + ">" + newUsersPart + sectionsPart);
            }

            try (FileWriter fw = new FileWriter(homes, false)) {
                for (String l : lines) {
                    fw.write(l + System.lineSeparator());
                }
            }
            out.writeObject("USER_ADDED");
            out.flush();
            System.out.println("[" + user + " Thread] User " + userToAdd + " added to home " + homeName + " with section " + section);

        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private boolean hasPerm(String data, String section) {
        String[] userPerms = data.split(",");
        for (String perm : userPerms) {
            if (perm.equals(section)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidSection(String section) {
        for (String perm : PERMS) {
            if (perm.equals(section)) {
                return true;
            }
        }
        return false;
    }

    private Map.Entry<Number, String[]> getHistory(String house, String user) {
        File decFile = new File("ho_dec.txt");
        try {
            if (homes.length() != 0) {
                decipher(serverKey, homes, "ho_dec.txt");
            } else {
                decFile.createNewFile();
            }
            File home = new File("homes/" + house);

            if (!homeExists(house, decFile)) {
                decFile.delete();
                return Map.entry(-1, new String[0]); // NOHM
            }
            try (Scanner sc = new Scanner(new File("homesLog.txt"))) {
                List<String> latestByDevice = new ArrayList<>();
                File devicesLog = new File(home.getPath() + "/devicesLog.txt");

                if (checkOwner(house, user, decFile)) {
                    decFile.delete();
                    return Map.entry(devicesLog.length(), Arrays.copyOfRange(PERMS, 1, PERMS.length));
                } else if (verifyUserPermission(house, user, decFile)) {
                    while (sc.hasNextLine()) {
                        String homesLine = sc.nextLine();
                        if (homesLine.contains(house)) {
                            String[] owners = homesLine.split(">");
                            String[] users_from_File = owners[1].split("/");

                            for (String user1 : users_from_File) {
                                String[] devices = user1.split(":");

                                if (user.equals(devices[0])) {
                                    String[] device_User = devices[1].split(",");
                                    for (String line : device_User) {
                                        if (line.equals(devices[1]) || devices[1].equals("all")) {
                                            latestByDevice.add(line);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    decFile.delete();
                    return Map.entry(devicesLog.length(), latestByDevice.toArray(String[]::new)); //OK/NODATA
                } else {
                    decFile.delete();
                    return Map.entry(-2, new String[0]); //NOPERM
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        decFile.delete();
        return Map.entry(2, new String[0]); //ERROR
    }

    private int verify(String[] commands, String user) {

        File decFile = new File("ho_dec.txt");
        try {
            if (homes.length() != 0) {
                decipher(serverKey, homes, "ho_dec.txt");
            } else {
                return -1; // no homes at all
            }

            if (!homeExists(commands[1], decFile)) {
                decFile.delete();
                return -1; // NOHM
            }

            Path path = decFile.toPath();
            List<String> lines = Files.readAllLines(path);
            List<String> updated = new ArrayList<>();

            if (checkOwner(commands[1], user, decFile)) {
                for (String line : lines) {
                    int last = line.lastIndexOf('>');
                    String devicesPart = line.substring(last + 1);
                    String owners = line.substring(0, last);
                    String[] house_Owner = line.split(">");
                    if (house_Owner[0].contains(user) && house_Owner[0].contains(commands[1])) {
                        String[] devices = devicesPart.split(";");
                        int i = 0;
                        while (!devices[i].contains(commands[2])) {
                            i++;
                        }
                        line = updatedDevice(devices, devices[i]);
                        updated.add(owners + ">" + line);
                    } else {
                        updated.add(line);
                    }
                }

                // Write updated content to ho_dec.txt, copy back and encrypt
                Files.write(path, updated);
                Files.copy(decFile.toPath(), homes.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                cipher(homes, serverKey);
                homesMac = generateMac(macKey, Files.readAllBytes(homes.toPath()));
                decFile.delete();
                return 1; // OK
            } else {
                decFile.delete();
                return 0; // NOPERM
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        return 2;
    }

    private String updatedDevice(String[] devices, String Key) {
        StringBuilder sB = new StringBuilder();
        String[] targetKey = Key.split(":");
        for (int i = 0; i < devices.length; i++) {
            String[] kV = devices[i].split(":");
            String key = kV[0];
            String value = kV[1];
            if (key.equals(targetKey[0])) {
                int counter = Integer.parseInt(value);
                counter++;
                value = String.valueOf(counter);
            }
            sB.append(key).append(":").append(value);
            if (i < devices.length - 1) {
                sB.append(";");
            }
        }
        return sB.toString();
    }

    private boolean verifyUserPermission(String homeName, String user, File homes) {
        return verifyUserPermission(homeName, user, "all", homes);
    }

    private boolean verifyUserPermission(String homeName, String user, String section, File file) {
        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(homeName + ":")) {
                    String[] parts = line.split(">");

                    String[] ownerData = parts[0].split(":");
                    if (ownerData.length > 1 && ownerData[1].equals(user)) {
                        return true; // O dono tem sempre acesso total
                    }

                    if (parts.length < 2 || parts[1].isEmpty()) {
                        return false;
                    }

                    String usersPart = parts[1];
                    String[] userEntries = usersPart.split("/");

                    for (String entry : userEntries) {
                        String[] userData = entry.split(":");
                        if (userData.length > 1 && userData[0].equals(user)) {
                            String perms = userData[1];
                            return perms.contains(section) || perms.equals("all") || section.equals("all");
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private void updateGlobalDeviceLog(String homeName, String deviceName, String lastValue) {
        File globalLog = new File("homes/" + homeName + "/devicesLog.txt");
        Map<String, String> states = new LinkedHashMap<>();
        try {
            if (globalLog.exists()) {
                List<String> lines = Files.readAllLines(globalLog.toPath());
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        states.put(parts[0], parts[1]);
                    }
                }
            }
            states.put(deviceName, lastValue);
            try (FileWriter fw = new FileWriter(globalLog, false)) {
                for (Map.Entry<String, String> entry : states.entrySet()) {
                    fw.write(entry.getKey() + ":" + entry.getValue() + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao atualizar log global.");
        }
    }
}
