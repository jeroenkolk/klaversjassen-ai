Project: Klavers Jassen Trainer (Rotterdam rules)

1. Overview
- Goal: Provide an HTTP API that returns the best card to play for a Klaverjassen trick given the current game context, using an AI model service.
- Scope phase 1: Define requirements, domain model, and BDD feature scenarios so development can start. Add a simple training facility to build a local model artifact (model.txt) using an external API for supervision.

2. Terminology and domain
- Suits: Clubs (C), Diamonds (D), Hearts (H), Spades (S).
- Ranks (non-trump order high→low): A, K, Q, J, 10, 9, 8, 7.
- Ranks (trump order high→low): J, 9, A, 10, K, Q, 8, 7.
- Players: 4 players, fixed seating P0..P3 clockwise. Partnerships: (P0,P2) vs (P1,P3).
- Trick: Each player plays exactly one card per trick, following Rotterdam rule obligations.
- Trump (Atout): One suit designated as trump for the hand.

3. Rotterdam rule obligations (high-level)
- Follow suit if you can: If you hold cards of the led suit, you must play one of them.
- If you cannot follow suit and trump has been played in the trick already by an opponent, you must overtrump if you can; otherwise, you must play a trump if you have one; otherwise, play any card.
- If your partner is currently winning the trick (with highest card so far) and you cannot follow suit, you do not have to overtrump; you may discard (play any non-trump) unless you are void in all non-trump and hold trump only, then play trump.
- Trump ranking and non-trump ranking as listed above determine “winning” card of the trick.
- Additional nuances (to be optionally added later): signaling, ten/A protection, counting points, endgame optimization. For v1 the AI can be a black-box scorer that selects the best legal card under these obligations.

4. Functional requirements
FR-1 API endpoint
- Method/Path: POST /v1/best-card
- Purpose: Return the best legal card to play in the current trick.

FR-2 Request payload fields
- hand: array of cards the player currently holds (2–8 depending on stage).
- table: array of cards already played in the current trick in play order, with player positions.
- history: array of finished tricks (optional for v1): each trick includes starting player, sequence of 4 plays, winner, and points if available.
- trump: suit (C|D|H|S).
- playerPosition: integer 0..3; the caller’s seat index.
- leaderPosition: integer 0..3; who led the current trick (for table[0]).
- partnerPosition: integer 0..3; partner seat (derived by (player+2)%4 but included for clarity).
- scoringMode (optional): simple|points|winTrick; default simple.

FR-3 Response payload fields
- bestCard: card (rank+suit) that is legal and maximizes model score/utility.
- candidates: array of up to topK cards with scores (descending), include legality flag and brief reason when available.
- legalCards: array of legal cards available under rules (for debugging/clients).
- model: metadata of model used (name, version).
- requestId: echo from request.

FR-4 Card representation
- rank: one of [A,K,Q,J,10,9,8,7]
- suit: one of [C,D,H,S]
- compact notation: e.g., "JH" (Jack of Hearts), "10S".

FR-5 Legality enforcement
- The service must compute legalCards from hand, table, trump, leaderPosition, partnerPosition, and rule obligations. The bestCard MUST be one of legalCards even if the external AI suggests otherwise.

FR-6 External model integration
- The service will call an external AI Scoring API (spec TBD) to obtain a score per candidate card given the full game context.
- If the model call fails or is unavailable, the service must fall back to a deterministic heuristic baseline selecting a legal card (e.g., lowest winning card if must win, else lowest discard). The concrete heuristic can be implemented later; for now, requirement is to return a legal card with an explanation "fallback".

FR-7 Validation and errors
- 400 Bad Request when:
  - hand is empty or contains duplicates.
  - table contains invalid size (>3) or duplicates with hand/history conflicts.
  - trump invalid.
  - positions invalid (not 0..3) or inconsistent with table ordering.
  - illegal state (e.g., table non-empty but leaderPosition not provided).
- 422 Unprocessable Entity when no legal move exists due to inconsistent state.
- 503 Service Unavailable when external model is required (scoringMode simple may still use fallback) and no fallback is allowed by client.

FR-8 Observability
- Include requestId; log inputs (sanitized), derived legalCards, chosen bestCard, model scores, and timing.

5. Non-functional requirements
- Performance: < 150 ms p95 for local inference with fallback; < 600 ms p95 including external API round-trip (LAN). Timeouts configurable.
- Reliability: Graceful degradation to fallback when model unavailable.
- Security: No PII; reject oversized payloads (>64KB). Validate strictly.
- Config: External API base URL, API key, timeouts in application.yml under klaverjas.model.*.

