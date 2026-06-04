package CustomView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import top.nones.chessgame.R;

import Utils.LogUtils;

import Info.ChessInfo;

public class SetupModeView extends View {
    private ChessInfo chessInfo;
    private Paint paint;
    private Bitmap[] RP = new Bitmap[7];
    private Bitmap[] BP = new Bitmap[7];
    private int selectedSetupPieceID = 0;
    private OnSetupModeListener listener;

    public interface OnSetupModeListener {
        void onPieceSelected(int pieceID);
        void onClearBoard();
        void onHelpClicked();
        void onCameraClicked();
        void onGalleryClicked();
    }

    public SetupModeView(Context context, ChessInfo chessInfo) {
        super(context);
        this.chessInfo = chessInfo;
        init();
    }

    public void setOnSetupModeListener(OnSetupModeListener listener) {
        this.listener = listener;
    }

    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
        invalidate();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);

        // 将dp转换为像素
        int pieceSize = (int) convertDpToPixel(35, getContext());

        try {
            // 加载棋子图片
            RP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shuai, pieceSize, pieceSize);
            RP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shi, pieceSize, pieceSize);
            RP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_xiang, pieceSize, pieceSize);
            RP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ma, pieceSize, pieceSize);
            RP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ju, pieceSize, pieceSize);
            RP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_pao, pieceSize, pieceSize);
            RP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_bing, pieceSize, pieceSize);

            BP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_jiang, pieceSize, pieceSize);
            BP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_shi, pieceSize, pieceSize);
            BP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_xiang, pieceSize, pieceSize);
            BP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ma, pieceSize, pieceSize);
            BP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ju, pieceSize, pieceSize);
            BP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_pao, pieceSize, pieceSize);
            BP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_zu, pieceSize, pieceSize);
        } catch (Exception e) {
            LogUtils.e("SetupModeView", "Error loading images: " + e.getMessage());
            // 确保即使图片加载失败，应用也能继续运行
        }
    }

    // 优化图片加载，减少内存使用
    private android.graphics.Bitmap decodeSampledBitmapFromResource(android.content.res.Resources res, int resId, int reqWidth, int reqHeight) {
        // 首先获取图片的边界信息，而不加载整个图片
        final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(res, resId, options);

        // 计算采样率
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 使用计算出的采样率加载图片
        options.inJustDecodeBounds = false;
        return android.graphics.BitmapFactory.decodeResource(res, resId, options);
    }

    // 计算图片采样率
    private int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 原始图片的宽高
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的采样率，使得采样后的图片宽高仍大于等于需求的宽高
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chessInfo == null) return;

        // 绘制背景
        paint.setColor(Color.parseColor("#F0F0F0"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // 将dp转换为像素
        float textSize = convertDpToPixel(12, getContext());
        int pieceSize = (int) convertDpToPixel(35, getContext());
        int padding = (int) convertDpToPixel(2, getContext());
        int titleY = (int) convertDpToPixel(15, getContext());

        // 绘制标题
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("选择棋子", getWidth() / 2, titleY, paint);

        // 计算棋子选择区域的位置和大小
        int windowWidth = getWidth();

        // 计算棋盘上各棋子的数量
        int[] pieceCounts = new int[15]; // 棋子ID从1-14
        if (chessInfo.piece != null) {
            for (int i = 0; i < 10; i++) {
                if (chessInfo.piece[i] != null) {
                    for (int j = 0; j < 9; j++) {
                        int pieceID = chessInfo.piece[i][j];
                        if (pieceID > 0) {
                            pieceCounts[pieceID]++;
                        }
                    }
                }
            }
        }

        // 计算黑棋和红棋的数量
        int blackPieceCount = 0;
        int redPieceCount = 0;
        for (int i = 0; i < BP.length; i++) {
            int pieceID = i + 1; // 黑棋ID: 1-7
            int maxCount = getMaxPieceCount(pieceID);
            if (BP[i] != null && pieceCounts[pieceID] < maxCount) {
                blackPieceCount++;
            }
        }
        for (int i = 0; i < RP.length; i++) {
            int pieceID = i + 8; // 红棋ID: 8-14
            int maxCount = getMaxPieceCount(pieceID);
            if (RP[i] != null && pieceCounts[pieceID] < maxCount) {
                redPieceCount++;
            }
        }

        // 将dp转换为像素
        int blackStartY = (int) convertDpToPixel(20, getContext());
        int redYOffset = (int) convertDpToPixel(5, getContext());

        // 计算每行棋子的总宽度和起始X坐标，使棋子居中
        int blackTotalWidth = blackPieceCount * (pieceSize + padding) - padding;
        int blackStartX = (windowWidth - blackTotalWidth) / 2;
        int blackCurrentX = blackStartX;

        // 绘制黑棋
        for (int i = 0; i < BP.length; i++) {
            int pieceID = i + 1; // 黑棋ID: 1-7
            int maxCount = getMaxPieceCount(pieceID);
            if (BP[i] != null && pieceCounts[pieceID] < maxCount) {
                int x = blackCurrentX;
                int y = blackStartY;
                Rect pSrcRect = new Rect(0, 0, BP[i].getWidth(), BP[i].getHeight());
                Rect pDesRect = new Rect(x, y, x + pieceSize, y + pieceSize);
                canvas.drawBitmap(BP[i], pSrcRect, pDesRect, null);

                // 绘制选中效果
                if (selectedSetupPieceID == pieceID) {
                    // 绘制选中背景（半透明）
                    paint.setColor(Color.parseColor("#FFFFCC"));
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(100); // 设置透明度，0-255，100表示半透明
                    // 使用与API级别16兼容的drawRoundRect方法
                    int borderOffset = (int) convertDpToPixel(2, getContext());
                    float strokeWidth = convertDpToPixel(1.5f, getContext());
                    float selectCornerRadius = convertDpToPixel(3, getContext());
                    android.graphics.RectF rectF = new android.graphics.RectF(x - borderOffset, y - borderOffset, x + pieceSize + borderOffset, y + pieceSize + borderOffset);
                    canvas.drawRoundRect(rectF, selectCornerRadius, selectCornerRadius, paint);

                    // 绘制选中边框
                    paint.setColor(Color.parseColor("#FFD700"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setAlpha(255); // 边框不透明
                    // 直接使用已定义的rectF变量
                    canvas.drawRoundRect(rectF, selectCornerRadius, selectCornerRadius, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(255); // 恢复不透明状态
                }

                blackCurrentX += pieceSize + padding;
            }
        }

        // 计算红棋的起始X坐标，使棋子居中
        int redTotalWidth = redPieceCount * (pieceSize + padding) - padding;
        int redStartX = (windowWidth - redTotalWidth) / 2;
        int redCurrentX = redStartX;
        int redStartY = blackStartY + pieceSize + redYOffset;

        // 绘制红棋
        for (int i = 0; i < RP.length; i++) {
            int pieceID = i + 8; // 红棋ID: 8-14
            int maxCount = getMaxPieceCount(pieceID);
            if (RP[i] != null && pieceCounts[pieceID] < maxCount) {
                int x = redCurrentX;
                int y = redStartY;
                Rect pSrcRect = new Rect(0, 0, RP[i].getWidth(), RP[i].getHeight());
                Rect pDesRect = new Rect(x, y, x + pieceSize, y + pieceSize);
                canvas.drawBitmap(RP[i], pSrcRect, pDesRect, null);

                // 绘制选中效果
                if (selectedSetupPieceID == pieceID) {
                    // 绘制选中背景（半透明）
                    paint.setColor(Color.parseColor("#FFFFCC"));
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(100); // 设置透明度，0-255，100表示半透明
                    // 使用与API级别16兼容的drawRoundRect方法
                    int borderOffset = (int) convertDpToPixel(2, getContext());
                    float strokeWidth = convertDpToPixel(1.5f, getContext());
                    float selectCornerRadius = convertDpToPixel(3, getContext());
                    android.graphics.RectF rectF = new android.graphics.RectF(x - borderOffset, y - borderOffset, x + pieceSize + borderOffset, y + pieceSize + borderOffset);
                    canvas.drawRoundRect(rectF, selectCornerRadius, selectCornerRadius, paint);

                    // 绘制选中边框
                    paint.setColor(Color.parseColor("#FFD700"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setAlpha(255); // 边框不透明
                    // 直接使用已定义的rectF变量
                    canvas.drawRoundRect(rectF, selectCornerRadius, selectCornerRadius, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(255); // 恢复不透明状态
                }

                redCurrentX += pieceSize + padding;
            }
        }

        // 绘制清空棋盘、拍照、图片按钮
        int buttonWidth = (int) convertDpToPixel(90, getContext()); // 3个按钮，每个90dp
        int buttonHeight = (int) convertDpToPixel(25, getContext());
        int buttonYOffset = (int) convertDpToPixel(5, getContext());
        int buttonSpacing = (int) convertDpToPixel(10, getContext());
        int totalButtonsWidth = buttonWidth * 3 + buttonSpacing * 2;
        int startX = (windowWidth - totalButtonsWidth) / 2;
        int buttonY = redStartY + pieceSize + buttonYOffset;

        // 按钮背景颜色
        int[] buttonColors = {
            Color.parseColor("#E0E0E0"), // 清空棋盘
            Color.parseColor("#E8F5E9"), // 拍照
            Color.parseColor("#E3F2FD")  // 图片
        };
        String[] buttonTexts = {"清空棋盘", "拍照", "图片"};

        for (int i = 0; i < 3; i++) {
            int buttonX = startX + i * (buttonWidth + buttonSpacing);

            // 绘制按钮背景
            paint.setColor(buttonColors[i]);
            paint.setStyle(Paint.Style.FILL);
            android.graphics.RectF rectF = new android.graphics.RectF(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
            float buttonCornerRadius = convertDpToPixel(3, getContext());
            canvas.drawRoundRect(rectF, buttonCornerRadius, buttonCornerRadius, paint);

            // 绘制按钮边框
            paint.setColor(Color.parseColor("#333333"));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(convertDpToPixel(1, getContext()));
            canvas.drawRoundRect(rectF, buttonCornerRadius, buttonCornerRadius, paint);

            // 绘制按钮文本
            paint.setStyle(Paint.Style.FILL);
            float buttonTextSize = convertDpToPixel(10, getContext());
            paint.setTextSize(buttonTextSize);
            paint.setColor(Color.parseColor("#333333"));
            canvas.drawText(buttonTexts[i], buttonX + buttonWidth / 2, buttonY + buttonHeight / 2 + buttonTextSize / 2, paint);
        }
    }

    // 获取每种棋子的最大数量
    private int getMaxPieceCount(int pieceID) {
        switch (pieceID) {
            case 1: // 黑将
            case 8: // 红帅
                return 1;
            case 2: // 黑士
            case 3: // 黑象
            case 4: // 黑马
            case 5: // 黑车
            case 6: // 黑炮
            case 9: // 红士
            case 10: // 红相
            case 11: // 红马
            case 12: // 红车
            case 13: // 红炮
                return 2;
            case 7: // 黑卒
            case 14: // 红兵
                return 5;
            default:
                return 0;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
        int height = (int) convertDpToPixel(130, getContext()); // 135dp高度，在清空棋盘按钮下方留出空间

        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (chessInfo == null || !chessInfo.IsSetupMode) return false;

        float x = event.getX();
        float y = event.getY();

        // 将dp转换为像素
        int pieceSize = (int) convertDpToPixel(35, getContext());
        int padding = (int) convertDpToPixel(2, getContext());
        int windowWidth = getWidth();

        // 计算棋盘上各棋子的数量
        int[] pieceCounts = new int[15]; // 棋子ID从1-14
        if (chessInfo.piece != null) {
            for (int i = 0; i < 10; i++) {
                if (chessInfo.piece[i] != null) {
                    for (int j = 0; j < 9; j++) {
                        int pieceID = chessInfo.piece[i][j];
                        if (pieceID > 0) {
                            pieceCounts[pieceID]++;
                        }
                    }
                }
            }
        }

        // 计算黑棋和红棋的数量
        int blackPieceCount = 0;
        int redPieceCount = 0;
        for (int i = 0; i < BP.length; i++) {
            int pieceID = i + 1; // 黑棋ID: 1-7
            int maxCount = getMaxPieceCount(pieceID);
            if (BP[i] != null && pieceCounts[pieceID] < maxCount) {
                blackPieceCount++;
            }
        }
        for (int i = 0; i < RP.length; i++) {
            int pieceID = i + 8; // 红棋ID: 8-14
            int maxCount = getMaxPieceCount(pieceID);
            if (RP[i] != null && pieceCounts[pieceID] < maxCount) {
                redPieceCount++;
            }
        }

        // 将dp转换为像素
        int blackStartY = (int) convertDpToPixel(20, getContext());
        int redYOffset = (int) convertDpToPixel(5, getContext());
        int buttonYOffset = (int) convertDpToPixel(5, getContext());
        int buttonWidth = (int) convertDpToPixel(100, getContext());
        int buttonHeight = (int) convertDpToPixel(25, getContext());

        // 计算黑棋的起始X坐标，与绘制逻辑一致
        int blackTotalWidth = blackPieceCount * (pieceSize + padding) - padding;
        int blackStartX = (windowWidth - blackTotalWidth) / 2;
        int blackCurrentX = blackStartX;

        // 检查黑棋
        for (int i = 0; i < BP.length; i++) {
            int pieceID = i + 1; // 黑棋ID: 1-7
            int maxCount = getMaxPieceCount(pieceID);
            if (BP[i] != null && pieceCounts[pieceID] < maxCount) {
                int pieceX = blackCurrentX;
                int pieceY = blackStartY;
                if (x >= pieceX && x <= pieceX + pieceSize && y >= pieceY && y <= pieceY + pieceSize) {
                    selectedSetupPieceID = pieceID;
                    if (listener != null) {
                        listener.onPieceSelected(pieceID);
                    }
                    invalidate();
                    return true;
                }
                blackCurrentX += pieceSize + padding;
            }
        }

        // 计算红棋的起始X坐标，与绘制逻辑一致
        int redTotalWidth = redPieceCount * (pieceSize + padding) - padding;
        int redStartX = (windowWidth - redTotalWidth) / 2;
        int redCurrentX = redStartX;
        int redStartY = blackStartY + pieceSize + redYOffset;

        // 检查红棋
        for (int i = 0; i < RP.length; i++) {
            int pieceID = i + 8; // 红棋ID: 8-14
            int maxCount = getMaxPieceCount(pieceID);
            if (RP[i] != null && pieceCounts[pieceID] < maxCount) {
                int pieceX = redCurrentX;
                int pieceY = redStartY;
                if (x >= pieceX && x <= pieceX + pieceSize && y >= pieceY && y <= pieceY + pieceSize) {
                    selectedSetupPieceID = pieceID;
                    if (listener != null) {
                        listener.onPieceSelected(pieceID);
                    }
                    invalidate();
                    return true;
                }
                redCurrentX += pieceSize + padding;
            }
        }

        // 检查清空棋盘、拍照、图片按钮
        int btnWidth = (int) convertDpToPixel(90, getContext());
        int btnHeight = (int) convertDpToPixel(25, getContext());
        int btnSpacing = (int) convertDpToPixel(10, getContext());
        int totalBtnsWidth = btnWidth * 3 + btnSpacing * 2;
        int btnStartX = (windowWidth - totalBtnsWidth) / 2;
        int btnY = redStartY + pieceSize + buttonYOffset;

        for (int i = 0; i < 3; i++) {
            int buttonX = btnStartX + i * (btnWidth + btnSpacing);
            if (x >= buttonX && x <= buttonX + btnWidth && y >= btnY && y <= btnY + btnHeight) {
                if (listener != null) {
                    if (i == 0) {
                        listener.onClearBoard();
                    } else if (i == 1) {
                        listener.onCameraClicked();
                    } else if (i == 2) {
                        listener.onGalleryClicked();
                    }
                }
                return true;
            }
        }

        // 未点击到任何元素，重置选中状态
        if (selectedSetupPieceID != 0) {
            selectedSetupPieceID = 0;
            invalidate();
        }

        return super.onTouchEvent(event);
    }

    public void clearSelection() {
        selectedSetupPieceID = 0;
        invalidate();
    }
    
    // 将dp转换为像素
    private float convertDpToPixel(float dp, Context context) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
