package nl.jvdkolk.klaversjassentrainer.train;

import nl.jvdkolk.klaversjassentrainer.config.TrainerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExternalCalcApiClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalCalcApiClient.class);

    private final RestTemplate restTemplate;
    private final String url;
    private final String gameVariant;

    public ExternalCalcApiClient(RestTemplate restTemplate, TrainerProperties props) {
        this.restTemplate = restTemplate;
        this.url = props.getExternalApi().getBaseUrl() + props.getExternalApi().getCalcAiCardPath();
        this.gameVariant = props.getTraining().getGameVariant();
    }

    public String fetchBestCard(List<String> currentTrick, String trumpSuit, List<String> hand) {
        // Map internal representations to API schema
        List<String> apiTrick = currentTrick == null ? List.of() : currentTrick.stream()
                .filter(s -> s != null && !s.isBlank() && !"-".equals(s))
                .map(ExternalCalcApiClient::toApiCard)
                .toList();
        List<String> apiHand = hand == null ? List.of() : hand.stream()
                .map(ExternalCalcApiClient::toApiCard)
                .toList();
        String apiTrump = toApiSuit(trumpSuit);

        Map<String, Object> body = new HashMap<>();
        body.put("currentTrick", apiTrick);
        body.put("trumpSuit", apiTrump);
        body.put("hand", apiHand);
        body.put("gameVariant", gameVariant);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            if (response == null) return null;
            // Response is quoted JSON string or plain string; trim quotes if present
            String trimmed = response.trim();
            if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("\'") && trimmed.endsWith("\'")))) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            // Map API card back to our internal representation if possible
            try {
                return fromApiCard(trimmed);
            } catch (IllegalArgumentException ex) {
                // If mapping fails, return the raw value
                return trimmed;
            }
        } catch (RestClientException ex) {
            log.warn("External calcAiCard call failed: {}", ex.getMessage());
            return null;
        }
    }

    // Convert internal card (e.g., "AH", "10S") to API enum (e.g., "Ah", "Ts")
    static String toApiCard(String internal) {
        if (internal == null || internal.isBlank()) throw new IllegalArgumentException("Empty card");
        String rank;
        String suitChar;
        if (internal.length() == 3) { // 10S
            rank = internal.substring(0, 2);
            suitChar = internal.substring(2, 3);
        } else {
            rank = internal.substring(0, 1);
            suitChar = internal.substring(1, 2);
        }
        String apiRank = switch (rank) {
            case "A" -> "A";
            case "K" -> "K";
            case "Q" -> "Q";
            case "J" -> "J";
            case "10" -> "T"; // Ten -> T
            case "9" -> "N";  // Nine -> N (per provided schema enum)
            case "8" -> "E";  // Eight -> E
            case "7" -> "S";  // Seven -> S
            default -> throw new IllegalArgumentException("Unsupported rank: " + rank);
        };
        String apiSuit = switch (suitChar.toUpperCase()) {
            case "H" -> "h";
            case "C" -> "c";
            case "D" -> "d";
            case "S" -> "s";
            default -> throw new IllegalArgumentException("Unsupported suit: " + suitChar);
        };
        return apiRank + apiSuit;
    }

    // Convert API enum card back to internal representation
    static String fromApiCard(String api) {
        if (api == null || api.isBlank()) throw new IllegalArgumentException("Empty api card");
        String rank = api.substring(0, 1);
        String suit = api.substring(1, 2);
        String internalRank = switch (rank) {
            case "A", "K", "Q", "J" -> rank;
            case "T" -> "10";
            case "N" -> "9";
            case "E" -> "8";
            case "S" -> "7";
            default -> throw new IllegalArgumentException("Unsupported api rank: " + rank);
        };
        String internalSuit = switch (suit) {
            case "h" -> "H";
            case "c" -> "C";
            case "d" -> "D";
            case "s" -> "S";
            default -> throw new IllegalArgumentException("Unsupported api suit: " + suit);
        };
        return internalRank + internalSuit;
    }

    // Convert internal suit letter (H/C/D/S) to API Suit (Hearts/Clubs/Diamonds/Spades)
    static String toApiSuit(String internal) {
        if (internal == null || internal.isBlank()) throw new IllegalArgumentException("Empty suit");
        return switch (internal.toUpperCase()) {
            case "H" -> "Hearts";
            case "C" -> "Clubs";
            case "D" -> "Diamonds";
            case "S" -> "Spades";
            default -> internal; // if already full name, pass-through
        };
    }
}
