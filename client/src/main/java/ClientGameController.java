import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ClientGameController {
    
    @FXML private ImageView c1, c2, c3;
    @FXML private ImageView d1, d2, d3;
    @FXML private Text ante, pp, play, cash;
    @FXML private Text pHandVal, dHandVal;
    @FXML private Text dealerCardLabel, yourHandLabel;
    @FXML private Text anteLabel, ppLabel, playLabel, winningsLabel;
    @FXML private Button dealButton, playButton, foldButton, confirmButton;
    @FXML private TextField anteWagerField, ppWagerField;
    @FXML private ListView<String> listItems2;
    @FXML private VBox gamePane;
    
    public Client clientConnection;
    public PokerInfo clientPokerInfo;
    private ObservableList<String> logMessages;
    private int roundCount = 0;
    private boolean isFirstLook = true;
    private boolean themeApplied = false;
    
    @FXML
    public void initialize() {
        logMessages = FXCollections.observableArrayList();
        listItems2.setItems(logMessages);
        clientPokerInfo = new PokerInfo();
        
        // Add focus listener to clear focus when clicking outside text fields
        gamePane.setOnMousePressed(event -> {
            if (event.getTarget() != anteWagerField && event.getTarget() != ppWagerField) {
                gamePane.requestFocus();
            }
        });
        
        // Set initial values with dollar signs
        ante.setText("$0");
        pp.setText("$0");
        play.setText("$0");
        cash.setText("$0");
        
        // Add rounded corners to all card images
        addRoundedCorners(c1);
        addRoundedCorners(c2);
        addRoundedCorners(c3);
        addRoundedCorners(d1);
        addRoundedCorners(d2);
        addRoundedCorners(d3);
        
        // Set initial card backs
        setCardImage(c1, "back");
        setCardImage(c2, "back");
        setCardImage(c3, "back");
        setCardImage(d1, "back");
        setCardImage(d2, "back");
        setCardImage(d3, "back");
        
        addLogMessage("Welcome to 3 Card Poker!");
        addLogMessage("Place your bets to begin.");
    }
    
    public void setClient(Client client) {
        this.clientConnection = client;
        
        // Update callback to handle messages
        this.clientConnection.callback = data -> {
            Platform.runLater(() -> {
                if (data instanceof PokerInfo) {
                    handleServerResponse((PokerInfo) data);
                } else if (data instanceof String) {
                    addLogMessage((String) data);
                }
            });
        };
    }
    
    public void addLogMessage(String message) {
        Platform.runLater(() -> {
            String timeStamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            logMessages.add(0, "[" + timeStamp + "] " + message);
            
            if (logMessages.size() > 100) {
                logMessages.remove(logMessages.size() - 1);
            }
        });
    }
    
    private void setCardImage(ImageView imageView, String cardString) {
        try {
            String imagePath = "/cards/" + cardString + ".png";
            Image image = new Image(getClass().getResourceAsStream(imagePath));
            imageView.setImage(image);
        } catch (Exception e) {
            addLogMessage("Error loading card image: " + cardString);
        }
    }
    
    private void addRoundedCorners(ImageView imageView) {
        Rectangle clip = new Rectangle();
        clip.setWidth(110);
        clip.setHeight(154);
        clip.setArcWidth(5);
        clip.setArcHeight(5);
        imageView.setClip(clip);
    }
    
    @FXML
    private void handleConfirm() {
        try {
            int anteValue = Integer.parseInt(anteWagerField.getText().trim());
            int ppValue = ppWagerField.getText().trim().isEmpty() ? 
                         0 : Integer.parseInt(ppWagerField.getText().trim());
            
            if (anteValue < 5 || anteValue > 25) {
                addLogMessage("Ante must be between $5 and $25");
                return;
            }
            
            if (ppValue > 0 && (ppValue < 5 || ppValue > 25)) {
                addLogMessage("Pair Plus must be between $5 and $25 or 0");
                return;
            }
            
            clientPokerInfo.ante = anteValue;
            clientPokerInfo.pairPlus = ppValue;
            
            ante.setText("$" + anteValue);
            pp.setText("$" + ppValue);
            
            confirmButton.setDisable(true);
            dealButton.setDisable(false);
            anteWagerField.setDisable(true);
            ppWagerField.setDisable(true);
            
            addLogMessage("Bets confirmed. Click Deal to start.");
            
        } catch (NumberFormatException e) {
            addLogMessage("Please enter valid bet amounts");
        }
    }
    
    @FXML
    private void handleDeal() {
        clientPokerInfo.buttonPressed = 1; // Deal
        clientConnection.send(clientPokerInfo);
        
        dealButton.setDisable(true);
        addLogMessage("Dealing cards...");
    }
    
    private void handleServerResponse(PokerInfo data) {
        clientPokerInfo = data;
        
        if (data.buttonPressed == 1) { // Response to deal
            // Show player cards
            setCardImage(c1, data.card1);
            setCardImage(c2, data.card2);
            setCardImage(c3, data.card3);
            
            // Show dealer cards face down initially
            setCardImage(d1, "back");
            setCardImage(d2, "back");
            setCardImage(d3, "back");
            
            pHandVal.setText(data.pHandVal);
            dHandVal.setText("");
            
            playButton.setDisable(false);
            foldButton.setDisable(false);
            
            addLogMessage("Cards dealt! You have: " + data.pHandVal);
            addLogMessage("Play or Fold?");
            
        } else if (data.buttonPressed == 2 || data.buttonPressed == 3) { // Play or Fold response
            // Reveal dealer cards
            setCardImage(d1, data.dCard1);
            setCardImage(d2, data.dCard2);
            setCardImage(d3, data.dCard3);
            dHandVal.setText(data.dHandVal);
            
            play.setText("$" + data.play);
            cash.setText("$" + data.cash);
            
            if (data.buttonPressed == 3) { // Folded
                addLogMessage("You folded. Lost: $" + (-data.winningsThisRound));
            } else { // Played
                addLogMessage("Dealer has: " + data.dHandVal);
                
                if (data.winner == 0) {
                    addLogMessage("Dealer does not qualify, ante pushed!");
                } else if (data.winner == 1) {
                    addLogMessage("YOU WIN! You earned $" + data.winningsThisRound);
                } else {
                    addLogMessage("Dealer wins. You lost $" + (-data.winningsThisRound));
                }
                
                // Check Pair Plus result
                if (clientPokerInfo.pairPlus > 0) {
                    if (data.winningsThisRound > 0) {
                        addLogMessage("Pair Plus paid!");
                    } else {
                        addLogMessage("No Pair Plus payout");
                    }
                }
            }
            
            addLogMessage("Total winnings: $" + data.cash);
            
            roundCount++;
            
            // Add delay before transitioning to end screen so player can see dealer cards
            new Thread(() -> {
                try {
                    Thread.sleep(7000);
                    Platform.runLater(() -> sendToEndScreen());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    @FXML
    private void handlePlay() {
        clientPokerInfo.buttonPressed = 2; // Play
        clientConnection.send(clientPokerInfo);
        
        playButton.setDisable(true);
        foldButton.setDisable(true);
        addLogMessage("You chose to play!");
    }
    
    @FXML
    private void handleFold() {
        clientPokerInfo.buttonPressed = 3; // Fold
        clientConnection.send(clientPokerInfo);
        
        playButton.setDisable(true);
        foldButton.setDisable(true);
        addLogMessage("You folded.");
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }
    
    @FXML
    private void handleFreshStart() {
        clientPokerInfo = new PokerInfo();
        roundCount = 0;
        returnToGame();
        addLogMessage("Fresh start! FUIYOHH Another one. Nicee");
    }
    
    @FXML
    private void handleNewLook() {
        themeApplied = true;
        if (isFirstLook) {
            gamePane.getStyleClass().remove("theme-green");
            gamePane.getStyleClass().add("theme-purple");
            setTextColor("white");
            addLogMessage("Theme changed to Purple Royale!");
        } else {
            gamePane.getStyleClass().remove("theme-purple");
            gamePane.getStyleClass().add("theme-green");
            setTextColor("white");
            addLogMessage("Theme changed back to Classic Green!");
        }
        isFirstLook = !isFirstLook;
    }
    
    private void setTextColor(String color) {
        String style = "-fx-fill: " + color + ";";
        ante.setStyle(style);
        pp.setStyle(style);
        play.setStyle(style);
        cash.setStyle(style);
        pHandVal.setStyle(style);
        dHandVal.setStyle(style);
        dealerCardLabel.setStyle(style);
        yourHandLabel.setStyle(style);
        anteLabel.setStyle(style);
        ppLabel.setStyle(style);
        playLabel.setStyle(style);
        winningsLabel.setStyle(style);
    }
    
    public void restoreThemeState(boolean savedIsFirstLook, boolean savedThemeApplied) {
        this.isFirstLook = savedIsFirstLook;
        this.themeApplied = savedThemeApplied;
        
        if (!themeApplied) {
            return;
        }

        if (!isFirstLook) {
            gamePane.getStyleClass().remove("theme-green");
            gamePane.getStyleClass().add("theme-purple");
            setTextColor("white");
        } else {
            gamePane.getStyleClass().remove("theme-purple");
            gamePane.getStyleClass().add("theme-green");
            setTextColor("white");
        }
    }

    private void sendToEndScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientEnd.fxml"));
            Parent root = loader.load();
            
            ClientEndController endController = loader.getController();
            endController.setGameController(this);
            endController.setPokerInfo(clientPokerInfo);
            endController.setThemeState(isFirstLook, themeApplied);
            
            Scene scene = new Scene(root, 1000, 700);
            Stage stage = (Stage) dealButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(false);
            
        } catch (Exception e) {
            e.printStackTrace();
            addLogMessage("Error loading end screen");
        }
    }
    
    public void returnToGame() {
        // Reset UI for new round
        ante.setText("$0");
        pp.setText("$0");
        play.setText("$0");
        cash.setText("$" + clientPokerInfo.cash);
        pHandVal.setText("");
        dHandVal.setText("");
        
        anteWagerField.setText("");
        ppWagerField.setText("");
        anteWagerField.setDisable(false);
        ppWagerField.setDisable(false);
        
        confirmButton.setDisable(false);
        dealButton.setDisable(true);
        playButton.setDisable(true);
        foldButton.setDisable(true);
        
        setCardImage(c1, "back");
        setCardImage(c2, "back");
        setCardImage(c3, "back");
        setCardImage(d1, "back");
        setCardImage(d2, "back");
        setCardImage(d3, "back");
        
        addLogMessage("New round! Place your bets.");
    }
}
