# Three Card Poker

A networked, multiplayer Three Card Poker game built with Java 11, JavaFX 19, and Maven. The application is split into two independently runnable programs: a server that hosts the game logic and manages all connected players, and a client that each player runs to connect, place bets, and interact with the game through a graphical user interface.

You can view the original wireframe for this project here: [wireframe.pdf](wireframe.pdf)

---

## Table of Contents

1. [Section 1: Purpose](#section-1--purpose)
2. [Section 2: High-Level Entities](#section-2--high-level-entities)
3. [Section 3: Low-Level Design](#section-3--low-level-design)
4. [Section 4: Benefits, Assumptions, and Risks](#section-4--benefits-assumptions-and-risks)
5. [Conclusion](#conclusion)
6. [How to Play](#how-to-play)
7. [Quick Start](#quick-start)
8. [Testing](#testing)

---

## Section 1: Purpose

The purpose of this project is to implement a fully functional, networked Three Card Poker game that supports multiple simultaneous players. The system is composed of two sub-systems: the **server** and the **client**.

The **server sub-system** is responsible for all authoritative game logic. It accepts incoming TCP connections from players, manages each player's game state in an isolated thread, evaluates poker hands using a dedicated logic engine, calculates winnings and losses according to standard Three Card Poker rules, and broadcasts results back to the respective client. It also provides a JavaFX administrative dashboard that displays the number of connected clients and a live timestamped activity log. The server operator can enable or disable the connection listener at any time without shutting down the process.

The **client sub-system** is a graphical front-end that a player runs on their own machine. It presents a multi-screen interface that guides the player through connecting to a server, placing bets, viewing dealt cards with animated flip transitions, making a Play or Fold decision, and reviewing the round result on a dedicated end screen. The client contains no authoritative game logic; it exclusively sends player actions to the server and renders the responses it receives.

Together, these two sub-systems form a complete application that demonstrates TCP socket networking, Java object serialization, multi-threaded server design, and JavaFX GUI development.

---

## Section 2: High-Level Entities

The system is organized around the following high-level entities.

**JavaFXTemplate** serves as the application entry point for both the server and the client. It extends `javafx.application.Application`, launches the primary stage, and holds static references that allow non-UI threads to interact with the UI safely.

**Server** is the central networking component on the server side. It owns a background accept thread (`TheServer`) that listens for incoming connections and spawns a dedicated `ClientThread` for each player that connects.

**ThreeCardLogic** is a stateless utility class that encapsulates all poker rules. It evaluates hand rankings, determines whether the dealer qualifies, compares the dealer's hand to the player's hand, and computes Pair Plus winnings.

**Client** is the networking thread on the client side. It opens a TCP socket to the server, reads `PokerInfo` objects from an `ObjectInputStream` in a loop, and dispatches each received object to a callback that updates the UI.

**PokerInfo** is the sole data transfer object used in all communication between the client and the server. It implements `Serializable` and carries every piece of game state needed for a round, including bet amounts, card strings, hand descriptions, winner flags, and cash balance.

**Controllers** are the JavaFX controller classes that manage each screen. On the server side these are `ServerStartController` and `ServerGameController`. On the client side these are `ClientStartController`, `ClientGameController`, and `ClientEndController`.

**Domain Models** such as `Card`, `Deck`, `Dealer`, and `Player`, represent the physical and logical objects of the poker game. These classes are duplicated in both the server and client modules so that each sub-system can compile independently.

---

## Section 3: Low-Level Design

### JavaFXTemplate

Both the server and client have their own `JavaFXTemplate` class. Each loads an initial FXML file to construct the first screen, sets the window to 1000 by 700 pixels, and marks it non-resizable. The server version additionally holds a static reference to `ServerGameController` so that background threads can post client-count updates to the UI via `Platform.runLater`. The client version holds a static `PokerInfo` instance that acts as shared state across screen transitions.

### Server

`Server` contains an inner class `TheServer` that extends `Thread`. When `startServer()` is called, `TheServer` begins accepting connections on the configured port. For each accepted socket it constructs a `ClientThread`, adds it to a synchronized `ArrayList<ClientThread>`, and starts it. `stopServer()` closes the server socket, which causes the accept loop to exit. `getClientCount()` is synchronized to return a safe count at any moment. Client removal is also synchronized to prevent concurrent modification.

`ClientThread` extends `Thread` and manages the full game lifecycle for one player. On startup it sends a welcome `PokerInfo` with `cash` set to 200 and `buttonPressed` set to 0. It then enters a read loop that blocks on `ObjectInputStream.readObject()`. Based on the `buttonPressed` value it received from the client, it routes execution to one of four game phases:

- When `buttonPressed` equals 1, it validates that the ante is between five and twenty-five dollars and that the Pair Plus is zero or between five and twenty-five dollars. It then calls `Dealer.dealHand()` twice to obtain three cards for the player and three for the dealer, calls `ThreeCardLogic.evalHand()` on the player's hand, deducts the bets from cash, and sends the player's cards and hand description back.
- When `buttonPressed` equals 2, it deducts the play wager, reveals the dealer's cards, checks dealer qualification, compares hands, computes Pair Plus winnings, and updates `cash` with the net result before sending the full result back.
- When `buttonPressed` equals 3, it treats the round as a fold, deducting only the ante and Pair Plus already placed, and sends back the updated cash with the dealer's cards exposed.
- When `buttonPressed` equals 4, it resets the player to a fresh `PokerInfo` with two hundred dollars.

### ThreeCardLogic

`ThreeCardLogic` is a pure static utility class with no instance state. `evalHand` inspects an `ArrayList<Card>` of three cards and returns a value from the inner `Hands` enum: `STRAIGHT_FLUSH`, `THREE_KIND`, `STRAIGHT`, `FLUSH`, `PAIR`, or `HIGH_CARD`. `evalPPWinnings` multiplies the Pair Plus bet by the payout multiplier for the detected hand. `dealerQualifies` checks whether the dealer's highest card is Queen (value 12) or better. `compareHands` returns 1 if the player wins, 2 if the dealer wins, and 0 for a tie by comparing hand rank first and then the highest card value as a tiebreaker.

### Client

`Client` extends `Thread` and takes a `Consumer<Serializable>` callback in its constructor. Its `run()` method opens a `Socket` to the given IP and port, wraps it in `ObjectOutputStream` and `ObjectInputStream`, and loops on `readObject()`. Each received `PokerInfo` is passed to the callback via `Platform.runLater` so the JavaFX UI thread handles the update. The `send(PokerInfo)` method writes a `PokerInfo` object to the output stream and flushes it. When the server closes the connection, the `IOException` caught in the read loop triggers `notifyServerClosed()`, which displays an information alert and navigates back to the start screen.

### ClientStartController

`ClientStartController` reads the IP address and port from two text fields. When the Connect button is pressed it constructs a `Client` with a callback that waits for the first `PokerInfo` from the server (the welcome message with `buttonPressed` equal to 0) and then loads `ClientGame.fxml`, passes the `Client` reference to `ClientGameController`, and replaces the scene on the primary stage. The `hasTransitioned` flag ensures this scene switch happens exactly once.

### ClientGameController

`ClientGameController` is the most complex controller, managing the full in-game experience across approximately 536 lines. It maintains `clientPokerInfo` as the local copy of game state, `roundCount` to track rounds played, `animationInProgress` to block user actions during card flip animations, and `savedLogMessages` to restore the game log after screen transitions.

Card flip animations are constructed with two chained `ScaleTransition` instances (each 150 ms) that shrink the card's X scale to zero, swap the image source, and then expand back to one, producing a flip effect. A `PauseTransition` staggers each card by 500 ms so the three cards reveal sequentially. All animations are grouped in a `SequentialTransition` whose `setOnFinished` handler re-enables the action buttons. The `handlePlay()` method performs an optimistic UI update, deducting the play wager from the displayed cash immediately before the server confirms the result. The `handleFreshStart()` method is blocked while an animation is in progress. The theme toggle between a green and maroon style sheet is tracked with `isFirstLook` and `themeApplied` booleans that are passed to `ClientEndController` and restored when returning to the game screen.

### ClientEndController

`ClientEndController` receives a `PokerInfo` and a reference to the originating `ClientGameController` and displays the round summary. The net result text is colored green for a win, red for a loss or fold, and yellow for a push. The Return to Game button reloads `ClientGame.fxml`, restores the theme state, re-binds the existing `Client` thread to the new controller, and replaces the scene. The Exit button closes the application.

### Domain Models

`Card` stores a `char` suit (C, D, H, or S) and an `int` value (2 through 14, where 14 is Ace). `Deck` extends `ArrayList<Card>` and its `newDeck()` method populates and shuffles a 52-card deck using `Collections.shuffle`. `Dealer` owns a `Deck` instance and an `ArrayList<Card>` for the dealer's hand; its `dealHand()` method removes and returns three cards from the top, reshuffling automatically when fewer than three cards remain. `Player` holds the hand, the three bet amounts, and the total winnings for the session.

### PokerInfo

`PokerInfo` implements `Serializable` with a fixed `serialVersionUID` so that objects serialized by one JVM can be deserialized by another even if minor class changes are made. It carries integer fields for `ante`, `pairPlus`, `play`, `cash`, `winningsThisRound`, `buttonPressed`, and `winner`, boolean flags for reserved game-state fields, and six `String` fields for the card identifiers (for example "AH" or "10C") for both the player and dealer hands, plus string descriptions of each hand's value.

---

## Section 4 — Benefits, Assumptions, and Risks

### Benefits

The one-thread-per-client model on the server is simple to reason about and debug. Each `ClientThread` handles exactly one player with straightforward sequential logic, which makes it easy to trace a bug to a specific game phase without needing to track concurrent state across multiple players. Java's built-in object serialization eliminates the need for an external JSON or Protocol Buffers library, keeping the dependency footprint minimal. JavaFX's `Platform.runLater` makes it safe to update the UI from background threads without additional locking. The use of a single `PokerInfo` DTO for all messages simplifies the communication contract: both sides only need to know one class.

### Assumptions

The design assumes that the number of simultaneous players will remain small enough that one thread per client is acceptable. It also assumes that both the server and the client are running on the same local area network or on localhost, which means network latency is negligible and the optimistic UI update in `handlePlay()` will appear correct to the player in practice. The project assumes Java 11 and Maven are installed on the host machine, as there is no bundled JRE. It also assumes that the serialized form of `PokerInfo` does not change between the server and client builds, since both modules compile the class independently.

### Risks and Issues

The primary scalability risk is that the one-thread-per-client model does not scale to a large number of concurrent players. Each thread consumes stack memory and an OS-level thread handle, so a server with hundreds of clients would exhaust resources. A production system would use Java NIO with a selector-based event loop instead.

Code duplication is a maintenance risk. The domain model classes `Card`, `Deck`, `Dealer`, `Player`, and `PokerInfo` exist in both the `server` and `client` source trees. If a bug is fixed or a field is added in one copy, the developer must remember to update the other copy, and the serialized form must remain compatible or deserialization will fail at runtime with a `InvalidClassException`.

There is no reconnection mechanism. If the server restarts or the network drops, the client is sent back to the start screen and loses the current game session and cash balance, with no way to resume.

Bet validation is performed only on the server side. The client does not validate the ante and Pair Plus values before sending them, which means a malformed request will be silently ignored by the server rather than giving the player an immediate error message in the UI.

---

## Conclusion

The hardest part of this project was coordinating the asynchronous communication between the client thread and the JavaFX application thread in a way that kept the UI consistent at all times. JavaFX is single-threaded by design, meaning that any attempt to modify a UI node from the background `Client` thread throws an `IllegalStateException`. This forces every server response to be dispatched through `Platform.runLater`, which introduces a subtle ordering problem: by the time the lambda runs on the application thread, the local variable it captured may have already been overwritten by the next incoming message. Debugging this required carefully understanding when state was being read versus when it was being mutated, and adding flags like `animationInProgress` and `hasTransitioned` to guard critical transitions. The card flip animation added another layer of complexity because buttons had to be disabled precisely for the duration of the animation sequence and re-enabled in the `setOnFinished` callback, and any mistake in that timing caused the player to be able to send a game action before the UI had fully updated to reflect the previous server response.

---

## How to Play

### Objective

The goal of Three Card Poker is to build a better three-card poker hand than the dealer using the hand rankings below. Players also have the option of placing a Pair Plus side bet that pays out regardless of whether they beat the dealer.

### Hand Rankings (highest to lowest)

1. **Straight Flush** Three cards of the same suit in consecutive order (for example, 7, 8, and 9 of hearts).
2. **Three of a Kind** Three cards of the same rank (for example, three Queens).
3. **Straight** Three cards in consecutive order of any suit (for example, 4 of clubs, 5 of diamonds, 6 of spades).
4. **Flush** Three cards of the same suit in any order (for example, 2, 7, and King of clubs).
5. **Pair** Two cards of the same rank (for example, two Jacks).
6. **High Card** None of the above combinations.

### Pair Plus Payouts

The Pair Plus bet is evaluated independently of the main game outcome. If your three-card hand is a Pair or better, you win regardless of what the dealer holds.

| Hand | Payout |
|---|---|
| Straight Flush | 40 to 1 |
| Three of a Kind | 30 to 1 |
| Straight | 6 to 1 |
| Flush | 3 to 1 |
| Pair | 1 to 1 |

### Step-by-Step Rules

**Step 1: Connect.** Launch the client application, enter the server IP address and port number, and click Connect and Play. Each new session begins with $200 in cash.

**Step 2: Place Your Bets.** Enter an Ante wager between $5 and $25. You may also enter a Pair Plus wager of $5 to $25, or leave it at $0 to skip the side bet. When you are satisfied with your bets, click Confirm Bets.

**Step 3: Deal.** Click the Deal button. The Ante and Pair Plus amounts are deducted from your cash immediately. Your three cards are revealed one at a time with a flip animation. The dealer's three cards remain face down.

**Step 4: Make Your Decision.** After your cards are fully visible, you must choose one of two actions.

Choosing **Play** places an additional Play wager equal to your Ante and deducts it from your cash. The dealer's cards are then revealed and the round is resolved.

Choosing **Fold** forfeits your Ante and any Pair Plus bet. No Play wager is placed. The dealer's cards are revealed but the outcome does not affect your balance.

**Step 5: Dealer Qualification.** For the main Ante and Play bets to be contested, the dealer must hold a hand of Queen-high or better. If the dealer does not qualify, your Ante and Play wagers are returned to you as a push and the round ends. The Pair Plus bet is still evaluated normally regardless of whether the dealer qualifies.

**Step 6: Compare Hands.** If the dealer qualifies, the hands are compared using the rankings above. If you win, your Ante and Play bets each pay 1 to 1, meaning you receive your wager back plus an equal amount in winnings. If the dealer wins, both bets are lost. If the hands tie, the Ante and Play wagers are pushed back.

**Step 7: View Results.** After the round is resolved, the game transitions to the Result Screen showing a breakdown of every bet and the net amount won or lost. Click Play Again to return to the game for another hand, or click Exit Game to close the application.

### Additional Options

You can access additional features at any time through the Options menu in the top corner of the game screen. Selecting Fresh Start resets your cash to $200 and clears the game log so you can begin a completely new session. Selecting New Look toggles the visual theme of the interface between green and maroon. Selecting Payouts opens a separate window that displays the full Pair Plus payout table for quick reference. Selecting Exit closes the client application.

---

## Quick Start

### Prerequisites

Java 11 or higher and Maven 3.6 or higher must be installed and available on your system path.

### Starting the Server

Open a terminal, navigate to the `server` directory, and run the following command.

```
cd server
mvn javafx:run
```

On the Server Intro Screen, enter a port number (the default is 5555) and click Start Server. The Server Dashboard will appear showing the number of connected clients and a timestamped log of activity. Use the Enable Server and Disable Server buttons to start and stop accepting new connections without exiting the application.

### Starting the Client

Open a separate terminal for each player, navigate to the `client` directory, and run the following command.

```
cd client
mvn javafx:run
```

On the Client Intro Screen, enter the server's IP address (use 127.0.0.1 if running on the same machine) and the port number the server was started on, then click Connect and Play.

---

## Testing

The server module contains a full JUnit 5 test suite covering the domain models and game logic. Run it with the following command from the `server` directory.

```
cd server
mvn test
```

The client module includes a placeholder test class that can be expanded with additional UI and integration tests. Run it with the following command from the `client` directory.

```
cd client
mvn test
```
## License
MIT