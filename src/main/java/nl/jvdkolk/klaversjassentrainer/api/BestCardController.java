package nl.jvdkolk.klaversjassentrainer.api;

import nl.jvdkolk.klaversjassentrainer.service.InferenceService;
import nl.jvdkolk.klaversjassentrainer.service.LegalityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/v1")
public class BestCardController {
    private static final Logger log = LoggerFactory.getLogger(BestCardController.class);

    private final LegalityService legality;
    private final InferenceService inference;

    public BestCardController(LegalityService legality, InferenceService inference) {
        this.legality = legality;
        this.inference = inference;
    }

    @PostMapping("/best-card")
    public ResponseEntity<BestCardResponse> bestCard(@RequestBody BestCardRequest req) {
        // Basic validation
        if (req.getHand() == null || req.getHand().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.getTrump() == null || req.getTrump().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.getPlayerPosition() == null || req.getPartnerPosition() == null || req.getLeaderPosition() == null
                || req.getPlayerPosition() < 0 || req.getPlayerPosition() > 3
                || req.getPartnerPosition() < 0 || req.getPartnerPosition() > 3
                || req.getLeaderPosition() < 0 || req.getLeaderPosition() > 3) {
            return ResponseEntity.badRequest().build();
        }

        List<BestCardRequest.Play> table = Optional.ofNullable(req.getTable()).orElse(List.of());
        // Compute legal cards
        List<String> legal = legality.computeLegalCards(req.getHand(), table, req.getTrump(), req.getPartnerPosition(), req.getLeaderPosition());
        if (legal.isEmpty()) {
            return ResponseEntity.unprocessableEntity().build();
        }

        // Score candidates with model (or fallback)
        InferenceService.Result result = inference.pickBest(req.getHand(), table, req.getTrump(), legal, Optional.ofNullable(req.getTopK()).orElse(3));

        BestCardResponse resp = new BestCardResponse();
        resp.setBestCard(result.getBestCard());
        resp.setCandidates(result.getCandidates());
        resp.setLegalCards(legal);
        resp.setModel(result.getModel());
        resp.setRequestId(req.getRequestId());
        return ResponseEntity.ok(resp);
    }
}
