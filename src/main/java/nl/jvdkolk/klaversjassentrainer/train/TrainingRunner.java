package nl.jvdkolk.klaversjassentrainer.train;

import nl.jvdkolk.klaversjassentrainer.config.TrainerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static nl.jvdkolk.klaversjassentrainer.train.CardUtil.*;

@Component
public class TrainingRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TrainingRunner.class);

    private final TrainerProperties props;
    private final ExternalCalcApiClient api;

    public TrainingRunner(TrainerProperties props, ExternalCalcApiClient api) {
        this.props = props;
        this.api = api;
    }

    @Override
    public void run(String... args) throws Exception {
        TrainerProperties.Training tp = props.getTraining();
        if (!tp.isEnabled()) {
            log.info("Training profile active but klaverjas.training.enabled is false - skipping training run.");
            return;
        }
        int generations = tp.getGenerations();
        int gamesPerGen = tp.getGamesPerGeneration();
        double lr = tp.getLearningRate();
        String modelFile = tp.getModelFile();
        // Persist the trained model into src/main/resources so it can be picked up from the classpath at runtime
        Path resourcesModelPath = Path.of("src", "main", "resources").resolve(modelFile);
        int threads = Math.max(1, tp.getThreads());

        int inputSize = ALL_CARDS.size() /*hand*/ + (4 * ALL_CARDS.size()) /*trick ordered*/ + SUITS.length /*trump*/;
        int hidden = 128;
        int output = ALL_CARDS.size();
        NeuralNetwork nn = new NeuralNetwork(inputSize, hidden, output, 42L);

        log.info("Starting training: generations={}, games/gen={}, lr={}, threads={}, modelFile={} (resources={})", generations, gamesPerGen, lr, threads, modelFile, resourcesModelPath);
        Instant startAll = Instant.now();
        int totalSamples = 0;
        int usedSamples = 0;
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setName("trainer-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        for (int g = 1; g <= generations; g++) {
            int successes = 0;
            int skipped = 0;
            int correct = 0; // count how often current model predicts the API label
            Instant genStart = Instant.now();
            // Prepare parallel tasks to collect supervised samples
            List<Callable<Sample>> tasks = new ArrayList<>(gamesPerGen);
            for (int n = 0; n < gamesPerGen; n++) {
                tasks.add(() -> {
                    Random rnd = ThreadLocalRandom.current();
                    List<String> hand = randomHand(rnd, 8);
                    String trump = randomSuit(rnd);
                    List<String> trick = randomTrickGivenNotIn(rnd, hand);
                    String label = api.fetchBestCard(trick, trump, hand);
                    if (label == null) return null;
                    if (!hand.contains(label)) return null;
                    double[] x = concatVectors(encodeHand(hand), encodeTrickOrdered(trick), encodeTrump(trump));
                    int yIdx = cardIndex(label);
                    boolean[] allowed = new boolean[ALL_CARDS.size()];
                    for (String c : hand) allowed[cardIndex(c)] = true;
                    return new Sample(x, yIdx, allowed);
                });
            }
            List<Future<Sample>> futures = pool.invokeAll(tasks);
            List<Sample> batch = new ArrayList<>(gamesPerGen);
            for (Future<Sample> f : futures) {
                try {
                    Sample s = f.get();
                    totalSamples++;
                    if (s != null) {
                        batch.add(s);
                    } else {
                        skipped++;
                    }
                } catch (ExecutionException e) {
                    skipped++;
                    // log at debug to avoid noise
                    log.debug("Sample task failed: {}", e.getMessage());
                }
            }
            // Evaluate current accuracy (sequential read-only forward) and train sequentially
            for (Sample s : batch) {
                double[] probs = nn.forward(s.x);
                int predIdx = argMaxAllowed(probs, s.allowed);
                if (predIdx == s.yIdx) correct++;
                nn.trainStepMasked(s.x, s.yIdx, s.allowed, lr);
                usedSamples++;
                successes++;
            }
            Duration genDur = Duration.between(genStart, Instant.now());
            if (g % 10 == 0 || g == generations) {
                double acc = successes == 0 ? 0.0 : (correct * 1.0 / successes);
                log.info("Gen {}/{}: used={}, skipped={}, acc={}% , duration={} ms", g, generations, successes, skipped, String.format(Locale.ROOT, "%.2f", acc * 100.0), genDur.toMillis());
            }
            // Save checkpoint occasionally
            if (g % 50 == 0 || g == generations) {
                nn.save(resourcesModelPath);
            }
            // mild learning rate decay
            lr *= 0.999;
        }
        pool.shutdown();
        Duration totalDur = Duration.between(startAll, Instant.now());
        log.info("Training completed. TotalSamples={}, UsedSamples={}, duration={} s", totalSamples, usedSamples, totalDur.toSeconds());
        // final save to resources, so InferenceService can load from classpath
        nn.save(resourcesModelPath);
    }

    private static double[] concatVectors(double[]... arrs) {
        int len = 0;
        for (double[] a : arrs) len += a.length;
        double[] out = new double[len];
        int p = 0;
        for (double[] a : arrs) {
            System.arraycopy(a, 0, out, p, a.length);
            p += a.length;
        }
        return out;
    }

    private static List<String> randomTrickGivenNotIn(Random rnd, Collection<String> exclude) {
        // pick 0-3 cards from the deck not in exclude
        int size = rnd.nextInt(4); // 0..3
        List<String> deck = new ArrayList<>(ALL_CARDS);
        deck.removeAll(exclude);
        Collections.shuffle(deck, rnd);
        return new ArrayList<>(deck.subList(0, size));
    }

    private static int argMax(double[] arr) {
        int idx = 0;
        double best = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > best) {
                best = arr[i];
                idx = i;
            }
        }
        return idx;
    }

    private static int argMaxAllowed(double[] arr, boolean[] allowed) {
        int idx = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < arr.length; i++) {
            if (allowed != null && i < allowed.length && !allowed[i]) continue;
            if (arr[i] > best) { best = arr[i]; idx = i; }
        }
        if (idx >= 0) return idx;
        // fallback to global argmax
        return argMax(arr);
    }

    private static final class Sample {
        final double[] x;
        final int yIdx;
        final boolean[] allowed;

        Sample(double[] x, int yIdx, boolean[] allowed) {
            this.x = x;
            this.yIdx = yIdx;
            this.allowed = allowed;
        }
    }
}
