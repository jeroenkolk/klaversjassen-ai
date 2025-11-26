package nl.jvdkolk.klaversjassentrainer.train;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Minimal 2-layer feed-forward neural network with tanh hidden and softmax output.
 * No external dependencies. For training we use simple SGD with cross-entropy.
 */
public class NeuralNetwork {
    private final int inputSize;
    private final int hiddenSize;
    private final int outputSize;

    // Weights: input->hidden (W1: hidden x input), bias b1 (hidden)
    //          hidden->output (W2: output x hidden), bias b2 (output)
    private final double[][] W1;
    private final double[] b1;
    private final double[][] W2;
    private final double[] b2;

    private final Random rnd;

    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize, long seed) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        this.W1 = new double[hiddenSize][inputSize];
        this.b1 = new double[hiddenSize];
        this.W2 = new double[outputSize][hiddenSize];
        this.b2 = new double[outputSize];
        this.rnd = new Random(seed);
        initWeights();
    }

    private void initWeights() {
        double std1 = Math.sqrt(2.0 / (inputSize + hiddenSize));
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                W1[i][j] = rnd.nextGaussian() * std1;
            }
            b1[i] = 0.0;
        }
        double std2 = Math.sqrt(2.0 / (hiddenSize + outputSize));
        for (int i = 0; i < outputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                W2[i][j] = rnd.nextGaussian() * std2;
            }
            b2[i] = 0.0;
        }
    }

    public double[] forward(double[] x) {
        double[] h = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = b1[i];
            for (int j = 0; j < inputSize; j++) sum += W1[i][j] * x[j];
            h[i] = Math.tanh(sum);
        }
        double[] o = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = b2[i];
            for (int j = 0; j < hiddenSize; j++) sum += W2[i][j] * h[j];
            o[i] = sum;
        }
        // softmax
        double max = Double.NEGATIVE_INFINITY;
        for (double v : o) max = Math.max(max, v);
        double sumExp = 0.0;
        for (int i = 0; i < o.length; i++) {
            o[i] = Math.exp(o[i] - max);
            sumExp += o[i];
        }
        for (int i = 0; i < o.length; i++) o[i] /= sumExp;
        return o;
    }

    public void trainStep(double[] x, int targetIndex, double learningRate) {
        // forward
        double[] h = new double[hiddenSize];
        double[] preH = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = b1[i];
            for (int j = 0; j < inputSize; j++) sum += W1[i][j] * x[j];
            preH[i] = sum;
            h[i] = Math.tanh(sum);
        }
        double[] o = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = b2[i];
            for (int j = 0; j < hiddenSize; j++) sum += W2[i][j] * h[j];
            o[i] = sum;
        }
        // softmax
        double max = Double.NEGATIVE_INFINITY;
        for (double v : o) max = Math.max(max, v);
        double sumExp = 0.0;
        for (int i = 0; i < o.length; i++) {
            o[i] = Math.exp(o[i] - max);
            sumExp += o[i];
        }
        for (int i = 0; i < o.length; i++) o[i] /= sumExp;

        // gradients output (softmax with cross-entropy): dL/do = yhat - y
        double[] dO = new double[outputSize];
        for (int i = 0; i < outputSize; i++) dO[i] = o[i];
        dO[targetIndex] -= 1.0;

        // backprop W2, b2 and compute dH
        double[] dH = new double[hiddenSize];
        for (int i = 0; i < outputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                W2[i][j] -= learningRate * dO[i] * h[j];
                dH[j] += dO[i] * W2[i][j];
            }
            b2[i] -= learningRate * dO[i];
        }

        // backprop through tanh: d(tanh)/dz = 1 - tanh^2
        for (int j = 0; j < hiddenSize; j++) dH[j] *= (1 - Math.pow(Math.tanh(preH[j]), 2));

        // backprop W1, b1
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                W1[i][j] -= learningRate * dH[i] * x[j];
            }
            b1[i] -= learningRate * dH[i];
        }
    }

    /**
     * Train one step but restrict the softmax to a subset of allowed output indices (e.g., cards in hand).
     * Disallowed classes receive effectively -inf logit, zero probability, and zero gradient.
     */
    public void trainStepMasked(double[] x, int targetIndex, boolean[] allowed, double learningRate) {
        if (allowed == null || allowed.length != outputSize) {
            // fallback to unmasked step to avoid silent shape issues
            trainStep(x, targetIndex, learningRate);
            return;
        }
        // forward
        double[] h = new double[hiddenSize];
        double[] preH = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = b1[i];
            for (int j = 0; j < inputSize; j++) sum += W1[i][j] * x[j];
            preH[i] = sum;
            h[i] = Math.tanh(sum);
        }
        double[] o = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            if (!allowed[i]) { o[i] = Double.NEGATIVE_INFINITY; continue; }
            double sum = b2[i];
            for (int j = 0; j < hiddenSize; j++) sum += W2[i][j] * h[j];
            o[i] = sum;
        }
        // softmax over allowed only
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < outputSize; i++) if (allowed[i]) max = Math.max(max, o[i]);
        double sumExp = 0.0;
        for (int i = 0; i < outputSize; i++) {
            if (!allowed[i]) { o[i] = 0.0; continue; }
            o[i] = Math.exp(o[i] - max);
            sumExp += o[i];
        }
        if (sumExp == 0.0) {
            // nothing allowed? fall back
            trainStep(x, targetIndex, learningRate);
            return;
        }
        for (int i = 0; i < outputSize; i++) if (allowed[i]) o[i] /= sumExp;

        // gradients
        double[] dO = new double[outputSize];
        for (int i = 0; i < outputSize; i++) dO[i] = allowed[i] ? o[i] : 0.0;
        if (targetIndex >= 0 && targetIndex < outputSize && allowed[targetIndex]) dO[targetIndex] -= 1.0;

        double[] dH = new double[hiddenSize];
        for (int i = 0; i < outputSize; i++) {
            if (!allowed[i]) continue;
            for (int j = 0; j < hiddenSize; j++) {
                W2[i][j] -= learningRate * dO[i] * h[j];
                dH[j] += dO[i] * W2[i][j];
            }
            b2[i] -= learningRate * dO[i];
        }
        for (int j = 0; j < hiddenSize; j++) dH[j] *= (1 - Math.pow(Math.tanh(preH[j]), 2));
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                W1[i][j] -= learningRate * dH[i] * x[j];
            }
            b1[i] -= learningRate * dH[i];
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(String.format(Locale.ROOT, "# model nn input=%d hidden=%d output=%d\n", inputSize, hiddenSize, outputSize));
            writeMatrix(w, "W1", W1);
            writeVector(w, "b1", b1);
            writeMatrix(w, "W2", W2);
            writeVector(w, "b2", b2);
        }
    }

    public int getInputSize() { return inputSize; }
    public int getOutputSize() { return outputSize; }

    private static void writeMatrix(Writer w, String name, double[][] M) throws IOException {
        w.write(name + "\n");
        for (double[] row : M) {
            for (int j = 0; j < row.length; j++) {
                if (j > 0) w.write(",");
                w.write(String.format(Locale.ROOT, "%f", row[j]));
            }
            w.write("\n");
        }
    }

    private static void writeVector(Writer w, String name, double[] v) throws IOException {
        w.write(name + "\n");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) w.write(",");
            w.write(String.format(Locale.ROOT, "%f", v[i]));
        }
        w.write("\n");
    }

    public static NeuralNetwork load(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parseFromReader(r);
        }
    }

    /**
     * Load a model from an InputStream (e.g., a classpath resource).
     */
    public static NeuralNetwork load(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parseFromReader(r);
        }
    }

    private static void expectSection(BufferedReader r, String name) throws IOException {
        String line = r.readLine();
        if (line == null || !line.trim().equals(name)) {
            throw new IOException("Expected section '" + name + "' but got: " + line);
        }
    }

    private static NeuralNetwork parseFromReader(BufferedReader r) throws IOException {
        String header = r.readLine();
        if (header == null || !header.startsWith("# model nn")) {
            throw new IOException("Unrecognized model header");
        }
        int input = parseDim(header, "input");
        int hidden = parseDim(header, "hidden");
        int output = parseDim(header, "output");
        NeuralNetwork nn = new NeuralNetwork(input, hidden, output, 42L);

        // Expect sections in order: W1, rows(hidden), b1, W2, rows(output), b2
        expectSection(r, "W1");
        for (int i = 0; i < hidden; i++) {
            double[] row = parseRow(r.readLine(), input);
            System.arraycopy(row, 0, nn.W1[i], 0, input);
        }
        expectSection(r, "b1");
        double[] b1 = parseRow(r.readLine(), hidden);
        System.arraycopy(b1, 0, nn.b1, 0, hidden);

        expectSection(r, "W2");
        for (int i = 0; i < output; i++) {
            double[] row = parseRow(r.readLine(), hidden);
            System.arraycopy(row, 0, nn.W2[i], 0, hidden);
        }
        expectSection(r, "b2");
        double[] b2 = parseRow(r.readLine(), output);
        System.arraycopy(b2, 0, nn.b2, 0, output);
        return nn;
    }

    private static int parseDim(String header, String key) throws IOException {
        String token = key + "=";
        int idx = header.indexOf(token);
        if (idx < 0) throw new IOException("Missing dimension: " + key);
        int end = header.indexOf(' ', idx + token.length());
        String val = (end < 0 ? header.substring(idx + token.length()) : header.substring(idx + token.length(), end)).trim();
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IOException("Bad dimension for " + key + ": " + val);
        }
    }

    private static double[] parseRow(String line, int expected) throws IOException {
        if (line == null) throw new IOException("Unexpected EOF while reading row");
        String[] parts = line.split(",");
        if (parts.length != expected) {
            throw new IOException("Expected " + expected + " values, got " + parts.length);
        }
        double[] v = new double[expected];
        for (int i = 0; i < expected; i++) {
            v[i] = Double.parseDouble(parts[i]);
        }
        return v;
    }
}
