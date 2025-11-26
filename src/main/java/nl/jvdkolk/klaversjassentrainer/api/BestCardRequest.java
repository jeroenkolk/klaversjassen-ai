package nl.jvdkolk.klaversjassentrainer.api;

import java.util.List;

public class BestCardRequest {
    private List<String> hand;
    private List<Play> table;
    private String trump; // H/C/D/S
    private Integer playerPosition;
    private Integer leaderPosition;
    private Integer partnerPosition;
    private String scoringMode;
    private Integer topK;
    private String requestId;

    public List<String> getHand() { return hand; }
    public void setHand(List<String> hand) { this.hand = hand; }
    public List<Play> getTable() { return table; }
    public void setTable(List<Play> table) { this.table = table; }
    public String getTrump() { return trump; }
    public void setTrump(String trump) { this.trump = trump; }
    public Integer getPlayerPosition() { return playerPosition; }
    public void setPlayerPosition(Integer playerPosition) { this.playerPosition = playerPosition; }
    public Integer getLeaderPosition() { return leaderPosition; }
    public void setLeaderPosition(Integer leaderPosition) { this.leaderPosition = leaderPosition; }
    public Integer getPartnerPosition() { return partnerPosition; }
    public void setPartnerPosition(Integer partnerPosition) { this.partnerPosition = partnerPosition; }
    public String getScoringMode() { return scoringMode; }
    public void setScoringMode(String scoringMode) { this.scoringMode = scoringMode; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public static class Play {
        private Integer player;
        private String card; // e.g., "AH" or "-" for empty placeholder

        public Integer getPlayer() { return player; }
        public void setPlayer(Integer player) { this.player = player; }
        public String getCard() { return card; }
        public void setCard(String card) { this.card = card; }
    }
}
