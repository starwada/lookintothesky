package com.lunarbase24.lookintothesky;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created by Wada on 2016/10/07.
 * 通知結果データクラス
 * データメンバ
 * ウィジェットID
 * 時間帯フラグ
 */

public class NotifyResult {

    private int mWidgetID;
    private int mTimezone[] = {0, 0, 0, 0};
    private int mId;            // 通知ID 通知毎にIDがある

    public NotifyResult(int widget, int id){
        mWidgetID = widget;
        mId = id;
    }

    public int getWidgetID(){
        return mWidgetID;
    }
    public int getNotifyId(){ return mId; }

    public void setTimezone(int timezone){
        reset(timezone);
    }

    public boolean checktimezone(GregorianCalendar now){
        boolean bOk = false;

        int index = now.get(Calendar.HOUR_OF_DAY)/6;
        if(index >= 0 && index < 4){
            if(mTimezone[index] > 0){
                bOk = true;
                mTimezone[index] = 0;
            }
        }
        return bOk;
    }

    public void reset(int timezone){
        int mask = 1;
        for(int i=0; i<4; i++) {
            mask = 1 << i;
            mTimezone[i] = 0;
            if((timezone & mask) != 0){
                mTimezone[i] = 1;
            }
        }
    }

}
