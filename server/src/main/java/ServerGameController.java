import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class ServerGameController {
    
    @FXML
    private Text numClientsText;
    
    @FXML
    private ListView<String> listItems;
    
    @FXML
    private Button serverOnButton;
    
    @FXML
    private Button serverOffButton;
    
    @FXML
    private VBox gameRoot;
    
    private Server server;
    private ObservableList<String> logMessages;
    
    @FXML
    public void initialize() {
        logMessages = FXCollections.observableArrayList();
        listItems.setItems(logMessages);
        serverOffButton.setDisable(false);
        serverOnButton.setDisable(true);
        
        // Add focus listener to clear ListView selection when clicking outside
        gameRoot.setOnMousePressed(event -> {
            if (event.getTarget() != listItems) {
                gameRoot.requestFocus();
                listItems.getSelectionModel().clearSelection();
            }
        });
    }
    
    public void setServer(Server server) {
        this.server = server;
        updateClientCount(0);
    }
    
    public void updateClientCount(int count) {
        Platform.runLater(() -> {
            synchronized (this) {
                numClientsText.setText(String.valueOf(count));
            }
        });
    }
    
    public void addLogMessage(Object message) {
        Platform.runLater(() -> {
            synchronized (this) {
                String timeStamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                logMessages.add(0, "[" + timeStamp + "] " + message.toString());
                
                // Keep log to reasonable size
                if (logMessages.size() > 500) {
                    logMessages.remove(logMessages.size() - 1);
                }
                
                // Update client count
                if (server != null) {
                    numClientsText.setText(String.valueOf(server.getClientCount()));
                }
            }
        });
    }
    
    @FXML
    private void handleEnableServer() {
        if (server != null && !server.isRunning) {
            server.startServer();
            serverOnButton.setDisable(true);
            serverOffButton.setDisable(false);
            addLogMessage("Server enabled");
        }
    }
    
    @FXML
    private void handleDisableServer() {
        if (server != null && server.isRunning) {
            server.stopServer();
            serverOnButton.setDisable(false);
            serverOffButton.setDisable(true);
            addLogMessage("Server disabled");
        }
    }
}
