package com.lunarbase24.lookintothesky;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wada on 2016/06/07.
 */
public class GraphViewAdapter extends RecyclerView.Adapter<GraphViewAdapter.ViewHolder> {
    private Context mContext;
    private List<Soramame> mList;
    private int mMode;      // データ表示モード　0 PM2.5/1 光化学オキシダント/2 風速
    private int mDay;
    private int mGraph;     // グラフ背景の透過度

    public static class ViewHolder extends RecyclerView.ViewHolder{
        public TextView stationname;
        public SoraGraphView soragraph;
        public TextView soramax, soraave;
        public TextView date, hour, value;
        public ImageView imageWS, imageSnap;

        public ViewHolder(View view){
            super(view);
            soragraph = (SoraGraphView) view.findViewById(R.id.soragraph);
            stationname = (TextView)view.findViewById(R.id.MstName);
            soramax = (TextView)view.findViewById(R.id.soramax);
            soraave = (TextView)view.findViewById(R.id.soraave);
            date = (TextView)view.findViewById(R.id.date);
            hour = (TextView)view.findViewById(R.id.hour);
            value = (TextView)view.findViewById(R.id.value);
            imageWS = (ImageView)view.findViewById(R.id.imageWS);
            imageSnap = (ImageView)view.findViewById(R.id.card_snap);
        }
    }

    public GraphViewAdapter(Context context, List<Soramame> objects, int mode, int day, int graph){
        this.mContext = context;
        this.mList = objects;
        this.mMode = mode;
        this.mDay = day;
        this.mGraph = graph;
    }

    public void SetMode(int mode){ mMode = mode; }
    public void SetDispDay(int day){ mDay = day; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.graphcard, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        Soramame data = mList.get(position);

        holder.soragraph.setData(data);
        holder.soragraph.setMode(mMode);
        holder.soragraph.setDispDay(mDay);
        holder.soragraph.setTransparency(mGraph);
        holder.soramax.setText(holder.soragraph.getMaxString());
        holder.soraave.setText(holder.soragraph.getAveString());
        holder.stationname.setText(data.getMstName());
        // 以下はリストのインデックス確認のデバッグ用
        //holder.stationname.setText(data.getMstName() + ":" + String.valueOf(data.getSelIndex()));

        // 測定局名のロングタップでマップ起動（あれば）
        holder.stationname.setOnLongClickListener(new View.OnLongClickListener(){
            @Override
            public boolean onLongClick(View v) {
                Soramame data = mList.get(position);
                Uri location = Uri.parse("geo:0,0?q=" + Uri.encode(data.getAddress()));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, location);
                mapIntent.setPackage("com.google.android.apps.maps");

                if (mapIntent.resolveActivity(mContext.getPackageManager()) != null) {
                    mContext.startActivity(mapIntent);
                }
                return false;
            }
        });

        TouchEvent( holder, position);

        // グラフビューにタッチリスナー設定
        holder.soragraph.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_MOVE ) {
                    float px = event.getX(0);
                    int sum = event.getPointerCount();
                    holder.soragraph.Touch(px);
                }
                else if(event.getActionMasked() == MotionEvent.ACTION_UP){
//                    holder.soragraph.showToast();
                    TouchEvent( holder, position);
                }
                //else if(event.getActionMasked() == MotionEvent.ACTION_POINTER_INDEX_MASK) {

                //}
                return true;
            }
        });

        // キャプチャアイコンのロングタップ
        holder.imageSnap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                holder.soragraph.Capture();
                holder.soragraph.showToast();
                return false;
            }
        });
    }

    // 指定位置の日時、計測値を設定
    public void TouchEvent(final ViewHolder holder, final int position ){
        Soramame.SoramameData val = holder.soragraph.getPosData();
        holder.date.setText(val.getDateString());
        holder.hour.setText(val.getHourString());
        switch(mMode){
            case Soramame.SORAMAME_MODE_PM25:
                holder.value.setText(val.getPM25String());
                holder.imageWS.setVisibility(View.INVISIBLE);
                break;
            case Soramame.SORAMAME_MODE_OX:
                holder.value.setText(val.getOXString());
                holder.imageWS.setVisibility(View.INVISIBLE);
                break;
            case Soramame.SORAMAME_MODE_WS:
                // 風向のアイコン用にスペースを入れる
                holder.value.setText(val.getWSString()+"　");
                // 静穏の場合
                if(val.getWDRotation() < 0.0f) {
                    holder.imageWS.setVisibility(View.INVISIBLE);
                }else{
                    holder.imageWS.setVisibility(View.VISIBLE);
                    // 風向の向きにアイコンを回転させる
                    holder.imageWS.setRotation(val.getWDRotation());
                }
                break;
        }
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }
}
