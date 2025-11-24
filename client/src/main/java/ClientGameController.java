import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;

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
    private static ObservableList<String> savedLogMessages = FXCollections.observableArrayList();
    private static int roundCount = 0;
    private boolean isFirstLook = true;
    private boolean themeApplied = false;
    private boolean animationInProgress = false;

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

        // Only print the Welcome message to the Game Log on the very first round
        if (roundCount == 0) {
            addLogMessage("Welcome to 3 Card Poker!");
            addLogMessage("Place your bets to begin.");
        }
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
        // Check if button is in "Continue" mode
        if (confirmButton.getText().equals("Continue")) {
            sendToEndScreen();
            return;
        }
        
        // Otherwise, handle bet confirmation
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
            
            // Check if player has enough cash
            int totalBet = anteValue + ppValue;
            if (clientPokerInfo.cash < totalBet) {
                addLogMessage("Not enough cash! You have $" + clientPokerInfo.cash);
                return;
            }
            
            // Check if player is out of money
            if (clientPokerInfo.cash <= 0) {
                addLogMessage("Out of money! Please start a new game.");
                confirmButton.setDisable(true);
                dealButton.setDisable(true);
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
        
        if (data.buttonPressed == 0) {
            cash.setText("$" + data.cash);
            clientPokerInfo.cash = data.cash;
        } else if (data.buttonPressed == 1) { // Response to deal
            // Update cash display (ante + pair plus deducted)
            cash.setText("$" + data.cash);
            
            animationInProgress = true;
            
            // Flip player cards one by one into their actual values
            // Small stagger for a "dealing" effect (0ms, 500ms, 1000ms)
            SequentialTransition playerCardsAnimation = new SequentialTransition();
            playFlipAnimation(c1, data.card1, 0, playerCardsAnimation);
            playFlipAnimation(c2, data.card2, 500, playerCardsAnimation);
            playFlipAnimation(c3, data.card3, 1000, playerCardsAnimation);

            
            // Show dealer cards face down initially
            setCardImage(d1, "back");
            setCardImage(d2, "back");
            setCardImage(d3, "back");
            
            pHandVal.setText(data.pHandVal);
            dHandVal.setText("");
            
            // Enable buttons after animation completes (1500ms: 1000ms delay + 300ms for last card flip)
            PauseTransition enableButtons = new PauseTransition(Duration.millis(1500));
            enableButtons.setOnFinished(e -> {
                playButton.setDisable(false);
                foldButton.setDisable(false);
                animationInProgress = false;
            });
            enableButtons.play();
            
            addLogMessage("Ante ($" + clientPokerInfo.ante + ") and pair plus ($" + clientPokerInfo.pairPlus + ") deducted");
            addLogMessage("Cards dealt! You have " + data.pHandVal);
            addLogMessage("Play or Fold?");
            
        } else if (data.buttonPressed == 2 || data.buttonPressed == 3) { // Play or Fold response
            animationInProgress = true;
            
            // Reveal dealer cards
            SequentialTransition dealerCardsAnimation = new SequentialTransition();
            playFlipAnimation(d1, data.dCard1, 0, dealerCardsAnimation);
            playFlipAnimation(d2, data.dCard2, 500, dealerCardsAnimation);
            playFlipAnimation(d3, data.dCard3, 1000, dealerCardsAnimation);

            dHandVal.setText(data.dHandVal);

            
            // Update final cash from server
            cash.setText("$" + data.cash);
            
            if (data.buttonPressed == 3) { // Folded
                addLogMessage("You folded. You lost $" + (data.ante + data.pairPlus));
            } else { // Played
                addLogMessage("Dealer has " + data.dHandVal);
                
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
                        addLogMessage("Pair plus paid!");
                    } else {
                        addLogMessage("No pair plus payout");
                    }
                }
            }
            
            addLogMessage("Total cash: $" + data.cash);
            addLogMessage("Click Continue to see results.");
            
            roundCount++;
            
            // Enable Continue button after animation finishes (1500ms: 1000ms delay + 300ms for last card flip)
            PauseTransition enableContinue = new PauseTransition(Duration.millis(1500));
            enableContinue.setOnFinished(e -> {
                confirmButton.setText("Continue");
                confirmButton.setDisable(false);
                animationInProgress = false;
            });
            enableContinue.play();
        }
    }
    
    @FXML
    private void handlePlay() {
        clientPokerInfo.buttonPressed = 2; // Play
        
        // Show play wager immediately (equal to ante)
        int playWager = clientPokerInfo.ante;
        play.setText("$" + playWager);
        
        // Deduct play wager from cash immediately
        clientPokerInfo.cash -= playWager;
        cash.setText("$" + clientPokerInfo.cash);
        
        clientConnection.send(clientPokerInfo);
        
        playButton.setDisable(true);
        foldButton.setDisable(true);
        addLogMessage("You chose to play! Play wager ($" + playWager + ") deducted");
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
        // Prevent fresh start during animations
        if (animationInProgress) {
            addLogMessage("Please wait for animations to complete.");
            return;
        }
        
        // Send fresh start signal to server
        PokerInfo freshStartSignal = new PokerInfo();
        freshStartSignal.buttonPressed = 4; // Fresh start
        clientConnection.send(freshStartSignal);
        
        clientPokerInfo = new PokerInfo();
        roundCount = 0;
        savedLogMessages.clear();
        returnToGame(false);
        addLogMessage("Fresh start! FUIYOHH Another one. Nicee");
        addLogMessage("Place your bets to begin.");
    }
    
    @FXML
    private void handleNewLook() {
        themeApplied = true;
        if (isFirstLook) {
            gamePane.getStyleClass().remove("theme-green");
            gamePane.getStyleClass().add("theme-purple");
            setTextColor("white");
            addLogMessage("Theme changed to purple color!");
        } else {
            gamePane.getStyleClass().remove("theme-purple");
            gamePane.getStyleClass().add("theme-green");
            setTextColor("white");
            addLogMessage("Theme changed back to green color!");
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

            savedLogMessages.setAll(logMessages);
            
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
        returnToGame(true);
    }

    public void returnToGame(boolean showMessage) {
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
        
        confirmButton.setText("Confirm Bets");
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

        logMessages.setAll(savedLogMessages);
        listItems2.setItems(logMessages);
        if (showMessage) {
            addLogMessage("New round! Place your bets.");
        }
    }
    
    public void updateCashDisplay(int cashAmount) {
        cash.setText("$" + cashAmount);
    }

    // Flip-card animation helper: shrinks card, swaps image, then expands.
    private void playFlipAnimation(ImageView cardView, String finalCardString, double delayMillis, SequentialTransition parent) {
        // Ensure we start from normal scale
        cardView.setScaleX(1.0);

        // Delay before animation starts
        PauseTransition delay = new PauseTransition(Duration.millis(delayMillis));

        // Shrink horizontally (simulate turning sideways)
        ScaleTransition shrink = new ScaleTransition(Duration.millis(150), cardView);
        shrink.setFromX(1);
        shrink.setToX(0);

        // After we are "edge-on", swap to the final card image
        shrink.setOnFinished(e -> setCardImage(cardView, finalCardString));

        // Expand back to full width (now showing the new image)
        ScaleTransition expand = new ScaleTransition(Duration.millis(150), cardView);
        expand.setFromX(0);
        expand.setToX(1);

        SequentialTransition seq = new SequentialTransition(delay, shrink, expand);
        seq.play();
    }

    // method that returns client thread to start screen when server is disconnected
    public void returnToStartAfterDisconnect() {
        try {
            // Send pop up alert to client
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Disconnected");
            alert.setHeaderText(null);
            alert.setContentText("Server connection lost\nReturning to the start screen");
            alert.getDialogPane().setPrefSize(300, 150);
            alert.setResizable(false);
            alert.showAndWait();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("ClientStart.fxml"));
            Parent root = loader.load();

            ClientStartController startController = loader.getController();
            startController.setClient(clientConnection);

            // Switch scenes
            Stage stage = (Stage) cash.getScene().getWindow();
            Scene scene = new Scene(root);
            Platform.runLater(() -> stage.setScene(scene));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
