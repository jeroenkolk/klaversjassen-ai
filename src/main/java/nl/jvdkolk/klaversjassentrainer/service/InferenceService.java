package nl.jvdkolk.klaversjassentrainer.service;

import nl.jvdkolk.klaversjassentrainer.api.BestCardRequest;
import nl.jvdkolk.klaversjassentrainer.api.BestCardResponse;
import nl.jvdkolk.klaversjassentrainer.config.TrainerProperties;
import nl.jvdkolk.klaversjassentrainer.train.NeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static nl.jvdkolk.klaversjassentrainer.train.CardUtil.*;

@Service
public class InferenceService {
    private static final Logger log = LoggerFactory.getLogger(InferenceService.class);

    private final TrainerProperties props;
    private NeuralNetwork nn; // may be null => fallback

    public InferenceService(TrainerProperties props) {
        this.props = props;
        loadModelIfPresent();
    }

    private void loadModelIfPresent() {
        try {
            Path path = Path.of(props.getTraining().getModelFile());
            if (Files.exists(path)) {
                this.nn = NeuralNetwork.load(path);
                log.info("Loaded model from {}", path);
            } else {
                log.warn("Model file not found at {}. Inference will use fallback.", path);
            }
        } catch (Exception e) {
            log.warn("Failed to load model: {}. Using fallback.", e.getMessage());
            this.nn = null;
        }
    }

    public Result pickBest(List<String> hand,
                           List<BestCardRequest.Play> table,
                           String trump,
                           List<String> legal,
                           int topK) {
        Map<String, String> modelMeta = new LinkedHashMap<>();
        modelMeta.put("name", nn == null ? "fallback" : "kj-rotterdam-small");
        modelMeta.put("version", "0.1.0");
        modelMeta.put("ts", Instant.now().toString());

        if (nn == null) {
            // simple deterministic fallback: choose the first legal card in alphabetical order
            List<String> sorted = new ArrayList<>(legal);
            Collections.sort(sorted);
            String best = sorted.get(0);
            List<BestCardResponse.Candidate> cands = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
                String card = sorted.get(i);
                cands.add(new BestCardResponse.Candidate(card, 0.0, "fallback"));
            }
            return new Result(best, cands, modelMeta);
        }

        // Build input vector. Prefer ordered trick encoding if model trained that way.
        List<String> trickOrder = new ArrayList<>();
        if (table != null) {
            for (BestCardRequest.Play p : table) {
                if (p == null) { trickOrder.add(null); continue; }
                String c = p.getCard();
                trickOrder.add(c);
            }
        }
        // Compute expected input sizes for both encodings
        int unorderedInput = ALL_CARDS.size() + ALL_CARDS.size() + SUITS.length;
        int orderedInput = ALL_CARDS.size() + (4 * ALL_CARDS.size()) + SUITS.length;
        double[] x;
        if (nn != null && nn.getInputSize() == orderedInput) {
            x = concat(encodeHand(hand), encodeTrickOrdered(trickOrder), encodeTrump(trump));
        } else {
            // fallback/backward compatible
            List<String> trickCards = new ArrayList<>();
            for (String c : trickOrder) {
                if (c == null || c.isBlank() || "-".equals(c)) continue;
                trickCards.add(c);
            }
            x = concat(encodeHand(hand), encodeTrick(trickCards), encodeTrump(trump));
        }
        double[] probs = nn.forward(x);

        // Collect candidates
        List<BestCardResponse.Candidate> candidates = new ArrayList<>();
        for (String card : legal) {
            int idx = cardIndex(card);
            double score = (idx >= 0 && idx < probs.length) ? probs[idx] : 0.0;
            candidates.add(new BestCardResponse.Candidate(card, score, null));
        }
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        String best = candidates.get(0).getCard();

        // Trim topK
        if (candidates.size() > topK) {
            candidates = new ArrayList<>(candidates.subList(0, topK));
        }
        return new Result(best, candidates, modelMeta);
    }

    private static double[] concat(double[]... xs) {
        int n = 0; for (double[] a : xs) n += a.length;
        double[] out = new double[n];
        int p = 0; for (double[] a : xs) { System.arraycopy(a, 0, out, p, a.length); p += a.length; }
        return out;
    }

    public static class Result {
        private final String bestCard;
        private final List<BestCardResponse.Candidate> candidates;
        private final Map<String, String> model;

        public Result(String bestCard, List<BestCardResponse.Candidate> candidates, Map<String, String> model) {
            this.bestCard = bestCard;
            this.candidates = candidates;
            this.model = model;
        }

        public String getBestCard() { return bestCard; }
        public List<BestCardResponse.Candidate> getCandidates() { return candidates; }
        public Map<String, String> getModel() { return model; }
    }
}