6. Data contracts (JSON)
Request example
{
  "hand": ["JH", "9H", "AC", "10S", "7D", "KH", "QC", "8S"],
  "table": [
    {"player": 1, "card": "AH"},
    {"player": 2, "card": "7H"}
  ],
  "history": [
    {
      "leader": 3,
      "plays": [
        {"player": 3, "card": "10C"},
        {"player": 0, "card": "AC"},
        {"player": 1, "card": "7C"},
        {"player": 2, "card": "KC"}
      ],
      "winner": 0,
      "points": 16
    }
  ],
  "trump": "H",
  "playerPosition": 0,
  "leaderPosition": 1,
  "partnerPosition": 2,
  "scoringMode": "simple",
  "topK": 3,
  "requestId": "abc-123"
}

Response example
{
  "bestCard": "JH",
  "candidates": [
    {"card": "JH", "score": 0.82, "reason": "overtrump wins"},
    {"card": "9H", "score": 0.61},
    {"card": "KH", "score": 0.40}
  ],
  "legalCards": ["JH", "9H", "KH"],
  "model": {"name": "kj-rotterdam-small", "version": "0.1.0"},
  "requestId": "abc-123"
}

7. Legal card derivation (algorithm sketch)
- Input: hand, table, trump, leaderPosition, partnerPosition, currentPlayer.
- Steps:
  1) If table empty: legalCards = hand.
  2) Else let ledSuit = suit(table[0].card).
     - If hand contains any ledSuit: legalCards = all cards of ledSuit.
     - Else (void in ledSuit):
       a) Determine currently winning card on table (using trump order when trump present).
       b) If partner is currently winning: legalCards =
          - any non-trump from hand; if none, any card.
       c) If partner not winning:
          - If hand contains trump:
             i) If an opponent played trump already: legalCards = trumps that overtrump if any, otherwise all trumps.
            ii) If no trump on table yet: legalCards = all trumps.
          - Else: any card.

8. Acceptance criteria
- AC-1: For a set of canonical scenarios (see feature file), the legalCards computed must match expectations.
- AC-2: The API never returns a card outside legalCards.
- AC-3: When the external model is unavailable, the API returns a legal card with reason indicating fallback.
- AC-4: Input validation errors return appropriate HTTP codes and messages.

9. External AI API (placeholder)
- Endpoint: configurable; will accept a game state and return scores for candidate cards.
- Contract TBD; adapter will be implemented behind an interface ModelClient with method scoreCandidates(state, candidates) -> list of (card, score, reason?).

10. Training (phase 1a)
- Purpose: Produce a local model file (model.txt) that can be loaded at runtime to score cards without calling the external API.
- Source of supervision: External API call
  - POST https://api.klaversjassen.nl/api/v1/calcAiCard
  - Body: {"currentTrick": ["..."], "trumpSuit": "...", "hand": ["..."], "gameVariant": "amsterdams"}
  - Response: "<bestCard>" (e.g., "JH")
- Training driver
  - Spring profile: train
  - Component: CommandLineRunner that generates random game states (hand, current trick, trump), queries the API for bestCard labels, and trains a lightweight neural network.
  - Parameters (defaults): generations=1000, gamesPerGeneration=500, learningRate configurable in application.yml.
  - Constraints: Use only Java/Spring (no third-party ML libs). Persist model as a plain text file model/model.txt.
- Model format
  - Simple feed-forward network (e.g., input -> hidden -> 32 output logits for all cards), serialized as weights and biases in text (CSV/JSON-line). Include metadata: card ordering, input dimension, timestamp, and training params.
- Usage in API
  - On application startup (non-train profiles), load model.txt if present and use for local inference when serving /v1/best-card. If absent, fall back to external model (when available) or baseline heuristic.

11. Roadmap (implementation steps)
- Define domain types: Suit, Rank, Card, Play, Trick, GameState.
- Implement legality engine to compute legalCards.
- Implement Controller POST /v1/best-card and DTOs.
- Implement ModelClient adapter (HTTP) with configuration and timeout.
- Implement fallback heuristic.
- Implement training runner (@Profile("train")) that produces model/model.txt using the external API as teacher.
- Add Cucumber step defs to validate legalCards and sample decisions using the feature in src/test/resources/features.
- Add unit tests for edge cases (void, overtrump, partner winning, no-trump discard).
