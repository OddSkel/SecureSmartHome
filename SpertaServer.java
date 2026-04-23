
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
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SpertaServer {
	private static final int MAX_CLIENTS = 3;
	private static final Semaphore signal = new Semaphore(MAX_CLIENTS);
	private static final Semaphore command_signal = new Semaphore(1);
	private String serverPwdCifra;
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

	public void startServer (int port, String pwdCifra, String keystorePath, String keystorePwd){
		try(ServerSocket sSoc = new ServerSocket(port)) {
			System.out.println("[SERVER] Server started on port " + port);
			while(true) {
				try {
					Socket inSoc = sSoc.accept();
					SecureRandom secure_random = new SecureRandom();
					ObjectOutputStream check = new ObjectOutputStream(inSoc.getOutputStream());
					ObjectInputStream rec = new ObjectInputStream(inSoc.getInputStream());

					byte[] nounce = new byte[8];
					secure_random.nextBytes(nounce);
					check.writeObject(nounce);
					check.flush();

					byte [] receive = (byte[]) rec.readObject();
					if (!Arrays.equals(nounce, receive)) {
						check.writeObject("NOK-ATTEST");
						check.flush();
						inSoc.close();
						continue;
					}
					check.writeObject("OK-ATTEST");
					check.flush();

					signal.acquire();
					ServerThread newServerThread = new ServerThread(inSoc, signal, command_signal, check, rec, pwdCifra);
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
}

class ServerThread extends Thread {
	private Socket socket = null;
	private Semaphore signal = null;
	private Semaphore command = null;
	private String serverPwdCifra;

    private File users, homes, homesFolder;
    private String user, pwd, trustore, pass_truststore, keystore, pass_keystore;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    private static final String[] PERMS = {"all", "E", "G", "L", "M", "P", "S"};

	ServerThread(Socket inSoc, Semaphore signal, Semaphore command_signal, ObjectOutputStream out, ObjectInputStream in, String pwdCifra) {
		socket = inSoc;
		this.signal = signal;
		command = command_signal;
		this.out = out;
		this.in = in;
		serverPwdCifra = pwdCifra;
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
                authenticate(client_args);
                while (true) {
                    String[] client_Commands = (String[]) in.readObject();
                    command.acquire();
                    try {
                        switch (client_Commands[0]) {
                            case "CREATE" -> {
                                String houseName = client_Commands[1];
                                System.out.println("[" + user + " Thread] CREATE command received for home: " + houseName);
                                if (createHome(houseName)) {
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
                                        File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith(client_Commands[2]));
                                        if (matchingFiles.length == 1) {
                                            out.writeObject(new String[]{"OK", Long.toString(matchingFiles[0].length()), matchingFiles[0].getName()});
                                            File keyFile = new File(dir, "key." + client_Commands[1] + "." + client_Commands[2] + "." + user);
                                            out.writeObject(keyFile.length());
                                            try (FileInputStream key = new FileInputStream(keyFile)) {
                                                int bytesToRead;
                                                byte[] buf = new byte[1024];
                                                while ((bytesToRead = key.read(buf, 0, buf.length)) != -1) {
                                                    out.write(buf, 0, bytesToRead);
                                                    out.flush();
                                                }
                                            }
                                            try (FileInputStream key = new FileInputStream(matchingFiles[0])) {
                                                int bytesToRead;
                                                byte[] buf = new byte[1024];
                                                while ((bytesToRead = key.read(buf, 0, buf.length)) != -1) {
                                                    out.write(buf, 0, bytesToRead);
                                                    out.flush();
                                                }
                                            }
                                            matchingFiles[0].delete();
                                        } else {
                                            out.writeObject(new String[]{"OK", Long.toString(0)});
                                            File keyFile = new File(dir, "key." + client_Commands[1] + "." + client_Commands[2] + "." + user);
                                            out.writeObject(keyFile.length());
                                            try (FileInputStream key = new FileInputStream(keyFile)) {
                                                int bytesToRead;
                                                byte[] buf = new byte[1024];
                                                while ((bytesToRead = key.read(buf, 0, buf.length)) != -1) {
                                                    out.write(buf, 0, bytesToRead);
                                                    out.flush();
                                                }
                                            }
                                        }

                                        String name = (String) in.readObject();
                                        long size = in.readLong();
                                        File deviceFile = new File(dir, name);

                                        try (FileOutputStream history = new FileOutputStream(deviceFile)) {
                                            int bytesRead;
                                            byte[] buffer = new byte[1024];
                                            while (size > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(size, (long) buffer.length))) != -1) {
                                                history.write(buffer, 0, bytesRead);
                                                size -= bytesRead;
                                            }
                                        } catch (IOException e) {
                                            System.err.println(e.getMessage());
                                            System.exit(-1);
                                        }
                                    }
                                    case -1 ->
                                        out.writeObject(new String[]{"NOHM"});
                                    default ->
                                        throw new AssertionError();
                                }
                                out.flush();
                            }
                            case "EC" -> {
                                String homeNameEC = client_Commands[1];
                                String deviceName = client_Commands[2];
                                String s = deviceName.substring(0, 1).toUpperCase();

                                if (verifyUserPermission(homeNameEC, user, s)) {
                                    File keyFile = new File("homes/" + homeNameEC + "/" + s, "key." + homeNameEC + "." + s + "." + user);
                                    byte[] wrappedKey = Files.readAllBytes(keyFile.toPath());
                                    out.writeObject(wrappedKey);
                                    out.flush();

                                    byte[] encryptedValue = (byte[]) in.readObject();
                                    String valToStore = Base64.getEncoder().encodeToString(encryptedValue);

                                    File deviceFile = new File("homes/" + homeNameEC + "/" + s + "/" + deviceName + ".txt");
                                    try (FileWriter fwDevice = new FileWriter(deviceFile, true)) {
                                        fwDevice.write(System.currentTimeMillis() + "," + deviceName + "," + valToStore + System.lineSeparator());
                                    }

                                    updateGlobalDeviceLog(homeNameEC, deviceName, valToStore);
                                    out.writeObject("OK");
                                } else {
                                    out.writeObject("NOPERM");
                                }
                            }
                            case "RT" -> {
                                Entry<Number, String[]> result = getHistory(client_Commands[1], user);
                                if (result.getValue().length != 0 && (long) result.getKey() > 0) {
                                    File keyFile = new File("homes/" + client_Commands[1], "key."
                                            + client_Commands[1] + "." + user);
                                    File logFile = new File("homes/" + client_Commands[1] + "/devicesLog.txt");
                                    String[] prefix = {"OK", Long.toString((long) result.getKey()), Long.toString(keyFile.length()), keyFile.getName(), Long.toString(logFile.length())};
                                    String[] sent = Stream.concat(Arrays.stream(prefix), Arrays.stream(result.getValue())).toArray(String[]::new);
                                    out.writeObject(sent);
                                    try (FileInputStream fin = new FileInputStream(keyFile); FileInputStream log = new FileInputStream(logFile)) {

                                        int bytesToRead;
                                        byte[] buf = new byte[1024];
                                        while ((bytesToRead = fin.read(buf, 0, buf.length)) != -1) {
                                            out.write(buf, 0, bytesToRead);
                                            out.flush();
                                        }

                                        int bytesLog;
                                        buf = new byte[1024];
                                        while ((bytesLog = log.read(buf, 0, buf.length)) != -1) {
                                            out.write(buf, 0, bytesLog);
                                            out.flush();
                                        }

                                    } catch (Exception e) {
                                        System.err.println(e.getMessage());
                                        System.exit(-1);
                                    }
                                } else if ((long) result.getKey() == 0){
									out.writeObject(new String[]{"NODATA"});
									out.flush();}
								else {
                                    switch ((int) result.getKey()) {
                                        case -2 ->
                                            out.writeObject(new String[]{"NOPERM"});
                                        case -1 ->
                                            out.writeObject(new String[]{"NOHM"});
                                        default ->
                                            throw new AssertionError();
                                    }
                                    out.flush();
                                }
                            }
                            case "RH" -> {
                                String hm = client_Commands[1];
                                String dev = client_Commands[2];
                                String section = dev.substring(0, 1).toUpperCase();

                                if (!homeExists(hm)) {
                                    out.writeObject("NOHM");
                                } else if (!checkOwner(hm, user) && !verifyUserPermission(hm, user, section)) {
                                    out.writeObject("NOPERM");
                                } else {
                                    File logFile = new File("homes/" + hm + "/" + section + "/" + dev + ".txt");

                                    if (!logFile.exists()) {
                                        out.writeObject("NOD");
                                    } else if (logFile.length() == 0) {
                                        out.writeObject("NODATA");
                                    } else {
                                        // Ir buscar a Chave de Secção do utilizador para esta secção
                                        File keyFile = new File("homes/" + hm + "/" + section, "key." + hm + "." + section + "." + user);

                                        if (!keyFile.exists()) {
                                            out.writeObject("NOKEY"); // Chave não encontrada
                                        } else {
                                            out.writeObject("OK");

                                            // 1. Enviar a Chave de Secção cifrada
                                            byte[] wrappedKey = Files.readAllBytes(keyFile.toPath());
                                            out.writeObject(wrappedKey);

                                            // 2. Ler e enviar o conteúdo do ficheiro de log
                                            byte[] fileContent = Files.readAllBytes(logFile.toPath());
                                            out.writeLong((long) fileContent.length); // Envia o tamanho
                                            out.write(fileContent);                   // Envia o conteúdo
                                            System.out.println("[" + user + " Thread] RH: Sent " + fileContent.length + " bytes for " + dev);
                                        }
                                    }
                                }
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

    private void authenticate(String[] args) {
        try (Scanner sc = new Scanner(users)) {
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
                            return;
                        } else {
                            out.writeObject("WRONG_PWD");
                            out.flush();
                            System.out.println("[" + user + " Thread] Authentication failed for user: " + user + ". Incorrect password.");
                            pwd = (String) in.readObject();
                        }
                    }
                }
            }
            createUser(user, pwd);
            out.writeObject("OK_NEW_USER");
            out.flush();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private void createUser(String user, String pwd) {
        try {
            byte[] salt = generateSalt();
            String hash = hashPassword(pwd, salt);
            String newUser = user + ":" + hash + ":" + Base64.getEncoder().encodeToString(salt);
            try (FileWriter fw = new FileWriter(users, true)) {
                fw.write(newUser + System.lineSeparator());
                System.out.println("[" + user + " Thread] New user created: " + user);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }

            out.writeObject("SEND_CERT");
            out.flush();

            try (FileOutputStream cert = new FileOutputStream(Path.of("Certs", user + ".cer").toString())) {
                int bytesRead;
                long size = in.readLong();
                byte[] buffer = new byte[1024];
                while (size > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(size, (long) buffer.length))) != -1) {
                    cert.write(buffer, 0, bytesRead);
                    size -= bytesRead;
                }
            }
        } catch (IOException e) {
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

    private boolean createHome(String homeName) {
        try {
            if (homeExists(homeName)) {
                out.writeObject("HOME_EXISTS");
                out.flush();
                System.out.println("[" + user + " Thread] Home creation failed. Home already exists: " + homeName);
                return false;
            } else {
                try (FileWriter fw = new FileWriter(homes, true)) {
                    fw.write(homeName + ":" + user + ">>E:0;G:0;L:0;M:0;P:0;S:0" + System.lineSeparator());

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

                    System.out.println("[" + user + " Thread] Home created: " + homeName);
                    out.writeObject("HOME_CREATED");
                    out.flush();
                    return true;
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return false;
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
        try (Scanner sc = new Scanner(homes)) {
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
        try (Scanner sc = new Scanner(homes)) {
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
        File home = new File("homes/" + house);
        if (!homeExists(house)) {
            return Map.entry(-1, new String[0]); //NOHM

                }try (Scanner sc = new Scanner(new File("homesLog.txt"))) {

            List<String> devices_Lines = Files.readAllLines(Path.of(home.getAbsolutePath() + "/devicesLog.txt"));
            Map<String, String> latestByDevice = new LinkedHashMap<>();
            File devicesLog = new File(home.getPath() + "/devicesLog.txt");

            if (checkOwner(house, user)) {
                return Map.entry(devicesLog.length(), Arrays.copyOfRange(PERMS, 1, PERMS.length));
            } else if (verifyUserPermission(house, user)) {
                while (sc.hasNextLine()) {
                    String homesLine = sc.nextLine();
                    if (homesLine.contains(house)) {
                        String[] owners = homesLine.split(">");
                        String[] users_from_File = owners[1].split("/");

                        for (String user1 : users_from_File) {
                            String[] devices = user1.split(":");

                            if (user.equals(devices[0])) {
                                for (String line : devices_Lines) {
                                    String[] parts = line.split(":");

                                    if (parts[0].contains(devices[1]) || devices[1].equals("all")) {
                                        latestByDevice.put(parts[0], parts[1]);
                                    }
                                }
                            }
                        }
                    }
                }
                return Map.entry(devicesLog.length(), latestByDevice.keySet().toArray(String[]::new));
            } else {
                return Map.entry(-2, new String[0]); //NOPERM
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }

        return Map.entry(2, new String[0]); //ERROR
    }

    private int verify(String[] commands, String user) {
        if (!homeExists(commands[1])) {
            return -1; //NOHM

                }try (Scanner sc = new Scanner(new File("homesLog.txt"))) {
            Path path = Path.of("homesLog.txt");
            List<String> lines = Files.readAllLines(path);
            List<String> updated = new ArrayList<>();
            while (sc.hasNextLine()) {
                if (checkOwner(commands[1], user)) {
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
                    Files.write(path, updated);
                    return 1; //OK
                } else {
                    return 0; //NOPERM

                            }}
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

    private boolean verifyUserPermission(String homeName, String user) {
        return verifyUserPermission(homeName, user, "all");
    }

    private boolean verifyUserPermission(String homeName, String user, String section) {
		try (Scanner sc = new Scanner(homes)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.startsWith(homeName + ":")) {
					String[] parts = line.split(">");
					
					String[] ownerData = parts[0].split(":");
					if (ownerData.length > 1 && ownerData[1].equals(user)) {
						return true; // O dono tem sempre acesso total
					}

					if (parts.length < 2 || parts[1].isEmpty()) return false;

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

/*
try(FileWriter fW = new FileWriter(Paths.get("homes/" + target[1], target[2], target[2] + value + ".txt").toString())) {
					fW.write(System.currentTimeMillis() + "," + key + ":" + value + System.lineSeparator());
				} catch (Exception e) {
					System.err.println(e.getMessage());
					System.exit(-1);
				}
 */

/*
try(FileWriter fW = new FileWriter(Paths.get("homes/" + target[1], target[2], target[2] + value + ".txt").toString())) {
					fW.write(System.currentTimeMillis() + "," + key + ":" + value + System.lineSeparator());
				} catch (Exception e) {
					System.err.println(e.getMessage());
					System.exit(-1);
				}
*/
