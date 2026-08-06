package top.nones.chessgame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.util.Log;
import android.app.AlertDialog;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import Info.ChessInfo;
import Info.InfoSet;
import Info.SaveInfo;
import Info.Setting;
import CustomView.ChessView;
import CustomView.ModeIconDrawable;
import CustomView.RoundView;
import CustomView.SetupModeView;
import Utils.LogUtils;
import Utils.GameResourceManager;
import AICore.PikafishAI;

public class PvMActivityInit {
    private PvMActivity activity;
    
    public PvMActivityInit(PvMActivity activity) {
        this.activity = activity;
    }
    
    public void init() {
        if (activity == null) {
            Log.e("PvMActivityInit", "Activity is null, initialization failed");
            return;
        }
        
        Log.d("PvMActivity", "开始初始化");
        
        // 初始化LogUtils
        try {
            LogUtils.init(activity);
            Log.d("PvMActivity", "LogUtils初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing LogUtils: " + e.getMessage());
        }

        // 初始化SaveInfo
        try {
            SaveInfo.init(activity);
            Log.d("PvMActivity", "SaveInfo初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing SaveInfo: " + e.getMessage());
        }

        // 初始化SharedPreferences
        try {
            activity.sharedPreferences = activity.getSharedPreferences("setting", activity.MODE_PRIVATE);
            Log.d("PvMActivity", "SharedPreferences初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing SharedPreferences: " + e.getMessage());
        }

        // 初始化设置
        try {
            if (activity.sharedPreferences != null) {
                GameResourceManager manager = GameResourceManager.getInstance();
                manager.initSettings(activity);
                activity.setting = manager.getSetting();
                LogUtils.d("PvMActivity", "Setting初始化完成");
            } else {
                LogUtils.e("PvMActivityInit", "SharedPreferences is null, cannot initialize Setting");
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivityInit", "Error initializing Setting: " + e.getMessage());
        }

        // 初始化默认值
        try {
            activity.chessInfo = new ChessInfo();
            // 总是设置setting，确保使用最新的设置
            activity.chessInfo.setting = activity.setting;
            activity.infoSet = new InfoSet();
            // 将初始状态保存到preInfo栈中，确保第一次悔棋时有可恢复的状态
            try {
                activity.infoSet.pushInfo(activity.chessInfo);
                Log.d("PvMActivity", "初始状态保存到preInfo栈中");
            } catch (CloneNotSupportedException e) {
                Log.e("PvMActivityInit", "Error pushing initial info: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing default values: " + e.getMessage());
        }

        // 在主线程初始化音效（与PvP模式一致，避免后台线程MediaPlayer.create返回null）
        initMusic();

        // 检查是否有从棋谱管理界面传递过来的棋谱数据
        checkIntentData();
    }
    
    // 在主线程初始化音效
    private void initMusic() {
        if (activity == null) {
            Log.e("PvMActivityInit", "Activity is null, cannot init music");
            return;
        }
        try {
            GameResourceManager manager = GameResourceManager.getInstance();
            manager.initSoundEffects(activity);
            
            activity.selectMusic = manager.getSelectMusic();
            activity.clickMusic = manager.getClickMusic();
            activity.captureMusic = manager.getCaptureMusic();
            activity.checkMusic = manager.getCheckMusic();
            activity.winMusic = manager.getWinMusic();
            
            LogUtils.d("PvMActivity", "音效初始化完成 selectMusic=" + activity.selectMusic + " clickMusic=" + activity.clickMusic + " checkMusic=" + activity.checkMusic);
        } catch (Exception e) {
            Log.e("PvMActivityInit", "音效初始化失败: " + e.getMessage());
        }
    }
    
    private void checkIntentData() {
        if (activity == null) {
            Log.e("PvMActivityInit", "Activity is null, cannot check intent data");
            return;
        }
        
        try {
            Intent intent = activity.getIntent();
            if (intent != null && intent.hasExtra("notation")) {
                Info.ChessNotation notation = (Info.ChessNotation) intent.getSerializableExtra("notation");
                if (notation != null) {
                    // 初始化棋盘状态为初始状态
                    activity.chessInfo = new ChessInfo();
                    // 总是设置setting，确保使用最新的设置
                    activity.chessInfo.setting = activity.setting;
                    activity.infoSet = new InfoSet();
                    // 延迟处理棋谱加载，确保notationManager已经初始化
                    if (activity.notationManager != null) {
                        activity.notationManager.setCurrentNotation(notation);
                        activity.notationManager.setCurrentMoveIndex(0);
                        activity.notationManager.generateBoardStateFromNotation();
                        Toast.makeText(activity, "棋谱已加载，点击下一步开始回放", Toast.LENGTH_SHORT).show();
                        Log.d("PvMActivity", "棋谱已加载");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error checking intent data: " + e.getMessage());
        }
    }
    
    public void initViews() {
        if (activity == null) {
            Log.e("PvMActivityInit", "Activity is null, cannot initialize views");
            return;
        }
        
        if (activity.relativeLayout == null) {
            Log.e("PvMActivityInit", "RelativeLayout is null, cannot initialize views");
            return;
        }
        
        Log.d("PvMActivity", "开始初始化视图");
        
        // 先添加回合显示视图
        try {
            if (activity.chessInfo != null) {
                activity.roundView = new RoundView(activity, activity.chessInfo, activity.gameMode);
                if (activity.roundView != null) {
                    activity.relativeLayout.addView(activity.roundView);

                    android.widget.RelativeLayout.LayoutParams paramsRound = (android.widget.RelativeLayout.LayoutParams) activity.roundView.getLayoutParams();
                    if (paramsRound != null) {
                        paramsRound.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsRound.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                        paramsRound.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsRound.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsRound.setMargins(0, 0, 0, 0); // 贴屏幕顶部，不留空隙
                        activity.roundView.setLayoutParams(paramsRound);
                        activity.roundView.setId(R.id.roundView);

                        // 点击回合信息条中的"最优一步"：触发模拟行棋（等同于点击第 1 条候选变线）
                        activity.roundView.setOnClickListener(v -> {
                            if (activity != null) {
                                activity.onRoundBestMoveClick();
                            }
                        });
                    }
                }
            }

            // 顶部状态栏：左上角「菜单」图标按钮（下拉菜单：新局/保存/加载/设置/模式切换），
            // 右上角「切换模式」图标按钮（仅图标）。两者尽量贴近屏幕左右边缘（round 信息条区域）。
            try {
                float density = activity.getResources().getDisplayMetrics().density;
                int btnSize = (int) (42 * density); // 按钮尺寸略放大，使图标更大
                int edge = (int) (4 * density); // 贴近屏幕边缘的外边距
                // 时间行基线位于 round 内 paddingTop(5dp)+39dp=44dp 处；按钮恢复至距屏幕顶 44dp
                int top = (int) (44 * density);

                // 左上角：菜单按钮（背景透明、方形，置于 round 信息条顶部左缘）
                android.widget.ImageButton btnMenu = new android.widget.ImageButton(activity);
                btnMenu.setId(R.id.btn_menu);
                btnMenu.setImageResource(R.drawable.ic_menu);
                btnMenu.setBackground(null); // 完全透明
                btnMenu.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                btnMenu.setContentDescription("菜单");
                android.widget.RelativeLayout.LayoutParams mp = new android.widget.RelativeLayout.LayoutParams(btnSize, btnSize);
                mp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                mp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
                mp.setMargins(edge, top, 0, 0);
                btnMenu.setLayoutParams(mp);
                activity.relativeLayout.addView(btnMenu);

                // 右上角：切换模式按钮（仅图标，背景透明、方形，置于 round 信息条顶部右缘）
                android.widget.ImageButton btnModeSwitch = new android.widget.ImageButton(activity);
                btnModeSwitch.setId(R.id.btn_mode_switch);
                btnModeSwitch.setBackground(null); // 完全透明
                btnModeSwitch.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                btnModeSwitch.setContentDescription("切换模式");
                btnModeSwitch.setPadding(0, 0, 0, 0);
                btnModeSwitch.setImageDrawable(new ModeIconDrawable(activity, activity.gameMode, density,
                        ModeIconDrawable.SIDE_BOTH, 0xFFFFFFFF));
                android.widget.RelativeLayout.LayoutParams msp = new android.widget.RelativeLayout.LayoutParams(btnSize, btnSize);
                msp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                msp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
                msp.setMargins(0, top, edge, 0);
                btnModeSwitch.setLayoutParams(msp);
                activity.relativeLayout.addView(btnModeSwitch);
                // 点击复用隐藏的 R.id.btn_mode（已接入模式切换逻辑）
                btnModeSwitch.setOnClickListener(v -> {
                    android.view.View hidden = activity.findViewById(R.id.btn_mode);
                    if (hidden != null) hidden.performClick();
                });
            } catch (Exception e) {
                LogUtils.e("PvMActivityInit", "添加菜单/模式按钮失败: " + e.getMessage());
            }
            Log.d("PvMActivity", "回合显示视图初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing round view: " + e.getMessage());
        }

        // 然后添加摆棋模式视图，放在回合视图和棋盘视图之间
        try {
            if (activity.chessInfo != null) {
                activity.setupModeView = new SetupModeView(activity, activity.chessInfo);
                if (activity.setupModeView != null) {
                    activity.relativeLayout.addView(activity.setupModeView);

                    android.widget.RelativeLayout.LayoutParams paramsSetup = (android.widget.RelativeLayout.LayoutParams) activity.setupModeView.getLayoutParams();
                    if (paramsSetup != null) {
                        paramsSetup.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsSetup.addRule(android.widget.RelativeLayout.BELOW, R.id.roundView);
                        paramsSetup.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsSetup.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsSetup.setMargins(0, 0, 0, 0); // 摆棋视图同样不留空隙
                        activity.setupModeView.setLayoutParams(paramsSetup);
                        activity.setupModeView.setId(R.id.setupModeView);
                        activity.setupModeView.setVisibility(android.view.View.GONE); // 默认隐藏

                        // 设置摆棋模式监听器
                        activity.setupModeView.setOnSetupModeListener(new SetupModeListener(activity));
                    }
                }
            }
            Log.d("PvMActivity", "摆棋模式视图初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing setup mode view: " + e.getMessage());
        }

        // 然后添加棋盘视图
        try {
            if (activity.chessInfo != null) {
                activity.chessView = new ChessView(activity, activity.chessInfo);
                if (activity.chessView != null) {
                    activity.chessView.setOnTouchListener(new ChessViewTouchListener(activity));
                    activity.relativeLayout.addView(activity.chessView);

                    android.widget.RelativeLayout.LayoutParams paramsChess = (android.widget.RelativeLayout.LayoutParams) activity.chessView.getLayoutParams();
                    if (paramsChess != null) {
                        paramsChess.addRule(android.widget.RelativeLayout.BELOW, R.id.setupModeView);
                        paramsChess.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsChess.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsChess.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        // 左右不留边距，棋盘铺满屏幕宽度
                        paramsChess.setMargins(0, 0, 0, 0);
                        activity.chessView.setLayoutParams(paramsChess);
                        activity.chessView.setId(R.id.chessView);
                    }
                }
            }
            Log.d("PvMActivity", "棋盘视图初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing chess view: " + e.getMessage());
        }

        // 棋盘两侧的浮动「上一步/下一步」大按钮（底部按钮行已移除上一步/下一步）
        try {
            float density = activity.getResources().getDisplayMetrics().density;
            final int navW = (int) (60 * density); // 按钮长度（左右方向），略加长
            final int navH = (int) (44 * density); // 按钮高度，降低
            final int edge = (int) (4 * density);

            // 左侧：上一步
            final android.widget.Button fPrev = new android.widget.Button(activity);
            fPrev.setBackgroundResource(R.drawable.bg_board_btn_nav_prev);
            fPrev.setText("");
            fPrev.setGravity(android.view.Gravity.CENTER);
            fPrev.setPadding(0, 0, 0, 0);
            fPrev.setContentDescription("上一步");
            android.widget.RelativeLayout.LayoutParams lpPrev = new android.widget.RelativeLayout.LayoutParams(navW, navH);
            lpPrev.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
            fPrev.setLayoutParams(lpPrev);
            activity.relativeLayout.addView(fPrev);
            fPrev.setOnClickListener(v -> {
                if (activity.controlsManager != null) activity.controlsManager.handlePrevButton();
            });
            fPrev.setOnLongClickListener(v -> {
                showJumpToStepDialog();
                return true; // 消费长按，避免再触发单击单步
            });
            activity.btnPrev = fPrev;

            // 右侧：下一步
            final android.widget.Button fNext = new android.widget.Button(activity);
            fNext.setBackgroundResource(R.drawable.bg_board_btn_nav_next);
            fNext.setText("");
            fNext.setGravity(android.view.Gravity.CENTER);
            fNext.setPadding(0, 0, 0, 0);
            fNext.setContentDescription("下一步");
            android.widget.RelativeLayout.LayoutParams lpNext = new android.widget.RelativeLayout.LayoutParams(navW, navH);
            lpNext.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
            fNext.setLayoutParams(lpNext);
            activity.relativeLayout.addView(fNext);
            fNext.setOnClickListener(v -> {
                if (activity.controlsManager != null) activity.controlsManager.handleNextButton();
            });
            fNext.setOnLongClickListener(v -> {
                showJumpToStepDialog();
                return true; // 消费长按，避免再触发单击单步
            });
            activity.btnNext = fNext;

            // 棋盘布局完成后，将两侧浮动按钮垂直居中于棋盘
            activity.chessView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int boardTop = activity.chessView.getTop();
                        int boardH = activity.chessView.getHeight();
                        if (boardH <= 0) return;
                        int offset = boardTop + boardH / 2 - navH / 2;
                        android.widget.RelativeLayout.LayoutParams p = (android.widget.RelativeLayout.LayoutParams) fPrev.getLayoutParams();
                        p.setMargins(edge, offset, 0, 0); // 左侧按钮：贴左缘、垂直居中
                        fPrev.setLayoutParams(p);
                        android.widget.RelativeLayout.LayoutParams n = (android.widget.RelativeLayout.LayoutParams) fNext.getLayoutParams();
                        n.setMargins(0, offset, edge, 0); // 右侧按钮：贴右缘、垂直居中
                        fNext.setLayoutParams(n);
                        android.view.ViewTreeObserver vto = activity.chessView.getViewTreeObserver();
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            vto.removeOnGlobalLayoutListener(this);
                        } else {
                            vto.removeGlobalOnLayoutListener(this);
                        }
                    }
                });

            // 确保浮动按钮绘制在棋盘之上
            fPrev.bringToFront();
            fNext.bringToFront();
        } catch (Exception e) {
            LogUtils.e("PvMActivityInit", "添加棋盘两侧导航/模拟提示失败: " + e.getMessage());
        }

        // 初始化AI支招信息显示 - 由aiManager处理
        Log.d("PvMActivity", "AI支招信息显示初始化完成");

        // 最后添加按钮组，确保它在顶层
        try {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(activity);
                if (inflater != null) {
                    android.widget.LinearLayout buttonGroup = (android.widget.LinearLayout) inflater.inflate(R.layout.button_group, activity.relativeLayout, false);
                    if (buttonGroup != null) {
                        activity.relativeLayout.addView(buttonGroup);

                        // 使用手动定义的ID，并同步到 activity 字段供摆棋禁用逻辑使用
                        activity.buttonGroupId = 10001;
                        buttonGroup.setId(activity.buttonGroupId);

                        // 绑定按钮组底部的引擎结果框引用
                        activity.engineResultContainer = buttonGroup.findViewById(R.id.engine_result_container);
                        activity.engineResultScroll = buttonGroup.findViewById(R.id.engine_result_scroll);
                        // 绑定底部评分曲线视图（提示盒背景层）
                        activity.scoreCurveView = buttonGroup.findViewById(R.id.score_curve_view);

                        android.widget.RelativeLayout.LayoutParams paramsV = (android.widget.RelativeLayout.LayoutParams) buttonGroup.getLayoutParams();
                        if (paramsV != null) {
                            paramsV.addRule(android.widget.RelativeLayout.BELOW, R.id.chessView);
                            paramsV.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                            paramsV.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                            // 控制栏延伸到底部，让评分曲线占满剩余区域、贴近屏幕底线
                            paramsV.height = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                            paramsV.setMargins(0, 0, 0, 0);
                            buttonGroup.setLayoutParams(paramsV);

                            // 让控制面板与按钮组一起延伸到屏幕底部，评分曲线框用 weight 占满剩余垂直空间
                            try {
                                android.widget.LinearLayout controlPanel = buttonGroup.findViewById(R.id.control_panel_ui);
                                if (controlPanel != null) {
                                    android.widget.LinearLayout.LayoutParams cpParams = new android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
                                    controlPanel.setLayoutParams(cpParams);
                                }
                                android.widget.FrameLayout curveFrame = buttonGroup.findViewById(R.id.score_curve_frame);
                                if (curveFrame != null) {
                                    android.widget.LinearLayout.LayoutParams cfParams = new android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0);
                                    cfParams.weight = 1f;
                                    curveFrame.setLayoutParams(cfParams);
                                }
                                if (activity.scoreCurveView != null) {
                                    // 曲线高度 = 外框（score_curve_frame）高度的 70%，并垂直居中；
                                    // 外框由 weight=1 动态撑出，故在其布局完成后按实测高度计算（不写死）。
                                    final android.widget.FrameLayout fFrame = curveFrame;
                                    final android.view.View fCurve = activity.scoreCurveView;
                                    fFrame.getViewTreeObserver().addOnGlobalLayoutListener(
                                        new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                                            private int lastH = -1;
                                            @Override
                                            public void onGlobalLayout() {
                                                int frameH = fFrame.getHeight();
                                                if (frameH <= 0) return;
                                                int curveH = (int) (frameH * 0.7f);
                                                if (curveH == lastH) return; // 防止重复触发布局循环
                                                lastH = curveH;
                                                android.widget.FrameLayout.LayoutParams scParams =
                                                        new android.widget.FrameLayout.LayoutParams(
                                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                                curveH);
                                                scParams.gravity = android.view.Gravity.CENTER;
                                                fCurve.setLayoutParams(scParams);
                                            }
                                        });
                                }
                            } catch (Exception e) {
                                LogUtils.e("PvMActivityInit", "调整评分曲线区域失败: " + e.getMessage());
                            }

                            // 处理嵌套的LinearLayout布局
                            if (activity.controlsManager != null) {
                                activity.controlsManager.setupButtonListeners(buttonGroup);
                            }

                            // 上一步/下一步按钮已移到棋盘两侧（浮动大按钮），此处仅获取悔棋按钮引用
                            activity.btnRecall = buttonGroup.findViewById(R.id.btn_recall);

                            if (activity.notationManager != null) {
                                activity.notationManager.updateNavButtonsEnabled();
                            }
                            // 初始化「模式」按钮图标（以图标形式显示当前模式）
                            activity.updateModeButton();

                            // 顶部「菜单」按钮：下拉菜单包含 新局/保存/加载/设置/模式切换
                            android.view.View btnMenu = activity.findViewById(R.id.btn_menu);
                            if (btnMenu != null) {
                            btnMenu.setOnClickListener(v -> {
                                // 自定义下拉菜单（ListPopupWindow）：宽度紧凑可控、图标可见、有分隔线
                                final int[] iconRes = {R.drawable.ic_new_game, R.drawable.ic_load,
                                        R.drawable.ic_save, R.drawable.ic_settings};
                                final int[] actionIds = {R.id.btn_retry, R.id.btn_load,
                                        R.id.btn_save, R.id.btn_settings};
                                final String[] titles = {"新局", "加载", "保存", "设置"};
                                final int ICON_TINT = 0xFFE6B36A; // 暖金，深色背景上更醒目

                                final float dens = activity.getResources().getDisplayMetrics().density;
                                // 下拉菜单内容短（图标+2字），固定窄宽更紧凑；模式菜单因含双图标+4字名保持 168dp
                                final int compactW = (int) (120 * dens);

                                final android.widget.ArrayAdapter<String> adapter =
                                        new android.widget.ArrayAdapter<String>(activity, 0, titles) {
                                            @Override
                                            public android.view.View getView(int position,
                                                                             android.view.View convertView,
                                                                             android.view.ViewGroup parent) {
                                                if (convertView == null) {
                                                    convertView = android.view.LayoutInflater.from(activity)
                                                            .inflate(R.layout.popup_menu_item, parent, false);
                                                }
                                                ImageView iv = convertView.findViewById(R.id.iv_icon);
                                                TextView tv = convertView.findViewById(R.id.tv_title);
                                                tv.setText(titles[position]);
                                                iv.setImageDrawable(tintMenuIcon(activity,
                                                        iconRes[position], ICON_TINT));
                                                return convertView;
                                            }
                                        };

                                final androidx.appcompat.widget.ListPopupWindow lpw =
                                        new androidx.appcompat.widget.ListPopupWindow(activity);
                                lpw.setAnchorView(v);
                                lpw.setModal(true);
                                lpw.setWidth(compactW);
                                lpw.setAdapter(adapter);
                                lpw.setBackgroundDrawable(
                                        activity.getResources().getDrawable(R.drawable.popup_menu_bg));
                                // 垂直紧贴触发按钮（负值上移），与模式菜单弹出位置基准一致，避免整体偏下
                lpw.setVerticalOffset((int) (-4 * dens));
                lpw.setOnItemClickListener((parent, view, position, id) -> {
                    android.view.View target = buttonGroup.findViewById(actionIds[position]);
                    if (target != null) target.performClick();
                    lpw.dismiss();
                });
                lpw.show();
                // 加暖色分隔线，使各菜单项区分明显
                android.widget.ListView lv = lpw.getListView();
                if (lv != null) {
                    lv.setDivider(new android.graphics.drawable.ColorDrawable(0x557A6040));
                    lv.setDividerHeight((int) (1 * dens));
                }
                            });
                            }
                        }
                    }
                }
            Log.d("PvMActivity", "按钮组初始化完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error initializing button group: " + e.getMessage());
        }

