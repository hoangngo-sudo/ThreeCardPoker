import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ClientEndController {
    
    @FXML
    private Text endScreenWinLoseText;
    
    @FXML
    private Text endScreenWinLossMoney;
    
    @FXML
    private Button returnToGameButton;
    
    @FXML
    private Button endGameButton;
    
    @FXML
    private VBox endScreenClientBox;
    
    private ClientGameController gameController;
    private PokerInfo pokerInfo;
    private boolean isFirstLook = true; // Default to true (Green theme)
    private boolean themeApplied = false;
    
    @FXML
    public void initialize() {
        endScreenWinLossMoney.setText("$0");
    }
    
    public void setGameController(ClientGameController controller) {
        this.gameController = controller;
    }
    
    public void setPokerInfo(PokerInfo info) {
        this.pokerInfo = info;
        updateDisplay();
    }
    
    public void setThemeState(boolean isFirstLook, boolean themeApplied) {
        this.isFirstLook = isFirstLook;
        this.themeApplied = themeApplied;
    }
    
    private void updateDisplay() {
        Platform.runLater(() -> {
            if (pokerInfo.winningsThisRound > 0) {
                // Positive net earnings - player won
                endScreenWinLoseText.setText("YOU WON!");
                endScreenWinLoseText.setStyle("-fx-fill: #4CAF50;");
                endScreenWinLossMoney.setText("+$" + pokerInfo.winningsThisRound);
            } else if (pokerInfo.winningsThisRound < 0) {
                // Negative net earnings - player lost
                endScreenWinLoseText.setText("You Lost");
                endScreenWinLoseText.setStyle("-fx-fill: #F44336;");
                endScreenWinLossMoney.setText("-$" + Math.abs(pokerInfo.winningsThisRound));
            } else {
                // Zero net earnings - push (tie or dealer didn't qualify)
                endScreenWinLoseText.setText("Push");
                endScreenWinLoseText.setStyle("-fx-fill: #FFC107;");
                endScreenWinLossMoney.setText("$0");
            }
        });
    }
    
    @FXML
    private void handleReturn() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientGame.fxml"));
            Parent root = loader.load();
            
            ClientGameController newGameController = loader.getController();
            newGameController.setClient(gameController.clientConnection);
            
            // Preserve poker info
            newGameController.clientPokerInfo = new PokerInfo();
            newGameController.clientPokerInfo.cash = pokerInfo.cash;
            
            // Restore theme state
            newGameController.restoreThemeState(isFirstLook, themeApplied);
            
            Scene scene = new Scene(root, 1000, 700);
            Stage stage = (Stage) returnToGameButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setTitle("Three Card Poker");
            
            newGameController.returnToGame();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }
}
