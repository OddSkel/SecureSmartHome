# SegConf

Commands to execute .jar:

- Compile all .java files with `javac *.java`

- Create Server .jar with: `jar cfe SpertaServer.jar SpertaServer SpertaServer.class ServerThread.class`

- Create Client .jar with: `jar cfe SpertaClient.jar SpertaClient SpertaClient.class ServerThread.class`

- Run Server with: `java -jar SpertaServer.jar <Port> <password-cifra> <keystore> <password-keystore>`

- Run Client in another terminal with: `java -jar SpertaClient.jar <IP:Port> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>`

## Description of Project

Implementation of Sperta, a smart home system that manages smart things. It is based on a Client-Server authentication, where the client sends its username and password to the server. If it's a new user, then the server accepts the user and registers it in a file(`usersLog.txt`). If it's not a new user, then the server checks the password kept in `usersLog.txt`. If it's a match, then the server accepts the authentication, otherwise it asks to insert again the password.

When the server is executed without parameters, it starts, by ommition, on port 22345. The client then has to connect to that port if it wants to be authenticated by that server. By ommission, it does so.

## Organization of Project

The organization of this project is as follows:

When the first user is created, the server creates a folder called `homes`. In there, all of the homes created by the users will be put inside this folder. In each folder, there will be created 6 folders, each for every smart device and a text file called `devicesLog.txt`. In there, there will be the last EC command made by each device, as well as the time of activation. After the RD command, a new file will be created inside with the name of the device, as well as the number of devices created in that house(like `M1.txt` is inside folder `M`) with the time it was created, as well as the device name and its counter. The information about the houses, as well as its permissons and the devices created in the house, are all stored in a file called `homesLog.txt`.

## Commands Available

After the client successfully authenticates, it is presented a set of available commands to be made by this application. Those are:

`CREATE <hm>`: Create a new home with name **hm**;

`ADD <user> <hm> <s>`: Adds **user** to **hm**, section **s**;

`RD <hm> <s>`: Registers a new device inside **hm**, section **s**. After registering a new device in section **s**, it increments the counter to account a new device registered in the server;

`EC <hm> <d> <int>`: Sends value **int** of state/time of device **d** in home **hm**. The possible values of int are: 0(off), 1(on) and between 2 and 600(time connected, maximum 600 minutes);

`RT <hm>`: Receives a .txt file with the last command of state/time sent to each device in home **hm**;

`RH <hm> <d>`: Receives a .csv file with all of the commands made to device **d** of home **hm**.

## Output Message

If the operation was a success, then the server sends a `OK` response to the client. Otherwise, this application also comes with its error handling of the application, such as:

`NOK`: Response received in CREATE command when a house already exists. It is also received in EC when the value sent is not vallid;

`NOPERM`: Response received in various commands. It states that the user doesn't have permissions to do that command;

`NOHM`: Response received in various commands. This happens when the house that the house in input doesn't exists and, therefore, can't perform the operation;

`NOUSER`: Response received in ADD command. As the name says, we're trying to add a name that it isn't registered in the server;

`NOD`: Response received in RH and EC commands. It is used to clarify the user that the device doesn't exist.

## Limitations

The communication, in phase 1, isn't secured. Since the client and server communicate with each other without proper secure connection, then it is prone to Man-in-the-Middle attacks.

The server trusts the input of client, and vice-versa, which makes it vulnerable to spoofing attacks, where a mallicious user personifies the server and communicates with the client, giving him wrong messages or wrong files to see.

The files, in phase 1, are not encrypted, which leads to a mallicious user to collect the information without needing it to decrypt it.

The number of devices of this program is limited, which means that a new device needs to be manually added to the PERMS constant, in order to being able to accept registering it and sending state/time value.

In phase 1, the server doesn't know if it's talking with the right client and the client doesn't know if it's being authenticated by a server (could be a malicious impersonating a server talking to a client or impersonating a client talking to a server).

## Future Work

Encrypting files, as well as creating proper secure communication and handling confidentiality, integrity and availability properties.

## Authors

Frederico Dias number 59807

Manuel Leitão number 59809

Bruno Sousa number 58804

This work is in the context of the course **Segurança e Confiabilidade** of the university **Faculdade de Ciências da Universidade de Lisboa** for a bachelor's degree.
