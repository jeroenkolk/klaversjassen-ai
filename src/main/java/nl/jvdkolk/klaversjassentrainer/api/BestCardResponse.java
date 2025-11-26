package nl.jvdkolk.klaversjassentrainer.api;

import java.util.List;
import java.util.Map;

public class BestCardResponse {
    public static class Candidate {
        private String card;
        private double score;
        private String reason;

        public Candidate() {}
        public Candidate(String card, double score, String reason) {
            this.card = card;
            this.score = score;
            this.reason = reason;
        }
        public String getCard() { return card; }
        public void setCard(String card) { this.card = card; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private String bestCard;
    private List<Candidate> candidates;
    private List<String> legalCards;
    private Map<String, String> model;
    private String requestId;

    public String getBestCard() { return bestCard; }
    public void setBestCard(String bestCard) { this.bestCard = bestCard; }
    public List<Candidate> getCandidates() { return candidates; }
    public void setCandidates(List<Candidate> candidates) { this.candidates = candidates; }
    public List<String> getLegalCards() { return legalCards; }
    public void setLegalCards(List<String> legalCards) { this.legalCards = legalCards; }
    public Map<String, String> getModel() { return model; }
    public void setModel(Map<String, String> model) { this.model = model; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
