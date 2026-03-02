package top.nones.chessgame;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import Info.ChessNotation;

import java.util.List;

public class NotationActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private ListView lvNotations;
    private Button btnBack;
    private Button btnLoad;
    private Button btnBackToList;
    private TextView tvNotationInfo;
    private ListView lvMoveRecords;
    private List<String> notationList;
    private List<String> moveRecordList;
    private ArrayAdapter<String> adapter;
    private ArrayAdapter<String> moveAdapter;
    private boolean returnToGame;
    private boolean isShowingDetail = false;
    private ChessNotation currentNotation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notation);

        // 初始化SaveInfo
        Info.SaveInfo.init(this);

        // 初始化视图
        lvNotations = findViewById(R.id.lv_notations);
        btnBack = findViewById(R.id.btn_back);
        btnLoad = findViewById(R.id.btn_load);
        btnBackToList = findViewById(R.id.btn_back_to_list);
        tvNotationInfo = findViewById(R.id.tv_notation_info);
        lvMoveRecords = findViewById(R.id.lv_move_records);

        // 检查是否是从游戏界面返回
        Intent intent = getIntent();
        returnToGame = intent != null && intent.getBooleanExtra("returnToGame", false);

        // 设置按钮监听器
        btnBack.setOnClickListener(this);
        btnLoad.setOnClickListener(this);
        btnBackToList.setOnClickListener(this);

        // 初始状态：显示棋谱列表
        showNotationList();

        // 加载棋谱列表
        loadNotations();

        // 设置列表监听器
        lvNotations.setOnItemClickListener(this);
        lvNotations.setOnItemLongClickListener(this);
    }

    private void showNotationList() {
        isShowingDetail = false;
        lvNotations.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        btnLoad.setVisibility(View.GONE);
        btnBackToList.setVisibility(View.GONE);
        tvNotationInfo.setVisibility(View.GONE);
        lvMoveRecords.setVisibility(View.GONE);
    }

    private void showNotationDetail() {
        isShowingDetail = true;
        lvNotations.setVisibility(View.GONE);
        btnBack.setVisibility(View.GONE);
        btnLoad.setVisibility(View.VISIBLE);
        btnBackToList.setVisibility(View.VISIBLE);
        tvNotationInfo.setVisibility(View.VISIBLE);
        lvMoveRecords.setVisibility(View.VISIBLE);
    }

    private void loadNotations() {
        // 获取保存的棋谱列表
        notationList = ChessNotation.getSavedNotations(this);
        if (notationList == null) {
            notationList = java.util.Collections.emptyList();
        }

        // 创建适配器
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, notationList);
        lvNotations.setAdapter(adapter);
    }

    private void displayNotationInfo(ChessNotation notation) {
        if (notation == null) return;
        
        StringBuilder infoBuilder = new StringBuilder();
        infoBuilder.append("[对局信息]\n");
        if (notation.getPlayerRed() != null) {
            infoBuilder.append("红方: " + notation.getPlayerRed() + "\n");
        }
        if (notation.getPlayerBlack() != null) {
            infoBuilder.append("黑方: " + notation.getPlayerBlack() + "\n");
        }
        if (notation.getMatchDate() != null) {
            infoBuilder.append("日期: " + notation.getMatchDate() + "\n");
        }
        if (notation.getLocation() != null) {
            infoBuilder.append("地点: " + notation.getLocation() + "\n");
        }
        if (notation.getResult() != null) {
            infoBuilder.append("结果: " + notation.getResult() + "\n");
        }
        infoBuilder.append("\n总回合数: " + notation.getMoveRecords().size());
        tvNotationInfo.setText(infoBuilder.toString());
    }

    private void displayMoveRecords(ChessNotation notation) {
        if (notation == null) return;
        
        moveRecordList = new java.util.ArrayList<>();
        List<ChessNotation.MoveRecord> moveRecords = notation.getMoveRecords();
        for (int i = 0; i < moveRecords.size(); i++) {
            ChessNotation.MoveRecord record = moveRecords.get(i);
            moveRecordList.add((i + 1) + ". " + record.redMove + " " + record.blackMove);
        }
        moveAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, moveRecordList);
        lvMoveRecords.setAdapter(moveAdapter);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_back) {
            finish();
        } else if (id == R.id.btn_load) {
            // 加载棋谱并返回游戏界面
            if (currentNotation != null) {
                Intent intent = new Intent();
                intent.putExtra("notation", currentNotation);
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(this, "没有加载棋谱", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.btn_back_to_list) {
            // 返回棋谱列表
            showNotationList();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String fileName = notationList.get(position);
        if (returnToGame) {
            // 加载棋谱并返回游戏界面
            ChessNotation notation = ChessNotation.loadFromFile(this, fileName);
            if (notation != null) {
                Intent intent = new Intent();
                intent.putExtra("notation", notation);
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(this, "加载棋谱失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 查看棋谱详情
            currentNotation = ChessNotation.loadFromFile(this, fileName);
            if (currentNotation != null) {
                displayNotationInfo(currentNotation);
                displayMoveRecords(currentNotation);
                showNotationDetail();
            } else {
                Toast.makeText(this, "加载棋谱失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        String fileName = notationList.get(position);
        // 删除棋谱
        boolean deleted = ChessNotation.deleteNotation(this, fileName);
        if (deleted) {
            Toast.makeText(this, "棋谱已删除", Toast.LENGTH_SHORT).show();
            loadNotations();
        } else {
            Toast.makeText(this, "删除棋谱失败", Toast.LENGTH_SHORT).show();
        }
        return true;
    }
}
