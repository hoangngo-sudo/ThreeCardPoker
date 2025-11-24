import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;

import javafx.application.Platform;

public class Client extends Thread {
    Socket socketClient;
    String IP;
    int port;
    boolean successfulConnection = false;
    ObjectInputStream in;
    ObjectOutputStream out;
    Consumer<Serializable> callback;

    private ClientGameController gameControllerRef;     // reference to GameController

    // method to set the Game Controller reference
    public void setGameController(ClientGameController gameControllerRef) {
        this.gameControllerRef = gameControllerRef;
    }

    Client(Consumer<Serializable> call) {
        callback = call;
        this.IP = "127.0.0.1";
        this.port = 5555;
    }
    
    Client(Consumer<Serializable> call, String IP, String port) {
        callback = call;
        this.IP = IP;
        try {
            this.port = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            this.port = 5555;
        }
    }
    
    @Override
    public void run() {
        try {
            socketClient = new Socket(IP, port);
            out = new ObjectOutputStream(socketClient.getOutputStream());
            in = new ObjectInputStream(socketClient.getInputStream());
            socketClient.setTcpNoDelay(true);
            successfulConnection = true;
            callback.accept("Connected to server");
        } catch (Exception e) {
            callback.accept("Connection failed: " + e.getMessage());
            successfulConnection = false;
            return;
        }
        
        while (true) {
            try {
                PokerInfo message = (PokerInfo) in.readObject();
                callback.accept(message);
            } catch (Exception e) {
                callback.accept("Connection lost");
                notifyServerClosed();       // call method when server is closed
                break;
            }
        }
    }
    
    public void send(PokerInfo pInfo) {
        try {
            out.writeObject(pInfo);
            out.reset();
        } catch (Exception e) {
            callback.accept("Error sending data: " + e.getMessage());
        }
    }

    // private method that notifies client when server is closed
    private void notifyServerClosed() {

        try {
            socketClient.close();
        } catch (Exception ignored) {}

        if (gameControllerRef != null) {
            Platform.runLater(() -> gameControllerRef.returnToStartAfterDisconnect());
        }
    }
}
