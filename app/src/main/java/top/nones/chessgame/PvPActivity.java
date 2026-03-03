package top.nones.chessgame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.media.MediaPlayer;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import ChessMove.Rule;
import CustomView.ChessView;
import CustomView.RoundView;
import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import Info.SaveInfo;
import Info.ChessNotation;
import Info.Setting;
import Utils.LogUtils;


public class PvPActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    // 移除对PvMActivity静态变量的依赖
    private static final int MIN_CLICK_DELAY_TIME = 100;
    private static long curClickTime = 0L;
    private static long lastClickTime = 0L;
    private static MediaPlayer backMusic;
    private static MediaPlayer selectMusic;
    private static MediaPlayer clickMusic;
    private static MediaPlayer checkMusic;
    private static MediaPlayer winMusic;
    private static Setting setting;
    
    private RelativeLayout relativeLayout;
    private ChessInfo chessInfo;
    private InfoSet infoSet;
    private ChessView chessView;
    private RoundView roundView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvp);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        if (SaveInfo.fileIsExists("ChessInfo_pvp.bin")) {
            try {
                chessInfo = SaveInfo.DeserializeChessInfo("ChessInfo_pvp.bin");
            } catch (Exception e) {
                e.printStackTrace();
                chessInfo = new ChessInfo();
            }
        } else {
            chessInfo = new ChessInfo();
        }

        if (SaveInfo.fileIsExists("InfoSet_pvp.bin")) {
            try {
                infoSet = SaveInfo.DeserializeInfoSet("InfoSet_pvp.bin");
                // 确保preInfo栈不为空，否则第一次悔棋会没反应
                if (infoSet.preInfo == null || infoSet.preInfo.isEmpty()) {
                    try {
                        infoSet.pushInfo(chessInfo);
                    } catch (CloneNotSupportedException ce) {
                        ce.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                infoSet = new InfoSet();
                // 确保preInfo栈中有初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException ce) {
                    ce.printStackTrace();
                }
            }
        } else {
            infoSet = new InfoSet();
            // 确保preInfo栈中有初始状态
            try {
                infoSet.pushInfo(chessInfo);
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        // 初始化设置
        if (setting == null) {
            setting = new Setting(getSharedPreferences("setting", MODE_PRIVATE));
        }

        // 初始化音乐
        initMusic();

        roundView = new RoundView(this, chessInfo);
        relativeLayout.addView(roundView);

        RelativeLayout.LayoutParams paramsRound = (RelativeLayout.LayoutParams) roundView.getLayoutParams();
        paramsRound.addRule(RelativeLayout.CENTER_IN_PARENT);
        paramsRound.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        paramsRound.setMargins(30, 30, 30, 30);
        roundView.setLayoutParams(paramsRound);
        roundView.setId(R.id.roundView);

        chessView = new ChessView(this, chessInfo);
        chessView.setOnTouchListener((view, event) -> {
            // 先让ChessView处理触摸事件（用于摆棋窗口拖动和棋子点击）
            boolean handled = chessView.onTouchEvent(event);
            if (handled) {
                // 如果是点击棋子或清空按钮，需要更新Activity中的选中状态
                if (chessInfo.IsSetupMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    int pieceID = chessView.getSetupModePieceAt(x, y);
                    if (pieceID > 0) {
                        // 选中棋子
                        selectedPieceID = pieceID;
                        Toast.makeText(this, "已选中" + (pieceID >= 8 ? "红" : "黑") + "棋", Toast.LENGTH_SHORT).show();
                    } else if (pieceID == -1) {
                        // 点击了清空棋盘按钮
                        Toast.makeText(this, "已清空棋盘（保留老将）", Toast.LENGTH_SHORT).show();
                    } else if (pieceID == -2) {
                        // 点击了帮助按钮
                        showSetupHelp();
                    }
                }
                return true;
            }
            // 否则由Activity处理
            return onTouch(view, event);
        });
        relativeLayout.addView(chessView);

        RelativeLayout.LayoutParams paramsChess = (RelativeLayout.LayoutParams) chessView.getLayoutParams();
        paramsChess.addRule(RelativeLayout.BELOW, R.id.roundView);
        chessView.setLayoutParams(paramsChess);
        chessView.setId(R.id.chessView);

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout buttonGroup = (LinearLayout) inflater.inflate(R.layout.button_group, relativeLayout, false);
        relativeLayout.addView(buttonGroup);

        RelativeLayout.LayoutParams paramsV = (RelativeLayout.LayoutParams) buttonGroup.getLayoutParams();
        paramsV.addRule(RelativeLayout.BELOW, R.id.chessView);
        buttonGroup.setLayoutParams(paramsV);

        for (int i = 0; i < buttonGroup.getChildCount(); i++) {
            Button btn = (Button) buttonGroup.getChildAt(i);
            btn.setOnClickListener(this);
        }

    }

    // 摆棋模式下选中的棋子ID
    private int selectedPieceID = 0;
    // 摆棋模式下选中的棋盘上的棋子位置
    private int[] selectedBoardPiecePos = {-1, -1};
    
    // 保存棋谱相关的临时变量
    private String pendingSaveFileName;
    private String pendingSaveRedPlayer;
    private String pendingSaveBlackPlayer;
    private String pendingSaveDate;
    private String pendingSaveLocation;
    private String pendingSaveEvent;
    private String pendingSaveRound;
    private String pendingSaveResult;

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return false;
        }
        curClickTime = lastClickTime;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (chessInfo != null && chessInfo.status == 1 && chessView != null) {
                // 摆棋模式处理
                if (chessInfo.IsSetupMode) {
                    // 检查是否点击在棋子选择区域
                    int pieceID = chessView.getSetupModePieceAt(x, y);
                    if (pieceID > 0) {
                        // 选中棋子
                        selectedPieceID = pieceID;
                        Toast.makeText(this, "已选中" + (pieceID >= 8 ? "红" : "黑") + "棋", Toast.LENGTH_SHORT).show();
                    } else if (pieceID == -1) {
                        // 点击了清空棋盘按钮
                        Toast.makeText(this, "已清空棋盘（保留老将）", Toast.LENGTH_SHORT).show();
                    } else if (pieceID == -2) {
                        // 点击了帮助按钮
                        showSetupHelp();
                    } 
                    // 检查是否点击在棋盘上
                        else if (x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
                            chessInfo.Select = getPos(event);
                            if (chessInfo.Select != null) {
                                int i = chessInfo.Select[0], j = chessInfo.Select[1];
                                if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && chessInfo.piece != null) {
                                    // 获取点击位置的棋子ID
                                    int boardPieceID = chessInfo.piece[j][i];
                                    
                                    // 如果已经选中了棋盘上的棋子
                                    if (selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                                        // 点击的是空白区域
                                        if (boardPieceID == 0) {
                                            // 获取要操作的棋子ID
                                            int pieceToOperate = chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                                            // 检查是否是老将
                                            if (pieceToOperate == 1 || pieceToOperate == 8) {
                                                // 老将不能下架，但可以移动到合法位置
                                                // 检查新位置是否合理
                                                if (isValidPiecePosition(pieceToOperate, i, j)) {
                                                    // 先将原位置设为0
                                                    chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                    // 再将新位置设为棋子ID
                                                    placePiece(i, j, pieceToOperate);
                                                    // 重置选中状态
                                                    selectedBoardPiecePos[0] = -1;
                                                    selectedBoardPiecePos[1] = -1;
                                                } else {
                                                    Toast.makeText(this, "该位置不适合放置老将", Toast.LENGTH_SHORT).show();
                                                }
                                            } else {
                                                // 不是老将，可以下架或移动
                                                // 检查是否是点击原位置（下架）还是点击新位置（移动）
                                                if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                                    // 点击原位置，下架棋子
                                                    if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                                                        placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                                        // 重置选中状态
                                                        selectedBoardPiecePos[0] = -1;
                                                        selectedBoardPiecePos[1] = -1;
                                                    } else {
                                                        Toast.makeText(this, "老将不能下架", Toast.LENGTH_SHORT).show();
                                                    }
                                                } else {
                                                    // 点击新位置，移动棋子
                                                    // 检查新位置是否合理
                                                    if (isValidPiecePosition(pieceToOperate, i, j)) {
                                                        // 先将原位置设为0
                                                        chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                        // 再将新位置设为棋子ID
                                                        placePiece(i, j, pieceToOperate);
                                                        // 重置选中状态
                                                        selectedBoardPiecePos[0] = -1;
                                                        selectedBoardPiecePos[1] = -1;
                                                    } else {
                                                        Toast.makeText(this, "该位置不适合放置此棋子", Toast.LENGTH_SHORT).show();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                                    else if (selectedPieceID > 0) {
                                        placePiece(i, j, selectedPieceID);
                                        // 重置选中状态
                                        selectedPieceID = 0;
                                    }
                                    // 如果点击的是棋盘上的棋子，选中该棋子
                                    else if (boardPieceID > 0) {
                                        selectedBoardPiecePos[0] = i;
                                        selectedBoardPiecePos[1] = j;
                                        // 显示选中效果
                                        chessInfo.Select = new int[]{i, j};
                                        chessView.requestDraw();
                                        Toast.makeText(this, "已选中棋盘上的棋子，点击空白区域移动或下架", Toast.LENGTH_SHORT).show();
                                    }
                                    // 点击空白区域，重置选中状态
                                    else {
                                        selectedBoardPiecePos[0] = -1;
                                        selectedBoardPiecePos[1] = -1;
                                        chessInfo.Select = new int[]{-1, -1};
                                        chessView.requestDraw();
                                    }
                                }
                            }
                        }
                } 
                // 正常游戏模式处理
                else {
                    if (x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
                        chessInfo.Select = getPos(event);
                        if (chessInfo.Select != null) {
                            int i = chessInfo.Select[0], j = chessInfo.Select[1];
                            if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && chessInfo.piece != null) {
                                if (chessInfo.IsRedGo == true) {
                                    if (chessInfo.IsChecked == false) {
                                        if (chessInfo.piece[j][i] >= 8 && chessInfo.piece[j][i] <= 14) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.IsChecked = true;
                                            chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                                            if (selectMusic != null) {
                                                playEffect(selectMusic);
                                            }
                                        }
                                    } else {
                                        if (chessInfo.piece[j][i] >= 8 && chessInfo.piece[j][i] <= 14) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                                            if (selectMusic != null) {
                                                playEffect(selectMusic);
                                            }
                                        } else if (chessInfo.ret != null && chessInfo.ret.contains(new Pos(i, j))) {
                                            int tmp = chessInfo.piece[j][i];
                                            chessInfo.piece[j][i] = chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x];
                                            chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = 0;
                                            if (Rule.isKingDanger(chessInfo.piece, true)) {
                                                chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = chessInfo.piece[j][i];
                                                chessInfo.piece[j][i] = tmp;
                                                Toast toast = Toast.makeText(PvPActivity.this, "帅被将军", Toast.LENGTH_SHORT);
                                                toast.setGravity(Gravity.CENTER, 0, 0);
                                                toast.show();
                                            } else {
                                                chessInfo.IsChecked = false;
                                                chessInfo.IsRedGo = false;
                                                chessInfo.curPos = new Pos(i, j);

                                                // 生成并记录标准象棋记谱走法
                                                int piece = chessInfo.piece[j][i];
                                                boolean isRed = piece >= 8 && piece <= 14;
                                                String moveString = generateMoveString(piece, chessInfo.prePos, chessInfo.curPos, isRed);
                                                if (moveString != null) {
                                                    LogUtils.i("Move", "红方走棋: " + moveString);
                                                }

                                                chessInfo.updateAllInfo(chessInfo.prePos, chessInfo.curPos, chessInfo.piece[j][i], tmp);

                                                try {
                                                    if (infoSet != null) {
                                                        infoSet.pushInfo(chessInfo);
                                                    }
                                                } catch (CloneNotSupportedException e) {
                                                    e.printStackTrace();
                                                }

                                                if (clickMusic != null) {
                                                    playEffect(clickMusic);
                                                }

                                                int key = 0;
                                                if (Rule.isKingDanger(chessInfo.piece, false)) {
                                                    key = 1;
                                                }
                                                if (Rule.isDead(chessInfo.piece, false)) {
                                                    key = 2;
                                                }
                                                if (key == 1) {
                                                    if (checkMusic != null) {
                                                        playEffect(checkMusic);
                                                    }
                                                    Toast toast = Toast.makeText(PvPActivity.this, "将军", Toast.LENGTH_SHORT);
                                                    toast.setGravity(Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                } else if (key == 2) {
                                                    if (winMusic != null) {
                                                        playEffect(winMusic);
                                                    }
                                                    chessInfo.status = 2;
                                                    Toast toast = Toast.makeText(PvPActivity.this, "红方获得胜利", Toast.LENGTH_SHORT);
                                                    toast.setGravity(Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                }

                                                if (chessInfo.status == 1) {
                                                    if (chessInfo.peaceRound >= 60) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "双方60回合内未吃子，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    } else if (chessInfo.attackNum_B == 0 && chessInfo.attackNum_R == 0) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "双方都无攻击性棋子，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    } else if (infoSet != null && infoSet.ZobristInfo != null && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) >= 4) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "重复局面出现4次，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    }
                                                }

                                                if (chessView != null) {
                                                    chessView.requestDraw();
                                                }
                                                if (roundView != null) {
                                                    roundView.requestDraw();
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (chessInfo.IsChecked == false) {
                                        if (chessInfo.piece[j][i] >= 1 && chessInfo.piece[j][i] <= 7) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.IsChecked = true;
                                            chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                                            if (selectMusic != null) {
                                                playEffect(selectMusic);
                                            }
                                        }
                                    } else {
                                        if (chessInfo.piece[j][i] >= 1 && chessInfo.piece[j][i] <= 7) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                                            if (selectMusic != null) {
                                                playEffect(selectMusic);
                                            }
                                        } else if (chessInfo.ret != null && chessInfo.ret.contains(new Pos(i, j))) {
                                            int tmp = chessInfo.piece[j][i];
                                            chessInfo.piece[j][i] = chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x];
                                            chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = 0;
                                            if (Rule.isKingDanger(chessInfo.piece, false)) {
                                                chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = chessInfo.piece[j][i];
                                                chessInfo.piece[j][i] = tmp;
                                                Toast toast = Toast.makeText(PvPActivity.this, "将被将军", Toast.LENGTH_SHORT);
                                                toast.setGravity(Gravity.CENTER, 0, 0);
                                                toast.show();
                                            } else {
                                                chessInfo.IsChecked = false;
                                                chessInfo.IsRedGo = true;
                                                chessInfo.curPos = new Pos(i, j);

                                                // 生成并记录标准象棋记谱走法
                                                int piece = chessInfo.piece[j][i];
                                                boolean isRed = piece >= 8 && piece <= 14;
                                                String moveString = generateMoveString(piece, chessInfo.prePos, chessInfo.curPos, isRed);
                                                if (moveString != null) {
                                                    LogUtils.i("Move", "黑方走棋: " + moveString);
                                                }

                                                chessInfo.updateAllInfo(chessInfo.prePos, chessInfo.curPos, chessInfo.piece[j][i], tmp);

                                                try {
                                                    if (infoSet != null) {
                                                        infoSet.pushInfo(chessInfo);
                                                    }
                                                } catch (CloneNotSupportedException e) {
                                                    e.printStackTrace();
                                                }

                                                if (clickMusic != null) {
                                                    playEffect(clickMusic);
                                                }

                                                int key = 0;
                                                if (Rule.isKingDanger(chessInfo.piece, true)) {
                                                    key = 1;
                                                }
                                                if (Rule.isDead(chessInfo.piece, true)) {
                                                    key = 2;
                                                }
                                                if (key == 1) {
                                                    if (checkMusic != null) {
                                                        playEffect(checkMusic);
                                                    }
                                                    Toast toast = Toast.makeText(PvPActivity.this, "将军", Toast.LENGTH_SHORT);
                                                    toast.setGravity(Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                } else if (key == 2) {
                                                    if (winMusic != null) {
                                                        playEffect(winMusic);
                                                    }
                                                    chessInfo.status = 2;
                                                    Toast toast = Toast.makeText(PvPActivity.this, "黑方获得胜利", Toast.LENGTH_SHORT);
                                                    toast.setGravity(Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                }

                                                if (chessInfo.status == 1) {
                                                    if (chessInfo.peaceRound >= 60) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "双方60回合内未吃子，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    } else if (chessInfo.attackNum_B == 0 && chessInfo.attackNum_R == 0) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "双方都无攻击性棋子，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    } else if (infoSet != null && infoSet.ZobristInfo != null && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) >= 4) {
                                                        chessInfo.status = 2;
                                                        Toast toast = Toast.makeText(PvPActivity.this, "重复局面出现4次，此乃和棋", Toast.LENGTH_SHORT);
                                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                                        toast.show();
                                                    }
                                                }

                                                if (chessView != null) {
                                                    chessView.requestDraw();
                                                }
                                                if (roundView != null) {
                                                    roundView.requestDraw();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public int[] getPos(MotionEvent e) {
        int[] pos = new int[2];
        double x = e.getX();
        double y = e.getY();
        int[] dis = new int[]{
                chessView.Scale(3), chessView.Scale(41), chessView.Scale(80), chessView.Scale(85)
        };
        x = x - dis[0];
        y = y - dis[1];
        if (x % dis[3] <= dis[2] && y % dis[3] <= dis[2]) {
            pos[0] = (int) Math.floor(x / dis[3]);
            pos[1] = (int) Math.floor(y / dis[3]);
            // 反转y坐标，与ChessView中的显示逻辑一致
            pos[1] = 9 - pos[1];
            if (pos[0] >= 9 || pos[1] >= 10 || pos[1] < 0) {
                pos[0] = pos[1] = -1;
            }
        } else {
            pos[0] = pos[1] = -1;
        }
        return pos;
    }

    @Override
    public void onClick(View view) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return;
        }
        curClickTime = lastClickTime;

        if (selectMusic != null) {
            playEffect(selectMusic);
        }
        int viewId = view.getId();
        if (viewId == R.id.btn_retry) {
            handleRetryButton();
        } else if (viewId == R.id.btn_recall) {
            handleRecallButton();
        } else if (viewId == R.id.btn_save) {
            // 保存棋谱，让用户指定文件名
            handleSaveButton();
        } else if (viewId == R.id.btn_setup) {
            // 切换摆棋模式
            if (chessInfo != null) {
                if (chessInfo.IsSetupMode) {
                    // 关闭摆棋模式，检查摆棋是否完成
                    finishSetup();
                } else {
                    // 开启摆棋模式
                    chessInfo.IsSetupMode = true;
                    // 清空原来的缓存
                    if (infoSet != null) {
                        infoSet.newInfo();
                    }
                    // 重新绘制界面
                    if (chessView != null) {
                        chessView.onSetupModeChanged();
                    }
                    if (roundView != null) {
                        roundView.requestDraw();
                    }
                    Toast.makeText(this, "摆棋模式已开启", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // 显示棋子选择对话框
    private void showPieceSelectionDialog(final int x, final int y) {
        final String[] blackPieces = {"黑将", "黑士", "黑象", "黑马", "黑车", "黑炮", "黑卒", "移除棋子"};
        final String[] redPieces = {"红帅", "红士", "红象", "红马", "红车", "红炮", "红兵", "移除棋子"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择棋子");
        
        // 显示黑棋和红棋选项
        builder.setItems(blackPieces, (dialog, which) -> {
            int pieceID = 0;
            switch (which) {
                case 0: pieceID = 1; break; // 黑将
                case 1: pieceID = 2; break; // 黑士
                case 2: pieceID = 3; break; // 黑象
                case 3: pieceID = 4; break; // 黑马
                case 4: pieceID = 5; break; // 黑车
                case 5: pieceID = 6; break; // 黑炮
                case 6: pieceID = 7; break; // 黑卒
                case 7: pieceID = 0; break; // 移除棋子
            }
            placePiece(x, y, pieceID);
        });
        
        builder.setNegativeButton("红棋", (dialog, which) -> {
            AlertDialog.Builder redBuilder = new AlertDialog.Builder(PvPActivity.this);
            redBuilder.setTitle("选择红棋");
            redBuilder.setItems(redPieces, (dialog1, which1) -> {
                int pieceID = 0;
                switch (which1) {
                    case 0: pieceID = 8; break; // 红帅
                    case 1: pieceID = 9; break; // 红士
                    case 2: pieceID = 10; break; // 红象
                    case 3: pieceID = 11; break; // 红马
                    case 4: pieceID = 12; break; // 红车
                    case 5: pieceID = 13; break; // 红炮
                    case 6: pieceID = 14; break; // 红兵
                    case 7: pieceID = 0; break; // 移除棋子
                }
                placePiece(x, y, pieceID);
            });
            redBuilder.show();
        });
        
        builder.show();
    }
    
    // 放置棋子
    private void placePiece(int x, int y, int pieceID) {
        if (chessInfo != null && x >= 0 && x < 9 && y >= 0 && y < 10) {
            // 检查棋子数量限制
            if (!checkPieceCount(pieceID)) {
                Toast.makeText(this, "棋子数量已达到上限", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 检查位置合理性
            if (!isValidPiecePosition(pieceID, x, y)) {
                Toast.makeText(this, "该位置不适合放置此棋子", Toast.LENGTH_SHORT).show();
                return;
            }
            
            chessInfo.piece[y][x] = pieceID;
            // 重新计算攻击棋子数量
            chessInfo.attackNum_B = 0;
            chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int piece = chessInfo.piece[i][j];
                    if (piece != 0) {
                        // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                        if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                            chessInfo.attackNum_B++;
                        }
                        // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                        else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                            chessInfo.attackNum_R++;
                        }
                    }
                }
            }
            // 重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
            
            // 不再自动检查摆棋完成，由用户点击摆棋按钮结束
        }
    }
    
    // 检查棋子位置是否合理
    private boolean isValidPiecePosition(int pieceID, int x, int y) {
        // 检查坐标是否在棋盘范围内
        if (x < 0 || x >= 9 || y < 0 || y >= 10) {
            return false;
        }
        
        // 摆棋模式下的位置限制
        if (chessInfo != null && chessInfo.IsSetupMode) {
            switch (pieceID) {
                case 1: // 黑将
                case 8: // 红帅
                    // 将帅只能在九宫格内
                    if (pieceID == 1) { // 黑将
                        // 黑将九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 7 && y <= 9;
                    } else { // 红帅
                        // 红帅九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 0 && y <= 2;
                    }
                case 2: // 黑士
                case 9: // 红士
                    // 士只能在九宫格内且走斜线位置
                    if (pieceID == 2) { // 黑士
                        // 黑士九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 7 && y <= 9) && 
                               ((x == 3 && (y == 7 || y == 9)) || (x == 4 && y == 8) || (x == 5 && (y == 7 || y == 9)));
                    } else { // 红士
                        // 红士九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 0 && y <= 2) && 
                               ((x == 3 && (y == 0 || y == 2)) || (x == 4 && y == 1) || (x == 5 && (y == 0 || y == 2)));
                    }
                case 3: // 黑象
                case 10: // 红相
                    // 相只能在己方半场
                    if (pieceID == 3) { // 黑象
                        // 黑象位置：在己方半场（因为坐标已经反转）
                        return y >= 5 && y <= 9;
                    } else { // 红相
                        // 红相位置：在己方半场（因为坐标已经反转）
                        return y >= 0 && y <= 4;
                    }
                case 7: // 黑卒
                    // 摆棋模式下黑卒可以自由摆放
                    return true;
                case 14: // 红兵
                    // 摆棋模式下红兵可以自由摆放
                    return true;
                case 4: // 黑马
                case 11: // 红马
                    // 马可以自由摆放
                    return true;
                case 5: // 黑车
                case 12: // 红车
                    // 车可以自由摆放
                    return true;
                case 6: // 黑炮
                case 13: // 红炮
                    // 炮可以自由摆放
                    return true;
                default:
                    // 其他棋子默认可以自由摆放
                    return true;
            }
        }
        
        // 正常游戏模式下的位置限制
        switch (pieceID) {
            case 1: // 黑将
                // 黑将只能在九宫格内
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 8: // 红帅
                // 红帅只能在九宫格内
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 2: // 黑士
                // 黑士只能在九宫格内
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 9: // 红士
                // 红士只能在九宫格内
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 3: // 黑象
                // 黑象只能在己方半场
                return y >= 5 && y <= 9;
            case 10: // 红相
                // 红相只能在己方半场
                return y >= 0 && y <= 4;
            default:
                // 其他棋子可以在任何位置
                return true;
        }
    }
    
    // 检查棋子数量是否符合标准
    private boolean checkPieceCount(int pieceID) {
        if (pieceID == 0) return true; // 移除棋子总是允许的
        
        int count = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == pieceID) {
                    count++;
                }
            }
        }
        
        // 标准中国象棋棋子数量限制
        switch (pieceID) {
            case 1: // 黑将
            case 8: // 红帅
                return count < 1;
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
                return count < 2;
            case 7: // 黑卒
            case 14: // 红兵
                return count < 5;
            default:
                return true;
        }
    }
    
    // 显示摆棋模式帮助信息
    private void showSetupHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("摆棋模式帮助");
        builder.setMessage("1. 点击左侧棋子选择区域选择棋子\n" +
                          "2. 点击棋盘放置选中的棋子\n" +
                          "3. 点击棋盘上已放置的棋子可移动或移除\n" +
                          "4. 点击清空棋盘按钮可清空除将/帅外的所有棋子\n" +
                          "\n棋子放置规则：\n" +
                          "- 将/帅：只能放在九宫格内\n" +
                          "- 士：只能放在九宫格内的斜线位置\n" +
                          "- 象/相：只能放在己方半场的田字中心\n" +
                          "- 卒/兵：只能放在己方兵线位置\n" +
                          "- 马、车、炮：可以自由放置\n" +
                          "\n摆棋完成后，会自动提示选择开局方。");
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    // 检查摆棋是否完成
    private boolean checkSetupComplete() {
        if (chessInfo == null || chessInfo.piece == null) return false;
        
        // 只检查基本合法性：双方都有将/帅
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece == 1) { // 黑将
                    hasBlackKing = true;
                } else if (piece == 8) { // 红帅
                    hasRedKing = true;
                }
            }
        }
        
        return hasRedKing && hasBlackKing;
    }
    
    // 结束摆棋并选择开局方
    private void finishSetup() {
        if (checkSetupComplete()) {
            // 显示选择开局方的对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择开局方");
            builder.setMessage("请选择由哪一方开始下棋");
            builder.setPositiveButton("红方开始", (dialog, which) -> {
                // 红方开始，设置IsRedGo为true
                chessInfo.IsRedGo = true;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this, "摆棋完成！红方开始", Toast.LENGTH_SHORT).show();
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
            });
            builder.setNegativeButton("黑方开始", (dialog, which) -> {
                // 黑方开始，设置IsRedGo为false
                chessInfo.IsRedGo = false;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this, "摆棋完成！黑方开始", Toast.LENGTH_SHORT).show();
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
            });
            builder.setCancelable(false); // 必须选择一个选项
            builder.show();
        } else {
            Toast.makeText(this, "棋子放置不完整，请继续放置棋子", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRetryButton() {
        // 简化实现，直接重置游戏
        try {
            chessInfo.setInfo(new ChessInfo());
            infoSet.newInfo();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        Toast.makeText(this, "游戏已重置", Toast.LENGTH_SHORT).show();
    }

    private void handleRecallButton() {
        int cnt = 0;
        int total = 1; // 一次悔一步
        if (infoSet != null && infoSet.preInfo != null && chessInfo != null && infoSet.curInfo != null) {
            // 确保至少有一个状态可以恢复
            if (!infoSet.preInfo.isEmpty()) {
                while (!infoSet.preInfo.empty() && cnt < total) {
                    ChessInfo tmp = infoSet.preInfo.pop();
                    cnt++;
                    try {
                        if (infoSet.ZobristInfo != null) {
                            infoSet.recallZobristInfo(chessInfo.ZobristKeyCheck);
                        }
                        chessInfo.setInfo(tmp);
                        infoSet.curInfo.setInfo(tmp);
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                }
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
                if (roundView != null) {
                    roundView.requestDraw();
                }
                Toast.makeText(this, "已悔棋", Toast.LENGTH_SHORT).show();
            } else {
                // 当栈为空时，重置为初始状态
                try {
                    ChessInfo initialInfo = new ChessInfo();
                    chessInfo.setInfo(initialInfo);
                    infoSet.curInfo.setInfo(initialInfo);
                    // 重新绘制界面
                    if (chessView != null) {
                        chessView.requestDraw();
                    }
                    if (roundView != null) {
                        roundView.requestDraw();
                    }
                    Toast.makeText(this, "已重置到初始局面", Toast.LENGTH_SHORT).show();
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Toast.makeText(this, "无法悔棋", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return true;
        }
        curClickTime = lastClickTime;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 直接退出
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        stopMusic(backMusic);
        super.onPause();
    }


    
    // 生成FEN字符串
    private String generateFEN(ChessInfo chessInfo) {
        StringBuilder fen = new StringBuilder();
        
        // 生成棋盘部分，从黑方底线开始（y=9）到红方底线结束（y=0），符合标准FEN格式
        for (int y = 9; y >= 0; y--) {
            int emptyCount = 0;
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFEN(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (y > 0) {
                fen.append('/');
            }
        }
        
        // 生成回合部分，符合标准FEN格式
        // 'w' 表示白方（红方）走，'b' 表示黑方走
        fen.append(' ');
        fen.append(chessInfo.IsRedGo ? 'w' : 'b');
        
        // 生成 castle 部分（中国象棋不需要）
        fen.append(" - - 0 1");
        
        return fen.toString();
    }
    
    // 将棋子ID转换为FEN符号
    private char pieceToFEN(int piece) {
        switch (piece) {
            case 1: return 'k'; // 黑将
            case 2: return 'a'; // 黑士
            case 3: return 'b'; // 黑象
            case 4: return 'n'; // 黑马
            case 5: return 'r'; // 黑车
            case 6: return 'c'; // 黑炮
            case 7: return 'p'; // 黑卒
            case 8: return 'K'; // 红帅
            case 9: return 'A'; // 红士
            case 10: return 'B'; // 红相
            case 11: return 'N'; // 红马
            case 12: return 'R'; // 红车
            case 13: return 'C'; // 红炮
            case 14: return 'P'; // 红兵
            default: return ' ';
        }
    }
    
    // 生成标准象棋记谱走法
    private String generateMoveString(int pieceType, Info.Pos fromPos, Info.Pos toPos, boolean isRed) {
        // 确保位置有效
        if (fromPos == null || toPos == null || 
            fromPos.x < 0 || fromPos.x > 8 || fromPos.y < 0 || fromPos.y > 9 ||
            toPos.x < 0 || toPos.x > 8 || toPos.y < 0 || toPos.y > 9) {
            return null;
        }
        
        // 检查是否有多个相同的棋子
        String prefix = "";
        int baseType = pieceType % 7;
        boolean isPawn = baseType == 0; // 兵/卒
        boolean isSameColumn = false;
        boolean isSameRow = false;
        java.util.List<Info.Pos> samePieces = new java.util.ArrayList<>();
        
        // 收集同一列的相同棋子
        if (chessInfo != null && chessInfo.piece != null) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (x == fromPos.x && chessInfo.piece[y][x] == pieceType) {
                        samePieces.add(new Info.Pos(x, y));
                    }
                }
            }
        }
        
        // 如果同一列有多个相同的棋子，添加前缀
        if (samePieces.size() > 1) {
            isSameColumn = true;
            // 对棋子按y坐标排序（兼容API 16）
            // 使用简单的冒泡排序，避免使用匿名内部类
            for (int i = 0; i < samePieces.size() - 1; i++) {
                for (int j = 0; j < samePieces.size() - i - 1; j++) {
                    Info.Pos p1 = samePieces.get(j);
                    Info.Pos p2 = samePieces.get(j + 1);
                    if (p1 != null && p2 != null && p1.y > p2.y) {
                        // 交换位置
                        samePieces.set(j, p2);
                        samePieces.set(j + 1, p1);
                    }
                }
            }
            
            if (isPawn) {
                // 兵/卒使用数字前缀：一兵、二兵、三兵、四兵、五兵
                // 按照从前往后的顺序编号
                int index = samePieces.indexOf(new Info.Pos(fromPos.x, fromPos.y)) + 1;
                prefix = getColChar(index);
            } else {
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    Info.Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.y == frontPiece.y) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    Info.Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Info.Pos middlePiece = samePieces.get(1);
                    if (fromPos.y == frontPiece.y) {
                        prefix = "前";
                    } else if (fromPos.y == middlePiece.y) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                } else if (samePieces.size() > 3) {
                    // 四个或五个棋子：前、二、三、四、五
                    int index = samePieces.indexOf(new Info.Pos(fromPos.x, fromPos.y)) + 1;
                    if (index == 1) {
                        prefix = "前";
                    } else {
                        prefix = getColChar(index);
                    }
                }
            }
        } else {
            // 检查同一行是否有多个相同的棋子
            samePieces.clear();
            if (chessInfo != null && chessInfo.piece != null) {
                for (int y = 0; y < 10; y++) {
                    for (int x = 0; x < 9; x++) {
                        if (y == fromPos.y && chessInfo.piece[y][x] == pieceType) {
                            samePieces.add(new Info.Pos(x, y));
                        }
                    }
                }
            }
            
            if (samePieces.size() > 1) {
                isSameRow = true;
                // 对棋子按x坐标排序（兼容API 16）
                // 使用简单的冒泡排序，避免使用匿名内部类
                for (int i = 0; i < samePieces.size() - 1; i++) {
                    for (int j = 0; j < samePieces.size() - i - 1; j++) {
                        Info.Pos p1 = samePieces.get(j);
                        Info.Pos p2 = samePieces.get(j + 1);
                        if (p1 != null && p2 != null && p1.x > p2.x) {
                            // 交换位置
                            samePieces.set(j, p2);
                            samePieces.set(j + 1, p1);
                        }
                    }
                }
                
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    // 对于红方，右边的棋子是"前"；对于黑方，左边的棋子是"前"
                    Info.Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.x == frontPiece.x) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    // 对于红方，从右到左为前、中、后；对于黑方，从左到右为前、中、后
                    Info.Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Info.Pos middlePiece = samePieces.get(1);
                    if (fromPos.x == frontPiece.x) {
                        prefix = "前";
                    } else if (fromPos.x == middlePiece.x) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                }
            }
        }
        
        // 计算起始列号
        int startCol;
        if (isRed) {
            // 红方：从右到左计数，右为一
            startCol = 9 - fromPos.x;
        } else {
            // 黑方：从左到右计数，左为1（对应红方的九）
            startCol = fromPos.x + 1;
        }
        startCol = Math.max(1, Math.min(9, startCol));
        // 红黑方都使用中文数字，以匹配棋谱格式
        String startColStr = getColChar(startCol);
        
        // 计算移动类型
        String moveType;
        int colDiff = toPos.x - fromPos.x;
        int rowDiff = toPos.y - fromPos.y;
        
        // 确定移动方向（红黑相对）
        if (colDiff == 0) {
            // 纵向移动
            if (isRed) {
                // 红方：向黑方（y值增大）为进
                moveType = rowDiff > 0 ? "进" : "退";
            } else {
                // 黑方：向红方（y值减小）为进
                moveType = rowDiff < 0 ? "进" : "退";
            }
        } else {
            // 横向或斜向移动
            // 车、炮、兵/卒、帅（将）使用"平"
            if (baseType == 5 || baseType == 6 || baseType == 0 || baseType == 1) {
                moveType = "平";
            } else {
                // 士、象、马使用"进"或"退"
                if (isRed) {
                    // 红方：向黑方（y值增大）为进
                    moveType = rowDiff > 0 ? "进" : "退";
                } else {
                    // 黑方：向红方（y值减小）为进
                    moveType = rowDiff < 0 ? "进" : "退";
                }
            }
        }
        
        // 计算目标位置
        String targetPos;
        if (moveType.equals("平")) {
            // 横向移动使用列号
            int targetCol;
            if (isRed) {
                // 红方：从右到左计数，右为一
                targetCol = 9 - toPos.x;
            } else {
                // 黑方：从左到右计数，左为1
                targetCol = toPos.x + 1;
            }
            targetCol = Math.max(1, Math.min(9, targetCol));
            // 红黑方都使用中文数字，以匹配棋谱格式
            targetPos = getColChar(targetCol);
        } else {
            // 纵向或斜向移动
            boolean isSpecialPiece = baseType == 2 || baseType == 3 || baseType == 4; // 士、象、马
            
            if (isSpecialPiece) {
                // 马、相（象）、仕（士）：使用目标列坐标
                int targetCol;
                if (isRed) {
                    // 红方：从右到左计数，右为一
                    targetCol = 9 - toPos.x;
                } else {
                    // 黑方：从左到右计数，左为1
                    targetCol = toPos.x + 1;
                }
                targetCol = Math.max(1, Math.min(9, targetCol));
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = getColChar(targetCol);
            } else {
                // 车、炮、兵（卒）、帅（将）：使用移动的行数（格数）
                int moveSteps = Math.abs(toPos.y - fromPos.y);
                // 确保移动的格数至少为1
                moveSteps = Math.max(1, moveSteps);
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = getColChar(moveSteps);
            }
        }
        
        // 获取棋子名称
        String pieceName = getPieceName(pieceType);
        
        // 生成走法字符串
        String moveString;
        if ((isSameColumn || isSameRow) && !prefix.isEmpty()) {
            if (isPawn) {
                // 兵/卒：一兵、二兵等
                moveString = prefix + pieceName + moveType + targetPos;
            } else {
                // 其他棋子：前马、后车等
                moveString = prefix + pieceName + startColStr + moveType + targetPos;
            }
        } else {
            // 普通走法
            moveString = pieceName + startColStr + moveType + targetPos;
        }
        
        // 生成黑方走法的阿拉伯数字版本，以符合中国象棋记谱标准
        if (!isRed) {
            moveString = moveString.replace("一", "1")
                                  .replace("二", "2")
                                  .replace("三", "3")
                                  .replace("四", "4")
                                  .replace("五", "5")
                                  .replace("六", "6")
                                  .replace("七", "7")
                                  .replace("八", "8")
                                  .replace("九", "9");
        }
        
        return moveString;
    }
    
    private String getPieceName(int pieceType) {
        switch (pieceType) {
            case 1: return "将";
            case 2: return "士";
            case 3: return "象";
            case 4: return "马";
            case 5: return "车";
            case 6: return "炮";
            case 7: return "卒";
            case 8: return "帅";
            case 9: return "仕";
            case 10: return "相";
            case 11: return "马";
            case 12: return "车";
            case 13: return "炮";
            case 14: return "兵";
            default: return "未知";
        }
    }
    
    private String getColChar(int col) {
        switch (col) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "七";
            case 8: return "八";
            case 9: return "九";
            default: return "";
        }
    }

    

    

    
    // 保存棋谱（默认名称）
    private void handleSaveButton() {
        // 创建一个布局用于输入对局信息
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_save_notation, null);
        
        final EditText redPlayerEditText = dialogView.findViewById(R.id.red_player_edit);
        final EditText blackPlayerEditText = dialogView.findViewById(R.id.black_player_edit);
        final EditText dateEditText = dialogView.findViewById(R.id.date_edit);
        final EditText locationEditText = dialogView.findViewById(R.id.location_edit);
        final EditText eventEditText = dialogView.findViewById(R.id.event_edit);
        final EditText roundEditText = dialogView.findViewById(R.id.round_edit);
        final RadioButton resultRedWin = dialogView.findViewById(R.id.result_red_win);
        final RadioButton resultBlackWin = dialogView.findViewById(R.id.result_black_win);
        final RadioButton resultDraw = dialogView.findViewById(R.id.result_draw);
        
        // 设置默认值
        dateEditText.setText(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        // 根据当前游戏状态设置默认结果
        if (chessInfo != null && chessInfo.status == 2) {
            if (Rule.isDead(chessInfo.piece, true)) {
                resultBlackWin.setChecked(true);
            } else if (Rule.isDead(chessInfo.piece, false)) {
                resultRedWin.setChecked(true);
            } else {
                resultDraw.setChecked(true);
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("保存棋谱");
        builder.setView(dialogView);
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 生成默认文件名
            String fileName = "双人对局_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pgn";
            
            String redPlayer = redPlayerEditText.getText().toString().trim();
            String blackPlayer = blackPlayerEditText.getText().toString().trim();
            String date = dateEditText.getText().toString().trim();
            String location = locationEditText.getText().toString().trim();
            String event = eventEditText.getText().toString().trim();
            String round = roundEditText.getText().toString().trim();
            String result = "";
            if (resultRedWin.isChecked()) {
                result = "红胜";
            } else if (resultBlackWin.isChecked()) {
                result = "黑胜";
            } else if (resultDraw.isChecked()) {
                result = "和棋";
            }
            
            // 保存信息到成员变量
            pendingSaveFileName = fileName;
            pendingSaveRedPlayer = redPlayer;
            pendingSaveBlackPlayer = blackPlayer;
            pendingSaveDate = date;
            pendingSaveLocation = location;
            pendingSaveEvent = event;
            pendingSaveRound = round;
            pendingSaveResult = result;
            
            // 使用SAF打开文件保存选择器
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, 1003);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    // 保存棋谱到URI
    private void saveChessNotationToUri(Uri uri) {
        try {
            // 使用保存对话框中输入的信息
            String fileName = pendingSaveFileName != null ? pendingSaveFileName : "双人对局.pgn";
            String redPlayer = pendingSaveRedPlayer != null ? pendingSaveRedPlayer : "";
            String blackPlayer = pendingSaveBlackPlayer != null ? pendingSaveBlackPlayer : "";
            String date = pendingSaveDate != null ? pendingSaveDate : "";
            String location = pendingSaveLocation != null ? pendingSaveLocation : "";
            String event = pendingSaveEvent != null ? pendingSaveEvent : "";
            String round = pendingSaveRound != null ? pendingSaveRound : "";
            String result = pendingSaveResult != null ? pendingSaveResult : "";
            
            // 创建棋谱对象
            ChessNotation notation = new ChessNotation();
            notation.setFileName(fileName);
            notation.setDate(new Date());
            notation.setPlayerRed(redPlayer);
            notation.setPlayerBlack(blackPlayer);
            notation.setMatchDate(date);
            notation.setLocation(location);
            notation.setEvent(event);
            notation.setRound(round);
            
            // 添加FEN信息
            if (chessInfo != null) {
                String fen;
                // 在非摆棋模式下，使用infoSet中的初始状态生成FEN，确保保存的是摆棋完成时的局面
                if (!chessInfo.IsSetupMode && infoSet != null && !infoSet.preInfo.empty()) {
                    // 获取infoSet中的第一个元素（初始状态）
                    java.util.Stack<ChessInfo> tempStack = new java.util.Stack<>();
                    ChessInfo initialInfo = null;
                    
                    // 弹出所有元素，找到第一个元素
                    while (!infoSet.preInfo.empty()) {
                        ChessInfo info = infoSet.preInfo.pop();
                        tempStack.push(info);
                        if (initialInfo == null) {
                            initialInfo = info;
                        }
                    }
                    
                    // 恢复原栈
                    while (!tempStack.empty()) {
                        infoSet.preInfo.push(tempStack.pop());
                    }
                    
                    // 使用初始状态生成FEN
                    if (initialInfo != null) {
                        fen = generateFEN(initialInfo);
                    } else {
                        // 如果没有初始状态，使用当前状态
                        fen = generateFEN(chessInfo);
                    }
                } else {
                    // 在摆棋模式下，使用当前棋盘状态生成FEN
                    fen = generateFEN(chessInfo);
                }
                notation.setFen(fen);
            }
            
            // 添加结果信息
            if (!result.isEmpty()) {
                notation.setResult(result);
            } else if (chessInfo != null && chessInfo.status == 2) {
                if (Rule.isDead(chessInfo.piece, true)) {
                    notation.setResult("黑胜");
                } else if (Rule.isDead(chessInfo.piece, false)) {
                    notation.setResult("红胜");
                } else {
                    notation.setResult("和棋");
                }
            }
            
            // 只有在非摆棋模式下才提取走法记录
            if (chessInfo != null && !chessInfo.IsSetupMode && infoSet != null && infoSet.preInfo != null) {
                // 创建一个临时列表来存储所有ChessInfo对象，而不修改原栈
                java.util.List<ChessInfo> tempList = new java.util.ArrayList<>();
                java.util.Stack<ChessInfo> originalStack = new java.util.Stack<>();
                
                // 先将所有ChessInfo对象弹出到临时列表，同时保存到原始栈
                while (!infoSet.preInfo.empty()) {
                    ChessInfo info = infoSet.preInfo.pop();
                    tempList.add(info);
                    originalStack.push(info);
                }
                
                // 恢复原栈
                while (!originalStack.empty()) {
                    infoSet.preInfo.push(originalStack.pop());
                }
                
                // 按照临时列表的顺序处理，保证走法记录顺序正确
                for (int i = tempList.size() - 1; i >= 0; i--) {
                    ChessInfo info = tempList.get(i);
                    
                    // 生成走法记录
                    if (info.prePos != null && info.curPos != null) {
                        // 尝试获取移动的棋子类型
                        int piece = 0;
                        boolean isRed = false;
                        
                        // 首先尝试从当前位置获取棋子
                        if (info.piece != null && info.curPos.y >= 0 && info.curPos.y < info.piece.length && 
                            info.curPos.x >= 0 && info.curPos.x < info.piece[info.curPos.y].length) {
                            piece = info.piece[info.curPos.y][info.curPos.x];
                            isRed = piece >= 8 && piece <= 14;
                        }
                        
                        if (piece != 0) {
                            String move = generateMoveString(piece, info.prePos, info.curPos, isRed);
                            
                            if (move != null) {
                                if (isRed) {
                                    // 红方走法，添加新记录
                                    notation.addMoveRecord(move, "");
                                } else {
                                    // 黑方走法，更新最后一条记录
                                    if (!notation.getMoveRecords().isEmpty()) {
                                        ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                                        if (lastRecord.blackMove.isEmpty()) {
                                            lastRecord.blackMove = move;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 只有在非摆棋模式下才处理当前chessInfo中的走法记录（最后一步）
            if (chessInfo != null && !chessInfo.IsSetupMode && chessInfo.prePos != null && chessInfo.curPos != null) {
                // 尝试获取移动的棋子类型
                int piece = 0;
                boolean isRed = false;
                
                // 首先尝试从当前位置获取棋子
                if (chessInfo.piece != null && chessInfo.curPos.y >= 0 && chessInfo.curPos.y < chessInfo.piece.length && 
                    chessInfo.curPos.x >= 0 && chessInfo.curPos.x < chessInfo.piece[chessInfo.curPos.y].length) {
                    piece = chessInfo.piece[chessInfo.curPos.y][chessInfo.curPos.x];
                    isRed = piece >= 8 && piece <= 14;
                }
                
                if (piece != 0) {
                    String move = generateMoveString(piece, chessInfo.prePos, chessInfo.curPos, isRed);
                    
                    if (move != null) {
                        if (isRed) {
                            // 红方走法，添加新记录
                            notation.addMoveRecord(move, "");
                        } else {
                            // 黑方走法，更新最后一条记录
                            if (!notation.getMoveRecords().isEmpty()) {
                                ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                                if (lastRecord.blackMove.isEmpty()) {
                                    lastRecord.blackMove = move;
                                }
                            }
                        }
                    }
                }
            }
            
            // 生成棋谱内容
            String content = notation.toSaveContent();
            
            // 写入到选择的URI，确保完全覆盖文件内容
            // 先获取文件描述符，然后使用 FileOutputStream 来确保覆盖模式
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    // 写入新内容
                    writer.write(content);
                    writer.flush();
                    Toast.makeText(this, "棋谱保存成功: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "保存棋谱失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    try {
                        pfd.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Toast.makeText(this, "无法创建文件描述符", Toast.LENGTH_SHORT).show();
            }
            
            // 清空临时变量
            pendingSaveFileName = null;
            pendingSaveRedPlayer = null;
            pendingSaveBlackPlayer = null;
            pendingSaveDate = null;
            pendingSaveLocation = null;
            pendingSaveEvent = null;
            pendingSaveRound = null;
            pendingSaveResult = null;
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存棋谱失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1003 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                saveChessNotationToUri(uri);
            }
        }
    }

    @Override
    protected void onStop() {
        try {
            SaveInfo.SerializeChessInfo(chessInfo, "ChessInfo_pvp.bin");
            SaveInfo.SerializeInfoSet(infoSet, "InfoSet_pvp.bin");
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        playMusic(backMusic);
        super.onStart();
    }

    // 初始化音乐
    private void initMusic() {
        backMusic = MediaPlayer.create(this, R.raw.background);
        if (backMusic != null) {
            backMusic.setLooping(true);
            backMusic.setVolume(0.2f, 0.2f);
        }
        selectMusic = MediaPlayer.create(this, R.raw.select);
        if (selectMusic != null) {
            selectMusic.setVolume(5f, 5f);
        }
        clickMusic = MediaPlayer.create(this, R.raw.click);
        if (clickMusic != null) {
            clickMusic.setVolume(5f, 5f);
        }
        checkMusic = MediaPlayer.create(this, R.raw.checkmate);
        if (checkMusic != null) {
            checkMusic.setVolume(5f, 5f);
        }
        winMusic = MediaPlayer.create(this, R.raw.win);
        if (winMusic != null) {
            winMusic.setVolume(5f, 5f);
        }
    }
    
    // 播放音乐
    private static void playMusic(MediaPlayer mediaPlayer) {
        if (setting != null && mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            }
            mediaPlayer.start();
        }
    }
    
    // 停止音乐
    private static void stopMusic(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
        }
    }
    
    // 播放音效
    private static void playEffect(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            if (setting != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    mediaPlayer.seekTo(0);
                }
                mediaPlayer.start();
            }
        }
    }


}
