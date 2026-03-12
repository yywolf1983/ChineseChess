package CustomView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import top.nones.chessgame.R;

import java.util.Iterator;

import ChessMove.Rule;
import Info.ChessInfo;
import Info.Pos;

/**
 * Created by 77304 on 2021/4/5.
 */

public class ChessView extends SurfaceView implements SurfaceHolder.Callback {
    public Paint paint;

    public Bitmap ChessBoard;
    public Bitmap B_box, R_box, Pot;
    public Bitmap[] RP = new Bitmap[7];
    public Bitmap[] BP = new Bitmap[7];

    public Rect cSrcRect, cDesRect;

    public int Board_width, Board_height;

    public ChessInfo chessInfo;



    public String[] thinkMood = new String[]{"😀", "🙂", "😶", "😣", "😵", "😭"};
    public int thinkIndex = 0;
    public int thinkFlag = 0;
    public String thinkContent = "😀·····";

    public ChessView(Context context, ChessInfo chessInfo) {
        super(context);
        this.chessInfo = chessInfo;
        getHolder().addCallback(this);
        init();
    }
    
    // 设置ChessInfo对象
    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
    }

    public void init() {
        try {
            // 加载棋盘图片并检查是否成功
            ChessBoard = decodeSampledBitmapFromResource(getResources(), R.drawable.chessboard, 768, 909);
            if (ChessBoard == null) {
                android.util.Log.e("ChessView", "Failed to load chessboard image");
            } else {
                android.util.Log.i("ChessView", "Successfully loaded chessboard image: " + ChessBoard.getWidth() + "x" + ChessBoard.getHeight());
            }

            B_box = decodeSampledBitmapFromResource(getResources(), R.drawable.b_box, 80, 80);
            R_box = decodeSampledBitmapFromResource(getResources(), R.drawable.r_box, 80, 80);
            Pot = decodeSampledBitmapFromResource(getResources(), R.drawable.pot, 80, 80);

            RP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shuai, 80, 80);
            RP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shi, 80, 80);
            RP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_xiang, 80, 80);
            RP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ma, 80, 80);
            RP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ju, 80, 80);
            RP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_pao, 80, 80);
            RP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_bing, 80, 80);

            BP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_jiang, 80, 80);
            BP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_shi, 80, 80);
            BP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_xiang, 80, 80);
            BP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ma, 80, 80);
            BP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ju, 80, 80);
            BP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_pao, 80, 80);
            BP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_zu, 80, 80);
        } catch (Exception e) {
            android.util.Log.e("ChessView", "Error loading images: " + e.getMessage());
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

    public void Draw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        // 添加空指针检查，确保 ChessBoard 不为 null 时才绘制
        if (ChessBoard != null && cSrcRect != null && cDesRect != null) {
            // 绘制棋盘图片
            canvas.drawBitmap(ChessBoard, cSrcRect, cDesRect, null);
            // 添加日志，检查绘制参数
            android.util.Log.i("ChessView", "Drawing chessboard: cSrcRect=" + cSrcRect.toString() + ", cDesRect=" + cDesRect.toString());
        } else {
            // 当棋盘图片加载失败时，绘制一个简单的棋盘网格
            drawChessboardGrid(canvas);
            android.util.Log.i("ChessView", "Drawing chessboard grid instead of bitmap");
        }
        
        // 绘制传统坐标
        drawTraditionalCoordinates(canvas);
        
        // 添加空指针检查，确保 chessInfo 不为 null
        if (chessInfo == null) {
            return;
        }
        
        Rect pSrcRect, pDesRect;

        // 检查 chessInfo.piece 是否为 null
        if (chessInfo.piece != null) {
            for (int i = 0; i < chessInfo.piece.length; i++) {
                if (chessInfo.piece[i] != null) {
                    for (int j = 0; j < chessInfo.piece[i].length; j++) {
                        if (chessInfo.piece[i][j] > 0) {
                            // 反转y坐标，确保红棋在屏幕下方，黑棋在屏幕上方
                            int drawY = 9 - i;
                            pDesRect = new Rect(Scale(j * 85 + 3), Scale(drawY * 85 + 41), Scale(j * 85 + 83), Scale(drawY * 85 + 121));
                            if (chessInfo.piece[i][j] <= 7) {
                                int num = chessInfo.piece[i][j] - 1;
                                if (BP != null && num >= 0 && num < BP.length && BP[num] != null) {
                                    pSrcRect = new Rect(0, 0, BP[num].getWidth(), BP[num].getHeight());
                                    canvas.drawBitmap(BP[num], pSrcRect, pDesRect, null);
                                }
                            }
                            if (chessInfo.piece[i][j] >= 8) {
                                int num = chessInfo.piece[i][j] - 8;
                                if (RP != null && num >= 0 && num < RP.length && RP[num] != null) {
                                    pSrcRect = new Rect(0, 0, RP[num].getWidth(), RP[num].getHeight());
                                    canvas.drawBitmap(RP[num], pSrcRect, pDesRect, null);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 检查 chessInfo.Select 是否为 null
        if (chessInfo.Select != null && chessInfo.Select.length >= 2) {
            int drawX = chessInfo.Select[0], drawY = chessInfo.Select[1];
            if (drawX >= 0 && drawY >= 0 && drawY < 10 && drawX < 9 && chessInfo.piece != null && chessInfo.piece[drawY] != null && chessInfo.piece[drawY][drawX] > 0) {
                int piece = chessInfo.piece[drawY][drawX];
                boolean isRedPiece = piece >= 8 && piece <= 14;
                
                // 反转y坐标，确保选中效果显示在正确的位置
                int displayY = 9 - drawY;
                // 绘制选中效果，无论当前是哪个玩家的回合
                if (isRedPiece && R_box != null) {
                    pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                    pDesRect = new Rect(Scale(drawX * 85 + 3), Scale(displayY * 85 + 41), Scale(drawX * 85 + 83), Scale(displayY * 85 + 121));
                    canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                } else if (B_box != null) {
                    pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                    pDesRect = new Rect(Scale(drawX * 85 + 3), Scale(displayY * 85 + 41), Scale(drawX * 85 + 83), Scale(displayY * 85 + 121));
                    canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                }
                
                // 绘制可移动位置
                if (chessInfo.ret != null) {
                    Iterator<Pos> it = chessInfo.ret.iterator();
                    while (it.hasNext()) {
                        Pos pos = it.next();
                        int x = pos.x, y = pos.y;
                        // 反转y坐标，确保可移动位置显示在正确的位置
                        int displayPosY = 9 - y;
                        if (Pot != null) {
                            pSrcRect = new Rect(0, 0, Pot.getWidth(), Pot.getHeight());
                            pDesRect = new Rect(Scale(x * 85 + 3), Scale(displayPosY * 85 + 41), Scale(x * 85 + 83), Scale(displayPosY * 85 + 121));
                            canvas.drawBitmap(Pot, pSrcRect, pDesRect, null);
                        }
                    }
                }
            }
        }

        if (chessInfo.prePos != null && chessInfo.curPos != null && !chessInfo.prePos.equals(new Pos(-1, -1)) && !chessInfo.IsChecked) {
            // 直接使用原始坐标获取棋子颜色
            int real_curX = chessInfo.curPos.x;
            int real_curY = chessInfo.curPos.y;

            // 反转y坐标，确保走棋轨迹显示在正确的位置
            int draw_preX = chessInfo.prePos.x;
            int draw_preY = 9 - chessInfo.prePos.y;
            int draw_curX = chessInfo.curPos.x;
            int draw_curY = 9 - chessInfo.curPos.y;

            Rect tmpRect;

            pDesRect = new Rect(Scale(draw_curX * 85 + 3), Scale(draw_curY * 85 + 41), Scale(draw_curX * 85 + 83), Scale(draw_curY * 85 + 121));
            tmpRect = new Rect(Scale(draw_preX * 85 + 3), Scale(draw_preY * 85 + 41), Scale(draw_preX * 85 + 83), Scale(draw_preY * 85 + 121));

            if (real_curY >= 0 && real_curY < 10 && real_curX >= 0 && real_curX < 9 && chessInfo.piece != null && chessInfo.piece[real_curY] != null && chessInfo.piece[real_curY][real_curX] >= 1 && chessInfo.piece[real_curY][real_curX] <= 7) {
                if (B_box != null) {
                    pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                    canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                    canvas.drawBitmap(B_box, pSrcRect, tmpRect, null);
                }
            } else {
                if (R_box != null) {
                    pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                    canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                    canvas.drawBitmap(R_box, pSrcRect, tmpRect, null);
                }
            }
        }

        // 绘制支招提示线
        if (chessInfo.suggestFromPos != null && chessInfo.suggestToPos != null) {
            // 反转y坐标，确保提示线显示在正确的位置
            int fromX = chessInfo.suggestFromPos.x;
            int fromY = 9 - chessInfo.suggestFromPos.y;
            int toX = chessInfo.suggestToPos.x;
            int toY = 9 - chessInfo.suggestToPos.y;
            
            // 计算棋子中心点坐标
            int fromCenterX = Scale(fromX * 85 + 43); // 3 + 80/2
            int fromCenterY = Scale(fromY * 85 + 81); // 41 + 80/2
            int toCenterX = Scale(toX * 85 + 43);
            int toCenterY = Scale(toY * 85 + 81);
            
            // 设置画笔样式
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8); // 线宽减半
            paint.setAlpha(150); // 半透明效果
            
            // 绘制带箭头的提示线
            drawArrow(canvas, fromCenterX, fromCenterY, toCenterX, toCenterY, paint);
            
            // 重置画笔样式
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            paint.setAlpha(255);
        }

        if (chessInfo.status == 1) {
            if (chessInfo.isMachine) {
                if (thinkFlag == 0) {
                    thinkContent = "";
                    for (int i = 0; i < thinkIndex; i++) {
                        thinkContent += '·';
                    }
                    thinkContent += thinkMood[thinkIndex];
                    for (int i = thinkIndex + 1; i < 6; i++) {
                        thinkContent += '·';
                    }
                    thinkIndex = (thinkIndex + 1) % 6;
                }
                thinkFlag = (thinkFlag + 1) % 5;
                canvas.drawText(thinkContent, Board_width / 2, Board_height / 2 + Scale(57) * 7 / 20, paint);
            } else {
                thinkIndex = 0;
                thinkContent = "😀·····";
            }
        }
    }



    public int Scale(int x) {
        return x * Board_width / 768;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Board_width = MeasureSpec.getSize(widthMeasureSpec);
        Board_height = Board_width * 909 / 750;

        // 添加空指针检查，确保 ChessBoard 不为 null
        if (ChessBoard != null) {
            cSrcRect = new Rect(0, 0, ChessBoard.getWidth(), ChessBoard.getHeight());
        } else {
            cSrcRect = new Rect(0, 0, Board_width, Board_height);
        }
        cDesRect = new Rect(0, 0, Board_width, Board_height);
        
        // 摆棋UI现在是浮动的，不需要增加View高度
        setMeasuredDimension(Board_width, Board_height);

        paint = new Paint();
        paint.setTextSize(Scale(57));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(1);
        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // 当 Surface 创建时，立即绘制一次棋盘
        if (holder != null) {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    Draw(canvas);
                    android.util.Log.i("ChessView", "Surface created, drawing initial chessboard");
                } catch (Exception e) {
                    android.util.Log.e("ChessView", "Error drawing on surface creation: " + e.getMessage());
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    // 绘制方法，添加空指针检查
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas != null && chessInfo != null) {
            Draw(canvas);
        }
    }

    // 外部调用的绘制方法
    public void requestDraw() {
        SurfaceHolder holder = getHolder();
        if (holder != null) {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    Draw(canvas);
                } catch (Exception e) {
                    // 捕获异常，避免崩溃
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
    

    
    // 处理触摸事件，实现摆棋窗口的拖动功能
    // 绘制带箭头的线段
    private void drawArrow(Canvas canvas, float fromX, float fromY, float toX, float toY, Paint paint) {
        // 绘制直线
        canvas.drawLine(fromX, fromY, toX, toY, paint);
        
        // 计算箭头角度
        double angle = Math.atan2(toY - fromY, toX - fromX);
        
        // 箭头长度和角度
        float arrowLength = 40;
        float arrowAngle = (float) Math.PI / 6; // 30度
        
        // 计算箭头两个点的坐标
        float arrowX1 = (float) (toX - arrowLength * Math.cos(angle - arrowAngle));
        float arrowY1 = (float) (toY - arrowLength * Math.sin(angle - arrowAngle));
        float arrowX2 = (float) (toX - arrowLength * Math.cos(angle + arrowAngle));
        float arrowY2 = (float) (toY - arrowLength * Math.sin(angle + arrowAngle));
        
        // 绘制箭头
        canvas.drawLine(toX, toY, arrowX1, arrowY1, paint);
        canvas.drawLine(toX, toY, arrowX2, arrowY2, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 让父类处理触摸事件
        return super.onTouchEvent(event);
    }

    // 绘制棋盘网格的方法，当棋盘图片加载失败时使用
    private void drawChessboardGrid(Canvas canvas) {
        if (canvas == null) return;
        
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.BLACK);
        gridPaint.setStrokeWidth(2);
        gridPaint.setAntiAlias(true);
        
        // 绘制棋盘边框
        int padding = 30;
        int gridSize = Math.min(Board_width - 2 * padding, Board_height - 2 * padding) / 9;
        int startX = padding;
        int startY = padding;
        int endX = startX + 8 * gridSize;
        int endY = startY + 9 * gridSize;
        
        canvas.drawRect(startX, startY, endX, endY, gridPaint);
        
        // 绘制横线
        for (int i = 0; i <= 9; i++) {
            int y = startY + i * gridSize;
            canvas.drawLine(startX, y, endX, y, gridPaint);
        }
        
        // 绘制竖线
        for (int i = 0; i <= 8; i++) {
            int x = startX + i * gridSize;
            canvas.drawLine(x, startY, x, endY, gridPaint);
        }
        
        // 绘制九宫格
        Paint palacePaint = new Paint();
        palacePaint.setColor(Color.BLACK);
        palacePaint.setStrokeWidth(2);
        palacePaint.setAntiAlias(true);
        
        // 红方九宫格
        int palaceStartY = startY;
        int palaceEndY = startY + 2 * gridSize;
        int palaceMidX = startX + 3 * gridSize;
        canvas.drawLine(startX + 3 * gridSize, palaceStartY, startX + 5 * gridSize, palaceEndY, palacePaint);
        canvas.drawLine(startX + 5 * gridSize, palaceStartY, startX + 3 * gridSize, palaceEndY, palacePaint);
        
        // 黑方九宫格
        palaceStartY = startY + 7 * gridSize;
        palaceEndY = startY + 9 * gridSize;
        canvas.drawLine(startX + 3 * gridSize, palaceStartY, startX + 5 * gridSize, palaceEndY, palacePaint);
        canvas.drawLine(startX + 5 * gridSize, palaceStartY, startX + 3 * gridSize, palaceEndY, palacePaint);
    }
    
    // 绘制传统坐标
    private void drawTraditionalCoordinates(Canvas canvas) {
        // 空实现，不绘制任何坐标
    }
}