import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ClientStartController {
    
    @FXML
    private TextField portField;
    
    @FXML
    private TextField IPField;
    
    @FXML
    private Button startButton;
    
    @FXML
    private VBox welcomePane;
    
    private Client clientConnection;
    private boolean hasTransitioned = false;
    private PokerInfo initialPokerInfo = null;
    
    @FXML
    public void initialize() {
        // Set up focus listeners to clear focus when clicking outside text fields
        welcomePane.setOnMousePressed(event -> {
            welcomePane.requestFocus();
        });
    }
    
    @FXML
    private void handleConnect() {
        String portText = portField.getText().trim();
        String ipText = IPField.getText().trim();
        
        // IP and Port validation
        if (portText.isEmpty() || ipText.isEmpty()) {
            startButton.setText("Please enter IP and Port");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        startButton.setText("Connect");
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return;
        }
        
        final String port = portText;
        final String ip = ipText;
        
        startButton.setDisable(true);
        startButton.setText("Connecting...");
        
        clientConnection = new Client(data -> {
            Platform.runLater(() -> {
                if (data instanceof String) {
                    String message = (String) data;
                    System.out.println(message);
                    if (message.equals("Connected to server")) {
                        // Connection established, waiting for initial message from server
                    }
                } else if (data instanceof PokerInfo) {
                    // Received initial message from server, switch to game screen
                    initialPokerInfo = (PokerInfo) data;
                    System.out.println("Client received PokerInfo with cash: $" + initialPokerInfo.cash);
                    if (!hasTransitioned) {
                        hasTransitioned = true;
                        try {
                            switchToGameScreen();
                        } catch (Exception e) {
                            e.printStackTrace();
                            hasTransitioned = false;
                        }
                    }
                }
            });
        }, ip, port);
        
        clientConnection.start();
        
        // Wait a moment for connection
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    if (!clientConnection.successfulConnection) {
                        startButton.setDisable(false);
                        startButton.setText("Connect");
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void switchToGameScreen() throws Exception {
        System.out.println("switchToGameScreen called");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientGame.fxml"));
        Parent root = loader.load();
        
        ClientGameController gameController = loader.getController();
        gameController.setClient(clientConnection);
        
        // Set initial cash from server
        if (initialPokerInfo != null) {
            gameController.clientPokerInfo.cash = initialPokerInfo.cash;
            gameController.updateCashDisplay(initialPokerInfo.cash);
            System.out.println("Set initial cash to: $" + initialPokerInfo.cash);
        }
        
        Scene scene = new Scene(root, 1000, 700);
        Stage stage = (Stage) startButton.getScene().getWindow();
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setTitle("Three Card Poker");
        System.out.println("Scene switched successfully");
    }
}
