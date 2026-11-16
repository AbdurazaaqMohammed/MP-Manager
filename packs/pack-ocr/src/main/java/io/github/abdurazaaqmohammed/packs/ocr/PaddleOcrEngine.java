package io.github.abdurazaaqmohammed.packs.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.icu.text.Bidi;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrEngine;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrResult;

/** PP-OCRv5 mobile DB detection + batched SVTR recognition, entirely on the CPU. */
final class PaddleOcrEngine implements OcrEngine {
    private static final int DETECTION_SIDE = 1280;
    private static final int RECOGNITION_HEIGHT = 48;
    private static final int MAX_RECOGNITION_WIDTH = 2048;
    private static final int BATCH_SIZE = 4;
    private final OrtEnvironment environment;
    private OrtSession detector;
    private OrtSession recognizer;
    private final CtcDecoder decoder;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private FloatBuffer inputBuffer;

    PaddleOcrEngine(File detectionModel, File recognitionModel) throws Exception {
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setIntraOpNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
            options.setInterOpNumThreads(1);
            options.setCPUArenaAllocator(false);
            options.setMemoryPatternOptimization(false);
            options.addConfigEntry("session.intra_op.allow_spinning", "0");
            detector = environment.createSession(detectionModel.getAbsolutePath(), options);
            recognizer = environment.createSession(recognitionModel.getAbsolutePath(), options);
            decoder = new CtcDecoder(recognizer.getMetadata().getCustomMetadata().get("character"));
        } catch (Exception | LinkageError error) {
            close();
            throw error;
        }
    }

    @Override
    public OcrResult recognize(Bitmap image, BooleanSupplier cancelled) throws Exception {
        long start = System.nanoTime();
        checkCancelled(cancelled);
        List<DbDetector.Quad> boxes = detect(image, cancelled);
        if (boxes.size() > 400) {
            boxes.sort(Comparator.comparingDouble(DbDetector.Quad::confidence).reversed());
            boxes = new ArrayList<>(boxes.subList(0, 400));
        }
        // Similar-width crops share a batch, avoiding excessive padding work.
        boxes.sort(Comparator.comparingDouble(box -> box.width() / Math.max(1, box.height())));
        List<OcrResult.Line> lines = new ArrayList<>();
        for (int offset = 0; offset < boxes.size(); offset += BATCH_SIZE) {
            checkCancelled(cancelled);
            int count = Math.min(BATCH_SIZE, boxes.size() - offset);
            List<DbDetector.Quad> batch = boxes.subList(offset, offset + count);
            recognizeBatch(image, batch, lines, cancelled);
        }
        return new OcrResult(lines, (System.nanoTime() - start) / 1_000_000);
    }

    private List<DbDetector.Quad> detect(Bitmap image, BooleanSupplier cancelled) throws Exception {
        float scale = Math.min(1f, DETECTION_SIDE / (float) Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(32, Math.round(image.getWidth() * scale / 32) * 32);
        int height = Math.max(32, Math.round(image.getHeight() * scale / 32) * 32);
        Bitmap resized = Bitmap.createScaledBitmap(image, width, height, true);
        FloatBuffer input = buffer(width * height * 3);
        try {
            normalize(resized, input, width, 0);
        } finally {
            if (resized != image) resized.recycle();
        }
        checkCancelled(cancelled);
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, new long[]{1, 3, height, width});
             OrtSession.Result output = detector.run(Collections.singletonMap(detector.getInputNames().iterator().next(), tensor))) {
            OnnxTensor result = (OnnxTensor) output.get(0);
            long[] shape = ((TensorInfo) result.getInfo()).getShape();
            if (shape.length != 4 || shape[0] != 1 || shape[1] != 1) {
                throw new IllegalStateException("Unexpected detection model output");
            }
            int mapHeight = Math.toIntExact(shape[2]), mapWidth = Math.toIntExact(shape[3]);
            FloatBuffer values = result.getFloatBuffer();
            float[] map = new float[Math.multiplyExact(mapHeight, mapWidth)];
            values.get(map);
            return DbDetector.boxes(map, mapWidth, mapHeight, image.getWidth(), image.getHeight(), cancelled);
        }
    }

    private void recognizeBatch(Bitmap image, List<DbDetector.Quad> boxes, List<OcrResult.Line> lines,
                                BooleanSupplier cancelled) throws Exception {
        int width = 320;
        int[] cropWidths = new int[boxes.size()];
        boolean[] vertical = new boolean[boxes.size()];
        for (int index = 0; index < boxes.size(); index++) {
            DbDetector.Quad box = boxes.get(index);
            vertical[index] = box.height() / Math.max(1, box.width()) >= 1.5f;
            float ratio = vertical[index] ? box.height() / Math.max(1, box.width())
                    : box.width() / Math.max(1, box.height());
            cropWidths[index] = Math.max(8, Math.min(MAX_RECOGNITION_WIDTH,
                    (int) Math.ceil(RECOGNITION_HEIGHT * ratio)));
            width = Math.max(width, cropWidths[index]);
        }
        FloatBuffer input = buffer(boxes.size() * 3 * RECOGNITION_HEIGHT * width);
        for (int index = 0; index < boxes.size(); index++) {
            checkCancelled(cancelled);
            Bitmap crop = warp(image, boxes.get(index), cropWidths[index], RECOGNITION_HEIGHT, vertical[index]);
            try {
                normalize(crop, input, width, index * 3 * RECOGNITION_HEIGHT * width);
            } finally {
                crop.recycle();
            }
        }
        checkCancelled(cancelled);
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input,
                new long[]{boxes.size(), 3, RECOGNITION_HEIGHT, width});
             OrtSession.Result output = recognizer.run(Collections.singletonMap(recognizer.getInputNames().iterator().next(), tensor))) {
            OnnxTensor result = (OnnxTensor) output.get(0);
            long[] shape = ((TensorInfo) result.getInfo()).getShape();
            if (shape.length != 3 || shape[0] != boxes.size()) {
                throw new IllegalStateException("Unexpected recognition model output");
            }
            int steps = Math.toIntExact(shape[1]), classes = Math.toIntExact(shape[2]);
            FloatBuffer predictions = result.getFloatBuffer();
            for (int index = 0; index < boxes.size(); index++) {
                checkCancelled(cancelled);
                predictions.position(index * steps * classes);
                FloatBuffer slice = predictions.slice();
                CtcDecoder.Decoded decoded = decoder.decode(slice, steps, classes);
                if (decoded.text().isEmpty() || decoded.confidence() < .5f) continue;
                DbDetector.Quad quad = boxes.get(index);
                OcrResult.Box bounds = bounds(quad);
                List<OcrResult.Word> words = words(decoded, quad, width, cropWidths[index], steps, vertical[index]);
                String text = logicalText(decoded.text());
                if (OcrResult.isRightToLeft(decoded.text())) {
                    List<OcrResult.Word> logicalWords = new ArrayList<>();
                    for (OcrResult.Word word : words) logicalWords.add(new OcrResult.Word(
                            logicalText(word.text()), word.confidence(), word.box()));
                    // Model time spans advance left-to-right; returned text must
                    // use logical RTL word order for Android rendering/clipboard.
                    Collections.reverse(logicalWords);
                    words = logicalWords;
                }
                lines.add(new OcrResult.Line(text, decoded.confidence(), bounds, words));
            }
        }
    }

    private FloatBuffer buffer(int size) {
        if (inputBuffer == null || inputBuffer.capacity() < size) {
            inputBuffer = ByteBuffer.allocateDirect(Math.multiplyExact(size, 4))
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        inputBuffer.clear();
        inputBuffer.limit(size);
        // Recognition padding is zero AFTER normalization, not black pixels.
        for (int index = 0; index < size; index++) inputBuffer.put(index, 0);
        return inputBuffer;
    }

    private void normalize(Bitmap bitmap, FloatBuffer buffer, int paddedWidth, int offset) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] row = new int[width];
        int plane = paddedWidth * height;
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int start = offset + y * paddedWidth;
            for (int x = 0; x < width; x++) {
                int color = row[x];
                // ModelScope/RapidOCR models expect BGR NCHW normalized by 0.5.
                buffer.put(start + x, Color.blue(color) / 127.5f - 1);
                buffer.put(start + plane + x, Color.green(color) / 127.5f - 1);
                buffer.put(start + 2 * plane + x, Color.red(color) / 127.5f - 1);
            }
        }
    }

    private Bitmap warp(Bitmap image, DbDetector.Quad box, int width, int height, boolean vertical) {
        Bitmap crop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop);
        canvas.drawColor(Color.WHITE);
        float[] from = new float[]{box.topLeft().x(), box.topLeft().y(), box.topRight().x(), box.topRight().y(),
                box.bottomRight().x(), box.bottomRight().y(), box.bottomLeft().x(), box.bottomLeft().y()};
        // Rotate tall text crops 90 degrees counter-clockwise as PaddleOCR does.
        float[] to = vertical ? new float[]{0, height, 0, 0, width, 0, width, height}
                : new float[]{0, 0, width, 0, width, height, 0, height};
        Matrix matrix = new Matrix();
        if (matrix.setPolyToPoly(from, 0, to, 0, 4)) {
            canvas.drawBitmap(image, matrix, paint);
        } else {
            OcrResult.Box bounds = bounds(box);
            canvas.drawBitmap(image, new Rect((int) bounds.left(), (int) bounds.top(),
                    Math.max((int) bounds.left() + 1, (int) bounds.right()),
                    Math.max((int) bounds.top() + 1, (int) bounds.bottom())), new Rect(0, 0, width, height), paint);
        }
        return crop;
    }

    private static OcrResult.Box bounds(DbDetector.Quad box) {
        return new OcrResult.Box(Math.min(Math.min(box.topLeft().x(), box.topRight().x()), Math.min(box.bottomLeft().x(), box.bottomRight().x())),
                Math.min(Math.min(box.topLeft().y(), box.topRight().y()), Math.min(box.bottomLeft().y(), box.bottomRight().y())),
                Math.max(Math.max(box.topLeft().x(), box.topRight().x()), Math.max(box.bottomLeft().x(), box.bottomRight().x())),
                Math.max(Math.max(box.topLeft().y(), box.topRight().y()), Math.max(box.bottomLeft().y(), box.bottomRight().y())));
    }

    private static List<OcrResult.Word> words(CtcDecoder.Decoded decoded, DbDetector.Quad quad,
                                               int paddedWidth, int cropWidth, int steps, boolean vertical) {
        List<OcrResult.Word> words = new ArrayList<>();
        List<CtcDecoder.Token> group = new ArrayList<>();
        for (CtcDecoder.Token token : decoded.tokens()) {
            boolean space = token.text().trim().isEmpty();
            int codePoint = token.text().isEmpty() ? 0 : token.text().codePointAt(0);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            boolean cjk = block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA;
            if (space || cjk) addWord(words, group, quad, paddedWidth, cropWidth, steps, vertical);
            if (!space) group.add(token);
            if (cjk) addWord(words, group, quad, paddedWidth, cropWidth, steps, vertical);
        }
        addWord(words, group, quad, paddedWidth, cropWidth, steps, vertical);
        return words;
    }

    private static void addWord(List<OcrResult.Word> words, List<CtcDecoder.Token> tokens,
                                 DbDetector.Quad quad, int paddedWidth, int cropWidth, int steps, boolean vertical) {
        if (tokens.isEmpty()) return;
        StringBuilder text = new StringBuilder();
        float confidence = 0;
        for (CtcDecoder.Token token : tokens) { text.append(token.text()); confidence += token.confidence(); }
        float start = Math.max(0, (tokens.get(0).start() - 1f) * paddedWidth / steps / cropWidth);
        float end = Math.min(1, (tokens.get(tokens.size() - 1).end() + 1f) * paddedWidth / steps / cropWidth);
        OcrResult.Box box = bounds(quad);
        OcrResult.Box wordBox = vertical
                ? new OcrResult.Box(box.left(), box.top() + start * box.height(), box.right(), box.top() + end * box.height())
                : new OcrResult.Box(box.left() + start * box.width(), box.top(), box.left() + end * box.width(), box.bottom());
        words.add(new OcrResult.Word(text.toString(), confidence / tokens.size(), wordBox));
        tokens.clear();
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static String logicalText(String visualText) {
        if (!OcrResult.isRightToLeft(visualText)) return visualText;
        Bidi bidi = new Bidi();
        bidi.setReorderingMode(Bidi.REORDER_INVERSE_LIKE_DIRECT);
        bidi.setPara(visualText, Bidi.LEVEL_DEFAULT_RTL, null);
        return bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.KEEP_BASE_COMBINING);
    }

    @Override
    public void close() throws Exception {
        try {
            if (recognizer != null) recognizer.close();
        } finally {
            recognizer = null;
            if (detector != null) detector.close();
            detector = null;
            inputBuffer = null;
        }
        // OrtEnvironment is shared within the process and must not be closed here.
    }
}
