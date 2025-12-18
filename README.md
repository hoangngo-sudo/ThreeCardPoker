# Three Card Poker

> Two standalone Maven JavaFX apps, one is a server that runs the game logic and the other is a client players use to connect and play. The server shuffles and evaluates every hand while the client only sends inputs and renders the UI.

## Prerequisites

- Java 11+ and Maven 3.6+
- JavaFX Maven plugin

## Demo

## Project Layout

- server/
- client/

## Quick Start

1) Start the server

```
cd server
mvn clean compile exec:java
```

- Enter a port (default 5555) on the intro screen and click Start.
- The game screen shows connected client count and a live log. Use Enable/Disable to toggle the listener.

2) Start one or more clients using another terminal

```
cd client
mvn clean compile exec:java
```

- On the welcome screen, enter the server IP (127.0.0.1) and port, then Connect.
- Each client starts with $200. Up to 8 clients may play independently.

## How to Play

- **Betting**: Set an **Ante** between $5–$25 and an optional **Pair Plus** bet (0 or $5–$25). Click **Confirm Bets**.
- **Deal**: Click **Deal**. Ante and Pair Plus are deducted; your three cards flip up. Dealer cards stay face down.
- **Decision**: Choose **Play** or **Fold**.
  - Play: A **Play** wager equal to the ante is deducted and the dealer hand is revealed.
  - Fold: You forfeit the ante and any Pair Plus bet.
- **Dealer qualifies** with Queen-high or better; if not, ante and play are pushed back (Pair Plus still resolves).
- **Compare hands** (Straight Flush > Three of a Kind > Straight > Flush > Pair > High Card). If you win, ante and play pay 1:1; ties push.
- **Pair Plus** pays independently of the dealer:
  - Straight Flush 40:1
  - Three of a Kind 30:1
  - Straight 6:1
  - Flush 3:1
  - Pair 1:1
- **End screen**: After results, click **Continue** to see a summary with net win/loss and buttons to play another hand or exit.

## Client UI Tips

- **Fresh Start** resets cash to $200 and clears logs.
- **NewLook** toggles between the two provided themes; **Payouts** opens the payout table; **Exit** quits.

## Server Notes

- Each client connection runs on its own thread; synchronization guards client list updates and log writes.
- A new 52-card deck is shuffled for every deal; all scoring uses `ThreeCardLogic` on the server.
- Server can be stopped; connected clients will be notified and returned to their start screen.

## Testing

Run the bundled JUnit 5 suites (logic lives in the server project):

```
cd server
mvn test
```

The client project also includes a placeholder test harness:

```
cd client
mvn test
```
