package nl.jvdkolk.klaversjassentrainer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "klaverjas")
public class TrainerProperties {
    public static class Training {
        private boolean enabled = false;
        private int generations = 1000;
        private int gamesPerGeneration = 500;
        private double learningRate = 0.05;
        private String modelFile = "model/model.txt";
        private String gameVariant = "amsterdams";
        private int threads = Math.max(1, Runtime.getRuntime().availableProcessors());

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getGenerations() { return generations; }
        public void setGenerations(int generations) { this.generations = generations; }
        public int getGamesPerGeneration() { return gamesPerGeneration; }
        public void setGamesPerGeneration(int gamesPerGeneration) { this.gamesPerGeneration = gamesPerGeneration; }
        public double getLearningRate() { return learningRate; }
        public void setLearningRate(double learningRate) { this.learningRate = learningRate; }
        public String getModelFile() { return modelFile; }
        public void setModelFile(String modelFile) { this.modelFile = modelFile; }
        public String getGameVariant() { return gameVariant; }
        public void setGameVariant(String gameVariant) { this.gameVariant = gameVariant; }
        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }
    }

    public static class ExternalApi {
        private String baseUrl = "https://api.klaversjassen.nl";
        private String calcAiCardPath = "/api/v1/calcAiCard";
        private int timeoutMs = 5000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getCalcAiCardPath() { return calcAiCardPath; }
        public void setCalcAiCardPath(String calcAiCardPath) { this.calcAiCardPath = calcAiCardPath; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    private Training training = new Training();
    private ExternalApi externalApi = new ExternalApi();

    public Training getTraining() { return training; }
    public void setTraining(Training training) { this.training = training; }
    public ExternalApi getExternalApi() { return externalApi; }
    public void setExternalApi(ExternalApi externalApi) { this.externalApi = externalApi; }
}
