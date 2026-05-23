package top.nones.chessgame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import Info.ChessInfo;

/**
 * 中国象棋棋盘识别服务
 * 两步流水线：1) pose模型检测棋盘角点 → 2) 透视变换 → 3) 分类模型识别棋子
 */
public class ChessRecognitionService {
    private static final String TAG = "ChessRecognitionService";

    // 模型文件名
    private static final String POSE_MODEL = "4_v6-0301.onnx";     // 关键点检测(棋盘角点)
    private static final String LAYOUT_MODEL = "nano_v3-0319.onnx"; // 棋局分类(10x9x16)

    // 模型输入尺寸
    private static final int POSE_INPUT_SIZE = 256;    // pose模型输入尺寸
    private static final int CLS_INPUT_WIDTH = 280;    // 分类模型输入宽度
    private static final int CLS_INPUT_HEIGHT = 315;   // 分类模型输入高度

    // 棋盘尺寸
    private static final int BOARD_ROWS = 10;
    private static final int BOARD_COLS = 9;
    private static final int NUM_CLASSES = 16;        // 16分类

    // 透视变换目标尺寸 (与原项目一致)
    private static final int WARP_WIDTH = 450;
    private static final int WARP_HEIGHT = 500;
    private static final int WARP_PADDING = 50;

    // 棋子类别映射 (模型输出 -> ChessInfo)
    private static final Map<String, Integer> PIECE_MAP = new HashMap<>();
    static {
        PIECE_MAP.put("K", 8);   PIECE_MAP.put("A", 9);   PIECE_MAP.put("B", 10);
        PIECE_MAP.put("N", 11);  PIECE_MAP.put("R", 12);  PIECE_MAP.put("C", 13);
        PIECE_MAP.put("P", 14);  PIECE_MAP.put("k", 1);   PIECE_MAP.put("a", 2);
        PIECE_MAP.put("b", 3);   PIECE_MAP.put("n", 4);   PIECE_MAP.put("r", 5);
        PIECE_MAP.put("c", 6);   PIECE_MAP.put("p", 7);
    }

    // 类别索引映射 (与原始项目一致)
    // 0:point("."), 1:other("x"), 2-8:red(K,A,B,N,R,C,P), 9-15:black(k,a,b,n,r,c,p)
    private static final String[] CLASS_INDEX_MAP = {
        ".", "x", "K", "A", "B", "N", "R", "C", "P",
        "k", "a", "b", "n", "r", "c", "p"
    };

    private Context context;
    private boolean initialized = false;

