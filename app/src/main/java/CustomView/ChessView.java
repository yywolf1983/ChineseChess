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

    // 摆棋窗口拖动相关变量
    private boolean isDragging = false;
    private float lastX = 0;
    private float lastY = 0;
    private int setupWindowX = 0;
    private int setupWindowY = 0;
    // 摆棋UI选中的棋子ID
    private int selectedSetupPieceID = 0;

    public String[] thinkMood = new String[]{"😀", "🙂", "😶", "😣", "😵", "😭"};
    public int thinkIndex = 0;
    public int thinkFlag = 0;
    public String thinkContent = "😀·····";

    public ChessView(Context context, ChessInfo chessInfo) {
        super(context);
        this.chessInfo = chessInfo;
        getHolder().addCallback(this);
        init();
        // 初始化摆棋窗口的默认位置
        setupWindowX = 0;
        setupWindowY = 0;
    }
    
    // 设置ChessInfo对象
    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
    }

    public void init() {
        // 加载棋盘图片并检查是否成功
        ChessBoard = BitmapFactory.decodeResource(getResources(), R.drawable.chessboard);
        if (ChessBoard == null) {
            android.util.Log.e("ChessView", "Failed to load chessboard image");
        } else {
            android.util.Log.i("ChessView", "Successfully loaded chessboard image: " + ChessBoard.getWidth() + "x" + ChessBoard.getHeight());
        }

        B_box = BitmapFactory.decodeResource(getResources(), R.drawable.b_box);
        R_box = BitmapFactory.decodeResource(getResources(), R.drawable.r_box);
        Pot = BitmapFactory.decodeResource(getResources(), R.drawable.pot);

        RP[0] = BitmapFactory.decodeResource(getResources(), R.drawable.r_shuai);
        RP[1] = BitmapFactory.decodeResource(getResources(), R.drawable.r_shi);
        RP[2] = BitmapFactory.decodeResource(getResources(), R.drawable.r_xiang);
        RP[3] = BitmapFactory.decodeResource(getResources(), R.drawable.r_ma);
        RP[4] = BitmapFactory.decodeResource(getResources(), R.drawable.r_ju);
        RP[5] = BitmapFactory.decodeResource(getResources(), R.drawable.r_pao);
        RP[6] = BitmapFactory.decodeResource(getResources(), R.drawable.r_bing);

        BP[0] = BitmapFactory.decodeResource(getResources(), R.drawable.b_jiang);
        BP[1] = BitmapFactory.decodeResource(getResources(), R.drawable.b_shi);
        BP[2] = BitmapFactory.decodeResource(getResources(), R.drawable.b_xiang);
        BP[3] = BitmapFactory.decodeResource(getResources(), R.drawable.b_ma);
        BP[4] = BitmapFactory.decodeResource(getResources(), R.drawable.b_ju);
        BP[5] = BitmapFactory.decodeResource(getResources(), R.drawable.b_pao);
        BP[6] = BitmapFactory.decodeResource(getResources(), R.drawable.b_zu);
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
        Rect pSrcRect, pDesRect;

        for (int i = 0; i < chessInfo.piece.length; i++) {
            for (int j = 0; j < chessInfo.piece[i].length; j++) {
                if (chessInfo.piece[i][j] > 0) {
                    // 反转y坐标，确保红棋在屏幕下方，黑棋在屏幕上方
                    int drawY = 9 - i;
                    pDesRect = new Rect(Scale(j * 85 + 3), Scale(drawY * 85 + 41), Scale(j * 85 + 83), Scale(drawY * 85 + 121));
                    if (chessInfo.piece[i][j] <= 7) {
                        int num = chessInfo.piece[i][j] - 1;
                        pSrcRect = new Rect(0, 0, BP[num].getWidth(), BP[num].getHeight());
                        canvas.drawBitmap(BP[num], pSrcRect, pDesRect, null);
                    }
                    if (chessInfo.piece[i][j] >= 8) {
                        int num = chessInfo.piece[i][j] - 8;
                        pSrcRect = new Rect(0, 0, RP[num].getWidth(), RP[num].getHeight());
                        canvas.drawBitmap(RP[num], pSrcRect, pDesRect, null);
                    }
                }
            }
        }

        int drawX = chessInfo.Select[0], drawY = chessInfo.Select[1];
        if (drawX >= 0 && drawY >= 0 && drawY >= 0 && drawY < 10 && drawX >= 0 && drawX < 9 && chessInfo.piece[drawY][drawX] > 0) {
            int piece = chessInfo.piece[drawY][drawX];
            boolean isRedPiece = piece >= 8 && piece <= 14;
            
            // 反转y坐标，确保选中效果显示在正确的位置
            int displayY = 9 - drawY;
            // 绘制选中效果，无论当前是哪个玩家的回合
            if (isRedPiece) {
                pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                pDesRect = new Rect(Scale(drawX * 85 + 3), Scale(displayY * 85 + 41), Scale(drawX * 85 + 83), Scale(displayY * 85 + 121));
                canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
            } else {
                pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                pDesRect = new Rect(Scale(drawX * 85 + 3), Scale(displayY * 85 + 41), Scale(drawX * 85 + 83), Scale(displayY * 85 + 121));
                canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
            }
            
            // 绘制可移动位置
            Iterator<Pos> it = chessInfo.ret.iterator();
            while (it.hasNext()) {
                Pos pos = it.next();
                int x = pos.x, y = pos.y;
                // 反转y坐标，确保可移动位置显示在正确的位置
                int displayPosY = 9 - y;
                pSrcRect = new Rect(0, 0, Pot.getWidth(), Pot.getHeight());
                pDesRect = new Rect(Scale(x * 85 + 3), Scale(displayPosY * 85 + 41), Scale(x * 85 + 83), Scale(displayPosY * 85 + 121));
                canvas.drawBitmap(Pot, pSrcRect, pDesRect, null);
            }
        }

        if (chessInfo.prePos != null && chessInfo.curPos != null && chessInfo.prePos.equals(new Pos(-1, -1)) == false && chessInfo.IsChecked == false) {
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

            if (real_curY >= 0 && real_curY < 10 && real_curX >= 0 && real_curX < 9 && chessInfo.piece[real_curY][real_curX] >= 1 && chessInfo.piece[real_curY][real_curX] <= 7) {
                pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                canvas.drawBitmap(B_box, pSrcRect, tmpRect, null);
            } else {
                pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                canvas.drawBitmap(R_box, pSrcRect, tmpRect, null);
            }
        }

        if (chessInfo.IsSetupMode) {
            // 绘制摆棋模式的棋子选择区域
            drawSetupModePieces(canvas);
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
            paint.setStrokeWidth(16);
            paint.setAlpha(150); // 半透明效果
            
            // 绘制提示线
            canvas.drawLine(fromCenterX, fromCenterY, toCenterX, toCenterY, paint);
            
            // 绘制起点和终点的圆圈
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);
            canvas.drawCircle(fromCenterX, fromCenterY, Scale(20), paint);
            canvas.drawCircle(toCenterX, toCenterY, Scale(20), paint);
            
            // 重置画笔样式
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            paint.setAlpha(255);
        }

        if (chessInfo.status == 1) {
            if (chessInfo.isMachine == true) {
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

    // 绘制摆棋模式的棋子选择区域
    private void drawSetupModePieces(Canvas canvas) {
        // 计算棋子选择区域的位置和大小（浮动窗口形式）
        int pieceSize = Scale(50);
        int padding = Scale(15);
        int windowWidth = Scale(400);
        int windowHeight = Scale(250); // 增加高度以容纳清空按钮
        
        // 使用可拖动的窗口位置
        int startX = setupWindowX;
        int startY = setupWindowY;
        
        // 确保窗口不会超出屏幕边界
        if (startX < 0) startX = 0;
        if (startY < 0) startY = 0;
        if (startX + windowWidth > Board_width) startX = Board_width - windowWidth;
        if (startY + windowHeight > Board_height) startY = Board_height - windowHeight;
        
        // 绘制浮动窗口阴影
        paint.setColor(Color.parseColor("#80000000"));
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path shadowPath = new android.graphics.Path();
        float radius = Scale(10);
        shadowPath.addRoundRect(new android.graphics.RectF(startX + Scale(5), startY + Scale(5), startX + windowWidth + Scale(5), startY + windowHeight + Scale(5)), radius, radius, android.graphics.Path.Direction.CW);
        canvas.drawPath(shadowPath, paint);
        
        // 绘制浮动窗口背景
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        // 使用兼容API 16的方法绘制圆角矩形
        android.graphics.Path path = new android.graphics.Path();
        path.addRoundRect(new android.graphics.RectF(startX, startY, startX + windowWidth, startY + windowHeight), radius, radius, android.graphics.Path.Direction.CW);
        canvas.drawPath(path, paint);
        
        // 绘制窗口边框
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(path, paint);
        
        // 绘制窗口标题栏背景
        paint.setColor(Color.parseColor("#F0F0F0"));
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path titlePath = new android.graphics.Path();
        titlePath.addRoundRect(new android.graphics.RectF(startX, startY, startX + windowWidth, startY + Scale(40)), radius, radius, android.graphics.Path.Direction.CW);
        canvas.drawPath(titlePath, paint);
        
        // 绘制标题栏边框
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawPath(titlePath, paint);
        
        // 绘制标题
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Scale(25));
        paint.setColor(Color.parseColor("#333333"));
        canvas.drawText("选择棋子", startX + windowWidth / 2, startY + Scale(30), paint);
        
        // 绘制帮助按钮
        int helpButtonSize = Scale(30);
        int helpButtonX = startX + windowWidth - helpButtonSize - padding;
        int helpButtonY = startY + (Scale(40) - helpButtonSize) / 2;
        
        // 绘制帮助按钮背景
        paint.setColor(Color.parseColor("#E0E0E0"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(helpButtonX + helpButtonSize / 2, helpButtonY + helpButtonSize / 2, helpButtonSize / 2, paint);
        
        // 绘制帮助按钮边框
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(helpButtonX + helpButtonSize / 2, helpButtonY + helpButtonSize / 2, helpButtonSize / 2, paint);
        
        // 绘制帮助按钮文本
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Scale(20));
        paint.setColor(Color.parseColor("#333333"));
        canvas.drawText("?", helpButtonX + helpButtonSize / 2, helpButtonY + helpButtonSize / 2 + Scale(8), paint);
        
        // 计算棋盘上各棋子的数量
        int[] pieceCounts = new int[15]; // 棋子ID从1-14
        if (chessInfo != null && chessInfo.piece != null) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int pieceID = chessInfo.piece[i][j];
                    if (pieceID > 0) {
                        pieceCounts[pieceID]++;
                    }
                }
            }
        }
        
        // 绘制黑棋
        String[] blackPieceNames = {"将", "士", "象", "马", "车", "炮", "卒"};
        int blackStartX = startX + padding;
        int blackCurrentX = blackStartX;
        int blackStartY = startY + Scale(50);
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
                    // 添加选中动画效果
                    long currentTime = System.currentTimeMillis();
                    float scale = 1.0f + (float)Math.sin(currentTime / 300.0) * 0.1f;
                    int scaledSize = (int)(pieceSize * scale);
                    int offset = (pieceSize - scaledSize) / 2;
                    
                    // 绘制选中背景
                    paint.setColor(Color.parseColor("#FFFFCC"));
                    paint.setStyle(Paint.Style.FILL);
                    // 使用兼容 API 16 的方法绘制圆角矩形
                    android.graphics.Path selectedPath = new android.graphics.Path();
                    float selectedRadius = Scale(5);
                    selectedPath.addRoundRect(new android.graphics.RectF(x - 4, y - 4, x + pieceSize + 4, y + pieceSize + 4), selectedRadius, selectedRadius, android.graphics.Path.Direction.CW);
                    canvas.drawPath(selectedPath, paint);
                    
                    // 绘制选中边框
                    paint.setColor(Color.parseColor("#FFD700"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3);
                    canvas.drawPath(selectedPath, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                
                // 绘制棋子名称
                paint.setTextSize(Scale(15));
                canvas.drawText(blackPieceNames[i], x + pieceSize / 2, y + pieceSize + Scale(20), paint);
                blackCurrentX += pieceSize + padding;
            }
        }
        
        // 绘制红棋
        String[] redPieceNames = {"帅", "士", "相", "马", "车", "炮", "兵"};
        int redStartX = startX + padding;
        int redCurrentX = redStartX;
        int redStartY = blackStartY + pieceSize + Scale(30);
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
                    // 添加选中动画效果
                    long currentTime = System.currentTimeMillis();
                    float scale = 1.0f + (float)Math.sin(currentTime / 300.0) * 0.1f;
                    int scaledSize = (int)(pieceSize * scale);
                    int offset = (pieceSize - scaledSize) / 2;
                    
                    // 绘制选中背景
                    paint.setColor(Color.parseColor("#FFFFCC"));
                    paint.setStyle(Paint.Style.FILL);
                    // 使用兼容 API 16 的方法绘制圆角矩形
                    android.graphics.Path selectedPath = new android.graphics.Path();
                    float selectedRadius = Scale(5);
                    selectedPath.addRoundRect(new android.graphics.RectF(x - 4, y - 4, x + pieceSize + 4, y + pieceSize + 4), selectedRadius, selectedRadius, android.graphics.Path.Direction.CW);
                    canvas.drawPath(selectedPath, paint);
                    
                    // 绘制选中边框
                    paint.setColor(Color.parseColor("#FFD700"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3);
                    canvas.drawPath(selectedPath, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                
                // 绘制棋子名称
                paint.setTextSize(Scale(15));
                canvas.drawText(redPieceNames[i], x + pieceSize / 2, y + pieceSize + Scale(20), paint);
                redCurrentX += pieceSize + padding;
            }
        }
        
        // 绘制清空棋盘按钮
        int buttonWidth = Scale(150);
        int buttonHeight = Scale(40);
        int buttonX = startX + (windowWidth - buttonWidth) / 2;
        int buttonY = startY + windowHeight - buttonHeight - padding;
        
        // 绘制按钮阴影
        paint.setColor(Color.parseColor("#80000000"));
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path buttonShadowPath = new android.graphics.Path();
        float buttonRadius = Scale(5);
        buttonShadowPath.addRoundRect(new android.graphics.RectF(buttonX + Scale(2), buttonY + Scale(2), buttonX + buttonWidth + Scale(2), buttonY + buttonHeight + Scale(2)), buttonRadius, buttonRadius, android.graphics.Path.Direction.CW);
        canvas.drawPath(buttonShadowPath, paint);
        
        // 绘制按钮背景
        paint.setColor(Color.parseColor("#E0E0E0"));
        paint.setStyle(Paint.Style.FILL);
        // 使用兼容API 16的方法绘制圆角矩形
        android.graphics.Path buttonPath = new android.graphics.Path();
        buttonPath.addRoundRect(new android.graphics.RectF(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight), buttonRadius, buttonRadius, android.graphics.Path.Direction.CW);
        canvas.drawPath(buttonPath, paint);
        
        // 绘制按钮边框
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawPath(buttonPath, paint);
        
        // 绘制按钮文本
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Scale(20));
        paint.setColor(Color.parseColor("#333333"));
        canvas.drawText("清空棋盘", buttonX + buttonWidth / 2, buttonY + buttonHeight / 2 + Scale(8), paint);
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

    // 检查点击是否在摆棋模式的棋子选择区域
    public int getSetupModePieceAt(float x, float y) {
        if (!chessInfo.IsSetupMode) return 0;
        
        int pieceSize = Scale(50);
        int padding = Scale(15);
        int windowWidth = Scale(400);
        int windowHeight = Scale(250); // 增加高度以容纳清空按钮
        
        // 使用可拖动的窗口位置
        int startX = setupWindowX;
        int startY = setupWindowY;
        
        // 确保窗口不会超出屏幕边界
        if (startX < 0) startX = 0;
        if (startY < 0) startY = 0;
        if (startX + windowWidth > Board_width) startX = Board_width - windowWidth;
        if (startY + windowHeight > Board_height) startY = Board_height - windowHeight;
        
        // 检查是否点击在帮助按钮上
        int helpButtonSize = Scale(30);
        int helpButtonX = startX + windowWidth - helpButtonSize - padding;
        int helpButtonY = startY + (Scale(40) - helpButtonSize) / 2;
        if (x >= helpButtonX && x <= helpButtonX + helpButtonSize && y >= helpButtonY && y <= helpButtonY + helpButtonSize) {
            // 显示帮助信息
            return -2; // 表示点击了帮助按钮
        }
        
        // 检查是否点击在清空棋盘按钮上
        int buttonWidth = Scale(150);
        int buttonHeight = Scale(40);
        int buttonX = startX + (windowWidth - buttonWidth) / 2;
        int buttonY = startY + windowHeight - buttonHeight - padding;
        if (x >= buttonX && x <= buttonX + buttonWidth && y >= buttonY && y <= buttonY + buttonHeight) {
            clearBoard();
            return -1; // 表示点击了清空按钮
        }
        
        // 计算棋盘上各棋子的数量
        int[] pieceCounts = new int[15]; // 棋子ID从1-14
        if (chessInfo != null && chessInfo.piece != null) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int pieceID = chessInfo.piece[i][j];
                    if (pieceID > 0) {
                        pieceCounts[pieceID]++;
                    }
                }
            }
        }
        
        // 检查黑棋
        int blackStartX = startX + padding;
        int blackCurrentX = blackStartX;
        int blackStartY = startY + Scale(50);
        for (int i = 0; i < BP.length; i++) {
            int pieceID = i + 1; // 黑棋ID: 1-7
            int maxCount = getMaxPieceCount(pieceID);
            if (BP[i] != null && pieceCounts[pieceID] < maxCount) {
                int pieceX = blackCurrentX;
                int pieceY = blackStartY;
                if (x >= pieceX && x <= pieceX + pieceSize && y >= pieceY && y <= pieceY + pieceSize) {
                    selectedSetupPieceID = pieceID;
                    requestDraw(); // 重新绘制以显示选中效果
                    return pieceID; // 黑棋ID: 1-7
                }
                blackCurrentX += pieceSize + padding;
            }
        }
        
        // 检查红棋
        int redStartX = startX + padding;
        int redCurrentX = redStartX;
        int redStartY = blackStartY + pieceSize + Scale(30);
        for (int i = 0; i < RP.length; i++) {
            int pieceID = i + 8; // 红棋ID: 8-14
            int maxCount = getMaxPieceCount(pieceID);
            if (RP[i] != null && pieceCounts[pieceID] < maxCount) {
                int pieceX = redCurrentX;
                int pieceY = redStartY;
                if (x >= pieceX && x <= pieceX + pieceSize && y >= pieceY && y <= pieceY + pieceSize) {
                    selectedSetupPieceID = pieceID;
                    requestDraw(); // 重新绘制以显示选中效果
                    return pieceID; // 红棋ID: 8-14
                }
                redCurrentX += pieceSize + padding;
            }
        }
        
        // 未点击到棋子，重置选中状态
        if (selectedSetupPieceID != 0) {
            selectedSetupPieceID = 0;
            requestDraw(); // 重新绘制以移除选中效果
        }
        return 0; // 未点击到棋子
    }
    
    // 清空棋盘，除了老将
    public void clearBoard() {
        if (chessInfo != null && chessInfo.piece != null) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int pieceID = chessInfo.piece[i][j];
                    // 保留老将（将、帅）
                    if (pieceID != 1 && pieceID != 8) {
                        chessInfo.piece[i][j] = 0;
                    }
                }
            }
            // 重新绘制
            requestDraw();
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
    
    // 当摆棋模式状态改变时调用，重新测量View高度
    public void onSetupModeChanged() {
        // 重新测量View
        requestLayout();
        // 重新绘制
        requestDraw();
    }
    
    // 处理触摸事件，实现摆棋窗口的拖动功能
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (chessInfo != null && chessInfo.IsSetupMode) {
            float x = event.getX();
            float y = event.getY();
            
            // 计算摆棋窗口的位置和大小
            int pieceSize = Scale(50);
            int padding = Scale(15);
            int windowWidth = Scale(400);
            int windowHeight = Scale(250); // 与drawSetupModePieces中的窗口高度保持一致
            
            int startX = setupWindowX;
            int startY = setupWindowY;
            
            // 确保窗口不会超出屏幕边界
            if (startX < 0) startX = 0;
            if (startY < 0) startY = 0;
            if (startX + windowWidth > Board_width) startX = Board_width - windowWidth;
            if (startY + windowHeight > Board_height) startY = Board_height - windowHeight;
            
            // 检查是否点击在摆棋窗口内
            boolean isInWindow = x >= startX && x <= startX + windowWidth && y >= startY && y <= startY + windowHeight;
            // 检查是否点击在摆棋窗口的标题栏区域
            boolean isInTitleBar = x >= startX && x <= startX + windowWidth && y >= startY && y <= startY + Scale(40);
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isInWindow) {
                        if (isInTitleBar) {
                            isDragging = true;
                            lastX = x;
                            lastY = y;
                            return true; // 表示事件已处理，防止点击穿透
                        } else {
                            // 点击在窗口内但不在标题栏，可能是点击棋子或清空按钮
                            // 调用getSetupModePieceAt处理点击
                            int pieceID = getSetupModePieceAt(x, y);
                            if (pieceID != 0) {
                                // 处理了点击事件（选中棋子或点击清空按钮）
                                requestDraw(); // 重新绘制以显示选中效果
                                return true;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        // 计算移动距离
                        float deltaX = x - lastX;
                        float deltaY = y - lastY;
                        
                        // 更新窗口位置
                        setupWindowX += deltaX;
                        setupWindowY += deltaY;
                        
                        // 保存当前位置
                        lastX = x;
                        lastY = y;
                        
                        // 重新绘制
                        requestDraw();
                        return true; // 表示事件已处理
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (isDragging) {
                        isDragging = false;
                        return true; // 表示事件已处理
                    }
                    break;
            }
        }
        
        // 让父类处理其他触摸事件
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