        // 翻转功能已并入按钮组（btn_flip），不再使用浮动图标

        // 初始绘制界面
        try {
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            Log.d("PvMActivity", "初始界面绘制完成");
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error requesting draw: " + e.getMessage());
        }

        // 确保菜单 / 切换模式按钮始终绘制在棋盘与按钮组之上（否则会被后加入的 chessView 覆盖）
        try {
            android.view.View bm = activity.findViewById(R.id.btn_menu);
            if (bm != null) bm.bringToFront();
            android.view.View bms = activity.findViewById(R.id.btn_mode_switch);
            if (bms != null) bms.bringToFront();
        } catch (Exception e) {
            LogUtils.e("PvMActivityInit", "bringToFront 顶部按钮失败: " + e.getMessage());
        }
    }

    // 长按浮动「上一步/下一步」按钮：弹出棋谱所有步数列表，点击跳转
    private void showJumpToStepDialog() {
        if (activity == null || activity.notationManager == null) return;
        final Info.ChessNotation notation = activity.notationManager.getCurrentNotation();
        final int total = activity.notationManager.getOriginalTotalMoves();
        if (notation == null || total <= 0) {
            Toast.makeText(activity, "暂无可跳转的棋谱", Toast.LENGTH_SHORT).show();
            return;
        }
        final int current = activity.notationManager.getCurrentMoveIndex();
        final java.util.List<Info.ChessNotation.MoveRecord> records = notation.getMoveRecords();
        final boolean redFirst = notation.isRedFirst();

        // 选项：第 0 项=初始局面，1..total 为每一步
        final String[] items = new String[total + 1];
        items[0] = "初始局面（第 0 步）";
        for (int ply = 1; ply <= total; ply++) {
            final int recIdx = (ply - 1) / 2;
            String move = "";
            if (recIdx < records.size()) {
                final Info.ChessNotation.MoveRecord rec = records.get(recIdx);
                if (rec != null) {
                    final boolean isRed = redFirst ? (ply % 2 == 1) : (ply % 2 == 0);
                    move = isRed ? rec.redMove : rec.blackMove;
                }
            }
            items[ply] = "第 " + ply + " 步" + (move != null && !move.isEmpty() ? "  " + move : "");
        }

        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("选择要跳转的步数");
        builder.setSingleChoiceItems(items, current, (android.content.DialogInterface d, int which) -> {
            activity.notationManager.seekTo(which);
            d.dismiss();
        });
        builder.setNegativeButton("取消", (android.content.DialogInterface d, int which) -> { });
        builder.show();
    }

    public void initBackgroundTasks() {
        if (activity == null) {
            Log.e("PvMActivityInit", "Activity is null, cannot initialize background tasks");
            return;
        }
        
        Log.d("PvMActivity", "开始初始化后台任务");
        
        // 线程池由aiManager管理，不需要在这里初始化
        Log.d("PvMActivity", "线程池初始化完成");

        // 在后台线程中初始化音乐，避免阻塞主线程
        try {
            new Thread(new MusicInitRunnable(activity)).start();
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error starting music initialization thread: " + e.getMessage());
        }

        // 在后台线程中初始化数据文件（仅在文件不存在时）
        try {
            new Thread(new DataFileInitRunnable()).start();
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error starting data file initialization thread: " + e.getMessage());
        }

        // 在后台线程中加载数据，避免阻塞主线程
        try {
            new Thread(new DataLoadRunnable(activity)).start();
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error starting data loading thread: " + e.getMessage());
        }

        // 在后台线程中初始化PikafishAI，避免阻塞主线程
        try {
            new Thread(new AIInitRunnable(activity)).start();
        } catch (Exception e) {
            Log.e("PvMActivityInit", "Error starting AI initialization thread: " + e.getMessage());
        }
    }
    
    // 摆棋模式监听器类
    private static class SetupModeListener implements SetupModeView.OnSetupModeListener {
        private final PvMActivity activity;
        
        public SetupModeListener(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void onPieceSelected(int pieceID) {
            if (activity != null && activity.setupManager != null) {
                // 选择面板棋子时清除棋盘上的选中，保持选中状态互斥
                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                activity.setupManager.setSelectedPieceID(pieceID);
            }
        }

        @Override
        public void onClearBoard() {
            // 清空棋盘，除了老将
            if (activity != null && activity.chessInfo != null && activity.chessInfo.piece != null) {
                for (int i = 0; i < 10; i++) {
                    for (int j = 0; j < 9; j++) {
                        int pieceID = activity.chessInfo.piece[i][j];
                        // 保留老将（将、帅）
                        if (pieceID != 1 && pieceID != 8) {
                            activity.chessInfo.piece[i][j] = 0;
                        }
                    }
                }
                // 重新计算攻击棋子数量
                activity.chessInfo.attackNum_B = 0;
                activity.chessInfo.attackNum_R = 0;
                for (int i = 0; i < 10; i++) {
                    if (activity.chessInfo.piece[i] != null) {
                        for (int j = 0; j < 9; j++) {
                            int piece = activity.chessInfo.piece[i][j];
                            if (piece != 0) {
                                // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                                if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                                    activity.chessInfo.attackNum_B++;
                                }
                                // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                                else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                                    activity.chessInfo.attackNum_R++;
                                }
                            }
                        }
                    }
                }
                // 重新绘制
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                    activity.chessView.invalidate();
                    activity.chessView.postInvalidate();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                    activity.setupModeView.postInvalidate();
                }
            }
        }

        @Override
        public void onHelpClicked() {
            if (activity != null && activity.setupManager != null) {
                activity.setupManager.showSetupHelp();
            }
        }

        @Override
        public void onCameraClicked() {
            if (activity != null) {
                activity.dispatchCameraIntent();
            }
        }

        @Override
        public void onGalleryClicked() {
            if (activity != null) {
                activity.dispatchGalleryIntent();
            }
        }
    }

    // 棋盘触摸监听器类
    private static class ChessViewTouchListener implements android.view.View.OnTouchListener {
        private final PvMActivity activity;
        
        public ChessViewTouchListener(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public boolean onTouch(android.view.View view, android.view.MotionEvent event) {
            if (activity == null || activity.chessView == null) {
                return false;
            }

            // 翻转状态下变换触摸坐标，使触摸与视觉一致（仅显示调度，不动数据）
            android.view.MotionEvent ev = activity.chessView.transformTouchForFlip(event);

            // 先让ChessView处理触摸事件（用于摆棋窗口拖动和棋子点击）
            activity.chessView.onTouchEvent(ev);

            // 处理摆棋模式下的触摸事件
            if (activity.chessInfo != null && activity.chessInfo.IsSetupMode && ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                // 处理棋盘上的放置逻辑
                float x = ev.getX();
                float y = ev.getY();
                if (x >= 0 && x <= activity.chessView.Board_width && y >= 0 && y <= activity.chessView.Board_height) {
                    int[] pos = activity.getPos(ev);
                    if (pos != null) {
                        activity.chessInfo.Select = pos;
                        int i = pos[0];
                        int j = pos[1];

                        if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && activity.chessInfo.piece != null && activity.chessInfo.piece[j] != null) {
                            // 获取点击位置的棋子ID
                            int boardPieceID = activity.chessInfo.piece[j][i];
                            
                            // 如果已经选中了棋盘上的棋子
                            if (activity.setupManager != null) {
                                int[] selectedBoardPiecePos = activity.setupManager.getSelectedBoardPiecePos();
                                if (selectedBoardPiecePos != null && selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1 && activity.chessInfo.piece[selectedBoardPiecePos[1]] != null) {
                                    // 获取要操作的棋子ID
                                    int pieceToOperate = activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                                    
                                    // 检查是否是点击原位置（下架）
                                    if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                        // 点击原位置，下架棋子
                                        if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                                            activity.setupManager.placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                            // 重置选中状态
                                            activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                        }
                                    }
                                    // 点击的是空白区域（移动棋子）
                                    else if (boardPieceID == 0) {
                                        // 检查是否是老将
                                        if (pieceToOperate == 1 || pieceToOperate == 8) {
                                            // 老将不能下架，但可以移动到合法位置
                                            // 检查新位置是否合理
                                            if (activity.setupManager.isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                activity.setupManager.placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                            }
                                        } else {
                                            // 不是老将，可以移动
                                            // 检查新位置是否合理
                                            if (activity.setupManager.isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                activity.setupManager.placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                            }
                                        }
                                    }
                                }
                                // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                                else if (activity.setupManager.getSelectedPieceID() > 0) {
                                    activity.setupManager.placePiece(i, j, activity.setupManager.getSelectedPieceID());
                                    // 重置选中状态
                                    activity.setupManager.setSelectedPieceID(0);
                                    if (activity.setupModeView != null) {
                                        activity.setupModeView.clearSelection();
                                    }
                                }
                                // 如果点击的是棋盘上的棋子，选中该棋子
                                else if (boardPieceID > 0) {
                                    activity.setupManager.setSelectedBoardPiecePos(new int[]{i, j});
                                    // 显示选中效果
                                    activity.chessInfo.Select = new int[]{i, j};
                                    activity.chessView.requestDraw();
                                }
                                // 点击空白区域，重置选中状态
                                else {
                                    activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                    activity.chessInfo.Select = new int[]{-1, -1};
                                    activity.chessView.requestDraw();
                                }
                            }
                        }
                    }
                }
                if (ev != event) ev.recycle();
                return true;
            }

            // 非摆棋模式下，由Activity处理触摸事件
            boolean result = activity.onTouch(view, ev);
            if (ev != event) ev.recycle();
            return result;
        }
    }
    
    // 数据文件初始化的Runnable类
    private static class DataFileInitRunnable implements Runnable {
        @Override
        public void run() {
            Log.d("PvMActivity", "开始初始化数据文件");
            try {
                // 只有在文件不存在时才创建新的文件
                if (!SaveInfo.fileIsExists("ChessInfo_pvm.bin")) {
                    SaveInfo.SerializeChessInfo(new ChessInfo(), "ChessInfo_pvm.bin");
                }
                if (!SaveInfo.fileIsExists("InfoSet_pvm.bin")) {
                    SaveInfo.SerializeInfoSet(new InfoSet(), "InfoSet_pvm.bin");
                }
                Log.d("PvMActivity", "数据文件初始化完成");
            } catch (Exception e) {
                LogUtils.e("PvMActivityInit", "操作失败", e);
            }
        }
    }
    
    // 数据加载的Runnable类
    private static class DataLoadRunnable implements Runnable {
        private final PvMActivity activity;
        
        public DataLoadRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            Log.d("PvMActivity", "开始加载数据");
            try {
                // 总是使用新的游戏状态，不加载旧存档
                // 这样可以确保新局总是被正确初始化
                if (activity != null) {
                    activity.runOnUiThread(new LoadDefaultAllRunnable(activity));
                }
                Log.d("PvMActivity", "数据加载完成");
            } catch (Exception e) {
                LogUtils.e("PvMActivityInit", "操作失败", e);
                // 确保在发生任何异常时，应用不会崩溃
                if (activity != null) {
                    activity.runOnUiThread(new LoadDefaultAllRunnable(activity));
                }
            }
        }
    }
    
    // 加载ChessInfo的UI Runnable类
    private static class LoadChessInfoRunnable implements Runnable {
        private final PvMActivity activity;
        private final ChessInfo loadedChessInfo;
        
        public LoadChessInfoRunnable(PvMActivity activity, ChessInfo loadedChessInfo) {
            this.activity = activity;
            this.loadedChessInfo = loadedChessInfo;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                activity.chessInfo = loadedChessInfo;
                // 总是设置setting，确保使用最新的设置
                activity.chessInfo.setting = activity.setting;
                // 更新roundView、chessView和setupModeView中的chessInfo引用
                if (activity.roundView != null) {
                    activity.roundView.setChessInfo(activity.chessInfo);
                }
                if (activity.chessView != null) {
                    activity.chessView.setChessInfo(activity.chessInfo);
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.setChessInfo(activity.chessInfo);
                }
                // 重新绘制界面
                if (activity.roundView != null) {
                    activity.roundView.requestDraw();
                }
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                }
                Log.d("PvMActivity", "ChessInfo数据加载完成");
            }
        }
    }
    
    // 加载默认ChessInfo的UI Runnable类
    private static class LoadDefaultChessInfoRunnable implements Runnable {
        private final PvMActivity activity;
        
        public LoadDefaultChessInfoRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                activity.chessInfo = new ChessInfo();
                // 总是设置setting，确保使用最新的设置
                activity.chessInfo.setting = activity.setting;
                // 更新roundView、chessView和setupModeView中的chessInfo引用
                if (activity.roundView != null) {
                    activity.roundView.setChessInfo(activity.chessInfo);
                }
                if (activity.chessView != null) {
                    activity.chessView.setChessInfo(activity.chessInfo);
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.setChessInfo(activity.chessInfo);
                }
                // 重新绘制界面
                if (activity.roundView != null) {
                    activity.roundView.requestDraw();
                }
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                }
            }
        }
    }
    
    // 加载InfoSet的UI Runnable类
    private static class LoadInfoSetRunnable implements Runnable {
        private final PvMActivity activity;
        private final InfoSet loadedInfoSet;
        
        public LoadInfoSetRunnable(PvMActivity activity, InfoSet loadedInfoSet) {
            this.activity = activity;
            this.loadedInfoSet = loadedInfoSet;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                activity.infoSet = loadedInfoSet;
                // 确保preInfo栈不为空，否则第一次悔棋会没反应
                if (activity.infoSet.preInfo == null || activity.infoSet.preInfo.isEmpty()) {
                    try {
                        activity.infoSet.pushInfo(activity.chessInfo);
                    } catch (CloneNotSupportedException ce) {
                        LogUtils.e("PvMActivityInit", "操作失败", ce);
                    }
                }
                Log.d("PvMActivity", "InfoSet数据加载完成");
            }
        }
    }
    
    // 加载默认InfoSet的UI Runnable类
    private static class LoadDefaultInfoSetRunnable implements Runnable {
        private final PvMActivity activity;
        
        public LoadDefaultInfoSetRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                activity.infoSet = new InfoSet();
                // 将初始状态保存到preInfo栈中
                try {
                    activity.infoSet.pushInfo(activity.chessInfo);
                } catch (CloneNotSupportedException ce) {
                    LogUtils.e("PvMActivityInit", "操作失败", ce);
                }
            }
        }
    }
    
    // 推送初始Info的UI Runnable类
    private static class PushInitialInfoRunnable implements Runnable {
        private final PvMActivity activity;
        
        public PushInitialInfoRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                try {
                    activity.infoSet.pushInfo(activity.chessInfo);
                } catch (CloneNotSupportedException e) {
                    LogUtils.e("PvMActivityInit", "操作失败", e);
                }
            }
        }
    }
    
    // 加载默认所有数据的UI Runnable类
    private static class LoadDefaultAllRunnable implements Runnable {
        private final PvMActivity activity;
        
        public LoadDefaultAllRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null) {
                activity.chessInfo = new ChessInfo();
                // 总是设置setting，确保使用最新的设置
                activity.chessInfo.setting = activity.setting;
                activity.infoSet = new InfoSet();
                try {
                    activity.infoSet.pushInfo(activity.chessInfo);
                } catch (CloneNotSupportedException ce) {
                    LogUtils.e("PvMActivityInit", "操作失败", ce);
                }
                // 更新roundView、chessView和setupModeView中的chessInfo引用
                if (activity.roundView != null) {
                    activity.roundView.setChessInfo(activity.chessInfo);
                }
                if (activity.chessView != null) {
                    activity.chessView.setChessInfo(activity.chessInfo);
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.setChessInfo(activity.chessInfo);
                }
                // 重新绘制界面
                if (activity.roundView != null) {
                    activity.roundView.requestDraw();
                }
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                }
            }
        }
    }
    
    // 音乐初始化的Runnable类
    private static class MusicInitRunnable implements Runnable {
        private final PvMActivity activity;
        
        public MusicInitRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            Log.d("PvMActivity", "开始初始化音乐");
            // 初始化音乐的代码直接在这里实现
            try {
                // 初始化背景音乐
                if (activity.backMusic == null) {
                    try {
                        if (activity != null) {
                            GameResourceManager manager = GameResourceManager.getInstance();
                            manager.initBackgroundMusic(activity);
                            activity.backMusic = manager.getBackMusic();
                            if (activity.backMusic != null) {
                                activity.backMusic.setLooping(true);
                                if (activity.setting != null && activity.setting.isMusicPlay) {
                                    activity.backMusic.start();
                                }
                            }
                        }
                    } catch (Exception e) {
                        LogUtils.e("PvMActivityInit", "操作失败", e);
                    }
                }
                
                // 音效已在主线程initMusic()中初始化，此处不再重复初始化
            } catch (Exception e) {
                LogUtils.e("PvMActivityInit", "操作失败", e);
            }
            Log.d("PvMActivity", "音乐初始化完成");
        }
    }
    
    // AI初始化的Runnable类
    private static class AIInitRunnable implements Runnable {
        private final PvMActivity activity;
        
        public AIInitRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            Log.d("PvMActivity", "开始初始化PikafishAI");
            try {
                if (activity != null) {
                    activity.pikafishAI = new PikafishAI(activity);
                    
                    // 设置初始化监听器 - 使用独立的监听器类
                    activity.pikafishAI.setInitializationListener(new AIInitListener(activity));
                    
                    Log.d("PvMActivity", "PikafishAI初始化启动");
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivityInit", "操作失败", e);
                if (activity != null && activity.roundView != null) {
                    activity.runOnUiThread(new AIInitFailureRunnable(activity));
                }
            }
        }
    }
    
    // AI初始化监听器类
    private static class AIInitListener implements PikafishAI.InitializationListener {
        private final PvMActivity activity;
        
        public AIInitListener(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void onInitializationStarted() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (activity != null && activity.roundView != null) {
                activity.runOnUiThread(new AILoadingStartRunnable(activity));
            }
        }
        
        @Override
        public void onInitializationCompleted() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (activity != null && activity.roundView != null) {
                activity.runOnUiThread(new AILoadingCompleteRunnable(activity));
            }
            // PikafishAI 初始化完成后，再启动 ONNX 识别服务初始化，
            // 避免两个重量级 native 初始化并发导致内存压力崩溃
            if (activity != null) {
                activity.initRecognitionService();
            }
        }

        @Override
        public void onInitializationFailed() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (activity != null && activity.roundView != null) {
                activity.runOnUiThread(new AILoadingFailureRunnable(activity));
            }
            // 即使 AI 初始化失败，也启动识别服务（用户仍可使用拍照识别功能）
            if (activity != null) {
                activity.initRecognitionService();
            }
        }
    }
    
    // UI更新 - AI开始加载
    private static class AILoadingStartRunnable implements Runnable {
        private final PvMActivity activity;
        
        public AILoadingStartRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null && activity.roundView != null) {
                activity.roundView.setAILoading(true);
                LogUtils.i("PvMActivity", "AI开始加载");
            }
        }
    }
    
    // UI更新 - AI加载完成
    private static class AILoadingCompleteRunnable implements Runnable {
        private final PvMActivity activity;
        
        public AILoadingCompleteRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null && activity.roundView != null) {
                activity.roundView.setAILoading(false);
                LogUtils.i("PvMActivity", "AI加载完成");
            }
        }
    }
    
    // UI更新 - AI加载失败
    private static class AILoadingFailureRunnable implements Runnable {
        private final PvMActivity activity;
        
        public AILoadingFailureRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null && activity.roundView != null) {
                activity.roundView.setAILoading(false);
                LogUtils.e("PvMActivity", "AI加载失败");
            }
        }
    }
    
    // UI更新 - AI初始化异常
    private static class AIInitFailureRunnable implements Runnable {
        private final PvMActivity activity;
        
        public AIInitFailureRunnable(PvMActivity activity) {
            this.activity = activity;
        }
        
        @Override
        public void run() {
            if (activity != null && activity.roundView != null) {
                activity.roundView.setAILoading(false);
            }
        }
    }

    /** 给菜单图标着色（暖金），在深色背景上更醒目；返回 mutate 后的 drawable */
    private static android.graphics.drawable.Drawable tintMenuIcon(android.content.Context ctx,
                                                                    int resId, int color) {
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat
                .getDrawable(ctx, resId).mutate();
        DrawableCompat.setTint(d, color);
        return d;
    }
}