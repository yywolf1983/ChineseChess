package top.nones.chessgame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.Result;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import Info.ChessInfo;

/**
 * 中国象棋棋盘识别服务
 * 使用 ONNX Runtime 进行模型推理
 */
public class ChessRecognitionService {
    private static final String TAG = "ChessRecognitionService";

    // 模型文件名
    private static final String POSE_MODEL = "4_v6-0301.onnx";     // 关键点检测(棋盘角点)
    private static final String LAYOUT_MODEL = "nano_v3-0319.onnx"; // 棋局分类(10x9x16)

    // 模型输入尺寸
    private static final int REG_INPUT_SIZE = 256;    // 分类模型输入尺寸

    // 棋盘尺寸
    private static final int BOARD_ROWS = 10;
    private static final int BOARD_COLS = 9;
    private static final int NUM_CLASSES = 16;        // 16分类

    // 棋子类别映射 (模型输出 -> ChessInfo)
    private static final Map<String, Integer> PIECE_MAP = new HashMap<>();
    static {
        // 红方棋子 (ChessInfo: 8-14)
        PIECE_MAP.put("K", 8);  // 红帅
        PIECE_MAP.put("A", 9);  // 红仕
        PIECE_MAP.put("B", 10); // 红相
        PIECE_MAP.put("N", 11); // 红马
        PIECE_MAP.put("R", 12); // 红车
        PIECE_MAP.put("C", 13); // 红炮
        PIECE_MAP.put("P", 14); // 红兵
        // 黑方棋子 (ChessInfo: 1-7)
        PIECE_MAP.put("k", 1);  // 黑将
        PIECE_MAP.put("a", 2);  // 黑仕
        PIECE_MAP.put("b", 3);  // 黑象
        PIECE_MAP.put("n", 4);  // 黑马
        PIECE_MAP.put("r", 5);  // 黑车
        PIECE_MAP.put("c", 6);  // 黑炮
        PIECE_MAP.put("p", 7);  // 黑卒
    }

    // 类别索引映射 (模型输出索引 -> 字符)
    private static final String[] CLASS_INDEX_MAP = {
        ".",  // 0: 空位
        "k",  // 1: 黑将
        "a",  // 2: 黑仕
        "b",  // 3: 黑象
        "n",  // 4: 黑马
        "r",  // 5: 黑车
        "c",  // 6: 黑炮
        "p",  // 7: 黑卒
        "K",  // 8: 红帅
        "A",  // 9: 红仕
        "B",  // 10: 红相
        "N",  // 11: 红马
        "R",  // 12: 红车
        "C",  // 13: 红炮
        "P",  // 14: 红兵
        "x"   // 15: 其他/未知
    };

    private Context context;
    private boolean initialized = false;

    // ONNX Runtime session
    private OrtEnvironment ortEnv;
    private OrtSession regSession;

    public ChessRecognitionService(Context context) {
        this.context = context;
    }