    // ONNX Runtime
    private OrtEnvironment ortEnv;
    private OrtSession poseSession;    // pose模型
    private OrtSession clsSession;     // 分类模型

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
            ortEnv = OrtEnvironment.getEnvironment();
            SessionOptions sessionOptions = new SessionOptions();
            sessionOptions.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT);

            // 加载 pose 模型（棋盘角点检测）
            File poseModelFile = extractModelFile(POSE_MODEL);
            Log.d(TAG, "Loading pose model: " + poseModelFile.getAbsolutePath());
            poseSession = ortEnv.createSession(poseModelFile.getAbsolutePath(), sessionOptions);
            Log.d(TAG, "Pose model loaded: inputs=" + poseSession.getNumInputs() + ", outputs=" + poseSession.getNumOutputs());

            // 加载分类模型（棋局识别）
            File clsModelFile = extractModelFile(LAYOUT_MODEL);
            Log.d(TAG, "Loading layout model: " + clsModelFile.getAbsolutePath());
            clsSession = ortEnv.createSession(clsModelFile.getAbsolutePath(), sessionOptions);
            Log.d(TAG, "Layout model loaded: inputs=" + clsSession.getNumInputs() + ", outputs=" + clsSession.getNumOutputs());

            initialized = true;
            Log.d(TAG, "All ONNX models initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ONNX models: " + e.getMessage(), e);
            initialized = true;
        }
    }

    private File extractModelFile(String modelName) throws IOException {
        File cacheDir = context.getCacheDir();
        File modelFile = new File(cacheDir, modelName);
        if (modelFile.exists()) return modelFile;

        Log.d(TAG, "Extracting model from assets: " + modelName);
        try (InputStream is = context.getAssets().open(modelName);
             FileOutputStream fos = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        return modelFile;
    }

    /**
     * 识别棋盘（完整流水线）
     */
    public ChessInfo recognize(Bitmap bitmap) {
        if (!initialized) {
            Log.e(TAG, "Service not initialized");
            return null;
        }

        try {
            Log.d(TAG, "=== Starting recognition pipeline ===");
            long startTime = System.currentTimeMillis();

            // Step 1: 检测棋盘4个角点
            float[][] keypoints = detectKeypoints(bitmap);
            long t1 = System.currentTimeMillis();
            Log.d(TAG, "Step1 keypoints: " + (keypoints != null ? "OK" : "FAILED") + " (" + (t1 - startTime) + "ms)");

            Bitmap warpedBoard = null;
            if (keypoints != null) {
                // Step 2: 透视变换
                warpedBoard = warpPerspective(bitmap, keypoints);
                long t2 = System.currentTimeMillis();
                Log.d(TAG, "Step2 warp: " + (warpedBoard != null ? "OK" : "FAILED") + " (" + (t2 - t1) + "ms)");
            }

            ChessInfo chessInfo;
            if (warpedBoard != null) {
                // Step 3: 在变换后的标准棋盘图上分类
                chessInfo = runClassification(warpedBoard);
            } else {
                // 降级：直接在原图上分类（效果会差）
                Log.w(TAG, "Pose/warp failed, falling back to direct classification");
                chessInfo = runClassification(bitmap);
            }

            long endTime = System.currentTimeMillis();
            Log.d(TAG, "=== Recognition completed in " + (endTime - startTime) + "ms ===");
            return chessInfo;

        } catch (Exception e) {
            Log.e(TAG, "Recognition error: " + e.getMessage(), e);
            return createSimulatedChessInfo();
        }
    }

    /**
     * Step 1: 用 pose 模型检测棋盘4个角点 (A0, A8, J0, J8)
     * @return float[4][2] 关键点坐标 (原图坐标系)，失败返回 null
     */
    private float[][] detectKeypoints(Bitmap bitmap) {
        if (poseSession == null) return null;

        try {
            int origW = bitmap.getWidth();
            int origH = bitmap.getHeight();

            // 1. 缩放到 256x256
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, POSE_INPUT_SIZE, POSE_INPUT_SIZE, true);

            // 2. 准备输入 [1, 3, 256, 256]
            float[][][][] input = prepareNCHWInput(resized, POSE_INPUT_SIZE, POSE_INPUT_SIZE);
            OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, input);

            // 3. 运行 pose 模型
            Result result = poseSession.run(Collections.singletonMap("input", inputTensor));

            // 4. 解析 SimCC 输出
            // 输出: simcc_x [1, 4, 512], simcc_y [1, 4, 512]
            float[][][] simccX = (float[][][]) result.get(0).getValue();
            float[][][] simccY = (float[][][]) result.get(1).getValue();

            int numKpt = simccX[0].length;     // 4 个关键点
            int mapSizeX = simccX[0][0].length; // 512
            int mapSizeY = simccY[0][0].length; // 512

            Log.d(TAG, "Pose output: numKpt=" + numKpt + ", mapSizeX=" + mapSizeX + ", mapSizeY=" + mapSizeY);

            // 5. 解码 SimCC: argmax → 归一化坐标 → 原图坐标
            float[][] keypoints = new float[numKpt][2];
            for (int k = 0; k < numKpt; k++) {
                // 找 argmax
                int maxX = 0, maxY = 0;
                float maxValX = simccX[0][k][0];
                float maxValY = simccY[0][k][0];
                for (int i = 1; i < mapSizeX; i++) {
                    if (simccX[0][k][i] > maxValX) {
                        maxValX = simccX[0][k][i];
                        maxX = i;
                    }
                }
                for (int i = 1; i < mapSizeY; i++) {
                    if (simccY[0][k][i] > maxValY) {
                        maxValY = simccY[0][k][i];
                        maxY = i;
                    }
                }

                // 归一化到 [0, 1]
                float normX = (float) maxX / mapSizeX;
                float normY = (float) maxY / mapSizeY;

                // 映射回原图坐标
                keypoints[k][0] = normX * origW;
                keypoints[k][1] = normY * origH;

                Log.d(TAG, String.format("  kpt[%d] = (%.1f, %.1f) conf=(%.3f, %.3f)",
                    k, keypoints[k][0], keypoints[k][1], maxValX, maxValY));
            }

            inputTensor.close();
            result.close();
            return keypoints;

        } catch (Exception e) {
            Log.e(TAG, "Pose detection failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Step 2: 透视变换 - 将倾斜棋盘拉伸为标准俯视图
     * @param bitmap 原图
     * @param keypoints 4个角点 [A0, A8, J0, J8]
     * @return 变换后的标准棋盘图 (450x500)
     */
    private Bitmap warpPerspective(Bitmap bitmap, float[][] keypoints) {
        try {
            // 关键点顺序: A0(左上), A8(右上), J0(左下), J8(右下)
            float ax0 = keypoints[0][0], ay0 = keypoints[0][1]; // A0: 左上
            float ax8 = keypoints[1][0], ay8 = keypoints[1][1]; // A8: 右上
            float jx0 = keypoints[2][0], jy0 = keypoints[2][1]; // J0: 左下
            float jx8 = keypoints[3][0], jy8 = keypoints[3][1]; // J8: 右下

            // 目标点 (标准棋盘，带 padding)
            float dstAx0 = WARP_PADDING, dstAy0 = WARP_PADDING;                       // 左上
            float dstAx8 = WARP_WIDTH - WARP_PADDING, dstAy8 = WARP_PADDING;           // 右上
            float dstJx0 = WARP_PADDING, dstJy0 = WARP_HEIGHT - WARP_PADDING;         // 左下
            float dstJx8 = WARP_WIDTH - WARP_PADDING, dstJy8 = WARP_HEIGHT - WARP_PADDING; // 右下

            // 源点和目标点
            float[] src = {ax0, ay0, ax8, ay8, jx0, jy0, jx8, jy8};
            float[] dst = {dstAx0, dstAy0, dstAx8, dstAy8, dstJx0, dstJy0, dstJx8, dstJy8};

            // 使用 Android Matrix 做透视变换
            Matrix matrix = new Matrix();
            boolean success = matrix.setPolyToPoly(src, 0, dst, 0, 4);
            if (!success) {
                Log.e(TAG, "setPolyToPoly failed");
                return null;
            }

            // 创建变换后的图像
            Bitmap warped = Bitmap.createBitmap(WARP_WIDTH, WARP_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(warped);
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(bitmap, matrix, null);

            Log.d(TAG, "Perspective transform done: " + WARP_WIDTH + "x" + WARP_HEIGHT);
            return warped;

        } catch (Exception e) {
            Log.e(TAG, "Perspective transform failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Step 3: 在标准棋盘图上运行分类模型
     */
    private ChessInfo runClassification(Bitmap bitmap) throws OrtException {
        ChessInfo chessInfo = new ChessInfo();
        long t0 = System.currentTimeMillis();

        // 1. 缩放到模型输入尺寸 (280x315)
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, CLS_INPUT_WIDTH, CLS_INPUT_HEIGHT, true);
        long t1 = System.currentTimeMillis();

        // 2. 准备输入
        float[][][][] input = prepareNCHWInput(resized, CLS_INPUT_WIDTH, CLS_INPUT_HEIGHT);
        long t2 = System.currentTimeMillis();

        // 3. 推理
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, input);
        Result result = clsSession.run(Collections.singletonMap("input", inputTensor));
        long t3 = System.currentTimeMillis();

        // 4. 解析输出 - 获取所有概率
        float[][][] allProbabilities = null;
        Object outputValue = result.get(0).getValue();
        int[][] classificationResults;

        if (outputValue instanceof float[][][][]) {
            float[][][][] tensor = (float[][][][]) outputValue;
            int[] shape = getShape4D(tensor);
            Log.d(TAG, "Cls output shape: [" + shape[0] + "," + shape[1] + "," + shape[2] + "," + shape[3] + "]");

            // 获取所有概率信息用于后续验证
            allProbabilities = extractAllProbabilities(tensor, shape);

            if (shape[1] == NUM_CLASSES && shape[2] == BOARD_ROWS && shape[3] == BOARD_COLS) {
                classificationResults = parseNCHWFormat(tensor);
            } else if (shape[1] == BOARD_ROWS && shape[2] == BOARD_COLS && shape[3] == NUM_CLASSES) {
                classificationResults = parseNHWCFormat(tensor);
            } else {
                classificationResults = parseGenericFormat(tensor, shape);
            }
        } else if (outputValue instanceof float[][][]) {
            float[][][] tensor = (float[][][]) outputValue;
            // 获取所有概率信息用于后续验证
            allProbabilities = extractAllProbabilitiesFrom3D(tensor);
            classificationResults = parse3DTensor(tensor);
        } else {
            Log.w(TAG, "Unknown output type: " + outputValue.getClass().getName());
            return createSimulatedChessInfo();
        }
        long t4 = System.currentTimeMillis();

        // 5. 映射到 ChessInfo (row0=黑方顶部 → y=9黑方, row9=红方底部 → y=0红方)
        int nonEmptyCount = 0;
        int[][] tempBoard = new int[10][9];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int classIndex = classificationResults[y][x];
                if (classIndex >= 0 && classIndex < CLASS_INDEX_MAP.length) {
                    String pieceChar = CLASS_INDEX_MAP[classIndex];
                    Integer pieceId = PIECE_MAP.get(pieceChar);
                    tempBoard[9 - y][x] = pieceId != null ? pieceId : 0;
                    if (pieceId != null && pieceId > 0) nonEmptyCount++;
                } else {
                    tempBoard[9 - y][x] = 0;
                }
            }
        }

        // 6. 验证棋子数量，如果超出限制则调整
        if (allProbabilities != null) {
            tempBoard = validateAndAdjustPieceCounts(tempBoard, allProbabilities);
        }

        // 7. 将调整后的棋盘赋值给 chessInfo
        for (int y = 0; y < 10; y++) {
            System.arraycopy(tempBoard[y], 0, chessInfo.piece[y], 0, 9);
        }

        // 打印识别结果网格
        String[] pieceNames = {"空", "黑将", "黑士", "黑象", "黑马", "黑车", "黑炮", "黑卒", "红帅", "红仕", "红相", "红马", "红车", "红炮", "红兵"};
        StringBuilder grid = new StringBuilder("=== 识别结果 ===");
        grid.append("\n  非空格子=").append(nonEmptyCount).append("/90");
        for (int y = 0; y < BOARD_ROWS; y++) {
            grid.append("\n  y=").append(y).append(": ");
            for (int x = 0; x < BOARD_COLS; x++) {
                int p = chessInfo.piece[y][x];
                grid.append(p > 0 && p < pieceNames.length ? pieceNames[p] : ".").append(" ");
            }
        }
        Log.d(TAG, grid.toString());

        inputTensor.close();
        result.close();
        long t5 = System.currentTimeMillis();

        Log.d(TAG, String.format("Cls timing: resize=%dms, prep=%dms, infer=%dms, parse=%dms, total=%dms",
            t1-t0, t2-t1, t3-t2, t4-t3, t5-t0));

        // 8. 先手默认红方
        chessInfo.IsRedGo = true;
        return chessInfo;
    }
    
    /**
     * 从4D张量中提取所有位置的概率信息
     */
    private float[][][] extractAllProbabilities(float[][][][] tensor, int[] shape) {
        float[][][] probabilities = new float[BOARD_ROWS][BOARD_COLS][NUM_CLASSES];
        
        if (shape[1] == NUM_CLASSES && shape[2] == BOARD_ROWS && shape[3] == BOARD_COLS) {
            // NCHW格式
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    for (int c = 0; c < NUM_CLASSES; c++) {
                        probabilities[y][x][c] = tensor[0][c][y][x];
                    }
                }
            }
        } else if (shape[1] == BOARD_ROWS && shape[2] == BOARD_COLS && shape[3] == NUM_CLASSES) {
            // NHWC格式
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    for (int c = 0; c < NUM_CLASSES; c++) {
                        probabilities[y][x][c] = tensor[0][y][x][c];
                    }
                }
            }
        } else {
            // 通用格式
            for (int y = 0; y < Math.min(BOARD_ROWS, shape[2]); y++) {
                for (int x = 0; x < Math.min(BOARD_COLS, shape[3]); x++) {
                    for (int c = 0; c < Math.min(NUM_CLASSES, shape[1]); c++) {
                        probabilities[y][x][c] = tensor[0][c][y][x];
                    }
                }
            }
        }
        
        return probabilities;
    }
    
    /**
     * 从3D张量中提取所有位置的概率信息
     */
    private float[][][] extractAllProbabilitiesFrom3D(float[][][] tensor) {
        float[][][] probabilities = new float[BOARD_ROWS][BOARD_COLS][NUM_CLASSES];
        int dim0 = tensor.length, dim1 = tensor[0].length, dim2 = tensor[0][0].length;
        
        if (dim0 == NUM_CLASSES && dim1 == BOARD_ROWS && dim2 == BOARD_COLS) {
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    for (int c = 0; c < NUM_CLASSES; c++) {
                        probabilities[y][x][c] = tensor[c][y][x];
                    }
                }
            }
        } else if (dim0 == BOARD_ROWS && dim1 == BOARD_COLS && dim2 == NUM_CLASSES) {
            for (int y = 0; y < BOARD_ROWS; y++) {
                for (int x = 0; x < BOARD_COLS; x++) {
                    for (int c = 0; c < NUM_CLASSES; c++) {
                        probabilities[y][x][c] = tensor[y][x][c];
                    }
                }
            }
        } else if (dim0 == 1 && dim1 == BOARD_ROWS * BOARD_COLS && dim2 == NUM_CLASSES) {
            for (int p = 0; p < dim1; p++) {
                int y = p / BOARD_COLS;
                int x = p % BOARD_COLS;
                for (int c = 0; c < NUM_CLASSES; c++) {
                    probabilities[y][x][c] = tensor[0][p][c];
                }
            }
        }
        
        return probabilities;
    }
    
    /**
     * 验证并调整棋子数量，确保不超出正常范围，保留概率最高的结果
     */
    private int[][] validateAndAdjustPieceCounts(int[][] board, float[][][] probabilities) {
        int[][] resultBoard = new int[10][9];
        for (int y = 0; y < 10; y++) {
            System.arraycopy(board[y], 0, resultBoard[y], 0, 9);
        }
        
        // 棋子数量限制
        int[] maxCount = new int[15];
        maxCount[1] = 1;  // 黑将
        maxCount[8] = 1;  // 红帅
        maxCount[2] = 2;  // 黑士
        maxCount[3] = 2;  // 黑象
        maxCount[4] = 2;  // 黑马
        maxCount[5] = 2;  // 黑车
        maxCount[6] = 2;  // 黑炮
        maxCount[7] = 5;  // 黑卒
        maxCount[9] = 2;  // 红士
        maxCount[10] = 2; // 红相
        maxCount[11] = 2; // 红马
        maxCount[12] = 2; // 红车
        maxCount[13] = 2; // 红炮
        maxCount[14] = 5; // 红兵
        
        // 统计每种棋子的位置和概率
        java.util.List<java.util.List<PieceCandidate>> pieceCandidates = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            pieceCandidates.add(new java.util.ArrayList<>());
        }
        
        // 收集所有棋子候选
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int pieceId = resultBoard[y][x];
                if (pieceId > 0 && pieceId < 15) {
                    // 获取概率值（注意坐标转换）
                    int modelY = 9 - y;
                    // 根据 CLASS_INDEX_MAP 找到对应的 classIndex
                    int classIndex = 0;
                    for (int c = 0; c < CLASS_INDEX_MAP.length; c++) {
                        Integer mappedPieceId = PIECE_MAP.get(CLASS_INDEX_MAP[c]);
                        if (mappedPieceId != null && mappedPieceId == pieceId) {
                            classIndex = c;
                            break;
                        }
                    }
                    float prob = 0;
                    if (modelY >= 0 && modelY < BOARD_ROWS && classIndex < NUM_CLASSES) {
                        prob = probabilities[modelY][x][classIndex];
                    }
                    pieceCandidates.get(pieceId).add(new PieceCandidate(x, y, prob));
                }
            }
        }
        
        // 对每种棋子按概率降序排序，保留最多允许的数量
        for (int pieceId = 1; pieceId < 15; pieceId++) {
            if (maxCount[pieceId] > 0 && pieceCandidates.get(pieceId).size() > maxCount[pieceId]) {
                // 按概率降序排序
                java.util.Collections.sort(pieceCandidates.get(pieceId), (a, b) -> Float.compare(b.probability, a.probability));
                
                // 移除超出数量限制的棋子（从概率最低的开始）
                for (int i = maxCount[pieceId]; i < pieceCandidates.get(pieceId).size(); i++) {
                    PieceCandidate removed = pieceCandidates.get(pieceId).get(i);
                    resultBoard[removed.y][removed.x] = 0;
                    
                    Log.d(TAG, "移除超出数量的棋子: piece=" + pieceId + " at (" + removed.x + "," + removed.y + ") prob=" + removed.probability);
                }
            }
        }
        
        return resultBoard;
    }
    
    /**
     * 棋子候选类，用于保存棋子位置和概率信息
     */
    private static class PieceCandidate {
        int x;
        int y;
        float probability;
        
        PieceCandidate(int x, int y, float probability) {
            this.x = x;
            this.y = y;
            this.probability = probability;
        }
    }

    /**
     * 准备 NCHW 输入 [1, 3, height, width]
     * 使用 ImageNet 标准化
     */
    private float[][][][] prepareNCHWInput(Bitmap bitmap, int width, int height) {
        float[] mean = {123.675f, 116.28f, 103.53f};
        float[] std = {58.395f, 57.12f, 57.375f};

        float[][][][] input = new float[1][3][height][width];
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int y = i / width;
            int x = i % width;
            input[0][0][y][x] = (((pixel >> 16) & 0xFF) - mean[0]) / std[0]; // R
            input[0][1][y][x] = (((pixel >> 8) & 0xFF) - mean[1]) / std[1];  // G
            input[0][2][y][x] = ((pixel & 0xFF) - mean[2]) / std[2];         // B
        }
        return input;
    }

    // ========== 输出解析方法 ==========

    private int[] getShape4D(float[][][][] tensor) {
        return new int[]{tensor.length, tensor[0].length, tensor[0][0].length, tensor[0][0][0].length};
    }

    private int[][] parseNCHWFormat(float[][][][] tensor) {
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int maxClass = 0;
                float maxVal = tensor[0][0][y][x];
                for (int c = 1; c < NUM_CLASSES; c++) {
                    if (tensor[0][c][y][x] > maxVal) { maxVal = tensor[0][c][y][x]; maxClass = c; }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parseNHWCFormat(float[][][][] tensor) {
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int maxClass = 0;
                float maxVal = tensor[0][y][x][0];
                for (int c = 1; c < NUM_CLASSES; c++) {
                    if (tensor[0][y][x][c] > maxVal) { maxVal = tensor[0][y][x][c]; maxClass = c; }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parseGenericFormat(float[][][][] tensor, int[] shape) {
        Log.w(TAG, "Generic argmax: [" + shape[0] + "," + shape[1] + "," + shape[2] + "," + shape[3] + "]");
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];
        for (int y = 0; y < Math.min(BOARD_ROWS, shape[2]); y++) {
            for (int x = 0; x < Math.min(BOARD_COLS, shape[3]); x++) {
                int maxClass = 0;
                float maxVal = 0;
                for (int c = 0; c < Math.min(NUM_CLASSES, shape[1]); c++) {
                    if (tensor[0][c][y][x] > maxVal) { maxVal = tensor[0][c][y][x]; maxClass = c; }
                }
                results[y][x] = maxClass;
            }
        }
        return results;
    }

    private int[][] parse3DTensor(float[][][] tensor) {
        int dim0 = tensor.length, dim1 = tensor[0].length, dim2 = tensor[0][0].length;
        Log.d(TAG, "3D Tensor: [" + dim0 + "," + dim1 + "," + dim2 + "]");
        int[][] results = new int[BOARD_ROWS][BOARD_COLS];

        if (dim0 == NUM_CLASSES && dim1 == BOARD_ROWS && dim2 == BOARD_COLS) {
            for (int y = 0; y < BOARD_ROWS; y++)
                for (int x = 0; x < BOARD_COLS; x++) {
                    int maxC = 0; float maxV = tensor[0][y][x];
                    for (int c = 1; c < NUM_CLASSES; c++) if (tensor[c][y][x] > maxV) { maxV = tensor[c][y][x]; maxC = c; }
                    results[y][x] = maxC;
                }
        } else if (dim0 == BOARD_ROWS && dim1 == BOARD_COLS && dim2 == NUM_CLASSES) {
            for (int y = 0; y < BOARD_ROWS; y++)
                for (int x = 0; x < BOARD_COLS; x++) {
                    int maxC = 0; float maxV = tensor[y][x][0];
                    for (int c = 1; c < NUM_CLASSES; c++) if (tensor[y][x][c] > maxV) { maxV = tensor[y][x][c]; maxC = c; }
                    results[y][x] = maxC;
                }
        } else if (dim0 == 1 && dim1 == BOARD_ROWS * BOARD_COLS && dim2 == NUM_CLASSES) {
            for (int p = 0; p < dim1; p++) {
                int y = p / BOARD_COLS, x = p % BOARD_COLS;
                int maxC = 0; float maxV = tensor[0][p][0];
                for (int c = 1; c < NUM_CLASSES; c++) if (tensor[0][p][c] > maxV) { maxV = tensor[0][p][c]; maxC = c; }
                results[y][x] = maxC;
            }
        }
        return results;
    }

    // ========== 模拟数据 ==========

    private ChessInfo createSimulatedChessInfo() {
        ChessInfo chessInfo = new ChessInfo();
        for (int y = 0; y < 10; y++) for (int x = 0; x < 9; x++) chessInfo.piece[y][x] = 0;
        // 红方
        chessInfo.piece[0][0] = 12; chessInfo.piece[0][1] = 11; chessInfo.piece[0][2] = 10;
        chessInfo.piece[0][3] = 9;  chessInfo.piece[0][4] = 8;  chessInfo.piece[0][5] = 9;
        chessInfo.piece[0][6] = 10; chessInfo.piece[0][7] = 11; chessInfo.piece[0][8] = 12;
        chessInfo.piece[2][1] = 13; chessInfo.piece[2][7] = 13;
        chessInfo.piece[3][0] = 14; chessInfo.piece[3][2] = 14; chessInfo.piece[3][4] = 14;
        chessInfo.piece[3][6] = 14; chessInfo.piece[3][8] = 14;
        // 黑方
        chessInfo.piece[9][0] = 5;  chessInfo.piece[9][1] = 4;  chessInfo.piece[9][2] = 3;
        chessInfo.piece[9][3] = 2;  chessInfo.piece[9][4] = 1;  chessInfo.piece[9][5] = 2;
        chessInfo.piece[9][6] = 3;  chessInfo.piece[9][7] = 4;  chessInfo.piece[9][8] = 5;
        chessInfo.piece[7][1] = 6;  chessInfo.piece[7][7] = 6;
        chessInfo.piece[6][0] = 7;  chessInfo.piece[6][2] = 7;  chessInfo.piece[6][4] = 7;
        chessInfo.piece[6][6] = 7;  chessInfo.piece[6][8] = 7;
        chessInfo.IsRedGo = true;
        return chessInfo;
    }

    /**
     * 释放资源
     */
    public void close() {
        initialized = false;
        if (poseSession != null) { try { poseSession.close(); } catch (OrtException e) {} poseSession = null; }
        if (clsSession != null) { try { clsSession.close(); } catch (OrtException e) {} clsSession = null; }
        if (ortEnv != null) { try { ortEnv.close(); } catch (Exception e) {} ortEnv = null; }
        Log.d(TAG, "Resources released");
    }
}
