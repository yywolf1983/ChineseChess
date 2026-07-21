package top.nones.chessgame;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * 流式布局：子 View 按自身宽度从左到右排列，行宽不足时自动折行。
 * 子项保持各自固有宽度（若 LayoutParams 指定了固定像素宽则按该宽测量），
 * 不强制等宽；每行高度取该行最高子项，行与行之间以 vSpacing 间隔。
 */
public class FlowLayout extends ViewGroup {
    private int hSpacing;
    private int vSpacing;

    private ArrayList<ArrayList<Integer>> mLineIndices;
    private int[] mLineHeights;

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float d = context.getResources().getDisplayMetrics().density;
        hSpacing = Math.round(4 * d);
        vSpacing = Math.round(4 * d);
    }

    public void setSpacingDp(int h, int v) {
        float d = getResources().getDisplayMetrics().density;
        hSpacing = Math.round(h * d);
        vSpacing = Math.round(v * d);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int paddingL = getPaddingLeft();
        int paddingR = getPaddingRight();
        int paddingT = getPaddingTop();
        int paddingB = getPaddingBottom();
        int avail = width - paddingL - paddingR;

        int count = getChildCount();
        if (count == 0) {
            setMeasuredDimension(width, paddingT + paddingB);
            return;
        }

        int[] w = new int[count];
        int[] h = new int[count];
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) {
                w[i] = 0;
                h[i] = 0;
                continue;
            }
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            int childWidthSpec;
            if (lp.width > 0) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(avail, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
            v.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            w[i] = v.getMeasuredWidth();
            h[i] = v.getMeasuredHeight();
        }

        mLineIndices = new ArrayList<>();
        ArrayList<Integer> cur = new ArrayList<>();
        int lineW = 0;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i).getVisibility() == GONE) continue;
            int add = w[i] + (cur.isEmpty() ? 0 : hSpacing);
            if (!cur.isEmpty() && lineW + add > avail) {
                mLineIndices.add(cur);
                cur = new ArrayList<>();
                lineW = 0;
            }
            cur.add(i);
            lineW += (cur.size() == 1 ? 0 : hSpacing) + w[i];
        }
        if (!cur.isEmpty()) mLineIndices.add(cur);

        mLineHeights = new int[mLineIndices.size()];
        int totalH = paddingT + paddingB;
        for (int li = 0; li < mLineIndices.size(); li++) {
            if (li > 0) totalH += vSpacing;
            int lh = 0;
            for (int idx : mLineIndices.get(li)) {
                if (h[idx] > lh) lh = h[idx];
            }
            mLineHeights[li] = lh;
            totalH += lh;
        }

        setMeasuredDimension(width, totalH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingL = getPaddingLeft();
        int paddingT = getPaddingTop();
        int x = paddingL;
        int y = paddingT;
        for (int li = 0; li < mLineIndices.size(); li++) {
            for (int idx : mLineIndices.get(li)) {
                View v = getChildAt(idx);
                if (v.getVisibility() == GONE) continue;
                int cw = v.getMeasuredWidth();
                int ch = v.getMeasuredHeight();
                v.layout(x, y, x + cw, y + ch);
                x += cw + hSpacing;
            }
            y += mLineHeights[li] + vSpacing;
            x = paddingL;
        }
    }
}
