import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ThreeCardLogic {
    
    public enum Hands {
        STRAIGHT_FLUSH(0, "Straight Flush"),
        THREE_KIND(1, "Three of a Kind"),
        STRAIGHT(2, "Straight"),
        FLUSH(3, "Flush"),
        PAIR(4, "Pair"),
        HIGH_CARD(5, "High Card");
        
        private int num;
        String name;
        
        Hands(int num, String name) {
            this.num = num;
            this.name = name;
        }
        
        public int getNum() {
            return num;
        }
        
        public String getName() {
            return name;
        }
    }
    
    // Returns the value of the highest card in the hand
    public static int highestCardVal(ArrayList<Card> hand) {
        int max = 0;
        for (Card card : hand) {
            if (card.getValue() > max) {
                max = card.getValue();
            }
        }
        return max;
    }
    
    // Get the name of a card value
    public static String getCardName(int value) {
        switch(value) {
            case 14: return "Ace";
            case 13: return "King";
            case 12: return "Queen";
            case 11: return "Jack";
            case 10: return "10";
            case 9: return "9";
            case 8: return "8";
            case 7: return "7";
            case 6: return "6";
            case 5: return "5";
            case 4: return "4";
            case 3: return "3";
            case 2: return "2";
            default: return "Unknown";
        }
    }
    
    // Get detailed hand description including high card information
    public static String getHandDescription(ArrayList<Card> hand) {
        Hands handType = evalHand(hand);
        
        if (handType == Hands.HIGH_CARD) {
            int highCard = highestCardVal(hand);
            return getCardName(highCard) + " High";
        } else {
            return handType.getName();
        }
    }
    
    // Evaluates a three card poker hand and returns what type of hand it is
    public static Hands evalHand(ArrayList<Card> hand) {
        if (hand == null || hand.size() != 3) {
            return Hands.HIGH_CARD;
        }
        
        boolean isFlush = isFlush(hand);
        boolean isStraight = isStraight(hand);
        boolean isThreeKind = isThreeOfAKind(hand);
        boolean isPair = isPair(hand);
        
        if (isFlush && isStraight) {
            return Hands.STRAIGHT_FLUSH;
        } else if (isThreeKind) {
            return Hands.THREE_KIND;
        } else if (isStraight) {
            return Hands.STRAIGHT;
        } else if (isFlush) {
            return Hands.FLUSH;
        } else if (isPair) {
            return Hands.PAIR;
        } else {
            return Hands.HIGH_CARD;
        }
    }
    
    // Evaluates Pair Plus winnings
    public static int evalPPWinnings(ArrayList<Card> hand, int bet) {
        Hands handType = evalHand(hand);
        
        switch (handType) {
            case STRAIGHT_FLUSH:
                return bet * 40;
            case THREE_KIND:
                return bet * 30;
            case STRAIGHT:
                return bet * 6;
            case FLUSH:
                return bet * 3;
            case PAIR:
                return bet * 1;
            default:
                return 0;
        }
    }
    
    // Checks if dealer qualifies Queen high or better
    public static boolean dealerQualifies(ArrayList<Card> dealer) {
        Hands handType = evalHand(dealer);
        
        // If dealer has a pair or better, they qualify
        if (handType != Hands.HIGH_CARD) {
            return true;
        }
        
        // Check if highest card is Queen or higher
        int highCard = highestCardVal(dealer);
        return highCard >= 12;
    }
    
    // Compares dealer and player hands
    // Returns: 2 if dealer wins, 1 if player wins, 0 if tie
    public static int compareHands(ArrayList<Card> dealer, ArrayList<Card> player) {
        Hands dealerHand = evalHand(dealer);
        Hands playerHand = evalHand(player);
        
        // Compare hand types first
        if (dealerHand.getNum() < playerHand.getNum()) {
            return 2; // Dealer wins
        } else if (playerHand.getNum() < dealerHand.getNum()) {
            return 1; // Player wins
        }
        
        // Same hand type, compare high cards
        return compareHighCards(dealer, player, dealerHand);
    }
    
    // Helper method to check if hand is a flush
    private static boolean isFlush(ArrayList<Card> hand) {
        char suit = hand.get(0).getSuit();
        return hand.get(1).getSuit() == suit && hand.get(2).getSuit() == suit;
    }
    
    // Helper method to check if hand is a straight
    private static boolean isStraight(ArrayList<Card> hand) {
        ArrayList<Integer> values = new ArrayList<>();
        for (Card card : hand) {
            values.add(card.getValue());
        }
        Collections.sort(values);
        
        // Check for normal straight
        if (values.get(2) - values.get(1) == 1 && values.get(1) - values.get(0) == 1) {
            return true;
        }
        
        // Check for A-2-3 straight (special case)
        if (values.get(0) == 2 && values.get(1) == 3 && values.get(2) == 14) {
            return true;
        }
        
        return false;
    }
    
    // Helper method to check if hand is three of a kind
    private static boolean isThreeOfAKind(ArrayList<Card> hand) {
        int val = hand.get(0).getValue();
        return hand.get(1).getValue() == val && hand.get(2).getValue() == val;
    }
    
    // Helper method to check if hand is a pair
    private static boolean isPair(ArrayList<Card> hand) {
        return hand.get(0).getValue() == hand.get(1).getValue() ||
               hand.get(0).getValue() == hand.get(2).getValue() ||
               hand.get(1).getValue() == hand.get(2).getValue();
    }
    
    // Compare high cards when hands are of same type
    private static int compareHighCards(ArrayList<Card> dealer, ArrayList<Card> player, Hands handType) {
        ArrayList<Integer> dealerVals = new ArrayList<>();
        ArrayList<Integer> playerVals = new ArrayList<>();
        
        for (Card card : dealer) {
            dealerVals.add(card.getValue());
        }
        for (Card card : player) {
            playerVals.add(card.getValue());
        }
        
        Collections.sort(dealerVals, Collections.reverseOrder());
        Collections.sort(playerVals, Collections.reverseOrder());
        
        // For pairs and three of a kind, compare the paired/triple value first
        if (handType == Hands.PAIR || handType == Hands.THREE_KIND) {
            int dealerPairVal = getPairValue(dealer);
            int playerPairVal = getPairValue(player);
            
            if (dealerPairVal > playerPairVal) {
                return 2;
            } else if (playerPairVal > dealerPairVal) {
                return 1;
            }
        }
        
        // Compare high cards in order
        for (int i = 0; i < 3; i++) {
            if (dealerVals.get(i) > playerVals.get(i)) {
                return 2; // Dealer wins
            } else if (playerVals.get(i) > dealerVals.get(i)) {
                return 1; // Player wins
            }
        }
        
        return 0; // Tie
    }
    
    // Get the value of the pair or triple
    private static int getPairValue(ArrayList<Card> hand) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card card : hand) {
            counts.put(card.getValue(), counts.getOrDefault(card.getValue(), 0) + 1);
        }
        
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 2) {
                return entry.getKey();
            }
        }
        
        return 0;
    }
}
