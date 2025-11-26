Feature: Determine the best legal card to play (Rotterdam rules)
  The service suggests the best card given hand, table, history, and trump.
  Card notation: RankSuit (e.g., AH = Ace of Hearts, 10S = Ten of Spades).

  Background:
    Given trump is H
    And player position is 0
    And partner position is 2

  Scenario: Following suit is mandatory
    Given hand is ["AC", "KH", "7H", "9D", "8S", "QC", "10C", "7S"]
    And current trick leader is 1
    And table is:
      | player | card |
      | 1      | AH   |
    When requesting the best card
    Then the legal cards should be ["KH", "7H"]
    And the best card must be one of the legal cards

  Scenario: Must overtrump when void in led suit and opponent trumped
    Given hand is ["JH", "9H", "AC", "10S", "7D", "QC", "8S", "7C"]
    And current trick leader is 3
    And table is:
      | player | card |
      | 3      | AD   |
      | 0      | -    |
      | 1      | 7D   |
      | 2      | 9H   |
    When requesting the best card
    Then the legal cards should be ["JH"]
    And the best card must be "JH"

  Scenario: Partner winning means no obligation to overtrump
    Given hand is ["KH", "QC", "10C", "9S", "8S", "7S", "AD", "7C"]
    And current trick leader is 1
    And table is:
      | player | card |
      | 1      | 7D   |
      | 2      | 9H   |
    When requesting the best card
    Then the partner is currently winning the trick
    And the legal cards should be ["QC", "10C", "9S", "8S", "7S", "AD", "7C", "KH"]
    And at least one non-trump discard should be among candidates

  Scenario: Void in led suit, no trump on table, must play trump if you have it
    Given hand is ["KH", "9H", "QC", "10C", "AD", "KD", "7S", "8S"]
    And current trick leader is 3
    And table is:
      | player | card |
      | 3      | AS   |
      | 0      | -    |
      | 1      | 7S   |
    When requesting the best card
    Then the legal cards should be ["KH", "9H"]
    And the best card must be one of the legal cards

  Scenario: Lead player can play any card from hand
    Given hand is ["AH", "KH", "QC", "10C", "AD", "KD", "9S", "8S"]
    And current trick leader is 0
    And table is:
      | player | card |
    When requesting the best card
    Then the legal cards should contain all cards from hand
    And the best card must be one of the legal cards

  Scenario: API fallback when external model unavailable
    Given hand is ["AH", "KH", "QC", "10C", "AD", "KD", "9S", "8S"]
    And current trick leader is 0
    And table is:
      | player | card |
    And the external model is unavailable
    When requesting the best card
    Then the response should include a fallback reason
    And the best card must be one of the legal cards
