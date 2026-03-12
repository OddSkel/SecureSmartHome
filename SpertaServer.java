import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class SpertaServer {
    public static void main(String[] args) {
        System.out.println("[SERVER] Starting server...");
        SpertaServer server = new SpertaServer();
        switch (args.length) {
            case 1 -> server.startServer(Integer.parseInt(args[0]));
            case 0 -> server.startServer(22345);
            default -> {
                System.out.println("Usage: java SpertaServer <port>");
                System.exit(-1);
            }
        }
    }

    public void startServer(int port) {
        try (ServerSocket sSoc = new ServerSocket(port)) {
            System.out.println("[SERVER] Server started on port " + port);
            while (true) {
                try {
                    Socket inSoc = sSoc.accept();
                    ServerThread newServerThread = new ServerThread(inSoc);
                    newServerThread.start();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
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
    private boolean running = true;
    private File usersFile, workspacesFile;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    ServerThread(Socket inSoc) {
        this.socket = inSoc;
        System.out.println("thread do server para cada cliente");
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            usersFile = new File("usersLog.txt");
            if (!usersFile.exists()) usersFile.createNewFile();
            
            workspacesFile = new File("workspaces.txt");
            if (!workspacesFile.exists()) workspacesFile.createNewFile();

            String user = (String) in.readObject();
            String pwd = (String) in.readObject();
            authenticate(user, pwd);

            while (running) {
                Object received = in.readObject();
                String command;
                String[] command_Args = null;

                if (received instanceof String[]) {
                    command_Args = (String[]) received;
                    command = command_Args[0];
                } else {
                    command = (String) received;
                }

                switch (command) {
                    case "RD" -> {
                        int result = verify(command_Args[1], command_Args[2], user);
                        switch (result) {
                            case 0 -> out.writeObject("NOPERM");
                            case 1 -> out.writeObject("OK");
                            case -1 -> out.writeObject("NOHM");
                        }
                        out.flush();
                    }
                    case "RT" -> {
                        Number result = getHistory(command_Args[1], user);
                        if (result instanceof Long) {
                            String[] resp = {"Ok", Long.toString((long) result)};
                            out.writeObject(resp);
                            try (FileInputStream fis = new FileInputStream("history_to_send.txt")) {
                                byte[] buf = new byte[1024];
                                int n;
                                while ((n = fis.read(buf)) != -1) {
                                    out.write(buf, 0, n);
                                }
                                out.flush();
                            }
                        } else {
                            switch ((int) result) {
                                case 0 -> out.writeObject("NODATA");
                                case 1 -> out.writeObject("NOPERM");
                                case -1 -> out.writeObject("NOHM");
                            }
                        }
                        out.flush();
                    }
                    case "CREATE", "ADD", "EC", "RH" -> { /* Por implementar */ }
                    default -> out.writeObject("NOCOMMAND");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client disconnected.");
        }
    }

    private void authenticate(String user, String pwd) throws IOException, ClassNotFoundException {
        boolean isAuthed = false;
        try (Scanner sc = new Scanner(usersFile)) {
            while (sc.hasNextLine()) {
                String[] credentials = sc.nextLine().split(":");
                if (credentials[0].equals(user)) {
                    if (credentials[1].equals(pwd)) {
                        out.writeObject("OK_USER");
                        out.flush();
                        return;
                    } else {
                        out.writeObject("WRONG_PWD");
                        out.flush();
                        pwd = (String) in.readObject();
                        authenticate(user, pwd); // Re-tentativa
                        return;
                    }
                }
            }
            createUser(user, pwd);
            out.writeObject("OK_NEW_USER");
            out.flush();
        }
    }

    private void createUser(String user, String pwd) throws IOException {
        try (FileWriter fw = new FileWriter(usersFile, true)) {
            fw.write(user + ":" + pwd + System.lineSeparator());
        }
    }

    private int verify(String house, String section, String user) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("workspaces.txt"));
            for (int i = 0; i < lines.size(); i++) {
                String[] lineArgs = lines.get(i).split(":");
                if (lineArgs[0].equals(house)) {
                    if (lineArgs[1].equals(user)) {
                        lines.set(i, lineArgs[0] + ":" + user + ":" + section);
                        Files.write(Paths.get("workspaces.txt"), lines);
                        return 1;
                    } else return 0;
                }
            }
        } catch (IOException e) { return -1; }
        return -1;
    }

    private Number getHistory(String house, String user) {
        File house_Dir = new File(house);
        if (!house_Dir.exists()) return -1;
        try (Scanner sc = new Scanner(new File("users.txt"))) {
            while (sc.hasNextLine()) {
                String[] line = sc.nextLine().split(":");
                if (line[0].equals(house)) {
                    for (int i = 1; i < line.length; i++) {
                        if (line[i].equals(user)) {
                            File history = new File(house + "/history.txt");
                            if (history.length() == 0) return 0;
                            File toSend = new File("history_to_send.txt");
                            try (Scanner sc1 = new Scanner(history); FileWriter fw = new FileWriter(toSend)) {
                                while (sc1.hasNextLine()) {
                                    String[] l = sc1.nextLine().split(":");
                                    fw.write("Last Operation of " + l[0] + ": " + l[l.length - 1] + "\n");
                                }
                                return toSend.length();
                            }
                        }
                    }
                    return 1;
                }
            }
        } catch (IOException e) { return -1; }
        return 2;
    }
}