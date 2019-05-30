package com.lhg.excelview;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelView extends ViewGroup {

    private static final String TAG = "ExcelView";
    private final int mMinimumVelocity;
    private final int mMaximumVelocity;

    boolean mIsLayouting = false;
    final LayoutState mPreLayoutState = new LayoutState();
    final LayoutState mVisibleState = new LayoutState();
    final Map<Position, Cell> mVisibleCells = new HashMap<>();
    final Recycler mRecycler = new Recycler();

    boolean mIsDragging = false;
    Scroller mScroller;
    int mTouchSlop;
    Point mLastMotion;
    VelocityTracker mVelocityTracker;

    ExcelAdapter mAdapter;
    int mDividerColor = Color.LTGRAY;
    int mDividerWidth = 2;
    Paint mDividerPaint = new Paint();

    ScrollHelper mScrollHelperY = new ScrollHelper() {
        @Override
        public int getViewCount() {
            return mAdapter != null ? mAdapter.getRowCount():0;
        }

        @Override
        public int getViewSize(int index) {
            return mAdapter.getRowHeight(index);
        }
    };
    ScrollHelper mScrollHelperX = new ScrollHelper() {
        @Override
        public int getViewCount() {
            return mAdapter != null ? mAdapter.getColCount():0;
        }

        @Override
        public int getViewSize(int index) {
            return mAdapter.getColWidth(index);
        }
    };


    public ExcelView(@NonNull Context context) {
        this(context, null);
    }

    public ExcelView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExcelView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setChildrenDrawingOrderEnabled(true);
        mScroller = new Scroller(context);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mTouchSlop = viewConfiguration.getScaledTouchSlop();
        mMinimumVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        mDividerPaint.setAntiAlias(true);
        mDividerPaint.setColor(mDividerColor);
        mDividerPaint.setStrokeWidth(mDividerWidth);
    }

    public void setDividerWidth(int dividerWidth) {
        this.mDividerWidth = dividerWidth;
        mPreLayoutState.invalid = true;
        mVisibleState.invalid = true;
        mDividerPaint.setStrokeWidth(dividerWidth);
        requestLayout();
    }

    public void setDividerColor(int dividerColor) {
        this.mDividerColor = dividerColor;
        mDividerPaint.setColor(dividerColor);
        invalidate();
    }

    private DataSetObserver mObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            mPreLayoutState.invalid = true;
            mVisibleState.invalid = true;
            requestLayout();
        }
    };

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        requestLayout();
    }

    public ExcelAdapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(ExcelAdapter adapter) {
        mRecycler.clear();
        mVisibleCells.clear();
        mVisibleState.init();
        mPreLayoutState.init();
        removeAllViews();

        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            mAdapter.registerDataSetObserver(mObserver);
        }
        requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mLastMotion = new Point((int) ev.getX(), (int) ev.getY());
                if (mScroller != null) {
                    if (!mScroller.isFinished()) {
                        mScroller.forceFinished(true);
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (ev.getX() - mLastMotion.x);
                int dy = (int) (ev.getY() - mLastMotion.y);
                if (Math.max(Math.abs(dx), Math.abs(dy)) >= mTouchSlop) {
                    mIsDragging = true;
                }
                return mIsDragging;//必须 拦截move, 否则 自视图中如果设置了onclick事件,则无法滚动
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (ev.getX() - mLastMotion.x);
                int dy = (int) (ev.getY() - mLastMotion.y);
                if (Math.max(Math.abs(dx), Math.abs(dy)) > mTouchSlop) {
                    mIsDragging = true;
                }
                if (mIsDragging) {
                    Log.i(TAG, "move " + (getScrollX() - dx) + ", " + (getScrollY() - dy));
                    scrollTo(getScrollX() - dx, getScrollY() - dy);
                    mLastMotion = new Point((int) ev.getX(), (int) ev.getY());
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mVelocityTracker.computeCurrentVelocity(1000);
                int velocityX = (int) mVelocityTracker.getXVelocity();
                int velocityY = (int) mVelocityTracker.getYVelocity();
                Log.i(TAG, "1velocityX=" + velocityX + ", velocityY=" + velocityY);
                if (Math.abs(velocityX) >= Math.abs(velocityY)) {
                    velocityY = 0;
                } else {
                    velocityX = 0;
                }
                if ((Math.abs(velocityX) <= mMinimumVelocity)) {
                    velocityX = 0;
                }
                if ((Math.abs(velocityY) <= mMinimumVelocity)) {
                    velocityY = 0;
                }

                Log.i(TAG, "2velocityX=" + velocityX + ", velocityY=" + velocityY);
                if ((velocityX !=0 || velocityY != 0) && getChildCount() > 0) {
                    mScroller.fling(getScrollX(), getScrollY(), -velocityX, -velocityY,
                            0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
                    invalidate();
                }
                mIsDragging = false;
                if (mVelocityTracker != null) {
                    mVelocityTracker.clear();
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            }
        }
        return true;
    }


    @Override
    public void scrollTo(int x, int y) {
        preLayoutAndAdjustScroll(x, y);
        if (mPreLayoutState.scrollX != x || mPreLayoutState.scrollY != y) {
            //被调整了,取消接下来的滑动
            if (!mScroller.isFinished()) {
                mScroller.forceFinished(true);
            }
        }
        super.scrollTo(mPreLayoutState.scrollX, mPreLayoutState.scrollY);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        layoutChildren();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutChildren();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        Cell cell = mVisibleCells.get(new Position(0,0));
        if (cell == null) {
            return;
        }
        LayoutState state = mVisibleState;

        //画body
        canvas.save();
        canvas.clipRect(cell.w + state.scrollX, cell.h + state.scrollY,
                state.scrollX + getWidth(), state.scrollY + getHeight());
        for (Position pos : mVisibleCells.keySet()) {
            if (pos.col == 0 || pos.row == 0) {
                continue;
            }
            drawCellDivider(canvas, mVisibleCells.get(pos));
        }
        canvas.restore();

        //画header
        for (Position pos : mVisibleCells.keySet()) {
            if (pos.col == 0 || pos.row == 0) {
               drawCellDivider(canvas, mVisibleCells.get(pos));
            }
        }
    }

    private void drawCellDivider(Canvas canvas, Cell cell) {
        View v = cell.view;
        float w = mDividerWidth;
        //底部横线
        canvas.drawLine(v.getLeft(), v.getBottom() + w / 2, v.getRight() + w, v.getBottom() + w / 2, mDividerPaint);
        //右侧竖线
        canvas.drawLine(v.getRight() + w / 2, v.getTop(), v.getRight() + w / 2, v.getBottom() + mDividerWidth, mDividerPaint);
    }

    //计算bodycell和调整scrollxy
    private void preLayoutAndAdjustScroll(int scrollX, int scrollY) {
        boolean layout = false;
        LayoutState state = mPreLayoutState;
        if (state.invalid || state.width != getWidth() || state.scrollX != scrollX) {
            mScrollHelperX.scroll(state.firstBodyColOffset, state.firstBodyCol, getWidth(), state.scrollX, scrollX);
            state.firstBodyCol = mScrollHelperX.startIndex;
            state.firstBodyColOffset = mScrollHelperX.startOffset;
            state.bodyColCount = mScrollHelperX.bodyCount;
            state.scrollX = mScrollHelperX.scroll;
            state.width = getWidth();
            layout = true;
        }

        if (state.invalid || state.height != getHeight() || state.scrollY != scrollY) {
            mScrollHelperY.scroll(state.firstBodyRowOffset, state.firstBodyRow, getHeight(), state.scrollY, scrollY);
            state.firstBodyRow = mScrollHelperY.startIndex;
            state.firstBodyRowOffset = mScrollHelperY.startOffset;
            state.bodyRowCount = mScrollHelperY.bodyCount;
            state.scrollY = mScrollHelperY.scroll;
            state.height = getHeight();
            layout = true;
        }
        state.invalid = false;
        if (layout) {
            Log.i(TAG, "prelayout = " + state);
        }
    }

    protected void layoutChildren() {
        if (mAdapter == null || mAdapter.getColCount() <= 0 || mAdapter.getRowCount() <= 0) {
            recycleAllCells();
            return;
        }

        if (mIsLayouting) {
            return;
        }

        preLayoutAndAdjustScroll(getScrollX(), getScrollY());
        if (mVisibleState.equals(mPreLayoutState) && !mVisibleState.invalid) {
            return;
        }

        mVisibleState.copyFrom(mPreLayoutState);
        Log.i(TAG, "layoutChildren " + mVisibleState);
        mIsLayouting = true;
        recycleCells();//TODO mVisibleState.invalid应该全部回收掉,
        LayoutState state = mVisibleState;

        int firstRowHeight = mAdapter.getRowHeight(0);
        int firstColWidth = mAdapter.getColWidth(0);
        int rowY = mVisibleState.scrollY - state.firstBodyRowOffset + firstRowHeight;
        for (int row = state.firstBodyRow; row < state.firstBodyRow + state.bodyRowCount; row++) {
            layoutRow(firstColWidth, row, rowY);
            rowY += mAdapter.getRowHeight(row);
        }
        //head row
        layoutRow(firstColWidth, 0, state.scrollY);
        mVisibleState.invalid = false;
        mIsLayouting = false;
        //排版完之后自动滚动,防止越界
        scrollTo(mVisibleState.scrollX, mVisibleState.scrollY);
    }

    //合并单元格需要改造layoutRow和recycleCells, 合并单元格只处理左上角
    private void layoutRow(final int firstColWidth, final int row, final int y) {
        LayoutState state = mVisibleState;
        int x = state.scrollX - state.firstBodyColOffset + firstColWidth;//内部起点
        int rowHeight = mAdapter.getRowHeight(row);
        for (int col = state.firstBodyCol; col < state.firstBodyCol + state.bodyColCount;) {
            Span span = mAdapter.querySpan(row, col);
            if (Span.isSpan(span)) {
                int spanX = x, spanY = y;
                int spanWidth = 0, spanHeight = 0;
                for (int i = span.lt.col; i <= span.rb.col; i++) {
                    int w = mAdapter.getColWidth(i);
                    spanWidth += w;
                    if (i < col) {
                        spanX -= w;
                    }
                }
                for (int i = span.lt.row; i <= span.rb.row; i++) {
                    int h = mAdapter.getRowHeight(i);
                    spanHeight += h;
                    if (i < row) {
                        spanY -= h;
                    }
                }

                Cell cell = layoutCell(span.lt, spanX, spanY, spanWidth, spanHeight);
                cell.rb = span.rb;
                col = span.rb.col + 1;
                x = cell.x + cell.w;
            } else {
                int colWidth = mAdapter.getColWidth(col);
                layoutCell(new Position(row, col), x, y, colWidth, rowHeight);
                x += colWidth;
                col++;
            }
        }
        //最左侧列
        Cell cell = layoutCell(new Position(row, 0), state.scrollX, y, firstColWidth, rowHeight);
        if (row == 0 && getChildAt(getChildCount()-1) != cell.view) {
            bringChildToFront(cell.view);
        }
    }

    private Cell layoutCell(Position pos, int x, int y, int w, int h) {
        Cell cell = mVisibleCells.get(pos);
        boolean needLayout = false;
        if (cell == null) {
            int viewType = mAdapter.getCellViewType(pos.row, pos.col);
            View view = mRecycler.reuse(viewType);
            view = mAdapter.getCellView(getContext(), view, pos.row, pos.col);
            mVisibleCells.put(pos, cell = new Cell(view, viewType));
            needLayout = true;
        } else if (mVisibleState.invalid) {
            cell.view = mAdapter.getCellView(getContext(), cell.view, pos.row, pos.col);
        }

        cell.lt = pos;
        cell.rb = null;

        if (cell.view.getParent() == null) {
            addView(cell.view, (pos.row == 0 || pos.col == 0) ? -1 : 0);
            needLayout = true;
        } else if (cell.view.getParent() != this) {
            throw new IllegalStateException("view.parent != this");
        }

        if (cell.w != w || cell.h != h || cell.view.getWidth() != w - mDividerWidth ||
                cell.view.getHeight() != h - mDividerWidth) {
            cell.w = w;
            cell.h = h;
            cell.view.measure(
                    MeasureSpec.makeMeasureSpec(w - mDividerWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h - mDividerWidth, MeasureSpec.EXACTLY)
            );
            needLayout = true;
        }
        if (cell.x != x || cell.y != y) {
            cell.x = x;
            cell.y = y;
            needLayout = true;
        }
        if (needLayout) {
            cell.view.layout(x, y, x + w - mDividerWidth, y + h - mDividerWidth);
            Log.i(TAG, "layoutCell " + pos);
        }

        return cell;
    }


    private void recycleAllCells() {
        for (Position position : mVisibleCells.keySet()) {
            Cell cell = mVisibleCells.get(position);
            mRecycler.recycle(cell.viewType, cell.view);
        }
        removeAllViews();
        mVisibleCells.clear();
        mVisibleState.init();
        mPreLayoutState.init();
    }

    //合并单元格需要改造layoutRow和recycleCells
    private void recycleCells() {
        LayoutState state = mPreLayoutState;
        mRecycler.tmpPositions.clear();
        for (Position position : mVisibleCells.keySet()) {
            Cell cell = mVisibleCells.get(position);
            boolean cellVisible = state.isCellVisible(cell.lt.row, cell.lt.col);
            if (cell.rb != null && !cellVisible) {
                cellVisible = state.isCellVisible(cell.rb.row, cell.rb.col);
            }
            if (!cellVisible) {
                mRecycler.tmpPositions.add(position);
            }
        }

        for (Position position : mRecycler.tmpPositions) {
            Cell cell = mVisibleCells.remove(position);
            removeView(cell.view);
            mRecycler.recycle(cell.viewType, cell.view);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


    public static abstract class ExcelAdapter {
        private final DataSetObservable mDataSetObservable = new DataSetObservable();

        public void registerDataSetObserver(DataSetObserver observer) {
            mDataSetObservable.registerObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDataSetObservable.unregisterObserver(observer);
        }

        public void notifyDataSetChanged() {
            mDataSetObservable.notifyChanged();
        }

        public abstract Span querySpan(int row, int col);

        public abstract int getColCount();

        public abstract int getRowCount();

        public abstract int getRowHeight(int row);

        public abstract int getColWidth(int col);

        public abstract int getCellViewType(int row, int col);

        public abstract View getCellView(Context context, View convertView, int row, int col);
    }

    public static class Span {
        final Position lt;//左上角
        final Position rb;//右下角

        public Span(Position lt, Position rb) {
            this.lt = lt;
            this.rb = rb;
        }
        public Span(int r1, int c1, int r2, int c2) {
            this(new Position(r1, c1), new Position(r2, c2));
        }

        public boolean contains(int row, int col) {
            return row >= lt.row && row <= rb.row && col >= lt.col && col <= rb.col;
        }

        private static boolean isSpan(Span span) {
            return !(span == null || span.lt.equals(span.rb));
        }

        private boolean isLeftTop(int row, int col) {//
            return lt.row == row && lt.col == col;
        }
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    private static class Recycler {
        Map<Integer, List<View>> views = new HashMap<>();
        List<Position> tmpPositions = new ArrayList<>();

        public void recycle(int viewType, View view) {
            List<View> list = views.get(viewType);
            if (list == null) {
                views.put(viewType, list = new ArrayList<>());
            }
            list.add(view);
        }

        public View reuse(int viewType) {
            List<View> list = views.get(viewType);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.remove(list.size() - 1);
        }

        public void clear() {
            views.clear();
            tmpPositions.clear();
        }
    }

    private static class LayoutState {
        boolean invalid = true;
        int scrollX, scrollY;
        int width, height;
        int firstBodyRow = 0, firstBodyCol = 0;
        int firstBodyRowOffset = 0, firstBodyColOffset = 0;//firstBodyRow被head挡住的部分, 为正数
        int bodyRowCount = 0, bodyColCount = 0;
        
        public boolean isCellVisible(int row, int col) {
            boolean cellVisible;
            int bodyRowEnd = firstBodyRow + bodyRowCount;
            int bodyColEnd = firstBodyCol + bodyColCount;
            if (col == 0 && row == 0) {//左上角永不回收
                cellVisible = true;
            } else if (col == 0) {//第0列
                cellVisible = row >= firstBodyRow && row < bodyRowEnd;
            } else if (row == 0) {//第0行
                cellVisible = col >= firstBodyCol && col < bodyColEnd;
            } else {
                cellVisible = row >= firstBodyRow && row < bodyRowEnd &&
                        col >= firstBodyCol && col < bodyColEnd;
            }
            return cellVisible;
        }

        @Override
        public String toString() {
            return "LayoutState{" +
                    "invalid=" + invalid +
                    ", scrollX=" + scrollX +
                    ", scrollY=" + scrollY +
                    ", width=" + width +
                    ", height=" + height +
                    ", firstBodyRow=" + firstBodyRow +
                    ", firstBodyCol=" + firstBodyCol +
                    ", firstBodyRowOffset=" + firstBodyRowOffset +
                    ", firstBodyColOffset=" + firstBodyColOffset +
                    ", bodyRowCount=" + bodyRowCount +
                    ", bodyColCount=" + bodyColCount +
                    '}';
        }

        public void init() {
            scrollX = 0;
            scrollY = 0;
            firstBodyRow = 0;
            firstBodyCol = 0;
            bodyRowCount = 0;
            bodyColCount = 0;
            firstBodyRowOffset = 0;
            firstBodyColOffset = 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LayoutState that = (LayoutState) o;

            if (scrollX != that.scrollX) return false;
            if (scrollY != that.scrollY) return false;
            if (width != that.width) return false;
            if (height != that.height) return false;
            if (firstBodyRow != that.firstBodyRow) return false;
            if (firstBodyCol != that.firstBodyCol) return false;
            if (firstBodyRowOffset != that.firstBodyRowOffset) return false;
            if (firstBodyColOffset != that.firstBodyColOffset) return false;
            if (bodyRowCount != that.bodyRowCount) return false;
            return bodyColCount == that.bodyColCount;
        }
        
        public void copyFrom(LayoutState that) {
            scrollX = that.scrollX ;
            scrollY = that.scrollY ;
            width = that.width ;
            height = that.height ;
            firstBodyRow = that.firstBodyRow ;
            firstBodyCol = that.firstBodyCol ;
            firstBodyRowOffset = that.firstBodyRowOffset;
            firstBodyColOffset = that.firstBodyColOffset;
            bodyRowCount = that.bodyRowCount ;
            bodyColCount = that.bodyColCount;
        }
    }

//    private static class Layouter {
//        final Map<Position, Cell> cells = new HashMap<>();
//        final LayoutState state = new LayoutState();
//
//        public void add(Position position, Cell cell) {
//            if (cells.containsKey(position)) {
//                throw new IllegalStateException(position + " 已经有cell");
//            }
//            cells.put(position, cell);
//        }
//
//        public Cell remove(Position position) {
//            return cells.remove(position);
//        }
//
//        public Cell get(Position position) {
//            return cells.get(position);
//        }
//        
//        public void clear() {
//            state.init();
//            cells.clear();
//        }
//    }

    private static abstract class ScrollHelper {
        int startOffset;
        int startIndex;
        int bodyCount;
        int scroll;

        public abstract int getViewCount();
        public abstract int getViewSize(int index);

        public void scroll(int _startOffset, int _startIndex, int visibleSize, int oldScroll, int newScroll) {
            this.startOffset = _startOffset;
            this.startIndex = _startIndex;
            this.scroll = oldScroll;
            newScroll = Math.max(0, newScroll);

            int viewCount = getViewCount();
            if (viewCount <= 1) {
                startIndex = 1;
                startOffset = 0;
                bodyCount = 0;
                scroll = 0;
                return;
            }

            startIndex = Math.max(1, Math.min(viewCount-1, startIndex));
            startOffset = startOffset >= getViewSize(startIndex) ? 0 : startOffset;
            int scrollDistance = newScroll - oldScroll;
            if (scrollDistance > 0) {
                while (scrollDistance > 0 && startIndex < viewCount) {
                    int tmp = getViewSize(startIndex)-startOffset;
                    if (scrollDistance >= tmp) {
                        startOffset = 0;
                        startIndex++;
                        scrollDistance -= tmp;
                    } else {
                        startOffset += scrollDistance;
                        scrollDistance = 0;
                    }
                }//startIndex可能==cellCount
            } else if (scrollDistance < 0) {
                int tmp = Math.min(-scrollDistance, startOffset);
                startOffset -= tmp;
                scrollDistance += tmp;
                while (scrollDistance < 0 && startIndex > 1) {
                    tmp = getViewSize(--startIndex);
                    if (-scrollDistance >= tmp) {
                        scrollDistance += tmp;
                    } else {
                        startOffset = tmp+scrollDistance;
                        scrollDistance = 0;
                    }
                }
            }
            scroll = newScroll - scrollDistance;
            int lastIndex = Math.min(startIndex, viewCount-1);//因为startIndex可能==cellcount
            int space = visibleSize - getViewSize(0) + startOffset;
            for (int i = startIndex; i < viewCount && space > 0; i++) {
                space -= getViewSize(i);
                lastIndex = i;
            }
            //右下部还有空间, 强制右下滚动, 调整scroll
            while (true) {
                if (space > 0) {
                    int min = Math.min(startOffset, space);
                    startOffset -= min;
                    space -= min;
                    scroll -= min;
                }
                if (space > 0 && startIndex > 1) {
                    int size = getViewSize(--startIndex);
                    int min = Math.min(size, space);
                    startOffset = size - min;
                    space -= min;
                    scroll -= min;
                } else {
                    break;
                }
            }
            if (startIndex == 1) {
                scroll = startOffset;
            }
            bodyCount = lastIndex - startIndex + 1;
        }
    }


    private static class Cell {
        View view;
        int viewType;
        int x, y, w, h;
        Position lt, rb;//如果rb!=null说明是合并单元格

        public Cell(View view, int viewType) {
            this.view = view;
            this.viewType = viewType;
        }
    }

    private static class Position {
        public final int row, col;

        public Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Position position = (Position) o;

            if (row != position.row) return false;
            return col == position.col;
        }

        @Override
        public int hashCode() {
            int result = row;
            result = 31 * result + col;
            return result;
        }

        @Override
        public String toString() {
            return "Cell{" + "row=" + row + ", col=" + col + '}';
        }
    }


    //可视界面的 合并单元格 冲突处理, 暂未实现
//    private static class SpanHitTest {
//        public int colCount, rowCount;
//        public final List<Span> spanList = new ArrayList<>();
//        public Span find(Position p) {
//            for (Span s : spanList) {
//                if (s.contains(p.row, p.col)) {
//                    return s;
//                } else if (s.lt.col > p.col) {
//                    break;
//                }
//            }
//            return null;
//        }
//        public void add(Span span) {
//            int i;
//            for (i = 0; i < spanList.size(); i++) {
//                if (span.lt.col < spanList.get(i).lt.col) {
//                    break;
//                }
//            }
//            spanList.add(i, span);
//        }
//    }

}