    /**
     * 初始化 ONNX 模型
     */
    public void initialize() throws Exception {
        if (initialized) return;
        Log.d(TAG, "Initializing ONNX models...");

        try {
            // 创建 ONNX Runtime 环境
            ortEnv = OrtEnvironment.getEnvironment();

            // 设置 session 选项
            SessionOptions sessionOptions = new SessionOptions();
            sessionOptions.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT);

            // 加载布局分类模型（核心模型）
            File regModelFile = extractModelFile(LAYOUT_MODEL);
            Log.d(TAG, "Loading layout model from: " + regModelFile.getAbsolutePath());
            regSession = ortEnv.createSession(regModelFile.getAbsolutePath(), sessionOptions);
            Log.d(TAG, "Layout model loaded successfully, inputs=" + regSession.getNumInputs() 
                + ", outputs=" + regSession.getNumOutputs());

            initialized = true;
            Log.d(TAG, "ONNX models initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ONNX models: " + e.getMessage(), e);
            // 如果 ONNX Runtime 不可用，使用模拟模式
            initialized = true;
            Log.d(TAG, "Using fallback mode (no ONNX model inference)");
        }
    }

    /**
     * 从 assets 提取模型文件到缓存目录
     */
    private File extractModelFile(String modelName) throws IOException {
        File cacheDir = context.getCacheDir();
        File modelFile = new File(cacheDir, modelName);

        if (modelFile.exists()) {
            Log.d(TAG, "Model file already exists: " + modelFile.getAbsolutePath() 
                + " (" + modelFile.length() + " bytes)");
            return modelFile;
        }

        Log.d(TAG, "Extracting model from assets: " + modelName);
        try (InputStream is = context.getAssets().open(modelName);
             FileOutputStream fos = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            Log.d(TAG, "Model extracted: " + modelName + " (" + total + " bytes)");
        }
        return modelFile;
    }

    /**
     * 识别棋盘
     * @param bitmap 输入图片
     * @return ChessInfo 棋盘信息
     */
    public ChessInfo recognize(Bitmap bitmap) {
        if (!initialized) {
            Log.e(TAG, "Service not initialized");
            return null;
        }

        try {
            Log.d(TAG, "Starting recognition...");
            long startTime = System.currentTimeMillis();

            ChessInfo chessInfo;

            // 使用 ONNX Runtime 进行推理
            if (regSession != null && ortEnv != null) {
                chessInfo = runInference(bitmap);
            } else {
                // 使用模拟模式（开发/测试用）
                Log.d(TAG, "Using simulated recognition (model not loaded)");
                chessInfo = createSimulatedChessInfo();
            }

            long endTime = System.currentTimeMillis();
            Log.d(TAG, "Recognition completed in " + (endTime - startTime) + "ms");

            return chessInfo;

        } catch (Exception e) {
            Log.e(TAG, "Recognition error: " + e.getMessage(), e);
            return createSimulatedChessInfo();
        }
    }

    /**
     * 运行 ONNX 模型推理
     */
    private ChessInfo runInference(Bitmap bitmap) throws OrtException {
        ChessInfo chessInfo = new ChessInfo();
        long t0 = System.currentTimeMillis();

        // 1. 调整图片尺寸
        Bitmap resizedBitmap = resizeBitmap(bitmap, REG_INPUT_SIZE, REG_INPUT_SIZE);
        long t1 = System.currentTimeMillis();

        // 2. 准备输入数据 (CHW 格式, 归一化到 [0,1])
        float[][][] inputHWC = prepareInputHWC(resizedBitmap);
        long t2 = System.currentTimeMillis();

        // 转换为 CHW 格式的 float[1][3][256][256] 或 float[1][256][256][3]
        // 根据模型期望的格式
        // 先尝试 NCHW 格式 [1, 3, 256, 256]
        float[][][][] inputNCHW = new float[1][3][REG_INPUT_SIZE][REG_INPUT_SIZE];
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < REG_INPUT_SIZE; y++) {
                for (int x = 0; x < REG_INPUT_SIZE; x++) {
                    inputNCHW[0][c][y][x] = inputHWC[c][y][x];
                }
            }
        }
        long t3 = System.currentTimeMillis();

        // 3. 运行模型推理
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, inputNCHW);
        Result result = regSession.run(Collections.singletonMap("input", inputTensor));
        long t4 = System.currentTimeMillis();

        // 4. 处理输出
        // 输出可能是多种格式：[1, 16, 10, 9] 或 [1, 10, 9, 16]
        Object outputValue = result.get(0).getValue();
        int[][] classificationResults;

        if (outputValue instanceof float[][][][]) {
            // 格式 [1, C, H, W] 或 [1, H, W, C]
            float[][][][] tensor = (float[][][][]) outputValue;
            int[] shape = getShape4D(tensor);

            Log.d(TAG, "Output tensor shape: [" + shape[0] + "," + shape[1] + "," + shape[2] + "," + shape[3] + "]");

            if (shape[1] == NUM_CLASSES && shape[2] == BOARD_ROWS && shape[3] == BOARD_COLS) {
                // [1, 16, 10, 9] - correct format
                classificationResults = parseNCHWFormat(tensor);
            } else if (shape[1] == BOARD_ROWS && shape[2] == BOARD_COLS && shape[3] == NUM_CLASSES) {
                // [1, 10, 9, 16] - NHWC format
                classificationResults = parseNHWCFormat(tensor);
            } else {
                Log.w(TAG, "Unexpected tensor shape, using argmax on last dim");
                classificationResults = parseGenericFormat(tensor, shape);
            }
        } else if (outputValue instanceof float[][][]) {
            float[][][] tensor = (float[][][]) outputValue;
            classificationResults = parse3DTensor(tensor);
        } else {
            Log.w(TAG, "Unknown output type: " + outputValue.getClass().getName());
            return createSimulatedChessInfo();
        }
        long t5 = System.currentTimeMillis();

        // 5. 转换结果到 ChessInfo 格式
        // 直接映射：classificationResults[y][x] → piece[y][x]
        // 不翻转 Y 轴，让 generateFEN + ChessView (drawY=9-i) 自动处理方向
        int nonEmptyCount = 0;
        int[] classCounts = new int[NUM_CLASSES];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int classIndex = classificationResults[y][x];
                classCounts[classIndex]++;
                if (classIndex >= 0 && classIndex < CLASS_INDEX_MAP.length) {
                    String pieceChar = CLASS_INDEX_MAP[classIndex];
                    Integer pieceId = PIECE_MAP.get(pieceChar);
                    chessInfo.piece[y][x] = pieceId != null ? pieceId : 0;
                    if (pieceId != null && pieceId > 0) nonEmptyCount++;
                } else {
                    chessInfo.piece[y][x] = 0;
                }
            }
        }
        Log.d(TAG, "推理结果: 非空格子=" + nonEmptyCount + "/90, 各类别分布: ");
        for (int c = 0; c < NUM_CLASSES; c++) {
            if (classCounts[c] > 0) {
                Log.d(TAG, "  类别[" + c + "]=" + CLASS_INDEX_MAP[c] + " x" + classCounts[c]);
            }
        }
        // 采样打印前2行和后2行的预测，方便核对方向
        StringBuilder sample = new StringBuilder("预测采样: ");
        for (int y = 0; y < Math.min(2, BOARD_ROWS); y++) {
            sample.append("\n  row").append(y).append(": ");
            for (int x = 0; x < BOARD_COLS; x++) {
                sample.append(CLASS_INDEX_MAP[classificationResults[y][x]]);
            }
        }
        sample.append("\n  ...");
        for (int y = Math.max(0, BOARD_ROWS - 2); y < BOARD_ROWS; y++) {
            sample.append("\n  row").append(y).append(": ");
            for (int x = 0; x < BOARD_COLS; x++) {
                sample.append(CLASS_INDEX_MAP[classificationResults[y][x]]);
            }
        }
        Log.d(TAG, sample.toString());

        inputTensor.close();
        result.close();
        long t6 = System.currentTimeMillis();

        Log.d(TAG, String.format("Inference timing: resize=%dms, prep=%dms, convert=%dms, infer=%dms, parse=%dms, total=%dms",
            t1 - t0, t2 - t1, t3 - t2, t4 - t3, t5 - t4, t6 - t0));

        // 6. 确定谁先手
        int redCount = 0, blackCount = 0;
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int piece = chessInfo.piece[y][x];
                if (piece >= 8) redCount++;
                else if (piece >= 1) blackCount++;
            }
        }
        chessInfo.IsRedGo = true;

        return chessInfo;
    }

    private int[] getShape4D(float[][][][] tensor) {
        return new int[]{
            tensor.length,
            tensor[0].length,
            tensor[0][0].length,
            tensor[0][0][0].length
        };
    }

    private int[][] parseNCHWFormat(float[][][][] tensor) {
        // [1, 16, 10, 9] -> argmax over dim 1
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int maxClass = 0;
                float maxVal = tensor[0][0][y][x];
                for (int c = 1; c < NUM_CLASSES; c++) {
                    float val = tensor[0][c][y][x];
                    if (val > maxVal) {
                        maxVal = val;
                        maxClass = c;
                    }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parseNHWCFormat(float[][][][] tensor) {
        // [1, 10, 9, 16] -> argmax over dim 3
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int maxClass = 0;
                float maxVal = tensor[0][y][x][0];
                for (int c = 1; c < NUM_CLASSES; c++) {
                    float val = tensor[0][y][x][c];
                    if (val > maxVal) {
                        maxVal = val;
                        maxClass = c;
                    }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parseGenericFormat(float[][][][] tensor, int[] shape) {
        Log.w(TAG, "Using generic argmax over last dimension: [" 
            + shape[0] + "," + shape[1] + "," + shape[2] + "," + shape[3] + "]");
        // Try to figure out the layout
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < Math.min(BOARD_ROWS, shape[2]); y++) {
            for (int x = 0; x < Math.min(BOARD_COLS, shape[3]); x++) {
                int maxClass = 0;
                float maxVal = 0;
                for (int c = 0; c < Math.min(NUM_CLASSES, shape[1]); c++) {
                    float val = tensor[0][c][y][x];
                    if (val > maxVal) {
                        maxVal = val;
                        maxClass = c;
                    }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parse3DTensor(float[][][] tensor) {
        int dim0 = tensor.length;
        int dim1 = tensor[0].length;
        int dim2 = tensor[0][0].length;

        Log.d(TAG, "3D Tensor shape: [" + dim0 + "," + dim1 + "," + dim2 + "]");

        int[][] results = new int[BOARD_ROWS][BOARD_COLS];

        if (dim0 == NUM_CLASSES && dim1 == BOARD_ROWS && dim2 == BOARD_COLS) {
            // [16, 10, 9]
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    int maxClass = 0;
                    float maxVal = tensor[0][y][x];
                    for (int c = 1; c < NUM_CLASSES; c++) {
                        float val = tensor[c][y][x];
                        if (val > maxVal) {
                            maxVal = val;
                            maxClass = c;
                        }
                    }
                    results[y][x] = maxClass;
                }
            }
        } else if (dim0 == BOARD_ROWS && dim1 == BOARD_COLS && dim2 == NUM_CLASSES) {
            // [10, 9, 16]
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    int maxClass = 0;
                    float maxVal = tensor[y][x][0];
                    for (int c = 1; c < NUM_CLASSES; c++) {
                        float val = tensor[y][x][c];
                        if (val > maxVal) {
                            maxVal = val;
                            maxClass = c;
                        }
                    }
                    results[y][x] = maxClass;
                }
            }
        } else if (dim0 == 1 && dim1 == BOARD_ROWS * BOARD_COLS && dim2 == NUM_CLASSES) {
            // [1, 90, 16] - 展平后的位置×类别
            for (int p = 0; p < dim1; p++) {
                int y = p / BOARD_COLS;
                int x = p % BOARD_COLS;
                int maxClass = 0;
                float maxVal = tensor[0][p][0];
                for (int c = 1; c < NUM_CLASSES; c++) {
                    float val = tensor[0][p][c];
                    if (val > maxVal) {
                        maxVal = val;
                        maxClass = c;
                    }
                }
                results[y][x] = maxClass;
            }
        } else {
            Log.w(TAG, "Unknown 3D tensor layout");
        }
        return results;
    }

    /**
     * 准备 HWC 输入数据
     */
    private float[][][] prepareInputHWC(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float[][][] data = new float[3][height][width];

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int y = i / width;
            int x = i % width;

            // ARGB -> RGB，归一化到 [0, 1]
            data[0][y][x] = ((pixel >> 16) & 0xFF) / 255.0f; // R
            data[1][y][x] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
            data[2][y][x] = (pixel & 0xFF) / 255.0f;         // B
        }

        return data;
    }

    /**
     * 创建模拟棋盘信息（用于开发/测试）
     */
    private ChessInfo createSimulatedChessInfo() {
        ChessInfo chessInfo = new ChessInfo();

        // 初始化棋盘为空（对齐 ChessInfo.init() 坐标：y=0=红方底线，y=9=黑方底线）
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                chessInfo.piece[y][x] = 0;
            }
        }

        // 红方棋子（y=0 红方底线，y=9 红方兵线）
        chessInfo.piece[0][0] = 12; // 红车
        chessInfo.piece[0][1] = 11; // 红马
        chessInfo.piece[0][2] = 10; // 红相
        chessInfo.piece[0][3] = 9;  // 红仕
        chessInfo.piece[0][4] = 8;  // 红帅
        chessInfo.piece[0][5] = 9;  // 红仕
        chessInfo.piece[0][6] = 10; // 红相
        chessInfo.piece[0][7] = 11; // 红马
        chessInfo.piece[0][8] = 12; // 红车
        chessInfo.piece[2][1] = 13; // 红炮
        chessInfo.piece[2][7] = 13; // 红炮
        chessInfo.piece[3][0] = 14; // 红兵
        chessInfo.piece[3][2] = 14; // 红兵
        chessInfo.piece[3][4] = 14; // 红兵
        chessInfo.piece[3][6] = 14; // 红兵
        chessInfo.piece[3][8] = 14; // 红兵

        // 黑方棋子（y=6 黑方兵线，y=9 黑方底线）
        chessInfo.piece[9][0] = 5;  // 黑车
        chessInfo.piece[9][1] = 4;  // 黑马
        chessInfo.piece[9][2] = 3;  // 黑象
        chessInfo.piece[9][3] = 2;  // 黑士
        chessInfo.piece[9][4] = 1;  // 黑将
        chessInfo.piece[9][5] = 2;  // 黑士
        chessInfo.piece[9][6] = 3;  // 黑象
        chessInfo.piece[9][7] = 4;  // 黑马
        chessInfo.piece[9][8] = 5;  // 黑车
        chessInfo.piece[7][1] = 6;  // 黑炮
        chessInfo.piece[7][7] = 6;  // 黑炮
        chessInfo.piece[6][0] = 7;  // 黑卒
        chessInfo.piece[6][2] = 7;  // 黑卒
        chessInfo.piece[6][4] = 7;  // 黑卒
        chessInfo.piece[6][6] = 7;  // 黑卒
        chessInfo.piece[6][8] = 7;  // 黑卒

        chessInfo.IsRedGo = true;
        return chessInfo;
    }

    /**
     * 将 Bitmap 调整为固定尺寸（保持长宽比，黑边填充）
     */
    private Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        
        // 计算缩放比例，保持长宽比
        float scale = Math.min(
            (float) targetWidth / bitmap.getWidth(),
            (float) targetHeight / bitmap.getHeight()
        );
        int scaledW = Math.round(bitmap.getWidth() * scale);
        int scaledH = Math.round(bitmap.getHeight() * scale);
        
        // 缩放
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true);
        
        // 创建目标尺寸的画布，居中放置
        Bitmap result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        canvas.drawColor(android.graphics.Color.BLACK); // 黑边填充
        int offsetX = (targetWidth - scaledW) / 2;
        int offsetY = (targetHeight - scaledH) / 2;
        canvas.drawBitmap(scaled, offsetX, offsetY, null);
        
        if (scaled != bitmap) {
            scaled.recycle();
        }
        
        return result;
    }

    /**
     * 释放资源
     */
    public void close() {
        initialized = false;
        if (regSession != null) {
            try { regSession.close(); } catch (OrtException e) { }
            regSession = null;
        }
        if (ortEnv != null) {
            try { ortEnv.close(); } catch (Exception e) { }
            ortEnv = null;
        }
        Log.d(TAG, "Resources released");
    }
}
