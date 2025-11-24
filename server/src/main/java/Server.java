import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Server {
    int count = 1;
    ArrayList<ClientThread> clients = new ArrayList<>();
    TheServer server;
    private Consumer<Serializable> callback;
    int port;
    public boolean isRunning = false;
    
    public Server(Consumer<Serializable> call) {
        callback = call;
        this.port = 5555;
    }
    
    public Server(Consumer<Serializable> call, String text) {
        callback = call;
        try {
            this.port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            this.port = 5555;
        }
    }
    
    public void startServer() {
        if (!isRunning) {
            server = new TheServer();
            server.start();
            isRunning = true;
        }
    }
    
    public void stopServer() {
        if (isRunning) {
            try {
                isRunning = false;
                for (ClientThread client : clients) {
                    try {
                        client.connection.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                clients.clear();
                count = 1;      // reset client count when server is disabled
                if (server != null && server.mysocket != null) {
                    server.mysocket.close();
                }
                callback.accept("Server stopped");
            } catch (Exception e) {
                callback.accept("Error stopping server: " + e.getMessage());
            }
        }
    }
    
    public synchronized int getClientCount() {
        return clients.size();
    }
    
    // Synchronized method to log messages in proper order
    private synchronized void logMessage(String message) {
        callback.accept(message);
    }
    
    // Synchronized method to remove client
    private synchronized void removeClient(ClientThread client) {
        clients.remove(client);
        JavaFXTemplate.updateClientCount(clients.size());
    }
    
    class TheServer extends Thread {
        ServerSocket mysocket;
        
        public void run() {
            try {
                mysocket = new ServerSocket(port);
                Server.this.logMessage("Server is waiting for a client on port " + port);
                
                while (isRunning) {
                    ClientThread c = new ClientThread(mysocket.accept(), count);
                    synchronized (Server.this) {
                        callback.accept("Client " + count + " has connected to server");
                        clients.add(c);
                        count++;
                        JavaFXTemplate.updateClientCount(clients.size());
                    }
                    c.start();
                }
            } catch (Exception e) {
                if (isRunning) {
                    Server.this.logMessage("Server socket did not launch: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
    
    class ClientThread extends Thread {
        Socket connection;
        int count;
        ObjectInputStream in;
        ObjectOutputStream out;
        Player player;
        Dealer dealer;
        
        ClientThread(Socket s, int count) {
            this.connection = s;
            this.count = count;
            this.player = new Player();
            this.dealer = new Dealer();
        }
        
        public void run() {
            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                in = new ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);
                
                // Send initial welcome message to client
                PokerInfo welcome = new PokerInfo();
                welcome.buttonPressed = 0; // 0 means ready to play
                welcome.cash = 200; // Starting cash for each player
                out.writeObject(welcome);
                out.reset();
                Server.this.logMessage("Client " + count + " sent welcome message with $200 starting cash");
            } catch (Exception e) {
                Server.this.logMessage("Streams not open for client " + count);
                return;
            }
            
            while (true) {
                try {
                    PokerInfo data = (PokerInfo) in.readObject();
                    
                    // Process the game based on button pressed
                    if (data.buttonPressed == 1) { // Deal button
                        handleDeal(data);
                    } else if (data.buttonPressed == 2) { // Play button
                        handlePlay(data);
                    } else if (data.buttonPressed == 3) { // Fold button
                        handleFold(data);
                    } else if (data.buttonPressed == 4) { // Fresh start
                        handleFreshStart(data);
                    }
                    
                    out.writeObject(data);
                    out.reset();
                    
                } catch (Exception e) {
                    Server.this.logMessage("Client " + count + " disconnected");
                    Server.this.removeClient(this);
                    break;
                }
            }
        }
        
        private void handleDeal(PokerInfo data) {
            // Validate bets
            if (data.ante < 5 || data.ante > 25) {
                Server.this.logMessage("Client " + count + " has invalid ante bet: $" + data.ante);
                return;
            }
            
            if (data.pairPlus > 0 && (data.pairPlus < 5 || data.pairPlus > 25)) {
                Server.this.logMessage("Client " + count + " has invalid pair plus bet: $" + data.pairPlus);
                return;
            }
            
            // Deduct ante and pair plus from player's cash
            int totalBet = data.ante + data.pairPlus;
            player.setTotalWinnings(data.cash - totalBet);
            data.cash = player.getTotalWinnings();
            
            // Set player bets
            player.setAnteBet(data.ante);
            player.setPairPlusBet(data.pairPlus);
            
            // Deal cards
            dealer.theDeck.newDeck();
            ArrayList<Card> playerHand = dealer.dealHand();
            ArrayList<Card> dealerHand = dealer.dealHand();
            
            player.setHand(playerHand);
            dealer.setDealersHand(dealerHand);
            
            // Send cards to client
            data.card1 = playerHand.get(0).toString();
            data.card2 = playerHand.get(1).toString();
            data.card3 = playerHand.get(2).toString();
            data.dCard1 = dealerHand.get(0).toString();
            data.dCard2 = dealerHand.get(1).toString();
            data.dCard3 = dealerHand.get(2).toString();
            
            // Evaluate hands
            data.pHandVal = ThreeCardLogic.getHandDescription(playerHand);
            data.dHandVal = ThreeCardLogic.getHandDescription(dealerHand);
            
            Server.this.logMessage("Client " + count + " has dealt Ante = $" + data.ante + 
                          ", Pair Plus = $" + data.pairPlus + ". Client " + count + " has " + data.pHandVal);
        }
        
        private void handlePlay(PokerInfo data) {
            // Deduct play wager from player's cash (equal to ante)
            player.setPlayBet(player.getAnteBet());
            data.play = player.getPlayBet();
            player.setTotalWinnings(player.getTotalWinnings() - data.play);
            
            int winnings = 0;
            int netEarnings = 0; // Track net profit/loss for display
            ArrayList<Card> playerHand = player.getHand();
            ArrayList<Card> dealerHand = dealer.getDealersHand();
            
            // Evaluate Pair Plus first
            if (player.getPairPlusBet() > 0) {
                int ppWinnings = ThreeCardLogic.evalPPWinnings(playerHand, player.getPairPlusBet());
                if (ppWinnings > 0) {
                    winnings += ppWinnings + player.getPairPlusBet(); // Return bet + winnings
                    netEarnings += ppWinnings; // Net profit from PP
                    Server.this.logMessage("Client " + count + " won pair plus: $" + ppWinnings + " (total payout: $" + (ppWinnings + player.getPairPlusBet()) + ")");
                } else {
                    netEarnings -= player.getPairPlusBet(); // Lost PP bet
                    Server.this.logMessage("Client " + count + " lost pair plus bet: $" + player.getPairPlusBet());
                }
            }
            
            // Check if dealer qualifies
            boolean dealerQualifies = ThreeCardLogic.dealerQualifies(dealerHand);
            
            if (!dealerQualifies) {
                // Dealer doesn't qualify
                // Return both play wager and ante wager (pushed)
                winnings += player.getAnteBet() + player.getPlayBet();
                // netEarnings stays 0 for ante/play (push)
                Server.this.logMessage("Client " + count + " dealer does not qualify. Ante ($" + player.getAnteBet() + ") and play ($" + player.getPlayBet() + ") returned");
                data.winner = 0; // Indicate dealer didn't qualify
            } else {
                // Compare hands
                int result = ThreeCardLogic.compareHands(dealerHand, playerHand);
                data.winner = result;
                
                if (result == 1) { // Player wins
                    // Return original bets + winnings (1 to 1)
                    int totalPayout = (player.getAnteBet() + player.getPlayBet()) * 2;
                    winnings += totalPayout;
                    netEarnings += player.getAnteBet() + player.getPlayBet(); // Net profit
                    Server.this.logMessage("Client " + count + " player wins! Payout: $" + totalPayout);
                } else if (result == 2) { // Dealer wins
                    // Ante and play already deducted, no refund
                    netEarnings -= (player.getAnteBet() + player.getPlayBet()); // Lost both
                    Server.this.logMessage("Client " + count + " dealer wins. Client " + count + " lost ante ($" + player.getAnteBet() + ") and play ($" + player.getPlayBet() + ")");
                } else { // Tie
                    // Return both bets
                    winnings += player.getAnteBet() + player.getPlayBet();
                    // netEarnings stays 0 (push)
                    Server.this.logMessage("Client " + count + " has tie game. Ante and play returned");
                }
            }
            
            player.setTotalWinnings(player.getTotalWinnings() + winnings);
            data.winningsThisRound = netEarnings; // Use net earnings for display
            data.cash = player.getTotalWinnings();
            
            Server.this.logMessage("Client " + count + " total cash: $" + data.cash);
        }
        
        private void handleFold(PokerInfo data) {
            // Ante and Pair Plus already deducted during deal
            // No refunds when folding
            data.cash = player.getTotalWinnings();
            data.winningsThisRound = 0; // No additional change
            
            Server.this.logMessage("Client " + count + " folded. Client " + count + " lost ante ($" + player.getAnteBet() + 
                                 ") and pair plus ($" + player.getPairPlusBet() + "). Total cash: $" + data.cash);
        }
        
        private void handleFreshStart(PokerInfo data) {
            // Reset player and dealer state
            player = new Player();
            dealer = new Dealer();
            
            // Send fresh welcome message with starting cash
            data.buttonPressed = 0;
            data.cash = 200;
            data.ante = 0;
            data.pairPlus = 0;
            data.play = 0;
            data.winningsThisRound = 0;
            
            Server.this.logMessage("Client " + count + " started fresh game with $200");
        }
    }
}
